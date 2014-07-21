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

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.google.common.primitives.Shorts;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Decodes {@link Config} and associated classes into runtime objects. */
@Beta
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
     * Construct an object of the requested type based on the default values and types (if the requested
     * is not a concrete class).
     */
    public <T> T newDefault(@Nonnull Class<T> type) {
        CodableClassInfo classInfo = getOrCreateClassInfo(type);
        return hydrateObject(classInfo, classInfo.getPluginMap(), type, ConfigFactory.empty().root());
    }

    /** Construct an object of the requested plugin category based on the default type and values */
    public <T> T newDefault(@Nonnull String category) {
        PluginMap pluginMap = Preconditions.checkNotNull(pluginRegistry.asMap().get(category),
                                                         "could not find anything about the category %s", category);
        return hydrateObject(null, pluginMap, null, ConfigFactory.empty().root());
    }

    /**
     * Instantiate an object of the requested type based on the provided config. The config should only contain
     * field and type information for the object to be constructed. Global defaults, plugin configuration, etc, are
     * provided by this CodecConfig instance's globalConfig and pluginRegistry fields.
     */
    public <T> T decodeObject(@Nonnull Class<T> type, @Nonnull Config config) {
        CodableClassInfo classInfo = getOrCreateClassInfo(type);
        return hydrateObject(classInfo, classInfo.getPluginMap(), type, config.root());
    }

    /**
     * Instantiate an object of the requested category based on the provided config. The config should only contain
     * field and type information for the object to be constructed. Global defaults, plugin configuration, etc, are
     * provided by this CodecConfig instance's globalConfig and pluginRegistry fields.
     */
    public <T> T decodeObject(@Nonnull String category, @Nonnull Config config) {
        PluginMap pluginMap = Preconditions.checkNotNull(pluginRegistry.asMap().get(category),
                                                         "could not find anything about the category %s", category);
        return hydrateObject(null, pluginMap, null, config.root());
    }

    /**
     * Tries to parse the string as an isolated typesafe-config object, tries to resolve it, and then calls
     * {@link #decodeObject(Class, Config)} with the resultant config and the passed in type. Pretty much just
     * a convenience function for simple use cases that don't want to care about how ConfigFactory works.
     */
    public <T> T decodeObject(@Nonnull Class<T> type, @Language("HOCON") @Nonnull String configText) {
        Config config = ConfigFactory.parseString(configText).resolve();
        return decodeObject(type, config);
    }

    /**
     * Tries to parse the string as an isolated typesafe-config object, tries to resolve it, and then calls
     * {@link #decodeObject(String, Config)} with the resultant config and the passed in category. Pretty much just
     * a convenience function for simple use cases that don't want to care about how ConfigFactory works.
     */
    public <T> T decodeObject(@Nonnull String category, @Language("HOCON") @Nonnull String configText) {
        PluginMap pluginMap = Preconditions.checkNotNull(pluginRegistry.asMap().get(category),
                                                         "could not find anything about the category %s", category);
        Config config = ConfigFactory.parseString(configText).resolve();
        return hydrateObject(null, pluginMap, null, config.root());
    }

    /**
     * Instantiate an object without a compile time expected type. This expects a config of the
     * form "{plugin-category: {...}}". ie. there should be exactly one top level key and that
     * key should be a valid, loaded, plug-in category.
     */
    public <T> T decodeObject(@Language("HOCON") String configText) {
        Config config = ConfigFactory.parseString(configText).resolve();
        return decodeObject(config);
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
        return hydrateValueAsObject(null, pluginMap, null, config.root().get(category));
    }

    /** visibility intended for internal use, but should be safe to use */
    @Nullable public Object hydrateField(@Nonnull CodableFieldInfo field,
                                         @Nonnull Config config,
                                         @Nonnull Object objectShell) {
        // must use wildcards to get around CodableFieldInfo erasing array types (for now)
        Class<?> expectedType = field.getType();
        String fieldName = field.getName();
        if (expectedType.isAssignableFrom(ConfigValue.class)) {
            // different from hasPath in that this will accept ConfigValueType.NULL
            return config.root().get(fieldName);
        } else if (!config.hasPath(fieldName)) {
            return null;
        } else if (field.isArray()) { // check CodableFieldInfo instead of expectedType
            ConfigValue configValue = config.root().get(fieldName);
            if ((configValue.valueType() != ConfigValueType.LIST) &&
                field.autoArrayEnabled()) {
                Object singleArrayValue = hydrateFieldComponent(expectedType, fieldName, config);
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

    /**
     * Used to hydrate values for arrays, collections, and maps. CodableFieldInfo/ Codec2 design means that we don't
     * get a CodableFieldInfo object here and so some logic (like time/ bytes annotations) is not available.
     *
     * Currently is anti-dry due to some subtle logic differences that complicate abstraction. Some of those logic
     * differences are questionable though so they may not have to be around forever (replicates legacy behavior).
     */
    @Nullable Object hydrateFieldComponent(@Nonnull Class<?> expectedType,
                                           @Nonnull String fieldName,
                                           @Nonnull Config config) {
        if (expectedType.isAssignableFrom(ConfigValue.class)) {
            // different from hasPath in that this will accept ConfigValueType.NULL
            return config.root().get(fieldName);
        } else if (!config.hasPath(fieldName)) {
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

    /** called when the expected type is a codable object, but we know very little about it */
    private <T> T hydrateObject(Class<T> type, ConfigValue configValue) {
        CodableClassInfo info = getOrCreateClassInfo(type);
        PluginMap pluginMap = info.getPluginMap();
        return hydrateValueAsObject(info, pluginMap, type, configValue);
    }

    /** called when the expected type is a codable object, but the config value might not be an object */
    private <T> T hydrateValueAsObject(@Nullable CodableClassInfo info,
                                       PluginMap pluginMap,
                                       @Nullable Class<T> type,
                                       ConfigValue configValue) {

        if (configValue.valueType() != ConfigValueType.OBJECT) {

            if ((type == null)
                || Modifier.isAbstract(type.getModifiers()) || Modifier.isInterface(type.getModifiers())) {

                /* if config value is a list see if the base class has an _array alias */
                if (configValue.valueType() == ConfigValueType.LIST) {
                    Class<T> arrayType = (Class<T>) pluginMap.arraySugar();
                    if (arrayType != null) {
                        Config aliasDefaults = pluginMap.aliasDefaults("_array").toConfig();
                        ConfigObject fieldsValues = configValue.atKey(aliasDefaults.getString("_primary")).root()
                                                               .withFallback(aliasDefaults);
                        CodableClassInfo arrayInfo = getOrCreateClassInfo(arrayType);
                        return createAndPopulate(arrayInfo, arrayType, fieldsValues);
                    } // else just let the error get thrown below
                }
            } else {

                /* for non-pluggable ValueCodable implementors */
                if (ValueCodable.class.isAssignableFrom(type)) {
                    try {
                        T objectShell = type.newInstance();
                        CodableClassInfo configInfo = (info != null) ? info : getOrCreateClassInfo(type);
                        Config fieldDefaults = configInfo.getFieldDefaults();
                        ((ValueCodable) objectShell).fromConfigValue(configValue, fieldDefaults.root());
                        if (objectShell instanceof SuperCodable) {
                            ((SuperCodable) objectShell).postDecode();
                        }
                        return objectShell;
                    } catch (InstantiationException | IllegalAccessException | RuntimeException ex) {
                        throw new ConfigException.BadValue(configValue.origin(), type.getName(),
                                                           "exception during instantiation of a ValueCodable", ex);
                    }
                }
            }

            /* could theoretically support some kind of _simple handler for other non-object types, but have yet to
             * think of a compelling use case. */
            String path = Objects.firstNonNull(info, "some value").toString();
            throw new ConfigException.WrongType(configValue.origin(), path,
                                                "OBJECT", configValue.valueType().toString());
        }

        /* have an object as 'expected'; resolve the type if needed/ possible, then build it */
        return hydrateObject(info, pluginMap, type, (ConfigObject) configValue);
    }

    /** called when the expected type is a codable object whose type may need to be resolved */
    private <T> T hydrateObject(@Nullable CodableClassInfo info,
                                PluginMap pluginMap,
                                @Nullable Class<T> type,
                                ConfigObject configObject) {

        /* look for normal, explicit type syntax. ie. "{type: my-type, val: my-val}" */
        String classField = pluginMap.classField();
        ConfigValue typeValue = configObject.get(classField);
        if (typeValue != null) {
            if (typeValue.valueType() != ConfigValueType.STRING) {
                throw new ConfigException.WrongType(typeValue.origin(), classField,
                                                    "STRING", typeValue.valueType().toString());
            }
            String stype = (String) typeValue.unwrapped();
            try {
                Class<T> normalType = (Class<T>) pluginMap.getClass(stype);
                ConfigObject aliasDefaults = pluginMap.aliasDefaults(stype);
                ConfigObject fieldValues = configObject.withoutKey(classField).withFallback(aliasDefaults);
                CodableClassInfo normalInfo = getOrCreateClassInfo(normalType);
                return createAndPopulate(normalInfo, normalType, fieldValues);
            } catch (ClassNotFoundException e) {
                String helpMessage = Plugins.classNameSuggestions(pluginRegistry, pluginMap, stype);
                throw new ConfigException.UnresolvedSubstitution(configObject.origin(), helpMessage, e);
            }
        }

        /* if no chance of instantiating current type, try to get a new type from various special syntax/ settings */
        if ((type == null) || Modifier.isAbstract(type.getModifiers()) || Modifier.isInterface(type.getModifiers())) {

            /* "type-value : {...}"  syntax; ie. if there is only one key, see if it would be a valid type */
            if (configObject.size() == 1) {
                String singleKeyName = configObject.keySet().iterator().next();
                try {
                    Class<T> singleKeyType = (Class<T>) pluginMap.getClass(singleKeyName);
                    CodableClassInfo singleKeyInfo = getOrCreateClassInfo(singleKeyType);
                    ConfigObject aliasDefaults = pluginMap.aliasDefaults(singleKeyName);
                    ConfigValue configValue = configObject.get(singleKeyName);
                    if (configValue.valueType() != ConfigValueType.OBJECT) {
                        if (aliasDefaults.get("_primary") != null) {
                            // if value is not an object, try supporting _primary syntax to derive one
                            configValue = configValue.atPath((String) aliasDefaults.get("_primary").unwrapped()).root();
                        } else if (ValueCodable.class.isAssignableFrom(singleKeyType)) {
                            // see if the resolved type is innately okay with non-objects
                            try {
                                T objectShell = singleKeyType.newInstance();
                                Config fieldDefaults = singleKeyInfo.getFieldDefaults();
                                // do not merge objects between global defaults and user defaults (incl. alias defaults)
                                ConfigObject mergedDefaults = aliasDefaults;
                                for (Map.Entry<String, ConfigValue> pair : fieldDefaults.entrySet()) {
                                    if (!mergedDefaults.containsKey(pair.getKey())) {
                                        mergedDefaults = mergedDefaults.withValue(pair.getKey(), pair.getValue());
                                    }
                                }
                                ((ValueCodable) objectShell).fromConfigValue(configValue, mergedDefaults);
                                return objectShell;
                            } catch (InstantiationException | IllegalAccessException | RuntimeException ex) {
                                throw new ConfigException.BadValue(configValue.origin(), singleKeyType.getName(),
                                                                   "exception during instantiation of a ValueCodable", ex);
                            }
                        } else {
                            throw new ConfigException.WrongType(configValue.origin(), singleKeyName,
                                                                "OBJECT", configValue.valueType().toString());
                        }
                    }
                    ConfigObject fieldValues = ((ConfigObject) configValue).withFallback(aliasDefaults);
                    return createAndPopulate(singleKeyInfo, singleKeyType, fieldValues);
                } catch (ClassNotFoundException ignored) {
                    // expected when the single key is not a valid alias or class. could avoid exception if we dropped
                    // support for single-keys that are just classes (ie. anonymous aliases), but we'll leave it in
                    // until we have some, more concrete, reason to remove it.
                }
            }

            /* inlined types syntax ie "{ type-value: some-value, some-field: some-other-value, ...}".
             * Opt-in is on a per alias basis, and the target type must be unambiguous amongst aliases
             * that have opted in. The recognized alias label is then replaced with the _primary field. */
            String matched = null;
            for (String alias : pluginMap.inlinedAliases()) {
                if (configObject.get(alias) != null) {
                    if (matched != null) {
                        String message = String.format(
                                "no type specified, more than one key, and both %s and %s match for inlined types.",
                                matched, alias);
                        throw new ConfigException.Parse(configObject.origin(), message);
                    }
                    matched = alias;
                }
            }
            if (matched != null) {
                Class<T> inlinedType = (Class<T>) pluginMap.getClassIfConfigured(matched);
                assert inlinedType != null : "matched is always a key from the pluginMap's inlinedAliases set";
                CodableClassInfo inlinedInfo = getOrCreateClassInfo(inlinedType);
                ConfigObject aliasDefaults = pluginMap.aliasDefaults(matched);
                ConfigValue configValue = configObject.get(matched);
                String primaryField = (String) aliasDefaults.get("_primary").unwrapped();
                ConfigObject fieldValues =  configObject.toConfig().withValue(primaryField, configValue).root()
                                                        .withoutKey(matched)
                                                        .withFallback(aliasDefaults);
                return createAndPopulate(inlinedInfo, inlinedType, fieldValues);
            }

            /* lastly, check for a _default type. */
            Class<T> defaultType = (Class<T>) pluginMap.defaultSugar();
            if (defaultType != null) {
                CodableClassInfo defaultInfo = getOrCreateClassInfo(defaultType);
                ConfigObject aliasDefaults = pluginMap.aliasDefaults("_default");
                ConfigObject fieldValues = configObject.withFallback(aliasDefaults);
                return createAndPopulate(defaultInfo, defaultType, fieldValues);
            }

            /* we know it is not a type we can instantiate, and none of our syntactic sugar picked up anything. */
            throw new ConfigException.Parse(configObject.origin(),
                                            Objects.firstNonNull(type, pluginMap.category())
                                            + " is not a concrete class and cannot figure out a suitable subclass");
        }

        /* type is instantiable -- could just be a random concrete type that doesn't care about pluggable types */
        if (info == null) {
            return createAndPopulate(type, configObject);
        } else {
            return createAndPopulate(info, type, configObject);
        }
    }

    private <T> T createAndPopulate(Class<T> type, ConfigObject configObject) {
        CodableClassInfo info = getOrCreateClassInfo(type);
        return createAndPopulate(info, type, configObject);
    }

    private <T> T createAndPopulate(CodableClassInfo info, Class<T> type, ConfigObject configObject) {
        try {
            T objectShell = type.newInstance();
            if (objectShell instanceof ConfigCodable) {
                ConfigObject newConfig = ((ConfigCodable) objectShell).fromConfigObject(
                        configObject, info.getFieldDefaults().root());
                if (newConfig != null) {
                    populateObjectFields(info, objectShell, newConfig.toConfig());
                }
            } else {
                populateObjectFields(info, objectShell, configObject.toConfig());
            }
            if (objectShell instanceof SuperCodable) {
                ((SuperCodable) objectShell).postDecode();
            }
            return objectShell;
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new ConfigException.BadValue(configObject.origin(), type.getName(),
                                               "failed to get a concrete, working pluggable", ex);
        }
    }

    /** called when the expected type is an array */
    @Nullable Object hydrateArray(@Nonnull Class<?> componentType, @Nonnull String fieldName, @Nonnull Config config) {
        if (!config.hasPath(fieldName)) {
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
            map.clear();
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
                map.put(key, hydrateFieldComponent(vc, "field", fieldHolder));
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
            col.clear();
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
                Object singleObject = hydrateFieldComponent(vc, field.getName(), config);
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
    private void populateObjectFields(@Nonnull CodableClassInfo classInfo,
                                      @Nonnull Object objectShell,
                                      @Nonnull Config config) {
        Config fieldDefaults = classInfo.getFieldDefaults();
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
                throw new ConfigException.BadPath(config.origin(), "unrecognized key(s) " + unusedKeys.toString());
            }
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
