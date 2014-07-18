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

import com.addthis.codec.codables.Codable;

import com.google.common.annotations.Beta;

import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

/**
 * Note: This is only intended for unusual cases. If this functionality seems tempting, you should probably look more
 * into "_primary" sugar on pluggable types or consider whether a codable ConfigValue field would suffice (such fields
 * are assigned the same value that would otherwise be passed to a field with a type that implements this interface).
 *
 * Objects that implement this interface can be initialized with only a single, non-map, ConfigValue. This does not
 * prevent construction of this object using a ConfigObject, but it is always mutually exclusive.
 */
@Beta
public interface ValueCodable extends Codable {

    /**
     * This method should perform all object initialization -- including any that may be required by super classes.
     *
     * @param defaults the default values for objects of this class and/ or alias. This includes defaults derived
     *                 from super classes and this method should use those to do any setup the super classes may need.
     */
    public void fromConfigValue(ConfigValue configValue, ConfigObject defaults);
}
