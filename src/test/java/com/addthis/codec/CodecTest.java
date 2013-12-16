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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CodecTest extends Codec {

    @Test
    public void testIt() throws Exception {
        for (int x = 0; x < 5; x++) {
            int y = Integer.MAX_VALUE - 2 + x;
            long tou = Bytes.toUInt(Bytes.toBytes(y));
            long toi = Bytes.toInt(Bytes.toBytes(y));
            assertTrue(y < 0 ? (toi < 0 && tou > 0) : (toi > 0 && tou > 0));
        }
        for (long x = 0; x < 5; x++) {
            long y = Integer.MAX_VALUE - 2 + x;
            long tou = Bytes.toUInt(Bytes.toBytes((int) y));
            long toi = Bytes.toInt(Bytes.toBytes((int) y));
            assertTrue(y > Integer.MAX_VALUE ? toi < 0 : toi == tou);
        }
        int cv = 1;
        for (int i = 0; i < 1000; i++) {
            if (Bytes.toInt(Bytes.toBytes(cv)) != cv) {
                System.out.println("codec error at : " + i);
                assertTrue(false);
                return;
            }
            cv = cv * 2 + 1;
        }
        assertTrue(true);
        assertEquals(CodecKV.tickCode("a=b&c=d"), "&a=b&c=d");
    }

    @Test
    public void testStartsWith() {
        assertTrue(Bytes.startsWith(Bytes.toBytes("12345"), Bytes.toBytes("123")));
        assertFalse(Bytes.startsWith(Bytes.toBytes("12345"), Bytes.toBytes("234")));
    }

    @Test
    public void testReplaceTrue() {
        byte buf[] = Bytes.toBytes("abc 123 abc");
        byte pat[] = Bytes.toBytes("123");
        byte rep[] = Bytes.toBytes("xyzfoo");
        assertEquals("abc xyzfoo abc", Bytes.toString(Bytes.replace(buf, pat, rep)));
    }

    @Test
    public void testOverwriteTrue() {
        // test normal overwrite test
        byte buf1[] = Bytes.toBytes("abc 123 abc");
        byte pat1[] = Bytes.toBytes("123");
        byte rep1[] = Bytes.toBytes("xyzfoo");
        assertTrue(Bytes.overwrite(buf1, pat1, rep1));

        assertEquals(Bytes.toString(buf1), "abc xyzfooc");
        // test replace buffer that would exceed base buffer (should be limited)
        byte buf2[] = Bytes.toBytes("abc 123 abc");
        byte pat2[] = Bytes.toBytes("123");
        byte rep2[] = Bytes.toBytes("xyzfoodfight");
        assertTrue(Bytes.overwrite(buf2, pat2, rep2));
        assertEquals("abc xyzfood", Bytes.toString(buf2));
    }

    @Test
    public void testOverwriteFalse() {
        byte buf1[] = Bytes.toBytes("abc 123 abc");
        byte pat1[] = Bytes.toBytes("888");
        byte rep1[] = Bytes.toBytes("xyzfoo");
        assertFalse(Bytes.overwrite(buf1, pat1, rep1));
        assertEquals("abc 123 abc", Bytes.toString(buf1));
    }

    @Test
    public void testCatCut() {
        byte b1[] = Bytes.toBytes("123");
        byte b2[] = Bytes.toBytes("abc");
        // cat
        assertEquals(Bytes.toString(Bytes.cat(b1, b2)), "123abc");
        assertEquals(Bytes.toString(Bytes.cat(b1, b2, b1)), "123abc123");
        assertEquals(Bytes.toString(Bytes.cat(b1, b2, b1, b2)), "123abc123abc");
        assertEquals(Bytes.toString(Bytes.cat(b1, b2, b1, b2, b1)), "123abc123abc123");
        // cut
        assertEquals(Bytes.toString(Bytes.cut(Bytes.cat(b1, b2), 0, 4)), "123a");
        assertEquals(Bytes.toString(Bytes.cut(Bytes.cat(b1, b2), 2, 2)), "3a");
        assertEquals(Bytes.toString(Bytes.cut(Bytes.cat(b1, b2), 2, 4)), "3abc");
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

    public void main(String args[]) throws Exception {
        System.out.println("decode 'abc+123' = " + Bytes.urldecode("abc+123"));

        for (String tok : Strings.split("abc def ghi jkl", " ")) {
            System.out.println("t1: " + tok);
        }
        for (String tok : Strings.split("abc:def/ghi:jkl", ":/", true)) {
            System.out.println("t2: " + tok);
        }

        byte s1[] = Bytes.toBytes("1");
        byte s2[] = Bytes.toBytes("2");
        byte s3[] = Bytes.toBytes("3");
        byte s4[] = Bytes.toBytes("4");

        System.out.println(Strings.cat("1", "2", "3"));
        System.out.println(Strings.cat("1", "2", "3", "4"));
        System.out.println(Strings.cat("1", "2", "3", "4", "5"));
        System.out.println(Strings.cat("1", "2", "3", "4", "5", "6"));

        System.out.println(Bytes.toString(Bytes.cat(s1, s2)));
        System.out.println(Bytes.toString(Bytes.cat(s1, s2, s3)));
        System.out.println(Bytes.toString(Bytes.cat(s1, s2, s3, s4)));

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
        byte b[] = new byte[len];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Bytes.writeBytes(b, bos);
        System.out.println(b.length + " encoded to " + bos.size());
        ByteArrayInputStream bin = new ByteArrayInputStream(bos.toByteArray());
        byte o[] = Bytes.readBytes(bin);
        System.out.println("decoded to " + o.length);
    }

    @Override
    public Object decode(Object shell, byte[] data) throws Exception {
        return null;
    }

    @Override
    public byte[] encode(Object obj) throws Exception {
        return null;
    }

    @Override
    public CodableStatistics statistics(Object obj) {
        return null;
    }

    @Override
    public boolean storesNull(byte[] data) {
        return false;
    }
}
