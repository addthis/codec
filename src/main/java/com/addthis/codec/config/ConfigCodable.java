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
package com.addthis.codec.config;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.addthis.codec.codables.Codable;
import com.addthis.codec.embedded.com.typesafe.config.ConfigObject;

import com.google.common.annotations.Beta;

/**
 * Note: This is only intended for unusual cases. Possibly debugging or classes that manipulate Config objects as a
 * core part of their functionality and need more than the raw ConfigValues that fields thusly typed would provide.
 *
 * The method defined here is called when an object is constructed from a Config or ConfigObject (even if it is derived
 * from a combination of a ConfigValue and syntax sugar from a pluggable type). In other words, unless this class
 * also implements {@link ValueCodable}, this method is guaranteed to be called before any fields are assigned.
 *
 * It gives implementors a chance to make any last minute changes or observations on its intended config -- or
 * to supplant the process altogether. If the implementor returns a non-null ConfigOject, construction will resume
 * as normal with the returned ConfigObject replacing the one that was passed in as a parameter. Otherwise, no
 * fields as assigned values by Codec. This differs from returning an empty object in at least three notable ways:
 *
 * 1. fields flagged as required are not checked
 * 2. default values are not used (the second parameter passed in - their behavior will otherwise be unchanged)
 * 3. codable fields in the super class will not be set, so make sure to initialize them in this method if needed
 */
@Beta
public interface ConfigCodable extends Codable {

    /**
     * This method may perform any amount (including none) of object initialization. However, if it does not commit
     * to entirely initializing the object (including any super classes), then it should return a ConfigObject
     * (maybe just the one that was passed in if no codable fields were set).
     *
     * @param config the configured values that would otherwise be scanned for codable field names and used to
     *               construct this object. This includes any alias defaults, but not global defaults.
     * @param defaults the global default values for objects of this class. This includes defaults derived from
     *                 super classes and this method may need to use those to do any setup the super classes may need.
     */
    @Nullable public ConfigObject fromConfigObject(@Nonnull ConfigObject config, @Nonnull ConfigObject defaults);
}
