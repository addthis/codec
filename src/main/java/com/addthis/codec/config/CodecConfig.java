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

import java.lang.reflect.Modifier;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.addthis.codec.codables.SuperCodable;
import com.addthis.codec.reflection.CodableClassInfo;
import com.addthis.codec.reflection.CodableFieldInfo;
import com.addthis.codec.reflection.Fields;
import com.addthis.maljson.JSONArray;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Decodes {@link Config} and associated classes into runtime objects. */
public final class CodecConfig {
    private CodecConfig() {}

    private static final Logger log = LoggerFactory.getLogger(CodecConfig.class);

    /** public facing end point */
    public static <T> T decodeObject(Class<T> type, ConfigValue configValue) {
        return hydrateCustom(type, configValue);
    }

    /** called when the expected type hasn't been inspected yet */
    private static Object hydrateField(CodableFieldInfo<?> field, Config config) {
        // must use wildcards to get around CodableFieldInfo erasing array types (for now)
        Class<?> expectedType = field.getType();
        String   fieldName    = field.getName();
        if ((config == null) || !config.hasPath(fieldName)) {
            return null;
        } else if (field.isArray()) { // check CodableFieldInfo instead of expectedType
            return hydrateArray(expectedType, fieldName, config);
        } else if (field.isMap()) {
            return hydrateMap(field, config);
        } else if (field.isCollection()) {
            return hydrateCollection(field, config);
        } else if (expectedType.isAssignableFrom(String.class)) {
            return config.getString(fieldName);
        } else if (expectedType == boolean.class || expectedType == Boolean.class) {
            return config.getBoolean(fieldName);
        } else if (expectedType == AtomicBoolean.class) {
            return new AtomicBoolean(config.getBoolean(fieldName));
        } else if (Number.class.isAssignableFrom(expectedType)) {
            return hydrateNumber(expectedType, fieldName, config);
        } else if (expectedType.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) expectedType,
                                config.getString(fieldName).toUpperCase());
        } else if (field.isCodable()) {
            return hydrateCustom(expectedType, config.getValue(fieldName));
        } else {
            return null;
        }
    }

    /** called when the expected type is a number */
    static <T> T hydrateNumber(Class<T> type, String fieldName, Config config) {
        Number num;
        if (type == Short.class) {
            num = config.getNumber(fieldName).shortValue();
        } else if (type == Integer.class) {
            num = config.getInt(fieldName);
        } else if (type == Long.class) {
            num = config.getLong(fieldName);
        } else if (type == Float.class) {
            num = config.getNumber(fieldName).floatValue();
        } else if (type == Double.class) {
            num = config.getDouble(fieldName);
        } else if (type == AtomicInteger.class) {
            num = new AtomicInteger(config.getInt(fieldName));
        } else if (type == AtomicLong.class) {
            num = new AtomicLong(config.getLong(fieldName));
        } else {
            num = null;
        }
        return (T) num;
    }

    /** called when the expected type is a non-standard object */
    private static <T> T hydrateCustom(Class<T> type, ConfigValue configValue) {
        CodableClassInfo classInfo = Fields.getClassFieldMap(type);

        // config is "unexpectedly" a list; if the base class has registered a handler, use it
        if (configValue.valueType() == ConfigValueType.LIST) {
            Class<?> arrarySugar = classInfo.getArraySugar();
            if (arrarySugar != null) {
                classInfo = Fields.getClassFieldMap(arrarySugar);
                for (CodableFieldInfo fieldInfo : classInfo.values()) {
                    if (fieldInfo.isArray() && (fieldInfo.getType() == type)) {
                        configValue = configValue.atKey(fieldInfo.getName()).root();
                        type = (Class<T>) arrarySugar;
                        break;
                    }
                }
                if (configValue instanceof JSONArray) {
                    log.warn("failed to find an appropriate array field for class marked as array" +
                             "sugar: {}", arrarySugar);
                }
            }
        }
        ConfigObject configObject = (ConfigObject) configValue;

        String classField = classInfo.getClassField();
        ConfigValue typeValue = configObject.get(classField);
        String stype = null;
        if ((typeValue != null) && (typeValue.valueType() == ConfigValueType.STRING)) {
            stype = (String) typeValue.unwrapped();
        }
        if ((stype == null) && Modifier.isAbstract(type.getModifiers()) &&
            (configObject.size() == 1)) {
            // if otherwise doomed to fail, try supporting "type-value : {...}"  syntax
            stype = configObject.keySet().iterator().next();
            configObject = (ConfigObject) configObject.get(stype);
        }
        try {
            if (stype != null) {
                Class<?> atype = classInfo.getClass(stype);
                classInfo = Fields.getClassFieldMap(atype);
                type = (Class<T>) atype;
                configObject.remove(classField);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        try {
            T objectShell = type.newInstance();
            populateObjectFields(classInfo, objectShell, configObject);
            return objectShell;
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** called when the expected type is an array */
    private static Object[] hydrateArray(Class<?> componentType,
                                             String fieldName,
                                             Config config) {
        if ((config == null) || !config.hasPath(fieldName)) {
            return null;
        } else if (componentType.isAssignableFrom(String.class)) {
            return config.getStringList(fieldName).toArray();
        } else if (Number.class.isAssignableFrom(componentType)) {
            return hydrateNumberArray((Class<? extends Number>) componentType, fieldName, config);
        } else if (componentType.isEnum()) {
            List<String> nameList = config.getStringList(fieldName);
            Enum[] enums = new Enum[nameList.size()];
            int index = 0;
            for (String name : nameList) {
                enums[index++] = Enum.valueOf((Class<? extends Enum>) componentType,
                                              name.toUpperCase());
            }
            return enums;
        } else if ((componentType == boolean.class) || (componentType == Boolean.class)) {
            return config.getBooleanList(fieldName).toArray();
        } else {
            ConfigList configValues = config.getList(fieldName);
            Object[] customs = new Object[configValues.size()];
            int index = 0;
            for (ConfigValue value : configValues) {
                if ((value == null) || (value.valueType() == ConfigValueType.NULL)) {
                    customs[index++] = null;
                } else {
                    customs[index++] = hydrateCustom(componentType, value);
                }
            }
            return customs;
        }
    }

    /** called when the expected type is a number */
    static Number[] hydrateNumberArray(Class<? extends Number> type, String fieldName, Config config) {
        Number[] num;
        if (type == Short.class) {
            List<Integer> integerList = config.getIntList(fieldName);
            Short[] shorts = new Short[integerList.size()];
            int index = 0;
            for (Integer integer : integerList) {
                shorts[index++] = integer.shortValue();
            }
            num = shorts;
        } else if (type == Integer.class) {
            List<Integer> integerList = config.getIntList(fieldName);
            num = integerList.toArray(new Integer[integerList.size()]);
        } else if (type == Long.class) {
            List<Long> longList = config.getLongList(fieldName);
            num = longList.toArray(new Long[longList.size()]);
        } else if (type == Double.class) {
            List<Double> doubleList = config.getDoubleList(fieldName);
            num = doubleList.toArray(new Double[doubleList.size()]);
        } else if (type == Float.class) {
            List<Double> doubleList = config.getDoubleList(fieldName);
            Float[] floats = new Float[doubleList.size()];
            int index = 0;
            for (Double doub : doubleList) {
                floats[index++] = doub.floatValue();
            }
            num = floats;
        } else if (type == AtomicInteger.class) {
            List<Integer> integerList = config.getIntList(fieldName);
            AtomicInteger[] atomicIntegers = new AtomicInteger[integerList.size()];
            int index = 0;
            for (Integer integer : integerList) {
                atomicIntegers[index++] = new AtomicInteger(integer);
            }
            num = atomicIntegers;
        } else if (type == AtomicLong.class) {
            List<Long> longList = config.getLongList(fieldName);
            AtomicLong[] atomicLongs = new AtomicLong[longList.size()];
            int index = 0;
            for (Long l : longList) {
                atomicLongs[index++] = new AtomicLong(l);
            }
            num = atomicLongs;
        } else {
            num = null;
        }
        return num;
    }

    static Map hydrateMap(CodableFieldInfo<?> field, Config config) {
        Map map;
        try {
            map = (Map) field.getType().newInstance();
        } catch (IllegalAccessException | InstantiationException ex) {
            throw new RuntimeException(ex);
        }
        Class vc = (Class) field.getGenericTypes()[1];
        boolean va = field.isMapValueArray();
        Config configMap = config.getConfig(field.getName());
        for (String key : configMap.root().keySet()) {
            if (field.isInterned()) {
                key = key.intern();
            }
            if (va) {
                map.put(key, hydrateArray(vc, key, configMap));
            } else {
                map.put(key, hydrateCustom(vc, configMap.getValue(key)));
            }
        }
        return map;
    }

    static Collection hydrateCollection(CodableFieldInfo<?> field, Config config) {
        Collection col;
        try {
            col = (Collection) field.getType().newInstance();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        Class vc = field.getCollectionClass();
        boolean ar = field.isCollectionArray();
        if (!ar) {
            Object[] asArray = hydrateArray(vc, field.getName(), config);
            Collections.addAll(col, asArray);
        } else {
            ConfigList configValues = config.getList(field.getName());
            for (ConfigValue configValue : configValues) {
                Config arrayContainer = configValue.atKey("array");
                Object[] arrayValue = hydrateArray(vc, "array", arrayContainer);
                col.add(arrayValue);
            }
        }
        return col;
    }

    /** given a class, instance, and config.. turn config values into field values */
    private static void populateObjectFields(CodableClassInfo classInfo, Object objectShell,
                                              ConfigObject configObject) {
        if (objectShell instanceof ConfigCodable) {
            ((ConfigCodable) objectShell).fromConfigObject(configObject);
            return;
        }
        Config config = configObject.toConfig();
        for (CodableFieldInfo field : classInfo.values()) {
            if (field.isWriteOnly()) {
                continue;
            }

            Object value = hydrateField(field, config);

            field.set(objectShell, value);
        }
        if (objectShell instanceof SuperCodable) {
            ((SuperCodable) objectShell).postDecode();
        }
    }
}
