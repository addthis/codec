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

import com.addthis.codec.annotations.Pluggable;

@Pluggable("ccm")
public class C extends X {

    public int x_int_a = 3;

    @Override
    public boolean check() {
        return check(x_int_a, 1, "CP.x_int_a");
    }
}
