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

import javax.validation.Validation;
import javax.validation.Validator;

import com.addthis.codec.plugins.PluginRegistry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.ConfigFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultCodecJackson {
    private static final Logger log;

    static final CodecModule DEFAULT_MODULE;
    static final ObjectMapper DEFAULT_MAPPER;
    static final Validator DEFAULT_VALIDATOR;
    static final CodecJackson DEFAULT;

    static {
        log = LoggerFactory.getLogger(DefaultCodecJackson.class);
        DEFAULT_MODULE = new CodecModule(PluginRegistry.defaultRegistry(), ConfigFactory.load());
        log.trace("initialized default codec module {}", DEFAULT_MODULE);
        DEFAULT_MAPPER = Jackson.newObjectMapper(DEFAULT_MODULE);
        log.trace("initialized default codec mapper {}", DEFAULT_MAPPER);
        DEFAULT_VALIDATOR =  Validation.buildDefaultValidatorFactory().getValidator();
        log.trace("initialized default codec validator {}", DEFAULT_VALIDATOR);
        DEFAULT = new CodecJackson(DEFAULT_MAPPER, PluginRegistry.defaultRegistry(),
                                   ConfigFactory.load(), DEFAULT_VALIDATOR);
        log.trace("initialized default codec jackson {}", DEFAULT);
    }
}
