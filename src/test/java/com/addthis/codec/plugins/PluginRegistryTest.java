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
import com.typesafe.config.ConfigValueFactory;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import static org.hamcrest.CoreMatchers.isA;

public class PluginRegistryTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void loadSimple() {
        Config testPluginConfig = ConfigFactory.load("plugins/greet-good");
        PluginRegistry pluginRegistry = new PluginRegistry(testPluginConfig);
        Map<String, PluginMap> mapping = pluginRegistry.asMap();
        Assert.assertEquals(1, mapping.size());
        Assert.assertEquals(2, mapping.get("greet").asBiMap().size());
    }

    @Test
    public void loadError() {
        thrown.expectCause(isA(ClassNotFoundException.class));
        Config testPluginConfig = ConfigFactory.load("plugins/greet-bad");
        PluginRegistry shouldFail = new PluginRegistry(testPluginConfig);
    }

    @Test
    public void loadWithBaseClass() {
        Config testPluginConfig = ConfigFactory.load("plugins/greet-with-baseclass");
        PluginRegistry pluginRegistry = new PluginRegistry(testPluginConfig);
        Map<String, PluginMap> mapping = pluginRegistry.asMap();
        Assert.assertEquals(1, mapping.size());
        Assert.assertEquals(2, mapping.get("greet").asBiMap().size());
    }

    @Test
    public void loadAndGetWithBaseClass() {
        Config testPluginConfig = ConfigFactory.load("plugins/greet-with-baseclass");
        PluginRegistry pluginRegistry = new PluginRegistry(testPluginConfig);
        Map<String, PluginMap> byCategory = pluginRegistry.asMap();
        Map<Class<?>, PluginMap> byClass = pluginRegistry.byClass();
        Assert.assertEquals(byCategory.size(), byClass.size());
        Assert.assertEquals(byCategory.get("greet"), byClass.get(Greeter.class));
    }

    @Test
    public void loadErrorWithBaseClass() {
        thrown.expect(isA(ClassCastException.class));
        Config testPluginConfig = ConfigFactory.load("plugins/greet-with-baseclass-bad");
        PluginRegistry shouldFail = new PluginRegistry(testPluginConfig);
    }

    @Test
    public void loadDynamicWithBaseClass() throws ClassNotFoundException {
        Config testPluginConfig = ConfigFactory.load("plugins/greet-with-baseclass");
        PluginRegistry pluginRegistry = new PluginRegistry(testPluginConfig);
        Map<String, PluginMap> mapping = pluginRegistry.asMap();
        mapping.get("greet").getClass("EasyGreet");
        mapping.get("greet").getClass("com.addthis.codec.plugins.ListGreet");
        mapping.get("greet").getClass("codec.plugins.EnumGreet");
        mapping.get("greet").getClass("other.SimpleGreet");
        thrown.expect(isA(ClassCastException.class));
        mapping.get("greet").getClass("PluginRegistryTest");
        thrown.expectCause(isA(ClassNotFoundException.class));
        mapping.get("greet").getClass("com.addthis.error.CrowdGreet");
    }

    @Test
    public void loadInterestingBaseClasses() throws ClassNotFoundException {
        Config testPluginConfig = ConfigFactory.load("plugins/greet-with-baseclass");
        testPluginConfig = testPluginConfig.withValue(
                "plugins-test.greet._class",
                ConfigValueFactory.fromAnyRef("com.addthis.codec.plugins.SimpleGreet"));
        thrown.expect(isA(ClassCastException.class));
        PluginRegistry shouldThrowClassCastException = new PluginRegistry(testPluginConfig);
        testPluginConfig = testPluginConfig.withValue(
                "plugins-test.greet._class",
                ConfigValueFactory.fromAnyRef(".addthis.codec.plugins.SimpleGreet"));
        thrown.expectCause(isA(ClassNotFoundException.class));
        PluginRegistry shouldThrowClassNotFoundException = new PluginRegistry(testPluginConfig);
    }
}