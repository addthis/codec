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

import java.util.Iterator;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.deser.std.DelegatingDeserializer;
import com.fasterxml.jackson.databind.deser.std.EnumDeserializer;
import com.fasterxml.jackson.databind.deser.std.MapDeserializer;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.MapType;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigValue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CodecBeanDeserializerModifier extends BeanDeserializerModifier {
    private static final Logger log = LoggerFactory.getLogger(CodecBeanDeserializerModifier.class);

    private final Config globalDefaults;

    public CodecBeanDeserializerModifier(Config globalDefaults) {
        this.globalDefaults = globalDefaults;
    }

    @Override public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
                                                            BeanDescription beanDesc,
                                                            JsonDeserializer<?> deserializer) {
        if (deserializer instanceof DelegatingDeserializer) {
            JsonDeserializer<?> delegatee = ((DelegatingDeserializer) deserializer).getDelegatee();
            JsonDeserializer<?> replacementDelegatee = modifyDeserializer(config, beanDesc, delegatee);
            return deserializer.replaceDelegatee(replacementDelegatee);
        } else if (deserializer instanceof BeanDeserializerBase) {
            BeanDeserializerBase beanDeserializer = (BeanDeserializerBase)  deserializer;
            ObjectNode fieldDefaults = config.getNodeFactory().objectNode();
            Iterator<SettableBeanProperty> propertyIterator = beanDeserializer.properties();
            while (propertyIterator.hasNext()) {
                SettableBeanProperty prop = propertyIterator.next();
                Class<?> declaringClass = prop.getMember().getDeclaringClass();
                String canonicalClassName = declaringClass.getCanonicalName();
                if ((canonicalClassName != null) && globalDefaults.hasPath(canonicalClassName)) {
                    Config declarerDefaults = globalDefaults.getConfig(canonicalClassName);
                    String propertyName = prop.getName();
                    if (declarerDefaults.hasPath(propertyName)) {
                        ConfigValue defaultValue = declarerDefaults.getValue(propertyName);
                        JsonNode fieldDefault = Jackson.configConverter(defaultValue);
                        fieldDefaults.set(propertyName, fieldDefault);
                    }
                }
            }
            return new CodecBeanDeserializer(beanDeserializer, fieldDefaults);
        } else {
            return deserializer;
        }
    }

    @Override public JsonDeserializer<?> modifyMapDeserializer(DeserializationConfig config,
                                                               MapType type,
                                                               BeanDescription beanDesc,
                                                               JsonDeserializer<?> deserializer) {
        if (deserializer instanceof DelegatingDeserializer) {
            JsonDeserializer<?> delegatee = ((DelegatingDeserializer) deserializer).getDelegatee();
            JsonDeserializer<?> replacementDelegatee = modifyMapDeserializer(config, type, beanDesc, delegatee);
            return deserializer.replaceDelegatee(replacementDelegatee);
        } else if (deserializer.getClass() == MapDeserializer.class) {
            return new KeyReportingMapDeserializer((MapDeserializer) deserializer);
        } else {
            return deserializer;
        }
    }

    @Override public JsonDeserializer<?> modifyEnumDeserializer(DeserializationConfig config,
                                                                JavaType type,
                                                                BeanDescription beanDesc,
                                                                JsonDeserializer<?> deserializer) {
        if (deserializer instanceof DelegatingDeserializer) {
            JsonDeserializer<?> delegatee = ((DelegatingDeserializer) deserializer).getDelegatee();
            JsonDeserializer<?> replacementDelegatee = modifyEnumDeserializer(config, type, beanDesc, delegatee);
            return deserializer.replaceDelegatee(replacementDelegatee);
        } else if (deserializer instanceof EnumDeserializer) {
            return new CodecEnumDeserializer((EnumDeserializer) deserializer);
        } else {
            return deserializer;
        }
    }
}
