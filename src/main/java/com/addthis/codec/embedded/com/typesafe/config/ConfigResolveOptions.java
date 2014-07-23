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
package com.addthis.codec.embedded.com.typesafe.config;

/**
 * A set of options related to resolving substitutions. Substitutions use the
 * <code>${foo.bar}</code> syntax and are documented in the <a
 * href="https://github.com/typesafehub/config/blob/master/HOCON.md">HOCON</a>
 * spec.
 * <p>
 * Typically this class would be used with the method
 * {@link com.addthis.codec.embedded.com.typesafe.config.Config#resolve(com.addthis.codec.embedded.com.typesafe.config.ConfigResolveOptions)}.
 * <p>
 * This object is immutable, so the "setters" return a new object.
 * <p>
 * Here is an example of creating a custom {@code ConfigResolveOptions}:
 *
 * <pre>
 *     ConfigResolveOptions options = ConfigResolveOptions.defaults()
 *         .setUseSystemEnvironment(false)
 * </pre>
 * <p>
 * In addition to {@link com.addthis.codec.embedded.com.typesafe.config.ConfigResolveOptions#defaults}, there's a prebuilt
 * {@link com.addthis.codec.embedded.com.typesafe.config.ConfigResolveOptions#noSystem} which avoids looking at any system
 * environment variables or other external system information. (Right now,
 * environment variables are the only example.)
 */
public final class ConfigResolveOptions {
    private final boolean useSystemEnvironment;
    private final boolean allowUnresolved;

    private ConfigResolveOptions(boolean useSystemEnvironment, boolean allowUnresolved) {
        this.useSystemEnvironment = useSystemEnvironment;
        this.allowUnresolved = allowUnresolved;
    }

    /**
     * Returns the default resolve options. By default the system environment
     * will be used and unresolved substitutions are not allowed.
     *
     * @return the default resolve options
     */
    public static com.addthis.codec.embedded.com.typesafe.config.ConfigResolveOptions defaults() {
        return new com.addthis.codec.embedded.com.typesafe.config.ConfigResolveOptions(true, false);
    }

    /**
     * Returns resolve options that disable any reference to "system" data
     * (currently, this means environment variables).
     *
     * @return the resolve options with env variables disabled
     */
    public static com.addthis.codec.embedded.com.typesafe.config.ConfigResolveOptions noSystem() {
        return defaults().setUseSystemEnvironment(false);
    }

    /**
     * Returns options with use of environment variables set to the given value.
     *
     * @param value
     *            true to resolve substitutions falling back to environment
     *            variables.
     * @return options with requested setting for use of environment variables
     */
    public com.addthis.codec.embedded.com.typesafe.config.ConfigResolveOptions setUseSystemEnvironment(boolean value) {
        return new com.addthis.codec.embedded.com.typesafe.config.ConfigResolveOptions(value, allowUnresolved);
    }

    /**
     * Returns whether the options enable use of system environment variables.
     * This method is mostly used by the config lib internally, not by
     * applications.
     *
     * @return true if environment variables should be used
     */
    public boolean getUseSystemEnvironment() {
        return useSystemEnvironment;
    }

    /**
     * Returns options with "allow unresolved" set to the given value. By
     * default, unresolved substitutions are an error. If unresolved
     * substitutions are allowed, then a future attempt to use the unresolved
     * value may fail, but {@link Config#resolve(com.addthis.codec.embedded.com.typesafe.config.ConfigResolveOptions)} itself
     * will now throw.
     * 
     * @param value
     *            true to silently ignore unresolved substitutions.
     * @return options with requested setting for whether to allow substitutions
     * @since 1.2.0
     */
    public com.addthis.codec.embedded.com.typesafe.config.ConfigResolveOptions setAllowUnresolved(boolean value) {
        return new com.addthis.codec.embedded.com.typesafe.config.ConfigResolveOptions(useSystemEnvironment, value);
    }

    /**
     * Returns whether the options allow unresolved substitutions. This method
     * is mostly used by the config lib internally, not by applications.
     * 
     * @return true if unresolved substitutions are allowed
     * @since 1.2.0
     */
    public boolean getAllowUnresolved() {
        return allowUnresolved;
    }
}
