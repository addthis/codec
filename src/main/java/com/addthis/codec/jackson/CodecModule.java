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

import com.addthis.codec.plugins.PluginRegistry;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;
import com.typesafe.config.Config;

public class CodecModule extends Module {

    private final PluginRegistry pluginRegistry;
    private final Config globalConfig;

    public CodecModule(PluginRegistry pluginRegistry, Config globalConfig) {
        this.pluginRegistry = pluginRegistry;
        this.globalConfig = globalConfig;
    }

    public CodecModule(PluginRegistry pluginRegistry) {
        this.pluginRegistry = pluginRegistry;
        this.globalConfig = pluginRegistry.config();
    }

    @Override public void setupModule(SetupContext context) {
        context.appendAnnotationIntrospector(new CodecIntrospector(pluginRegistry));
        if (globalConfig.getBoolean("addthis.codec.jackson.ignore.underscore")) {
            context.addDeserializationProblemHandler(new UnderscorePropertyIgnorer());
        }
        if (globalConfig.getBoolean("addthis.codec.jackson.ignore.write-only")) {
            context.addDeserializationProblemHandler(new WriteonlyPropertyIgnorer());
        }
        if (globalConfig.getBoolean("addthis.codec.jackson.modify-fields")) {
            context.addBeanDeserializerModifier(new CodecBeanDeserializerModifier(globalConfig));
        }
        context.addDeserializers(new CodecDeserializers());
    }

    @Override public String getModuleName() {
        return "addthis-codec";
    }

    @Override public Version version() {
        return Version.unknownVersion();
    }
}
