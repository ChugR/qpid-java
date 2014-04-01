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
import java.util.Collections;

import org.apache.qpid.test.utils.QpidTestCase;
import org.apache.qpid.util.FileUtils;

import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.Environment;

public class StandardEnvironmentFacadeTest extends QpidTestCase
{
    protected File _storePath;
    protected EnvironmentFacade _environmentFacade;

    protected void setUp() throws Exception
    {
        super.setUp();
        _storePath = new File(TMP_FOLDER + File.separator + "bdb" + File.separator + getTestName());
    }

    protected void tearDown() throws Exception
    {
        try
        {
            super.tearDown();
            if (_environmentFacade != null)
            {
                _environmentFacade.close();
            }
        }
        finally
        {
            if (_storePath != null)
            {
                FileUtils.delete(_storePath, true);
            }
        }
    }

    public void testEnvironmentFacade() throws Exception
    {
        EnvironmentFacade ef = getEnvironmentFacade();
        assertNotNull("Environment should not be null", ef);
        Environment e = ef.getEnvironment();
        assertTrue("Environment is not valid", e.isValid());
    }

    public void testClose() throws Exception
    {
        EnvironmentFacade ef = getEnvironmentFacade();
        ef.close();
        Environment e = ef.getEnvironment();

        assertNull("Environment should be null after facade close", e);
    }

    public void testOpenDatabases() throws Exception
    {
        EnvironmentFacade ef = getEnvironmentFacade();
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        ef.openDatabases(dbConfig, "test1", "test2");
        Database test1 = ef.getOpenDatabase("test1");
        Database test2 = ef.getOpenDatabase("test2");

        assertEquals("Unexpected name for open database test1", "test1" , test1.getDatabaseName());
        assertEquals("Unexpected name for open database test2", "test2" , test2.getDatabaseName());
    }

    public void testGetOpenDatabaseForNonExistingDatabase() throws Exception
    {
        EnvironmentFacade ef = getEnvironmentFacade();
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setTransactional(true);
        dbConfig.setAllowCreate(true);
        ef.openDatabases(dbConfig, "test1");
        Database test1 = ef.getOpenDatabase("test1");
        assertEquals("Unexpected name for open database test1", "test1" , test1.getDatabaseName());
        try
        {
            ef.getOpenDatabase("test2");
            fail("An exception should be thrown for the non existing database");
        }
        catch(IllegalArgumentException e)
        {
            assertEquals("Unexpected exception message", "Database with name 'test2' has not been opened", e.getMessage());
        }
    }

    EnvironmentFacade getEnvironmentFacade() throws Exception
    {
        if (_environmentFacade == null)
        {
            _environmentFacade = createEnvironmentFacade();
        }
        return _environmentFacade;
    }

    EnvironmentFacade createEnvironmentFacade()
    {
        return new StandardEnvironmentFacade(_storePath.getAbsolutePath(), Collections.<String, String>emptyMap(), null);
    }

}
