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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.addthis.codec.embedded.com.typesafe.config.ConfigException;
import com.addthis.codec.embedded.com.typesafe.config.ConfigMergeable;
import com.addthis.codec.embedded.com.typesafe.config.ConfigObject;
import com.addthis.codec.embedded.com.typesafe.config.ConfigOrigin;
import com.addthis.codec.embedded.com.typesafe.config.ConfigRenderOptions;
import com.addthis.codec.embedded.com.typesafe.config.ConfigValue;
import com.addthis.codec.embedded.com.typesafe.config.ConfigValueType;

abstract class AbstractConfigObject extends AbstractConfigValue implements ConfigObject {

    final private SimpleConfig config;

    protected AbstractConfigObject(ConfigOrigin origin) {
        super(origin);
        this.config = new SimpleConfig(this);
    }

    @Override
    public SimpleConfig toConfig() {
        return config;
    }

    @Override
    public com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject toFallbackValue() {
        return this;
    }

    @Override
    abstract public com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject withOnlyKey(String key);

    @Override
    abstract public com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject withoutKey(String key);

    @Override
    abstract public com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject withValue(String key, ConfigValue value);

    abstract protected com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject withOnlyPathOrNull(Path path);

    abstract com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject withOnlyPath(Path path);

    abstract com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject withoutPath(Path path);

    abstract com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject withValue(Path path, ConfigValue value);

    /**
     * This looks up the key with no transformation or type conversion of any
     * kind, and returns null if the key is not present. The object must be
     * resolved; use attemptPeekWithPartialResolve() if it is not.
     *
     * @param key
     * @return the unmodified raw value or null
     */
    protected final AbstractConfigValue peekAssumingResolved(String key, Path originalPath) {
        try {
            return attemptPeekWithPartialResolve(key);
        } catch (ConfigException.NotResolved e) {
            throw com.addthis.codec.embedded.com.typesafe.config.impl.ConfigImpl.improveNotResolved(originalPath, e);
        }
    }

    /**
     * Look up the key on an only-partially-resolved object, with no
     * transformation or type conversion of any kind; if 'this' is not resolved
     * then try to look up the key anyway if possible.
     *
     * @param key
     *            key to look up
     * @return the value of the key, or null if known not to exist
     * @throws ConfigException.NotResolved
     *             if can't figure out key's value or can't know whether it
     *             exists
     */
    protected abstract AbstractConfigValue attemptPeekWithPartialResolve(String key);

    /**
     * Looks up the path with no transformation, type conversion, or exceptions
     * (just returns null if path not found). Does however resolve the path, if
     * resolver != null.
     *
     * @throws NotPossibleToResolve
     *             if context is not null and resolution fails
     */
    protected AbstractConfigValue peekPath(Path path, com.addthis.codec.embedded.com.typesafe.config.impl.ResolveContext context) throws
                                                                                                       AbstractConfigValue.NotPossibleToResolve {
        return peekPath(this, path, context);
    }

    /**
     * Looks up the path. Doesn't do any resolution, will throw if any is
     * needed.
     */
    AbstractConfigValue peekPath(Path path) {
        try {
            return peekPath(this, path, null);
        } catch (AbstractConfigValue.NotPossibleToResolve e) {
            throw new ConfigException.BugOrBroken(
                    "NotPossibleToResolve happened though we had no ResolveContext in peekPath");
        }
    }

