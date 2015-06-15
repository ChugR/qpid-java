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
package org.apache.qpid.server.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;

public class LoggerNameAndLevelFilter extends Filter<ILoggingEvent>
{
    private final Filter<ILoggingEvent> _filter;
    private final String _loggerName;
    private volatile Level _level;

    public LoggerNameAndLevelFilter(String loggerName, Level level)
    {
        _level = level;
        _loggerName = loggerName;
        _filter = createFilter(loggerName);
    }

    @Override
    public FilterReply decide(ILoggingEvent event)
    {
        return _filter.decide(event);
    }

    public void setLevel(Level level)
    {
        _level = level;
    }

    public Level getLevel()
    {
        return _level;
    }

    public String getLoggerName()
    {
        return _loggerName;
    }

    private Filter<ILoggingEvent> createFilter(final String loggerName)
    {
        if(loggerName == null || "".equals(loggerName) || Logger.ROOT_LOGGER_NAME.equals(loggerName))
        {
            return new Filter<ILoggingEvent>()
            {
                @Override
                public FilterReply decide(final ILoggingEvent event)
                {
                    return event.getLevel().isGreaterOrEqual(_level) ? FilterReply.ACCEPT : FilterReply.NEUTRAL;
                }
            };
        }
        else if(loggerName.endsWith(".*"))
        {
            final String prefixName = loggerName.substring(0,loggerName.length()-2);
            return new Filter<ILoggingEvent>()
            {
                @Override
                public FilterReply decide(final ILoggingEvent event)
                {
                    return event.getLevel().isGreaterOrEqual(_level) && event.getLoggerName().startsWith(prefixName) ? FilterReply.ACCEPT : FilterReply.NEUTRAL;
                }
            };
        }
        else
        {
            return new Filter<ILoggingEvent>()
            {
                @Override
                public FilterReply decide(final ILoggingEvent event)
                {
                    return event.getLevel().isGreaterOrEqual(_level) && event.getLoggerName().equals(loggerName) ? FilterReply.ACCEPT : FilterReply.NEUTRAL;
                }
            };
        }
    }
}
