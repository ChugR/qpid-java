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
package org.apache.qpid.server.plugin;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.qpid.server.store.jdbc.ConnectionProvider;

public interface JDBCConnectionProviderFactory extends Pluggable
{
    String getType();

    ConnectionProvider getConnectionProvider(String connectionUrl, Map<String, Object> storeSettings)
            throws SQLException;

    static final class TYPES
    {
        private TYPES()
        {
        }

        public static Collection<String> get()
        {
            QpidServiceLoader<JDBCConnectionProviderFactory> qpidServiceLoader = new QpidServiceLoader<JDBCConnectionProviderFactory>();
            Iterable<JDBCConnectionProviderFactory> factories = qpidServiceLoader.atLeastOneInstanceOf(JDBCConnectionProviderFactory.class);
            List<String> names = new ArrayList<String>();
            for(JDBCConnectionProviderFactory factory : factories)
            {
                names.add(factory.getType());
            }
            return Collections.unmodifiableCollection(names);
        }
    }


    static final class FACTORIES
    {
        private FACTORIES()
        {
        }

        public static JDBCConnectionProviderFactory get(String type)
        {
            QpidServiceLoader<JDBCConnectionProviderFactory> qpidServiceLoader = new QpidServiceLoader<JDBCConnectionProviderFactory>();
            Iterable<JDBCConnectionProviderFactory> factories = qpidServiceLoader.atLeastOneInstanceOf(JDBCConnectionProviderFactory.class);
            for(JDBCConnectionProviderFactory factory : factories)
            {
                if(factory.getType().equals(type))
                {
                    return factory;
                }
            }
            return null;
        }
    }
}
