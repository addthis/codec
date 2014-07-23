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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Maps;

import com.addthis.codec.embedded.com.typesafe.config.Config;
import com.addthis.codec.embedded.com.typesafe.config.ConfigException;
import com.addthis.codec.embedded.com.typesafe.config.ConfigFactory;
import com.addthis.codec.embedded.com.typesafe.config.ConfigObject;
import com.addthis.codec.embedded.com.typesafe.config.ConfigValue;
import com.addthis.codec.embedded.com.typesafe.config.ConfigValueType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

@Beta
public class PluginMap {

    private static final Logger log = LoggerFactory.getLogger(PluginMap.class);

    public static final PluginMap EMPTY = new PluginMap();

    @Nonnull private final Config config;
    @Nonnull private final BiMap<String, Class<?>> map;
    @Nonnull private final Map<String, String> aliases;
    @Nonnull private final Set<String> inlinedAliases;

    @Nonnull private final String category;
    @Nonnull private final String classField;

    @Nullable private final Class<?> baseClass;

    public PluginMap(@Nonnull String category, @Nonnull Config config) {
        this.config = config;
        this.category = checkNotNull(category);
        classField = config.getString("_field");
        boolean errorMissing = config.getBoolean("_strict");
        if (config.hasPath("_class")) {
            String baseClassName = config.getString("_class");
            try {
                baseClass = Class.forName(baseClassName);
            } catch (ClassNotFoundException e) {
                log.error("could not find specified base class {} for category {}",
                          baseClassName, category);
                throw new RuntimeException(e);
            }
        } else {
            baseClass = null;
        }
        Set<String> labels = config.root().keySet();
        BiMap<String, Class<?>> mutableMap = HashBiMap.create(labels.size());
        Map<String, String> mutableAliasMap = new HashMap<>();
        Set<String> mutableInlinedAliasSet = new HashSet<>();
        for (String label : labels) {
            if (!((label.charAt(0) != '_') || "_array".equals(label) || "_default".equals(label))) {
                continue;
            }
            ConfigValue configValue = config.root().get(label);
            String className;
            if (configValue.valueType() == ConfigValueType.STRING) {
                className = (String) configValue.unwrapped();
            } else if (configValue.valueType() == ConfigValueType.OBJECT) {
                ConfigObject configObject = (ConfigObject) configValue;
                className = configObject.toConfig().getString("_class");
                if (configObject.toConfig().hasPath("_inline") &&
                        configObject.toConfig().getBoolean("_inline")) {
                    mutableInlinedAliasSet.add(label);
                }
            } else {
                throw new ConfigException.WrongType(configValue.origin(), label,
                                                    "STRING OR OBJECT", configValue.valueType().toString());
            }
            if (labels.contains(className)) {
                // points to another alias
                mutableAliasMap.put(label, className);
            } else {
                try {
                    Class<?> foundClass = findAndValidateClass(className);
                    mutableMap.put(label, foundClass);
                } catch (ClassNotFoundException maybeSwallowed) {
                    if (errorMissing) {
                        throw new RuntimeException(maybeSwallowed);
                    } else {
                        log.warn("plugin category {} with alias {} is pointing to missing class {}",
                                 category, label, className);
                    }
                }
            }
        }
        map = Maps.unmodifiableBiMap(mutableMap);
        aliases = Collections.unmodifiableMap(mutableAliasMap);
        checkAliasesForCycles();
        inlinedAliases = Collections.unmodifiableSet(mutableInlinedAliasSet);
    }

    private PluginMap() {
        config = ConfigFactory.empty();
        map = ImmutableBiMap.of();
        aliases = Collections.emptyMap();
        inlinedAliases = Collections.emptySet();
        classField = "class";
        category = "unknown";
        baseClass = null;
    }

    @Nonnull public BiMap<String, Class<?>> asBiMap() {
        return map;
    }

    @Nonnull public Config config() {
        return config;
    }

    @Nonnull public ConfigObject aliasDefaults(String alias) {
        ConfigValue configValue = config.root().get(alias);
        ConfigObject defaults;
        if ((configValue != null) && (configValue.valueType() == ConfigValueType.OBJECT)) {
            defaults = (ConfigObject) configValue;
        } else {
            defaults = ConfigFactory.empty().root();
        }
        String aliasTarget = aliases.get(alias);
        if (aliasTarget != null) {
            defaults = defaults.withFallback(aliasDefaults(aliasTarget));
        }
        return defaults;
    }

