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
package com.addthis.codec.reflection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.addthis.codec.annotations.FieldConfig;
import com.addthis.codec.annotations.Pluggable;
import com.addthis.codec.plugins.PluginMap;
import com.addthis.codec.plugins.PluginRegistry;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableSortedMap;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Beta
public class CodableClassInfo {
    private static final Logger log = LoggerFactory.getLogger(CodableClassInfo.class);

    @Nonnull private final Class<?>     baseClass;
    @Nonnull private final PluginMap    pluginMap;
    @Nonnull private final Config       fieldDefaults;
    @Nonnull private final ImmutableSortedMap<String, CodableFieldInfo> classData;

    public CodableClassInfo(@Nonnull Class<?> clazz) {
        this(clazz, ConfigFactory.load(), PluginRegistry.defaultRegistry());
    }

    public CodableClassInfo(@Nonnull Class<?> clazz,
                            @Nonnull Config globalDefaults,
                            @Nonnull PluginRegistry pluginRegistry) {

        // skip native classes
        if (Fields.isNative(clazz) || clazz.isArray()) {
            classData = ImmutableSortedMap.of();
            baseClass = clazz;
            pluginMap = PluginMap.EMPTY;
            fieldDefaults = ConfigFactory.empty();
            return;
        }

        // find any parent class (or itself) with a Pluggable annotation
        Class<?> findBaseClass = clazz;
        PluginMap findPluginMap = PluginMap.EMPTY;

        Class<?> ptr = clazz;
        while (ptr != null) {
            Pluggable pluggable = ptr.getAnnotation(Pluggable.class);
            if (pluggable != null) {
                String category = pluggable.value();
                findPluginMap = pluginRegistry.asMap().get(category);
                if (findPluginMap != null) {
                    findBaseClass = ptr;
                    break;
                } else {
                    log.warn("missing plugin map for {}, reached from {}", ptr, clazz);
                    findPluginMap = PluginMap.EMPTY;
                }
            }
            ptr = ptr.getSuperclass();
        }
        pluginMap = findPluginMap;
        baseClass = findBaseClass;

        // find all fields in the class and its parent classes, and aggregate any defaults
        Map<String, Field> fields = new HashMap<>();
        // slower than using unwrapped, mutable conversions but this preserves origins
        ConfigObject buildDefaults = ConfigFactory.empty().root();

        Class<?> ptrForFields = clazz;
        while (ptrForFields != null) {
            String canonicalClassName = ptrForFields.getCanonicalName();
            ConfigObject classDefaults;
            if ((canonicalClassName != null) && globalDefaults.hasPath(canonicalClassName)) {
                classDefaults = globalDefaults.getObject(canonicalClassName);
            } else {
                classDefaults = ConfigFactory.empty().root();
            }
            for (Field field : ptrForFields.getDeclaredFields()) {
                if (fields.get(field.getName()) == null) {
                    fields.put(field.getName(), field);
                } else {
                    classDefaults = classDefaults.withoutKey(field.getName());
                    log.debug("({}) ignoring field in parent class ({}) with duplicate name ({})",
                              clazz, ptrForFields, field.getName());
                }
            }
            for (Map.Entry<String, ConfigValue> pair : classDefaults.entrySet()) {
                if (!buildDefaults.containsKey(pair.getKey())) {
                    buildDefaults = buildDefaults.withValue(pair.getKey(), pair.getValue());
                }
            }
            ptrForFields = ptrForFields.getSuperclass();
        }
        fieldDefaults = buildDefaults.toConfig();

        // turn all the found fields into CodableFieldInfo objects
        Map<String, CodableFieldInfo> buildClassData = buildFieldInfoMap(fields.values());
        classData = ImmutableSortedMap.<String, CodableFieldInfo>naturalOrder()
                                      .putAll(buildClassData).build();
    }

    @Nonnull public PluginMap getPluginMap() {
        return pluginMap;
    }

    @Nonnull public Config getFieldDefaults() {
        return fieldDefaults;
    }

    @Nonnull public Class<?> getBaseClass() {
        return baseClass;
    }

    @Nonnull public String getClassField() {
        return pluginMap.classField();
    }

    @Nullable public Class<?> getArraySugar() {
        return pluginMap.arraySugar();
    }

    @Nullable public Class<?> getDefaultSugar() {
        return pluginMap.defaultSugar();
    }

    @Nullable public String getClassName(Object val) {
        if (val.getClass() != baseClass) {
            return pluginMap.getClassName(val.getClass());
        } else {
            return null;
        }
    }

    @Nonnull public Class<?> getClass(String name) throws ClassNotFoundException {
        return pluginMap.getClass(name);
    }

    public int size() {
        return classData.size();
    }

    @Nonnull public Collection<CodableFieldInfo> values() {
        return classData.values();
    }

    /** Immutable view of codable fields as a map of field names to {@link CodableFieldInfo}s. */
    @Nonnull public Map<String, CodableFieldInfo> fields() {
        return classData;
    }

    /**
     * Decide whether it is okay to read/ write a field. If configured via an annotation on the field, use that.
     * Otherwise return true only if the field is both public and non-final.
     */
    private static boolean isCodable(Field field) {
        FieldConfig fieldConfigPolicy = field.getAnnotation(FieldConfig.class);
        if (fieldConfigPolicy != null) {
            return fieldConfigPolicy.codable();
        }
        int modifierBitSet = field.getModifiers();
        return !Modifier.isFinal(modifierBitSet) && Modifier.isPublic(modifierBitSet);
    }

    @Nonnull private static Map<String, CodableFieldInfo> buildFieldInfoMap(Iterable<Field> fields) {
        SortedMap<String, CodableFieldInfo> buildClassData = new TreeMap<>();
        for (Field field : fields) {
            if (isCodable(field)) {
                Class<?> type = field.getType();
                boolean array = type.isArray();
                if (array) {
                    type = type.getComponentType();
                    if (type == null) {
                        throw new IllegalStateException("!! null array type for " + field + " !!");
                    }
                }
                CodableFieldInfo info = new CodableFieldInfo(field, type);
                // extract info bits
                if (array) {
                    info.updateBits(CodableFieldInfo.ARRAY);
                }
                // extract generics info
                if (!Fields.isNative(type)) {
                    info.setGenericTypes(Fields.collectTypes(type, field.getGenericType()));
                }
                buildClassData.put(field.getName(), info);
            }
        }
        return buildClassData;
    }

}
