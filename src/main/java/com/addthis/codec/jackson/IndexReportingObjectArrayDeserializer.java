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

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import com.fasterxml.jackson.databind.deser.std.ObjectArrayDeserializer;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.type.ArrayType;
import com.fasterxml.jackson.databind.util.ObjectBuffer;

/**
 * Provides missing array index information from error path reporting. Based on the version from master, but
 * with some additional improvements.
 */
public class IndexReportingObjectArrayDeserializer extends ObjectArrayDeserializer implements ContextualDeserializer {

    public IndexReportingObjectArrayDeserializer(ArrayType arrayType,
                                                 JsonDeserializer<Object> elemDeser,
                                                 TypeDeserializer elemTypeDeser) {
        super(arrayType, elemDeser, elemTypeDeser);
    }

    /** Overridable fluent-factory method used to create contextual instances */
    @SuppressWarnings("unchecked")
    public IndexReportingObjectArrayDeserializer withDeserializer(TypeDeserializer elemTypeDeser,
                                                                  JsonDeserializer<?> elemDeser) {
        if ((elemDeser == _elementDeserializer) && (elemTypeDeser == _elementTypeDeserializer)) {
            return this;
        }
        return new IndexReportingObjectArrayDeserializer(
                _arrayType, (JsonDeserializer<Object>) elemDeser, elemTypeDeser);
    }

    @Override
    public Object[] deserialize(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {

        if (!jp.isExpectedStartArrayToken()) {
            // punt to super method for edge case handling, we won't need to report any indexes anyway
            return super.deserialize(jp, ctxt);
        }

        final ObjectBuffer buffer = ctxt.leaseObjectBuffer();
        Object[] chunk = buffer.resetAndStart();
        int ix = 0;
        JsonToken t;
        final TypeDeserializer typeDeser = _elementTypeDeserializer;

        try {
            while ((t = jp.nextToken()) != JsonToken.END_ARRAY) {
                if (ix >= chunk.length) {
                    chunk = buffer.appendCompletedChunk(chunk);
                    ix = 0;
                }

                // Note: must handle null explicitly here; value deserializers won't
                Object value;
                if (t == JsonToken.VALUE_NULL) {
                    value = _elementDeserializer.getNullValue();
                } else if (typeDeser == null) {
                    value = _elementDeserializer.deserialize(jp, ctxt);
                } else {
                    value = _elementDeserializer.deserializeWithType(jp, ctxt, typeDeser);
                }
                chunk[ix] = value;
                ix++;
            }
        } catch (Exception e) {
            // note: pass Object.class, not Object[].class, as we need element type for error info
            // addition: add buffer.bufferedSize() since ix gets periodically reset
            // addition: report _elementClass instead of hard-coded Object.class
            throw JsonMappingException.wrapWithPath(e, _elementClass, ix + buffer.bufferedSize());
        }

        Object[] result;

        if (_untyped) {
            result = buffer.completeAndClearBuffer(chunk, ix);
        } else {
            result = buffer.completeAndClearBuffer(chunk, ix, _elementClass);
        }
        ctxt.returnObjectBuffer(buffer);
        return result;
    }
}