    // as a side effect, peekPath() will have to resolve all parents of the
    // child being peeked, but NOT the child itself. Caller has to resolve
    // the child itself if needed.
    private static AbstractConfigValue peekPath(com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject self, Path path,
            com.addthis.codec.embedded.com.typesafe.config.impl.ResolveContext context) throws AbstractConfigValue.NotPossibleToResolve {
        try {
            if (context != null) {
                // walk down through the path resolving only things along that
                // path, and then recursively call ourselves with no resolve
                // context.
                AbstractConfigValue partiallyResolved = context.restrict(path).resolve(self);
                if (partiallyResolved instanceof com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject) {
                    return peekPath((com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject) partiallyResolved, path, null);
                } else {
                    throw new ConfigException.BugOrBroken("resolved object to non-object " + self
                            + " to " + partiallyResolved);
                }
            } else {
                // with no resolver, we'll fail if anything along the path can't
                // be looked at without resolving.
                Path next = path.remainder();
                AbstractConfigValue v = self.attemptPeekWithPartialResolve(path.first());

                if (next == null) {
                    return v;
                } else {
                    if (v instanceof com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject) {
                        return peekPath((com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject) v, next, null);
                    } else {
                        return null;
                    }
                }
            }
        } catch (ConfigException.NotResolved e) {
            throw ConfigImpl.improveNotResolved(path, e);
        }
    }

    @Override
    public ConfigValueType valueType() {
        return ConfigValueType.OBJECT;
    }

    protected abstract com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject newCopy(ResolveStatus status, ConfigOrigin origin);

    @Override
    protected com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject newCopy(ConfigOrigin origin) {
        return newCopy(resolveStatus(), origin);
    }

    @Override
    protected com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject constructDelayedMerge(ConfigOrigin origin,
            List<AbstractConfigValue> stack) {
        return new com.addthis.codec.embedded.com.typesafe.config.impl.ConfigDelayedMergeObject(origin, stack);
    }

    @Override
    protected abstract com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject mergedWithObject(com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject fallback);

    @Override
    public com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject withFallback(ConfigMergeable mergeable) {
        return (com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject) super.withFallback(mergeable);
    }

    static ConfigOrigin mergeOrigins(
            Collection<? extends AbstractConfigValue> stack) {
        if (stack.isEmpty())
            throw new ConfigException.BugOrBroken(
                    "can't merge origins on empty list");
        List<ConfigOrigin> origins = new ArrayList<ConfigOrigin>();
        ConfigOrigin firstOrigin = null;
        int numMerged = 0;
        for (AbstractConfigValue v : stack) {
            if (firstOrigin == null)
                firstOrigin = v.origin();

            if (v instanceof com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject
                    && ((com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject) v).resolveStatus() == ResolveStatus.RESOLVED
                    && ((ConfigObject) v).isEmpty()) {
                // don't include empty files or the .empty()
                // config in the description, since they are
                // likely to be "implementation details"
            } else {
                origins.add(v.origin());
                numMerged += 1;
            }
        }

        if (numMerged == 0) {
            // the configs were all empty, so just use the first one
            origins.add(firstOrigin);
        }

        return com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigOrigin.mergeOrigins(origins);
    }

    static ConfigOrigin mergeOrigins(com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject... stack) {
        return mergeOrigins(Arrays.asList(stack));
    }

    @Override
    abstract com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject resolveSubstitutions(com.addthis.codec.embedded.com.typesafe.config.impl.ResolveContext context) throws
                                                                                                                                 AbstractConfigValue.NotPossibleToResolve;

    @Override
    abstract com.addthis.codec.embedded.com.typesafe.config.impl.AbstractConfigObject relativized(final Path prefix);

    @Override
    public abstract AbstractConfigValue get(Object key);

    @Override
    protected abstract void render(StringBuilder sb, int indent, boolean atRoot, ConfigRenderOptions options);

    private static UnsupportedOperationException weAreImmutable(String method) {
        return new UnsupportedOperationException("ConfigObject is immutable, you can't call Map."
                + method);
    }

    @Override
    public void clear() {
        throw weAreImmutable("clear");
    }

    @Override
    public ConfigValue put(String arg0, ConfigValue arg1) {
        throw weAreImmutable("put");
    }

    @Override
    public void putAll(Map<? extends String, ? extends ConfigValue> arg0) {
        throw weAreImmutable("putAll");
    }

    @Override
    public ConfigValue remove(Object arg0) {
        throw weAreImmutable("remove");
    }
}
