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

import com.addthis.codec.plugins.PluginMap;
import com.addthis.codec.plugins.PluginRegistry;

import com.google.common.base.Throwables;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

import static com.addthis.codec.jackson.Jackson.configConverter;


public class CodecJackson {

    public static CodecJackson getDefault() {
        return DefaultCodecJackson.DEFAULT;
    }

    private final ObjectMapper objectMapper;
    private final PluginRegistry pluginRegistry;
    private final Config globalDefaults;

    public CodecJackson(ObjectMapper objectMapper, PluginRegistry pluginRegistry, Config globalDefaults) {
        this.objectMapper = objectMapper;
        this.pluginRegistry = pluginRegistry;
        this.globalDefaults = globalDefaults;
    }

    /**
     * Construct an object of the requested type based on the default values and types (if the requested
     * is not a concrete class).
     */
    public <T> T newDefault(@Nonnull Class<T> type) {
        try {
            return objectMapper.treeToValue(DefaultCodecJackson.DEFAULT_MAPPER.createObjectNode(), type);
        } catch (JsonProcessingException e) {
            throw Throwables.propagate(e);
        }
    }

    public <T> T decodeObject(@Nonnull Class<T> type, @Syntax("HOCON") String configText) {
        Config config = ConfigFactory.parseString(configText).resolve();
        return decodeObject(type, config);
    }

    public <T> T decodeObject(@Nonnull Class<T> type, Config config) {
        return decodeObject(type, config.root());
    }

    public <T> T decodeObject(@Nonnull Class<T> type, ConfigValue configValue) {
        JsonNode objectNode = configConverter(configValue);
        try {
            return objectMapper.treeToValue(objectNode, type);
        } catch (JsonProcessingException e) {
            throw Throwables.propagate(e);
        }
    }

    /**
     * Instantiate an object without a compile time expected type. This expects a config of the
     * form "{plugin-category: {...}}". ie. there should be exactly one top level key and that
     * key should be a valid, loaded, plug-in category.
     */
    public <T> T decodeObject(Config config) {
        if (config.root().size() != 1) {
            throw new ConfigException.Parse(config.root().origin(),
                                            "config root must have exactly one key");
        }
        String category = config.root().keySet().iterator().next();
        PluginMap pluginMap = pluginRegistry.asMap().get(category);
        if (pluginMap == null) {
            throw new ConfigException.BadValue(config.root().get(category).origin(),
                                               category,
                                               "top level key must be a valid category");
        }
        ConfigValue configValue = config.root().get(category);
        return (T) decodeObject(pluginMap.baseClass(), configValue);
    }
}
