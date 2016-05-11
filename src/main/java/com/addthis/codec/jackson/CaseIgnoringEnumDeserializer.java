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
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.EnumDeserializer;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.util.EnumResolver;

// ignores case when deserializing enums
public class CaseIgnoringEnumDeserializer extends EnumDeserializer {

    public CaseIgnoringEnumDeserializer(EnumResolver enumResolver) {
        super(enumResolver);
    }

    @Override
    public Object deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
        JsonToken curr = jp.getCurrentToken();

        if ((curr == JsonToken.VALUE_STRING) || (curr == JsonToken.FIELD_NAME)
            || (curr == JsonToken.VALUE_FALSE) || (curr == JsonToken.VALUE_TRUE)) {
            String name = jp.getText();
            if (_getToStringLookup().find(name) != null) {
                return super.deserialize(jp, ctxt);
            }
            TextNode upperName = ctxt.getNodeFactory().textNode(name.toUpperCase());

            JsonParser treeParser = jp.getCodec().treeAsTokens(upperName);
            treeParser.nextToken();
            return super.deserialize(treeParser, ctxt);
        } else {
            return super.deserialize(jp, ctxt);
        }
    }
}
