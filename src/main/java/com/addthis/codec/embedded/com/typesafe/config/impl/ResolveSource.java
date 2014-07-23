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

import java.util.IdentityHashMap;
import java.util.Map;

import com.addthis.codec.embedded.com.typesafe.config.ConfigException;

/**
 * This class is the source for values for a substitution like ${foo}.
 */
final class ResolveSource {
    final private AbstractConfigObject root;
    // Conceptually, we transform the ResolveSource whenever we traverse
    // a substitution or delayed merge stack, in order to remove the
    // traversed node and therefore avoid circular dependencies.
    // We implement it with this somewhat hacky "patch a replacement"
    // mechanism instead of actually transforming the tree.
    final private Map<AbstractConfigValue, ResolveReplacer> replacements;

    ResolveSource(AbstractConfigObject root) {
        this.root = root;
        this.replacements = new IdentityHashMap<AbstractConfigValue, ResolveReplacer>();
    }

    static private AbstractConfigValue findInObject(AbstractConfigObject obj,
            ResolveContext context, SubstitutionExpression subst)
            throws AbstractConfigValue.NotPossibleToResolve {
        return obj.peekPath(subst.path(), context);
    }

    AbstractConfigValue lookupSubst(ResolveContext context, SubstitutionExpression subst,
            int prefixLength) throws AbstractConfigValue.NotPossibleToResolve {
        context.trace(subst);
        try {
            // First we look up the full path, which means relative to the
            // included file if we were not a root file
            AbstractConfigValue result = findInObject(root, context, subst);

            if (result == null) {
                // Then we want to check relative to the root file. We don't
                // want the prefix we were included at to be used when looking
                // up env variables either.
                SubstitutionExpression unprefixed = subst.changePath(subst.path().subPath(
                        prefixLength));

                // replace the debug trace path
                context.untrace();
                context.trace(unprefixed);

                if (prefixLength > 0) {
                    result = findInObject(root, context, unprefixed);
                }

                if (result == null && context.options().getUseSystemEnvironment()) {
                    result = findInObject(ConfigImpl.envVariablesAsConfigObject(), context,
                            unprefixed);
                }
            }

            if (result != null) {
                result = context.resolve(result);
            }

            return result;
        } finally {
            context.untrace();
        }
    }

    void replace(AbstractConfigValue value, ResolveReplacer replacer) {
        ResolveReplacer old = replacements.put(value, replacer);
        if (old != null)
            throw new ConfigException.BugOrBroken("should not have replaced the same value twice: "
                    + value);
    }

    void unreplace(AbstractConfigValue value) {
        ResolveReplacer replacer = replacements.remove(value);
        if (replacer == null)
            throw new ConfigException.BugOrBroken("unreplace() without replace(): " + value);
    }

    private AbstractConfigValue replacement(ResolveContext context, AbstractConfigValue value)
            throws AbstractConfigValue.NotPossibleToResolve {
        ResolveReplacer replacer = replacements.get(value);
        if (replacer == null) {
            return value;
        } else {
            return replacer.replace(context);
        }
    }

    /**
     * Conceptually, this is key.value().resolveSubstitutions() but using the
     * replacement for key.value() if any.
     */
    AbstractConfigValue resolveCheckingReplacement(ResolveContext context,
            AbstractConfigValue original) throws AbstractConfigValue.NotPossibleToResolve {
        AbstractConfigValue replacement;

        replacement = replacement(context, original);

        if (replacement != original) {
            // start over, checking if replacement was memoized
            return context.resolve(replacement);
        } else {
            AbstractConfigValue resolved;

            resolved = original.resolveSubstitutions(context);

            return resolved;
        }
    }
}
