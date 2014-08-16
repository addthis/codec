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
package com.addthis.codec;

import com.addthis.codec.jackson.CodecJackson;
import com.addthis.codec.json.CodecJSON;
import com.addthis.codec.letters.C;
import com.addthis.codec.letters.CCC;

import com.fasterxml.jackson.databind.DeserializationFeature;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CodecJsonBasicTest extends CodecBasicTests {

    @Override public Codec getCodec() {
        CodecJackson.getDefault().getObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return CodecJSON.INSTANCE;
    }

    @Test
    public void upgradesDowngrades() throws Exception {
        // upgrade on decode (C -> CC)
        CCC bdcu = codec.decode(new CCC(), byteEncodedSample);
        byte[] bdcue = codec.encode(bdcu);
        String s5 = "C->CC = " + bytesToString(bdcue, false);
        String s6 = "C->CC = " + bytesToString(bdcue, true);
        assertTrue(bdcu.check());
        // downgrade on decode (C -> X)
        C bdcd = codec.decode(new C(), byteEncodedSample);
        assertTrue(bdcd.check());
    }
}
