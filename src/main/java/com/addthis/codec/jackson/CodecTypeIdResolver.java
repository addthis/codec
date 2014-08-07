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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DatabindContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeIdResolverBase;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class CodecTypeIdResolver extends TypeIdResolverBase {
    private final PluginMap pluginMap;
    private final Collection<NamedType> subtypes;

    public CodecTypeIdResolver(PluginMap pluginMap, JavaType baseType,
                               TypeFactory typeFactory, Collection<NamedType> subtypes) {
        super(baseType, typeFactory);
        this.pluginMap = pluginMap;
        this.subtypes = subtypes;
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
        try {
            Class<?> cls = pluginMap.getClass(id);
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
