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

import com.addthis.codec.embedded.com.typesafe.config.ConfigException;
import com.addthis.codec.embedded.com.typesafe.config.ConfigOrigin;

class Token {
    final private TokenType tokenType;
    final private String debugString;
    final private ConfigOrigin origin;

    Token(TokenType tokenType, ConfigOrigin origin) {
        this(tokenType, origin, null);
    }

    Token(TokenType tokenType, ConfigOrigin origin, String debugString) {
        this.tokenType = tokenType;
        this.origin = origin;
        this.debugString = debugString;
    }

    // this is used for singleton tokens like COMMA or OPEN_CURLY
    static com.addthis.codec.embedded.com.typesafe.config.impl.Token newWithoutOrigin(TokenType tokenType, String debugString) {
        return new com.addthis.codec.embedded.com.typesafe.config.impl.Token(tokenType, null, debugString);
    }

    final TokenType tokenType() {
        return tokenType;
    }

    // this is final because we don't always use the origin() accessor,
    // and we don't because it throws if origin is null
    final ConfigOrigin origin() {
        // code is only supposed to call origin() on token types that are
        // expected to have an origin.
        if (origin == null)
            throw new ConfigException.BugOrBroken(
                    "tried to get origin from token that doesn't have one: " + this);
        return origin;
    }

    final int lineNumber() {
        if (origin != null)
            return origin.lineNumber();
        else
            return -1;
    }

    @Override
    public String toString() {
        if (debugString != null)
            return debugString;
        else
            return tokenType.name();
    }

    protected boolean canEqual(Object other) {
        return other instanceof com.addthis.codec.embedded.com.typesafe.config.impl.Token;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof com.addthis.codec.embedded.com.typesafe.config.impl.Token) {
            // origin is deliberately left out
            return canEqual(other)
                    && this.tokenType == ((com.addthis.codec.embedded.com.typesafe.config.impl.Token) other).tokenType;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // origin is deliberately left out
        return tokenType.hashCode();
    }
}
