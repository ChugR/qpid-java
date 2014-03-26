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
package org.apache.qpid.server.store.berkeleydb;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.configuration.BrokerProperties;
import org.apache.qpid.server.model.VirtualHost;

public class StandardEnvironmentFacadeFactory implements EnvironmentFacadeFactory
{

    @SuppressWarnings("unchecked")
    @Override
    public EnvironmentFacade createEnvironmentFacade(VirtualHost virtualHost, boolean isMessageStore)
    {
        Map<String, String> envConfigMap = new HashMap<String, String>();
        envConfigMap.putAll(EnvironmentFacade.ENVCONFIG_DEFAULTS);

        Object environmentConfigurationAttributes = virtualHost.getAttribute(BDBMessageStore.ENVIRONMENT_CONFIGURATION);
        if (environmentConfigurationAttributes instanceof Map)
        {
            envConfigMap.putAll((Map<String, String>) environmentConfigurationAttributes);
        }

        String name = virtualHost.getName();
        final String defaultPath = System.getProperty(BrokerProperties.PROPERTY_QPID_WORK) + File.separator + "bdbstore" + File.separator + name;

        String storeLocation;
        if(isMessageStore)
        {
            storeLocation = (String) virtualHost.getAttribute(VirtualHost.STORE_PATH);
            if(storeLocation == null)
            {
                storeLocation = defaultPath;
            }
        }
        else // we are acting only as the durable config store
        {
            storeLocation = (String) virtualHost.getAttribute(VirtualHost.CONFIG_STORE_PATH);
            if(storeLocation == null)
            {
                storeLocation = defaultPath;
            }
        }

        return new StandardEnvironmentFacade(storeLocation, envConfigMap);
    }

    @Override
    public String getType()
    {
        return StandardEnvironmentFacade.TYPE;
    }

}
