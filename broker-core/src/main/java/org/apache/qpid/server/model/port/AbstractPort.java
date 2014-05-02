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

package org.apache.qpid.server.model.port;

import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.qpid.server.configuration.IllegalConfigurationException;
import org.apache.qpid.server.model.AbstractConfiguredObject;
import org.apache.qpid.server.model.Broker;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.Connection;
import org.apache.qpid.server.model.KeyStore;
import org.apache.qpid.server.model.ManagedAttributeField;
import org.apache.qpid.server.model.Port;
import org.apache.qpid.server.model.Protocol;
import org.apache.qpid.server.model.State;
import org.apache.qpid.server.model.Transport;
import org.apache.qpid.server.model.TrustStore;
import org.apache.qpid.server.model.VirtualHost;
import org.apache.qpid.server.model.VirtualHostAlias;
import org.apache.qpid.server.model.VirtualHostNode;
import org.apache.qpid.server.security.access.Operation;
import org.apache.qpid.server.util.MapValueConverter;

abstract public class AbstractPort<X extends AbstractPort<X>> extends AbstractConfiguredObject<X> implements Port<X>
{

    private final Broker<?> _broker;
    private AtomicReference<State> _state;

    @ManagedAttributeField
    private int _port;

    @ManagedAttributeField
    private String _bindingAddress;

    @ManagedAttributeField
    private KeyStore<?> _keyStore;

    @ManagedAttributeField
    private Collection<TrustStore> _trustStores;

    @ManagedAttributeField
    private Set<Transport> _transports;

    @ManagedAttributeField
    private Set<Protocol> _protocols;

    public AbstractPort(Map<String, Object> attributes,
                        Broker<?> broker)
    {
        super(parentsMap(broker), attributes);

        _broker = broker;

        State state = MapValueConverter.getEnumAttribute(State.class, STATE, attributes, State.INITIALISING);
        _state = new AtomicReference<State>(state);
    }


    @Override
    public void onValidate()
    {
        super.onValidate();

        boolean useTLSTransport = getTransports().contains(Transport.SSL) || getTransports().contains(Transport.WSS);

        if(useTLSTransport && getKeyStore() == null)
        {
            throw new IllegalConfigurationException("Can't create a port which uses a secure transport but has no KeyStore");
        }

        if(!isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }
    }

    @Override
    protected void validateChange(final ConfiguredObject<?> proxyForValidation, final Set<String> changedAttributes)
    {
        super.validateChange(proxyForValidation, changedAttributes);
        if(changedAttributes.contains(DURABLE) && !proxyForValidation.isDurable())
        {
            throw new IllegalArgumentException(getClass().getSimpleName() + " must be durable");
        }
        Port<?> updated = (Port<?>)proxyForValidation;


        if(!getName().equals(updated.getName()))
        {
            throw new IllegalConfigurationException("Changing the port name is not allowed");
        }

        if(changedAttributes.contains(PORT))
        {
            int newPort = updated.getPort();
            if (getPort() != newPort)
            {
                for (Port p : _broker.getPorts())
                {
                    if (p.getPort() == newPort)
                    {
                        throw new IllegalConfigurationException("Port number "
                                                                + newPort
                                                                + " is already in use by port "
                                                                + p.getName());
                    }
                }
            }
        }


        Collection<Transport> transports = updated.getTransports();

        Collection<Protocol> protocols = updated.getProtocols();


        boolean usesSsl = transports != null && transports.contains(Transport.SSL);
        if (usesSsl)
        {
            if (updated.getKeyStore() == null)
            {
                throw new IllegalConfigurationException("Can't create port which requires SSL but has no key store configured.");
            }
        }

        if (protocols != null && protocols.contains(Protocol.RMI) && usesSsl)
        {
            throw new IllegalConfigurationException("Can't create RMI Registry port which requires SSL.");
        }

    }

    @Override
    public String getBindingAddress()
    {
        return _bindingAddress;
    }

    @Override
    public int getPort()
    {
        return _port;
    }

    @Override
    public Set<Transport> getTransports()
    {
        return _transports;
    }

    @Override
    public void addTransport(Transport transport)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @Override
    public Transport removeTransport(Transport transport)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @Override
    public Set<Protocol> getProtocols()
    {
        return _protocols;
    }

    @Override
    public void addProtocol(Protocol protocol)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @Override
    public Protocol removeProtocol(Protocol protocol)
            throws IllegalStateException, AccessControlException, IllegalArgumentException
    {
        throw new IllegalStateException();
    }

