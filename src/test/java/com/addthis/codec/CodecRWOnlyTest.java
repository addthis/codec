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

import com.addthis.codec.annotations.FieldConfig;
import com.addthis.codec.json.CodecJSON;

import org.junit.Test;

public class CodecRWOnlyTest {

    public static class A {

        @FieldConfig(readonly = true)
        public long read1;
        @FieldConfig(readonly = true)
        public long read2;
        @FieldConfig(writeonly = true)
        public long write1;
        @FieldConfig(writeonly = true)
        public long write2;

        public String toString() {
            return "r1:" + read1 + ",r2:" + read2 + ",w1:" + write1 + ",w2:" + write2;
        }
    }

    @Test
    public void readWrite() throws Exception {
        A a = new A();
        a.read1 = 123;
        a.read2 = 234;
        a.write1 = 456;
        a.write2 = 567;
        System.out.println(CodecJSON.encodeString(a));
        String dec = "{read1:111,read2:222,write1:333,write2:444}";
        a = CodecJSON.decodeString(new A(), dec);
        System.out.println(a);
    }
}
