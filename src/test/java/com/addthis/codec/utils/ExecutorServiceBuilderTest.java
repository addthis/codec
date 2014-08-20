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

import java.io.IOException;

import java.util.concurrent.ExecutorService;

import com.addthis.codec.jackson.CodecJackson;
import com.addthis.codec.jackson.Jackson;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecutorServiceBuilderTest {
    private static final Logger log = LoggerFactory.getLogger(ExecutorServiceBuilderTest.class);

    @Test
    public void dontError() throws IOException {
        CodecJackson codecJackson = Jackson.defaultCodec();
        ExecutorBuilders.mix(codecJackson.getObjectMapper());
        ExecutorService service =
                codecJackson.decodeObject(ExecutorService.class,
                                          "thread-factory {nameFormat = ianRules}, core-threads = 2," +
                                          "max-threads = 5, keep-alive = 0, queue-size = 100");
        log.debug("service {}", service);
    }
}