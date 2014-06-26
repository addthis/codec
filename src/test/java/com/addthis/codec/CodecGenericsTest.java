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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import com.addthis.codec.binary.CodecBin2;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CodecGenericsTest {

    public static void main(String args[]) throws Exception {
        new CodecGenericsTest().testAll();
    }

    @Test
    public void testAll() throws Exception {
        test(CodecBin2.INSTANCE);
    }

    public static void test(Codec codec) throws Exception {
        D input = new D();
        input.h = new HashMap<String[], D>();
        String[] stringArray = new String[2];
        stringArray[0] = "hello";
        stringArray[1] = "world";
        input.h.put(stringArray, new D());
        // FIXME: this will cause the library to throw an exception
        // input.h.put(stringArray, null);
        byte[] byteArray = codec.encode(input);
        D result = codec.decode(new D(), byteArray);
        assertFalse(result == null);
        assertFalse(result.h == null);
        Iterator<String[]> iter = result.h.keySet().iterator();
        assertTrue(iter.hasNext());
        String[] resultArray = iter.next();
        assertTrue(resultArray.length == 2);
        assertTrue(resultArray[0].equals("hello"));
        assertTrue(resultArray[1].equals("world"));
        assertFalse(iter.hasNext());
    }

    public static class A<K> extends HashMap<K, Long> {

    }

    public static class B extends A<String> {

    }

    public static class C extends B {

    }

    public static class D {

        public A<String> a;
        public B b;
        public C c;
        public HashMap<String[], D> h;
        public LinkedList<byte[]> i;
        public X<Long> x;
        public Y y;
        public Z z;
    }

    public static class E extends D {

    }

    public static class X<V> extends HashMap<String, V> {

    }

    public static class Y extends X<Long> {

    }

    public static class Z extends Y {

    }
}
