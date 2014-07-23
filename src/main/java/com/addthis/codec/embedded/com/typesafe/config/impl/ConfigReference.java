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

import java.util.Collection;
import java.util.Collections;

import com.addthis.codec.embedded.com.typesafe.config.ConfigException;
import com.addthis.codec.embedded.com.typesafe.config.ConfigOrigin;
import com.addthis.codec.embedded.com.typesafe.config.ConfigRenderOptions;
import com.addthis.codec.embedded.com.typesafe.config.ConfigValueType;

/**
 * ConfigReference replaces ConfigReference (the older class kept for back
 * compat) and represents the ${} substitution syntax. It can resolve to any
 * kind of value.
 */
final class ConfigReference extends AbstractConfigValue implements Unmergeable {

    final private com.addthis.codec.embedded.com.typesafe.config.impl.SubstitutionExpression expr;
    // the length of any prefixes added with relativized()
    final private int prefixLength;

    ConfigReference(ConfigOrigin origin, com.addthis.codec.embedded.com.typesafe.config.impl.SubstitutionExpression expr) {
        this(origin, expr, 0);
    }

    private ConfigReference(ConfigOrigin origin, com.addthis.codec.embedded.com.typesafe.config.impl.SubstitutionExpression expr, int prefixLength) {
        super(origin);
        this.expr = expr;
        this.prefixLength = prefixLength;
    }

    private ConfigException.NotResolved notResolved() {
        return new ConfigException.NotResolved(
                "need to Config#resolve(), see the API docs for Config#resolve(); substitution not resolved: "
                        + this);
    }

    @Override
    public ConfigValueType valueType() {
        throw notResolved();
    }

    @Override
    public Object unwrapped() {
        throw notResolved();
    }

    @Override
    protected com.addthis.codec.embedded.com.typesafe.config.impl.ConfigReference newCopy(ConfigOrigin newOrigin) {
        return new com.addthis.codec.embedded.com.typesafe.config.impl.ConfigReference(newOrigin, expr, prefixLength);
    }

    @Override
    protected boolean ignoresFallbacks() {
        return false;
    }

    @Override
    public Collection<com.addthis.codec.embedded.com.typesafe.config.impl.ConfigReference> unmergedValues() {
        return Collections.singleton(this);
    }

    // ConfigReference should be a firewall against NotPossibleToResolve going
    // further up the stack; it should convert everything to ConfigException.
    // This way it's impossible for NotPossibleToResolve to "escape" since
    // any failure to resolve has to start with a ConfigReference.
    @Override AbstractConfigValue resolveSubstitutions(com.addthis.codec.embedded.com.typesafe.config.impl.ResolveContext context) {
        context.source().replace(this, com.addthis.codec.embedded.com.typesafe.config.impl.ResolveReplacer.cycleResolveReplacer);
        try {
            AbstractConfigValue v;
            try {
                v = context.source().lookupSubst(context, expr, prefixLength);
            } catch (AbstractConfigValue.NotPossibleToResolve e) {
                if (expr.optional())
                    v = null;
                else
                    throw new ConfigException.UnresolvedSubstitution(origin(), expr
                            + " was part of a cycle of substitutions involving " + e.traceString(),
                            e);
            }

            if (v == null && !expr.optional()) {
                if (context.options().getAllowUnresolved())
                    return this;
                else
                    throw new ConfigException.UnresolvedSubstitution(origin(), expr.toString());
            } else {
                return v;
            }
        } finally {
            context.source().unreplace(this);
        }
    }

    @Override ResolveStatus resolveStatus() {
        return ResolveStatus.UNRESOLVED;
    }

    // when you graft a substitution into another object,
    // you have to prefix it with the location in that object
    // where you grafted it; but save prefixLength so
    // system property and env variable lookups don't get
    // broken.
    @Override com.addthis.codec.embedded.com.typesafe.config.impl.ConfigReference relativized(Path prefix) {
        com.addthis.codec.embedded.com.typesafe.config.impl.SubstitutionExpression newExpr = expr.changePath(expr.path().prepend(prefix));
        return new com.addthis.codec.embedded.com.typesafe.config.impl.ConfigReference(origin(), newExpr, prefixLength + prefix.length());
    }

    @Override
    protected boolean canEqual(Object other) {
        return other instanceof com.addthis.codec.embedded.com.typesafe.config.impl.ConfigReference;
    }

    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof com.addthis.codec.embedded.com.typesafe.config.impl.ConfigReference) {
            return canEqual(other) && this.expr.equals(((com.addthis.codec.embedded.com.typesafe.config.impl.ConfigReference) other).expr);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // note that "origin" is deliberately NOT part of equality
        return expr.hashCode();
    }

    @Override
    protected void render(StringBuilder sb, int indent, boolean atRoot, ConfigRenderOptions options) {
        sb.append(expr.toString());
    }

    com.addthis.codec.embedded.com.typesafe.config.impl.SubstitutionExpression expression() {
        return expr;
    }
}
