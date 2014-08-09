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
package com.addthis.codec.jackson.tree;

import javax.annotation.Nullable;

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import static com.typesafe.config.ConfigValueType.LIST;
import static com.typesafe.config.ConfigValueType.OBJECT;

abstract class ConfigNodeCursor extends JsonStreamContext {
    /**
     * Parent cursor of this cursor, if any; null for root
     * cursors.
     */
    protected final ConfigNodeCursor _parent;

    /**
     * Current field name
     */
    @Nullable protected String _currentName;

    public ConfigNodeCursor(int contextType, ConfigNodeCursor p) {
        super();
        _type = contextType;
        _index = -1;
        _parent = p;
    }

    /*
    /**********************************************************
    /* JsonStreamContext impl
    /**********************************************************
     */

    // note: co-variant return type
    @Override
    public final ConfigNodeCursor getParent() { return _parent; }

    @Override
    @Nullable
    public final String getCurrentName() {
        return _currentName;
    }

    /**
     * @since 2.0
     */
    public void overrideCurrentName(String name) {
        _currentName = name;
    }
    
    /*
    /**********************************************************
    /* Extended API
    /**********************************************************
     */

    public abstract JsonToken nextToken();

    public abstract JsonToken nextValue();

    public abstract JsonToken endToken();

    public abstract ConfigValue currentNode();

    public abstract boolean currentHasChildren();

    /**
     * Method called to create a new context for iterating all
     * contents of the current structured value (JSON array or object)
     */
    public final ConfigNodeCursor iterateChildren() {
        ConfigValue n = currentNode();
        if (n == null) throw new IllegalStateException("No current node");
        if (n.valueType() == LIST) { // false since we have already returned START_ARRAY
            return new Array((ConfigList) n, this);
        }
        if (n.valueType() == OBJECT) {
            return new Object((ConfigObject) n, this);
        }
        throw new IllegalStateException("Current node of type " + n.getClass().getName());
    }

    static JsonToken forConfigValue(ConfigValue configValue) {
        ConfigValueType valueType = configValue.valueType();
        switch (valueType) {
            case NUMBER:
                if (configValue.unwrapped() instanceof Double) {
                    return JsonToken.VALUE_NUMBER_FLOAT;
                } else {
                    return JsonToken.VALUE_NUMBER_INT;
                }
            case BOOLEAN:
                if (configValue.unwrapped().equals(Boolean.TRUE)) {
                    return JsonToken.VALUE_TRUE;
                } else {
                    return JsonToken.VALUE_FALSE;
                }
            case NULL:
                return JsonToken.VALUE_NULL;
            case STRING:
                return JsonToken.VALUE_STRING;
            case OBJECT:
                return JsonToken.START_OBJECT;
            case LIST:
                return JsonToken.START_ARRAY;
            default:
                // not possible unless the set of enums changes on us later
                throw new IllegalArgumentException(valueType.name() + " is not a supported ConfigValueType");
        }
    }

    /*
    /**********************************************************
    /* Concrete implementations
    /**********************************************************
     */

    /**
     * Context matching root-level value nodes (i.e. anything other
     * than JSON Object and Array).
     * Note that context is NOT created for leaf values.
     */
    protected static final class RootValue extends ConfigNodeCursor {
        @Nullable private ConfigValue _node;

        private boolean _done = false;

        public RootValue(ConfigValue n, ConfigNodeCursor p) {
            super(JsonStreamContext.TYPE_ROOT, p);
            _node = n;
        }

        @Override
        public void overrideCurrentName(String name) {}

        @Override
        @Nullable
        public JsonToken nextToken() {
            if (!_done) {
                _done = true;
                return forConfigValue(_node);
            }
            _node = null;
            return null;
        }

        @Override
        @Nullable
        public JsonToken nextValue() { return nextToken(); }

        @Override
        @Nullable
        public JsonToken endToken() { return null; }

        @Override
        public ConfigValue currentNode() { return _node; }

        @Override
        public boolean currentHasChildren() { return false; }
    }

    /**
     * Cursor used for traversing non-empty JSON Array nodes
     */
    protected static final class Array extends ConfigNodeCursor {
        private final Iterator<ConfigValue> _contents;

        @Nullable private ConfigValue _currentNode;

        public Array(ConfigList n, ConfigNodeCursor p) {
            super(JsonStreamContext.TYPE_ARRAY, p);
            _contents = n.iterator();
        }

        @Override
        @Nullable
        public JsonToken nextToken() {
            if (!_contents.hasNext()) {
                _currentNode = null;
                return null;
            }
            _currentNode = _contents.next();
            return forConfigValue(_currentNode);
        }

        @Override
        public JsonToken nextValue() { return nextToken(); }

        @Override
        public JsonToken endToken() { return JsonToken.END_ARRAY; }

        @Override
        public ConfigValue currentNode() { return _currentNode; }

        @Override
        public boolean currentHasChildren() {
            // note: ONLY to be called for container nodes
            ConfigValue currentValue = currentNode();
            if (currentValue.valueType() == ConfigValueType.LIST) {
                return !((ConfigList) currentValue).isEmpty();
            } else {
                return !((ConfigObject) currentValue).isEmpty();
            }
        }
    }

    /**
     * Cursor used for traversing non-empty JSON Object nodes
     */
    protected static final class Object extends ConfigNodeCursor {
        private final Iterator<Map.Entry<String, ConfigValue>> _contents;

        @Nullable private Map.Entry<String, ConfigValue> _current;
        private boolean _needEntry;

        public Object(ConfigObject n, ConfigNodeCursor p) {
            super(JsonStreamContext.TYPE_OBJECT, p);
            _contents = n.entrySet().iterator();
            _needEntry = true;
        }

        @Override
        @Nullable
        public JsonToken nextToken() {
            // Need a new entry?
            if (_needEntry) {
                if (!_contents.hasNext()) {
                    _currentName = null;
                    _current = null;
                    return null;
                }
                _needEntry = false;
                _current = _contents.next();
                _currentName = (_current == null) ? null : _current.getKey();
                return JsonToken.FIELD_NAME;
            }
            _needEntry = true;
            return forConfigValue(_current.getValue());
        }

        @Override
        public JsonToken nextValue() {
            JsonToken t = nextToken();
            if (t == JsonToken.FIELD_NAME) {
                t = nextToken();
            }
            return t;
        }

        @Override
        public JsonToken endToken() { return JsonToken.END_OBJECT; }

        @Override
        public ConfigValue currentNode() {
            if (_current == null) {
                return null;
            } else {
                return _current.getValue();
            }
        }

        @Override
        public boolean currentHasChildren() {
            // note: ONLY to be called for container nodes
            ConfigValue currentValue = currentNode();
            if (currentValue.valueType() == ConfigValueType.LIST) {
                return !((ConfigList) currentValue).isEmpty();
            } else {
                return !((ConfigObject) currentValue).isEmpty();
            }
        }
    }
}
