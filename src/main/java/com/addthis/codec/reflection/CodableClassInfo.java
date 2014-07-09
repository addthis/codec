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
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.addthis.codec.annotations.FieldConfig;
import com.addthis.codec.annotations.Pluggable;
import com.addthis.codec.plugins.PluginMap;
import com.addthis.codec.plugins.PluginRegistry;

import com.google.common.collect.ImmutableSortedMap;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
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

    @Nullable public static Type[] collectTypes(Class<?> type, Type node) {
        List<Type> l = collectTypes(new ArrayList<Type>(), type, node);
        while (!l.isEmpty()) {
            int ni = l.lastIndexOf(null);
            if (ni < 0) {
                break;
            }
            if (ni >= (l.size() - 1)) {
                l.remove(ni);
            } else {
                l.set(ni, l.get(l.size() - 1));
                l.remove(l.size() - 1);
            }
        }
        if (l.isEmpty()) {
            return null;
        } else {
            Type[] t = new Type[l.size()];
            l.toArray(t);
            return t;
        }
    }

    @Nonnull private static Map<String, CodableFieldInfo> buildFieldInfoMap(Iterable<Field> fields) {
        SortedMap<String, CodableFieldInfo> buildClassData = new TreeMap<>();
        for (Field field : fields) {
            int mod = field.getModifiers();
            boolean store = ((mod & Modifier.FINAL) == 0) && ((mod & Modifier.PUBLIC) != 0);
            // extract annotations
            FieldConfig fieldConfigPolicy = field.getAnnotation(FieldConfig.class);
            if (fieldConfigPolicy != null) {
                field.setAccessible(true);
                store |= fieldConfigPolicy.codable();
            }
            // field must be public and non-final or annotated with a store policy
            if (!store) {
                continue;
            }
            Class<?> type = field.getType();
            boolean array = type.isArray();
            if (array) {
                type = type.getComponentType();
                if (type == null) {
                    throw new IllegalStateException("!! null array type for " + field + " !!");
                }
            }
            CodableFieldInfo info = new CodableFieldInfo(field, type, fieldConfigPolicy);
            // extract info bits
            if (array) {
                info.updateBits(CodableFieldInfo.ARRAY);
            }
            // extract generics info
            if (!Fields.isNative(type)) {
                info.setGenericTypes(collectTypes(type, field.getGenericType()));
            }
            buildClassData.put(field.getName(), info);
        }
        return buildClassData;
    }

    @Nonnull private static List<Type> collectTypes(@Nonnull List<Type> list,
                                           @Nullable Class<?> type,
                                           @Nullable Type node) {
        if ((type == null) && (node == null)) {
            return list;
        }
        if (node instanceof Class) {
            if (type != null) {
                collectTypes(list, ((Class<?>) node).getSuperclass(), type.getGenericSuperclass());
            } else {
                collectTypes(list, ((Class<?>) node).getSuperclass(), null);
            }
        } else {
            if (type != null) {
                collectTypes(list, null, type.getGenericSuperclass());
            } else {
                collectTypes(list, null, null);
            }
        }
        if (node instanceof ParameterizedType) {
            List<Type> tl = Arrays.asList(((ParameterizedType) node).getActualTypeArguments());
            for (Type t : tl) {
                if ((t instanceof Class) || (t instanceof GenericArrayType)) {
                    list.add(t);
                } else if (t instanceof ParameterizedType) {
                    list.add(((ParameterizedType) t).getRawType());
                } else {
                    list.add(null);
                }
            }
        }
        return list;
    }
}
