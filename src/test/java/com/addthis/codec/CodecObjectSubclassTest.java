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

import java.util.Arrays;
import java.util.LinkedList;

import com.addthis.basis.util.Bytes;

import com.addthis.codec.Codec.Set;
import com.addthis.codec.Codec.TYPE;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CodecObjectSubclassTest {

    public static void main(String args[]) throws Exception {
        new CodecObjectSubclassTest().testAll();
    }

    @Test
    public void testAll() throws Exception {
        for (TYPE type : Codec.TYPE.values()) {
            Codec c = type.getInstance();
            Bundle testBundle = new Bundle();
            testBundle.a.set();
            testBundle.b.set();
            testBundle.c.set();
            testBundle.d.set();
            byte en1[] = c.encode(testBundle);
            Bundle b = c.decode(Bundle.class, en1);
            byte en2[] = c.encode(b);
            boolean passfail = Arrays.equals(en1, en2);
            System.out.println("en1[" + en1.length + "] -> " + Bytes.toString(en1));
            System.out.println("en2[" + en2.length + "] -> " + Bytes.toString(en2));
            System.out.println(c.getClass().getSimpleName() + " --> " + (passfail ? "PASSED" : "FAILED"));
            assertTrue(passfail);
        }
    }

    @Set(classMap = BundleMap.class)
    public static class A implements Codec.Codable {

        public String field1 = "unset1";

        public A set() {
            field1 = "ok1";
            return this;
        }
    }

    public static class B extends A {

        public String field2 = "unset2";

        @Override
        public B set() {
            super.set();
            field2 = "ok2";
            return this;
        }
    }

    public static class C extends B {

        public String field3 = "unset3";

        @Override
        public C set() {
            super.set();
            field3 = "ok3";
            return this;
        }
    }

    public static class D extends C {

        public String field4 = "unset4";
        public LinkedList<A> list4;

        @Override
        public D set() {
            super.set();
            field4 = "ok4";
            list4 = new LinkedList<A>();
            list4.add(new A().set());
            list4.add(new B().set());
            list4.add(new C().set());
            return this;
        }
    }

    public static class BundleMap extends Codec.ClassMap {

        public BundleMap() {
            add(A.class).add(B.class).add(C.class).add(D.class);
        }

        @Override
        public String getClassField() {
            return "type";
        }
    }

    public static class Bundle {

        public A a = new A();
        public A b = new B();
        public A c = new C();
        public A d = new D();
    }
}
