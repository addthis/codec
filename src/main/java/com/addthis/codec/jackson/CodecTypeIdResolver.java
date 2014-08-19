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
package com.addthis.codec.jackson;

import java.util.Collection;

import com.addthis.codec.plugins.PluginMap;
import com.addthis.codec.plugins.PluginRegistry;
import com.addthis.codec.plugins.Plugins;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Maps;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class CodecTypeIdResolver extends TypeIdResolverBase {
    private final PluginMap pluginMap;
    private final BiMap<String, Class<?>> extraSubTypes;
    private final PluginRegistry pluginRegistry;

    public CodecTypeIdResolver(PluginMap pluginMap, JavaType baseType,
                               TypeFactory typeFactory, Collection<NamedType> subtypes,
                               PluginRegistry pluginRegistry) {
        super(baseType, typeFactory);
        this.pluginRegistry = pluginRegistry;
        if (!subtypes.isEmpty()) {
            BiMap<String, Class<?>> mutableExtraSubTypes = HashBiMap.create(subtypes.size());
            for (NamedType namedType : subtypes) {
                if (namedType.hasName()) {
                    mutableExtraSubTypes.put(namedType.getName(), namedType.getType());
                }
            }
            this.extraSubTypes = Maps.unmodifiableBiMap(mutableExtraSubTypes);
        } else {
            this.extraSubTypes = ImmutableBiMap.of();
        }
        this.pluginMap = pluginMap;
    }

    public boolean isValidTypeId(String typeId) {
        Class<?> cls = pluginMap.getClassIfConfigured(typeId);
        if (cls == null) {
            cls = extraSubTypes.get(typeId);
        }
        try {
            if (cls == null) {
                cls = pluginMap.getClass(typeId);
            }
        } catch (ClassNotFoundException ignored) {
            return false;
        }
        return _baseType.getRawClass().isAssignableFrom(cls);
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id) {
        return _typeFromId(id, context.getTypeFactory());
    }

    @Override public String idFromValue(Object value) {
        return pluginMap.getClassName(value.getClass());
    }

    @Override public String idFromValueAndType(Object value, Class<?> suggestedType) {
        String alt = pluginMap.asBiMap().inverse().get(suggestedType);
        if (alt != null) {
            return alt;
        }
        if (extraSubTypes.containsValue(suggestedType)) {
            return extraSubTypes.inverse().get(suggestedType);
        }
        return pluginMap.getClassName(suggestedType);
    }

    @Override public JavaType typeFromId(String id) {
        return _typeFromId(id, _typeFactory);
    }

    @Override public JsonTypeInfo.Id getMechanism() {
        return JsonTypeInfo.Id.CUSTOM;
    }

    private JavaType _typeFromId(String id, TypeFactory typeFactory) {
        if (id == null) {
            return null;
        }
        Class<?> cls = pluginMap.getClassIfConfigured(id);
        if (cls == null) {
            cls = extraSubTypes.get(id);
        }
        try {
            if (cls == null) {
                cls = pluginMap.getClass(id);
            }
            return typeFactory.constructSpecializedType(_baseType, cls);
        } catch (Exception e) {
            String helpMessage = Plugins.classNameSuggestions(pluginRegistry, pluginMap, id);
            throw new IllegalArgumentException(helpMessage, e);
        }
    }
}
