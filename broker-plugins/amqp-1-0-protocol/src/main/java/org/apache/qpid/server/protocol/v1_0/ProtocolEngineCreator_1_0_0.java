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
package org.apache.qpid.server.protocol.v1_0;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.qpid.server.model.AuthenticationProvider;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.port.AmqpPort;
import org.apache.qpid.server.plugin.PluggableService;
import org.apache.qpid.server.plugin.ProtocolEngineCreator;
import org.apache.qpid.server.protocol.ServerProtocolEngine;
import org.apache.qpid.server.security.auth.manager.AnonymousAuthenticationManager;
import org.apache.qpid.server.security.auth.manager.ExternalAuthenticationManagerImpl;
import org.apache.qpid.transport.network.AggregateTicker;
import org.apache.qpid.transport.network.NetworkConnection;

@PluggableService
public class ProtocolEngineCreator_1_0_0 implements ProtocolEngineCreator
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ProtocolEngineCreator_1_0_0.class);

    private static final byte[] AMQP_1_0_0_HEADER =
            new byte[] { (byte) 'A',
                         (byte) 'M',
                         (byte) 'Q',
                         (byte) 'P',
                         (byte) 0,
                         (byte) 1,
                         (byte) 0,
                         (byte) 0
            };

    public ProtocolEngineCreator_1_0_0()
    {
    }

    public Protocol getVersion()
    {
        return Protocol.AMQP_1_0;
    }


    public byte[] getHeaderIdentifier()
    {
        return AMQP_1_0_0_HEADER;
    }

    public ServerProtocolEngine newProtocolEngine(Broker<?> broker,
                                                  NetworkConnection network,
                                                  AmqpPort<?> port,
                                                  Transport transport,
                                                  long id, final AggregateTicker aggregateTicker)
    {
        final AuthenticationProvider<?> authenticationProvider = port.getAuthenticationProvider();

        Set<String> supportedMechanisms = new HashSet<>(authenticationProvider.getMechanisms());
        supportedMechanisms.removeAll(authenticationProvider.getDisabledMechanisms());
        if(!transport.isSecure())
        {
            supportedMechanisms.removeAll(authenticationProvider.getSecureOnlyMechanisms());
        }


        if(supportedMechanisms.contains(AnonymousAuthenticationManager.MECHANISM_NAME)
                || (supportedMechanisms.contains(ExternalAuthenticationManagerImpl.MECHANISM_NAME) && network.getPeerPrincipal() != null))
        {
            return new ProtocolEngine_1_0_0(network, broker, id, port, transport, aggregateTicker, false);
        }
        else
        {
            LOGGER.info(
                    "Attempt to connect using AMQP 1.0 without using SASL authentication on a port which does not support ANONYMOUS or EXTERNAL by "
                    + network.getRemoteAddress());
            return null;
        }
    }

    @Override
    public byte[] getSuggestedAlternativeHeader()
    {
        return ProtocolEngineCreator_1_0_0_SASL.getInstance().getHeaderIdentifier();
    }

    private static ProtocolEngineCreator INSTANCE = new ProtocolEngineCreator_1_0_0();

    public static ProtocolEngineCreator getInstance()
    {
        return INSTANCE;
    }

    @Override
    public String getType()
    {
        return getVersion().toString();
    }
}
