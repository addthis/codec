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

import java.io.IOException;

import com.addthis.codec.plugins.PluginMap;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.jsontype.impl.TypeDeserializerBase;

public class CodecTypeDeserializer extends TypeDeserializerBase {
    private final PluginMap pluginMap;
    private final JsonTypeInfo.As inludeAs;
    private final CodecTypeIdResolver idRes;

    protected CodecTypeDeserializer(PluginMap pluginMap, JsonTypeInfo.As inludeAs,
                                    JavaType baseType, CodecTypeIdResolver idRes,
                                    String typePropertyName, boolean typeIdVisible,
                                    Class<?> defaultImpl) {
        super(baseType, idRes, typePropertyName, typeIdVisible, defaultImpl);
        this.pluginMap = pluginMap;
        this.inludeAs = inludeAs;
        this.idRes = idRes;
    }

    protected CodecTypeDeserializer(CodecTypeDeserializer src, BeanProperty property) {
        super(src, property);
        this.pluginMap = src.pluginMap;
        this.inludeAs = src.inludeAs;
        this.idRes = src.idRes;
    }

    @Override public CodecTypeDeserializer forProperty(BeanProperty prop) {
        return (prop == _property) ? this : new CodecTypeDeserializer(this, prop);
    }

    @Override
    public JsonTypeInfo.As getTypeInclusion() {
        return inludeAs;
    }

    @Override
    public Object deserializeTypedFromObject(JsonParser jp, DeserializationContext ctxt) throws IOException {
        return null;
    }

    @Override
    public Object deserializeTypedFromArray(JsonParser jp, DeserializationContext ctxt) throws IOException {
        return null;
    }

    @Override
    public Object deserializeTypedFromScalar(JsonParser jp, DeserializationContext ctxt) throws IOException {
        return null;
    }

    @Override
    public Object deserializeTypedFromAny(JsonParser jp, DeserializationContext ctxt) throws IOException {
        return null;
    }
}
