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

import javax.validation.Validator;

import java.io.IOException;

import java.util.Iterator;
import java.util.List;

import com.addthis.codec.plugins.PluginRegistry;
import com.addthis.codec.utils.ExecutorsModule;

import com.google.common.annotations.Beta;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.PropertyBindingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk7.Jdk7Module;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.fasterxml.jackson.datatype.jsr310.JSR310Module;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY;
import static com.fasterxml.jackson.databind.MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS;
import static com.fasterxml.jackson.databind.MapperFeature.INFER_PROPERTY_MUTATORS;
import static com.fasterxml.jackson.databind.MapperFeature.USE_GETTERS_AS_SETTERS;

/**
 * Utility methods for interacting with the jackson library and creating more customized
 * object mappers.
 */
@Beta
public final class Jackson {
    private Jackson() {}

    public static final ObjectMapper SIMPLE_MAPPER = new ObjectMapper();
    public static final JavaType NODE_TYPE = SIMPLE_MAPPER.constructType(JsonNode.class);

    public static ObjectMapper defaultMapper() {
        return DefaultCodecJackson.DEFAULT_MAPPER;
    }

    public static CodecJackson defaultCodec() {
        return DefaultCodecJackson.DEFAULT_CODEC;
    }

    public static Validator defaultValidator() {
        return DefaultCodecJackson.DEFAULT_VALIDATOR;
    }

    public static ObjectMapper newObjectMapper(PluginRegistry pluginRegistry) {
        CodecModule codecModule = new CodecModule(pluginRegistry);
        Config globalConfig = pluginRegistry.config();
        ObjectMapper objectMapper = new ObjectMapper();
        toggleObjectMapperOptions(objectMapper);
        objectMapper.registerModule(codecModule);
        registerExtraModules(objectMapper);
        allowCommentsAndUnquotedFields(objectMapper);
        objectMapper.setInjectableValues(new BasicInjectableValues());
        return objectMapper;
    }

    public static ObjectMapper registerExtraModules(ObjectMapper objectMapper) {
        objectMapper.registerModule(new GuavaModule());
        objectMapper.registerModule(new Jdk7Module());
        objectMapper.registerModule(new Jdk8Module());
        // jsr310 is basically just the jdk 8 date/time classes split into its own module
        objectMapper.registerModule(new JSR310Module());
        objectMapper.registerModule(new JodaModule());
        objectMapper.registerModule(new ExecutorsModule());
        return objectMapper;
    }

