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
package org.apache.qpid.server.transport;

import static org.apache.qpid.transport.ConnectionSettings.WILDCARD_ADDRESS;

import java.net.InetSocketAddress;
import java.util.Set;

import javax.net.ssl.SSLContext;

import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.protocol.MultiVersionProtocolEngineFactory;
import org.apache.qpid.transport.NetworkTransportConfiguration;
import org.apache.qpid.transport.network.IncomingNetworkTransport;

class TCPandSSLTransport implements AcceptingTransport
{
    private IncomingNetworkTransport _networkTransport;
    private Set<Transport> _transports;
    private SSLContext _sslContext;
    private InetSocketAddress _bindingSocketAddress;
    private Port<?> _port;
    private Set<Protocol> _supported;
    private Protocol _defaultSupportedProtocolReply;

    TCPandSSLTransport(final Set<Transport> transports,
                       final SSLContext sslContext,
                       final Port<?> port,
                       final Set<Protocol> supported,
                       final Protocol defaultSupportedProtocolReply)
    {
        _transports = transports;
        _sslContext = sslContext;
        _port = port;
        _supported = supported;
        _defaultSupportedProtocolReply = defaultSupportedProtocolReply;
    }

    @Override
    public void start()
    {
        String bindingAddress = (String) _port.getAttribute(Port.BINDING_ADDRESS);
        if (WILDCARD_ADDRESS.equals(bindingAddress))
        {
            bindingAddress = null;
        }
        Integer port = (Integer) _port.getAttribute(Port.PORT);
        if ( bindingAddress == null )
        {
            _bindingSocketAddress = new InetSocketAddress(port);
        }
        else
        {
            _bindingSocketAddress = new InetSocketAddress(bindingAddress, port);
        }

        final NetworkTransportConfiguration settings = new ServerNetworkTransportConfiguration();
        _networkTransport = org.apache.qpid.transport.network.Transport.getIncomingTransportInstance();
        final MultiVersionProtocolEngineFactory protocolEngineFactory =
                new MultiVersionProtocolEngineFactory(
                _port.getParent(Broker.class), _transports.contains(Transport.TCP) ? _sslContext : null,
                settings.wantClientAuth(), settings.needClientAuth(),
                _supported,
                _defaultSupportedProtocolReply,
                _port,
                _transports.contains(Transport.TCP) ? Transport.TCP : Transport.SSL);

        _networkTransport.accept(settings, protocolEngineFactory, _transports.contains(Transport.TCP) ? null : _sslContext);
    }

    @Override
    public void close()
    {
        _networkTransport.close();
    }

    class ServerNetworkTransportConfiguration implements NetworkTransportConfiguration
    {
        public ServerNetworkTransportConfiguration()
        {
        }

        @Override
        public boolean wantClientAuth()
        {
            return (Boolean)_port.getAttribute(Port.WANT_CLIENT_AUTH);
        }

        @Override
        public boolean needClientAuth()
        {
            return (Boolean)_port.getAttribute(Port.NEED_CLIENT_AUTH);
        }

        @Override
        public Boolean getTcpNoDelay()
        {
            return (Boolean)_port.getAttribute(Port.TCP_NO_DELAY);
        }

        @Override
        public Integer getSendBufferSize()
        {
            return (Integer)_port.getAttribute(AmqpPort.SEND_BUFFER_SIZE);
        }

        @Override
        public Integer getReceiveBufferSize()
        {
            return (Integer)_port.getAttribute(AmqpPort.RECEIVE_BUFFER_SIZE);
        }

        @Override
        public InetSocketAddress getAddress()
        {
            return _bindingSocketAddress;
        }

        @Override
        public String toString()
        {
            return _port.toString();
        }
    }
}
