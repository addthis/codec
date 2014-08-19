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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WriteonlyPropertyIgnorerTest {
    private static final Logger log = LoggerFactory.getLogger(WriteonlyPropertyIgnorerTest.class);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void ignoreWriteonly() throws Exception {
        WriteMany writeMany = new WriteMany();
        writeMany.readWrite = 6;
        String serialized = Jackson.defaultMapper().writeValueAsString(writeMany);
        log.debug("serial writeMany {}", serialized);

        // should silently ignore "someInt"
        Config passingOverride =
                ConfigValueFactory.fromAnyRef(true).atPath("addthis.codec.jackson.ignore.write-only");
        Jackson.defaultCodec()
               .withOverrides(passingOverride)
               .getObjectMapper()
               .readValue(serialized, WriteMany.class);

        // should error on "someInt"
        thrown.expect(UnrecognizedPropertyException.class);
        Config erroringOverride =
                ConfigValueFactory.fromAnyRef(false).atPath("addthis.codec.jackson.ignore.write-only");
        Jackson.defaultCodec()
               .withOverrides(erroringOverride)
               .getObjectMapper()
               .readValue(serialized, WriteMany.class);
    }

    static class WriteMany {
        @JsonProperty public int readWrite;

        public int getSomeInt() {
            return 12;
        }
    }
}