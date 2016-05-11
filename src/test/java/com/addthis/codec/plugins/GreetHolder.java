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
package com.addthis.codec.plugins;

import com.fasterxml.jackson.annotation.JsonCreator;

public class GreetHolder implements Greeter {

    private final Greeter greeter;
    private final int a;

    @JsonCreator
    public GreetHolder(Greeter greeter, int a) {
        this.greeter = greeter;
        this.a = 5;
    }

    @Override public String greet() {
        if (greeter == null) {
            return "null";
        } else {
            return greeter.greet();
        }
    }
}
