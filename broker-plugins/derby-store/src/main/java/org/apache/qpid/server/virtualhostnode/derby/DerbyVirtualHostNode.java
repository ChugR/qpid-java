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

package org.apache.qpid.server.virtualhostnode.derby;

import java.util.Map;

import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.ManagedObject;
import org.apache.qpid.server.plugin.DurableConfigurationStoreFactory;
import org.apache.qpid.server.store.derby.DerbyMessageStoreFactory;
import org.apache.qpid.server.virtualhostnode.AbstractStandardVirtualHostNode;
import org.apache.qpid.server.virtualhostnode.FileBasedVirtualHostNode;

@ManagedObject( category = false, type = "DERBY" )
public class DerbyVirtualHostNode extends AbstractStandardVirtualHostNode<DerbyVirtualHostNode> implements FileBasedVirtualHostNode<DerbyVirtualHostNode>
{
    @ManagedAttributeField
    private String _storePath;

    public DerbyVirtualHostNode(Broker<?> parent, Map<String, Object> attributes, TaskExecutor taskExecutor)
    {
        super(parent, attributes, taskExecutor);
    }

    @Override
    protected DurableConfigurationStoreFactory getDurableConfigurationStoreFactory()
    {
        return new DerbyMessageStoreFactory();
    }

    @Override
    public String getStorePath()
    {
        return _storePath;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " [id=" + getId() + ", name=" + getName() + ", storePath=" + getStorePath() + "]";
    }
}
