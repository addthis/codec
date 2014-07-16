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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.addthis.basis.collect.ConcurrentHashMapV8;

import com.addthis.codec.annotations.Bytes;
import com.addthis.codec.annotations.Time;
import com.addthis.codec.codables.SuperCodable;
import com.addthis.codec.plugins.PluginMap;
import com.addthis.codec.plugins.PluginRegistry;
import com.addthis.codec.plugins.Plugins;
import com.addthis.codec.reflection.CodableClassInfo;
import com.addthis.codec.reflection.CodableFieldInfo;
import com.addthis.codec.reflection.RequiredFieldException;

import com.google.common.base.Objects;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;

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
    private final ConcurrentMap<Class<?>, CodableClassInfo> fieldMaps = new ConcurrentHashMapV8<>();

    public CodecConfig(Config globalConfig) {
        this(globalConfig, new PluginRegistry(globalConfig));
    }

    public CodecConfig(Config globalConfig, PluginRegistry pluginRegistry) {
        this.globalConfig = globalConfig;
        this.pluginRegistry = pluginRegistry;
    }

    public Config globalConfig() {
        return globalConfig;
    }

    public PluginRegistry pluginRegistry() {
        return pluginRegistry;
    }

    /**
     * Instantiate an object of the requested type using the provided config.
     */
    public <T> T decodeObject(Class<T> type, Config config) {
        CodableClassInfo classInfo = getOrCreateClassInfo(type);
        return hydrateObject(classInfo, classInfo.getPluginMap(), type, config.root());
    }

    /**
     * Instantiate an object without a compile time expected type. This expects a config of the
     * form "{plugin-category: {...}}". ie. there should be exactly one top level key and that
     * key should be a valid, loaded, plug-in category.
     */
    public <T> T decodeObject(Config config) {
        if (config.root().size() != 1) {
            throw new ConfigException.Parse(config.root().origin(),
                                            "config root must have exactly one key");
        }
        String category = config.root().keySet().iterator().next();
        PluginMap pluginMap = pluginRegistry.asMap().get(category);
        if (pluginMap == null) {
            throw new ConfigException.BadValue(config.root().get(category).origin(),
                                               category,
                                               "top level key must be a valid category");
        }
        return hydrateObject(null, pluginMap, null, config.root().get(category));
    }

    /** visibility intended for internal use, but should be safe to use */
    @Nullable
    public Object hydrateField(@Nonnull CodableFieldInfo field, @Nonnull Config config, @Nullable Object objectShell) {
        // must use wildcards to get around CodableFieldInfo erasing array types (for now)
        Class<?> expectedType = field.getType();
        String fieldName = field.getName();
        if (!config.hasPath(fieldName)) {
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
            return hydrateMap(field, config, objectShell);
        } else if (field.isCollection()) {
            return hydrateCollection(field, config, objectShell);
        } else if (expectedType.isAssignableFrom(String.class)) {
            return config.getString(fieldName);
        } else if ((expectedType == boolean.class) || (expectedType == Boolean.class)) {
            return config.getBoolean(fieldName);
        } else if (expectedType == AtomicBoolean.class) {
            return new AtomicBoolean(config.getBoolean(fieldName));
        } else if (Number.class.isAssignableFrom(expectedType) || expectedType.isPrimitive()) {
            // primitive numeric types are not subclasses of Number, so just catch all non-booleans
            return hydrateNumber(field, expectedType, fieldName, config);
        } else if (expectedType.isEnum()) {
            return Enum.valueOf((Class<? extends Enum>) expectedType,
                                config.getString(fieldName).toUpperCase());
        } else if (field.isCodable()) {
            return hydrateObject(expectedType, config.getValue(fieldName));
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
            return hydrateObject(expectedType, config.getValue(fieldName));
        }
    }

    /** called when the expected type is a number */
    @SuppressWarnings("MethodMayBeStatic")
    Object hydrateNumber(CodableFieldInfo fieldInfo, Class<?> type, String fieldName, Config config) {
        // handle floating points
        if ((type == Float.class) || (type == float.class)) {
            return config.getNumber(fieldName).floatValue();
        } else if ((type == Double.class) || (type == double.class)) {
            return config.getDouble(fieldName);
        }

        Time time = fieldInfo.getField().getAnnotation(Time.class);
        Long asLong;
        if (time != null) {
            asLong = config.getDuration(fieldName, time.value());
        } else if (fieldInfo.getField().getAnnotation(Bytes.class) != null) {
            asLong = config.getBytes(fieldName);
        } else {
            asLong = config.getLong(fieldName);
        }

        if ((type == Short.class) || (type == short.class)) {
            return Shorts.checkedCast(asLong);
        } else if ((type == Integer.class) || (type == int.class)) {
            return Ints.checkedCast(asLong);
        } else if ((type == Long.class) || (type == long.class)) {
            return asLong;
        } else if (type == AtomicInteger.class) {
            return new AtomicInteger(Ints.checkedCast(asLong));
        } else if (type == AtomicLong.class) {
            return new AtomicLong(asLong);
        } else {
            throw new ConfigException.BadValue(config.origin(), fieldName,
                                               "unsupported numeric or primitive type");
        }
    }

    /** called when the expected type is a number */
    @SuppressWarnings("MethodMayBeStatic")
    Object hydrateNumber(Class<?> type, String fieldName, Config config) {
        if ((type == Short.class) || (type == short.class)) {
            return Shorts.checkedCast(config.getLong(fieldName));
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
            throw new ConfigException.BadValue(config.origin(), fieldName,
                                               "unsupported numeric or primitive type");
        }
    }

    /** called when the expected type is a non-standard object */
    private <T> T hydrateObject(Class<T> type, ConfigValue configValue) {
        CodableClassInfo info = getOrCreateClassInfo(type);
        PluginMap pluginMap = info.getPluginMap();
        return hydrateObject(info, pluginMap, type, configValue);
    }

    /** called when the expected type is a non-standard object */
    private <T> T hydrateObject(@Nullable CodableClassInfo info,
                                PluginMap pluginMap,
                                @Nullable Class<T> type,
                                ConfigValue configValue) {
        // config is "unexpectedly" a list; if the base class has registered a handler, use it
        if (configValue.valueType() == ConfigValueType.LIST) {
            Class<?> arrarySugar = pluginMap.arraySugar();
            if (arrarySugar != null) {
                // plugin map assumed to be the same (not enforced atm)
                type = (Class<T>) arrarySugar;
                info = getOrCreateClassInfo(type);
                configValue = configValue.atKey(pluginMap.arrayField()).root();
                return createAndPopulate(info, type, (ConfigObject) configValue);
            }
        }
        return hydrateObject(info, pluginMap, type, (ConfigObject) configValue);
    }

    /** called when the expected type is a non-standard object */
    private <T> T hydrateObject(@Nullable CodableClassInfo info,
                                PluginMap pluginMap,
                                @Nullable Class<T> type,
                                ConfigObject configObject) {
        String classField = pluginMap.classField();
        ConfigValue typeValue = configObject.get(classField);
        String stype = null;
        if (typeValue != null) {
            stype = configObject.toConfig().getString(classField);
        }
        // if otherwise doomed to fail, try supporting "type-value : {...}"  syntax
        if ((stype == null) && (configObject.size() == 1) &&
            ((type == null) || Modifier.isAbstract(type.getModifiers()) ||
             Modifier.isInterface(type.getModifiers()))) {
            String sugarType = configObject.keySet().iterator().next();
            boolean success = false;
            try {
                type = (Class<T>) pluginMap.getClass(sugarType);
                success = true;
            } catch (ClassNotFoundException ignored) {
                // there could still be a default, so defer throwing an exception
            }
            if (success) {
                configObject = (ConfigObject) configObject.get(sugarType);
                info = getOrCreateClassInfo(type);
                return createAndPopulate(info, type, configObject);
            }
        }
        if (stype == null) {
            // if no type field is set, then try using the default (if any)
            if (pluginMap.defaultSugar() != null) {
                type = (Class<T>) pluginMap.defaultSugar();
                info = null;
            }
        } else {
            try {
                type = (Class<T>) pluginMap.getClass(stype);
                configObject = configObject.withoutKey(classField);
                info = null;
            } catch (ClassNotFoundException e) {
                String helpMessage = Plugins.classNameSuggestions(pluginRegistry, pluginMap, stype);
                throw new ConfigException.UnresolvedSubstitution(
                        configObject.origin(), helpMessage, e);
            }
        }
        if (type == null) {
            throw new ConfigException.Parse(configObject.origin(),
                                            "expected type must either be a valid pluggable or concrete class");
        }
        if (info == null) {
            info = getOrCreateClassInfo(type);
        }
        return createAndPopulate(info, type, configObject);
    }

    private <T> T createAndPopulate(CodableClassInfo info, Class<T> type, ConfigObject configObject) {
        try {
            T objectShell = type.newInstance();
            populateObjectFields(info, objectShell, configObject.toConfig());
            return objectShell;
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new ConfigException.BadValue(configObject.origin(), type.getName(),
                                               "failed to get a concrete, working pluggable", ex);
        }
    }

    /** called when the expected type is an array */
    Object hydrateArray(Class<?> componentType, String fieldName, Config config) {
        if ((config == null) || !config.hasPath(fieldName)) {
            return null;
        } else if (componentType.isAssignableFrom(String.class)) {
            List<String> stringList = config.getStringList(fieldName);
            return stringList.toArray(new String[stringList.size()]);
        } else if (componentType.isEnum()) {
            List<String> nameList = config.getStringList(fieldName);
            Enum[] enums = (Enum[]) Array.newInstance(componentType, nameList.size());
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
            Object[] customs = (Object[]) Array.newInstance(componentType, configValues.size());
            int index = 0;
            for (ConfigValue value : configValues) {
                if ((value == null) || (value.valueType() == ConfigValueType.NULL)) {
                    customs[index++] = null;
                } else {
                    customs[index++] = hydrateObject(componentType, value);
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
                shorts[index++] = Shorts.checkedCast(integer);
            }
            return shorts;
        } else if (type == short.class) {
            List<Integer> integerList = config.getIntList(fieldName);
            short[] shorts = new short[integerList.size()];
            int index = 0;
            for (Integer integer : integerList) {
                shorts[index++] = Shorts.checkedCast(integer);
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
            throw new ConfigException.BadValue(config.origin(), fieldName,
                                               "unsupported numeric or primitive type");
        }
    }

    Map hydrateMap(CodableFieldInfo field, Config config) {
        return hydrateMap(field, config, null);
    }

    Map hydrateMap(CodableFieldInfo field, Config config, @Nullable Object objectShell) {
        Class<?> type = field.getType();
        Map map;
        if (Modifier.isAbstract(type.getModifiers()) || Modifier.isInterface(type.getModifiers())) {
            if (objectShell != null) {
                map = (Map) field.get(objectShell);
            } else {
                throw new ConfigException.BugOrBroken("field: " + field + " is not declared as a concrete " +
                                                      "Map subclass and there is no enclosing object provided " +
                                                      "to check for an existing Map to mutate");
            }
            if (map == null) {
                throw new ConfigException.BugOrBroken("field: " + field + " is not declared as a concrete " +
                                                      "Map subclass and there was no default instantiated object to " +
                                                      "add configured key-value pairs to. Problem is most likely in " +
                                                      "the java source code for the field's enclosing class: " +
                                                      objectShell.getClass());
            }
        } else {
            try {
                map = (Map) type.newInstance();
            } catch (IllegalAccessException | InstantiationException ex) {
                throw new ConfigException.BadValue(config.origin(), field.getName(),
                                                   "failed to instantiate map implementation", ex);
            }
        }
        Class vc = (Class) field.getGenericTypes()[1];
        boolean va = field.isMapValueArray();
        Config configMap = config.getConfig(field.getName());
        for (Map.Entry<String, ConfigValue> entry : configMap.root().entrySet()) {
            String key = entry.getKey();
            if (field.isInterned()) {
                key = key.intern();
            }
            // control for map keys that might have unexpected behavior when parsed as paths
            Config fieldHolder = entry.getValue().atKey("field");
            if (va) {
                map.put(key, hydrateArray(vc, "field", fieldHolder));
            } else {
                map.put(key, hydrateField(vc, "field", fieldHolder));
            }
        }
        return map;
    }

    Collection hydrateCollection(CodableFieldInfo field, Config config) {
        return hydrateCollection(field, config, null);
    }

    Collection hydrateCollection(CodableFieldInfo field, Config config, @Nullable Object objectShell) {
        Class<?> type = field.getType();
        Collection<Object> col;
        if (Modifier.isAbstract(type.getModifiers()) || Modifier.isInterface(type.getModifiers())) {
            if (objectShell != null) {
                col = (Collection<Object>) field.get(objectShell);
            } else {
                throw new ConfigException.BugOrBroken("field: " + field + " is not declared as a concrete " +
                                                      "Collection subclass and there is no enclosing object provided " +
                                                      "to check for an existing Collection to mutate");
            }
            if (col == null) {
                throw new ConfigException.BugOrBroken("field: " + field + " is not declared as a concrete " +
                                                      "Collection subclass and there was no default instantiated " +
                                                      "object to add configured values to. Problem is most likely in " +
                                                      "the java source code for the field's enclosing class: " +
                                                      objectShell.getClass());
            }
        } else {
            try {
                col = (Collection<Object>) field.getType().newInstance();
            } catch (Exception ex) {
                throw new ConfigException.BadValue(config.origin(), field.getName(),
                                                   "failed to get a concrete, working class", ex);
            }
        }
        Class vc = field.getCollectionClass();
        boolean ar = field.isCollectionArray();
        if (!ar) {
            // check for autocollection wrapping
            ConfigValueType configValueType = config.root().get(field.getName()).valueType();
            if ((configValueType != ConfigValueType.LIST) && field.autoArrayEnabled()) {
                Object singleObject = hydrateField(vc, field.getName(), config);
                col.add(singleObject);
            } else {
                // safe to cast to Object[] since cannot have collections of primitives
                Object[] asArray = (Object[]) hydrateArray(vc, field.getName(), config);
                Collections.addAll(col, asArray);
            }
        } else {
            // autocollection is a little ambiguous for nested lists, so just don't support
            ConfigList configValues = config.getList(field.getName());
            for (ConfigValue configValue : configValues) {
                Config arrayContainer = configValue.atKey("array");
                Object arrayValue = hydrateArray(vc, "array", arrayContainer);
                col.add(arrayValue);
            }
        }
        return col;
    }

    /** given a class, instance, and config.. turn config values into field values */
    private void populateObjectFields(CodableClassInfo classInfo, Object objectShell,
                                              Config config) {
        Config fieldDefaults = classInfo.getFieldDefaults();
        if (objectShell instanceof ConfigCodable) {
            ((ConfigCodable) objectShell).fromConfigObject(config.root(), fieldDefaults.root());
            return;
        }
        Collection<String> unusedKeys = new HashSet<>(config.root().keySet());
        for (CodableFieldInfo field : classInfo.values()) {
            if (field.isWriteOnly()) {
                continue;
            }
            unusedKeys.remove(field.getName());
            Object value = hydrateField(field, config, objectShell);
            if (value == null) {
                value = hydrateField(field, fieldDefaults, objectShell);
            }
            try {
                field.set(objectShell, value);
            } catch (RequiredFieldException ex) {
                throw new ConfigException.Null(config.origin(), field.getName(),
                                               field.toString(), ex);
            }
        }
        if (!unusedKeys.isEmpty()) {
            for (Iterator<String> unusedKeyIterator = unusedKeys.iterator();
                 unusedKeyIterator.hasNext(); ) {
                String unusedKey = unusedKeyIterator.next();
                if (unusedKey.charAt(0) == '_') {
                    unusedKeyIterator.remove();
                }
            }
            if (!unusedKeys.isEmpty()) {
                throw new ConfigException.UnresolvedSubstitution(
                        config.origin(), "unrecognized key(s) " + unusedKeys.toString());
            }
        }
        if (objectShell instanceof SuperCodable) {
            ((SuperCodable) objectShell).postDecode();
        }
    }

    // like the one in Fields.java, but non-static and using a possibly non-default registry
    CodableClassInfo getOrCreateClassInfo(Class<?> clazz) {
        CodableClassInfo fieldMap = fieldMaps.get(clazz);
        if (fieldMap == null) {
            fieldMap = new CodableClassInfo(clazz, globalConfig, pluginRegistry);
            fieldMaps.put(clazz, fieldMap);
        }
        return fieldMap;
    }

    @Override public String toString() {
        return Objects.toStringHelper(this)
                      .add("globalConfig", globalConfig)
                      .add("pluginRegistry", pluginRegistry)
                      .add("fieldMaps", fieldMaps)
                      .toString();
    }
}
