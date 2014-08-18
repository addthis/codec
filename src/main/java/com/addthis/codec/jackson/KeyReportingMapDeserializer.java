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

import java.util.HashSet;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.databind.deser.std.MapDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;

/** Wraps mapping exceptions with the key associated with the problem (if any). Useful for line numbers. */
public class KeyReportingMapDeserializer extends MapDeserializer  {
    public KeyReportingMapDeserializer(JavaType mapType, ValueInstantiator valueInstantiator,
                                       KeyDeserializer keyDeser, JsonDeserializer<Object> valueDeser,
                                       TypeDeserializer valueTypeDeser) {
        super(mapType, valueInstantiator, keyDeser, valueDeser, valueTypeDeser);
    }

    /**
     * Copy-constructor that can be used by sub-classes to allow
     * copy-on-write styling copying of settings of an existing instance.
     */
    protected KeyReportingMapDeserializer(MapDeserializer src) {
        super(src);
    }

    protected KeyReportingMapDeserializer(MapDeserializer src,
                                          KeyDeserializer keyDeser, JsonDeserializer<Object> valueDeser,
                                          TypeDeserializer valueTypeDeser,
                                          HashSet<String> ignorable) {
        super(src, keyDeser, valueDeser, valueTypeDeser, ignorable);
    }

    /**
     * Fluent factory method used to create a copy with slightly
     * different settings. When sub-classing, MUST be overridden.
     */
    @SuppressWarnings("unchecked")
    protected KeyReportingMapDeserializer withResolved(KeyDeserializer keyDeser,
            TypeDeserializer valueTypeDeser, JsonDeserializer<?> valueDeser,
            HashSet<String> ignorable) {
        if ((_keyDeserializer == keyDeser) && (_valueDeserializer == valueDeser)
            && (_valueTypeDeserializer == valueTypeDeser) && (_ignorableProperties == ignorable)) {
            return this;
        }
        return new KeyReportingMapDeserializer(this, keyDeser, (JsonDeserializer<Object>) valueDeser,
                                                 valueTypeDeser, ignorable);
    }

    /*
    /**********************************************************
    /* JsonDeserializer API
    /**********************************************************
     */

    @Override
    @SuppressWarnings("unchecked")
    public Map<Object,Object> deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException, JsonProcessingException {
        boolean useObjectId = _valueDeserializer.getObjectIdReader() != null;
        if (useObjectId) {
            return super.deserialize(jp, ctxt);
        }
        if (_propertyBasedCreator != null) {
            return super.deserialize(jp, ctxt);
        }
        if (_delegateDeserializer != null) {
            return super.deserialize(jp, ctxt);
        }
        if (!_hasDefaultCreator) {
            return super.deserialize(jp, ctxt);
        }
        // Ok: must point to START_OBJECT, FIELD_NAME or END_OBJECT
        JsonToken t = jp.getCurrentToken();
        if (t != JsonToken.START_OBJECT && t != JsonToken.FIELD_NAME && t != JsonToken.END_OBJECT) {
            return super.deserialize(jp, ctxt);
        }
        final Map<Object,Object> result = (Map<Object,Object>) _valueInstantiator.createUsingDefault(ctxt);
        if (_standardStringKey) {
            _readAndBindStringMap2(jp, ctxt, result);
            return result;
        }
        _readAndBind2(jp, ctxt, result);
        return result;
    }

    @Override
    public Map<Object,Object> deserialize(JsonParser jp,
                                          DeserializationContext ctxt,
                                          Map<Object,Object> result)
            throws IOException, JsonProcessingException {
        boolean useObjectId = _valueDeserializer.getObjectIdReader() != null;
        if (useObjectId) {
            return super.deserialize(jp, ctxt, result);
        }
        // Ok: must point to START_OBJECT or FIELD_NAME
        JsonToken t = jp.getCurrentToken();
        if (t != JsonToken.START_OBJECT && t != JsonToken.FIELD_NAME) {
            return super.deserialize(jp, ctxt, result);
        }
        if (_standardStringKey) {
            _readAndBindStringMap2(jp, ctxt, result);
            return result;
        }
        _readAndBind2(jp, ctxt, result);
        return result;
    }

    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */

    protected final void _readAndBind2(JsonParser jp,
                                       DeserializationContext ctxt,
                                       Map<Object,Object> result) throws IOException, JsonProcessingException {
        JsonToken t = jp.getCurrentToken();
        if (t == JsonToken.START_OBJECT) {
            t = jp.nextToken();
        }
        final KeyDeserializer keyDes = _keyDeserializer;
        final JsonDeserializer<Object> valueDes = _valueDeserializer;
        final TypeDeserializer typeDeser = _valueTypeDeserializer;

        for (; t == JsonToken.FIELD_NAME; t = jp.nextToken()) {
            // Must point to field name
            String fieldName = jp.getCurrentName();
            Object key = keyDes.deserializeKey(fieldName, ctxt);
            // And then the value...
            t = jp.nextToken();
            if (_ignorableProperties != null && _ignorableProperties.contains(fieldName)) {
                jp.skipChildren();
                continue;
            }
            try{
                // Note: must handle null explicitly here; value deserializers won't
                Object value;
                if (t == JsonToken.VALUE_NULL) {
                    value = valueDes.getNullValue();
                } else if (typeDeser == null) {
                    value = valueDes.deserialize(jp, ctxt);
                } else {
                    value = valueDes.deserializeWithType(jp, ctxt, typeDeser);
                }
                result.put(key, value);
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, _mapType, fieldName);
            }
        }
    }

    /**
     * Optimized method used when keys can be deserialized as plain old
     * {@link String}s, and there is no custom deserialized specified.
     */
    protected final void _readAndBindStringMap2(JsonParser jp,
                                                DeserializationContext ctxt,
                                                Map<Object,Object> result)
            throws IOException, JsonProcessingException {
        JsonToken t = jp.getCurrentToken();
        if (t == JsonToken.START_OBJECT) {
            t = jp.nextToken();
        }
        final JsonDeserializer<Object> valueDes = _valueDeserializer;
        final TypeDeserializer typeDeser = _valueTypeDeserializer;
        for (; t == JsonToken.FIELD_NAME; t = jp.nextToken()) {
            // Must point to field name
            String fieldName = jp.getCurrentName();
            // And then the value...
            t = jp.nextToken();
            if (_ignorableProperties != null && _ignorableProperties.contains(fieldName)) {
                jp.skipChildren();
                continue;
            }
            try {
                // Note: must handle null explicitly here; value deserializers won't
                Object value;
                if (t == JsonToken.VALUE_NULL) {
                    value = valueDes.getNullValue();
                } else if (typeDeser == null) {
                    value = valueDes.deserialize(jp, ctxt);
                } else {
                    value = valueDes.deserializeWithType(jp, ctxt, typeDeser);
                }
                result.put(fieldName, value);
            } catch (Exception e) {
                throw JsonMappingException.wrapWithPath(e, _mapType, fieldName);
            }
        }
    }
}
