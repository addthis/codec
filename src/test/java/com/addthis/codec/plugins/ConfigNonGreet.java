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

import javax.annotation.Nullable;

import com.addthis.codec.annotations.FieldConfig;
import com.addthis.codec.config.ConfigCodable;
import com.addthis.codec.config.ValueCodable;

import com.addthis.codec.embedded.com.typesafe.config.ConfigObject;
import com.addthis.codec.embedded.com.typesafe.config.ConfigRenderOptions;
import com.addthis.codec.embedded.com.typesafe.config.ConfigValue;
import com.addthis.codec.embedded.com.typesafe.config.ConfigValueType;

public class ConfigNonGreet implements ConfigCodable, ValueCodable {

    @FieldConfig(required = true) public ConfigValue rawConfigValue;
    @FieldConfig(required = true) public String source = "Constructor";

    public String greet() {
        return rawConfigValue.render(ConfigRenderOptions.concise());
    }

    @Nullable @Override public ConfigObject fromConfigObject(ConfigObject config, ConfigObject defaults) {
        ConfigValue configValue = config.get("rawConfigValue");
        if ((configValue != null) && (configValue.valueType() != ConfigValueType.NULL)) {
            rawConfigValue = configValue;
            source = (String) config.get("source").unwrapped();
            return null;
        } else {
            return config;
        }
    }

    @Override public void fromConfigValue(ConfigValue configValue, ConfigObject defaults) {
        rawConfigValue = configValue;
        source = "ValueCodable";
    }
}
