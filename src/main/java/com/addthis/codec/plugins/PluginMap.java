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

import javax.annotation.Nullable;

import java.lang.reflect.Field;

import java.util.Iterator;
import java.util.Set;

import com.google.common.base.Objects;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Maps;

import com.typesafe.config.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginMap {

    private static final Logger log = LoggerFactory.getLogger(PluginMap.class);

    public static final PluginMap EMPTY = new PluginMap();

    private final BiMap<String, Class<?>> map;

    private final String category;
    private final String classField;

    @Nullable private final Class<?>       defaultSugar;
    @Nullable private final Class<?>       arraySugar;
    @Nullable private final String         arrayField;
    @Nullable private final PluginRegistry typoRegistry;

    public PluginMap(String category, Config config) {
        this(category, config, null);
    }

    public PluginMap(String category, Config config, @Nullable PluginRegistry registry) {
        this.category = category;
        classField = config.getString("_field");
        boolean errorMissing = config.getBoolean("_strict");
        Set<String> labels = config.root().keySet();
        BiMap<String, Class<?>> mutableMap = HashBiMap.create(labels.size());
        for (String label : labels) {
            if (label.charAt(0) == '_') {
                continue;
            }
            String value = config.getString(label);
            try {
                Class<?> classValue = Class.forName(value);
                mutableMap.put(label, classValue);
            } catch (ClassNotFoundException maybeSwallowed) {
                if (errorMissing) {
                    throw new RuntimeException(maybeSwallowed);
                } else {
                    log.warn("plugin category {} with alias {} is pointing to missing class {}",
                             category, label, value);
                }
            }
        }
        map = Maps.unmodifiableBiMap(mutableMap);
        if (config.hasPath("_array")) {
            String arraySugarName = config.getString("_array");
            Class<?> configuredArraySugar = map.get(arraySugarName);
            arrayField = searchArraySugarFieldName(configuredArraySugar);
            if (arrayField != null) {
                arraySugar = configuredArraySugar;
            } else {
                arraySugar = null;
            }
        } else {
            arraySugar = null;
            arrayField = null;
        }
        if (config.hasPath("_default")) {
            String defaultName = config.getString("_default");
            defaultSugar = map.get(defaultName);
        } else {
            defaultSugar = null;
        }
        this.typoRegistry = registry;
    }

    private PluginMap() {
        map = ImmutableBiMap.of();
        classField = "class";
        category = "unknown";
        defaultSugar = null;
        arraySugar = null;
        arrayField = null;
        typoRegistry = null;
    }

    /** A thread safe, immutable bi-map view of this plugin map. */
    public BiMap<String, Class<?>> asBiMap() {
        return map;
    }

    public String classField() {
        return classField;
    }

    public String category() {
        return category;
    }

    @Nullable public Class<?> arraySugar() {
        return arraySugar;
    }

    @Nullable public String arrayField() {
        return arrayField;
    }

    @Nullable public Class<?> defaultSugar() {
        return defaultSugar;
    }

    public String getClassName(Class<?> type) {
        String alt = map.inverse().get(type);
        if (alt != null) {
            return alt;
        } else {
            return type.getName();
        }
    }

    public Class<?> getClass(String type) throws ClassNotFoundException {
        Class<?> alt = map.get(type);
        if (alt != null) {
            return alt;
        }
        try {
            return Class.forName(type);
        } catch (ClassNotFoundException ex) {
            throw classNameSuggestions(type, ex);
        }
    }

    private ClassNotFoundException classNameSuggestions(String type, ClassNotFoundException ex) {
        Set<String> classNames = map.keySet();
        if (classNames.isEmpty()) {
            return ex;
        }
        StringBuilder builder = new StringBuilder();

        if (category != null) {
            builder.append("Could not instantiate an instance of the ");
            builder.append(category);
            builder.append(" category that you have specified");
        } else {
            builder.append("Could not instantiate something you have specified");
        }
        builder.append(" with \"");
        builder.append(type);
        builder.append("\".");
        if (typoRegistry != null) {
            for (PluginMap pluginMap : typoRegistry.asMap().values()) {
                if (pluginMap.asBiMap().containsKey(type)) {
                    builder.append("\nIt looks like you tried to instantiate a ");
                    builder.append(pluginMap.category());
                    builder.append(" and I am expecting a ");
                    builder.append(category);
                    builder.append(".");
                    return new ClassNotFoundException(builder.toString(), ex);
                }
            }
        }
        builder.append("\nPerhaps you intended one of the following: ");
        Iterator<String> iterator = classNames.iterator();
        while (iterator.hasNext()) {
            String name = iterator.next();

            if (!iterator.hasNext() && classNames.size() > 1) {
                builder.append("or ");
            }

            builder.append('"');
            builder.append(name);
            builder.append('"');
            builder.append(" ");
        }
        builder.append("?\n");
        return new ClassNotFoundException(builder.toString(), ex);
    }

    @Override public String toString() {
        return Objects.toStringHelper(this)
                      .add("category", category)
                      .add("classField", classField)
                      .add("defaultSugar", defaultSugar)
                      .add("arraySugar", arraySugar)
                      .add("typoRegistry", typoRegistry)
                      .add("map", map)
                      .toString();
    }

    private static String searchArraySugarFieldName(Class<?> arraySugar) {
        Class<?> clazzptr = arraySugar;
        while (clazzptr != null) {
            for (Field field : clazzptr.getDeclaredFields()) {
                Class<?> fieldType = field.getType();
                if (fieldType.isArray()
                    && fieldType.getComponentType().isAssignableFrom(arraySugar)) {
                    return field.getName();
                }
            }
            clazzptr = clazzptr.getSuperclass();
        }
        log.warn("failed to find an appropriate array field for class marked as array" +
                 "sugar: {}", arraySugar);
        return null;
    }
}
