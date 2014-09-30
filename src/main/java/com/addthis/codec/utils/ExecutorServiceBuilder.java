/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.addthis.codec.utils;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.addthis.codec.annotations.Time;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.MoreExecutors;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.yammer.metrics.Metrics;

@Beta
@JsonDeserialize(builder = ExecutorServiceBuilder.class)
public class ExecutorServiceBuilder {

    private final ThreadFactory threadFactory;
    private final int coreThreads;
    private final int maxThreads;
    private final int keepAlive;
    private final BlockingQueue<Runnable> queue;
    private final boolean shutdownHook;

    @JsonCreator
    public ExecutorServiceBuilder(@JsonProperty("thread-factory") ThreadFactory threadFactory,
                                  @JsonProperty("core-threads") int coreThreads,
                                  @JsonProperty("max-threads") int maxThreads,
                                  @JsonProperty("keep-alive") @Time(TimeUnit.MILLISECONDS) int keepAlive,
                                  @JsonProperty("queue-size") int queueSize,
                                  @JsonProperty("queue-gauge-class") Class<?> gaugeClass,
                                  @JsonProperty("queue-gauge-name") String gaugeName,
                                  @JsonProperty("shutdown-hook") boolean shutdownHook) {
        this.threadFactory = threadFactory;
        this.coreThreads = coreThreads;
        this.maxThreads = maxThreads;
        this.keepAlive = keepAlive;
        this.queue = new LinkedBlockingQueue<>(queueSize);
        if ((gaugeClass != null) && (gaugeName != null)) {
            Metrics.newGauge(gaugeClass, gaugeName, new SizeGauge(queue));
        }
        this.shutdownHook = shutdownHook;
    }

    public ExecutorService build() {
        ThreadPoolExecutor service = new ThreadPoolExecutor(coreThreads, maxThreads,
                                                            keepAlive, TimeUnit.MILLISECONDS,
                                                            queue, threadFactory);
        if (shutdownHook) {
            return MoreExecutors.getExitingExecutorService(service);
        } else {
            return service;
        }
    }
}
