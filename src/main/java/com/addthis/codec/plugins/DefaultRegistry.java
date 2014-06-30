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
package com.addthis.codec.plugins;

import com.typesafe.config.ConfigFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DefaultRegistry {

    private static final Logger log;
    static final PluginRegistry DEFAULT;

    static {
        log = LoggerFactory.getLogger(DefaultRegistry.class);
        DEFAULT = new PluginRegistry(ConfigFactory.load());
        log.debug("initialized default plugin registry {}", DEFAULT);
    }
}
