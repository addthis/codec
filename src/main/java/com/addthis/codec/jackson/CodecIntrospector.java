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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.addthis.codec.annotations.FieldConfig;
import com.addthis.codec.annotations.Pluggable;
import com.addthis.codec.plugins.PluginMap;
import com.addthis.codec.plugins.PluginRegistry;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.PropertyName;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.NopAnnotationIntrospector;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.jsontype.TypeResolverBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CodecIntrospector extends NopAnnotationIntrospector {
    private static final Logger log = LoggerFactory.getLogger(CodecIntrospector.class);

    private final PluginRegistry pluginRegistry;

    public CodecIntrospector(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
    }

    @Override
    public TypeResolverBuilder<?> findTypeResolver(MapperConfig<?> config, AnnotatedClass ac, JavaType baseType) {
        Pluggable pluggable = ac.getAnnotation(Pluggable.class);
        if (pluggable != null) {
            PluginMap pluginMap = pluginRegistry.byCategory().get(pluggable.value());
            if (pluginMap != null) {
                return new CodecTypeResolverBuilder(pluginMap, config.getTypeFactory(), pluginRegistry);
            } else {
                log.warn("missing plugin map for {}, reached from {}", pluggable.value(), ac.getRawType());
            }
        } else if (pluginRegistry.byClass().containsKey(ac.getRawType())) {
            PluginMap pluginMap = pluginRegistry.byClass().get(ac.getRawType());
            return new CodecTypeResolverBuilder(pluginMap, config.getTypeFactory(), pluginRegistry);
        }
        return null;
    }

    /** report all non-alias plugin types */
    @Override
    public List<NamedType> findSubtypes(Annotated a) {
        Pluggable pluggable = a.getAnnotation(Pluggable.class);
        PluginMap pluginMap;
        if (pluggable != null) {
            pluginMap = pluginRegistry.byCategory().get(pluggable.value());
        } else if (pluginRegistry.byClass().containsKey(a.getRawType())) {
            pluginMap = pluginRegistry.byClass().get(a.getRawType());
        } else {
            return null;
        }
        List<NamedType> result = new ArrayList<>(pluginMap.asBiMap().size());
        for (Map.Entry<String, Class<?>> type : pluginMap.asBiMap().entrySet()) {
            result.add(new NamedType(type.getValue(), type.getKey()));
        }
        return result;
    }

    @Override
    public boolean hasIgnoreMarker(AnnotatedMember m) {
        FieldConfig fieldConfig = m.getAnnotation(FieldConfig.class);
        if (fieldConfig != null) {
            return !fieldConfig.codable();
        }
        return false;
    }

    @Override
    public Boolean hasRequiredMarker(AnnotatedMember m) {
        FieldConfig fieldConfig = m.getAnnotation(FieldConfig.class);
        if (fieldConfig != null) {
            return fieldConfig.required();
        }
        return null;
    }

    // read and write only settings only work in the absence of another, explicit, JsonProperty.
    // also, note that these methods are what cause FieldConfig to trigger inclusion, so if/when
    // we just drop readonly/writeonly, we can (should) add meta annotations to FieldConfig et al.

    @Override
    public PropertyName findNameForDeserialization(Annotated a) {
        FieldConfig fieldConfig = a.getAnnotation(FieldConfig.class);
        if ((fieldConfig != null) && !fieldConfig.writeonly() && fieldConfig.codable()) {
            return PropertyName.USE_DEFAULT;
        }
        return null;
    }

    @Override
    public PropertyName findNameForSerialization(Annotated a) {
        FieldConfig fieldConfig = a.getAnnotation(FieldConfig.class);
        if ((fieldConfig != null) && !fieldConfig.readonly() && fieldConfig.codable()) {
            return PropertyName.USE_DEFAULT;
        }
        return null;
    }
}
