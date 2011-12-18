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
package org.apache.qpid.ra.admin;

import javax.naming.NamingException;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

import org.apache.qpid.client.AMQTopic;

public class QpidTopic extends AMQTopic
{
    private String _url;

    public QpidTopic(final String address) throws Exception
    {
        super(address);
        this._url = address;
    }

    @Override
    public Reference getReference() throws NamingException
    {
        return new Reference(this.getClass().getName(), new StringRefAddr(this.getClass().getName(), toURL()),
                AdminObjectFactory.class.getName(), null);
    }

    @Override
    public String toURL()
    {
        return _url;
    }

}
