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
package org.apache.qpid.server.store.derby;


import java.io.File;
import java.sql.Blob;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.qpid.server.store.AbstractJDBCMessageStore;
import org.apache.qpid.server.store.Event;
import org.apache.qpid.server.store.EventListener;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreConstants;

/**
 * An implementation of a {@link MessageStore} that uses Apache Derby as the persistence
 * mechanism.
 *
 */
public class DerbyMessageStore extends AbstractJDBCMessageStore implements MessageStore
{

    private static final Logger _logger = Logger.getLogger(DerbyMessageStore.class);

    private static final String SQL_DRIVER_NAME = "org.apache.derby.jdbc.EmbeddedDriver";

    public static final String MEMORY_STORE_LOCATION = ":memory:";

    private static final String TABLE_EXISTANCE_QUERY = "SELECT 1 FROM SYS.SYSTABLES WHERE TABLENAME = ?";

    private static final String DERBY_SINGLE_DB_SHUTDOWN_CODE = "08006";

    public static final String TYPE = "DERBY";

    private long _totalStoreSize;
    private boolean _limitBusted;
    private long _persistentSizeLowThreshold;
    private long _persistentSizeHighThreshold;

    private String _storeLocation;
    private Class<Driver> _driverClass;

    public DerbyMessageStore()
    {
    }

    protected Logger getLogger()
    {
        return _logger;
    }

    @Override
    protected String getSqlBlobType()
    {
        return "blob";
    }

    @Override
    protected String getSqlVarBinaryType(int size)
    {
        return "varchar("+size+") for bit data";
    }

    @Override
    protected String getSqlBigIntType()
    {
        return "bigint";
    }

    protected void doClose() throws SQLException
    {
        try
        {
            Connection conn = DriverManager.getConnection(_connectionURL + ";shutdown=true");
            // Shouldn't reach this point - shutdown=true should throw SQLException
            conn.close();
            getLogger().error("Unable to shut down the store");
        }
        catch (SQLException e)
        {
            if (e.getSQLState().equalsIgnoreCase(DerbyMessageStore.DERBY_SINGLE_DB_SHUTDOWN_CODE))
            {
                //expected and represents a clean shutdown of this database only, do nothing.
            }
            else
            {
                getLogger().error("Exception whilst shutting down the store: " + e);
                throw e;
            }
        }
    }

    @Override
    protected void implementationSpecificConfiguration(String name, Configuration storeConfiguration)
            throws ClassNotFoundException
    {
        //Update to pick up QPID_WORK and use that as the default location not just derbyDB

        _driverClass = (Class<Driver>) Class.forName(SQL_DRIVER_NAME);

        final String databasePath = storeConfiguration.getString(MessageStoreConstants.ENVIRONMENT_PATH_PROPERTY, System.getProperty("QPID_WORK")
                + File.separator + "derbyDB");

        if(!MEMORY_STORE_LOCATION.equals(databasePath))
        {
            File environmentPath = new File(databasePath);
            if (!environmentPath.exists())
            {
                if (!environmentPath.mkdirs())
                {
                    throw new IllegalArgumentException("Environment path " + environmentPath + " could not be read or created. "
                        + "Ensure the path is correct and that the permissions are correct.");
                }
            }
        }

        _storeLocation = databasePath;

        _persistentSizeHighThreshold = storeConfiguration.getLong(MessageStoreConstants.OVERFULL_SIZE_PROPERTY, -1l);
        _persistentSizeLowThreshold = storeConfiguration.getLong(MessageStoreConstants.UNDERFULL_SIZE_PROPERTY, _persistentSizeHighThreshold);
        if(_persistentSizeLowThreshold > _persistentSizeHighThreshold || _persistentSizeLowThreshold < 0l)
        {
            _persistentSizeLowThreshold = _persistentSizeHighThreshold;
        }

        //FIXME this the _vhost name should not be added here, but derby wont use an empty directory as was possibly just created.
        _connectionURL = "jdbc:derby" + (databasePath.equals(MEMORY_STORE_LOCATION) ? databasePath: ":" + databasePath+ "/") + name + ";create=true";



        _eventManager.addEventListener(new EventListener()
                                        {
                                            @Override
                                            public void event(Event event)
                                            {
                                                setInitialSize();
                                            }
                                        }, Event.BEFORE_ACTIVATE);

    }

    private void setInitialSize()
    {
        Connection conn = null;
        try
        {


            try
            {
                conn = newAutoCommitConnection();
                _totalStoreSize = getSizeOnDisk(conn);
            }
            finally
            {
                if(conn != null)
                {
                        conn.close();


                }
            }
        }
        catch (SQLException e)
        {
            getLogger().error("Unable to set initial store size", e);
        }
    }

    protected String getBlobAsString(ResultSet rs, int col) throws SQLException
    {
        Blob blob = rs.getBlob(col);
        if(blob == null)
        {
            return null;
        }
        byte[] bytes = blob.getBytes(1, (int)blob.length());
        return new String(bytes, UTF8_CHARSET);
    }

