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
package org.apache.qpid.server.util;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.SocketAddress;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.security.auth.Subject;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.store.JsonConfigurationEntryStore;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.exchange.ExchangeImpl;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObjectFactory;
import org.apache.qpid.server.model.ConfiguredObjectFactoryImpl;
import org.apache.qpid.server.model.Exchange;
import org.apache.qpid.server.model.Queue;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.model.SystemContextImpl;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.protocol.AMQConnectionModel;
import org.apache.qpid.server.protocol.AMQSessionModel;
import org.apache.qpid.server.queue.AMQQueue;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.server.store.DurableConfigurationStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.TestMemoryMessageStore;
import org.apache.qpid.server.virtualhost.AbstractVirtualHost;
import org.apache.qpid.server.virtualhost.QueueExistsException;
import org.apache.qpid.server.virtualhost.StandardVirtualHost;
import org.apache.qpid.server.virtualhost.VirtualHostImpl;

public class BrokerTestHelper
{

    protected static final String BROKER_STORE_CLASS_NAME_KEY = "brokerstore.class.name";
    protected static final String JSON_BROKER_STORE_CLASS_NAME = JsonConfigurationEntryStore.class.getName();

    private static final TaskExecutor TASK_EXECUTOR = new TaskExecutor();
    static
    {
        TASK_EXECUTOR.start();
    }

    public static Broker<?> createBrokerMock()
    {
        ConfiguredObjectFactory objectFactory = new ConfiguredObjectFactoryImpl(BrokerModel.getInstance());
        EventLogger eventLogger = new EventLogger();

        SystemContext systemContext = mock(SystemContext.class);
        when(systemContext.getEventLogger()).thenReturn(eventLogger);
        when(systemContext.getObjectFactory()).thenReturn(objectFactory);
        when(systemContext.getModel()).thenReturn(objectFactory.getModel());
        when(systemContext.getCategoryClass()).thenReturn(SystemContext.class);

        SubjectCreator subjectCreator = mock(SubjectCreator.class);
        when(subjectCreator.getMechanisms()).thenReturn("");

        Broker broker = mock(Broker.class);
        when(broker.getConnection_sessionCountLimit()).thenReturn(1);
        when(broker.getConnection_closeWhenNoRoute()).thenReturn(false);
        when(broker.getId()).thenReturn(UUID.randomUUID());
        when(broker.getSubjectCreator(any(SocketAddress.class))).thenReturn(subjectCreator);
        when(broker.getSecurityManager()).thenReturn(new SecurityManager(broker, false));
        when(broker.getObjectFactory()).thenReturn(objectFactory);
        when(broker.getModel()).thenReturn(objectFactory.getModel());
        when(broker.getEventLogger()).thenReturn(eventLogger);
        when(broker.getCategoryClass()).thenReturn(Broker.class);
        when(broker.getParent(SystemContext.class)).thenReturn(systemContext);

        return broker;
    }

    public static void setUp()
    {
    }

    public static void tearDown()
    {
    }

    public static VirtualHostImpl<?,?,?> createVirtualHost(Map<String, Object> attributes)
            throws Exception
    {

        //VirtualHostFactory factory = new PluggableFactoryLoader<VirtualHostFactory>(VirtualHostFactory.class).get(hostType);
        Broker<?> broker = createBrokerMock();
        ConfiguredObjectFactory objectFactory = broker.getObjectFactory();
        SystemContext systemContext = new SystemContextImpl(TASK_EXECUTOR,
                                                            objectFactory,
                                                            mock(EventLogger.class),
                                                            mock(LogRecorder.class),
                                                            new BrokerOptions());
        when(broker.getTaskExecutor()).thenReturn(TASK_EXECUTOR);

        VirtualHostNode<?> virtualHostNode = mock(VirtualHostNode.class);
        DurableConfigurationStore dcs = mock(DurableConfigurationStore.class);
        when(virtualHostNode.getConfigurationStore()).thenReturn(dcs);
        when(virtualHostNode.getParent(Broker.class)).thenReturn(broker);
        when(virtualHostNode.getModel()).thenReturn(objectFactory.getModel());
        when(virtualHostNode.getObjectFactory()).thenReturn(objectFactory);
        AbstractVirtualHost host = (AbstractVirtualHost) objectFactory.create(VirtualHost.class, attributes, virtualHostNode );
        host.setDesiredState(host.getState(), State.ACTIVE);

        return host;
    }

