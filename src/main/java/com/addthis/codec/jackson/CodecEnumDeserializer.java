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

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.deser.std.EnumDeserializer;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.node.TextNode;

// ignores case when deserializing enums
public class CodecEnumDeserializer extends StdScalarDeserializer<Enum<?>> {
    private final EnumDeserializer base;

    public CodecEnumDeserializer(EnumDeserializer base) {
        super(Enum.class);
        this.base = base;
    }

    @Override
    public Enum<?> deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonToken curr = jp.getCurrentToken();

        if ((curr == JsonToken.VALUE_STRING) || (curr == JsonToken.FIELD_NAME)
            || (curr == JsonToken.VALUE_FALSE) || (curr == JsonToken.VALUE_TRUE)) {
            String name = jp.getText();
            TextNode upperName = ctxt.getNodeFactory().textNode(name.toUpperCase());

            JsonLocation currentLocation = jp.getTokenLocation();
            JsonParser treeParser = jp.getCodec().treeAsTokens(upperName);
            treeParser.nextToken();
            try {
                return base.deserialize(treeParser, ctxt);
            } catch (JsonProcessingException ex) {
                if (ex.getLocation() == JsonLocation.NA) {
                    throw new JsonMappingException(ex.getOriginalMessage(), currentLocation, ex);
                } else {
                    throw ex;
                }
            }
        } else {
            return base.deserialize(jp, ctxt);
        }
    }
}
