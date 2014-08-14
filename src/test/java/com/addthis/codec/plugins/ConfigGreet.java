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

import com.addthis.codec.annotations.FieldConfig;

import com.typesafe.config.ConfigRenderOptions;
import com.typesafe.config.ConfigValue;

public class ConfigGreet implements Greeter {

    @FieldConfig(required = true) public ConfigValue rawConfigValue;

    @FieldConfig(required = true) public String source = "Constructor";

    @Override public String greet() {
        return rawConfigValue.render(ConfigRenderOptions.concise());
    }
}
