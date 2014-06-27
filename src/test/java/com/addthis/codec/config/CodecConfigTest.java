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
package com.addthis.codec.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

import org.junit.Assert;
import org.junit.Test;

public class CodecConfigTest {

    @Test
    public void hydrateNumber() throws Exception {
        ConfigValue longValue = ConfigValueFactory.fromAnyRef(12L);
        Config valueHolder = longValue.atKey("value");
        short shortValue = CodecConfig.hydrateNumber(Short.class, "value", valueHolder);
        Assert.assertEquals((short) 12, shortValue);
    }
}