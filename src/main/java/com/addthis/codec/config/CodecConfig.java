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

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.addthis.codec.codables.SuperCodable;
import com.addthis.codec.plugins.PluginRegistry;
import com.addthis.codec.reflection.CodableClassInfo;
import com.addthis.codec.reflection.CodableFieldInfo;
import com.addthis.maljson.JSONArray;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Decodes {@link Config} and associated classes into runtime objects. */
public final class CodecConfig {

    private static final Logger log = LoggerFactory.getLogger(CodecConfig.class);

    public static CodecConfig getDefault() {
        return DefaultCodecConfig.DEFAULT;
    }

    private final Config         globalConfig;
    private final PluginRegistry pluginRegistry;
    private final ConcurrentMap<Class<?>, CodableClassInfo> fieldMaps = new ConcurrentHashMap<>();

    public CodecConfig(Config globalConfig) {
        this(globalConfig, new PluginRegistry(globalConfig));
    }

    public CodecConfig(Config globalConfig, PluginRegistry pluginRegistry) {
        this.globalConfig = globalConfig;
        this.pluginRegistry = pluginRegistry;
    }

    /** public facing end point */
    public <T> T decodeObject(Class<T> type, ConfigValue configValue) {
        return hydrateCustom(type, configValue);
    }

    /** called when the expected type hasn't been inspected yet */
    Object hydrateField(CodableFieldInfo field, Config config) {
        // must use wildcards to get around CodableFieldInfo erasing array types (for now)
        Class<?> expectedType = field.getType();
        String fieldName = field.getName();
        if ((config == null) || !config.hasPath(fieldName)) {
            return null;
        } else if (field.isArray()) { // check CodableFieldInfo instead of expectedType
            ConfigValue configValue = config.root().get(fieldName);
            if ((configValue.valueType() != ConfigValueType.LIST) &&
                field.autoArrayEnabled()) {
                Object singleArrayValue = hydrateField(expectedType, fieldName, config);
                Object wrappingArray    = Array.newInstance(expectedType, 1);
                Array.set(wrappingArray, 0, singleArrayValue);
                return wrappingArray;
            }
            return hydrateArray(expectedType, fieldName, config);
        } else if (field.isMap()) {
            return hydrateMap(field, config);
        } else if (field.isCollection()) {
            return hydrateCollection(field, config);
        } else if (expectedType.isAssignableFrom(String.class)) {
            return config.getString(fieldName);
        } else if ((expectedType == boolean.class) || (expectedType == Boolean.class)) {
            return config.getBoolean(fieldName);
        } else if (expectedType == AtomicBoolean.class) {
            return new AtomicBoolean(config.getBoolean(fieldName));
        } else if (Number.class.isAssignableFrom(expectedType) || expectedType.isPrimitive()) {
            // primitive numeric types are not subclasses of Number, so just catch all non-booleans
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

    /** variant to get around CodableInfo/ CodecJSON edge cases. Only works for certain types */
    Object hydrateField(Class<?> expectedType, String fieldName, Config config) {
        if ((config == null) || !config.hasPath(fieldName)) {
            return null;
        } else if (expectedType.isAssignableFrom(String.class)) {
            return config.getString(fieldName);
        } else if ((expectedType == boolean.class) || (expectedType == Boolean.class)) {
            return config.getBoolean(fieldName);
        } else if (expectedType == AtomicBoolean.class) {
            return new AtomicBoolean(config.getBoolean(fieldName));
        } else if (Number.class.isAssignableFrom(expectedType) || expectedType.isPrimitive()) {
            // primitive numeric types are not subclasses of Number, so just catch all non-booleans
            return hydrateNumber(expectedType, fieldName, config);
        } else if (expectedType.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) expectedType,
                                config.getString(fieldName).toUpperCase());
        } else {
            // assume codable instead of checking
            return hydrateCustom(expectedType, config.getValue(fieldName));
        }
    }

    /** called when the expected type is a number */
    @SuppressWarnings("MethodMayBeStatic")
    Object hydrateNumber(Class<?> type, String fieldName, Config config) {
        if ((type == Short.class) || (type == short.class)) {
            return config.getNumber(fieldName).shortValue();
        } else if ((type == Integer.class) || (type == int.class)) {
            return config.getInt(fieldName);
        } else if ((type == Long.class) || (type == long.class)) {
            return config.getLong(fieldName);
        } else if ((type == Float.class) || (type == float.class)) {
            return config.getNumber(fieldName).floatValue();
        } else if ((type == Double.class) || (type == double.class)) {
            return config.getDouble(fieldName);
        } else if (type == AtomicInteger.class) {
            return new AtomicInteger(config.getInt(fieldName));
        } else if (type == AtomicLong.class) {
            return new AtomicLong(config.getLong(fieldName));
        } else {
            throw new RuntimeException("unsupported numeric or primitive type");
        }
    }

