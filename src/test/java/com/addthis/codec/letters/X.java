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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.addthis.codec.codables.Codable;

public abstract class X implements Codable {

    public boolean check(byte[] a, byte[] b, String msg) {
        return !Arrays.equals(a, b) ? fail(msg, a, b) : true;
    }

    public boolean check(Integer[] a, Integer[] b, String msg) {
        return !Arrays.equals(a, b) ? fail(msg, a, b) : true;
    }

    public boolean check(int[] a, int[] b, String msg) {
        return !Arrays.equals(a, b) ? fail(msg, a, b) : true;
    }

    public boolean check(Object[] a, Object[] b, String msg) {
        return !Arrays.equals(a, b) ? fail(msg, a, b) : true;
    }

    public boolean check(AtomicLong a, AtomicLong b, String msg) {
        return !(a == null || !(a.get() == b.get())) || fail(msg, a, b);
    }

    public boolean check(AtomicInteger a, AtomicInteger b, String msg) {
        return !(a == null || !(a.get() == b.get())) || fail(msg, a, b);
    }

    public boolean check(AtomicBoolean a, AtomicBoolean b, String msg) {
        return !(a == null || !(a.get() == b.get())) || fail(msg, a, b);
    }

    public boolean check(boolean a, boolean b, String msg) {
        return (a != b) ? fail(msg, a, b) : true;
    }

    public boolean check(short a, short b, String msg) {
        return (a != b) ? fail(msg, a, b) : true;
    }

    public boolean check(int a, int b, String msg) {
        return (a != b) ? fail(msg, a, b) : true;
    }

    public boolean check(float a, float b, String msg) {
        return (a != b) ? fail(msg, a, b) : true;
    }

    public boolean check(double a, double b, String msg) {
        return (a != b) ? fail(msg, a, b) : true;
    }

    public boolean check(Object a, Object b, String msg) {
        return (a == null && b != null) || (a != null && !a.equals(b)) ? fail(msg, a, b) : true;
    }

    public boolean fail(String msg, Object a, Object b) {
        System.out.println("fail " + msg + " " + a + " != " + b);
        return false;
    }

    protected boolean original;

    public abstract boolean check();

    @Override
    public boolean equals(Object o) {
        if (o instanceof X && o.getClass() == getClass()) {
            if (((X) o).original) {
                return check();
            } else {
                return ((X) o).check();
            }
        }
        return false;
    }
}
