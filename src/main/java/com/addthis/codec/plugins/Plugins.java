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

public final class Plugins {
    private Plugins() {}

    public static String classNameSuggestions(PluginRegistry pluginRegistry,
                                              PluginMap pluginMap,
                                              String type) {
        StringBuilder builder = new StringBuilder();

        builder.append("Could not instantiate an instance of the ")
               .append(pluginMap.category()).append(" category that you have specified")
               .append(" with \"").append(type).append("\".");

        if (pluginRegistry != null) {
            for (PluginMap otherMap : pluginRegistry.asMap().values()) {
                if (otherMap.asBiMap().containsKey(type)) {
                    builder.append("\nIt looks like you tried to instantiate a ")
                           .append(otherMap.category()).append('.');
                    return builder.toString();
                }
            }
        }

        builder.append("\nPerhaps you intended one of the following: ")
               .append(pluginMap.asBiMap().keySet());
        return builder.toString();
    }

}
