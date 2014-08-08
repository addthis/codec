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
import java.util.Iterator;

import com.addthis.codec.annotations.Bytes;
import com.addthis.codec.annotations.Time;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.BeanDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.impl.ObjectIdReader;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.util.NameTransformer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.dropwizard.util.Duration;
import io.dropwizard.util.Size;

public class CodecBeanDeserializer extends BeanDeserializer {
    private static final Logger log = LoggerFactory.getLogger(CodecBeanDeserializer.class);

    private final ObjectNode fieldDefaults;

    protected CodecBeanDeserializer(BeanDeserializerBase src, ObjectNode fieldDefaults) {
        super(src);
        this.fieldDefaults = fieldDefaults;
    }

    protected CodecBeanDeserializer(CodecBeanDeserializer src, NameTransformer unwrapper) {
        super(src, unwrapper);
        this.fieldDefaults = src.fieldDefaults;
    }

    public CodecBeanDeserializer(CodecBeanDeserializer src, ObjectIdReader oir) {
        super(src, oir);
        this.fieldDefaults = src.fieldDefaults;
    }

    public CodecBeanDeserializer(CodecBeanDeserializer src, HashSet<String> ignorableProps) {
        super(src, ignorableProps);
        this.fieldDefaults = src.fieldDefaults;
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
            return super.deserialize(jp, ctxt);
        } catch (JsonProcessingException ex) {
            if ((currentLocation != JsonLocation.NA) && (ex.getLocation() == JsonLocation.NA)) {
                throw new JsonMappingException(ex.getOriginalMessage(), currentLocation, ex);
            } else {
                throw ex;
            }
        }
    }

    private void handleDefaultsAndRequiredAndNull(DeserializationContext ctxt, ObjectNode fieldValues)
            throws JsonMappingException {
        Iterator<SettableBeanProperty> propertyIterator = properties();
        while (propertyIterator.hasNext()) {
            SettableBeanProperty prop = propertyIterator.next();
            String propertyName = prop.getName();
            JsonNode fieldValue = fieldValues.path(propertyName);
            if (fieldValue.isMissingNode() || fieldValue.isNull()) {
                if (fieldDefaults.hasNonNull(propertyName)) {
                    fieldValue = fieldDefaults.get(propertyName).deepCopy();
                    fieldValues.set(propertyName, fieldValue);
                } else if (prop.isRequired()) {
                    throw ctxt.instantiationException(handledType(), "missing required field: " + propertyName);
                } else if (fieldValue.isNull()
                           && (prop.getType().isPrimitive() || (prop.getValueDeserializer().getNullValue() == null))) {
                    // don't overwrite possible hard-coded defaults/ values with nulls unless they are fancy
                    fieldValues.remove(propertyName);
                }
            }
            if (fieldValue.isTextual()) {
                Time time = prop.getAnnotation(Time.class);
                if (time != null) {
                    Duration dropWizardDuration = Duration.parse(fieldValue.asText());
                    long asLong = time.value().convert(dropWizardDuration.getQuantity(), dropWizardDuration.getUnit());
                    fieldValues.put(propertyName, asLong);
                } else if (prop.getAnnotation(Bytes.class) != null) {
                    Size dropWizardSize = Size.parse(fieldValue.asText());
                    long asLong = dropWizardSize.toBytes();
                    fieldValues.put(propertyName, asLong);
                }
            }
        }
    }

    // required overrides that don't actually change much

    @Override
    public JsonDeserializer<Object> unwrappingDeserializer(NameTransformer unwrapper)
    {
        /* bit kludgy but we don't want to accidentally change type; sub-classes
         * MUST override this method to support unwrapped properties...
         */
        if (getClass() != CodecBeanDeserializer.class) {
            return this;
        }
        /* main thing really is to just enforce ignoring of unknown
         * properties; since there may be multiple unwrapped values
         * and properties for all may be interleaved...
         */
        return new CodecBeanDeserializer(this, unwrapper);
    }

    @Override
    public BeanDeserializer withObjectIdReader(ObjectIdReader oir) {
        return new CodecBeanDeserializer(this, oir);
    }

    @Override
    public BeanDeserializer withIgnorableProperties(HashSet<String> ignorableProps) {
        return new CodecBeanDeserializer(this, ignorableProps);
    }
}
