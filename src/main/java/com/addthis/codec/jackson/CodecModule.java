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

import com.addthis.codec.config.CodecConfig;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.Module;

public class CodecModule extends Module {
    private final CodecConfig codecConfig;

    public CodecModule(CodecConfig codecConfig) {
        this.codecConfig = codecConfig;
    }

    @Override public void setupModule(SetupContext context) {
        context.insertAnnotationIntrospector(new CodecIntrospector(codecConfig.pluginRegistry()));
    }

    @Override public String getModuleName() {
        return "addthis-codec";
    }

    @Override public Version version() {
        return Version.unknownVersion();
    }
}