    @Override
    public Collection<VirtualHostAlias> getVirtualHostBindings()
    {
        List<VirtualHostAlias> aliases = new ArrayList<VirtualHostAlias>();
        for(VirtualHostNode<?> vhn : _broker.getVirtualHostNodes())
        {
            VirtualHost<?, ?, ?> vh = vhn.getVirtualHost();
            if (vh != null)
            {
                for(VirtualHostAlias<?> alias : vh.getAliases())
                {
                    if(alias.getPort().equals(this))
                    {
                        aliases.add(alias);
                    }
                }
            }
        }
        return Collections.unmodifiableCollection(aliases);
    }

    @Override
    public Collection<Connection> getConnections()
    {
        return null;
    }

    @Override
    public Set<Protocol> getAvailableProtocols()
    {
        Set<Protocol> protocols = getProtocols();
        if(protocols == null || protocols.isEmpty())
        {
            protocols = getDefaultProtocols();
        }
        return protocols;
    }

    protected abstract Set<Protocol> getDefaultProtocols();

    @Override
    public State getState()
    {
        return _state.get();
    }


    @Override
    public <C extends ConfiguredObject> Collection<C> getChildren(Class<C> clazz)
    {
        if(clazz == Connection.class)
        {
            return (Collection<C>) getConnections();
        }
        else
        {
            return Collections.emptySet();
        }
    }

    @Override
    public Object getAttribute(String name)
    {
        if(STATE.equals(name))
        {
            return getState();
        }
        return super.getAttribute(name);
    }

    @Override
    public boolean setState(State desiredState)
    {
        State state = _state.get();
        if (desiredState == State.DELETED)
        {
            if (state == State.INITIALISING || state == State.ACTIVE || state == State.STOPPED || state == State.QUIESCED  || state == State.ERRORED)
            {
                if( _state.compareAndSet(state, State.DELETED))
                {
                    onStop();
                    deleted();
                    return true;
                }
            }
            else
            {
                throw new IllegalStateException("Cannot delete port in " + state + " state");
            }
        }
        else if (desiredState == State.ACTIVE)
        {
            if ((state == State.INITIALISING || state == State.QUIESCED) && _state.compareAndSet(state, State.ACTIVE))
            {
                try
                {
                    onActivate();
                }
                catch(RuntimeException e)
                {
                    _state.compareAndSet(State.ACTIVE, State.ERRORED);
                    throw e;
                }
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot activate port in " + state + " state");
            }
        }
        else if (desiredState == State.QUIESCED)
        {
            if (state == State.INITIALISING && _state.compareAndSet(state, State.QUIESCED))
            {
                return true;
            }
        }
        else if (desiredState == State.STOPPED)
        {
            if (_state.compareAndSet(state, State.STOPPED))
            {
                onStop();
                return true;
            }
            else
            {
                throw new IllegalStateException("Cannot stop port in " + state + " state");
            }
        }
        return false;
    }

    protected void onActivate()
    {
        // no-op: expected to be overridden by subclass
    }

    protected void onStop()
    {
        // no-op: expected to be overridden by subclass
    }


    @Override
    protected void authoriseSetDesiredState(State desiredState) throws AccessControlException
    {
        if(desiredState == State.DELETED)
        {
            if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), Port.class, Operation.DELETE))
            {
                throw new AccessControlException("Deletion of port is denied");
            }
        }
    }

    @Override
    protected void authoriseSetAttributes(ConfiguredObject<?> modified, Set<String> attributes) throws AccessControlException
    {
        if (!_broker.getSecurityManager().authoriseConfiguringBroker(getName(), Port.class, Operation.UPDATE))
        {
            throw new AccessControlException("Setting of port attributes is denied");
        }
    }

    @Override
    public KeyStore getKeyStore()
    {
        return _keyStore;
    }

    @Override
    public Collection<TrustStore> getTrustStores()
    {
        return _trustStores;
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " [id=" + getId() + ", name=" + getName() + ", port=" + getPort() + "]";
    }


    protected void validateOnlyOneInstance()
    {
        Broker<?> broker = getParent(Broker.class);
        if(!broker.isManagementMode())
        {
            //ManagementMode needs this relaxed to allow its overriding management ports to be inserted.

            //Enforce only a single port of each management protocol, as the plugins will only use one.
            Collection<Port<?>> existingPorts = new HashSet<Port<?>>(broker.getPorts());
            existingPorts.remove(this);

            for (Port<?> existingPort : existingPorts)
            {
                Collection<Protocol> portProtocols = existingPort.getAvailableProtocols();
                if (portProtocols != null)
                {
                    final ArrayList<Protocol> intersection = new ArrayList(portProtocols);
                    intersection.retainAll(getAvailableProtocols());
                    if(!intersection.isEmpty())
                    {
                        throw new IllegalConfigurationException("Port for protocols " + intersection + " already exists. Only one management port per protocol can be created.");
                    }
                }
            }
        }
    }
}
