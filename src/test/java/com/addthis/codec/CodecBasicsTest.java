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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.addthis.codec.annotations.FieldConfig;
import com.addthis.codec.annotations.Pluggable;
import com.addthis.codec.binary.CodecBin2;
import com.addthis.codec.codables.Codable;
import com.addthis.codec.json.CodecJSON;
import com.addthis.codec.kv.CodecKV;
import com.addthis.codec.reflection.RequiredFieldException;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CodecBasicsTest {

    @Test
    public void utestJSON() throws Exception {
        assertTrue(testJSON());
    }

    @Test
    public void utestBin2() throws Exception {
        assertTrue(testBin2());
    }

    @Test
    public void utestKV() throws Exception {
        assertTrue(testKV());
    }

    public abstract static class X implements Codable {

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
            return (!(a == null || !(a.get() == b.get()))) || fail(msg, a, b);
        }

        public boolean check(AtomicInteger a, AtomicInteger b, String msg) {
            return (!(a == null || !(a.get() == b.get()))) || fail(msg, a, b);
        }

        public boolean check(AtomicBoolean a, AtomicBoolean b, String msg) {
            return (!(a == null || !(a.get() == b.get()))) || fail(msg, a, b);
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

    public static class A extends X {

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

    @Pluggable("empty")
    public static class B extends X {

        public int int_a;
        public String str_b;

        public B set() {
            int_a = 0xa;
            str_b = "bbb";
            return this;
        }

        public boolean check() {
            return
                    check(int_a, 0xa, "B.int_a") &&
                    check(str_b, "bbb", "B.str_b");
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof B)) {
                return false;
            }

            B otherB = (B) other;

            return int_a == otherB.int_a &&
                   str_b.equals(otherB.str_b);
        }
    }

    @Pluggable("ccm")
    public static class C extends X {

        public int x_int_a = 3;

        @Override
        public boolean check() {
            return check(x_int_a, 1, "CP.x_int_a");
        }
    }

    public static enum TheEnum {
        FOO, BAR
    }

    public static class CC extends C {

        @FieldConfig(codable = true)
        private   int x_int_a;
        @FieldConfig(codable = true)
        protected int x_int_b;
        @FieldConfig(codable = true)
        int x_int_c;
        public byte[]                  byte_d;
        public int                     int_e;
        public int[]                   arr_int_f;
        public String                  str_g;
        public A                       obj_A_h;
        public String[]                arr_string_f;
        public B[]                     arr_obj_B_i;
        public LinkedList<String>      list_str_j;
        public LinkedList<B>           list_obj_k;
        public HashMap<String, String> map_str_str_l;
        public HashMap<String, B>      map_str_B_m;
        public LinkedList<byte[]>      list_bytearray_n;
        public boolean                 boolean_o;
        public short                   short_p;
        public float                   float_q;
        public double                  double_r;
        public Integer                 int_obj_e;
        public Boolean                 boolean_obj_o;
        public Short                   short_obj_p;
        public Float                   float_obj_q;
        public Double                  double_obj_r;
        public String                  empty_string;
        public String                  null_string;
        public TheEnum                 theEnum;
        public TheEnum[]               theEnumArray;

        public CC set() {
            x_int_a = 1;
            x_int_b = 2;
            x_int_c = 3;
            byte_d = new byte[]{'a', 'b', 'c'};
            int_e = 0xe;
            int_obj_e = 0xe;
            arr_int_f = new int[]{0xf, 0xf};
            str_g = "ggg";
            obj_A_h = new A().set();
            arr_obj_B_i = new B[]{new B().set(), new B().set()};
            arr_string_f = new String[]{"hello", "world"};
            list_str_j = new LinkedList<String>(Arrays.asList(new String[]{"jjj"}));
            list_obj_k = new LinkedList<B>(Arrays.asList(new B().set()));
            map_str_str_l = new HashMap<String, String>();
            map_str_str_l.put("lll", "111");
            map_str_B_m = new HashMap<String, B>();
            map_str_B_m.put("mmm", new B().set());
            boolean_o = true;
            short_p = 16;
            float_q = 17.171717f;
            double_r = 18.181818;
            boolean_obj_o = true;
            short_obj_p = 16;
            float_obj_q = 17.171717f;
            double_obj_r = 18.181818;
            empty_string = "";
            theEnum = TheEnum.FOO;
            theEnumArray = new TheEnum[]{TheEnum.FOO, TheEnum.BAR};

            return this;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof CC)) {
                return false;
            }
            CC otherCC = (CC) other;
            return x_int_a == otherCC.x_int_a &&
                   x_int_b == otherCC.x_int_b &&
                   x_int_c == otherCC.x_int_c &&
                   Arrays.equals(byte_d, otherCC.byte_d) &&
                   int_e == otherCC.int_e &&
                   int_obj_e.equals(otherCC.int_obj_e) &&
                   Arrays.equals(arr_int_f, otherCC.arr_int_f) &&
                   str_g.equals(otherCC.str_g) &&
                   obj_A_h.equals(otherCC.obj_A_h) &&
                   Arrays.equals(arr_obj_B_i, otherCC.arr_obj_B_i) &&
                   Arrays.equals(arr_string_f, otherCC.arr_string_f) &&
                   list_str_j.equals(otherCC.list_str_j) &&
                   list_obj_k.equals(otherCC.list_obj_k) &&
                   map_str_str_l.equals(otherCC.map_str_str_l) &&
                   map_str_B_m.equals(otherCC.map_str_B_m) &&
                   boolean_o == otherCC.boolean_o &&
                   short_p == otherCC.short_p &&
                   float_q == otherCC.float_q &&
                   double_r == otherCC.double_r &&
                   boolean_obj_o.equals(otherCC.boolean_obj_o) &&
                   short_obj_p.equals(otherCC.short_obj_p) &&
                   float_obj_q.equals(otherCC.float_obj_q) &&
                   double_obj_r.equals(otherCC.double_obj_r) &&
                   empty_string.equals(otherCC.empty_string) &&
                   theEnum == otherCC.theEnum &&
                   Arrays.equals(theEnumArray, otherCC.theEnumArray) &&
                   null_string == null && null_string == otherCC.null_string;
        }

        public boolean check() {
            boolean success =
                    check(x_int_a, 1, "C.x_int_a") &&
                    check(x_int_b, 2, "C.x_int_b") &&
                    check(x_int_c, 3, "C.x_int_c") &&
                    check(byte_d, new byte[]{'a', 'b', 'c'}, "C.byte_d") &&
                    check(int_e, 0xe, "C.int_e") &&
                    check(int_obj_e, new Integer(0xe), "C.int_obj_e") &&
                    check(arr_int_f, new int[]{0xf, 0xf}, "C.arr_int_f") &&
                    check(str_g, "ggg", "C.str_g") &&
                    obj_A_h.check() &&
                    check(arr_obj_B_i, new B[]{new B().set(), new B().set()}, "C.arr_obj_i") &&
                    check(arr_string_f, new String[]{"hello", "world"}, "C.arr_string_f") &&
                    check(list_str_j.get(0), "jjj", "C.list_str_j") &&
                    check(list_obj_k.get(0), new B().set(), "C.list_obj_k") &&
                    check(map_str_str_l.get("lll"), "111", "C.map_str_str_l") &&
                    check(map_str_B_m.get("mmm"), new B().set(), "C.map_str_B_m") &&
                    check(boolean_o, true, "C.boolean_o") &&
                    check(short_p, (short) 16, "C.short_p") &&
                    check(float_q, 17.171717f, "C.float_q") &&
                    check(double_r, 18.181818, "C.double_r") &&
                    check(boolean_obj_o, Boolean.TRUE, "C.boolean_obj_o") &&
                    check(short_obj_p, new Short((short) 16), "C.short_obj_p") &&
                    check(float_obj_q, new Float(17.171717f), "C.float_obj_q") &&
                    check(double_obj_r, new Double(18.181818), "C.double_obj_r") &&
                    check(empty_string, "", "C.empty_string") &&
                    check(null_string, null, "C.null_string") &&
                    check(theEnum, TheEnum.FOO, "C.theEnum") &&
                    check(theEnumArray, new TheEnum[]{TheEnum.FOO, TheEnum.BAR}, "C.theEnumArray");

            return success;
        }

        public boolean check2() {
            boolean success =
                    check(x_int_a, 1, "C.x_int_a") &&
                    check(x_int_b, 2, "C.x_int_b") &&
                    check(x_int_c, 3, "C.x_int_c") &&
                    check(byte_d, new byte[]{'a', 'b', 'c'}, "C.byte_d") &&
                    check(int_e, 0xe, "C.int_e") &&
                    check(int_obj_e, new Integer(0xe), "C.int_obj_e") &&
                    check(arr_int_f, new int[]{0xf, 0xf}, "C.arr_int_f") &&
                    check(str_g, "ggg", "C.str_g") &&
                    obj_A_h.check() &&
                    check(arr_obj_B_i, new B[]{new B().set(), new B().set()}, "C.arr_obj_i") &&
                    check(arr_string_f, new String[]{"hello", "world"}, "C.arr_string_f") &&
                    check(list_str_j.get(0), "jjj", "C.list_str_j") &&
                    check(list_obj_k.get(0), new B().set(), "C.list_obj_k") &&
                    check(map_str_str_l.get("lll"), "111", "C.map_str_str_l") &&
                    check(map_str_B_m.get("mmm"), new B().set(), "C.map_str_B_m") &&
                    check(boolean_o, true, "C.boolean_o") &&
                    check(short_p, (short) 16, "C.short_p") &&
                    check(float_q, 17.171717f, "C.float_q") &&
                    check(double_r, 18.181818, "C.double_r") &&
                    check(boolean_obj_o, Boolean.TRUE, "C.boolean_obj_o") &&
                    check(short_obj_p, new Short((short) 16), "C.short_obj_p") &&
                    check(float_obj_q, new Float(17.171717f), "C.float_obj_q") &&
                    check(double_obj_r, new Double(18.181818), "C.double_obj_r") &&
                    check(empty_string, "", "C.empty_string") &&
                    check(null_string, null, "C.null_string");

            return success;
        }
    }


    public static class CCC extends CC {

        public int random = 12345;
    }

    public static class D extends CC {

        public String str_n;

        public D set() {
            str_n = "nnn";
            return this;
        }
    }

    public static class E extends B {

        public String str_o;

        public E set() {
            str_o = "this is e";
            return this;
        }
    }

    public static class G extends C {

        public String str_p;

        public G set() {
            str_p = "this is g";
            return this;
        }
    }

    public static class F extends X {

        public B b_field;
        public C c_field;

        public F set() {
            b_field = new E();
            c_field = new G();
            return this;
        }

        @Override
        public boolean check() {
            return
                    b_field != null && b_field instanceof E &&
                    c_field != null && c_field instanceof G;
        }
    }

    public static class RC {

        @FieldConfig(required = true)
        public String required;
        public String crap = "crap";
    }

    public static boolean test(Codec codec) throws Exception {
        CC c = new CC().set();
        String cn = codec.getClass().getName();
        // encode
        byte[] bec = codec.encode(c);
        String s1 = ("C->" + cn + " = " + bytesToString(bec, false));
        String s2 = ("C->" + cn + " = " + bytesToString(bec, true));
        // decode
        CC bdc = (CC) codec.decode(new CC(), bec);
        boolean encodeDecode = c.equals(bdc);
        byte[] bde = codec.encode(bdc);
        String s3 = (cn + "->C = " + bytesToString(bde, false));
        String s4 = (cn + "->C = " + bytesToString(bde, true));
        // validation
        boolean codes = Arrays.equals(bec, bde) && (bdc.check());
        boolean upgrades = true, downgrades = true;
        String s5 = "", s6 = "";
        // CodecBin2 does not support upgrades or downgrades
        if (codec != CodecBin2.INSTANCE) {
            // upgrade on decode (C -> CC)
            CCC bdcu = (CCC) codec.decode(new CCC(), bec);
            byte[] bdcue = codec.encode(bdcu);
            s5 = ("C->CC = " + bytesToString(bdcue, false));
            s6 = ("C->CC = " + bytesToString(bdcue, true));
            upgrades = bdcu.check();
            // downgrade on decode (C -> X)
            C bdcd = (C) codec.decode(new C(), bec);
            downgrades = bdcd.check();
        }
        // check subclassing
        F f = new F().set();
        bec = codec.encode(f);
        f = (F) codec.decode(F.class, bec);
        boolean subclasses = f.check();
        // check required fields
        boolean requireds = false;
        try {
            RC rc = new RC();
            codec.decode(rc, codec.encode(rc));
        } catch (Exception ex) {
            if (ex instanceof RequiredFieldException ||
                ex.getCause() instanceof RequiredFieldException) {
                requireds = true;
            }
        }
        boolean ok = encodeDecode && codes && upgrades && downgrades && requireds &&
                     subclasses;
        if (!ok) {
            System.out.println("encodeDecode=" + encodeDecode +
                               " codes=" + codes +
                               " upgrades=" + upgrades + " downgrades=" + downgrades +
                               " requireds=" + requireds + " subclasses=" + subclasses
            );
            System.out.println(
                    s1 + "\n" + s2 + "\n" + s3 + "\n" + s4 + "\n" + s5 + "\n" + s6);
        }
        System.out.println(cn + " Codec Test : " + (ok ? "PASSED" : "FAILED") + " " + bec.length);
        return ok;
    }

    private static boolean testJSON() throws Exception {
        return test(CodecJSON.INSTANCE);
    }

    private static boolean testKV() throws Exception {
        return test(CodecKV.INSTANCE);
    }

    private static boolean testBin2() throws Exception {
        return test(CodecBin2.INSTANCE);
    }

    static String bytesToString(byte[] data, boolean printable) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            String hex = Integer.toHexString((int) b);
            if (printable) {
                if (b > 32 && b < 127) {
                    sb.append((char) b);
                } else {
                    sb.append(' ');
                }
                if (hex.length() > 1) {
                    sb.append(' ');
                }
            } else {
                sb.append(hex);
            }
            sb.append(' ');
        }
        return sb.toString();
    }
}