    @Nonnull public String classField() {
        return classField;
    }

    @Nonnull public String category() {
        return category;
    }

    @Nonnull public Set<String> inlinedAliases() {
        return inlinedAliases;
    }

    @Nullable public Class<?> arraySugar() {
        return getClassIfConfigured("_array");
    }

    @Nullable public Class<?> defaultSugar() {
        return getClassIfConfigured("_default");
    }

    /** The base class, if any, that all configured plugins must be assignable to. */
    @Nullable public Class<?> baseClass() {
        return baseClass;
    }

    /** Reverse look-up on the bi-map to get the root alias for this type; if none, then returns the class name */
    @Nonnull public String getClassName(Class<?> type) {
        String alt = map.inverse().get(type);
        if (alt != null) {
            return alt;
        } else {
            return type.getName();
        }
    }

    /** Resolves aliases that point to other aliases until pointing to a real class; then return the current label. */
    @Nullable public String getLastAlias(String alias) {
        String aliasTarget = aliases.get(alias);
        if (aliasTarget != null) {
            return getLastAlias(aliasTarget);
        } else {
            if (asBiMap().containsKey(alias)) {
                return alias;
            } else {
                return null;
            }
        }
    }

    /**
     * Resolves aliases until the root class for that alias chain is found or if no such alias exsits, then
     * try find and validate the given string as if it were the class name of an anonymous alias. This means
     * that it if a base _class is defined for this category that all package prefixes will be tried and that
     * any found class must be assignable to the base _class type.
     */
    @Nonnull public Class<?> getClass(String type) throws ClassNotFoundException {
        Class<?> alt = map.get(type);
        if (alt != null) {
            return alt;
        } else {
            String aliasTarget = aliases.get(type);
            if (aliasTarget != null) {
                return getClass(aliasTarget);
            }
        }
        return findAndValidateClass(type);
    }

    /** Like {@link #getClass(String)}, but will return null rather than try to locate a new class. */
    @Nullable public Class<?> getClassIfConfigured(String type) {
        Class<?> alt = map.get(type);
        if (alt != null) {
            return alt;
        } else {
            String aliasTarget = aliases.get(type);
            if (aliasTarget != null) {
                return getClassIfConfigured(aliasTarget);
            }
        }
        return null;
    }

    @Override public String toString() {
        return Objects.toStringHelper(this)
                      .add("category", category)
                      .add("baseClass", baseClass)
                      .add("classField", classField)
                      .add("map", map)
                      .add("aliases", aliases)
                      .add("inlined-aliases", inlinedAliases)
                      .toString();
    }

    @Nonnull private Class<?> findAndValidateClass(String className) throws ClassNotFoundException {
        Class<?> classValue = null;
        // if baseClass is defined, support shared parent package omission
        if (baseClass != null) {
            @Nullable String packageName = baseClass.getPackage().getName();
            while ((packageName != null) && (classValue == null)) {
                String packageSugaredName = packageName + '.' + className;
                try {
                    classValue = Class.forName(packageSugaredName);
                } catch (ClassNotFoundException ignored) {
                    int lastDotIndex = packageName.lastIndexOf('.');
                    if (lastDotIndex >= 0) {
                        packageName = packageName.substring(0, lastDotIndex);
                    } else {
                        packageName = null;
                    }
                }
            }
        }
        if (classValue == null) {
            classValue = Class.forName(className);
        }
        // if baseClass is defined, validate all aliased classes as being valid subtypes
        if ((baseClass != null) && !baseClass.isAssignableFrom(classValue)) {
            throw new ClassCastException(String.format(
                    "plugin %s specified a base class %s and '%s: %s', is not a valid subtype",
                    category, baseClass.getName(), classField, classValue.getName()));
        }
        return classValue;
    }

    private void checkAliasesForCycles() {
        for (String key : aliases.keySet()) {
            Set<String> visited = new HashSet<>(aliases.size());
            checkAliasesForCyclesHelper(key, visited);
        }
    }

    private void checkAliasesForCyclesHelper(String key, Set<String> visited) {
        visited.add(key);
        String nextKey = aliases.get(key);
        if (nextKey == null) {
            // should mean it is present in the bimap
            return;
        }
        if (visited.contains(nextKey)) {
            throw new ConfigException.BadValue(config.root().get(key).origin(), key, "cyclical aliases detected");
        } else {
            checkAliasesForCyclesHelper(nextKey, visited);
        }
    }
}
