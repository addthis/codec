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

/**
 *   Copyright (C) 2011-2012 Typesafe Inc. <http://typesafe.com>
 */
package com.addthis.codec.embedded.com.typesafe.config.impl;

import java.util.Collection;

/**
 * Status of substitution resolution.
 */
enum ResolveStatus {
    UNRESOLVED, RESOLVED;

    final static com.addthis.codec.embedded.com.typesafe.config.impl.ResolveStatus fromValues(
            Collection<? extends AbstractConfigValue> values) {
        for (AbstractConfigValue v : values) {
            if (v.resolveStatus() == com.addthis.codec.embedded.com.typesafe.config.impl.ResolveStatus.UNRESOLVED)
                return com.addthis.codec.embedded.com.typesafe.config.impl.ResolveStatus.UNRESOLVED;
        }
        return com.addthis.codec.embedded.com.typesafe.config.impl.ResolveStatus.RESOLVED;
    }

    final static com.addthis.codec.embedded.com.typesafe.config.impl.ResolveStatus fromBoolean(boolean resolved) {
        return resolved ? com.addthis.codec.embedded.com.typesafe.config.impl.ResolveStatus.RESOLVED : com.addthis
                .codec.embedded.com.typesafe.config.impl.ResolveStatus.UNRESOLVED;
    }
}
