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

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class CodecTypeResolverBuilder extends StdTypeResolverBuilder {
    private final PluginMap pluginMap;
    private final TypeFactory typeFactory;
    private final PluginRegistry pluginRegistry;

    public CodecTypeResolverBuilder(PluginMap pluginMap, TypeFactory typeFactory, PluginRegistry pluginRegistry) {
        this.pluginMap = pluginMap;
        this.typeFactory = typeFactory;
        this.pluginRegistry = pluginRegistry;
        defaultImpl(pluginMap.defaultSugar());
        typeProperty(pluginMap.classField());
        inclusion(JsonTypeInfo.As.PROPERTY);
    }

    @Override public CodecTypeDeserializer buildTypeDeserializer(DeserializationConfig config,
                                                                 JavaType baseType,
                                                                 Collection<NamedType> subtypes) {
        CodecTypeIdResolver codecTypeIdResolver = idResolver(config, baseType, subtypes, false, true);
        Class<?> defaultImplForType = _defaultImpl;
        if ((_defaultImpl == null) || !baseType.getRawClass().isAssignableFrom(_defaultImpl)) {
            defaultImplForType = baseType.getRawClass();
        }
        return new CodecTypeDeserializer(pluginMap, _includeAs, baseType, codecTypeIdResolver,
                                         _typeProperty, _typeIdVisible, typeFactory.constructType(defaultImplForType));
    }

    @Override
    protected CodecTypeIdResolver idResolver(MapperConfig<?> config,
                                             JavaType baseType,
                                             Collection<NamedType> subtypes,
                                             boolean forSer,
                                             boolean forDeser) {
        return new CodecTypeIdResolver(pluginMap, baseType, typeFactory, subtypes, pluginRegistry);
    }
}
