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

import javax.annotation.Nullable;

import java.io.IOException;

import java.util.Iterator;

import com.addthis.codec.plugins.PluginMap;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.BeanDeserializerBase;
import com.fasterxml.jackson.databind.deser.SettableBeanProperty;
import com.fasterxml.jackson.databind.jsontype.TypeDeserializer;
import com.fasterxml.jackson.databind.jsontype.impl.TypeDeserializerBase;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;

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

    // based on methods/ comments from other TypeDeserializers, these methods cannot be trusted
    // to actually reflect the current json type. so reroute all to the switch and check ourselves.

    @Override
    public Object deserializeTypedFromObject(JsonParser jp, DeserializationContext ctxt) throws IOException {
        return deserializeTypedFromAny(jp, ctxt);
    }

    @Override
    public Object deserializeTypedFromArray(JsonParser jp, DeserializationContext ctxt) throws IOException {
        return deserializeTypedFromAny(jp, ctxt);
    }

    @Override
    public Object deserializeTypedFromScalar(JsonParser jp, DeserializationContext ctxt) throws IOException {
        return deserializeTypedFromAny(jp, ctxt);
    }

    @Override
    public Object deserializeTypedFromAny(JsonParser jp, DeserializationContext ctxt) throws IOException {
        // a jackson thing we might as well include
        if (jp.canReadTypeId()) {
            Object typeId = jp.getTypeId();
            if (typeId != null) {
                return _deserializeWithNativeTypeId(jp, ctxt, typeId);
            }
        }
        String classField = pluginMap.classField();
        Object bean = null;
        JsonToken currentToken = jp.getCurrentToken();
        JsonNode jsonNode = jp.readValueAsTree();

        // _array handler
        if (jsonNode.isArray()) {
            bean = _deserializeTypedFromArray((ArrayNode) jsonNode, classField, jp, ctxt);
        // object handler
        } else if (jsonNode.isObject()) {
            bean = _deserializeTypedFromObject((ObjectNode) jsonNode, classField, jp, ctxt);
        }
        if (bean != null) {
            return bean;
        } else {
            JsonDeserializer<Object> deser = _findDeserializer(ctxt, null);
            JsonParser treeParser = jp.getCodec().treeAsTokens(jsonNode);
            treeParser.nextToken();
            return deser.deserialize(treeParser, ctxt);
        }
    }

    @Nullable public Object _deserializeTypedFromObject(ObjectNode objectNode, String classField,
                                                        JsonParser jp, DeserializationContext ctxt) throws IOException {
        if (objectNode.hasNonNull(classField)) {
            return _deserializeObjectFromProperty(objectNode, classField, jp, ctxt);
        }
        if (objectNode.size() == 1) {
            Object bean = _deserializeObjectFromSingleKey(objectNode, classField, jp, ctxt);
            if (bean != null) {
                return bean;
            }
        }
        Object bean = _deserializeObjectFromInlinedType(objectNode, classField, jp, ctxt);
        if (bean != null) {
            return bean;
        }
        if (idRes.isValidTypeId("_default")) {
            ConfigObject aliasDefaults = pluginMap.aliasDefaults("_default");
            if (!aliasDefaults.isEmpty()) {
                Jackson.merge(objectNode, Jackson.configConverter(aliasDefaults));
            }
            JsonDeserializer<Object> deser = _findDeserializer(ctxt, "_default");
            JsonParser treeParser = jp.getCodec().treeAsTokens(objectNode);
            treeParser.nextToken();
            bean = deser.deserialize(treeParser, ctxt);
        }
        return bean;
    }

    @Nullable private Object _deserializeObjectFromInlinedType(ObjectNode objectNode, String classField, JsonParser jp,
                                                               DeserializationContext ctxt) throws IOException {
        String matched = null;
        for (String alias : pluginMap.inlinedAliases()) {
            if (objectNode.get(alias) != null) {
                if (matched != null) {
                    String message = String.format(
                            "no type specified, more than one key, and both %s and %s match for inlined types.",
                            matched, alias);
                    throw ctxt.instantiationException(_baseType.getRawClass(), message);
                }
                matched = alias;
            }
        }
        if (matched != null) {
            ConfigObject aliasDefaults = pluginMap.aliasDefaults(matched);
            JsonNode configValue = objectNode.get(matched);
            String primaryField = (String) aliasDefaults.get("_primary").unwrapped();
            objectNode.remove(matched);
            Jackson.setAt(objectNode, configValue, primaryField);
            Jackson.merge(objectNode, Jackson.configConverter(aliasDefaults));
            if (_typeIdVisible) {
                objectNode.put(classField, matched);
            }
            JsonDeserializer<Object> deser = _findDeserializer(ctxt, matched);
            JsonParser treeParser = jp.getCodec().treeAsTokens(objectNode);
            treeParser.nextToken();
            return deser.deserialize(treeParser, ctxt);
        } else {
            return null;
        }
    }

    @Nullable private Object _deserializeObjectFromSingleKey(ObjectNode objectNode, String classField, JsonParser jp,
                                                             DeserializationContext ctxt) throws IOException {
        String singleKeyName = objectNode.fieldNames().next();
        if (idRes.isValidTypeId(singleKeyName)) {
            ConfigObject aliasDefaults = pluginMap.aliasDefaults(singleKeyName);
            JsonNode singleKeyValue = objectNode.get(singleKeyName);
            JsonDeserializer<Object> deser = _findDeserializer(ctxt, singleKeyName);
            if (!singleKeyValue.isObject()) {
                // if value is not an object, try supporting _primary syntax to derive one
                if (aliasDefaults.get("_primary") != null) {
                    String primaryField = (String) aliasDefaults.get("_primary").unwrapped();
                    ObjectNode singleKeyObject = (ObjectNode) jp.getCodec().createObjectNode();
                    Jackson.setAt(singleKeyObject, singleKeyValue, primaryField);
                    Jackson.merge(singleKeyObject, Jackson.configConverter(aliasDefaults));
                    singleKeyValue = singleKeyObject;
                } // else let the downstream serializer try to handle it or complain
            } else {
                ObjectNode singleKeyObject = (ObjectNode) singleKeyValue;
                handleDefaultsAndImplicitPrimary(singleKeyObject, aliasDefaults, deser, ctxt);
            }
            if (_typeIdVisible && singleKeyValue.isObject()) {
                ((ObjectNode) singleKeyValue).put(classField, singleKeyName);
            }
            JsonParser treeParser = jp.getCodec().treeAsTokens(singleKeyValue);
            treeParser.nextToken();
            return deser.deserialize(treeParser, ctxt);
        }
        return null;
    }

    private Object _deserializeObjectFromProperty(ObjectNode objectNode, String classField,
                                                  JsonParser jp, DeserializationContext ctxt) throws IOException {
        String type = objectNode.get(classField).asText();
        if (!_typeIdVisible) {
            objectNode.remove(classField);
        }
        ConfigObject aliasDefaults = pluginMap.aliasDefaults(type);
        JsonDeserializer<Object> deser = _findDeserializer(ctxt, type);
        handleDefaultsAndImplicitPrimary(objectNode, aliasDefaults, deser, ctxt);
        JsonParser treeParser = jp.getCodec().treeAsTokens(objectNode);
        treeParser.nextToken();
        return deser.deserialize(treeParser, ctxt);
    }

    @Nullable private Object _deserializeTypedFromArray(ArrayNode arrayNode, String classField, JsonParser jp,
                                                        DeserializationContext ctxt) throws IOException {
        if (idRes.isValidTypeId("_array")) {
            Config aliasDefaults = pluginMap.aliasDefaults("_array").toConfig();
            String arrayField = aliasDefaults.getString("_primary");
            ObjectNode objectFieldValues = (ObjectNode) jp.getCodec().createObjectNode();
            Jackson.setAt(objectFieldValues, arrayNode, arrayField);
            ObjectNode aliasFieldDefaults = Jackson.configConverter(aliasDefaults.root());
            Jackson.merge(objectFieldValues, aliasFieldDefaults);
            JsonDeserializer<Object> deser = _findDeserializer(ctxt, "_array");
            JsonParser treeParser = jp.getCodec().treeAsTokens(objectFieldValues);
            treeParser.nextToken();
            return deser.deserialize(treeParser, ctxt);
        } else {
            return null;
        }
    }

    private void handleDefaultsAndImplicitPrimary(ObjectNode fieldValues, ConfigObject aliasDefaults,
                                                  JsonDeserializer<?> deserializer, DeserializationContext ctxt)
            throws JsonMappingException {
        if (!aliasDefaults.isEmpty()) {
            if ((deserializer instanceof BeanDeserializerBase) && (aliasDefaults.get("_primary") != null)) {
                BeanDeserializerBase beanDeserializer = (BeanDeserializerBase) deserializer;
                String primaryField = (String) aliasDefaults.get("_primary").unwrapped();
                if (!fieldValues.has(primaryField)) {
                    // user has not explicitly set a value where _primary points, see if _primary is a plugin type
                    SettableBeanProperty primaryProperty = beanDeserializer.findProperty(primaryField);
                    if ((primaryProperty != null) && primaryProperty.hasValueTypeDeserializer()) {
                        TypeDeserializer primaryTypeDeserializer = primaryProperty.getValueTypeDeserializer();
                        if (primaryTypeDeserializer instanceof CodecTypeDeserializer) {
                            CodecTypeIdResolver primaryPropertyTypeIdResolver =
                                    ((CodecTypeDeserializer) primaryTypeDeserializer).idRes;
                            String possibleInlinedPrimary = null;
                            Iterator<String> fieldNames = fieldValues.fieldNames();
                            while (fieldNames.hasNext()) {
                                String fieldName = fieldNames.next();
                                if ((fieldName.charAt(0) != '_') && !beanDeserializer.hasProperty(fieldName)) {
                                    if (primaryPropertyTypeIdResolver.isValidTypeId(fieldName)) {
                                        if (possibleInlinedPrimary == null) {
                                            possibleInlinedPrimary = fieldName;
                                        } else {
                                            String message = String.format(
                                                    "%s and %s are both otherwise unknown properties that "
                                                    + "could be types for the _primary property %s whose category is "
                                                    + "%s. This is too ambiguous to resolve.",
                                                    possibleInlinedPrimary, fieldName, primaryField,
                                                    ((CodecTypeDeserializer) primaryTypeDeserializer)
                                                            .pluginMap.category());
                                            throw ctxt.instantiationException(_baseType.getRawClass(), message);
                                        }
                                    }
                                }
                            }
                            // did we find a good candidate?
                            if (possibleInlinedPrimary != null) {
                                // then wrap the value with its key (its type), and stash it in our primary field
                                JsonNode inlinedPrimaryValue = fieldValues.remove(possibleInlinedPrimary);
                                fieldValues.with(primaryField).set(possibleInlinedPrimary, inlinedPrimaryValue);
                            }
                        }
                    }
                }
            }
            // merge alias defaults here since we check for empty etc anyway
            Jackson.merge(fieldValues, Jackson.configConverter(aliasDefaults));
        }
    }
}
