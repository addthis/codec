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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

import com.google.common.annotations.Beta;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Adds deserialization support for {@link ThreadFactory} and {@link ExecutorService} types.
 */
@Beta
public class ExecutorsModule extends Module {

    @Override public void setupModule(SetupContext context) {
        context.setMixInAnnotations(ThreadFactory.class, ThreadFactoryMixer.class);
        context.setMixInAnnotations(ThreadFactoryBuilder.class, ThreadFactoryBuilderMixer.class);
        context.setMixInAnnotations(ExecutorService.class, ExecutorServiceBuilder.class);
        context.setMixInAnnotations(ScheduledExecutorService.class, ScheduledExecutorServiceBuilder.class);
    }

    @Override public String getModuleName() {
        return "jdk-executors";
    }

    @Override public Version version() {
        return Version.unknownVersion();
    }

    @JsonDeserialize(builder = ThreadFactoryBuilder.class)
    private static class ThreadFactoryMixer {}

    @JsonPOJOBuilder(withPrefix = "set")
    private static class ThreadFactoryBuilderMixer {}
}