    /** called when the expected type is a non-standard object */
    private <T> T hydrateCustom(Class<T> type, ConfigValue configValue) {
        CodableClassInfo classInfo = getOrCreateClassInfo(type);

        // config is "unexpectedly" a list; if the base class has registered a handler, use it
        if (configValue.valueType() == ConfigValueType.LIST) {
            Class<?> arrarySugar = classInfo.getArraySugar();
            if (arrarySugar != null) {
                classInfo = getOrCreateClassInfo(arrarySugar);
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
        return hydrateObject(classInfo, type, (ConfigObject) configValue);
    }

    /** called when the expected type is a non-standard object */
    private <T> T hydrateObject(CodableClassInfo classInfo, Class<T> type, ConfigObject configObject) {
        String classField = classInfo.getClassField();
        ConfigValue typeValue = configObject.get(classField);
        String stype = null;
        if ((typeValue != null) && (typeValue.valueType() == ConfigValueType.STRING)) {
            stype = (String) typeValue.unwrapped();
        }
        // if otherwise doomed to fail, try supporting "type-value : {...}"  syntax
        if ((stype == null) && (configObject.size() == 1) &&
            (Modifier.isAbstract(type.getModifiers()) ||
             Modifier.isInterface(type.getModifiers()))) {
            String sugarType = configObject.keySet().iterator().next();
            try {
                if (classInfo.getClass(sugarType) != null) {
                    configObject = (ConfigObject) configObject.get(sugarType);
                    stype = sugarType;
                }
            } catch (ClassNotFoundException ignored) {
            }
        }
        Class<?> subType;
        if (stype == null) {
            // if no type field is set, then try using the default (if any)
            subType = classInfo.getDefaultSugar();
        } else {
            try {
                subType = classInfo.getClass(stype);
                configObject = configObject.withoutKey(classField);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        if (subType != null) {
            classInfo = getOrCreateClassInfo(subType);
            type = (Class<T>) subType;
        }
        try {
            T objectShell = type.newInstance();
            String className = type.getName();
            Config objectConfig = configObject.toConfig();
            try {
                objectConfig = objectConfig.withFallback(globalConfig.getConfig(className));
            } catch (ConfigException logged) {
                log.debug("failed to get defaults for {}", className, logged);
            }
            populateObjectFields(classInfo, objectShell, objectConfig);
            return objectShell;
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new RuntimeException(ex);
        }
    }

    /** called when the expected type is an array */
    Object hydrateArray(Class<?> componentType, String fieldName, Config config) {
        if ((config == null) || !config.hasPath(fieldName)) {
            return null;
        } else if (componentType.isAssignableFrom(String.class)) {
            return config.getStringList(fieldName).toArray();
        } else if (componentType.isEnum()) {
            List<String> nameList = config.getStringList(fieldName);
            Enum[] enums = new Enum[nameList.size()];
            int index = 0;
            for (String name : nameList) {
                enums[index++] = Enum.valueOf((Class<? extends Enum>) componentType,
                                              name.toUpperCase());
            }
            return enums;
        } else if (componentType == Boolean.class) {
            List<Boolean> booleanList = config.getBooleanList(fieldName);
            return booleanList.toArray(new Boolean[booleanList.size()]);
        } else if (componentType == boolean.class) {
            List<Boolean> booleanList = config.getBooleanList(fieldName);
            boolean[]  booleanArray = new boolean[booleanList.size()];
            int index = 0;
            for (Boolean bool : booleanList) {
                booleanArray[index++] = bool;
            }
            return booleanArray;
        } else if (Number.class.isAssignableFrom(componentType)  || componentType.isPrimitive()) {
            // primitive numeric types are not subclasses of Number, so just catch all non-booleans
            return hydrateNumberArray(componentType, fieldName, config);
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

    /** called when the expected type is a numeric array */
    @SuppressWarnings("MethodMayBeStatic")
    Object hydrateNumberArray(Class<?> type, String fieldName, Config config) {
        if (type == Short.class) {
            List<Integer> integerList = config.getIntList(fieldName);
            Short[] shorts = new Short[integerList.size()];
            int index = 0;
            for (Integer integer : integerList) {
                shorts[index++] = integer.shortValue();
            }
            return shorts;
        } else if (type == short.class) {
            List<Integer> integerList = config.getIntList(fieldName);
            short[] shorts = new short[integerList.size()];
            int index = 0;
            for (Integer integer : integerList) {
                shorts[index++] = integer.shortValue();
            }
            return shorts;
        } else if (type == Integer.class) {
            List<Integer> integerList = config.getIntList(fieldName);
            return integerList.toArray(new Integer[integerList.size()]);
        } else if (type == int.class) {
            List<Integer> integerList = config.getIntList(fieldName);
            int[] ints = new int[integerList.size()];
            int index = 0;
            for (Integer integer : integerList) {
                ints[index++] = integer;
            }
            return ints;
        } else if (type == Long.class) {
            List<Long> longList = config.getLongList(fieldName);
            return longList.toArray(new Long[longList.size()]);
        } else if (type == long.class) {
            List<Long> longList = config.getLongList(fieldName);
            long[] longs = new long[longList.size()];
            int index = 0;
            for (Long l : longList) {
                longs[index++] = l;
            }
            return longs;
        } else if (type == Double.class) {
            List<Double> doubleList = config.getDoubleList(fieldName);
            return doubleList.toArray(new Double[doubleList.size()]);
        } else if (type == double.class) {
            List<Double> doubleList = config.getDoubleList(fieldName);
            double[] doubles = new double[doubleList.size()];
            int index = 0;
            for (Double doub : doubleList) {
                doubles[index++] = doub;
            }
            return doubles;
        } else if (type == Float.class) {
            List<Double> doubleList = config.getDoubleList(fieldName);
            Float[] floats = new Float[doubleList.size()];
            int index = 0;
            for (Double doub : doubleList) {
                floats[index++] = doub.floatValue();
            }
            return floats;
        } else if (type == float.class) {
            List<Double> doubleList = config.getDoubleList(fieldName);
            float[] floats = new float[doubleList.size()];
            int index = 0;
            for (Double doub : doubleList) {
                floats[index++] = doub.floatValue();
            }
            return floats;
        } else if (type == AtomicInteger.class) {
            List<Integer> integerList = config.getIntList(fieldName);
            AtomicInteger[] atomicIntegers = new AtomicInteger[integerList.size()];
            int index = 0;
            for (Integer integer : integerList) {
                atomicIntegers[index++] = new AtomicInteger(integer);
            }
            return atomicIntegers;
        } else if (type == AtomicLong.class) {
            List<Long> longList = config.getLongList(fieldName);
            AtomicLong[] atomicLongs = new AtomicLong[longList.size()];
            int index = 0;
            for (Long l : longList) {
                atomicLongs[index++] = new AtomicLong(l);
            }
            return atomicLongs;
        } else {
            throw new RuntimeException("unsupported numeric type");
        }
    }

    Map hydrateMap(CodableFieldInfo field, Config config) {
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
                map.put(key, hydrateField(vc, key, configMap));
            }
        }
        return map;
    }

    Collection hydrateCollection(CodableFieldInfo field, Config config) {
        Collection col;
        try {
            col = (Collection) field.getType().newInstance();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        Class vc = field.getCollectionClass();
        boolean ar = field.isCollectionArray();
        if (!ar) {
            Object[] asArray = (Object[]) hydrateArray(vc, field.getName(), config);
            Collections.addAll(col, asArray);
        } else {
            ConfigList configValues = config.getList(field.getName());
            for (ConfigValue configValue : configValues) {
                Config arrayContainer = configValue.atKey("array");
                Object[] arrayValue = (Object[]) hydrateArray(vc, "array", arrayContainer);
                col.add(arrayValue);
            }
        }
        return col;
    }

    /** given a class, instance, and config.. turn config values into field values */
    private void populateObjectFields(CodableClassInfo classInfo, Object objectShell,
                                              Config config) {
        if (objectShell instanceof ConfigCodable) {
            ((ConfigCodable) objectShell).fromConfigObject(config.root());
            return;
        }
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

    // like the one in Fields.java, but non-static and using a possibly non-default registry
    private CodableClassInfo getOrCreateClassInfo(Class<?> clazz) {
        CodableClassInfo fieldMap = fieldMaps.get(clazz);
        if (fieldMap == null) {
            fieldMap = new CodableClassInfo(clazz, pluginRegistry);
            fieldMaps.put(clazz, fieldMap);
        }
        return fieldMap;
    }
}
