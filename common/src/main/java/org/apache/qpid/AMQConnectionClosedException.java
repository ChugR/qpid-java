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
package org.apache.qpid;

import org.apache.qpid.protocol.AMQConstant;

/**
 * AMQConnectionClosedException indicates that an operation cannot be performed becauase a connection has been closed.
 *
 * <p/><table id="crc"><caption>CRC Card</caption>
 * <tr><th> Responsibilities <th> Collaborations
 * <tr><td> Represents a failed operation on a closed conneciton.
 * </table>
 *
 * @todo Does this duplicate AMQConnectionException?
 */
public class AMQConnectionClosedException extends AMQException
{
    public AMQConnectionClosedException(AMQConstant errorCode, String msg)
    {
        super(errorCode, msg);
    }
}
