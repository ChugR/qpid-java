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

import org.apache.log4j.Logger;

import org.apache.qpid.server.message.MessageInstance;


class ExplicitAcceptDispositionChangeListener implements ServerSession.MessageDispositionChangeListener
{
    private static final Logger _logger = Logger.getLogger(ExplicitAcceptDispositionChangeListener.class);


    private final MessageInstance _entry;
    private final ConsumerTarget_0_10 _target;

    public ExplicitAcceptDispositionChangeListener(MessageInstance entry, ConsumerTarget_0_10 target)
    {
        _entry = entry;
        _target = target;
    }

    public void onAccept()
    {
        if(_target != null && _entry.isAcquiredBy(_target.getConsumer()))
        {
            _target.getSessionModel().acknowledge(_target, _entry);
        }
        else
        {
            _logger.warn("MessageAccept received for message which has not been acquired (likely client error)");
        }

    }

    public void onRelease(boolean setRedelivered)
    {
        if(_target != null && _entry.isAcquiredBy(_target.getConsumer()))
        {
            _target.release(_entry, setRedelivered);
        }
        else
        {
            _logger.warn("MessageRelease received for message which has not been acquired (likely client error)");
        }
    }

    public void onReject()
    {
        if(_target != null && _entry.isAcquiredBy(_target.getConsumer()))
        {
            _target.reject(_entry);
        }
        else
        {
            _logger.warn("MessageReject received for message which has not been acquired (likely client error)");
        }

    }

    public boolean acquire()
    {
        return _entry.acquire(_target.getConsumer());
    }


}
