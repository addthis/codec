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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import com.addthis.basis.kv.KVPairs;
import com.addthis.basis.util.LessBytes;
import com.addthis.basis.util.LessStrings;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CodecTest {
    private static final Logger log = LoggerFactory.getLogger(CodecTest.class);

    @Test
    public void testStartsWith() {
        assertTrue(LessBytes.startsWith(LessBytes.toBytes("12345"), LessBytes.toBytes("123")));
        assertFalse(LessBytes.startsWith(LessBytes.toBytes("12345"), LessBytes.toBytes("234")));
    }

    @Test
    public void testReplaceTrue() {
        byte[] buf = LessBytes.toBytes("abc 123 abc");
        byte[] pat = LessBytes.toBytes("123");
        byte[] rep = LessBytes.toBytes("xyzfoo");
        assertEquals("abc xyzfoo abc", LessBytes.toString(LessBytes.replace(buf, pat, rep)));
    }

    @Test
    public void testOverwriteTrue() {
        // test normal overwrite test
        byte[] buf1 = LessBytes.toBytes("abc 123 abc");
        byte[] pat1 = LessBytes.toBytes("123");
        byte[] rep1 = LessBytes.toBytes("xyzfoo");
        assertTrue(LessBytes.overwrite(buf1, pat1, rep1));

        assertEquals("abc xyzfooc", LessBytes.toString(buf1));
        // test replace buffer that would exceed base buffer (should be limited)
        byte[] buf2 = LessBytes.toBytes("abc 123 abc");
        byte[] pat2 = LessBytes.toBytes("123");
        byte[] rep2 = LessBytes.toBytes("xyzfoodfight");
        assertTrue(LessBytes.overwrite(buf2, pat2, rep2));
        assertEquals("abc xyzfood", LessBytes.toString(buf2));
    }

    @Test
    public void testOverwriteFalse() {
        byte[] buf1 = LessBytes.toBytes("abc 123 abc");
        byte[] pat1 = LessBytes.toBytes("888");
        byte[] rep1 = LessBytes.toBytes("xyzfoo");
        assertFalse(LessBytes.overwrite(buf1, pat1, rep1));
        assertEquals("abc 123 abc", LessBytes.toString(buf1));
    }

    @Test
    public void testCatCut() {
        byte[] b1 = LessBytes.toBytes("123");
        byte[] b2 = LessBytes.toBytes("abc");
        // cat
        assertEquals("123abc", LessBytes.toString(LessBytes.cat(b1, b2)));
        assertEquals("123abc123", LessBytes.toString(LessBytes.cat(b1, b2, b1)));
        assertEquals("123abc123abc", LessBytes.toString(LessBytes.cat(b1, b2, b1, b2)));
        assertEquals("123abc123abc123", LessBytes.toString(LessBytes.cat(b1, b2, b1, b2, b1)));
        // cut
        assertEquals("123a", LessBytes.toString(LessBytes.cut(LessBytes.cat(b1, b2), 0, 4)));
        assertEquals("3a", LessBytes.toString(LessBytes.cut(LessBytes.cat(b1, b2), 2, 2)));
        assertEquals("3abc", LessBytes.toString(LessBytes.cut(LessBytes.cat(b1, b2), 2, 4)));
    }

    @Test
    public void testNumCodec() {
        // long
        assertEquals(1L, LessBytes.toLong(LessBytes.toBytes(1L)));
        assertEquals(1000L, LessBytes.toLong(LessBytes.toBytes(1000L)));
        assertEquals(1000000000L, LessBytes.toLong(LessBytes.toBytes(1000000000L)));
        assertEquals(1234L, LessBytes.toUInt(null, 1234));
        assertEquals(1234L, LessBytes.toLong(null, 1234L));
        // int
        assertEquals(1, LessBytes.toInt(LessBytes.toBytes(1)));
        assertEquals(1000, LessBytes.toInt(LessBytes.toBytes(1000)));
        assertEquals(1000000000, LessBytes.toInt(LessBytes.toBytes(1000000000)));
        assertEquals(1234, LessBytes.toInt(null, 1234));
        // short
        assertEquals((short) 1, LessBytes.toShort(LessBytes.toBytes((short) 1)));
        assertEquals((short) 1000, LessBytes.toShort(LessBytes.toBytes((short) 1000)));
        assertEquals((short) 1000000000, LessBytes.toShort(LessBytes.toBytes((short) 1000000000)));
    }

    public void main(String[] args) throws Exception {
        log.info("decode 'abc+123' = {}", LessBytes.urldecode("abc+123"));

        for (String tok : LessStrings.split("abc def ghi jkl", " ")) {
            log.info("t1: {}", tok);
        }
        for (String tok : LessStrings.split("abc:def/ghi:jkl", ":/", true)) {
            log.info("t2: {}", tok);
        }

        byte[] s1 = LessBytes.toBytes("1");
        byte[] s2 = LessBytes.toBytes("2");
        byte[] s3 = LessBytes.toBytes("3");
        byte[] s4 = LessBytes.toBytes("4");

        log.info(LessStrings.cat("1", "2", "3"));
        log.info(LessStrings.cat("1", "2", "3", "4"));
        log.info(LessStrings.cat("1", "2", "3", "4", "5"));
        log.info(LessStrings.cat("1", "2", "3", "4", "5", "6"));

        log.info(LessBytes.toString(LessBytes.cat(s1, s2)));
        log.info(LessBytes.toString(LessBytes.cat(s1, s2, s3)));
        log.info(LessBytes.toString(LessBytes.cat(s1, s2, s3, s4)));

        test1(100);
        test1(200);
        test1(800);
        test1(17000);

        byte[] b = LessBytes.toBytes("Dear {{NAME}},\n" +
                                     "\n" +
                                     "Hello from Example and thanks for registering.\n" +
                                     "Use the following link to complete the registration for your account:\n" +
                                     "{{URL}}" +
                                     "\n" +
                                     "You can ignore this email if you did not request it.\n" +
                                     "\n" +
                                     "Regards,\n" +
                                     "The Example Team\n" +
                                     "http://www.example.com{{asdfasdf");

        KVPairs rep = new KVPairs();
        rep.putValue("URL", "http://asadsa.das.da.sd.ad.asd/asd");
        rep.putValue("NAME", "foo@example.com");
    }

    public static void test1(int len) throws IOException {
        byte[] b = new byte[len];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        LessBytes.writeBytes(b, bos);
        log.info("{} encoded to {}", b.length, bos.size());
        ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
        byte[] o = LessBytes.readBytes(bin);
        log.info("decoded to {}", o.length);
    }
}
