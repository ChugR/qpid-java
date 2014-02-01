/*
*
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
*
*/
package org.apache.qpid.server.queue;

import org.apache.qpid.AMQException;
import org.apache.qpid.server.filter.Filterable;
import org.apache.qpid.server.message.MessageInstance;
import org.apache.qpid.server.subscription.Subscription;
import org.apache.qpid.server.txn.ServerTransaction;

public interface QueueEntry extends MessageInstance, Comparable<QueueEntry>
{



    public static enum State
    {
        AVAILABLE,
        ACQUIRED,
        EXPIRED,
        DEQUEUED,
        DELETED;


    }

    public static interface StateChangeListener
    {
        public void stateChanged(QueueEntry entry, State oldSate, State newState);
    }

    public abstract class EntryState
    {
        private EntryState()
        {
        }

        public abstract State getState();

        /**
         * Returns true if state is either DEQUEUED or DELETED.
         *
         * @return true if state is either DEQUEUED or DELETED.
         */
        public boolean isDispensed()
        {
            State currentState = getState();
            return currentState == State.DEQUEUED || currentState == State.DELETED;
        }
    }


    public final class AvailableState extends EntryState
    {

        public State getState()
        {
            return State.AVAILABLE;
        }

        public String toString()
        {
            return getState().name();
        }
    }


    public final class DequeuedState extends EntryState
    {

        public State getState()
        {
            return State.DEQUEUED;
        }

        public String toString()
        {
            return getState().name();
        }
    }


    public final class DeletedState extends EntryState
    {

        public State getState()
        {
            return State.DELETED;
        }

        public String toString()
        {
            return getState().name();
        }
    }

    public final class ExpiredState extends EntryState
    {

        public State getState()
        {
            return State.EXPIRED;
        }

        public String toString()
        {
            return getState().name();
        }
    }


    public final class NonSubscriptionAcquiredState extends EntryState
    {
        public State getState()
        {
            return State.ACQUIRED;
        }

        public String toString()
        {
            return getState().name();
        }
    }

    public final class SubscriptionAcquiredState extends EntryState
    {
        private final Subscription _subscription;

        public SubscriptionAcquiredState(Subscription subscription)
        {
            _subscription = subscription;
        }


        public State getState()
        {
            return State.ACQUIRED;
        }

        public Subscription getSubscription()
        {
            return _subscription;
        }

        public String toString()
        {
            return "{" + getState().name() + " : " + _subscription +"}";
        }
    }


    final static EntryState AVAILABLE_STATE = new AvailableState();
    final static EntryState DELETED_STATE = new DeletedState();
    final static EntryState DEQUEUED_STATE = new DequeuedState();
    final static EntryState NON_SUBSCRIPTION_ACQUIRED_STATE = new NonSubscriptionAcquiredState();




    AMQQueue getQueue();

    long getSize();

    boolean getDeliveredToConsumer();

    boolean expired() throws AMQException;

    boolean acquire(Subscription sub);

    boolean acquiredBySubscription();
    boolean isAcquiredBy(Subscription subscription);

    void setRedelivered();

    boolean isRedelivered();

    Subscription getDeliveredSubscription();

    void reject();

    boolean isRejectedBy(long subscriptionId);

    int routeToAlternate(final BaseQueue.PostEnqueueAction action, ServerTransaction txn);

    boolean isQueueDeleted();

    QueueEntry getNextNode();

    QueueEntry getNextValidEntry();

    void addStateChangeListener(StateChangeListener listener);
    boolean removeStateChangeListener(StateChangeListener listener);


    /**
     * Number of times this queue entry has been delivered.
     *
     * @return delivery count
     */
    int getDeliveryCount();

    void incrementDeliveryCount();

    void decrementDeliveryCount();

    Filterable asFilterable();

}
