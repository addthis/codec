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
import javax.validation.ConstraintViolation;
import javax.validation.ValidationException;
import javax.validation.Validator;

import java.io.IOException;

import java.util.Set;

import com.addthis.codec.jackson.tree.ConfigTraversingParser;
import com.addthis.codec.plugins.PluginMap;
import com.addthis.codec.plugins.PluginRegistry;

import com.google.common.base.Preconditions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

import io.dropwizard.validation.ConstraintViolations;

public class CodecJackson {

    public static CodecJackson getDefault() {
        return DefaultCodecJackson.DEFAULT;
    }

    private final ObjectMapper objectMapper;
    private final PluginRegistry pluginRegistry;
    private final Config globalDefaults;
    private final Validator validator;

    public CodecJackson(ObjectMapper objectMapper, PluginRegistry pluginRegistry,
                        Config globalDefaults, Validator validator) {
        this.objectMapper = objectMapper;
        this.pluginRegistry = pluginRegistry;
        this.globalDefaults = globalDefaults;
        this.validator = validator;
    }

    public CodecJackson withConfig(Config newGlobalDefaults) {
        if (newGlobalDefaults == this.globalDefaults) {
            return this;
        } else {
            PluginRegistry newPluginRegistry = new PluginRegistry(newGlobalDefaults);
            CodecModule newCodecModule = new CodecModule(newPluginRegistry, newGlobalDefaults);
            ObjectMapper newObjectMapper = Jackson.newObjectMapper(newCodecModule);
            return new CodecJackson(newObjectMapper, newPluginRegistry, newGlobalDefaults, validator);
        }
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public PluginRegistry getPluginRegistry() {
        return pluginRegistry;
    }

    public Config getGlobalDefaults() {
        return globalDefaults;
    }

    public Validator getValidator() {
        return validator;
    }

    /**
     * Construct an object of the requested type based on the default values and types (if the requested
     * is not a concrete class).
     */
    public <T> T newDefault(@Nonnull Class<T> type) throws JsonProcessingException {
        T value = objectMapper.treeToValue(DefaultCodecJackson.DEFAULT_MAPPER.createObjectNode(), type);
        return validate(value);
    }

    public <T> T decodeObject(@Nonnull String category, @Syntax("HOCON") String configText)
            throws JsonProcessingException, IOException {
        PluginMap pluginMap = Preconditions.checkNotNull(pluginRegistry.asMap().get(category),
                                                         "could not find anything about the category %s", category);
        Config config = ConfigFactory.parseString(configText).resolve();
        return (T) decodeObject(pluginMap.baseClass(), config);
    }

    public <T> T decodeObject(@Nonnull Class<T> type, @Syntax("HOCON") String configText)
            throws JsonProcessingException, IOException {
        Config config = ConfigFactory.parseString(configText).resolve();
        return decodeObject(type, config);
    }

    public <T> T decodeObject(@Nonnull String category, @Nonnull Config config)
            throws JsonProcessingException, IOException {
        PluginMap pluginMap = Preconditions.checkNotNull(pluginRegistry.asMap().get(category),
                                                         "could not find anything about the category %s", category);
        return (T) decodeObject(pluginMap.baseClass(), config);
    }

    public <T> T decodeObject(@Nonnull Class<T> type, Config config)
            throws JsonProcessingException, IOException {
        return decodeObject(type, config.root());
    }

    // doesn't delegate
    public <T> T decodeObject(@Nonnull Class<T> type, ConfigValue configValue)
            throws JsonProcessingException, IOException {
        ConfigTraversingParser configParser = new ConfigTraversingParser(configValue, objectMapper);
        return validate(configParser.readValueAs(type));
    }

    // doesn't delegate
    public <T> T decodeObject(@Nonnull Class<T> type, JsonNode jsonNode) throws JsonProcessingException {
        return validate(objectMapper.treeToValue(jsonNode, type));
    }

    public <T> T decodeObject(@Syntax("HOCON") String configText) throws JsonProcessingException, IOException {
        Config config = ConfigFactory.parseString(configText).resolve();
        return decodeObject(config);
    }

    /**
     * Instantiate an object without a compile time expected type. This expects a config of the
     * form "{plugin-category: {...}}". ie. there should be exactly one top level key and that
     * key should be a valid, loaded, plug-in category.
     */
    public <T> T decodeObject(Config config) throws JsonProcessingException, IOException {
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

    public <T> T validate(T value) {
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw new ValidationException(ConstraintViolations.format(violations).toString());
        }
        return value;
    }
}
