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

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NetworkConnectionScheduler
{
    private static final Logger LOGGER = LoggerFactory.getLogger(NetworkConnectionScheduler.class);

    private final SelectorThread _selectorThread;
    private final ThreadPoolExecutor _executor;
    private final AtomicInteger _running = new AtomicInteger();
    private final int _poolSize;

    NetworkConnectionScheduler(final SelectorThread selectorThread, final NonBlockingNetworkTransport transport)
    {
        _selectorThread = selectorThread;
        _poolSize = transport.getThreadPoolSize();
        _executor = new ThreadPoolExecutor(_poolSize, _poolSize, 0L, TimeUnit.MILLISECONDS,
                                           new LinkedBlockingQueue<Runnable>(), new ThreadFactory()
        {
            final AtomicInteger _count = new AtomicInteger();

            @Override
            public Thread newThread(final Runnable r)
            {
                Thread t = Executors.defaultThreadFactory().newThread(r);
                t.setName("IO-pool-"+selectorThread.getName()+"-"+_count.incrementAndGet());
                return t;
            }
        });
        _executor.prestartAllCoreThreads();
    }

    public void schedule(final NonBlockingConnection connection)
    {
        _executor.execute(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                String currentName = Thread.currentThread().getName();
                                try
                                {
                                    Thread.currentThread().setName(
                                            SelectorThread.IO_THREAD_NAME_PREFIX + connection.getRemoteAddress().toString());
                                    processConnection(connection);
                                }
                                finally
                                {
                                    Thread.currentThread().setName(currentName);
                                }
                            }
                        });
    }

    private void processConnection(final NonBlockingConnection connection)
    {
        try
        {
            _running.incrementAndGet();
            boolean rerun;
            do
            {
                rerun = false;
                boolean closed = connection.doWork();

                if (!closed)
                {

                    if (connection.isStateChanged() || connection.isPartialRead())
                    {
                        if (_running.get() == _poolSize)
                        {
                            schedule(connection);
                        }
                        else
                        {
                            rerun = true;
                        }
                    }
                    else
                    {
                        _selectorThread.addConnection(connection);
                    }
                }

            } while (rerun);
        }
        finally
        {
            _running.decrementAndGet();
        }
    }

    public void close()
    {
        _executor.shutdown();
    }



}
