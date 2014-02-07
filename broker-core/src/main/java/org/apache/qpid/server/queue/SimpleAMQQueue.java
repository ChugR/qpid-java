/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.qpid.server.queue;

import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.log4j.Logger;
import org.apache.qpid.AMQException;
import org.apache.qpid.AMQSecurityException;
import org.apache.qpid.pool.ReferenceCountingExecutorService;
import org.apache.qpid.server.binding.Binding;
import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.configuration.QueueConfiguration;
import org.apache.qpid.server.exchange.Exchange;
import org.apache.qpid.server.filter.FilterManager;
import org.apache.qpid.server.logging.LogActor;
import org.apache.qpid.server.logging.LogSubject;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.QueueActor;
import org.apache.qpid.server.logging.messages.QueueMessages;
import org.apache.qpid.server.logging.subjects.QueueLogSubject;
import org.apache.qpid.server.message.InstanceProperties;
import org.apache.qpid.server.message.MessageDestination;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.message.MessageReference;
import org.apache.qpid.server.message.ServerMessage;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.security.AuthorizationHolder;
import org.apache.qpid.server.consumer.Consumer;
import org.apache.qpid.server.consumer.ConsumerTarget;
import org.apache.qpid.server.txn.AutoCommitTransaction;
import org.apache.qpid.server.txn.LocalTransaction;
import org.apache.qpid.server.txn.ServerTransaction;
import org.apache.qpid.server.util.Action;
import org.apache.qpid.server.util.StateChangeListener;
import org.apache.qpid.server.virtualhost.VirtualHost;

