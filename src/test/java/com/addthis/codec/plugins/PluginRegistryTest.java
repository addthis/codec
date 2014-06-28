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

import java.util.Map;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class PluginRegistryTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void loadSimple() {
        Config testPluginConfig = ConfigFactory.load("greet-good");
        PluginRegistry pluginRegistry = new PluginRegistry(testPluginConfig);
        Map<String, PluginMap> mapping = pluginRegistry.asMap();
        Assert.assertEquals(1, mapping.size());
        Assert.assertEquals(2, mapping.get("greet").asBiMap().size());
    }

    @Test
    public void loadError() {
        thrown.expect(RuntimeException.class);
        Config testPluginConfig = ConfigFactory.load("greet-bad");
        PluginRegistry pluginRegistry = new PluginRegistry(testPluginConfig);
    }

}