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
package org.apache.qpid.server.management.plugin;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.security.SubjectCreator;
import org.apache.qpid.test.utils.QpidTestCase;

public class HttpManagementTest extends QpidTestCase
{
    private UUID _id;
    private Broker _broker;
    private HttpManagement _management;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _id = UUID.randomUUID();
        _broker = mock(Broker.class);
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED, false);
        attributes.put(HttpManagement.HTTPS_BASIC_AUTHENTICATION_ENABLED, true);
        attributes.put(HttpManagement.HTTP_SASL_AUTHENTICATION_ENABLED, false);
        attributes.put(HttpManagement.HTTPS_SASL_AUTHENTICATION_ENABLED, true);
        attributes.put(HttpManagement.NAME, getTestName());
        attributes.put(HttpManagement.TIME_OUT, 10000l);
        _management = new HttpManagement(_id, _broker, attributes);
    }

    public void testGetBroker()
    {
        assertEquals("Unexpected broker", _broker, _management.getBroker());
    }

    public void testGetSessionTimeout()
    {
        assertEquals("Unexpected session timeout", 10000l, _management.getSessionTimeout());
    }

    public void testGetName()
    {
        assertEquals("Unexpected name", getTestName(), _management.getName());
    }

    public void testIsHttpsSaslAuthenticationEnabled()
    {
        assertEquals("Unexpected value for the https sasl enabled attribute", true,
                _management.isHttpsSaslAuthenticationEnabled());
    }

    public void testIsHttpSaslAuthenticationEnabled()
    {
        assertEquals("Unexpected value for the http sasl enabled attribute", false, _management.isHttpSaslAuthenticationEnabled());
    }

    public void testIsHttpsBasicAuthenticationEnabled()
    {
        assertEquals("Unexpected value for the https basic authentication enabled attribute", true,
                _management.isHttpsBasicAuthenticationEnabled());
    }

    public void testIsHttpBasicAuthenticationEnabled()
    {
        assertEquals("Unexpected value for the http basic authentication enabled attribute", false,
                _management.isHttpBasicAuthenticationEnabled());
    }

    public void testGetSubjectCreator()
    {
        SocketAddress localAddress = InetSocketAddress.createUnresolved("localhost", 8080);
        SubjectCreator subjectCreator = mock(SubjectCreator.class);
        when(_broker.getSubjectCreator(localAddress)).thenReturn(subjectCreator);
        SubjectCreator httpManagementSubjectCreator = _management.getSubjectCreator(localAddress);
        assertEquals("Unexpected subject creator", subjectCreator, httpManagementSubjectCreator);
    }

}