    protected byte[] getBlobAsBytes(ResultSet rs, int col) throws SQLException
    {
        Blob dataAsBlob = rs.getBlob(col);
        return dataAsBlob.getBytes(1,(int) dataAsBlob.length());
    }


    protected boolean tableExists(final String tableName, final Connection conn) throws SQLException
    {
        PreparedStatement stmt = conn.prepareStatement(TABLE_EXISTANCE_QUERY);
        try
        {
            stmt.setString(1, tableName);
            ResultSet rs = stmt.executeQuery();
            try
            {
                return rs.next();
            }
            finally
            {
                rs.close();
            }
        }
        finally
        {
            stmt.close();
        }
    }


    @Override
    public String getStoreLocation()
    {
        return _storeLocation;
    }

    protected synchronized void storedSizeChange(final int delta)
    {
        if(getPersistentSizeHighThreshold() > 0)
        {
            synchronized(this)
            {
                // the delta supplied is an approximation of a store size change. we don;t want to check the statistic every
                // time, so we do so only when there's been enough change that it is worth looking again. We do this by
                // assuming the total size will change by less than twice the amount of the message data change.
                long newSize = _totalStoreSize += 3*delta;

                Connection conn = null;
                try
                {

                    if(!_limitBusted &&  newSize > getPersistentSizeHighThreshold())
                    {
                        conn = newAutoCommitConnection();
                        _totalStoreSize = getSizeOnDisk(conn);
                        if(_totalStoreSize > getPersistentSizeHighThreshold())
                        {
                            _limitBusted = true;
                            _eventManager.notifyEvent(Event.PERSISTENT_MESSAGE_SIZE_OVERFULL);
                        }
                    }
                    else if(_limitBusted && newSize < getPersistentSizeLowThreshold())
                    {
                        long oldSize = _totalStoreSize;
                        conn = newAutoCommitConnection();
                        _totalStoreSize = getSizeOnDisk(conn);
                        if(oldSize <= _totalStoreSize)
                        {

                            reduceSizeOnDisk(conn);

                            _totalStoreSize = getSizeOnDisk(conn);
                        }

                        if(_totalStoreSize < getPersistentSizeLowThreshold())
                        {
                            _limitBusted = false;
                            _eventManager.notifyEvent(Event.PERSISTENT_MESSAGE_SIZE_UNDERFULL);
                        }


                    }
                }
                catch (SQLException e)
                {
                    closeConnection(conn);
                    throw new RuntimeException("Exception while processing store size change", e);
                }
            }
        }
    }

    private void reduceSizeOnDisk(Connection conn)
    {
        CallableStatement cs = null;
        PreparedStatement stmt = null;
        try
        {
            String tableQuery =
                    "SELECT S.SCHEMANAME, T.TABLENAME FROM SYS.SYSSCHEMAS S, SYS.SYSTABLES T WHERE S.SCHEMAID = T.SCHEMAID AND T.TABLETYPE='T'";
            stmt = conn.prepareStatement(tableQuery);
            ResultSet rs = null;

            List<String> schemas = new ArrayList<String>();
            List<String> tables = new ArrayList<String>();

            try
            {
                rs = stmt.executeQuery();
                while(rs.next())
                {
                    schemas.add(rs.getString(1));
                    tables.add(rs.getString(2));
                }
            }
            finally
            {
                if(rs != null)
                {
                    rs.close();
                }
            }


            cs = conn.prepareCall
                    ("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(?, ?, ?)");

            for(int i = 0; i < schemas.size(); i++)
            {
                cs.setString(1, schemas.get(i));
                cs.setString(2, tables.get(i));
                cs.setShort(3, (short) 0);
                cs.execute();
            }
        }
        catch (SQLException e)
        {
            closeConnection(conn);
            throw new RuntimeException("Error reducing on disk size", e);
        }
        finally
        {
            closePreparedStatement(stmt);
            closePreparedStatement(cs);
        }

    }

    private long getSizeOnDisk(Connection conn)
    {
        PreparedStatement stmt = null;
        try
        {
            String sizeQuery = "SELECT SUM(T2.NUMALLOCATEDPAGES * T2.PAGESIZE) TOTALSIZE" +
                    "    FROM " +
                    "        SYS.SYSTABLES systabs," +
                    "        TABLE (SYSCS_DIAG.SPACE_TABLE(systabs.tablename)) AS T2" +
                    "    WHERE systabs.tabletype = 'T'";

            stmt = conn.prepareStatement(sizeQuery);

            ResultSet rs = null;
            long size = 0l;

            try
            {
                rs = stmt.executeQuery();
                while(rs.next())
                {
                    size = rs.getLong(1);
                }
            }
            finally
            {
                if(rs != null)
                {
                    rs.close();
                }
            }

            return size;

        }
        catch (SQLException e)
        {
            closeConnection(conn);
            throw new RuntimeException("Error establishing on disk size", e);
        }
        finally
        {
            closePreparedStatement(stmt);
        }

    }


    private long getPersistentSizeLowThreshold()
    {
        return _persistentSizeLowThreshold;
    }

    private long getPersistentSizeHighThreshold()
    {
        return _persistentSizeHighThreshold;
    }

    @Override
    public String getStoreType()
    {
        return TYPE;
    }

}
