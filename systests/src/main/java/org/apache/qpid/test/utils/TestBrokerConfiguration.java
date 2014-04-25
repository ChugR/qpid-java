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
package org.apache.qpid.test.utils;

import static org.mockito.Mockito.mock;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.configuration.store.MemoryConfigurationEntryStore;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.logging.EventLogger;
import org.apache.qpid.server.logging.LogRecorder;
import org.apache.qpid.server.model.AccessControlProvider;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.GroupProvider;
import org.apache.qpid.server.model.Plugin;
import org.apache.qpid.server.model.PreferencesProvider;
import org.apache.qpid.server.model.SystemContextImpl;
import org.apache.qpid.server.model.UUIDGenerator;
import org.apache.qpid.server.model.adapter.FileBasedGroupProvider;
import org.apache.qpid.server.model.adapter.FileBasedGroupProviderImpl;
import org.apache.qpid.server.security.access.FileAccessControlProviderConstants;
import org.apache.qpid.server.store.ConfiguredObjectRecord;
import org.apache.qpid.server.store.ConfiguredObjectRecordImpl;
import org.apache.qpid.server.store.handler.ConfiguredObjectRecordHandler;
import org.apache.qpid.util.Strings;

public class TestBrokerConfiguration
{
    public static final String ENTRY_NAME_HTTP_PORT = "http";
    public static final String ENTRY_NAME_AMQP_PORT = "amqp";
    public static final String ENTRY_NAME_RMI_PORT = "rmi";
    public static final String ENTRY_NAME_JMX_PORT = "jmx";
    public static final String ENTRY_NAME_VIRTUAL_HOST = "test";
    public static final String ENTRY_NAME_AUTHENTICATION_PROVIDER = "plain";
    public static final String ENTRY_NAME_EXTERNAL_PROVIDER = "external";
    public static final String ENTRY_NAME_SSL_PORT = "sslPort";
    public static final String ENTRY_NAME_HTTP_MANAGEMENT = "MANAGEMENT-HTTP";
    public static final String MANAGEMENT_HTTP_PLUGIN_TYPE = "MANAGEMENT-HTTP";
    public static final String ENTRY_NAME_JMX_MANAGEMENT = "MANAGEMENT-JMX";
    public static final String MANAGEMENT_JMX_PLUGIN_TYPE = "MANAGEMENT-JMX";
    public static final String ENTRY_NAME_ANONYMOUS_PROVIDER = "anonymous";
    public static final String ENTRY_NAME_SSL_KEYSTORE = "systestsKeyStore";
    public static final String ENTRY_NAME_SSL_TRUSTSTORE = "systestsTrustStore";
    public static final String ENTRY_NAME_GROUP_FILE = "groupFile";
    public static final String ENTRY_NAME_ACL_FILE = "aclFile";

    private MemoryConfigurationEntryStore _store;
    private boolean _saved;

    public TestBrokerConfiguration(String storeType, String initialStoreLocation, final TaskExecutor taskExecutor)
    {
        _store = new MemoryConfigurationEntryStore(
                new SystemContextImpl(taskExecutor,
                                      mock(EventLogger.class),
                                      mock(LogRecorder.class),
                                      mock(BrokerOptions.class)),
                initialStoreLocation,
                null,
                Collections.<String,String>emptyMap());
        _store.visitConfiguredObjectRecords(new ConfiguredObjectRecordHandler()
        {
            @Override
            public boolean handle(ConfiguredObjectRecord record)
            {
                Map<String, Object> attributes = record.getAttributes();
                String rawType = (String)attributes.get("type");
                if (rawType != null)
                {
                    String interpolatedType = Strings.expand(rawType, false, Strings.ENV_VARS_RESOLVER, Strings.JAVA_SYS_PROPS_RESOLVER);
                    if (!interpolatedType.equals(rawType))
                    {
                        setObjectAttribute(record, "type", interpolatedType);
                    }
                }
                return true;
            }

            @Override
            public void end()
            {
            }

            @Override
            public void begin()
            {
            }
        });
    }

    public boolean setBrokerAttribute(String name, Object value)
    {
        ConfiguredObjectRecord entry = findObject(Broker.class, null);
        if (entry == null)
        {
            return false;
        }

        return setObjectAttribute(entry, name, value);
    }

    public boolean setObjectAttribute(final Class<? extends ConfiguredObject> category,
                                      String objectName,
                                      String attributeName,
                                      Object value)
    {
        ConfiguredObjectRecord entry = findObject(category, objectName);
        if (entry == null)
        {
            return false;
        }
        return setObjectAttribute(entry, attributeName, value);
    }

    public boolean setObjectAttributes(final Class<? extends ConfiguredObject> category,
                                       String objectName,
                                       Map<String, Object> attributes)
    {
        ConfiguredObjectRecord entry = findObject(category, objectName);
        if (entry == null)
        {
            return false;
        }
        return setObjectAttributes(entry, attributes);
    }

    public boolean save(File configFile)
    {
        _store.copyTo(configFile.getAbsolutePath());
        return true;
    }

    public UUID[] removeObjectConfiguration(final Class<? extends ConfiguredObject> category,
                                            String name)
    {
        final ConfiguredObjectRecord entry = findObject(category, name);
        if (entry != null)
        {
            return _store.remove(entry);
        }
        return null;
    }

