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

import javax.annotation.Nonnull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;

import com.addthis.codec.embedded.com.typesafe.config.Config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Beta
public class PluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistry.class);

    public static final String PLUGIN_DEFAULTS_PATH = "addthis.codec.plugins.defaults";
    public static final String PLUGINS_PATH_PATH    = "addthis.codec.plugins.path";

    public static PluginRegistry defaultRegistry() {
        return DefaultRegistry.DEFAULT;
    }

    @Nonnull private final Map<String, PluginMap> pluginMaps;

    public PluginRegistry(Config config) {
        Config defaultPluginMapSettings = config.getConfig(PLUGIN_DEFAULTS_PATH);
        String pluginPath = config.getString(PLUGINS_PATH_PATH);
        Config pluginConfigs = config.getConfig(pluginPath);

        Set<String> categories = pluginConfigs.root().keySet();
        Map<String, PluginMap> pluginsFromConfig = new HashMap<>(categories.size());
        for (String category : categories) {
            Config pluginConfig = pluginConfigs.getConfig(category)
                                               .withFallback(defaultPluginMapSettings);
            PluginMap pluginMap = new PluginMap(category, pluginConfig);
            pluginsFromConfig.put(category, pluginMap);
        }
        pluginMaps = Collections.unmodifiableMap(pluginsFromConfig);
    }

    public Map<String, PluginMap> asMap() {
        return pluginMaps;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                      .add("pluginMaps", pluginMaps)
                      .toString();
    }
}
