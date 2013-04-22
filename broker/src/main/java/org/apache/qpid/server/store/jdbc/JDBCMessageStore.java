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
package org.apache.qpid.server.store.jdbc;


import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.apache.qpid.AMQStoreException;
import org.apache.qpid.server.store.AbstractJDBCMessageStore;
import org.apache.qpid.server.store.MessageStore;
import org.apache.qpid.server.store.MessageStoreConstants;
import org.apache.qpid.server.store.StoreFuture;
import org.apache.qpid.server.store.Transaction;

/**
 * An implementation of a {@link org.apache.qpid.server.store.MessageStore} that uses a JDBC database as the persistence
 * mechanism.
 *
 */
public class JDBCMessageStore extends AbstractJDBCMessageStore implements MessageStore
{

    private static final Logger _logger = Logger.getLogger(JDBCMessageStore.class);


    public static final String TYPE = "JDBC";


    private static class JDBCDetails
    {
        private final String _vendor;
        private String _blobType;
        private String _varBinaryType;
        private String _bigintType;
        private boolean _useBytesMethodsForBlob;

        private JDBCDetails(String vendor,
                            String blobType,
                            String varBinaryType,
                            String bigIntType,
                            boolean useBytesMethodsForBlob)
        {
            _vendor = vendor;
            setBlobType(blobType);
            setVarBinaryType(varBinaryType);
            setBigintType(bigIntType);
            setUseBytesMethodsForBlob(useBytesMethodsForBlob);
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (o == null || getClass() != o.getClass())
            {
                return false;
            }

            JDBCDetails that = (JDBCDetails) o;

            if (!getVendor().equals(that.getVendor()))
            {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode()
        {
            return getVendor().hashCode();
        }

        @Override
        public String toString()
        {
            return "JDBCDetails{" +
                   "vendor='" + getVendor() + '\'' +
                   ", blobType='" + getBlobType() + '\'' +
                   ", varBinaryType='" + getVarBinaryType() + '\'' +
                   ", bigIntType='" + getBigintType() + '\'' +
                   ", useBytesMethodsForBlob=" + isUseBytesMethodsForBlob() +
                   '}';
        }

        public String getVendor()
        {
            return _vendor;
        }

        public String getBlobType()
        {
            return _blobType;
        }

        public void setBlobType(String blobType)
        {
            _blobType = blobType;
        }

        public String getVarBinaryType()
        {
            return _varBinaryType;
        }

        public void setVarBinaryType(String varBinaryType)
        {
            _varBinaryType = varBinaryType;
        }

        public boolean isUseBytesMethodsForBlob()
        {
            return _useBytesMethodsForBlob;
        }

        public void setUseBytesMethodsForBlob(boolean useBytesMethodsForBlob)
        {
            _useBytesMethodsForBlob = useBytesMethodsForBlob;
        }

        public String getBigintType()
        {
            return _bigintType;
        }

        public void setBigintType(String bigintType)
        {
            _bigintType = bigintType;
        }
    }

    private static JDBCDetails DERBY_DETAILS =
            new JDBCDetails("derby",
                            "blob",
                            "varchar(%d) for bit data",
                            "bigint",
                            false);

    private static JDBCDetails POSTGRESQL_DETAILS =
            new JDBCDetails("postgresql",
                            "bytea",
                            "bytea",
                            "bigint",
                            true);

    private static JDBCDetails MYSQL_DETAILS =
            new JDBCDetails("mysql",
                            "blob",
                            "varbinary(%d)",
                            "bigint",
                            false);


    private static JDBCDetails SYBASE_DETAILS =
            new JDBCDetails("sybase",
                            "image",
                            "varbinary(%d)",
                            "bigint",
                            false);


    private static JDBCDetails ORACLE_DETAILS =
            new JDBCDetails("oracle",
                            "blob",
                            "raw(%d)",
                            "number",
                            false);


    private static Map<String, JDBCDetails> VENDOR_DETAILS = new HashMap<String,JDBCDetails>();

    static
    {

        addDetails(DERBY_DETAILS);
        addDetails(POSTGRESQL_DETAILS);
        addDetails(MYSQL_DETAILS);
        addDetails(SYBASE_DETAILS);
        addDetails(ORACLE_DETAILS);
    }

    private static void addDetails(JDBCDetails details)
    {
        VENDOR_DETAILS.put(details.getVendor(), details);
    }

    private String _blobType;
    private String _varBinaryType;
    private String _bigIntType;
    private boolean _useBytesMethodsForBlob;

    private List<RecordedJDBCTransaction> _transactions = new CopyOnWriteArrayList<RecordedJDBCTransaction>();


    public JDBCMessageStore()
    {
    }

    protected Logger getLogger()
    {
        return _logger;
    }

    protected String getSqlBlobType()
    {
        return _blobType;
    }

    protected String getSqlVarBinaryType(int size)
    {
        return String.format(_varBinaryType, size);
    }

    public String getSqlBigIntType()
    {
        return _bigIntType;
    }

    @Override
    protected void doClose() throws AMQStoreException
    {
        while(!_transactions.isEmpty())
        {
            RecordedJDBCTransaction txn = _transactions.get(0);
            txn.abortTran();
        }
    }

    protected void implementationSpecificConfiguration(String name, Configuration storeConfiguration)
            throws ClassNotFoundException
    {

        _connectionURL = storeConfiguration.getString("connectionUrl",
                storeConfiguration.getString(MessageStoreConstants.ENVIRONMENT_PATH_PROPERTY));

        JDBCDetails details = null;

        String[] components = _connectionURL.split(":",3);
        if(components.length >= 2)
        {
            String vendor = components[1];
            details = VENDOR_DETAILS.get(vendor);
        }

        if(details == null)
        {
            getLogger().info("Do not recognize vendor from connection URL: " + _connectionURL);

            // TODO - is there a better default than derby
            details = DERBY_DETAILS;
        }

        _blobType = storeConfiguration.getString("sqlBlobType",details.getBlobType());
        _varBinaryType = storeConfiguration.getString("sqlVarbinaryType",details.getVarBinaryType());
        _useBytesMethodsForBlob = storeConfiguration.getBoolean("useBytesForBlob",details.isUseBytesMethodsForBlob());
        _bigIntType = storeConfiguration.getString("sqlBigIntType", details.getBigintType());
    }

    protected void storedSizeChange(int contentSize)
    {
    }

    @Override
    public String getStoreLocation()
    {
        return "";
    }

    @Override
    public String getStoreType()
    {
        return TYPE;
    }

    @Override
    protected byte[] getBlobAsBytes(ResultSet rs, int col) throws SQLException
    {
        if(_useBytesMethodsForBlob)
        {
            return rs.getBytes(col);
        }
        else
        {
            Blob dataAsBlob = rs.getBlob(col);
            return dataAsBlob.getBytes(1,(int) dataAsBlob.length());

        }
    }

    @Override
    protected String getBlobAsString(ResultSet rs, int col) throws SQLException
    {
        byte[] bytes;
        if(_useBytesMethodsForBlob)
        {
            bytes = rs.getBytes(col);
            return new String(bytes,UTF8_CHARSET);
        }
        else
        {
            Blob blob = rs.getBlob(col);
            if(blob == null)
            {
                return null;
            }
            bytes = blob.getBytes(1, (int)blob.length());
        }
        return new String(bytes, UTF8_CHARSET);

    }

    @Override
    public Transaction newTransaction()
    {
        return new RecordedJDBCTransaction();
    }

    private class RecordedJDBCTransaction extends JDBCTransaction
    {
        private RecordedJDBCTransaction()
        {
            super();
            JDBCMessageStore.this._transactions.add(this);
        }

        @Override
        public void commitTran() throws AMQStoreException
        {
            try
            {
                super.commitTran();
            }
            finally
            {
                JDBCMessageStore.this._transactions.remove(this);
            }
        }

        @Override
        public StoreFuture commitTranAsync() throws AMQStoreException
        {
            try
            {
                return super.commitTranAsync();
            }
            finally
            {
                JDBCMessageStore.this._transactions.remove(this);
            }
        }

        @Override
        public void abortTran() throws AMQStoreException
        {
            try
            {
                super.abortTran();
            }
            finally
            {
                JDBCMessageStore.this._transactions.remove(this);
            }
        }
    }

}
