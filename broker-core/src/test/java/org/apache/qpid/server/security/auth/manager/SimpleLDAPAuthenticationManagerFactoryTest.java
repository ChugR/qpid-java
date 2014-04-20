/*
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
package org.apache.qpid.server.security.auth.manager;


import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import junit.framework.TestCase;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.SystemContext;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.util.BrokerTestHelper;

public class SimpleLDAPAuthenticationManagerFactoryTest extends TestCase
{
    private SimpleLDAPAuthenticationManagerFactory _factory = new SimpleLDAPAuthenticationManagerFactory();
    private Map<String, Object> _configuration = new HashMap<String, Object>();
    private Broker _broker = BrokerTestHelper.createBrokerMock();
    private SystemContext _systemContext = mock(SystemContext.class);

    private TrustStore _trustStore = mock(TrustStore.class);

    public void setUp() throws Exception
    {
        super.setUp();

        when(_trustStore.getName()).thenReturn("mytruststore");
        when(_trustStore.getId()).thenReturn(UUID.randomUUID());

        _configuration.put(AuthenticationProvider.ID, UUID.randomUUID());
        _configuration.put(AuthenticationProvider.NAME, getName());
    }

    public void testLdapInstanceCreated() throws Exception
    {
        _configuration.put(AuthenticationProvider.TYPE, SimpleLDAPAuthenticationManagerFactory.PROVIDER_TYPE);
        _configuration.put("providerUrl", "ldap://example.com:389/");
        _configuration.put("searchContext", "dc=example");

        AuthenticationManager manager = _factory.create(null, _configuration, _broker);
        assertNotNull(manager);

    }

    public void testLdapsInstanceCreated() throws Exception
    {
        _configuration.put(AuthenticationProvider.TYPE, SimpleLDAPAuthenticationManagerFactory.PROVIDER_TYPE);
        _configuration.put("providerUrl", "ldaps://example.com:636/");
        _configuration.put("searchContext", "dc=example");

        AuthenticationManager manager = _factory.create(null, _configuration, _broker);
        assertNotNull(manager);

    }

    public void testLdapsWithTrustStoreInstanceCreated() throws Exception
    {
        when(_broker.getChildren(eq(TrustStore.class))).thenReturn(Collections.singletonList(_trustStore));


        _configuration.put(AuthenticationProvider.TYPE, SimpleLDAPAuthenticationManagerFactory.PROVIDER_TYPE);
        _configuration.put("providerUrl", "ldaps://example.com:636/");
        _configuration.put("searchContext", "dc=example");
        _configuration.put("trustStore", "mytruststore");

        AuthenticationManager manager = _factory.create(null, _configuration, _broker);
        assertNotNull(manager);
    }

    public void testLdapsWhenTrustStoreNotFound() throws Exception
    {
        when(_broker.getChildren(eq(TrustStore.class))).thenReturn(Collections.singletonList(_trustStore));

        _configuration.put(AuthenticationProvider.TYPE, SimpleLDAPAuthenticationManagerFactory.PROVIDER_TYPE);
        _configuration.put("providerUrl", "ldaps://example.com:636/");
        _configuration.put("searchContext", "dc=example");
        _configuration.put("trustStore", "notfound");

        try
        {
            _factory.create(null, _configuration, _broker);
            fail("Exception not thrown");
        }
        catch(IllegalArgumentException e)
        {
            assertEquals("Cannot find a TrustStore with name 'notfound'", e.getMessage());
        }
    }

}
