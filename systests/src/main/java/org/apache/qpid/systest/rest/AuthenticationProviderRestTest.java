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
package org.apache.qpid.systest.rest;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.qpid.server.BrokerOptions;
import org.apache.qpid.server.model.Attribute;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.LifetimePolicy;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManagerFactory;
import org.apache.qpid.server.security.auth.manager.PlainPasswordFileAuthenticationManagerFactory;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class AuthenticationProviderRestTest extends QpidRestTestCase
{

    public void testGet() throws Exception
    {
        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("/rest/authenticationprovider");
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 2, providerDetails.size());
        for (Map<String, Object> provider : providerDetails)
        {
            boolean managesPrincipals = true;
            String type = PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE;
            if (ANONYMOUS_AUTHENTICATION_PROVIDER.equals(provider.get(AuthenticationProvider.NAME)))
            {
                type = AnonymousAuthenticationManagerFactory.PROVIDER_TYPE;
                managesPrincipals = false;
            }
            assertProvider(managesPrincipals, type , provider);
            Map<String, Object> data = getRestTestHelper().getJsonAsSingletonList("/rest/authenticationprovider/"
                    + provider.get(AuthenticationProvider.NAME));
            assertNotNull("Cannot load data for " + provider.get(AuthenticationProvider.NAME), data);
            assertProvider(managesPrincipals, type, data);
        }
    }

    public void testPutCreateSecondPlainPrincipalDatabaseProviderSucceeds() throws Exception
    {
        File principalDatabase = getRestTestHelper().createTemporaryPasswdFile(new String[]{"admin2", "guest2", "test2"});

        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);
        attributes.put(PlainPasswordFileAuthenticationManagerFactory.ATTRIBUTE_PATH, principalDatabase.getAbsolutePath());

        int responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("failed to create authentication provider", 201, responseCode);
    }

    public void testPutCreateNewAnonymousProvider() throws Exception
    {
        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE);

        int responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Unexpected response code", 201, responseCode);

        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("/rest/authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());
        Map<String, Object> provider = providerDetails.get(0);
        assertProvider(false, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE, provider);
    }

    public void testUpdateAuthenticationProviderIdFails() throws Exception
    {
        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE);

        int responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Unexpected response code", 201, responseCode);

        attributes.put(AuthenticationProvider.ID, UUID.randomUUID());

        responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Update with new ID should fail", 409, responseCode);
    }

    public void testDeleteOfUsedAuthenticationProviderFails() throws Exception
    {
        // create provider
        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE);

        int responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Unexpected response code for provider creation", 201, responseCode);

        // create port
        String portName = "test-port";
        Map<String, Object> portAttributes = new HashMap<String, Object>();
        portAttributes.put(Port.NAME, portName);
        portAttributes.put(Port.AUTHENTICATION_PROVIDER, providerName);
        portAttributes.put(Port.PORT, findFreePort());

        responseCode = getRestTestHelper().submitRequest("/rest/port/" + portName, "PUT", portAttributes);
        assertEquals("Unexpected response code for port creation", 201, responseCode);

        responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName , "DELETE", null);
        assertEquals("Unexpected response code for provider deletion", 409, responseCode);

        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("/rest/authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 1, providerDetails.size());
        assertProvider(false, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE, providerDetails.get(0));
    }

    public void testDeleteOfUnusedAuthenticationProvider() throws Exception
    {
        // create provider
        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, AnonymousAuthenticationManagerFactory.PROVIDER_TYPE);

        int responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Unexpected response code for provider creation", 201, responseCode);

        responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName , "DELETE", null);
        assertEquals("Unexpected response code for provider deletion", 200, responseCode);

        List<Map<String, Object>> providerDetails = getRestTestHelper().getJsonAsList("/rest/authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected number of providers", 0, providerDetails.size());
    }

    public void testRemovalOfAuthenticationProviderInErrorStateUsingManagementMode() throws Exception
    {
        stopBroker();

        File file = new File(TMP_FOLDER, getTestName());
        if (file.exists())
        {
            file.delete();
        }
        assertFalse("Group file should not exist", file.exists());

        TestBrokerConfiguration config = getBrokerConfiguration();

        String providerName = getTestName();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.TYPE, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(PlainPasswordFileAuthenticationManagerFactory.ATTRIBUTE_PATH, file.getAbsoluteFile());

        UUID id = config.addAuthenticationProviderConfiguration(attributes);
        config.setSaved(false);
        startBroker(0, true);

        getRestTestHelper().setUsernameAndPassword(BrokerOptions.MANAGEMENT_MODE_USER_NAME, MANAGEMENT_MODE_PASSWORD);

        Map<String, Object> provider = getRestTestHelper().getJsonAsSingletonList("/rest/authenticationprovider/" + providerName);
        assertEquals("Unexpected id", id.toString(), provider.get(AuthenticationProvider.ID));
        assertEquals("Unexpected name", providerName, provider.get(AuthenticationProvider.NAME));
        assertEquals("Unexpected path", file.getAbsolutePath() , provider.get(PlainPasswordFileAuthenticationManagerFactory.ATTRIBUTE_PATH));
        assertEquals("Unexpected state", State.ERRORED.name() , provider.get(AuthenticationProvider.STATE));

        int status = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "DELETE", null);
        assertEquals("ACL was not deleted", 200, status);

        List<Map<String, Object>> providers = getRestTestHelper().getJsonAsList("/rest/authenticationprovider/" + providerName);
        assertEquals("Provider exists", 0, providers.size());
    }

    public void testUpdateOfAuthenticationProviderInErrorStateUsingManagementMode() throws Exception
    {
        stopBroker();

        File file = new File(TMP_FOLDER, getTestName());
        if (file.exists())
        {
            file.delete();
        }
        assertFalse("Group file should not exist", file.exists());

        TestBrokerConfiguration config = getBrokerConfiguration();

        String providerName = getTestName();
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.TYPE, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(PlainPasswordFileAuthenticationManagerFactory.ATTRIBUTE_PATH, file.getAbsoluteFile());

        UUID id = config.addAuthenticationProviderConfiguration(attributes);
        config.setSaved(false);
        startBroker(0, true);

        getRestTestHelper().setUsernameAndPassword(BrokerOptions.MANAGEMENT_MODE_USER_NAME, MANAGEMENT_MODE_PASSWORD);

        Map<String, Object> provider = getRestTestHelper().getJsonAsSingletonList("/rest/authenticationprovider/" + providerName);
        assertEquals("Unexpected id", id.toString(), provider.get(AuthenticationProvider.ID));
        assertEquals("Unexpected name", providerName, provider.get(AuthenticationProvider.NAME));
        assertEquals("Unexpected path", file.getAbsolutePath() , provider.get(PlainPasswordFileAuthenticationManagerFactory.ATTRIBUTE_PATH));
        assertEquals("Unexpected state", State.ERRORED.name() , provider.get(AuthenticationProvider.STATE));

        File principalDatabase = null;
        try
        {
            principalDatabase = getRestTestHelper().createTemporaryPasswdFile(new String[]{"admin2", "guest2", "test2"});
            attributes = new HashMap<String, Object>();
            attributes.put(AuthenticationProvider.NAME, providerName);
            attributes.put(AuthenticationProvider.ID, id);
            attributes.put(AuthenticationProvider.TYPE, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);
            attributes.put(PlainPasswordFileAuthenticationManagerFactory.ATTRIBUTE_PATH, principalDatabase.getAbsolutePath());

            int status = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", attributes);
            assertEquals("ACL was not deleted", 200, status);

            provider = getRestTestHelper().getJsonAsSingletonList("/rest/authenticationprovider/" + providerName);
            assertEquals("Unexpected id", id.toString(), provider.get(AuthenticationProvider.ID));
            assertEquals("Unexpected name", providerName, provider.get(AuthenticationProvider.NAME));
            assertEquals("Unexpected path", principalDatabase.getAbsolutePath() , provider.get(PlainPasswordFileAuthenticationManagerFactory.ATTRIBUTE_PATH));
            assertEquals("Unexpected state", State.ACTIVE.name() , provider.get(AuthenticationProvider.STATE));
        }
        finally
        {
            if (principalDatabase != null)
            {
                principalDatabase.delete();
            }
        }
    }

    public void testCreateAndDeletePasswordAuthenticationProviderWithNonExistingFile() throws Exception
    {
        stopBroker();
        getBrokerConfiguration().setSaved(false);
        getBrokerConfiguration().removeObjectConfiguration(TestBrokerConfiguration.ENTRY_NAME_AUTHENTICATION_PROVIDER);
        getBrokerConfiguration().setObjectAttribute(TestBrokerConfiguration.ENTRY_NAME_AMQP_PORT, Port.AUTHENTICATION_PROVIDER, ANONYMOUS_AUTHENTICATION_PROVIDER);
        getBrokerConfiguration().setObjectAttribute(TestBrokerConfiguration.ENTRY_NAME_HTTP_PORT, Port.AUTHENTICATION_PROVIDER, ANONYMOUS_AUTHENTICATION_PROVIDER);

        startBroker();

        File file = new File(TMP_FOLDER + File.separator + getTestName());
        if (file.exists())
        {
            file.delete();
        }
        assertFalse("File " + file.getAbsolutePath() + " should not exist", file.exists());

        // create provider
        String providerName = "test-provider";
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(AuthenticationProvider.NAME, providerName);
        attributes.put(AuthenticationProvider.TYPE, PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE);
        attributes.put(PlainPasswordFileAuthenticationManagerFactory.ATTRIBUTE_PATH, file.getAbsolutePath());

        int responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName, "PUT", attributes);
        assertEquals("Password provider was not created", 201, responseCode);


        Map<String, Object> providerDetails = getRestTestHelper().getJsonAsSingletonList("/rest/authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providerDetails);
        assertEquals("Unexpected name", providerName, providerDetails.get(AuthenticationProvider.NAME));
        assertEquals("Unexpected type", PlainPasswordFileAuthenticationManagerFactory.PROVIDER_TYPE, providerDetails.get(AuthenticationProvider.TYPE));
        assertEquals("Unexpected path", file.getAbsolutePath(), providerDetails.get(PlainPasswordFileAuthenticationManagerFactory.ATTRIBUTE_PATH));

        assertTrue("User file should be created", file.exists());

        responseCode = getRestTestHelper().submitRequest("/rest/authenticationprovider/" + providerName , "DELETE", null);
        assertEquals("Unexpected response code for provider deletion", 200, responseCode);

        List<Map<String, Object>> providers = getRestTestHelper().getJsonAsList("/rest/authenticationprovider/" + providerName);
        assertNotNull("Providers details cannot be null", providers);
        assertEquals("Unexpected number of providers", 0, providers.size());

        assertFalse("File " + file.getAbsolutePath() + " should be deleted", file.exists());
    }

    private void assertProvider(boolean managesPrincipals, String type, Map<String, Object> provider)
    {
        Asserts.assertAttributesPresent(provider, Attribute.getAttributeNames(AuthenticationProvider.class),
                AuthenticationProvider.DESCRIPTION, AuthenticationProvider.TIME_TO_LIVE, ConfiguredObject.CREATED_BY,
                ConfiguredObject.CREATED_TIME, ConfiguredObject.LAST_UPDATED_BY, ConfiguredObject.LAST_UPDATED_TIME);
        assertEquals("Unexpected value of provider attribute " + AuthenticationProvider.STATE, State.ACTIVE.name(),
                provider.get(AuthenticationProvider.STATE));
        assertEquals("Unexpected value of provider attribute " + AuthenticationProvider.LIFETIME_POLICY,
                LifetimePolicy.PERMANENT.name(), provider.get(AuthenticationProvider.LIFETIME_POLICY));
        assertEquals("Unexpected value of provider attribute " + AuthenticationProvider.DURABLE, Boolean.TRUE,
                provider.get(AuthenticationProvider.DURABLE));
        assertEquals("Unexpected value of provider attribute " + AuthenticationProvider.TYPE, type,
                provider.get(AuthenticationProvider.TYPE));

        if (managesPrincipals)
        {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> users = (List<Map<String, Object>>) provider.get("users");
            assertNotNull("Users are not found", users);
            assertTrue("Unexpected number of users", users.size() > 1);
            for (Map<String, Object> user : users)
            {
                assertNotNull("Attribute " + User.ID, user.get(User.ID));
                assertNotNull("Attribute " + User.NAME, user.get(User.NAME));
            }
        }
    }
}
