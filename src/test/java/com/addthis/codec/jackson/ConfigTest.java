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
package com.addthis.codec.jackson;

import java.util.concurrent.ArrayBlockingQueue;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigTest {

    private static final Logger log = LoggerFactory.getLogger(ConfigTest.class);

    static class QueueHolder {
        public ArrayBlockingQueue<?> q;

        public QueueHolder(int i) {
            q = new ArrayBlockingQueue<Object>(i);
        }

        public QueueHolder(boolean b) {
            if (b) {
                q = new ArrayBlockingQueue<Object>(1000);
            } else {
                q = new ArrayBlockingQueue<Object>(1000 / 2);
            }
        }

        public QueueHolder(int i, boolean b) {
            if (b) {
                q = new ArrayBlockingQueue<Object>(i);
            } else {
                q = new ArrayBlockingQueue<Object>(i / 2);
            }
        }
    }

    static class HolderHolder {
        public QueueHolder qh;
    }

    static class IntHolder {
        @JsonProperty("inty1") public int inty;

        public IntHolder(@JsonProperty("inty2") int inty) {
            log.info("IntHolder constructor called");
            this.inty = inty;
        }
    }

    static class IntHolderA {
        @JsonProperty("inty") int inty;

        public int returnInty() {
            return inty;
        }
    }

    static class IntHolderB extends IntHolderA {
        @JsonProperty("inty") int inty;
    }

    @Test
    public void someTest() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode node = mapper.createObjectNode().put("qh", false);
        QueueHolder myQueue = mapper.treeToValue(node, HolderHolder.class).qh;
        log.info("mqQueue.q {} ; size {}", myQueue.q, myQueue.q.remainingCapacity());
    }

    @Test
    public void someIntTest() throws Exception {
        IntHolder intHolder = Jackson.newDefault(IntHolder.class);
        log.info("IntHolder.inty {}", intHolder.inty);
        ObjectNode node = Jackson.defaultMapper()
                                 .createObjectNode()
                                 .put("inty2", 10)
                                 .put("inty1", 12);
        intHolder = CodecJackson.getDefault().decodeObject(IntHolder.class, node);
        log.info("IntHolder.inty {}", intHolder.inty);
    }

    @Test
    public void someIntTest2() throws Exception {
        ObjectMapper mapper = Jackson.defaultMapper();
        ObjectNode node = mapper.createObjectNode()
                                .put("inty", "10");
        IntHolderB intHolder = mapper.treeToValue(node, IntHolderB.class);
        log.info("IntHolder.inty {} returnInty {}", intHolder.inty, intHolder.returnInty());
    }
}
