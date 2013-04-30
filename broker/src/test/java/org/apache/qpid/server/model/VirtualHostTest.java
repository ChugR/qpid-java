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
package org.apache.qpid.server.model;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.configuration.ConfigurationEntry;
import org.apache.qpid.server.configuration.RecovererProvider;
import org.apache.qpid.server.configuration.startup.VirtualHostRecoverer;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.SystemOutMessageLogger;
import org.apache.qpid.server.logging.actors.CurrentActor;
import org.apache.qpid.server.logging.actors.TestLogActor;
import org.apache.qpid.server.stats.StatisticsGatherer;
import org.apache.qpid.server.store.Event;
import org.apache.qpid.server.store.EventListener;
import org.apache.qpid.server.store.EventManager;
import org.apache.qpid.server.store.MemoryMessageStore;
import org.apache.qpid.server.store.NullMessageStore;
import org.apache.qpid.server.store.StateManager;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.TestFileUtils;

public class VirtualHostTest extends TestCase
{

    private Broker _broker;
    private StatisticsGatherer _statisticsGatherer;
    private RecovererProvider _recovererProvider;

    @Override
    protected void setUp() throws Exception
    {
        super.setUp();
        CurrentActor.set(new TestLogActor(new SystemOutMessageLogger()));

        _broker = BrokerTestHelper.createBrokerMock();
        TaskExecutor taslExecutor = mock(TaskExecutor.class);
        when(taslExecutor.isTaskExecutorThread()).thenReturn(true);
        when(_broker.getTaskExecutor()).thenReturn(taslExecutor);

        _recovererProvider = mock(RecovererProvider.class);
        _statisticsGatherer = mock(StatisticsGatherer.class);
    }

    @Override
    protected void tearDown() throws Exception
    {
        super.tearDown();
        CurrentActor.remove();
    }

    public void testInitialisingState()
    {
        VirtualHost host = createHost();

        assertEquals("Unexpected state", State.INITIALISING, host.getAttribute(VirtualHost.STATE));
    }

    public void testActiveState()
    {
        VirtualHost host = createHost();

        host.setDesiredState(State.INITIALISING, State.ACTIVE);
        assertEquals("Unexpected state", State.ACTIVE, host.getAttribute(VirtualHost.STATE));
    }

    public void testQuiescedState()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(VirtualHost.NAME, getName());
        attributes.put(VirtualHost.STORE_TYPE, MemoryMessageStore.TYPE);
        attributes.put(VirtualHost.STATE, State.QUIESCED);

        VirtualHost host = createHost(attributes);

        assertEquals("Unexpected state", State.QUIESCED, host.getAttribute(VirtualHost.STATE));

        host.setDesiredState(State.QUIESCED, State.ACTIVE);
        assertEquals("Unexpected state", State.ACTIVE, host.getAttribute(VirtualHost.STATE));
    }

    public void testStoppedState()
    {
        VirtualHost host = createHost();

        assertEquals("Unexpected state", State.INITIALISING, host.getAttribute(VirtualHost.STATE));

        host.setDesiredState(State.INITIALISING, State.ACTIVE);
        assertEquals("Unexpected state", State.ACTIVE, host.getAttribute(VirtualHost.STATE));

        host.setDesiredState(State.ACTIVE, State.STOPPED);
        assertEquals("Unexpected state", State.STOPPED, host.getAttribute(VirtualHost.STATE));
    }

    public void testDeletedState()
    {
        VirtualHost host = createHost();

        assertEquals("Unexpected state", State.INITIALISING, host.getAttribute(VirtualHost.STATE));

        host.setDesiredState(State.INITIALISING, State.DELETED);
        assertEquals("Unexpected state", State.DELETED, host.getAttribute(VirtualHost.STATE));
    }

    public void testReplicaState()
    {
        File configPath = TestFileUtils.createTempFile(this, ".xml",
                "<virtualhost><store><class>" + ReplicaMessageStore.class.getName() + "</class></store></virtualhost>");
        try
        {
            Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put(VirtualHost.NAME, getName());
            attributes.put(VirtualHost.CONFIG_PATH, configPath.getAbsolutePath());

            VirtualHost host = createHost(attributes);

            assertEquals("Unexpected state", State.INITIALISING, host.getAttribute(VirtualHost.STATE));

            host.setDesiredState(State.INITIALISING, State.ACTIVE);

            assertEquals("Unexpected state", State.REPLICA, host.getAttribute(VirtualHost.STATE));
        }
        finally
        {
            configPath.delete();
        }
    }

    private VirtualHost createHost()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(VirtualHost.NAME, getName());
        attributes.put(VirtualHost.STORE_TYPE, MemoryMessageStore.TYPE);

        VirtualHost host = createHost(attributes);
        return host;
    }

    private VirtualHost createHost(Map<String, Object> attributes)
    {
        ConfigurationEntry entry = new ConfigurationEntry(UUID.randomUUID(), VirtualHost.class.getSimpleName(), attributes,
                Collections.<UUID> emptySet(), null);

        return new VirtualHostRecoverer(_statisticsGatherer).create(_recovererProvider, entry, _broker);
    }

    public static final class ReplicaMessageStore extends NullMessageStore
    {
        private final EventManager _eventManager = new EventManager();
        private final StateManager _stateManager = new StateManager(_eventManager);

        @Override
        public void activate() throws Exception
        {
            _stateManager.attainState(org.apache.qpid.server.store.State.INITIALISING);
            _stateManager.attainState(org.apache.qpid.server.store.State.INITIALISED);
            _stateManager.attainState(org.apache.qpid.server.store.State.ACTIVATING);
            _stateManager.attainState(org.apache.qpid.server.store.State.ACTIVE);

            // this should change the virtual host state to PASSIVE
            _stateManager.attainState(org.apache.qpid.server.store.State.INITIALISED);
        }

        @Override
        public void addEventListener(EventListener eventListener, Event... events)
        {
            _eventManager.addEventListener(eventListener, events);
        }

        @Override
        public String getStoreType()
        {
            return ReplicaMessageStore.class.getSimpleName();
        }
    }

}
