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
package com.addthis.codec.embedded.com.typesafe.config.impl;

import java.util.HashMap;
import java.util.Map;

/**
 * This exists because we have to memoize resolved substitutions as we go
 * through the config tree; otherwise we could end up creating multiple copies
 * of values or whole trees of values as we follow chains of substitutions.
 */
final class ResolveMemos {
    // note that we can resolve things to undefined (represented as Java null,
    // rather than ConfigNull) so this map can have null values.
    final private Map<MemoKey, AbstractConfigValue> memos;

    ResolveMemos() {
        this.memos = new HashMap<MemoKey, AbstractConfigValue>();
    }

    AbstractConfigValue get(MemoKey key) {
        return memos.get(key);
    }

    void put(MemoKey key, AbstractConfigValue value) {
        memos.put(key, value);
    }
}
