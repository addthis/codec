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
package com.addthis.codec.letters;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class A extends X {

    public int int_a;
    public B obj_B_b;
    public AtomicLong atomicLong_c;
    public AtomicInteger atomicInteger_d;
    public AtomicBoolean atomicBoolean_e;

    public A set() {
        int_a = 0xa;
        obj_B_b = new B().set();
        atomicLong_c = new AtomicLong(3);
        atomicInteger_d = new AtomicInteger(4);
        atomicBoolean_e = new AtomicBoolean(true);
        return this;
    }

    public boolean check() {
        return
                check(int_a, 0xa, "A.int_a") &&
                obj_B_b.check() &&
                check(atomicLong_c, new AtomicLong(3), "C.atomicLong_c") &&
                check(atomicInteger_d, new AtomicInteger(4), "D.atomicInteger_d") &&
                check(atomicBoolean_e, new AtomicBoolean(true), "E.atomicBoolean_e");
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof A)) {
            return false;
        }
        A otherA = (A) other;
        return int_a == otherA.int_a &&
               obj_B_b.equals(otherA.obj_B_b) &&
               atomicLong_c.get() == otherA.atomicLong_c.get() &&
               atomicInteger_d.get() == otherA.atomicInteger_d.get() &&
               atomicBoolean_e.get() == otherA.atomicBoolean_e.get();
    }
}
