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
package org.apache.qpid.server.protocol.v0_10;

import org.apache.qpid.protocol.ServerProtocolEngine;
import org.apache.qpid.server.logging.SystemLog;
import org.apache.qpid.server.logging.messages.ConnectionMessages;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.transport.Sender;
import org.apache.qpid.transport.network.Assembler;
import org.apache.qpid.transport.network.Disassembler;
import org.apache.qpid.transport.network.InputHandler;
import org.apache.qpid.transport.network.NetworkConnection;

import javax.security.auth.Subject;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedAction;


public class ProtocolEngine_0_10  extends InputHandler implements ServerProtocolEngine
{
    public static final int MAX_FRAME_SIZE = 64 * 1024 - 1;

    private NetworkConnection _network;
    private long _readBytes;
    private long _writtenBytes;
    private ServerConnection _connection;

    private long _createTime = System.currentTimeMillis();
    private long _lastReadTime;
    private long _lastWriteTime;

    public ProtocolEngine_0_10(ServerConnection conn,
                               NetworkConnection network,
                               Port port,
                               Transport transport)
    {
        super(new Assembler(conn));
        _connection = conn;
        _connection.setPort(port);
        _connection.setTransport(transport);

        if(network != null)
        {
            setNetworkConnection(network, network.getSender());
        }
    }

    public void setNetworkConnection(final NetworkConnection network, final Sender<ByteBuffer> sender)
    {
        if(!getSubject().equals(Subject.getSubject(AccessController.getContext())))
        {
            Subject.doAs(getSubject(), new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    setNetworkConnection(network,sender);
                    return null;
                }
            });
        }
        else
        {
            SystemLog.message(ConnectionMessages.OPEN(null, null, null, null, false, false, false, false));
            _network = network;

            _connection.setNetworkConnection(network);
            _connection.setSender(new Disassembler(wrapSender(sender), MAX_FRAME_SIZE));
            // FIXME Two log messages to maintain compatibility with earlier protocol versions
            SystemLog.message(ConnectionMessages.OPEN(null, "0-10", null, null, false, true, false, false));

        }
    }

    private Sender<ByteBuffer> wrapSender(final Sender<ByteBuffer> sender)
    {
        return new Sender<ByteBuffer>()
        {
            @Override
            public void setIdleTimeout(int i)
            {
                sender.setIdleTimeout(i);

            }

            @Override
            public void send(ByteBuffer msg)
            {
                _lastWriteTime = System.currentTimeMillis();
                sender.send(msg);

            }

            @Override
            public void flush()
            {
                sender.flush();

            }

            @Override
            public void close()
            {
                sender.close();

            }
        };
    }

    @Override
    public long getLastReadTime()
    {
        return _lastReadTime;
    }

    @Override
    public long getLastWriteTime()
    {
        return _lastWriteTime;
    }

    public SocketAddress getRemoteAddress()
    {
        return _network.getRemoteAddress();
    }

    public SocketAddress getLocalAddress()
    {
        return _network.getLocalAddress();
    }

    public void received(final ByteBuffer buf)
    {
        _lastReadTime = System.currentTimeMillis();
        super.received(buf);
        _connection.receivedComplete();
    }

    public long getReadBytes()
    {
        return _readBytes;
    }

    public long getWrittenBytes()
    {
        return _writtenBytes;
    }

    public void writerIdle()
    {
        _connection.doHeartBeat();
    }

    public void readerIdle()
    {
        Subject.doAs(_connection.getAuthorizedSubject(), new PrivilegedAction<Object>()
            {
                @Override
                public Object run()
                {
                    SystemLog.message(ConnectionMessages.IDLE_CLOSE());
                    _network.close();
                    return null;
                }
            });

    }

    public String getAddress()
    {
        return getRemoteAddress().toString();
    }

    public String getAuthId()
    {
        return _connection.getAuthorizedPrincipal() == null ? null : _connection.getAuthorizedPrincipal().getName();
    }

    public boolean isDurable()
    {
        return false;
    }

    @Override
    public void closed()
    {
        super.closed();
    }

    public long getCreateTime()
    {
        return _createTime;
    }

    public long getConnectionId()
    {
        return _connection.getConnectionId();
    }

    @Override
    public Subject getSubject()
    {
        return _connection.getAuthorizedSubject();
    }
}
