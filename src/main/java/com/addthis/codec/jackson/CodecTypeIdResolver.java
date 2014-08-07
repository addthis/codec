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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.addthis.codec.plugins.PluginMap;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class CodecTypeIdResolver extends TypeIdResolverBase {
    private final PluginMap pluginMap;
    private final Map<String, Class<?>> extraSubTypes;

    public CodecTypeIdResolver(PluginMap pluginMap, JavaType baseType,
                               TypeFactory typeFactory, Collection<NamedType> subtypes) {
        super(baseType, typeFactory);
        if (!subtypes.isEmpty()) {
            Map<String, Class<?>> mutableExtraSubTypes = new HashMap<>(subtypes.size());
            for (NamedType namedType : subtypes) {
                if (namedType.hasName()) {
                    mutableExtraSubTypes.put(namedType.getName(), namedType.getType());
                }
            }
            this.extraSubTypes = Collections.unmodifiableMap(mutableExtraSubTypes);
        } else {
            this.extraSubTypes = Collections.emptyMap();
        }
        this.pluginMap = pluginMap;
    }

    public boolean isValidTypeId(String typeId) {
        if ((pluginMap.getClassIfConfigured(typeId) != null) || extraSubTypes.containsKey(typeId)) {
            return true;
        }
        try {
            Class<?> cls = pluginMap.getClass(typeId);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }

    @Override
    public JavaType typeFromId(DatabindContext context, String id) {
        return _typeFromId(id, context.getTypeFactory());
    }

    @Override public String idFromValue(Object value) {
        return pluginMap.getClassName(value.getClass());
    }

    @Override public String idFromValueAndType(Object value, Class<?> suggestedType) {
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
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "Invalid type id '"+id+"' (for id type 'Id.class'): no such class found", e);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Invalid type id '"+id+"' (for id type 'Id.class'): "+e.getMessage(), e);
        }
    }
}
