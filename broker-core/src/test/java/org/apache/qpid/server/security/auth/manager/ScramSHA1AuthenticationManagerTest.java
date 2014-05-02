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
package org.apache.qpid.server.security.auth.manager;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.security.auth.login.AccountNotFoundException;

import org.apache.qpid.server.configuration.updater.CurrentThreadTaskExecutor;
import org.apache.qpid.server.configuration.updater.TaskExecutor;
import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.User;
import org.apache.qpid.server.security.SecurityManager;
import org.apache.qpid.server.security.auth.AuthenticationResult;
import org.apache.qpid.server.util.BrokerTestHelper;
import org.apache.qpid.test.utils.QpidTestCase;

public class ScramSHA1AuthenticationManagerTest extends QpidTestCase
{
    private ScramSHA1AuthenticationManager _authManager;
    private Broker _broker;
    private SecurityManager _securityManager;
    private TaskExecutor _executor;

    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        _executor = new CurrentThreadTaskExecutor();
        _executor.start();
        _broker = BrokerTestHelper.createBrokerMock();
        _securityManager = mock(SecurityManager.class);
        when(_broker.getTaskExecutor()).thenReturn(_executor);
        when(_broker.getSecurityManager()).thenReturn(_securityManager);
        final Map<String, Object> attributesMap = new HashMap<String, Object>();
        attributesMap.put(AuthenticationProvider.NAME, getTestName());
        attributesMap.put(AuthenticationProvider.ID, UUID.randomUUID());
        _authManager = new ScramSHA1AuthenticationManager(attributesMap, _broker);
    }

    @Override
    public void tearDown() throws Exception
    {
        _executor.stop();
        super.tearDown();
    }

    public void testAddChildAndThenDelete()
    {
        // No children should be present before the test starts
        assertEquals("No users should be present before the test starts", 0, _authManager.getChildren(User.class).size());
        assertEquals("No users should be present before the test starts", 0, _authManager.getUsers().size());

        final Map<String, Object> childAttrs = new HashMap<String, Object>();

        childAttrs.put(User.NAME, getTestName());
        childAttrs.put(User.PASSWORD, "password");
        User user = _authManager.addChild(User.class, childAttrs);
        assertNotNull("User should be created but addChild returned null", user);
        assertEquals(getTestName(), user.getName());
        // password shouldn't actually be the given string, but instead salt and the hashed value
        assertFalse("Password shouldn't actually be the given string, but instead salt and the hashed value", "password".equals(user.getPassword()));

        AuthenticationResult authResult =
                _authManager.authenticate(getTestName(), "password");

        assertEquals("User should authenticate with given password", AuthenticationResult.AuthenticationStatus.SUCCESS, authResult.getStatus());

        assertEquals("Manager should have exactly one user child",1, _authManager.getChildren(User.class).size());
        assertEquals("Manager should have exactly one user child",1, _authManager.getUsers().size());


        user.setDesiredState(State.DELETED);

        assertEquals("No users should be present after child deletion", 0, _authManager.getChildren(User.class).size());


        authResult = _authManager.authenticate(getTestName(), "password");
        assertEquals("User should no longer authenticate with given password", AuthenticationResult.AuthenticationStatus.ERROR, authResult.getStatus());

    }

    public void testCreateUser()
    {
        assertEquals("No users should be present before the test starts", 0, _authManager.getChildren(User.class).size());
        assertTrue(_authManager.createUser(getTestName(), "password", Collections.<String, String>emptyMap()));
        assertEquals("Manager should have exactly one user child",1, _authManager.getChildren(User.class).size());
        User user = _authManager.getChildren(User.class).iterator().next();
        assertEquals(getTestName(), user.getName());
        // password shouldn't actually be the given string, but instead salt and the hashed value
        assertFalse("Password shouldn't actually be the given string, but instead salt and the hashed value", "password".equals(user.getPassword()));
        final Map<String, Object> childAttrs = new HashMap<String, Object>();

        childAttrs.put(User.NAME, getTestName());
        childAttrs.put(User.PASSWORD, "password");
        try
        {
            user = _authManager.addChild(User.class, childAttrs);
            fail("Should not be able to create a second user with the same name");
        }
        catch(IllegalArgumentException e)
        {
            // pass
        }
        try
        {
            _authManager.deleteUser(getTestName());
        }
        catch (AccountNotFoundException e)
        {
            fail("AccountNotFoundException thrown when none was expected: " + e.getMessage());
        }
        try
        {
            _authManager.deleteUser(getTestName());
            fail("AccountNotFoundException not thrown when was expected");
        }
        catch (AccountNotFoundException e)
        {
            // pass
        }
    }

    public void testUpdateUser()
    {
        assertTrue(_authManager.createUser(getTestName(), "password", Collections.<String, String>emptyMap()));
        assertTrue(_authManager.createUser(getTestName()+"_2", "password", Collections.<String, String>emptyMap()));
        assertEquals("Manager should have exactly two user children",2, _authManager.getChildren(User.class).size());

        AuthenticationResult authResult = _authManager.authenticate(getTestName(), "password");

        assertEquals("User should authenticate with given password", AuthenticationResult.AuthenticationStatus.SUCCESS, authResult.getStatus());
        authResult = _authManager.authenticate(getTestName()+"_2", "password");
        assertEquals("User should authenticate with given password", AuthenticationResult.AuthenticationStatus.SUCCESS, authResult.getStatus());

        for(User user : _authManager.getChildren(User.class))
        {
            if(user.getName().equals(getTestName()))
            {
                user.setAttributes(Collections.singletonMap(User.PASSWORD, "newpassword"));
            }
        }

        authResult = _authManager.authenticate(getTestName(), "newpassword");
        assertEquals("User should authenticate with updated password", AuthenticationResult.AuthenticationStatus.SUCCESS, authResult.getStatus());
        authResult = _authManager.authenticate(getTestName()+"_2", "password");
        assertEquals("User should authenticate with original password", AuthenticationResult.AuthenticationStatus.SUCCESS, authResult.getStatus());

        authResult = _authManager.authenticate(getTestName(), "password");
        assertEquals("User not authenticate with original password", AuthenticationResult.AuthenticationStatus.ERROR, authResult.getStatus());

        for(User user : _authManager.getChildren(User.class))
        {
            if(user.getName().equals(getTestName()))
            {
                user.setPassword("newerpassword");
            }
        }

        authResult = _authManager.authenticate(getTestName(), "newerpassword");
        assertEquals("User should authenticate with updated password", AuthenticationResult.AuthenticationStatus.SUCCESS, authResult.getStatus());



    }

    public void testNonASCIIUser()
    {
        try
        {
            _authManager.createUser(getTestName()+Character.toString((char)0xa3), "password", Collections.<String, String>emptyMap());
            fail("Expected exception when attempting to create a user with a non ascii name");
        }
        catch(IllegalArgumentException e)
        {
            // pass
        }
    }

}
