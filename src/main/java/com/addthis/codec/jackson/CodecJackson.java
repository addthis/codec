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

import java.util.Set;

import com.addthis.codec.plugins.PluginMap;
import com.addthis.codec.plugins.PluginRegistry;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

import static com.addthis.codec.jackson.Jackson.configConverter;
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
    public <T> T newDefault(@Nonnull Class<T> type) {
        try {
            T value = objectMapper.treeToValue(DefaultCodecJackson.DEFAULT_MAPPER.createObjectNode(), type);
            return validate(value);
        } catch (JsonProcessingException e) {
            throw Throwables.propagate(e);
        }
    }

    public <T> T decodeObject(@Nonnull String category, @Syntax("HOCON") String configText) {
        PluginMap pluginMap = Preconditions.checkNotNull(pluginRegistry.asMap().get(category),
                                                         "could not find anything about the category %s", category);
        Config config = ConfigFactory.parseString(configText).resolve();
        return (T) validate(decodeObject(pluginMap.baseClass(), config));
    }

    public <T> T decodeObject(@Nonnull Class<T> type, @Syntax("HOCON") String configText) {
        Config config = ConfigFactory.parseString(configText).resolve();
        return validate(decodeObject(type, config));
    }

    public <T> T decodeObject(@Nonnull String category, @Nonnull Config config) {
        PluginMap pluginMap = Preconditions.checkNotNull(pluginRegistry.asMap().get(category),
                                                         "could not find anything about the category %s", category);
        return (T) validate(decodeObject(pluginMap.baseClass(), config));
    }

    public <T> T decodeObject(@Nonnull Class<T> type, Config config) {
        return validate(decodeObject(type, config.root()));
    }

    public <T> T decodeObject(@Nonnull Class<T> type, ConfigValue configValue) {
        JsonNode objectNode = configConverter(configValue);
        try {
            return validate(objectMapper.treeToValue(objectNode, type));
        } catch (JsonProcessingException e) {
            throw Throwables.propagate(e);
        }
    }

    public <T> T decodeObject(@Nonnull Class<T> type, JsonNode jsonNode) {
        try {
            return validate(objectMapper.treeToValue(jsonNode, type));
        } catch (JsonProcessingException e) {
            throw Throwables.propagate(e);
        }
    }

    public <T> T decodeObject(@Syntax("HOCON") String configText) {
        Config config = ConfigFactory.parseString(configText).resolve();
        return decodeObject(config);
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
        return validate((T) decodeObject(pluginMap.baseClass(), configValue));
    }

    public <T> T validate(T value) {
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw new ValidationException(ConstraintViolations.format(violations).toString());
        }
        return value;
    }
}
