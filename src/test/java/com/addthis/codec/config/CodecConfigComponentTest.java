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

import java.util.HashMap;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import com.addthis.codec.annotations.FieldConfig;
import com.addthis.codec.plugins.ConfigGreet;
import com.addthis.codec.plugins.ConfigGreetHolder;
import com.addthis.codec.plugins.ConfigNonGreet;
import com.addthis.codec.reflection.CodableFieldInfo;
import com.addthis.codec.reflection.Fields;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

import org.junit.Assert;
import org.junit.Test;

public class CodecConfigComponentTest {

    @Test
    public void hydrateNumber() throws Exception {
        ConfigValue longValue = ConfigValueFactory.fromAnyRef(12L);
        Config valueHolder = longValue.atKey("value");
        short shortValue = (short) CodecConfig.getDefault()
                                              .hydrateNumberComponent(Short.class, "value", valueHolder);
        Assert.assertEquals((short) 12, shortValue);
    }

    @Test
    public void hydrateNumberPrimitive() throws Exception {
        ConfigValue longValue = ConfigValueFactory.fromAnyRef(12L);
        Config valueHolder = longValue.atKey("value");
        short shortValue = (short) CodecConfig.getDefault()
                                              .hydrateNumberComponent(short.class, "value", valueHolder);
        Assert.assertEquals((short) 12, shortValue);
    }

    @Test
    public void hydratePrimitiveNumberArray() throws Exception {
        short[] expected = {1, 2, 3};
        Config arrayHolder = ConfigFactory.parseString("array: [1, 2, 3]");
        short[] shortPrimitiveArray =
                (short[]) CodecConfig.getDefault()
                                     .hydrateNumberArray(short.class, "array", arrayHolder);
        Assert.assertArrayEquals(shortPrimitiveArray, expected);
    }

    @Test
    public void hydrateNumberArrayFromWrapper() throws Exception {
        short[] expected = {1, 2, 3};
        Config arrayHolder = ConfigFactory.parseString("array: [1, 2, 3]");
        short[] shortPrimitiveArray =
                (short[]) CodecConfig.getDefault().hydrateArray(short.class, "array", arrayHolder);
        Assert.assertArrayEquals(shortPrimitiveArray, expected);
    }

    @Test
    public void hydrateBoolean() throws Exception {
        ConfigValue longValue = ConfigValueFactory.fromAnyRef(true);
        Config valueHolder = longValue.atKey("value");
        Boolean booleanValue = (Boolean) CodecConfig.getDefault().hydrateFieldComponent(Boolean.class, "value",
                                                                                        valueHolder);
        Assert.assertEquals(true, booleanValue);
    }

    static class MapHolder {
        public HashMap<String, Integer> map;
    }

    @Test
    public void hydrateHashMap() throws Exception {
        CodableFieldInfo mapField =
                Fields.getClassFieldMap(MapHolder.class).values().iterator().next();
        Config mapHolder = ConfigFactory.parseString("map { a: 1, b: 2, c: 14 }");
        Map<String, Integer> actual = CodecConfig.getDefault().hydrateMap(mapField, mapHolder);
        Map<String, Integer> expected = new HashMap<>();
        expected.put("a", 1);
        expected.put("b", 2);
        expected.put("c", 14);
        Assert.assertEquals(expected, actual);
    }

    static class MapInterfaceHolder {
        @FieldConfig public Map<String, Integer> map = new TreeMap<>();
    }

    @Test
    public void hydrateMap() throws Exception {
        CodableFieldInfo mapField =
                Fields.getClassFieldMap(MapInterfaceHolder.class).values().iterator().next();
        Config mapHolder = ConfigFactory.parseString("map { a: 1, b: 2, c: 14 }");
        Map<String, Integer> actual = CodecConfig.getDefault().hydrateMap(mapField, mapHolder, new MapInterfaceHolder());
        Map<String, Integer> expected = new HashMap<>();
        expected.put("a", 1);
        expected.put("b", 2);
        expected.put("c", 14);
        Assert.assertEquals(expected, actual);
    }

    static class CollectionInterfaceHolder {
        @FieldConfig public SortedSet<String> set = new TreeSet<>();
    }

    @Test
    public void hydrateCollection() throws Exception {
        CodableFieldInfo setField =
                Fields.getClassFieldMap(CollectionInterfaceHolder.class).values().iterator().next();
        Config setHolder = ConfigFactory.parseString("set: [ a, b, c]");
        SortedSet<String> actual = (SortedSet<String>) CodecConfig.getDefault().hydrateCollection(
                setField, setHolder, new CollectionInterfaceHolder());
        SortedSet<String> expected = new TreeSet<>();
        expected.add("a");
        expected.add("b");
        expected.add("c");
        Assert.assertEquals(expected, actual);
    }

    @Test
    public void valueCodableDirect() throws Exception {
        Config greet = ConfigFactory.parseString("raw: [\"someraw\"]");
        ConfigGreet greeterObject =
                (ConfigGreet) CodecConfig.getDefault().hydrateFieldComponent(ConfigGreet.class, "raw", greet);
        Assert.assertEquals("ValueCodable", greeterObject.source);
        Assert.assertEquals("[\"someraw\"]", greeterObject.greet());
    }

    @Test
    public void valueCodableNestedNoPluggables() throws Exception {
        Config greet = ConfigFactory.parseString("configNonGreet: [\"someraw\"]");
        ConfigGreetHolder greeterHolderObject = CodecConfig.getDefault().decodeObject(ConfigGreetHolder.class, greet);
        ConfigNonGreet greeterObject = greeterHolderObject.configNonGreet;
        Assert.assertEquals("ValueCodable", greeterObject.source);
        Assert.assertEquals("[\"someraw\"]", greeterObject.greet());
    }

    @Test
    public void valueCodableNested() throws Exception {
        ConfigGreetHolder greeterHolderObject = CodecConfig.getDefault().decodeObject(ConfigGreetHolder.class,
                                                                                      "configGreet: [\"someraw\"]");
        ConfigGreet greeterObject = greeterHolderObject.configGreet;
        Assert.assertEquals("ValueCodable", greeterObject.source);
        Assert.assertEquals("[\"someraw\"]", greeterObject.greet());
    }

    @Test
    public void configCodable() throws Exception {
        ConfigGreet greeterObject = CodecConfig.getDefault().decodeObject(
                "greet.config {rawConfigValue: [\"someraw\"], source: ConfigCodable}");
        Assert.assertEquals("ConfigCodable", greeterObject.source);
        Assert.assertEquals("[\"someraw\"]", greeterObject.greet());
    }

    @Test
    public void configCodableAliasDefaults() throws Exception {
        Config greet = ConfigFactory.parseString("greet.configDefaulted {}");
        ConfigGreet greeterObject = CodecConfig.getDefault().decodeObject(greet);
        Assert.assertEquals("alias defaults", greeterObject.source);
        Assert.assertEquals("[0,1,2]", greeterObject.greet());
    }

    @Test
    public void configCodableGlobalDefaults() throws Exception {
        Config greet = ConfigFactory.parseString("greet.config {}");
        ConfigGreet greeterObject = CodecConfig.getDefault().decodeObject(greet);
        Assert.assertEquals("global defaults", greeterObject.source);
        Assert.assertEquals("[3,4,5]", greeterObject.greet());
    }
}