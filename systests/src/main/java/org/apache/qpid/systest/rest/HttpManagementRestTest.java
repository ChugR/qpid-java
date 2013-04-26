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

import java.util.HashMap;
import java.util.Map;

import org.apache.qpid.server.management.plugin.HttpManagement;
import org.apache.qpid.test.utils.TestBrokerConfiguration;

public class HttpManagementRestTest extends QpidRestTestCase
{

    public void testGetHttpManagement() throws Exception
    {
        Map<String, Object> details = getRestTestHelper().getJsonAsSingletonList(
                "/rest/plugin/" + TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT);

        assertEquals("Unexpected session timeout", HttpManagement.DEFAULT_TIMEOUT_IN_SECONDS,
                details.get(HttpManagement.TIME_OUT));
        assertEquals("Unexpected http basic auth enabled", true,
                details.get(HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected https basic auth enabled", HttpManagement.DEFAULT_HTTPS_BASIC_AUTHENTICATION_ENABLED,
                details.get(HttpManagement.HTTPS_BASIC_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected http sasl auth enabled", HttpManagement.DEFAULT_HTTP_SASL_AUTHENTICATION_ENABLED,
                details.get(HttpManagement.HTTP_SASL_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected https sasl auth enabled", HttpManagement.DEFAULT_HTTPS_SASL_AUTHENTICATION_ENABLED,
                details.get(HttpManagement.HTTPS_SASL_AUTHENTICATION_ENABLED));
    }

    public void testUpdateAttributes() throws Exception
    {
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put(HttpManagement.NAME, TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT);
        attributes.put(HttpManagement.HTTPS_BASIC_AUTHENTICATION_ENABLED, false);
        attributes.put(HttpManagement.HTTPS_SASL_AUTHENTICATION_ENABLED, false);
        attributes.put(HttpManagement.HTTP_SASL_AUTHENTICATION_ENABLED, false);
        attributes.put(HttpManagement.TIME_OUT, 10000);

        getRestTestHelper().submitRequest("/rest/plugin/" + TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT, "PUT", attributes);

        Map<String, Object> details = getRestTestHelper().getJsonAsSingletonList(
                "/rest/plugin/" + TestBrokerConfiguration.ENTRY_NAME_HTTP_MANAGEMENT);

        assertEquals("Unexpected session timeout", 10000, details.get(HttpManagement.TIME_OUT));
        assertEquals("Unexpected http basic auth enabled", true, details.get(HttpManagement.HTTP_BASIC_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected https basic auth enabled", false, details.get(HttpManagement.HTTPS_BASIC_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected http sasl auth enabled", false, details.get(HttpManagement.HTTP_SASL_AUTHENTICATION_ENABLED));
        assertEquals("Unexpected https sasl auth enabled", false, details.get(HttpManagement.HTTPS_SASL_AUTHENTICATION_ENABLED));
    }
}
