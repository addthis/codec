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

import javax.annotation.Nonnull;
import javax.annotation.Syntax;

import java.util.Iterator;

import com.google.common.base.Splitter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.guava.GuavaModule;
import com.fasterxml.jackson.datatype.jdk7.Jdk7Module;
import com.fasterxml.jackson.datatype.joda.JodaModule;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

import static com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY;
import static com.fasterxml.jackson.databind.MapperFeature.ALLOW_FINAL_FIELDS_AS_MUTATORS;
import static com.fasterxml.jackson.databind.MapperFeature.INFER_PROPERTY_MUTATORS;
import static com.fasterxml.jackson.databind.MapperFeature.USE_GETTERS_AS_SETTERS;

public final class Jackson {
    private Jackson() {}

    public static final ObjectMapper SIMPLE_MAPPER = new ObjectMapper();
    public static final JavaType NODE_TYPE = SIMPLE_MAPPER.constructType(JsonNode.class);

    public static ObjectMapper defaultMapper() {
        return DefaultCodecJackson.DEFAULT_MAPPER;
    }

    /**
     * Construct an object of the requested type based on the default values and types (if the requested
     * is not a concrete class).
     */
    public static <T> T newDefault(@Nonnull Class<T> type) {
        return DefaultCodecJackson.DEFAULT.newDefault(type);
    }

    public static <T> T decodeObject(@Nonnull Class<T> type, @Syntax("HOCON") String configText) {
        return DefaultCodecJackson.DEFAULT.decodeObject(type, configText);
    }

    public static <T> T decodeObject(@Nonnull Class<T> type, Config config) {
        return DefaultCodecJackson.DEFAULT.decodeObject(type, config);
    }

    public static ObjectMapper newObjectMapper(CodecModule codecModule) {
        ObjectMapper objectMapper = new ObjectMapper();
        toggleObjectMapperOptions(objectMapper);
        objectMapper.registerModule(codecModule);
        registerExtraModules(objectMapper);
        toggleParserOptions(objectMapper);
        return objectMapper;
    }

    public static ObjectMapper registerExtraModules(ObjectMapper objectMapper) {
        objectMapper.registerModule(new GuavaModule());
        objectMapper.registerModule(new Jdk7Module());
        objectMapper.registerModule(new JodaModule());
        return objectMapper;
    }

    public static ObjectMapper toggleParserOptions(ObjectMapper objectMapper) {
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
        objectMapper.enable(FAIL_ON_IGNORED_PROPERTIES);

        // essentially auto-collection everywhere, but that seems fine and this is easy
        objectMapper.enable(ACCEPT_SINGLE_VALUE_AS_ARRAY);
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
