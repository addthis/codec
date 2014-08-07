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
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
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

        // _array handler
        if (jp.isExpectedStartArrayToken()) {
            return _deserializeTypedFromArray(classField, jp, ctxt);
        // object handler
        } else if (jp.getCurrentToken() == JsonToken.START_OBJECT) {
            return _deserializeTypedFromObject(classField, jp, ctxt);
        }
        throw ctxt.wrongTokenException(jp, jp.getCurrentToken(),
                                       "Need an object or an array (if _array is set) to resolve the subtype of your "
                                       + pluginMap.category());
    }

    public Object _deserializeTypedFromObject(String classField, JsonParser jp, DeserializationContext ctxt) throws IOException {
        ObjectNode objectNode = jp.readValueAsTree();
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
        ConfigObject aliasDefaults = pluginMap.aliasDefaults("_default");
        if (!aliasDefaults.isEmpty()) {
            Jackson.merge(objectNode, Jackson.configConverter(aliasDefaults));
        }
        JsonDeserializer<Object> deser = _findDeserializer(ctxt, null);
        JsonParser treeParser = jp.getCodec().treeAsTokens(objectNode);
        treeParser.nextToken();
        return deser.deserialize(treeParser, ctxt);
    }

    private Object _deserializeObjectFromInlinedType(ObjectNode objectNode, String classField,
                                                     JsonParser jp, DeserializationContext ctxt) throws IOException {
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
            Class<?> inlinedType = pluginMap.getClassIfConfigured(matched);
            assert inlinedType != null : "matched is always a key from the pluginMap's inlinedAliases set";
            ConfigObject aliasDefaults = pluginMap.aliasDefaults(matched);
            JsonNode configValue = objectNode.get(matched);
            String primaryField = (String) aliasDefaults.get("_primary").unwrapped();
            objectNode.remove(matched);
            objectNode.set(primaryField, configValue);
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

    private Object _deserializeObjectFromSingleKey(ObjectNode objectNode, String classField,
                                                   JsonParser jp, DeserializationContext ctxt) throws IOException {
        String singleKeyName = objectNode.fieldNames().next();
        try {
            Class<?> singleKeyType = pluginMap.getClass(singleKeyName);
            ConfigObject aliasDefaults = pluginMap.aliasDefaults(singleKeyName);
            JsonNode singleKeyValue = objectNode.get(singleKeyName);
            if (!singleKeyValue.isObject()) {
                // if value is not an object, try supporting _primary syntax to derive one
                if (aliasDefaults.get("_primary") != null) {
                    String primaryField = (String) aliasDefaults.get("_primary").unwrapped();
                    ObjectNode singleKeyObject = (ObjectNode) jp.getCodec().createObjectNode();
                    singleKeyObject.set(primaryField, singleKeyValue);
                    Jackson.merge(singleKeyObject, Jackson.configConverter(aliasDefaults));
                    singleKeyValue = singleKeyObject;
                } // else let the downstream serializer try to handle it or complain
            } else {
                ObjectNode singleKeyObject = (ObjectNode) singleKeyValue;
                Jackson.merge(singleKeyObject, Jackson.configConverter(aliasDefaults));
            }
            JsonDeserializer<Object> deser = _findDeserializer(ctxt, singleKeyName);
            if (_typeIdVisible && singleKeyValue.isObject()) {
                ((ObjectNode) singleKeyValue).put(classField, singleKeyName);
            }
            JsonParser treeParser = jp.getCodec().treeAsTokens(singleKeyValue);
            treeParser.nextToken();
            return deser.deserialize(treeParser, ctxt);
        } catch (ClassNotFoundException ignored) {
            // expected when the single key is not a valid alias or class. could avoid exception if we dropped
            // support for single-keys that are just classes (ie. anonymous aliases), but we'll leave it in
            // until we have some, more concrete, reason to remove it.
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
        if (!aliasDefaults.isEmpty()) {
            ObjectNode aliasDefaultsNode = Jackson.configConverter(aliasDefaults);
            Jackson.merge(objectNode, aliasDefaultsNode);
        }
        JsonDeserializer<Object> deser = _findDeserializer(ctxt, type);
        JsonParser treeParser = jp.getCodec().treeAsTokens(objectNode);
        treeParser.nextToken();
        return deser.deserialize(treeParser, ctxt);
    }

    private Object _deserializeTypedFromArray(String classField, JsonParser jp, DeserializationContext ctxt)
            throws IOException {
        if (pluginMap.arraySugar() == null) {
            throw ctxt.wrongTokenException(jp, jp.getCurrentToken(),
                                           "Found an array, but there is no _array subtype for "
                                           + pluginMap.category());
        }
        ArrayNode arrayNode = jp.readValueAsTree();
        Config aliasDefaults = pluginMap.aliasDefaults("_array").toConfig();
        String arrayField = aliasDefaults.getString("_primary");
        ObjectNode objectFieldValues = (ObjectNode) jp.getCodec().createObjectNode();
        objectFieldValues.set(arrayField, arrayNode);
        ObjectNode aliasFieldDefaults = Jackson.configConverter(aliasDefaults.root());
        Jackson.merge(objectFieldValues, aliasFieldDefaults);
        JsonDeserializer<Object> deser = _findDeserializer(ctxt, "_array");
        JsonParser treeParser = jp.getCodec().treeAsTokens(objectFieldValues);
        treeParser.nextToken();
        return deser.deserialize(treeParser, ctxt);
    }
}
