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

import com.addthis.codec.config.ConfigTraversingParser;
import com.addthis.codec.config.Configs;
import com.addthis.codec.plugins.PluginMap;
import com.addthis.codec.plugins.PluginRegistry;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValue;

import io.dropwizard.validation.ConstraintViolations;

/**
 * Wrapper over an object mapper, plugin registry, global config, and a validator. Provides
 * methods for constructing objects that automatically perform validation, and bridge hocon
 * strings to jackson construction. Normally obtained either from {@link Jackson#defaultCodec()}
 * or used indirectly from the static helper methods in {@link Configs}.
 */
@Beta
public class CodecJackson {

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
            ObjectMapper newObjectMapper = Jackson.newObjectMapper(newPluginRegistry);
            return new CodecJackson(newObjectMapper, newPluginRegistry, newGlobalDefaults, validator);
        }
    }

    public CodecJackson withOverrides(Config overrides) {
        if (overrides == this.globalDefaults) {
            return this;
        } else {
            PluginRegistry newPluginRegistry = pluginRegistry.withOverrides(overrides);
            ObjectMapper newObjectMapper = Jackson.newObjectMapper(newPluginRegistry);
            return new CodecJackson(newObjectMapper, newPluginRegistry, overrides, validator);
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

    // doesn't delegate

    /**
     * Construct an object of the requested type based on the default values and types (if the requested
     * is not a concrete class).
     */
    public <T> T newDefault(@Nonnull Class<T> type) throws JsonProcessingException, IOException {
        T value = objectMapper.treeToValue(DefaultCodecJackson.DEFAULT_MAPPER.createObjectNode(), type);
        return validate(value);
    }

    public <T> T decodeObject(@Nonnull Class<T> type, ConfigValue configValue)
            throws JsonProcessingException, IOException {
        ConfigTraversingParser configParser = new ConfigTraversingParser(configValue, objectMapper);
        return validate(configParser.readValueAs(type));
    }

    public <T> T decodeObject(@Nonnull Class<T> type, JsonNode jsonNode) throws JsonProcessingException {
        return validate(objectMapper.treeToValue(jsonNode, type));
    }

    // delegates

    /** Construct an object of the requested plugin category based on the default type and values */
    public <T> T newDefault(@Nonnull String category) throws JsonProcessingException, IOException {
        Class<T> type = (Class<T>) pluginRegistry.byCategory().get(category).baseClass();
        return newDefault(type);
    }

    /**
     * Tries to parse the string as an isolated typesafe-config object, tries to resolve it, and then calls
     * {@link #decodeObject(String, Config)} with the resultant config and the passed in category. Pretty much just
     * a convenience function for simple use cases that don't want to care about how ConfigFactory works.
     */
    public <T> T decodeObject(@Nonnull String category, @Syntax("HOCON") String configText)
            throws JsonProcessingException, IOException {
        PluginMap pluginMap = Preconditions.checkNotNull(pluginRegistry.asMap().get(category),
                                                         "could not find anything about the category %s", category);
        Config config = ConfigFactory.parseString(configText).resolve();
        return (T) decodeObject(pluginMap.baseClass(), config);
    }

    /**
     * Tries to parse the string as an isolated typesafe-config object, tries to resolve it, and then calls
     * {@link #decodeObject(Class, Config)} with the resultant config and the passed in type. Pretty much just
     * a convenience function for simple use cases that don't want to care about how ConfigFactory works.
     */
    public <T> T decodeObject(@Nonnull Class<T> type, @Syntax("HOCON") String configText)
            throws JsonProcessingException, IOException {
        Config config = ConfigFactory.parseString(configText).resolve();
        return decodeObject(type, config);
    }

    /**
     * Instantiate an object of the requested category based on the provided config. The config should only contain
     * field and type information for the object to be constructed. Global defaults, plugin configuration, etc, are
     * provided by this CodecConfig instance's globalConfig and pluginRegistry fields.
     */
    public <T> T decodeObject(@Nonnull String category, @Nonnull Config config)
            throws JsonProcessingException, IOException {
        PluginMap pluginMap = Preconditions.checkNotNull(pluginRegistry.asMap().get(category),
                                                         "could not find anything about the category %s", category);
        return (T) decodeObject(pluginMap.baseClass(), config);
    }

    /**
     * Instantiate an object of the requested type based on the provided config. The config should only contain
     * field and type information for the object to be constructed. Global defaults, plugin configuration, etc, are
     * provided by this CodecConfig instance's globalConfig and pluginRegistry fields.
     */
    public <T> T decodeObject(@Nonnull Class<T> type, Config config)
            throws JsonProcessingException, IOException {
        return decodeObject(type, config.root());
    }

    /**
     * Instantiate an object without a compile time expected type. This expects a config of the
     * form "{plugin-category: {...}}". ie. there should be exactly one top level key and that
     * key should be a valid, loaded, plug-in category.
     */
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
