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

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.Deserializers;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.type.ArrayType;

public class CodecDeserializers extends Deserializers.Base {

    @Override
    public JsonDeserializer<?> findArrayDeserializer(ArrayType type,
                                                     DeserializationConfig config,
                                                     BeanDescription beanDesc,
                                                     TypeDeserializer elementTypeDeserializer,
                                                     JsonDeserializer<?> elementDeserializer)
            throws JsonMappingException {
        // skip primitive and string cases that wouldn't become ObjectArrayDeserializers
        if (elementDeserializer == null) {
            Class<?> raw = type.getContentType().getRawClass();
            if (type.getContentType().isPrimitive()) {
                return null;
            } else if (raw == String.class) {
                return null;
            }
        }
        return new IndexReportingObjectArrayDeserializer(type,
                                                         (JsonDeserializer<Object>) elementDeserializer,
                                                         elementTypeDeserializer);
    }
}
