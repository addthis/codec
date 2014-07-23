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

import java.util.Stack;

import com.addthis.codec.embedded.com.typesafe.config.ConfigException;

final class PathBuilder {
    // the keys are kept "backward" (top of stack is end of path)
    final private Stack<String> keys;
    private Path result;

    PathBuilder() {
        keys = new Stack<String>();
    }

    private void checkCanAppend() {
        if (result != null)
            throw new ConfigException.BugOrBroken(
                    "Adding to PathBuilder after getting result");
    }

    void appendKey(String key) {
        checkCanAppend();

        keys.push(key);
    }

    void appendPath(Path path) {
        checkCanAppend();

        String first = path.first();
        Path remainder = path.remainder();
        while (true) {
            keys.push(first);
            if (remainder != null) {
                first = remainder.first();
                remainder = remainder.remainder();
            } else {
                break;
            }
        }
    }

    Path result() {
        // note: if keys is empty, we want to return null, which is a valid
        // empty path
        if (result == null) {
            Path remainder = null;
            while (!keys.isEmpty()) {
                String key = keys.pop();
                remainder = new Path(key, remainder);
            }
            result = remainder;
        }
        return result;
    }
}
