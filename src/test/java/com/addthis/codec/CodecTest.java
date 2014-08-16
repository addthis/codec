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
import com.addthis.basis.util.Bytes;
import com.addthis.basis.util.Strings;

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
        assertTrue(Bytes.startsWith(Bytes.toBytes("12345"), Bytes.toBytes("123")));
        assertFalse(Bytes.startsWith(Bytes.toBytes("12345"), Bytes.toBytes("234")));
    }

    @Test
    public void testReplaceTrue() {
        byte[] buf = Bytes.toBytes("abc 123 abc");
        byte[] pat = Bytes.toBytes("123");
        byte[] rep = Bytes.toBytes("xyzfoo");
        assertEquals("abc xyzfoo abc", Bytes.toString(Bytes.replace(buf, pat, rep)));
    }

    @Test
    public void testOverwriteTrue() {
        // test normal overwrite test
        byte[] buf1 = Bytes.toBytes("abc 123 abc");
        byte[] pat1 = Bytes.toBytes("123");
        byte[] rep1 = Bytes.toBytes("xyzfoo");
        assertTrue(Bytes.overwrite(buf1, pat1, rep1));

        assertEquals("abc xyzfooc", Bytes.toString(buf1));
        // test replace buffer that would exceed base buffer (should be limited)
        byte[] buf2 = Bytes.toBytes("abc 123 abc");
        byte[] pat2 = Bytes.toBytes("123");
        byte[] rep2 = Bytes.toBytes("xyzfoodfight");
        assertTrue(Bytes.overwrite(buf2, pat2, rep2));
        assertEquals("abc xyzfood", Bytes.toString(buf2));
    }

    @Test
    public void testOverwriteFalse() {
        byte[] buf1 = Bytes.toBytes("abc 123 abc");
        byte[] pat1 = Bytes.toBytes("888");
        byte[] rep1 = Bytes.toBytes("xyzfoo");
        assertFalse(Bytes.overwrite(buf1, pat1, rep1));
        assertEquals("abc 123 abc", Bytes.toString(buf1));
    }

    @Test
    public void testCatCut() {
        byte[] b1 = Bytes.toBytes("123");
        byte[] b2 = Bytes.toBytes("abc");
        // cat
        assertEquals("123abc", Bytes.toString(Bytes.cat(b1, b2)));
        assertEquals("123abc123", Bytes.toString(Bytes.cat(b1, b2, b1)));
        assertEquals("123abc123abc", Bytes.toString(Bytes.cat(b1, b2, b1, b2)));
        assertEquals("123abc123abc123", Bytes.toString(Bytes.cat(b1, b2, b1, b2, b1)));
        // cut
        assertEquals("123a", Bytes.toString(Bytes.cut(Bytes.cat(b1, b2), 0, 4)));
        assertEquals("3a", Bytes.toString(Bytes.cut(Bytes.cat(b1, b2), 2, 2)));
        assertEquals("3abc", Bytes.toString(Bytes.cut(Bytes.cat(b1, b2), 2, 4)));
    }

    @Test
    public void testNumCodec() {
        // long
        assertEquals(1L, Bytes.toLong(Bytes.toBytes(1L)));
        assertEquals(1000L, Bytes.toLong(Bytes.toBytes(1000L)));
        assertEquals(1000000000L, Bytes.toLong(Bytes.toBytes(1000000000L)));
        assertEquals(1234L, Bytes.toUInt(null, 1234));
        assertEquals(1234L, Bytes.toLong(null, 1234L));
        // int
        assertEquals(1, Bytes.toInt(Bytes.toBytes(1)));
        assertEquals(1000, Bytes.toInt(Bytes.toBytes(1000)));
        assertEquals(1000000000, Bytes.toInt(Bytes.toBytes(1000000000)));
        assertEquals(1234, Bytes.toInt(null, 1234));
        // short
        assertEquals((short) 1, Bytes.toShort(Bytes.toBytes((short) 1)));
        assertEquals((short) 1000, Bytes.toShort(Bytes.toBytes((short) 1000)));
        assertEquals((short) 1000000000, Bytes.toShort(Bytes.toBytes((short) 1000000000)));
    }

    public void main(String[] args) throws Exception {
        log.info("decode 'abc+123' = {}", Bytes.urldecode("abc+123"));

        for (String tok : Strings.split("abc def ghi jkl", " ")) {
            log.info("t1: {}", tok);
        }
        for (String tok : Strings.split("abc:def/ghi:jkl", ":/", true)) {
            log.info("t2: {}", tok);
        }

        byte[] s1 = Bytes.toBytes("1");
        byte[] s2 = Bytes.toBytes("2");
        byte[] s3 = Bytes.toBytes("3");
        byte[] s4 = Bytes.toBytes("4");

        log.info(Strings.cat("1", "2", "3"));
        log.info(Strings.cat("1", "2", "3", "4"));
        log.info(Strings.cat("1", "2", "3", "4", "5"));
        log.info(Strings.cat("1", "2", "3", "4", "5", "6"));

        log.info(Bytes.toString(Bytes.cat(s1, s2)));
        log.info(Bytes.toString(Bytes.cat(s1, s2, s3)));
        log.info(Bytes.toString(Bytes.cat(s1, s2, s3, s4)));

        test1(100);
        test1(200);
        test1(800);
        test1(17000);

        byte[] b = Bytes.toBytes("Dear {{NAME}},\n" +
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
        Bytes.writeBytes(b, bos);
        log.info("{} encoded to {}", b.length, bos.size());
        ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
        byte[] o = Bytes.readBytes(bin);
        log.info("decoded to {}", o.length);
    }
}