    public static ObjectMapper allowCommentsAndUnquotedFields(ObjectMapper objectMapper) {
        objectMapper.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_YAML_COMMENTS, true);
        objectMapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
        return objectMapper;
    }

    public static ObjectMapper toggleObjectMapperOptions(ObjectMapper objectMapper) {
        // potentially useful features, but disabled by default to maintain existing behavior

        // ignore final fields
        objectMapper.disable(ALLOW_FINAL_FIELDS_AS_MUTATORS);
        // do not try to modify existing containers
        objectMapper.disable(USE_GETTERS_AS_SETTERS);
        // public getters do not automaticaly imply codec should try to write to it
        objectMapper.disable(INFER_PROPERTY_MUTATORS);

        // more aggressive failure detection
        objectMapper.enable(FAIL_ON_READING_DUP_TREE_KEY);

        // essentially auto-collection everywhere, but that seems fine and this is easy
        objectMapper.enable(ACCEPT_SINGLE_VALUE_AS_ARRAY);

        // don't write out null fields
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return objectMapper;
    }

    public static JsonNode configConverter(ConfigValue source) {
        return SIMPLE_MAPPER.convertValue(source.unwrapped(), NODE_TYPE);
    }

    public static ObjectNode configConverter(ConfigObject source) {
        return SIMPLE_MAPPER.convertValue(source.unwrapped(), NODE_TYPE);
    }

    public static void merge(ObjectNode primary, ObjectNode backup) {
        Iterator<String> fieldNames = backup.fieldNames();
        while (fieldNames.hasNext()) {
            String fieldName = fieldNames.next();
            JsonNode primaryValue = primary.get(fieldName);
            if (primaryValue == null) {
                JsonNode backupValue = backup.get(fieldName).deepCopy();
                primary.set(fieldName, backupValue);
            } else if (primaryValue.isObject()) {
                JsonNode backupValue = backup.get(fieldName);
                if (backupValue.isObject()) {
                    merge((ObjectNode) primaryValue, (ObjectNode) backupValue.deepCopy());
                }
            }
        }
    }

    private static final Splitter dotSplitter = Splitter.on('.');

    public static void setAt(ObjectNode root, JsonNode value, String path) {
        if (path.indexOf('.') >= 0) {
            Iterator<String> pathIterator = dotSplitter.split(path).iterator();
            while (pathIterator.hasNext()) {
                String nextPart = pathIterator.next();
                if (pathIterator.hasNext()) {
                    root = root.with(nextPart);
                } else {
                    root.set(nextPart, value);
                }
            }
        } else {
            root.set(path, value);
        }
    }

    public static IOException maybeUnwrapPath(String pathToSkip, IOException cause) {
        if ((pathToSkip != null) && (cause instanceof JsonMappingException)) {
            JsonMappingException mappingException = (JsonMappingException) cause;
            List<JsonMappingException.Reference> paths = mappingException.getPath();
            if (!paths.isEmpty()) {
                Iterator<String> pathIterator = dotSplitter.split(pathToSkip).iterator();
                Iterator<JsonMappingException.Reference> refIterator = paths.iterator();
                while (pathIterator.hasNext()) {
                    String pathToSkipPart = pathIterator.next();
                    if (!refIterator.hasNext()) {
                        return cause;
                    }
                    String nextRefField = refIterator.next().getFieldName();
                    if (!pathToSkipPart.equals(nextRefField)) {
                        return cause;
                    }
                }
                JsonMappingException unwrapped = new JsonMappingException(rootMessage(mappingException),
                                                                          mappingException.getLocation(),
                                                                          mappingException.getCause());
                if (refIterator.hasNext()) {
                    List<JsonMappingException.Reference> remainingRefs = Lists.newArrayList(refIterator);
                    for (JsonMappingException.Reference reference : Lists.reverse(remainingRefs)) {
                        unwrapped.prependPath(reference);
                    }
                }
                return unwrapped;
            }
        }
        return cause;
    }

    public static boolean isRealLocation(JsonLocation jsonLocation) {
        return (jsonLocation != null) && (jsonLocation != JsonLocation.NA);
    }

    public static JsonMappingException maybeImproveLocation(JsonLocation wrapLoc, JsonMappingException cause) {
        JsonLocation exLoc = cause.getLocation();
        if (isRealLocation(wrapLoc) && !isRealLocation(exLoc)) {
            if (wrapLoc.getSourceRef() instanceof ConfigValue) {
                ConfigValue locRef = (ConfigValue) wrapLoc.getSourceRef();
                List<JsonMappingException.Reference> paths = cause.getPath();
                for (JsonMappingException.Reference path : paths) {
                    if (locRef instanceof ConfigObject) {
                        String fieldName = path.getFieldName();
                        ConfigObject locRefObject = (ConfigObject) locRef;
                        if (locRefObject.containsKey(fieldName)) {
                            locRef = locRefObject.get(fieldName);
                        } else {
                            break;
                        }
                    } else if (locRef instanceof ConfigList) {
                        int fieldIndex = path.getIndex();
                        ConfigList locRefList = (ConfigList) locRef;
                        if ((fieldIndex >= 0) && (locRefList.size() > fieldIndex)) {
                            locRef = locRefList.get(fieldIndex);
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }
                if (locRef != wrapLoc.getSourceRef()) {
                    wrapLoc = fromConfigValue(locRef);
                }
            }
            List<JsonMappingException.Reference> paths = Lists.reverse(cause.getPath());
            if (!paths.isEmpty()) {
                JsonMappingException withLoc = new JsonMappingException(rootMessage(cause), wrapLoc, cause);
                for (JsonMappingException.Reference path : paths) {
                    withLoc.prependPath(path);
                }
                return withLoc;
            } else {
                return new JsonMappingException(rootMessage(cause), wrapLoc, cause);
            }
        }
        return cause;
    }

    public static String rootMessage(JsonMappingException ex) {
        String rootMessage = ex.getOriginalMessage();
        if (ex instanceof PropertyBindingException) {
            String suffix = ((PropertyBindingException) ex).getMessageSuffix();
            if (rootMessage == null) {
                return suffix;
            } else if (suffix != null) {
                return rootMessage + suffix;
            }
        }
        return rootMessage;
    }

    public static JsonLocation fromConfigValue(ConfigValue configValue) {
        ConfigOrigin configOrigin = configValue.origin();
        return new JsonLocation(configValue, -1, configOrigin.lineNumber(), -1);
    }

    public static JsonNode pathAt(ObjectNode root, String path) {
        if (path.indexOf('.') >= 0) {
            JsonNode returnNode = root;
            for (String nextPart : dotSplitter.split(path)) {
                returnNode = returnNode.path(nextPart);
            }
            return returnNode;
        } else {
            return root.path(path);
        }
    }
}
