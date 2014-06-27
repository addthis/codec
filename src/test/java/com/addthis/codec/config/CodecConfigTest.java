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

import com.addthis.codec.reflection.CodableFieldInfo;
import com.addthis.codec.reflection.Fields;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;

import org.junit.Assert;
import org.junit.Test;

public class CodecConfigTest {

    @Test
    public void hydrateNumber() throws Exception {
        ConfigValue longValue = ConfigValueFactory.fromAnyRef(12L);
        Config valueHolder = longValue.atKey("value");
        short shortValue = (short) CodecConfig.hydrateNumber(Short.class, "value", valueHolder);
        Assert.assertEquals((short) 12, shortValue);
    }

    @Test
    public void hydrateNumberPrimitive() throws Exception {
        ConfigValue longValue = ConfigValueFactory.fromAnyRef(12L);
        Config valueHolder = longValue.atKey("value");
        short shortValue = (short) CodecConfig.hydrateNumber(short.class, "value", valueHolder);
        Assert.assertEquals((short) 12, shortValue);
    }

    @Test
    public void hydratePrimitiveNumberArray() throws Exception {
        short[] expected = {1, 2, 3};
        Config arrayHolder = ConfigFactory.parseString("array: [1, 2, 3]");
        short[] shortPrimitiveArray =
                (short[]) CodecConfig.hydrateNumberArray(short.class, "array", arrayHolder);
        Assert.assertArrayEquals(shortPrimitiveArray, expected);
    }

    @Test
    public void hydrateNumberArrayFromWrapper() throws Exception {
        short[] expected = {1, 2, 3};
        Config arrayHolder = ConfigFactory.parseString("array: [1, 2, 3]");
        short[] shortPrimitiveArray =
                (short[]) CodecConfig.hydrateArray(short.class, "array", arrayHolder);
        Assert.assertArrayEquals(shortPrimitiveArray, expected);
    }

    @Test
    public void hydrateBoolean() throws Exception {
        ConfigValue longValue = ConfigValueFactory.fromAnyRef(true);
        Config valueHolder = longValue.atKey("value");
        Boolean booleanValue = (Boolean) CodecConfig.hydrateField(Boolean.class, "value", valueHolder);
        Assert.assertEquals(true, booleanValue);
    }

    static class MapHolder {
        public HashMap<String, Integer> map;
    }

    @Test
    public void hydrateMap() throws Exception {
        CodableFieldInfo mapField =
                Fields.getClassFieldMap(MapHolder.class).values().iterator().next();
        Config mapHolder = ConfigFactory.parseString("map { a: 1, b: 2, c: 14 }");
        Map<String, Integer> actual = CodecConfig.hydrateMap(mapField, mapHolder);
        Map<String, Integer> expected = new HashMap<>();
        expected.put("a", 1);
        expected.put("b", 2);
        expected.put("c", 14);
        Assert.assertEquals(expected, actual);
    }
}