    public static VirtualHostImpl<?,?,?> createVirtualHost(String name) throws Exception
    {
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(org.apache.qpid.server.model.VirtualHost.TYPE, StandardVirtualHost.TYPE);

        Map<String, Object> messageStoreSettings = new HashMap<String, Object>();
        messageStoreSettings.put(MessageStore.STORE_TYPE, TestMemoryMessageStore.TYPE);

        attributes.put(org.apache.qpid.server.model.VirtualHost.MESSAGE_STORE_SETTINGS, messageStoreSettings);
        attributes.put(org.apache.qpid.server.model.VirtualHost.NAME, name);

        return createVirtualHost(attributes);
    }

    public static AMQSessionModel<?,?> createSession(int channelId, AMQConnectionModel<?,?> connection)
    {
        @SuppressWarnings("rawtypes")
        AMQSessionModel session = mock(AMQSessionModel.class);
        when(session.getConnectionModel()).thenReturn(connection);
        when(session.getChannelId()).thenReturn(channelId);
        return session;
    }

    public static AMQSessionModel<?,?> createSession(int channelId) throws Exception
    {
        AMQConnectionModel<?,?> session = createConnection();
        return createSession(channelId, session);
    }

    public static AMQSessionModel<?,?> createSession() throws Exception
    {
        return createSession(1);
    }

    public static AMQConnectionModel<?,?> createConnection() throws Exception
    {
        return createConnection("test");
    }

    public static AMQConnectionModel<?,?> createConnection(String hostName) throws Exception
    {
        return mock(AMQConnectionModel.class);
    }

    public static ExchangeImpl<?> createExchange(String hostName, final boolean durable, final EventLogger eventLogger) throws Exception
    {
        SecurityManager securityManager = new SecurityManager(mock(Broker.class), false);
        final VirtualHostImpl<?,?,?> virtualHost = mock(VirtualHostImpl.class);
        when(virtualHost.getName()).thenReturn(hostName);
        when(virtualHost.getSecurityManager()).thenReturn(securityManager);
        when(virtualHost.getEventLogger()).thenReturn(eventLogger);
        when(virtualHost.getDurableConfigurationStore()).thenReturn(mock(DurableConfigurationStore.class));
        final ConfiguredObjectFactory objectFactory = new ConfiguredObjectFactoryImpl(BrokerModel.getInstance());
        when(virtualHost.getObjectFactory()).thenReturn(objectFactory);
        when(virtualHost.getModel()).thenReturn(objectFactory.getModel());
        final Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(org.apache.qpid.server.model.Exchange.ID, UUIDGenerator.generateExchangeUUID("amp.direct", virtualHost.getName()));
        attributes.put(org.apache.qpid.server.model.Exchange.NAME, "amq.direct");
        attributes.put(org.apache.qpid.server.model.Exchange.TYPE, "direct");
        attributes.put(org.apache.qpid.server.model.Exchange.DURABLE, durable);
        return Subject.doAs(SecurityManager.getSubjectWithAddedSystemRights(), new PrivilegedAction<ExchangeImpl>()
        {
            @Override
            public ExchangeImpl run()
            {

                return (ExchangeImpl) objectFactory.create(Exchange.class, attributes, virtualHost);
            }
        });

    }

    public static AMQQueue<?> createQueue(String queueName, VirtualHostImpl<?,?,?> virtualHost)
            throws QueueExistsException
    {
        Map<String,Object> attributes = new HashMap<String, Object>();
        attributes.put(Queue.ID, UUIDGenerator.generateRandomUUID());
        attributes.put(Queue.NAME, queueName);
        AMQQueue<?> queue = virtualHost.createQueue(attributes);
        return queue;
    }

}
