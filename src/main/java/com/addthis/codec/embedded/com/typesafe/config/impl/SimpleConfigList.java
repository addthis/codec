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

import java.io.ObjectStreamException;
import java.io.Serializable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import com.addthis.codec.embedded.com.typesafe.config.ConfigException;
import com.addthis.codec.embedded.com.typesafe.config.ConfigList;
import com.addthis.codec.embedded.com.typesafe.config.ConfigOrigin;
import com.addthis.codec.embedded.com.typesafe.config.ConfigRenderOptions;
import com.addthis.codec.embedded.com.typesafe.config.ConfigValue;
import com.addthis.codec.embedded.com.typesafe.config.ConfigValueType;

final class SimpleConfigList extends AbstractConfigValue implements ConfigList, Serializable {

    private static final long serialVersionUID = 2L;

    final private List<AbstractConfigValue> value;
    final private boolean resolved;

    SimpleConfigList(ConfigOrigin origin, List<AbstractConfigValue> value) {
        this(origin, value, ResolveStatus
                .fromValues(value));
    }

    SimpleConfigList(ConfigOrigin origin, List<AbstractConfigValue> value,
            ResolveStatus status) {
        super(origin);
        this.value = value;
        this.resolved = status == ResolveStatus.RESOLVED;

        // kind of an expensive debug check (makes this constructor pointless)
        if (status != ResolveStatus.fromValues(value))
            throw new ConfigException.BugOrBroken(
                    "SimpleConfigList created with wrong resolve status: " + this);
    }

    @Override
    public ConfigValueType valueType() {
        return ConfigValueType.LIST;
    }

    @Override
    public List<Object> unwrapped() {
        List<Object> list = new ArrayList<Object>();
        for (AbstractConfigValue v : value) {
            list.add(v.unwrapped());
        }
        return list;
    }

    @Override ResolveStatus resolveStatus() {
        return ResolveStatus.fromBoolean(resolved);
    }

    private com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigList modify(NoExceptionsModifier modifier, ResolveStatus newResolveStatus) {
        try {
            return modifyMayThrow(modifier, newResolveStatus);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ConfigException.BugOrBroken("unexpected checked exception", e);
        }
    }

    private com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigList modifyMayThrow(Modifier modifier, ResolveStatus newResolveStatus)
            throws Exception {
        // lazy-create for optimization
        List<AbstractConfigValue> changed = null;
        int i = 0;
        for (AbstractConfigValue v : value) {
            AbstractConfigValue modified = modifier.modifyChildMayThrow(null /* key */, v);

            // lazy-create the new list if required
            if (changed == null && modified != v) {
                changed = new ArrayList<AbstractConfigValue>();
                for (int j = 0; j < i; ++j) {
                    changed.add(value.get(j));
                }
            }

            // once the new list is created, all elements
            // have to go in it. if modifyChild returned
            // null, we drop that element.
            if (changed != null && modified != null) {
                changed.add(modified);
            }

            i += 1;
        }

        if (changed != null) {
            if (newResolveStatus != null) {
                return new com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigList(origin(), changed, newResolveStatus);
            } else {
                return new com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigList(origin(), changed);
            }
        } else {
            return this;
        }
    }

    @Override
    com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigList resolveSubstitutions(final com.addthis.codec.embedded.com.typesafe.config.impl.ResolveContext context) throws
                                                                                                                          AbstractConfigValue.NotPossibleToResolve {
        if (resolved)
            return this;

        if (context.isRestrictedToChild()) {
            // if a list restricts to a child path, then it has no child paths,
            // so nothing to do.
            return this;
        } else {
            try {
                return modifyMayThrow(new Modifier() {
                    @Override
                    public AbstractConfigValue modifyChildMayThrow(String key, AbstractConfigValue v)
                            throws AbstractConfigValue.NotPossibleToResolve {
                        return context.resolve(v);
                    }

                }, null /* don't force resolve status -- could be allowing unresolved */);
            } catch (AbstractConfigValue.NotPossibleToResolve e) {
                throw e;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new ConfigException.BugOrBroken("unexpected checked exception", e);
            }
        }
    }

    @Override com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigList relativized(final Path prefix) {
        return modify(new NoExceptionsModifier() {
            @Override
            public AbstractConfigValue modifyChild(String key, AbstractConfigValue v) {
                return v.relativized(prefix);
            }

        }, resolveStatus());
    }

    @Override
    protected boolean canEqual(Object other) {
        return other instanceof com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigList;
    }

