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

import com.addthis.codec.CodecBasicTests;
import com.addthis.codec.annotations.Pluggable;

@Pluggable("empty")
public class B extends X {

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
