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
import java.util.HashMap;
import java.util.LinkedList;

import com.addthis.codec.annotations.FieldConfig;

public class CC extends C {

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
    public A obj_A_h;
    public String[]                arr_string_f;
    public B[]                     arr_obj_B_i;
    public LinkedList<String> list_str_j;
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
    public TheEnum theEnum;
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
