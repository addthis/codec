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

import java.lang.reflect.Field;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import com.addthis.codec.binary.CodecBin2;
import com.addthis.codec.reflection.CodableFieldInfo;

import com.google.common.reflect.TypeToken;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CodecGenericsTest {

    private static final Logger log = LoggerFactory.getLogger(CodecGenericsTest.class);

    public static void main(String args[]) throws Exception {
        new CodecGenericsTest().testAll();
    }

    @Test public void current() throws NoSuchFieldException {
        Field heldHolder = HolderHolder.class.getDeclaredField("heldHolder");
        Field heldHolderExtender = HolderHolder.class.getDeclaredField("heldHolderExtender");
        CodableFieldInfo heldInfo = new CodableFieldInfo(heldHolder);
        log.info("held info {}", heldInfo);
        CodableFieldInfo extenderInfo = new CodableFieldInfo(heldHolderExtender);
        log.info("extender info {}", extenderInfo);
    }

    @Test public void tokens() throws NoSuchFieldException {
        Field heldHolder = HolderHolder.class.getDeclaredField("heldHolder");

        log.info("generic {}", TypeToken.of(heldHolder.getGenericType()));
        log.info("raw {}", TypeToken.of(heldHolder.getType()));

        log.info("easier generic {}", new TypeToken<Holder<String>>() {});

        log.info("resolve type {}", new TypeToken<Holder<String>>() {}.resolveType(Holder.class.getTypeParameters()[0]));

        log.info("unresolved type {}", Arrays.toString(HolderExtender.class.getTypeParameters()));

        log.info("parent type {}", TypeToken.of(HolderExtender.class).resolveType(Holder.class));
    }

    public static class Holder<T> {
        T held;
    }

    public static class HolderExtender extends AbstractMap<String, Integer> {
        @Override public Set<Entry<String, Integer>> entrySet() {
            return null;
        }
    }

    public static class HolderHolder {
        Holder<String> heldHolder;
        HolderExtender heldHolderExtender;
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
