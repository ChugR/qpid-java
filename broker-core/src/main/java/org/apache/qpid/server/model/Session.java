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
package org.apache.qpid.server.model;

import java.util.Collection;
import java.util.Date;

@ManagedObject
public interface Session<X extends Session<X>> extends ConfiguredObject<X>
{
    String CHANNEL_ID = "channelId";
    // PRODUCER_FLOW_BLOCKED is exposed as an interim step.  We will expose attribute(s) that exposing
    // available credit of both producer and consumer sides.
    String PRODUCER_FLOW_BLOCKED = "producerFlowBlocked";

    @DerivedAttribute
    int getChannelId();

    @DerivedAttribute
    boolean isProducerFlowBlocked();


    Collection<Consumer> getConsumers();
    Collection<Publisher> getPublishers();

    @ManagedStatistic(statisticType = StatisticType.POINT_IN_TIME, units = StatisticUnit.COUNT, label = "Consumers")
    long getConsumerCount();

    @ManagedStatistic(statisticType = StatisticType.CUMULATIVE, units = StatisticUnit.COUNT, label = "Transactions")
    long getLocalTransactionBegins();

    @ManagedStatistic(statisticType = StatisticType.POINT_IN_TIME, units = StatisticUnit.COUNT, label = "Open Transactions")
    int getLocalTransactionOpen();

    @ManagedStatistic(statisticType = StatisticType.CUMULATIVE, units = StatisticUnit.COUNT, label = "Rolled-back Transactions")
    long getLocalTransactionRollbacks();

    @ManagedStatistic(statisticType = StatisticType.POINT_IN_TIME, units = StatisticUnit.MESSAGES, label = "Prefetched")
    long getUnacknowledgedMessages();

    /**
     * Return the time the current transaction started.
     *
     * @return the time this transaction started or 0 if not in a transaction
     */
    @ManagedStatistic(statisticType = StatisticType.POINT_IN_TIME, units = StatisticUnit.ABSOLUTE_TIME, label = "Last Transaction Start")
    Date getTransactionStartTime();

    /**
     * Return the time of the last activity on the current transaction.
     *
     * @return the time of the last activity or 0 if not in a transaction
     */
    @ManagedStatistic(statisticType = StatisticType.POINT_IN_TIME, units = StatisticUnit.ABSOLUTE_TIME, label = "Last Transaction Update")
    Date getTransactionUpdateTime();
}
