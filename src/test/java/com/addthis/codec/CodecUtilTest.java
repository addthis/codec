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

import com.addthis.basis.util.LessBytes;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertTrue;

public class CodecUtilTest {
    private static final Logger log = LoggerFactory.getLogger(CodecUtilTest.class);

    private boolean compareTest(String a, String b) {
        int scomp = a.compareTo(b);
        int bcomp = LessBytes.compare(LessBytes.toBytes(a), LessBytes.toBytes(b));
        log.info("compareTest({},{}) sc({}) bc({})", a, b, scomp, bcomp);
        return scomp == bcomp;
    }

    @Test
    public void testAll() {
        assertTrue(compareTest("abc", "ab"));
        assertTrue(compareTest("abc", "abc"));
        assertTrue(compareTest("abc", "abcd"));
        assertTrue(compareTest("abc", "def"));
        assertTrue(compareTest("def", "abc"));
        assertTrue(compareTest("abcd", "acbd"));
    }
}
