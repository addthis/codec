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

import java.util.Iterator;
import java.util.List;

import com.addthis.codec.embedded.com.typesafe.config.ConfigException;

final class Path {

    final private String first;
    final private com.addthis.codec.embedded.com.typesafe.config.impl.Path remainder;

    Path(String first, com.addthis.codec.embedded.com.typesafe.config.impl.Path remainder) {
        this.first = first;
        this.remainder = remainder;
    }

    Path(String... elements) {
        if (elements.length == 0)
            throw new ConfigException.BugOrBroken("empty path");
        this.first = elements[0];
        if (elements.length > 1) {
            PathBuilder pb = new PathBuilder();
            for (int i = 1; i < elements.length; ++i) {
                pb.appendKey(elements[i]);
            }
            this.remainder = pb.result();
        } else {
            this.remainder = null;
        }
    }

    // append all the paths in the list together into one path
    Path(List<com.addthis.codec.embedded.com.typesafe.config.impl.Path> pathsToConcat) {
        this(pathsToConcat.iterator());
    }

    // append all the paths in the iterator together into one path
    Path(Iterator<com.addthis.codec.embedded.com.typesafe.config.impl.Path> i) {
        if (!i.hasNext())
            throw new ConfigException.BugOrBroken("empty path");

        com.addthis.codec.embedded.com.typesafe.config.impl.Path firstPath = i.next();
        this.first = firstPath.first;

        PathBuilder pb = new PathBuilder();
        if (firstPath.remainder != null) {
            pb.appendPath(firstPath.remainder);
        }
        while (i.hasNext()) {
            pb.appendPath(i.next());
        }
        this.remainder = pb.result();
    }

    String first() {
        return first;
    }

    /**
     *
     * @return path minus the first element or null if no more elements
     */
    com.addthis.codec.embedded.com.typesafe.config.impl.Path remainder() {
        return remainder;
    }

    /**
     *
     * @return path minus the last element or null if we have just one element
     */
    com.addthis.codec.embedded.com.typesafe.config.impl.Path parent() {
        if (remainder == null)
            return null;

        PathBuilder pb = new PathBuilder();
        com.addthis.codec.embedded.com.typesafe.config.impl.Path p = this;
        while (p.remainder != null) {
            pb.appendKey(p.first);
            p = p.remainder;
        }
        return pb.result();
    }

    /**
     *
     * @return last element in the path
     */
    String last() {
        com.addthis.codec.embedded.com.typesafe.config.impl.Path p = this;
        while (p.remainder != null) {
            p = p.remainder;
        }
        return p.first;
    }

    com.addthis.codec.embedded.com.typesafe.config.impl.Path prepend(com.addthis.codec.embedded.com.typesafe.config.impl.Path toPrepend) {
        PathBuilder pb = new PathBuilder();
        pb.appendPath(toPrepend);
        pb.appendPath(this);
        return pb.result();
    }

    int length() {
        int count = 1;
        com.addthis.codec.embedded.com.typesafe.config.impl.Path p = remainder;
        while (p != null) {
            count += 1;
            p = p.remainder;
        }
        return count;
    }

    com.addthis.codec.embedded.com.typesafe.config.impl.Path subPath(int removeFromFront) {
        int count = removeFromFront;
        com.addthis.codec.embedded.com.typesafe.config.impl.Path p = this;
        while (p != null && count > 0) {
            count -= 1;
            p = p.remainder;
        }
        return p;
    }

    com.addthis.codec.embedded.com.typesafe.config.impl.Path subPath(int firstIndex, int lastIndex) {
        if (lastIndex < firstIndex)
            throw new ConfigException.BugOrBroken("bad call to subPath");

        com.addthis.codec.embedded.com.typesafe.config.impl.Path from = subPath(firstIndex);
        PathBuilder pb = new PathBuilder();
        int count = lastIndex - firstIndex;
        while (count > 0) {
            count -= 1;
            pb.appendKey(from.first());
            from = from.remainder();
            if (from == null)
                throw new ConfigException.BugOrBroken("subPath lastIndex out of range " + lastIndex);
        }
        return pb.result();
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof com.addthis.codec.embedded.com.typesafe.config.impl.Path) {
            com.addthis.codec.embedded.com.typesafe.config.impl.Path that = (com.addthis.codec.embedded.com.typesafe.config.impl.Path) other;
            return this.first.equals(that.first)
                    && com.addthis.codec.embedded.com.typesafe.config.impl.ConfigImplUtil.equalsHandlingNull(this.remainder,
                                                                                  that.remainder);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return 41 * (41 + first.hashCode())
                + (remainder == null ? 0 : remainder.hashCode());
    }

    // this doesn't have a very precise meaning, just to reduce
    // noise from quotes in the rendered path for average cases
    static boolean hasFunkyChars(String s) {
        int length = s.length();

        if (length == 0)
            return false;

        // if the path starts with something that could be a number,
        // we need to quote it because the number could be invalid,
        // for example it could be a hyphen with no digit afterward
        // or the exponent "e" notation could be mangled.
        char first = s.charAt(0);
        if (!(Character.isLetter(first)))
            return true;

        for (int i = 1; i < length; ++i) {
            char c = s.charAt(i);

            if (Character.isLetterOrDigit(c) || c == '-' || c == '_')
                continue;
            else
                return true;
        }
        return false;
    }

    private void appendToStringBuilder(StringBuilder sb) {
        if (hasFunkyChars(first) || first.isEmpty())
            sb.append(ConfigImplUtil.renderJsonString(first));
        else
            sb.append(first);
        if (remainder != null) {
            sb.append(".");
            remainder.appendToStringBuilder(sb);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Path(");
        appendToStringBuilder(sb);
        sb.append(")");
        return sb.toString();
    }

    /**
     * toString() is a debugging-oriented version while this is an
     * error-message-oriented human-readable one.
     */
    String render() {
        StringBuilder sb = new StringBuilder();
        appendToStringBuilder(sb);
        return sb.toString();
    }

    static com.addthis.codec.embedded.com.typesafe.config.impl.Path newKey(String key) {
        return new com.addthis.codec.embedded.com.typesafe.config.impl.Path(key, null);
    }

    static com.addthis.codec.embedded.com.typesafe.config.impl.Path newPath(String path) {
        return com.addthis.codec.embedded.com.typesafe.config.impl.Parser.parsePath(path);
    }
}
