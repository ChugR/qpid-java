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
package org.apache.qpid.server.logging;

import static org.apache.qpid.server.util.LoggerTestHelper.assertLoggedEvent;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutorImpl;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.BrokerLogInclusionRule;
import org.apache.qpid.server.model.BrokerModel;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Model;
import org.apache.qpid.server.model.State;
import org.apache.qpid.test.utils.QpidTestCase;

public class BrokerLoggerTest extends QpidTestCase
{
    public static final String APPENDER_NAME = "test";

    private AbstractBrokerLogger<?> _brokerLogger;
    private ListAppender _loggerAppender;
    private TaskExecutor _taskExecutor;
    private Broker<?> _broker;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();

        _taskExecutor = new TaskExecutorImpl();
        _taskExecutor.start();

        _loggerAppender = new ListAppender<>();
        _loggerAppender.setName(APPENDER_NAME);


        Model model = BrokerModel.getInstance();

        _broker = mock(Broker.class);
        when(_broker.getModel()).thenReturn(model);
        when(_broker.getChildExecutor()).thenReturn(_taskExecutor);
        doReturn(Broker.class).when(_broker).getCategoryClass();

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("name", APPENDER_NAME);
        _brokerLogger = new AbstractBrokerLogger(attributes, _broker)
        {
            @Override
            public Appender<ILoggingEvent> createAppenderInstance(Context context)
            {
                return _loggerAppender;
            }
        };
        _brokerLogger.open();
    }



    @Override
    public void tearDown() throws Exception
    {
        try
        {
            _brokerLogger.delete();
            _taskExecutor.stopImmediately();
        }
        finally
        {
            super.tearDown();
        }
    }

    public void testAddNewLogInclusionRule()
    {
        Map<String, Object> attributes = createBrokerNameAndLevelLogInclusionRuleAttributes("org.apache.qpid", LogLevel.INFO);

        Collection<BrokerLogInclusionRule> rulesBefore = _brokerLogger.getChildren(BrokerLogInclusionRule.class);
        assertEquals("Unexpected number of rules before creation", 0, rulesBefore.size());

        BrokerLogInclusionRule<?> createdRule = _brokerLogger.createChild(BrokerLogInclusionRule.class, attributes);
        assertEquals("Unexpected rule name", "test", createdRule.getName());

        Collection<BrokerLogInclusionRule> rulesAfter = _brokerLogger.getChildren(BrokerLogInclusionRule.class);
        assertEquals("Unexpected number of rules after creation", 1, rulesAfter.size());

        BrokerLogInclusionRule filter = rulesAfter.iterator().next();
        assertEquals("Unexpected rule", createdRule, filter);

        Logger logger = LoggerFactory.getLogger("org.apache.qpid");

        logger.debug("Test2");
        logger.info("Test3");
        assertLoggedEvent(_loggerAppender, false, "Test2", logger.getName(), Level.DEBUG);
        assertLoggedEvent(_loggerAppender, true, "Test3", logger.getName(), Level.INFO);
    }

    public void testRemoveExistingRule()
    {
        Map<String, Object> attributes = createBrokerNameAndLevelLogInclusionRuleAttributes("org.apache.qpid", LogLevel.INFO);
        BrokerLogInclusionRule<?> createdRule = _brokerLogger.createChild(BrokerLogInclusionRule.class, attributes);
        Logger logger = LoggerFactory.getLogger("org.apache.qpid");

        logger.info("Test1");
        assertLoggedEvent(_loggerAppender, true, "Test1", logger.getName(), Level.INFO);

        createdRule.delete();

        logger.info("Test2");
        assertLoggedEvent(_loggerAppender, false, "Test2", logger.getName(), Level.INFO);
    }

    public void testDeleteLogger()
    {
        ch.qos.logback.classic.Logger rootLogger =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        assertNotNull("Appender not found when it should have been created", rootLogger.getAppender(_brokerLogger.getName()));
        _brokerLogger.delete();
        assertEquals("Unexpected state after deletion", State.DELETED, _brokerLogger.getState());
        assertNull("Appender found when it should have been deleted", rootLogger.getAppender(_brokerLogger.getName()));
    }

    public void testBrokerMemoryLoggerGetLogEntries()
    {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(BrokerMemoryLogger.NAME, getTestName());
        attributes.put(ConfiguredObject.TYPE, BrokerMemoryLogger.TYPE);
        BrokerMemoryLogger logger = new BrokerMemoryLoggerImplFactory().createInstance(attributes, _broker);
        try
        {
            logger.open();

            Map<String, Object> filterAttributes = createBrokerNameAndLevelLogInclusionRuleAttributes("", LogLevel.ALL);
            logger.createChild(BrokerLogInclusionRule.class, filterAttributes);

            Logger messageLogger = LoggerFactory.getLogger("org.apache.qpid.test");
            messageLogger.debug("test message 1");
            Collection<LogRecord> logRecords = logger.getLogEntries(0);

            LogRecord foundRecord = findLogRecord("test message 1", logRecords);

            assertNotNull("Record is not found", foundRecord);

            messageLogger.debug("test message 2");

            Collection<LogRecord> logRecords2 = logger.getLogEntries(foundRecord.getId());
            for (LogRecord record: logRecords2)
            {
                assertTrue("Record id " + record.getId() + " is below " + foundRecord.getId(), record.getId() > foundRecord.getId());
            }

            LogRecord foundRecord2 = findLogRecord("test message 2", logRecords2);

            assertNotNull("Record2 is not found", foundRecord2);
        }
        finally
        {
            logger.delete();
        }
    }

    public void testStatistics()
    {
        Map<String, Object> attributes;
        String loggerName = getTestName();
        Logger messageLogger = LoggerFactory.getLogger(loggerName);

        assertEquals(0l, _brokerLogger.getWarnCount());
        assertEquals(0l, _brokerLogger.getErrorCount());

        attributes = createBrokerNameAndLevelLogInclusionRuleAttributes(loggerName, LogLevel.WARN);
        BrokerLogInclusionRule warnFilter = _brokerLogger.createChild(BrokerLogInclusionRule.class, attributes);

        messageLogger.warn("warn");
        assertEquals(1l, _brokerLogger.getWarnCount());
        assertEquals(0l, _brokerLogger.getErrorCount());

        messageLogger.error("error");
        assertEquals(1l, _brokerLogger.getWarnCount());
        assertEquals(1l, _brokerLogger.getErrorCount());

        warnFilter.delete();

        attributes = createBrokerNameAndLevelLogInclusionRuleAttributes(loggerName, LogLevel.ERROR);
        BrokerLogInclusionRule errorFilter = _brokerLogger.createChild(BrokerLogInclusionRule.class, attributes);

        messageLogger.warn("warn");
        assertEquals(1l, _brokerLogger.getWarnCount());
        assertEquals(1l, _brokerLogger.getErrorCount());
        messageLogger.error("error");
        assertEquals(1l, _brokerLogger.getWarnCount());
        assertEquals(2l, _brokerLogger.getErrorCount());

        errorFilter.delete();
    }

    private Map<String, Object> createBrokerNameAndLevelLogInclusionRuleAttributes(final String loggerName,
                                                                                   final LogLevel logLevel)
    {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(BrokerNameAndLevelLogInclusionRule.LOGGER_NAME, loggerName);
        attributes.put(BrokerNameAndLevelLogInclusionRule.LEVEL, logLevel);
        attributes.put(BrokerNameAndLevelLogInclusionRule.NAME, "test");
        attributes.put(ConfiguredObject.TYPE, BrokerNameAndLevelLogInclusionRule.TYPE);
        return attributes;
    }

    private LogRecord findLogRecord(String message, Collection<LogRecord> logRecords)
    {
        for (LogRecord record: logRecords)
        {
            if (message.equals(record.getMessage()))
            {
                return record;
            }
        }
        return null;
    }
}
