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

import java.util.Iterator;
import java.util.regex.Pattern;

import com.addthis.codec.annotations.Bytes;
import com.addthis.codec.annotations.Time;
import com.addthis.codec.codables.SuperCodable;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.NameTransformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.util.Duration;
import io.dropwizard.util.Size;

public class CodecBeanDeserializer extends DelegatingDeserializer {
    private static final Logger log = LoggerFactory.getLogger(CodecBeanDeserializer.class);
    private static final Pattern NUMBER_UNIT = Pattern.compile("(\\d+)\\s*([^\\s\\d]+)");

    private final ObjectNode fieldDefaults;

    protected CodecBeanDeserializer(BeanDeserializerBase src, ObjectNode fieldDefaults) {
        super(src);
        this.fieldDefaults = fieldDefaults;
    }

    @Override public BeanDeserializerBase getDelegatee() {
        return (BeanDeserializerBase) _delegatee;
    }

    @Override protected JsonDeserializer<?> newDelegatingInstance(JsonDeserializer<?> newDelegatee) {
        return new CodecBeanDeserializer((BeanDeserializerBase) newDelegatee, fieldDefaults);
    }

    @Override
    public Object deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonLocation currentLocation = jp.getTokenLocation();
        JsonToken t = jp.getCurrentToken();
        try {
            if (t == JsonToken.START_OBJECT) {
                ObjectNode objectNode = jp.readValueAsTree();
                handleDefaultsAndRequiredAndNull(ctxt, objectNode);
                jp = jp.getCodec().treeAsTokens(objectNode);
                jp.nextToken();
            } else if (t == JsonToken.END_OBJECT) {
                // for some reason this is how they chose to handle single field objects
                jp.nextToken();
                ObjectNode objectNode = ctxt.getNodeFactory().objectNode();
                handleDefaultsAndRequiredAndNull(ctxt, objectNode);
                jp = jp.getCodec().treeAsTokens(objectNode);
                jp.nextToken();
            }
            Object value = getDelegatee().deserialize(jp, ctxt);
            if (value instanceof SuperCodable) {
                ((SuperCodable) value).postDecode();
            }
            return value;
        } catch (JsonMappingException ex) {
            throw Jackson.maybeImproveLocation(currentLocation, ex);
        }
    }

    private void handleDefaultsAndRequiredAndNull(DeserializationContext ctxt, ObjectNode fieldValues)
            throws JsonMappingException {
        Iterator<SettableBeanProperty> propertyIterator = getDelegatee().properties();
        while (propertyIterator.hasNext()) {
            SettableBeanProperty prop = propertyIterator.next();
            String propertyName = prop.getName();
            JsonNode fieldValue = fieldValues.path(propertyName);
            if (fieldValue.isMissingNode() || fieldValue.isNull()) {
                if (fieldDefaults.hasNonNull(propertyName)) {
                    fieldValue = fieldDefaults.get(propertyName).deepCopy();
                    fieldValues.set(propertyName, fieldValue);
                } else if (prop.isRequired()) {
                    throw MissingPropertyException.from(ctxt.getParser(), prop.getType().getRawClass(),
                                                        propertyName, getKnownPropertyNames());
                } else if (fieldValue.isNull()
                           && (prop.getType().isPrimitive() || (prop.getValueDeserializer().getNullValue() == null))) {
                    // don't overwrite possible hard-coded defaults/ values with nulls unless they are fancy
                    fieldValues.remove(propertyName);
                }
            }
            if (fieldValue.isTextual()) {
                try {
                    // sometimes we erroneously get strings that would parse into valid numbers and maybe other edge
                    // cases (eg. when using system property overrides in typesafe-config). So we'll go ahead and guard
                    // with this regex to make sure we only get reasonable candidates.
                    Time time = prop.getAnnotation(Time.class);
                    if ((time != null) && NUMBER_UNIT.matcher(fieldValue.textValue()).matches()) {
                        Duration dropWizardDuration = Duration.parse(fieldValue.asText());
                        long asLong = time.value().convert(dropWizardDuration.getQuantity(), dropWizardDuration.getUnit());
                        fieldValues.put(propertyName, asLong);
                    } else if ((prop.getAnnotation(Bytes.class) != null) &&
                               NUMBER_UNIT.matcher(fieldValue.textValue()).matches()) {
                        Size dropWizardSize = Size.parse(fieldValue.asText());
                        long asLong = dropWizardSize.toBytes();
                        fieldValues.put(propertyName, asLong);
                    }
                } catch (Throwable cause) {
                    throw JsonMappingException.wrapWithPath(cause, prop.getType().getRawClass(), propertyName);
                }
            }
        }
    }

    // required overrides that don't actually change much

    @Override
    public JsonDeserializer<Object> unwrappingDeserializer(NameTransformer unwrapper) {
        return (JsonDeserializer<Object>) replaceDelegatee(getDelegatee().unwrappingDeserializer(unwrapper));
    }
}
