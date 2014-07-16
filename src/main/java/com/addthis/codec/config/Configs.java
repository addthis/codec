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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import com.addthis.codec.plugins.PluginMap;
import com.addthis.codec.reflection.CodableClassInfo;
import com.addthis.codec.reflection.CodableFieldInfo;

import com.google.common.base.Objects;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueFactory;
import com.typesafe.config.ConfigValueType;

public final class Configs {
    private Configs() {}

    public static ConfigObject expandSugar(Config config, CodecConfig codec) {
        if (config.root().size() != 1) {
            throw new ConfigException.Parse(config.root().origin(),
                                            "config root must have exactly one key");
        }
        String category = config.root().keySet().iterator().next();
        PluginMap pluginMap = codec.pluginRegistry().asMap().get(category);
        if (pluginMap == null) {
            throw new ConfigException.BadValue(config.root().get(category).origin(),
                                               category,
                                               "top level key must be a valid category");
        }
        Class<?> baseClass = Objects.firstNonNull(pluginMap.baseClass(), Object.class);
        return expandSugar(baseClass, config.root().get(category), codec);
    }

    public static ConfigObject expandSugar(Class<?> type, ConfigValue config, CodecConfig codec) {
        CodableClassInfo typeInfo = codec.getOrCreateClassInfo(type);
        PluginMap pluginMap = typeInfo.getPluginMap();
        ConfigObject root = resolveType(config, pluginMap);
        String classField = pluginMap.classField();
        if (root.get(classField) != null) {
            try {
                type = pluginMap.getClass((String) root.get(classField).unwrapped());
            } catch (ClassNotFoundException ignored) {
                // try not to throw exceptions or at least checked exceptions from this helper method
            }
        }
        return expandSugarSkipResolve(type, root, codec);
    }

    private static ConfigObject expandSugarSkipResolve(Class<?> type, ConfigObject root, CodecConfig codec) {
        CodableClassInfo resolvedTypeInfo = codec.getOrCreateClassInfo(type);
        ConfigObject fieldDefaults = resolvedTypeInfo.getFieldDefaults().root();
        for (CodableFieldInfo fieldInfo : resolvedTypeInfo.values()) {
            String fieldName = fieldInfo.getName();
            ConfigValue fieldValue = root.get(fieldName);
            if ((fieldValue == null) && (fieldDefaults.get(fieldName) != null)) {
                ConfigValue fieldDefault = fieldDefaults.get(fieldName);
                fieldValue = ConfigValueFactory.fromAnyRef(
                        fieldDefault.unwrapped(), "global default : " + fieldDefault.origin().description());
                root = root.withValue(fieldName, fieldValue);
            }
            if (fieldValue == null) {
                continue;
            }
            if ((fieldInfo.isArray() || fieldInfo.isCollection()) &&
                (fieldValue.valueType() != ConfigValueType.LIST) && fieldInfo.autoArrayEnabled()) {
                fieldValue = ConfigValueFactory.fromIterable(
                        Collections.singletonList(fieldValue.unwrapped()), "auto collection of " +
                                                                           fieldValue.origin().description());
                root = root.withValue(fieldName, fieldValue);
            }
            if (!isCodableType(fieldInfo)) {
                continue;
            }
            if (fieldInfo.isArray() || fieldInfo.isCollection()) {
                if (fieldValue.valueType() != ConfigValueType.LIST) {
                    throw new ConfigException.WrongType(fieldValue.origin(), fieldName,
                                                        ConfigValueType.LIST.name(), fieldValue.valueType().name());
                }
                Class<?> elementType = elementType(fieldInfo);
                boolean nested = fieldInfo.isCollectionArray();
                fieldValue = expandSugarArray(fieldValue, elementType, codec, nested);
            } else if (fieldInfo.isMap()) {
                if (fieldValue.valueType() != ConfigValueType.OBJECT) {
                    throw new ConfigException.WrongType(fieldValue.origin(), fieldName,
                                                        ConfigValueType.OBJECT.name(), fieldValue.valueType().name());
                }
                Class<?> elementType = elementType(fieldInfo);
                boolean nested = fieldInfo.isMapValueArray();
                ConfigObject fieldMap = (ConfigObject) fieldValue;
                Map<String, Object> newMap = new HashMap<>(fieldMap.size());
                for (Map.Entry<String, ConfigValue> mapEntry : fieldMap.entrySet()) {
                    ConfigValue mapValue = mapEntry.getValue();
                    String mapKey = mapEntry.getKey();
                    ConfigValue resolvedMapObject;
                    if (nested) {
                        resolvedMapObject = expandSugarArray(mapValue, elementType, codec, false);
                    } else {
                        resolvedMapObject = expandSugar(elementType, mapValue, codec);
                    }
                    newMap.put(mapKey, resolvedMapObject.unwrapped());
                }
                fieldValue = ConfigValueFactory.fromMap(newMap, fieldMap.origin().description());
            } else {
                fieldValue = expandSugar(fieldInfo.getType(), fieldValue, codec);
            }
            root = root.withValue(fieldName, fieldValue);
        }
        return root;
    }