    public UUID addObjectConfiguration(Class<? extends ConfiguredObject> type, Map<String, Object> attributes)
    {
        UUID id = UUIDGenerator.generateRandomUUID();
        addObjectConfiguration(id, type.getSimpleName(), attributes);
        return id;
    }

    public UUID addJmxManagementConfiguration()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Plugin.TYPE, MANAGEMENT_JMX_PLUGIN_TYPE);
        attributes.put(Plugin.NAME, ENTRY_NAME_JMX_MANAGEMENT);
        return addObjectConfiguration(Plugin.class, attributes);
    }

    public UUID addHttpManagementConfiguration()
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(Plugin.TYPE, MANAGEMENT_HTTP_PLUGIN_TYPE);
        attributes.put(Plugin.NAME, ENTRY_NAME_HTTP_MANAGEMENT);
        return addObjectConfiguration(Plugin.class, attributes);
    }

    public UUID addGroupFileConfiguration(String groupFilePath)
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(GroupProvider.NAME, ENTRY_NAME_GROUP_FILE);
        attributes.put(GroupProvider.TYPE, FileBasedGroupProviderImpl.GROUP_FILE_PROVIDER_TYPE);
        attributes.put(FileBasedGroupProvider.PATH, groupFilePath);

        return addObjectConfiguration(GroupProvider.class, attributes);
    }

    public UUID addAclFileConfiguration(String aclFilePath)
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AccessControlProvider.NAME, ENTRY_NAME_ACL_FILE);
        attributes.put(AccessControlProvider.TYPE, FileAccessControlProviderConstants.ACL_FILE_PROVIDER_TYPE);
        attributes.put(FileAccessControlProviderConstants.PATH, aclFilePath);

        return addObjectConfiguration(AccessControlProvider.class, attributes);
    }

    private boolean setObjectAttributes(ConfiguredObjectRecord entry, Map<String, Object> attributes)
    {
        Map<String, Object> newAttributes = new HashMap<String, Object>(entry.getAttributes());
        newAttributes.putAll(attributes);
        ConfiguredObjectRecord newEntry = new ConfiguredObjectRecordImpl(entry.getId(), entry.getType(), newAttributes,
                                                                         entry.getParents());
        _store.update(false, newEntry);
        return true;
    }

    private ConfiguredObjectRecord findObject(final Class<? extends ConfiguredObject> category, final String objectName)
    {
        final RecordFindingVisitor visitor = new RecordFindingVisitor(category, objectName);
        _store.visitConfiguredObjectRecords(visitor);
        return visitor.getFoundRecord();
    }

    private void addObjectConfiguration(UUID id, String type, Map<String, Object> attributes)
    {
        ConfiguredObjectRecord entry = new ConfiguredObjectRecordImpl(id, type, attributes, Collections.singletonMap(Broker.class.getSimpleName(), findObject(Broker.class,null)));

        _store.update(true, entry);
    }

    private boolean setObjectAttribute(ConfiguredObjectRecord entry, String attributeName, Object value)
    {
        Map<String, Object> attributes = new HashMap<String, Object>(entry.getAttributes());
        attributes.put(attributeName, value);
        ConfiguredObjectRecord newEntry = new ConfiguredObjectRecordImpl(entry.getId(), entry.getType(), attributes, entry.getParents());
        _store.update(false, newEntry);
        return true;
    }

    public boolean isSaved()
    {
        return _saved;
    }

    public void setSaved(boolean saved)
    {
        _saved = saved;
    }

    public void addPreferencesProviderConfiguration(String authenticationProvider, Map<String, Object> attributes)
    {
        ConfiguredObjectRecord authProviderRecord = findObject(AuthenticationProvider.class, authenticationProvider);
        ConfiguredObjectRecord pp = new ConfiguredObjectRecordImpl(UUIDGenerator.generateRandomUUID(),
                                                                   PreferencesProvider.class.getSimpleName(), attributes, Collections.<String, ConfiguredObjectRecord>singletonMap(AuthenticationProvider.class.getSimpleName(),authProviderRecord));

        _store.create(pp);
    }

    public Map<String,Object> getObjectAttributes(final Class<? extends ConfiguredObject> category, final String name)
    {
        return findObject(category, name).getAttributes();
    }

    private static class RecordFindingVisitor implements ConfiguredObjectRecordHandler
    {
        private final Class<? extends ConfiguredObject> _category;
        private final String _objectName;
        public ConfiguredObjectRecord _foundRecord;

        public RecordFindingVisitor(final Class<? extends ConfiguredObject> category, final String objectName)
        {
            _category = category;
            _objectName = objectName;
        }

        @Override
        public void begin()
        {
        }

        @Override
        public boolean handle(final ConfiguredObjectRecord object)
        {
            if (object.getType().equals(_category.getSimpleName())
                && (_objectName == null
                    || _objectName.equals(object.getAttributes().get(ConfiguredObject.NAME))))
            {
                _foundRecord = object;
                return false;
            }
            return true;
        }

        @Override
        public void end()
        {
        }

        public ConfiguredObjectRecord getFoundRecord()
        {
            return _foundRecord;
        }
    }
}
