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
package com.typesafe.config.impl;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Ordering;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;

public final class TypeUnsafe {
    private TypeUnsafe() {}

    private static final Splitter SPLITTER = Splitter.on(':');

    public static Config toSorted(Config config) {
        Ordering<Map.Entry<String, ConfigValue>> entryOrdering =
                Ordering.from(
                        new Comparator<Object>() {
                            @Override public int compare(Object o1, Object o2) {
                                if (o1 instanceof Number) {
                                    if (o2 instanceof Number) {
                                        return Integer.compare(((Number) o1).intValue(), ((Number) o2).intValue());
                                    } else {
                                        return -1;
                                    }
                                } else if (o2 instanceof Number) {
                                    return 1;
                                } else {
                                    return ((String) o1).compareTo((String) o2);
                                }
                            }
                        })
                        .lexicographical()
                        .onResultOf(new Function<Map.Entry<String, ConfigValue>, List<Object>>() {
                            @Nullable
                            @Override
                            public List<Object> apply(@Nullable Map.Entry<String, ConfigValue> input) {
                                ConfigOrigin origin = input.getValue().origin();
                                String description = origin.description();
                                List<?> stringList = SPLITTER.splitToList(description);
                                if (origin.lineNumber() < 0) {
                                    return (List<Object>) stringList;
                                }
                                List<Object> comparableList = new ArrayList<Object>(
                                        stringList.subList(0, stringList.size() - 1));
                                comparableList.add(origin.lineNumber());
                                return comparableList;
                            }
                        });
        return toSorted(entryOrdering, config);
    }

    public static Config toSorted(Comparator<Map.Entry<String, ConfigValue>> comparator, Config config) {
        Ordering<Map.Entry<String, ConfigValue>> entryOrdering = Ordering.from(comparator);
        return sortTransformHelper(entryOrdering, (AbstractConfigObject) config.root()).toConfig();
    }

    public static Config toSorted(Ordering<Map.Entry<String, ConfigValue>> ordering, Config config) {
        return sortTransformHelper(ordering, (AbstractConfigObject) config.root()).toConfig();
    }

    private static ConfigObject sortTransformHelper(Ordering<Map.Entry<String, ConfigValue>> ordering,
                                                    AbstractConfigObject config) {
        ImmutableMap.Builder<String, AbstractConfigValue> builder = ImmutableMap.builder();
        for (Map.Entry<String, ConfigValue> entry : ordering.sortedCopy(config.entrySet())) {
            String entryKey = entry.getKey();
            ConfigValue entryValue = entry.getValue();
            if (entryValue instanceof AbstractConfigObject) {
                entryValue = sortTransformHelper(ordering, (AbstractConfigObject) entryValue);
            }
            builder.put(entryKey, (AbstractConfigValue) entryValue);
        }
        return new SimpleConfigObject(config.origin(), builder.build(),
                                      config.resolveStatus(), config.ignoresFallbacks());
    }
}