    private static ConfigList expandSugarArray(ConfigValue fieldValue,
                                                 Class<?> elementType,
                                                 CodecConfig codec,
                                                 boolean nested) {
        ConfigList fieldList = (ConfigList) fieldValue;
        List<Object> newList = new ArrayList<>(fieldList.size());
        for (ConfigValue listEntry : fieldList) {
            ConfigValue listObject;
            if (nested) {
                listObject = expandSugarArray(listEntry, elementType, codec, false);
            } else {
                listObject = expandSugar(elementType, listEntry, codec);
            }
            newList.add(listObject.unwrapped());
        }
        return ConfigValueFactory.fromIterable(newList, fieldList.origin().description());
    }

    private static Class<?> elementType(CodableFieldInfo fieldInfo) {
        Class<?> elementType = fieldInfo.getType();
        if (fieldInfo.isMap()) {
            elementType = fieldInfo.getMapValueClass();
        } else if (fieldInfo.isCollection()) {
            elementType = fieldInfo.getCollectionClass();
        }
        return elementType;
    }

    private static boolean isCodableType(CodableFieldInfo fieldInfo) {
        Class<?> expectedType = elementType(fieldInfo);
        if (expectedType.isAssignableFrom(String.class)) {
            return false;
        } else if ((expectedType == boolean.class) || (expectedType == Boolean.class)) {
            return false;
        } else if (expectedType == AtomicBoolean.class) {
            return false;
        } else if (Number.class.isAssignableFrom(expectedType) || expectedType.isPrimitive()) {
            // primitive numeric types are not subclasses of Number, so just catch all non-booleans
            return false;
        } else if (expectedType.isEnum()) {
            return false;
        } else {
            return true;
        }
    }

    private static ConfigObject resolveType(ConfigValue objectOrArray, PluginMap pluginMap) {
        String classField = pluginMap.classField();
        if (objectOrArray.valueType() == ConfigValueType.LIST) {
            Class<?> arrayType = pluginMap.arraySugar();
            if (arrayType != null) {
                ConfigObject aliasDefaults = pluginMap.aliasDefaults("_array");
                String arrayFieldName = aliasDefaults.toConfig().getString("_primary");
                String arraySugarName = pluginMap.getLastAlias("_array");
                return ConfigFactory.empty().withValue(
                        classField, ConfigValueFactory.fromAnyRef(
                                arraySugarName, pluginMap.category() + " array sugar : " +
                                                pluginMap.config().root().get("_array").origin().description()))
                                    .withValue(arrayFieldName, objectOrArray)
                                    .withFallback(aliasDefaults)
                                    .root();
            } else {
                throw new ConfigException.WrongType(objectOrArray.origin(),
                                                    "found an array instead of an object, but no array type set");
            }
        }
        if (objectOrArray.valueType() != ConfigValueType.OBJECT) {
            throw new ConfigException.WrongType(objectOrArray.origin(),
                                                "invalid config type of " + objectOrArray.valueType() +
                                                " for " + pluginMap);
        }
        ConfigObject root = (ConfigObject) objectOrArray;
        ConfigValue classValue = root.get(classField);
        // normal, explicit typing
        if ((classValue != null) && (classValue.valueType() == ConfigValueType.STRING)) {
            String classValueString = (String) classValue.unwrapped();
            ConfigObject aliasDefaults = pluginMap.aliasDefaults(classValueString);
            return root.withFallback(aliasDefaults);
        }
        if (root.size() == 1) {
            String onlyKey = root.keySet().iterator().next();
            ConfigValue onlyValue = root.values().iterator().next();
            if (onlyValue.valueType() == ConfigValueType.OBJECT) {
                ConfigObject onlyObject = (ConfigObject) onlyValue;
                try {
                    pluginMap.getClass(onlyKey); // make sure key is a valid type
                    return onlyObject.withValue(classField,
                                                ConfigValueFactory.fromAnyRef(onlyKey, "single key to type from " +
                                                                                       root.origin().description()));
                } catch (ClassNotFoundException ignored) {
                }
            }
        }
        ConfigValue defaultObject = pluginMap.config().root().get("_default");
        if (defaultObject != null) {
            String defaultName = pluginMap.getLastAlias("_default");
            return root.withValue(classField, ConfigValueFactory.fromAnyRef(defaultName,
                                                                            pluginMap.category() + " default type : " +
                                                                            defaultObject.origin().description()));
        }
        return root;
    }
}