public class SimpleAMQQueue implements AMQQueue<QueueConsumer>,
                                       StateChangeListener<QueueConsumer, QueueConsumer.State>,
                                       MessageGroupManager.ConsumerResetHelper
{

    private static final Logger _logger = Logger.getLogger(SimpleAMQQueue.class);

    public static final String SHARED_MSG_GROUP_ARG_VALUE = "1";
    private static final String QPID_NO_GROUP = "qpid.no-group";
    private static final String DEFAULT_SHARED_MESSAGE_GROUP = System.getProperty(BrokerProperties.PROPERTY_DEFAULT_SHARED_MESSAGE_GROUP, QPID_NO_GROUP);

    // TODO - should make this configurable at the vhost / broker level
    private static final int DEFAULT_MAX_GROUPS = 255;

    private final VirtualHost _virtualHost;

    private final String _name;

    /** null means shared */
    private final String _owner;

    private AuthorizationHolder _authorizationHolder;

    private boolean _exclusive = false;
    private AMQSessionModel _exclusiveOwner;


    private final boolean _durable;

    /** If true, this queue is deleted when the last subscriber is removed */
    private final boolean _autoDelete;

    private Exchange _alternateExchange;


    private final QueueEntryList<QueueEntry> _entries;

    private final QueueConsumerList _consumerList = new QueueConsumerList();

    private volatile QueueConsumer _exclusiveSubscriber;



    private final AtomicInteger _atomicQueueCount = new AtomicInteger(0);

    private final AtomicLong _atomicQueueSize = new AtomicLong(0L);

    private final AtomicInteger _activeSubscriberCount = new AtomicInteger();

    private final AtomicLong _totalMessagesReceived = new AtomicLong();

    private final AtomicLong _dequeueCount = new AtomicLong();
    private final AtomicLong _dequeueSize = new AtomicLong();
    private final AtomicLong _enqueueCount = new AtomicLong();
    private final AtomicLong _enqueueSize = new AtomicLong();
    private final AtomicLong _persistentMessageEnqueueSize = new AtomicLong();
    private final AtomicLong _persistentMessageDequeueSize = new AtomicLong();
    private final AtomicLong _persistentMessageEnqueueCount = new AtomicLong();
    private final AtomicLong _persistentMessageDequeueCount = new AtomicLong();
    private final AtomicLong _unackedMsgCount = new AtomicLong(0);
    private final AtomicLong _unackedMsgBytes = new AtomicLong();

    private final AtomicInteger _bindingCountHigh = new AtomicInteger();

    /** max allowed size(KB) of a single message */
    private long _maximumMessageSize;

    /** max allowed number of messages on a queue. */
    private long _maximumMessageCount;

    /** max queue depth for the queue */
    private long _maximumQueueDepth;

    /** maximum message age before alerts occur */
    private long _maximumMessageAge;

    /** the minimum interval between sending out consecutive alerts of the same type */
    private long _minimumAlertRepeatGap;

    private long _capacity;

    private long _flowResumeCapacity;

    private final Set<NotificationCheck> _notificationChecks = EnumSet.noneOf(NotificationCheck.class);


    static final int MAX_ASYNC_DELIVERIES = 80;


    private final AtomicLong _stateChangeCount = new AtomicLong(Long.MIN_VALUE);

    private final Executor _asyncDelivery;
    private AtomicInteger _deliveredMessages = new AtomicInteger();
    private AtomicBoolean _stopped = new AtomicBoolean(false);

    private final Set<AMQSessionModel> _blockedChannels = new ConcurrentSkipListSet<AMQSessionModel>();

    private final AtomicBoolean _deleted = new AtomicBoolean(false);
    private final List<Action<AMQQueue>> _deleteTaskList = new CopyOnWriteArrayList<Action<AMQQueue>>();


    private LogSubject _logSubject;
    private LogActor _logActor;

    private static final String SUB_FLUSH_RUNNER = "SUB_FLUSH_RUNNER";
    private boolean _nolocal;

    private final AtomicBoolean _overfull = new AtomicBoolean(false);
    private boolean _deleteOnNoConsumers;
    private final CopyOnWriteArrayList<Binding> _bindings = new CopyOnWriteArrayList<Binding>();
    private UUID _id;
    private final Map<String, Object> _arguments;

    //TODO : persist creation time
    private long _createTime = System.currentTimeMillis();

    /** the maximum delivery count for each message on this queue or 0 if maximum delivery count is not to be enforced. */
    private int _maximumDeliveryCount;
    private final MessageGroupManager _messageGroupManager;

    private final Collection<ConsumerRegistrationListener> _consumerListeners =
            new ArrayList<ConsumerRegistrationListener>();

    private AMQQueue.NotificationListener _notificationListener;
    private final long[] _lastNotificationTimes = new long[NotificationCheck.values().length];


    public SimpleAMQQueue(UUID id, String queueName, boolean durable, String owner, boolean autoDelete, boolean exclusive, VirtualHost virtualHost, Map<String, Object> arguments)
    {
        this(id, queueName, durable, owner, autoDelete, exclusive, virtualHost, new SimpleQueueEntryList.Factory(), arguments);
    }

    protected SimpleAMQQueue(UUID id,
                             String name,
                             boolean durable,
                             String owner,
                             boolean autoDelete,
                             boolean exclusive,
                             VirtualHost virtualHost,
                             QueueEntryListFactory entryListFactory, Map<String,Object> arguments)
    {

        if (name == null)
        {
            throw new IllegalArgumentException("Queue name must not be null");
        }

        if (virtualHost == null)
        {
            throw new IllegalArgumentException("Virtual Host must not be null");
        }

        _name = name;
        _durable = durable;
        _owner = owner;
        _autoDelete = autoDelete;
        _exclusive = exclusive;
        _virtualHost = virtualHost;
        _entries = entryListFactory.createQueueEntryList(this);
        _arguments = Collections.synchronizedMap(arguments == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(arguments));

        _id = id;
        _asyncDelivery = ReferenceCountingExecutorService.getInstance().acquireExecutorService();

        _logSubject = new QueueLogSubject(this);
        _logActor = new QueueActor(this, CurrentActor.get().getRootMessageLogger());

        // Log the creation of this Queue.
        // The priorities display is toggled on if we set priorities > 0
        CurrentActor.get().message(_logSubject,
                                   QueueMessages.CREATED(String.valueOf(_owner),
                                                         _entries.getPriorities(),
                                                         _owner != null,
                                                         autoDelete,
                                                         durable, !durable,
                                                         _entries.getPriorities() > 0));

        if(arguments != null && arguments.containsKey(Queue.MESSAGE_GROUP_KEY))
        {
            if(arguments.get(Queue.MESSAGE_GROUP_SHARED_GROUPS) != null
               && (Boolean)(arguments.get(Queue.MESSAGE_GROUP_SHARED_GROUPS)))
            {
                Object defaultGroup = arguments.get(Queue.MESSAGE_GROUP_DEFAULT_GROUP);
                _messageGroupManager =
                        new DefinedGroupMessageGroupManager(String.valueOf(arguments.get(Queue.MESSAGE_GROUP_KEY)),
                                defaultGroup == null ? DEFAULT_SHARED_MESSAGE_GROUP : defaultGroup.toString(),
                                this);
            }
            else
            {
                _messageGroupManager = new AssignedConsumerMessageGroupManager(String.valueOf(arguments.get(
                        Queue.MESSAGE_GROUP_KEY)), DEFAULT_MAX_GROUPS);
            }
        }
        else
        {
            _messageGroupManager = null;
        }

        resetNotifications();

    }

    public void resetNotifications()
    {
        // This ensure that the notification checks for the configured alerts are created.
        setMaximumMessageAge(_maximumMessageAge);
        setMaximumMessageCount(_maximumMessageCount);
        setMaximumMessageSize(_maximumMessageSize);
        setMaximumQueueDepth(_maximumQueueDepth);
    }

    // ------ Getters and Setters

    public void execute(Runnable runnable)
    {
        try
        {
            _asyncDelivery.execute(runnable);
        }
        catch (RejectedExecutionException ree)
        {
            if (_stopped.get())
            {
                // Ignore - SubFlusherRunner or QueueRunner submitted execution as queue was being stopped.
            }
            else
            {
                _logger.error("Unexpected rejected execution", ree);
                throw ree;
            }
        }
    }

    public void setNoLocal(boolean nolocal)
    {
        _nolocal = nolocal;
    }

    public UUID getId()
    {
        return _id;
    }

    public boolean isDurable()
    {
        return _durable;
    }

    public boolean isExclusive()
    {
        return _exclusive;
    }

    public void setExclusive(boolean exclusive)
    {
        _exclusive = exclusive;
    }

    public Exchange getAlternateExchange()
    {
        return _alternateExchange;
    }

    public void setAlternateExchange(Exchange exchange)
    {
        if(_alternateExchange != null)
        {
            _alternateExchange.removeReference(this);
        }
        if(exchange != null)
        {
            exchange.addReference(this);
        }
        _alternateExchange = exchange;
    }


    @Override
    public Collection<String> getAvailableAttributes()
    {
        return new ArrayList<String>(_arguments.keySet());
    }

    @Override
    public Object getAttribute(String attrName)
    {
        return _arguments.get(attrName);
    }

    public boolean isAutoDelete()
    {
        return _autoDelete;
    }

    public String getOwner()
    {
        return _owner;
    }

    public AuthorizationHolder getAuthorizationHolder()
    {
        return _authorizationHolder;
    }

    public void setAuthorizationHolder(final AuthorizationHolder authorizationHolder)
    {
        _authorizationHolder = authorizationHolder;
    }


    public VirtualHost getVirtualHost()
    {
        return _virtualHost;
    }

    public String getName()
    {
        return _name;
    }

    // ------ Manage Consumers


    @Override
    public synchronized QueueConsumer addConsumer(final ConsumerTarget target,
                                     final FilterManager filters,
                                     final Class<? extends ServerMessage> messageClass,
                                     final String consumerName,
                                     EnumSet<Consumer.Option> optionSet) throws AMQException
    {

        // Access control
        if (!getVirtualHost().getSecurityManager().authoriseConsume(this))
        {
            throw new AMQSecurityException("Permission denied");
        }


        if (hasExclusiveConsumer())
        {
            throw new ExistingExclusiveConsumer();
        }


        boolean exclusive =  optionSet.contains(Consumer.Option.EXCLUSIVE);
        boolean isTransient =  optionSet.contains(Consumer.Option.TRANSIENT);

        if (exclusive && !isTransient && getConsumerCount() != 0)
        {
            throw new ExistingConsumerPreventsExclusive();
        }

        QueueConsumer consumer = new QueueConsumer(filters, messageClass,
                                                                optionSet.contains(Consumer.Option.ACQUIRES),
                                                                optionSet.contains(Consumer.Option.SEES_REQUEUES),
                                                                consumerName, optionSet.contains(Consumer.Option.TRANSIENT), target);
        target.consumerAdded(consumer);


        if (exclusive && !isTransient)
        {
            _exclusiveSubscriber = consumer;
        }

        if(consumer.isActive())
        {
            _activeSubscriberCount.incrementAndGet();
        }

        consumer.setStateListener(this);
        consumer.setQueueContext(new QueueContext(_entries.getHead()));

        if (!isDeleted())
        {
            consumer.setQueue(this, exclusive);
            if(_nolocal)
            {
                consumer.setNoLocal(_nolocal);
            }

            synchronized (_consumerListeners)
            {
                for(ConsumerRegistrationListener listener : _consumerListeners)
                {
                    listener.consumerAdded(this, consumer);
                }
            }

            _consumerList.add(consumer);

            if (isDeleted())
            {
                consumer.queueDeleted();
            }
        }
        else
        {
            // TODO
        }

        deliverAsync(consumer);

        return consumer;

    }

    synchronized void unregisterConsumer(final QueueConsumer consumer) throws AMQException
    {
        if (consumer == null)
        {
            throw new NullPointerException("consumer argument is null");
        }

        boolean removed = _consumerList.remove(consumer);

        if (removed)
        {
            consumer.close();
            // No longer can the queue have an exclusive consumer
            setExclusiveSubscriber(null);
            consumer.setQueueContext(null);

            if(!isDeleted() && isExclusive() && getConsumerCount() == 0)
            {
                setAuthorizationHolder(null);
            }

            if(_messageGroupManager != null)
            {
                resetSubPointersForGroups(consumer, true);
            }

            synchronized (_consumerListeners)
            {
                for(ConsumerRegistrationListener listener : _consumerListeners)
                {
                    listener.consumerRemoved(this, consumer);
                }
            }

            // auto-delete queues must be deleted if there are no remaining subscribers

            if (_autoDelete && getDeleteOnNoConsumers() && !consumer.isTransient() && getConsumerCount() == 0  )
            {
                if (_logger.isInfoEnabled())
                {
                    _logger.info("Auto-deleting queue:" + this);
                }

                getVirtualHost().removeQueue(this);

                // we need to manually fire the event to the removed consumer (which was the last one left for this
                // queue. This is because the delete method uses the consumer set which has just been cleared
                consumer.queueDeleted();
            }
        }

    }

    public Collection<QueueConsumer> getConsumers()
    {
        List<QueueConsumer> consumers = new ArrayList<QueueConsumer>();
        QueueConsumerList.ConsumerNodeIterator iter = _consumerList.iterator();
        while(iter.advance())
        {
            consumers.add(iter.getNode().getConsumer());
        }
        return consumers;

    }

    public void addConsumerRegistrationListener(final ConsumerRegistrationListener listener)
    {
        synchronized (_consumerListeners)
        {
            _consumerListeners.add(listener);
        }
    }

    public void removeConsumerRegistrationListener(final ConsumerRegistrationListener listener)
    {
        synchronized (_consumerListeners)
        {
            _consumerListeners.remove(listener);
        }
    }

    public void resetSubPointersForGroups(QueueConsumer consumer, boolean clearAssignments)
    {
        QueueEntry entry = _messageGroupManager.findEarliestAssignedAvailableEntry(consumer);
        if(clearAssignments)
        {
            _messageGroupManager.clearAssignments(consumer);
        }

        if(entry != null)
        {
            QueueConsumerList.ConsumerNodeIterator subscriberIter = _consumerList.iterator();
            // iterate over all the subscribers, and if they are in advance of this queue entry then move them backwards
            while (subscriberIter.advance())
            {
                QueueConsumer sub = subscriberIter.getNode().getConsumer();

                // we don't make browsers send the same stuff twice
                if (sub.seesRequeues())
                {
                    updateSubRequeueEntry(sub, entry);
                }
            }

            deliverAsync();

        }
    }

    public boolean getDeleteOnNoConsumers()
    {
        return _deleteOnNoConsumers;
    }

    public void setDeleteOnNoConsumers(boolean b)
    {
        _deleteOnNoConsumers = b;
    }

    public void addBinding(final Binding binding)
    {
        _bindings.add(binding);
        int bindingCount = _bindings.size();
        int bindingCountHigh;
        while(bindingCount > (bindingCountHigh = _bindingCountHigh.get()))
        {
            if(_bindingCountHigh.compareAndSet(bindingCountHigh, bindingCount))
            {
                break;
            }
        }
    }

    public int getBindingCountHigh()
    {
        return _bindingCountHigh.get();
    }

    public void removeBinding(final Binding binding)
    {
        _bindings.remove(binding);
    }

    public List<Binding> getBindings()
    {
        return Collections.unmodifiableList(_bindings);
    }

    public int getBindingCount()
    {
        return getBindings().size();
    }

    public LogSubject getLogSubject()
    {
        return _logSubject;
    }

    // ------ Enqueue / Dequeue

    public void enqueue(ServerMessage message, Action<MessageInstance<? extends Consumer>> action) throws AMQException
    {
        incrementQueueCount();
        incrementQueueSize(message);

        _totalMessagesReceived.incrementAndGet();


        QueueEntry entry;
        final QueueConsumer exclusiveSub = _exclusiveSubscriber;
        entry = _entries.add(message);

        if(action != null || (exclusiveSub == null  && _queueRunner.isIdle()))
        {
            /*

            iterate over consumers and if any is at the end of the queue and can deliver this message, then deliver the message

             */
            QueueConsumerList.ConsumerNode node = _consumerList.getMarkedNode();
            QueueConsumerList.ConsumerNode nextNode = node.findNext();
            if (nextNode == null)
            {
                nextNode = _consumerList.getHead().findNext();
            }
            while (nextNode != null)
            {
                if (_consumerList.updateMarkedNode(node, nextNode))
                {
                    break;
                }
                else
                {
                    node = _consumerList.getMarkedNode();
                    nextNode = node.findNext();
                    if (nextNode == null)
                    {
                        nextNode = _consumerList.getHead().findNext();
                    }
                }
            }

            // always do one extra loop after we believe we've finished
            // this catches the case where we *just* miss an update
            int loops = 2;

            while (entry.isAvailable() && loops != 0)
            {
                if (nextNode == null)
                {
                    loops--;
                    nextNode = _consumerList.getHead();
                }
                else
                {
                    // if consumer at end, and active, offer
                    QueueConsumer sub = nextNode.getConsumer();
                    deliverToConsumer(sub, entry);
                }
                nextNode = nextNode.findNext();

            }
        }


        if (entry.isAvailable())
        {
            checkConsumersNotAheadOfDelivery(entry);

            if (exclusiveSub != null)
            {
                deliverAsync(exclusiveSub);
            }
            else
            {
                deliverAsync();
           }
        }

        checkForNotification(entry.getMessage());

        if(action != null)
        {
            action.performAction(entry);
        }

    }

    private void deliverToConsumer(final QueueConsumer sub, final QueueEntry entry)
            throws AMQException
    {

        if(sub.trySendLock())
        {
            try
            {
                if (!sub.isSuspended()
                    && consumerReadyAndHasInterest(sub, entry)
                    && mightAssign(sub, entry)
                    && !sub.wouldSuspend(entry))
                {
                    if (sub.acquires() && !assign(sub, entry))
                    {
                        // restore credit here that would have been taken away by wouldSuspend since we didn't manage
                        // to acquire the entry for this consumer
                        sub.restoreCredit(entry);
                    }
                    else
                    {
                        deliverMessage(sub, entry, false);
                    }
                }
            }
            finally
            {
                sub.releaseSendLock();
            }
        }
    }

    private boolean assign(final QueueConsumer sub, final QueueEntry entry)
    {
        if(_messageGroupManager == null)
        {
            //no grouping, try to acquire immediately.
            return entry.acquire(sub);
        }
        else
        {
            //the group manager is responsible for acquiring the message if/when appropriate
            return _messageGroupManager.acceptMessage(sub, entry);
        }
    }

    private boolean mightAssign(final QueueConsumer sub, final QueueEntry entry)
    {
        if(_messageGroupManager == null || !sub.acquires())
        {
            return true;
        }
        QueueConsumer assigned = _messageGroupManager.getAssignedConsumer(entry);
        return (assigned == null) || (assigned == sub);
    }

    protected void checkConsumersNotAheadOfDelivery(final QueueEntry entry)
    {
        // This method is only required for queues which mess with ordering
        // Simple Queues don't :-)
    }

    private void incrementQueueSize(final ServerMessage message)
    {
        long size = message.getSize();
        getAtomicQueueSize().addAndGet(size);
        _enqueueCount.incrementAndGet();
        _enqueueSize.addAndGet(size);
        if(message.isPersistent() && isDurable())
        {
            _persistentMessageEnqueueSize.addAndGet(size);
            _persistentMessageEnqueueCount.incrementAndGet();
        }
    }

    public long getTotalDequeueCount()
    {
        return _dequeueCount.get();
    }

    public long getTotalEnqueueCount()
    {
        return _enqueueCount.get();
    }

    private void incrementQueueCount()
    {
        getAtomicQueueCount().incrementAndGet();
    }

    private void deliverMessage(final QueueConsumer sub, final QueueEntry entry, boolean batch)
            throws AMQException
    {
        setLastSeenEntry(sub, entry);

        _deliveredMessages.incrementAndGet();
        incrementUnackedMsgCount(entry);

        sub.send(entry, batch);
    }

    private boolean consumerReadyAndHasInterest(final QueueConsumer sub, final QueueEntry entry) throws AMQException
    {
        return sub.hasInterest(entry) && (getNextAvailableEntry(sub) == entry);
    }


    private void setLastSeenEntry(final QueueConsumer sub, final QueueEntry entry)
    {
        QueueContext subContext = sub.getQueueContext();
        if (subContext != null)
        {
            QueueEntry releasedEntry = subContext.getReleasedEntry();

            QueueContext._lastSeenUpdater.set(subContext, entry);
            if(releasedEntry == entry)
            {
               QueueContext._releasedUpdater.compareAndSet(subContext, releasedEntry, null);
            }
        }
    }

    private void updateSubRequeueEntry(final QueueConsumer sub, final QueueEntry entry)
    {

        QueueContext subContext = sub.getQueueContext();
        if(subContext != null)
        {
            QueueEntry oldEntry;

            while((oldEntry  = subContext.getReleasedEntry()) == null || oldEntry.compareTo(entry) > 0)
            {
                if(QueueContext._releasedUpdater.compareAndSet(subContext, oldEntry, entry))
                {
                    break;
                }
            }
        }
    }

    public void requeue(QueueEntry entry)
    {
        QueueConsumerList.ConsumerNodeIterator subscriberIter = _consumerList.iterator();
        // iterate over all the subscribers, and if they are in advance of this queue entry then move them backwards
        while (subscriberIter.advance() && entry.isAvailable())
        {
            QueueConsumer sub = subscriberIter.getNode().getConsumer();

            // we don't make browsers send the same stuff twice
            if (sub.seesRequeues())
            {
                updateSubRequeueEntry(sub, entry);
            }
        }

        deliverAsync();

    }

    public void dequeue(QueueEntry entry, Consumer sub)
    {
        decrementQueueCount();
        decrementQueueSize(entry);
        if (entry.acquiredByConsumer())
        {
            _deliveredMessages.decrementAndGet();
        }

        checkCapacity();

    }

    private void decrementQueueSize(final QueueEntry entry)
    {
        final ServerMessage message = entry.getMessage();
        long size = message.getSize();
        getAtomicQueueSize().addAndGet(-size);
        _dequeueSize.addAndGet(size);
        if(message.isPersistent() && isDurable())
        {
            _persistentMessageDequeueSize.addAndGet(size);
            _persistentMessageDequeueCount.incrementAndGet();
        }
    }

    void decrementQueueCount()
    {
        getAtomicQueueCount().decrementAndGet();
        _dequeueCount.incrementAndGet();
    }

    public boolean resend(final QueueEntry entry, final Consumer consumer) throws AMQException
    {
        /* TODO : This is wrong as the consumer may be suspended, we should instead change the state of the message
                  entry to resend and move back the consumer pointer. */

        consumer.getSendLock();
        try
        {
            if (!consumer.isClosed())
            {
                deliverMessage((QueueConsumer) consumer, entry, false);
                return true;
            }
            else
            {
                return false;
            }
        }
        finally
        {
            consumer.releaseSendLock();
        }
    }



    public int getConsumerCount()
    {
        return _consumerList.size();
    }

    public int getActiveConsumerCount()
    {
        return _activeSubscriberCount.get();
    }

    public boolean isUnused()
    {
        return getConsumerCount() == 0;
    }

    public boolean isEmpty()
    {
        return getMessageCount() == 0;
    }

    public int getMessageCount()
    {
        return getAtomicQueueCount().get();
    }

    public long getQueueDepth()
    {
        return getAtomicQueueSize().get();
    }

    public int getUndeliveredMessageCount()
    {
        int count = getMessageCount() - _deliveredMessages.get();
        if (count < 0)
        {
            return 0;
        }
        else
        {
            return count;
        }
    }

    public long getReceivedMessageCount()
    {
        return _totalMessagesReceived.get();
    }

    public long getOldestMessageArrivalTime()
    {
        QueueEntry entry = getOldestQueueEntry();
        return entry == null ? Long.MAX_VALUE : entry.getMessage().getArrivalTime();
    }

    protected QueueEntry getOldestQueueEntry()
    {
        return _entries.next(_entries.getHead());
    }

    public boolean isDeleted()
    {
        return _deleted.get();
    }

    public List<QueueEntry> getMessagesOnTheQueue()
    {
        ArrayList<QueueEntry> entryList = new ArrayList<QueueEntry>();
        QueueEntryIterator queueListIterator = _entries.iterator();
        while (queueListIterator.advance())
        {
            QueueEntry node = queueListIterator.getNode();
            if (node != null && !node.isDeleted())
            {
                entryList.add(node);
            }
        }
        return entryList;

    }

    public void stateChanged(QueueConsumer sub, QueueConsumer.State oldState, QueueConsumer.State newState)
    {
        if (oldState == QueueConsumer.State.ACTIVE && newState != QueueConsumer.State.ACTIVE)
        {
            _activeSubscriberCount.decrementAndGet();

        }
        else if (newState == QueueConsumer.State.ACTIVE)
        {
            if (oldState != QueueConsumer.State.ACTIVE)
            {
                _activeSubscriberCount.incrementAndGet();

            }
            deliverAsync(sub);
        }
    }

    public int compareTo(final AMQQueue o)
    {
        return _name.compareTo(o.getName());
    }

    public AtomicInteger getAtomicQueueCount()
    {
        return _atomicQueueCount;
    }

    public AtomicLong getAtomicQueueSize()
    {
        return _atomicQueueSize;
    }

    public boolean hasExclusiveConsumer()
    {
        return _exclusiveSubscriber != null;
    }

    private void setExclusiveSubscriber(QueueConsumer exclusiveSubscriber)
    {
        _exclusiveSubscriber = exclusiveSubscriber;
    }

    long getStateChangeCount()
    {
        return _stateChangeCount.get();
    }

    /** Used to track bindings to exchanges so that on deletion they can easily be cancelled. */
    protected QueueEntryList getEntries()
    {
        return _entries;
    }

    protected QueueConsumerList getConsumerList()
    {
        return _consumerList;
    }


    public static interface QueueEntryFilter
    {
        public boolean accept(QueueEntry entry);

        public boolean filterComplete();
    }



    public List<QueueEntry> getMessagesOnTheQueue(final long fromMessageId, final long toMessageId)
    {
        return getMessagesOnTheQueue(new QueueEntryFilter()
        {

            public boolean accept(QueueEntry entry)
            {
                final long messageId = entry.getMessage().getMessageNumber();
                return messageId >= fromMessageId && messageId <= toMessageId;
            }

            public boolean filterComplete()
            {
                return false;
            }
        });
    }

    public QueueEntry getMessageOnTheQueue(final long messageId)
    {
        List<QueueEntry> entries = getMessagesOnTheQueue(new QueueEntryFilter()
        {
            private boolean _complete;

            public boolean accept(QueueEntry entry)
            {
                _complete = entry.getMessage().getMessageNumber() == messageId;
                return _complete;
            }

            public boolean filterComplete()
            {
                return _complete;
            }
        });
        return entries.isEmpty() ? null : entries.get(0);
    }

    public List<QueueEntry> getMessagesOnTheQueue(QueueEntryFilter filter)
    {
        ArrayList<QueueEntry> entryList = new ArrayList<QueueEntry>();
        QueueEntryIterator queueListIterator = _entries.iterator();
        while (queueListIterator.advance() && !filter.filterComplete())
        {
            QueueEntry node = queueListIterator.getNode();
            if (!node.isDeleted() && filter.accept(node))
            {
                entryList.add(node);
            }
        }
        return entryList;

    }

    public void visit(final QueueEntryVisitor visitor)
    {
        QueueEntryIterator queueListIterator = _entries.iterator();

        while(queueListIterator.advance())
        {
            QueueEntry node = queueListIterator.getNode();

            if(!node.isDeleted())
            {
                if(visitor.visit(node))
                {
                    break;
                }
            }
        }
    }

    /**
     * Returns a list of QueEntries from a given range of queue positions, eg messages 5 to 10 on the queue.
     *
     * The 'queue position' index starts from 1. Using 0 in 'from' will be ignored and continue from 1.
     * Using 0 in the 'to' field will return an empty list regardless of the 'from' value.
     * @param fromPosition
     * @param toPosition
     * @return
     */
    public List<QueueEntry> getMessagesRangeOnTheQueue(final long fromPosition, final long toPosition)
    {
        return getMessagesOnTheQueue(new QueueEntryFilter()
                                        {
                                            private long position = 0;

                                            public boolean accept(QueueEntry entry)
                                            {
                                                position++;
                                                return (position >= fromPosition) && (position <= toPosition);
                                            }

                                            public boolean filterComplete()
                                            {
                                                return position >= toPosition;
                                            }
                                        });

    }

    public void purge(final long request) throws AMQException
    {
        clear(request);
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    // ------ Management functions

    // TODO - now only used by the tests
    public void deleteMessageFromTop()
    {
        QueueEntryIterator queueListIterator = _entries.iterator();
        boolean noDeletes = true;

        while (noDeletes && queueListIterator.advance())
        {
            QueueEntry node = queueListIterator.getNode();
            if (node.acquire())
            {
                dequeueEntry(node);
                noDeletes = false;
            }

        }
    }

    public long clearQueue() throws AMQException
    {
        return clear(0l);
    }

    private long clear(final long request) throws AMQSecurityException
    {
        //Perform ACLs
        if (!getVirtualHost().getSecurityManager().authorisePurge(this))
        {
            throw new AMQSecurityException("Permission denied: queue " + getName());
        }

        QueueEntryIterator queueListIterator = _entries.iterator();
        long count = 0;

        ServerTransaction txn = new LocalTransaction(getVirtualHost().getMessageStore());

        while (queueListIterator.advance())
        {
            QueueEntry node = queueListIterator.getNode();
            if (node.acquire())
            {
                dequeueEntry(node, txn);
                if(++count == request)
                {
                    break;
                }
            }

        }

        txn.commit();

        return count;
    }

    private void dequeueEntry(final QueueEntry node)
    {
        ServerTransaction txn = new AutoCommitTransaction(getVirtualHost().getMessageStore());
        dequeueEntry(node, txn);
    }

    private void dequeueEntry(final QueueEntry node, ServerTransaction txn)
    {
        txn.dequeue(this, node.getMessage(),
                    new ServerTransaction.Action()
                    {

                        public void postCommit()
                        {
                            node.delete();
                        }

                        public void onRollback()
                        {

                        }
                    });
    }

    public void addQueueDeleteTask(final Action<AMQQueue> task)
    {
        _deleteTaskList.add(task);
    }

    public void removeQueueDeleteTask(final Action<AMQQueue> task)
    {
        _deleteTaskList.remove(task);
    }

    // TODO list all thrown exceptions
    public int delete() throws AMQSecurityException, AMQException
    {
        // Check access
        if (!_virtualHost.getSecurityManager().authoriseDelete(this))
        {
            throw new AMQSecurityException("Permission denied: " + getName());
        }

        if (!_deleted.getAndSet(true))
        {

            final ArrayList<Binding> bindingCopy = new ArrayList<Binding>(_bindings);

            for (Binding b : bindingCopy)
            {
                b.getExchange().removeBinding(b);
            }

            QueueConsumerList.ConsumerNodeIterator consumerNodeIterator = _consumerList.iterator();

            while (consumerNodeIterator.advance())
            {
                QueueConsumer s = consumerNodeIterator.getNode().getConsumer();
                if (s != null)
                {
                    s.queueDeleted();
                }
            }


            List<QueueEntry> entries = getMessagesOnTheQueue(new QueueEntryFilter()
            {

                public boolean accept(QueueEntry entry)
                {
                    return entry.acquire();
                }

                public boolean filterComplete()
                {
                    return false;
                }
            });

            ServerTransaction txn = new LocalTransaction(getVirtualHost().getMessageStore());


            for(final QueueEntry entry : entries)
            {
                // TODO log requeues with a post enqueue action
                int requeues = entry.routeToAlternate(null, txn);

                if(requeues == 0)
                {
                    // TODO log discard
                }
            }

            txn.commit();

            if(_alternateExchange != null)
            {
                _alternateExchange.removeReference(this);
            }


            for (Action<AMQQueue> task : _deleteTaskList)
            {
                task.performAction(this);
            }

            _deleteTaskList.clear();
            stop();

            //Log Queue Deletion
            CurrentActor.get().message(_logSubject, QueueMessages.DELETED());

        }
        return getMessageCount();

    }

    public void stop()
    {
        if (!_stopped.getAndSet(true))
        {
            ReferenceCountingExecutorService.getInstance().releaseExecutorService();
        }
    }

    public void checkCapacity(AMQSessionModel channel)
    {
        if(_capacity != 0l)
        {
            if(_atomicQueueSize.get() > _capacity)
            {
                _overfull.set(true);
                //Overfull log message
                _logActor.message(_logSubject, QueueMessages.OVERFULL(_atomicQueueSize.get(), _capacity));

                _blockedChannels.add(channel);

                channel.block(this);

                if(_atomicQueueSize.get() <= _flowResumeCapacity)
                {

                    //Underfull log message
                    _logActor.message(_logSubject, QueueMessages.UNDERFULL(_atomicQueueSize.get(), _flowResumeCapacity));

                   channel.unblock(this);
                   _blockedChannels.remove(channel);

                }

            }



        }
    }

    private void checkCapacity()
    {
        if(_capacity != 0L)
        {
            if(_overfull.get() && _atomicQueueSize.get() <= _flowResumeCapacity)
            {
                if(_overfull.compareAndSet(true,false))
                {//Underfull log message
                    _logActor.message(_logSubject, QueueMessages.UNDERFULL(_atomicQueueSize.get(), _flowResumeCapacity));
                }

                for(final AMQSessionModel blockedChannel : _blockedChannels)
                {
                    blockedChannel.unblock(this);
                    _blockedChannels.remove(blockedChannel);
                }
            }
        }
    }

    private QueueRunner _queueRunner = new QueueRunner(this);

    public void deliverAsync()
    {
        _stateChangeCount.incrementAndGet();

        _queueRunner.execute(_asyncDelivery);

    }

    public void deliverAsync(QueueConsumer sub)
    {
        if(_exclusiveSubscriber == null)
        {
            deliverAsync();
        }
        else
        {
            SubFlushRunner flusher = sub.getRunner();
            flusher.execute(_asyncDelivery);
        }

    }

    void flushConsumer(QueueConsumer sub) throws AMQException
    {
        // Access control
        if (!getVirtualHost().getSecurityManager().authoriseConsume(this))
        {
            throw new AMQSecurityException("Permission denied: " + getName());
        }
        flushConsumer(sub, Long.MAX_VALUE);
    }

    boolean flushConsumer(QueueConsumer sub, long iterations) throws AMQException
    {
        boolean atTail = false;
        final boolean keepSendLockHeld = iterations <=  SimpleAMQQueue.MAX_ASYNC_DELIVERIES;
        boolean queueEmpty = false;

        try
        {
            if(keepSendLockHeld)
            {
                sub.getSendLock();
            }
            while (!sub.isSuspended() && !atTail && iterations != 0)
            {
                try
                {
                    if(!keepSendLockHeld)
                    {
                        sub.getSendLock();
                    }

                    atTail = attemptDelivery((QueueConsumer)sub, true);
                    if (atTail && getNextAvailableEntry((QueueConsumer)sub) == null)
                    {
                        queueEmpty = true;
                    }
                    else if (!atTail)
                    {
                        iterations--;
                    }
                }
                finally
                {
                    if(!keepSendLockHeld)
                    {
                        sub.releaseSendLock();
                    }
                }
            }
        }
        finally
        {
            if(keepSendLockHeld)
            {
                sub.releaseSendLock();
            }
            if(queueEmpty)
            {
                sub.queueEmpty();
            }

            sub.flushBatched();

        }


        // if there's (potentially) more than one consumer the others will potentially not have been advanced to the
        // next entry they are interested in yet.  This would lead to holding on to references to expired messages, etc
        // which would give us memory "leak".

        if (!hasExclusiveConsumer())
        {
            advanceAllConsumers();
        }
        return atTail;
    }

    /**
     * Attempt delivery for the given consumer.
     *
     * Looks up the next node for the consumer and attempts to deliver it.
     *
     *
     * @param sub
     * @param batch
     * @return true if we have completed all possible deliveries for this sub.
     * @throws AMQException
     */
    private boolean attemptDelivery(QueueConsumer sub, boolean batch) throws AMQException
    {
        boolean atTail = false;

        boolean subActive = sub.isActive() && !sub.isSuspended();
        if (subActive)
        {

            QueueEntry node  = getNextAvailableEntry(sub);

            if (node != null && node.isAvailable())
            {
                if (sub.hasInterest(node) && mightAssign(sub, node))
                {
                    if (!sub.wouldSuspend(node))
                    {
                        if (sub.acquires() && !assign(sub, node))
                        {
                            // restore credit here that would have been taken away by wouldSuspend since we didn't manage
                            // to acquire the entry for this consumer
                            sub.restoreCredit(node);
                        }
                        else
                        {
                            deliverMessage(sub, node, batch);
                        }

                    }
                    else // Not enough Credit for message and wouldSuspend
                    {
                        //QPID-1187 - Treat the consumer as suspended for this message
                        // and wait for the message to be removed to continue delivery.
                        subActive = false;
                        node.addStateChangeListener(new QueueEntryListener(sub));
                    }
                }

            }
            atTail = (node == null) || (_entries.next(node) == null);
        }
        return atTail || !subActive;
    }

    protected void advanceAllConsumers() throws AMQException
    {
        QueueConsumerList.ConsumerNodeIterator consumerNodeIterator = _consumerList.iterator();
        while (consumerNodeIterator.advance())
        {
            QueueConsumerList.ConsumerNode subNode = consumerNodeIterator.getNode();
            QueueConsumer sub = subNode.getConsumer();
            if(sub.acquires())
            {
                getNextAvailableEntry(sub);
            }
            else
            {
                // TODO
            }
        }
    }

    private QueueEntry getNextAvailableEntry(final QueueConsumer sub)
            throws AMQException
    {
        QueueContext context = sub.getQueueContext();
        if(context != null)
        {
            QueueEntry lastSeen = context.getLastSeenEntry();
            QueueEntry releasedNode = context.getReleasedEntry();

            QueueEntry node = (releasedNode != null && lastSeen.compareTo(releasedNode)>=0) ? releasedNode : _entries.next(lastSeen);

            boolean expired = false;
            while (node != null && (!node.isAvailable() || (expired = node.expired()) || !sub.hasInterest(node) ||
                                    !mightAssign(sub,node)))
            {
                if (expired)
                {
                    expired = false;
                    if (node.acquire())
                    {
                        dequeueEntry(node);
                    }
                }

                if(QueueContext._lastSeenUpdater.compareAndSet(context, lastSeen, node))
                {
                    QueueContext._releasedUpdater.compareAndSet(context, releasedNode, null);
                }

                lastSeen = context.getLastSeenEntry();
                releasedNode = context.getReleasedEntry();
                node = (releasedNode != null && lastSeen.compareTo(releasedNode)>0) ? releasedNode : _entries.next(lastSeen);
            }
            return node;
        }
        else
        {
            return null;
        }
    }

    public boolean isEntryAheadOfConsumer(QueueEntry entry, QueueConsumer sub)
    {
        QueueContext context = sub.getQueueContext();
        if(context != null)
        {
            QueueEntry releasedNode = context.getReleasedEntry();
            return releasedNode != null && releasedNode.compareTo(entry) < 0;
        }
        else
        {
            return false;
        }
    }

    /**
     * Used by queue Runners to asynchronously deliver messages to consumers.
     *
     * A queue Runner is started whenever a state change occurs, e.g when a new
     * message arrives on the queue and cannot be immediately delivered to a
     * consumer (i.e. asynchronous delivery is required). Unless there are
     * SubFlushRunners operating (due to consumers unsuspending) which are
     * capable of accepting/delivering all messages then these messages would
     * otherwise remain on the queue.
     *
     * processQueue should be running while there are messages on the queue AND
     * there are consumers that can deliver them. If there are no
     * consumers capable of delivering the remaining messages on the queue
     * then processQueue should stop to prevent spinning.
     *
     * Since processQueue is runs in a fixed size Executor, it should not run
     * indefinitely to prevent starving other tasks of CPU (e.g jobs to process
     * incoming messages may not be able to be scheduled in the thread pool
     * because all threads are working on clearing down large queues). To solve
     * this problem, after an arbitrary number of message deliveries the
     * processQueue job stops iterating, resubmits itself to the executor, and
     * ends the current instance
     *
     * @param runner the Runner to schedule
     * @throws AMQException
     */
    public long processQueue(QueueRunner runner) throws AMQException
    {
        long stateChangeCount = Long.MIN_VALUE;
        long previousStateChangeCount = Long.MIN_VALUE;
        long rVal = Long.MIN_VALUE;
        boolean deliveryIncomplete = true;

        boolean lastLoop = false;
        int iterations = MAX_ASYNC_DELIVERIES;

        final int numSubs = _consumerList.size();

        final int perSub = Math.max(iterations / Math.max(numSubs,1), 1);

        // For every message enqueue/requeue the we fire deliveryAsync() which
        // increases _stateChangeCount. If _sCC changes whilst we are in our loop
        // (detected by setting previousStateChangeCount to stateChangeCount in the loop body)
        // then we will continue to run for a maximum of iterations.
        // So whilst delivery/rejection is going on a processQueue thread will be running
        while (iterations != 0 && ((previousStateChangeCount != (stateChangeCount = _stateChangeCount.get())) || deliveryIncomplete))
        {
            // we want to have one extra loop after every consumer has reached the point where it cannot move
            // further, just in case the advance of one consumer in the last loop allows a different consumer to
            // move forward in the next iteration

            if (previousStateChangeCount != stateChangeCount)
            {
                //further asynchronous delivery is required since the
                //previous loop. keep going if iteration slicing allows.
                lastLoop = false;
                rVal = stateChangeCount;
            }

            previousStateChangeCount = stateChangeCount;
            boolean allConsumersDone = true;
            boolean consumerDone;

            QueueConsumerList.ConsumerNodeIterator consumerNodeIterator = _consumerList.iterator();
            //iterate over the subscribers and try to advance their pointer
            while (consumerNodeIterator.advance())
            {
                QueueConsumer sub = consumerNodeIterator.getNode().getConsumer();
                sub.getSendLock();

                    try
                    {
                        for(int i = 0 ; i < perSub; i++)
                        {
                            //attempt delivery. returns true if no further delivery currently possible to this sub
                            consumerDone = attemptDelivery(sub, true);
                            if (consumerDone)
                            {
                                sub.flushBatched();
                                if (lastLoop && !sub.isSuspended())
                                {
                                    sub.queueEmpty();
                                }
                                break;
                            }
                            else
                            {
                                //this consumer can accept additional deliveries, so we must
                                //keep going after this (if iteration slicing allows it)
                                allConsumersDone = false;
                                lastLoop = false;
                                if(--iterations == 0)
                                {
                                    sub.flushBatched();
                                    break;
                                }
                            }

                        }

                        sub.flushBatched();
                    }
                    finally
                    {
                        sub.releaseSendLock();
                    }
            }

            if(allConsumersDone && lastLoop)
            {
                //We have done an extra loop already and there are again
                //again no further delivery attempts possible, only
                //keep going if state change demands it.
                deliveryIncomplete = false;
            }
            else if(allConsumersDone)
            {
                //All consumers reported being done, but we have to do
                //an extra loop if the iterations are not exhausted and
                //there is still any work to be done
                deliveryIncomplete = _consumerList.size() != 0;
                lastLoop = true;
            }
            else
            {
                //some consumers can still accept more messages,
                //keep going if iteration count allows.
                lastLoop = false;
                deliveryIncomplete = true;
            }

        }

        // If iterations == 0 then the limiting factor was the time-slicing rather than available messages or credit
        // therefore we should schedule this runner again (unless someone beats us to it :-) ).
        if (iterations == 0)
        {
            if (_logger.isDebugEnabled())
            {
                _logger.debug("Rescheduling runner:" + runner);
            }
            return 0L;
        }
        return rVal;

    }

    public void checkMessageStatus() throws AMQException
    {
        QueueEntryIterator queueListIterator = _entries.iterator();

        while (queueListIterator.advance())
        {
            QueueEntry node = queueListIterator.getNode();
            // Only process nodes that are not currently deleted and not dequeued
            if (!node.isDeleted())
            {
                // If the node has expired then acquire it
                if (node.expired() && node.acquire())
                {
                    if (_logger.isDebugEnabled())
                    {
                        _logger.debug("Dequeuing expired node " + node);
                    }
                    // Then dequeue it.
                    dequeueEntry(node);
                }
                else
                {
                    // There is a chance that the node could be deleted by
                    // the time the check actually occurs. So verify we
                    // can actually get the message to perform the check.
                    ServerMessage msg = node.getMessage();
                    if (msg != null)
                    {
                        checkForNotification(msg);
                    }
                }
            }
        }

    }

    public long getMinimumAlertRepeatGap()
    {
        return _minimumAlertRepeatGap;
    }

    public void setMinimumAlertRepeatGap(long minimumAlertRepeatGap)
    {
        _minimumAlertRepeatGap = minimumAlertRepeatGap;
    }

    public long getMaximumMessageAge()
    {
        return _maximumMessageAge;
    }

    public void setMaximumMessageAge(long maximumMessageAge)
    {
        _maximumMessageAge = maximumMessageAge;
        if (maximumMessageAge == 0L)
        {
            _notificationChecks.remove(NotificationCheck.MESSAGE_AGE_ALERT);
        }
        else
        {
            _notificationChecks.add(NotificationCheck.MESSAGE_AGE_ALERT);
        }
    }

    public long getMaximumMessageCount()
    {
        return _maximumMessageCount;
    }

    public void setMaximumMessageCount(final long maximumMessageCount)
    {
        _maximumMessageCount = maximumMessageCount;
        if (maximumMessageCount == 0L)
        {
            _notificationChecks.remove(NotificationCheck.MESSAGE_COUNT_ALERT);
        }
        else
        {
            _notificationChecks.add(NotificationCheck.MESSAGE_COUNT_ALERT);
        }

    }

    public long getMaximumQueueDepth()
    {
        return _maximumQueueDepth;
    }

    // Sets the queue depth, the max queue size
    public void setMaximumQueueDepth(final long maximumQueueDepth)
    {
        _maximumQueueDepth = maximumQueueDepth;
        if (maximumQueueDepth == 0L)
        {
            _notificationChecks.remove(NotificationCheck.QUEUE_DEPTH_ALERT);
        }
        else
        {
            _notificationChecks.add(NotificationCheck.QUEUE_DEPTH_ALERT);
        }

    }

    public long getMaximumMessageSize()
    {
        return _maximumMessageSize;
    }

    public void setMaximumMessageSize(final long maximumMessageSize)
    {
        _maximumMessageSize = maximumMessageSize;
        if (maximumMessageSize == 0L)
        {
            _notificationChecks.remove(NotificationCheck.MESSAGE_SIZE_ALERT);
        }
        else
        {
            _notificationChecks.add(NotificationCheck.MESSAGE_SIZE_ALERT);
        }
    }

    public long getCapacity()
    {
        return _capacity;
    }

    public void setCapacity(long capacity)
    {
        _capacity = capacity;
    }

    public long getFlowResumeCapacity()
    {
        return _flowResumeCapacity;
    }

    public void setFlowResumeCapacity(long flowResumeCapacity)
    {
        _flowResumeCapacity = flowResumeCapacity;

        checkCapacity();
    }

    public boolean isOverfull()
    {
        return _overfull.get();
    }

    public Set<NotificationCheck> getNotificationChecks()
    {
        return _notificationChecks;
    }

    private final class QueueEntryListener implements StateChangeListener<MessageInstance<QueueConsumer>, QueueEntry.State>
    {

        private final QueueConsumer _sub;

        public QueueEntryListener(final QueueConsumer sub)
        {
            _sub = sub;
        }

        public boolean equals(Object o)
        {
            return o instanceof SimpleAMQQueue.QueueEntryListener
                    && _sub == ((QueueEntryListener) o)._sub;
        }

        public int hashCode()
        {
            return System.identityHashCode(_sub);
        }

        public void stateChanged(MessageInstance entry, QueueEntry.State oldSate, QueueEntry.State newState)
        {
            entry.removeStateChangeListener(this);
            deliverAsync(_sub);
        }
    }

    public List<Long> getMessagesOnTheQueue(int num)
    {
        return getMessagesOnTheQueue(num, 0);
    }

    public List<Long> getMessagesOnTheQueue(int num, int offset)
    {
        ArrayList<Long> ids = new ArrayList<Long>(num);
        QueueEntryIterator it = _entries.iterator();
        for (int i = 0; i < offset; i++)
        {
            it.advance();
        }

        for (int i = 0; i < num && !it.atTail(); i++)
        {
            it.advance();
            ids.add(it.getNode().getMessage().getMessageNumber());
        }
        return ids;
    }

    public AMQSessionModel getExclusiveOwningSession()
    {
        return _exclusiveOwner;
    }

    public void setExclusiveOwningSession(AMQSessionModel exclusiveOwner)
    {
        _exclusive = true;
        _exclusiveOwner = exclusiveOwner;
    }


    public void configure(QueueConfiguration config)
    {
        if (config != null)
        {
            setMaximumMessageAge(config.getMaximumMessageAge());
            setMaximumQueueDepth(config.getMaximumQueueDepth());
            setMaximumMessageSize(config.getMaximumMessageSize());
            setMaximumMessageCount(config.getMaximumMessageCount());
            setMinimumAlertRepeatGap(config.getMinimumAlertRepeatGap());
            setMaximumDeliveryCount(config.getMaxDeliveryCount());
            _capacity = config.getCapacity();
            _flowResumeCapacity = config.getFlowResumeCapacity();
        }
    }

    public long getMessageDequeueCount()
    {
        return  _dequeueCount.get();
    }

    public long getTotalEnqueueSize()
    {
        return _enqueueSize.get();
    }

    public long getTotalDequeueSize()
    {
        return _dequeueSize.get();
    }

    public long getPersistentByteEnqueues()
    {
        return _persistentMessageEnqueueSize.get();
    }

    public long getPersistentByteDequeues()
    {
        return _persistentMessageDequeueSize.get();
    }

    public long getPersistentMsgEnqueues()
    {
        return _persistentMessageEnqueueCount.get();
    }

    public long getPersistentMsgDequeues()
    {
        return _persistentMessageDequeueCount.get();
    }


    @Override
    public String toString()
    {
        return getName();
    }

    public long getUnackedMessageCount()
    {
        return _unackedMsgCount.get();
    }

    public long getUnackedMessageBytes()
    {
        return _unackedMsgBytes.get();
    }

    public void decrementUnackedMsgCount(QueueEntry queueEntry)
    {
        _unackedMsgCount.decrementAndGet();
        _unackedMsgBytes.addAndGet(-queueEntry.getSize());
    }

    private void incrementUnackedMsgCount(QueueEntry entry)
    {
        _unackedMsgCount.incrementAndGet();
        _unackedMsgBytes.addAndGet(entry.getSize());
    }

    public LogActor getLogActor()
    {
        return _logActor;
    }

    public int getMaximumDeliveryCount()
    {
        return _maximumDeliveryCount;
    }

    public void setMaximumDeliveryCount(final int maximumDeliveryCount)
    {
        _maximumDeliveryCount = maximumDeliveryCount;
    }

    /**
     * Checks if there is any notification to send to the listeners
     */
    private void checkForNotification(ServerMessage<?> msg) throws AMQException
    {
        final Set<NotificationCheck> notificationChecks = getNotificationChecks();
        final AMQQueue.NotificationListener listener = _notificationListener;

        if(listener != null && !notificationChecks.isEmpty())
        {
            final long currentTime = System.currentTimeMillis();
            final long thresholdTime = currentTime - getMinimumAlertRepeatGap();

            for (NotificationCheck check : notificationChecks)
            {
                if (check.isMessageSpecific() || (_lastNotificationTimes[check.ordinal()] < thresholdTime))
                {
                    if (check.notifyIfNecessary(msg, this, listener))
                    {
                        _lastNotificationTimes[check.ordinal()] = currentTime;
                    }
                }
            }
        }
    }

    public void setNotificationListener(AMQQueue.NotificationListener listener)
    {
        _notificationListener = listener;
    }

    @Override
    public void setDescription(String description)
    {
        if (description == null)
        {
            _arguments.remove(Queue.DESCRIPTION);
        }
        else
        {
            _arguments.put(Queue.DESCRIPTION, description);
        }
    }

    @Override
    public String getDescription()
    {
        return (String) _arguments.get(Queue.DESCRIPTION);
    }

    public final int send(final ServerMessage message,
                              final InstanceProperties instanceProperties,
                              final ServerTransaction txn,
                              final Action<MessageInstance<? extends Consumer>> postEnqueueAction)
    {
            txn.enqueue(this,message, new ServerTransaction.Action()
            {
                MessageReference _reference = message.newReference();

                public void postCommit()
                {
                    try
                    {
                        SimpleAMQQueue.this.enqueue(message, postEnqueueAction);
                    }
                    catch (AMQException e)
                    {
                        // TODO
                        throw new RuntimeException(e);
                    }
                    finally
                    {
                        _reference.release();
                    }
                }

                public void onRollback()
                {
                    _reference.release();
                }
            });
            return 1;

    }

}