    @Override
    public boolean equals(Object other) {
        // note that "origin" is deliberately NOT part of equality
        if (other instanceof com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigList) {
            // optimization to avoid unwrapped() for two ConfigList
            return canEqual(other) && value.equals(((com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigList) other).value);
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        // note that "origin" is deliberately NOT part of equality
        return value.hashCode();
    }

    @Override
    protected void render(StringBuilder sb, int indent, boolean atRoot, ConfigRenderOptions options) {
        if (value.isEmpty()) {
            sb.append("[]");
        } else {
            sb.append("[");
            if (options.getFormatted())
                sb.append('\n');
            for (AbstractConfigValue v : value) {
                if (options.getOriginComments()) {
                    indent(sb, indent + 1, options);
                    sb.append("# ");
                    sb.append(v.origin().description());
                    sb.append("\n");
                }
                if (options.getComments()) {
                    for (String comment : v.origin().comments()) {
                        indent(sb, indent + 1, options);
                        sb.append("# ");
                        sb.append(comment);
                        sb.append("\n");
                    }
                }
                indent(sb, indent + 1, options);

                v.render(sb, indent + 1, atRoot, options);
                sb.append(",");
                if (options.getFormatted())
                    sb.append('\n');
            }
            sb.setLength(sb.length() - 1); // chop or newline
            if (options.getFormatted()) {
                sb.setLength(sb.length() - 1); // also chop comma
                sb.append('\n');
                indent(sb, indent, options);
            }
            sb.append("]");
        }
    }

    @Override
    public boolean contains(Object o) {
        return value.contains(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return value.containsAll(c);
    }

    @Override
    public AbstractConfigValue get(int index) {
        return value.get(index);
    }

    @Override
    public int indexOf(Object o) {
        return value.indexOf(o);
    }

    @Override
    public boolean isEmpty() {
        return value.isEmpty();
    }

    @Override
    public Iterator<ConfigValue> iterator() {
        final Iterator<AbstractConfigValue> i = value.iterator();

        return new Iterator<ConfigValue>() {
            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public ConfigValue next() {
                return i.next();
            }

            @Override
            public void remove() {
                throw weAreImmutable("iterator().remove");
            }
        };
    }

    @Override
    public int lastIndexOf(Object o) {
        return value.lastIndexOf(o);
    }

    private static ListIterator<ConfigValue> wrapListIterator(
            final ListIterator<AbstractConfigValue> i) {
        return new ListIterator<ConfigValue>() {
            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public ConfigValue next() {
                return i.next();
            }

            @Override
            public void remove() {
                throw weAreImmutable("listIterator().remove");
            }

            @Override
            public void add(ConfigValue arg0) {
                throw weAreImmutable("listIterator().add");
            }

            @Override
            public boolean hasPrevious() {
                return i.hasPrevious();
            }

            @Override
            public int nextIndex() {
                return i.nextIndex();
            }

            @Override
            public ConfigValue previous() {
                return i.previous();
            }

            @Override
            public int previousIndex() {
                return i.previousIndex();
            }

            @Override
            public void set(ConfigValue arg0) {
                throw weAreImmutable("listIterator().set");
            }
        };
    }

    @Override
    public ListIterator<ConfigValue> listIterator() {
        return wrapListIterator(value.listIterator());
    }

    @Override
    public ListIterator<ConfigValue> listIterator(int index) {
        return wrapListIterator(value.listIterator(index));
    }

    @Override
    public int size() {
        return value.size();
    }

    @Override
    public List<ConfigValue> subList(int fromIndex, int toIndex) {
        List<ConfigValue> list = new ArrayList<ConfigValue>();
        // yay bloat caused by lack of type variance
        for (AbstractConfigValue v : value.subList(fromIndex, toIndex)) {
            list.add(v);
        }
        return list;
    }

    @Override
    public Object[] toArray() {
        return value.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return value.toArray(a);
    }

    private static UnsupportedOperationException weAreImmutable(String method) {
        return new UnsupportedOperationException(
                "ConfigList is immutable, you can't call List.'" + method + "'");
    }

    @Override
    public boolean add(ConfigValue e) {
        throw weAreImmutable("add");
    }

    @Override
    public void add(int index, ConfigValue element) {
        throw weAreImmutable("add");
    }

    @Override
    public boolean addAll(Collection<? extends ConfigValue> c) {
        throw weAreImmutable("addAll");
    }

    @Override
    public boolean addAll(int index, Collection<? extends ConfigValue> c) {
        throw weAreImmutable("addAll");
    }

    @Override
    public void clear() {
        throw weAreImmutable("clear");
    }

    @Override
    public boolean remove(Object o) {
        throw weAreImmutable("remove");
    }

    @Override
    public ConfigValue remove(int index) {
        throw weAreImmutable("remove");
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw weAreImmutable("removeAll");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw weAreImmutable("retainAll");
    }

    @Override
    public ConfigValue set(int index, ConfigValue element) {
        throw weAreImmutable("set");
    }

    @Override
    protected com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigList newCopy(ConfigOrigin newOrigin) {
        return new com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigList(newOrigin, value);
    }

    final com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigList concatenate(com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigList other) {
        ConfigOrigin combinedOrigin = com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigOrigin.mergeOrigins(origin(), other.origin());
        List<AbstractConfigValue> combined = new ArrayList<AbstractConfigValue>(value.size()
                + other.value.size());
        combined.addAll(value);
        combined.addAll(other.value);
        return new com.addthis.codec.embedded.com.typesafe.config.impl.SimpleConfigList(combinedOrigin, combined);
    }

    // serialization all goes through SerializedConfigValue
    private Object writeReplace() throws ObjectStreamException {
        return new SerializedConfigValue(this);
    }
}
