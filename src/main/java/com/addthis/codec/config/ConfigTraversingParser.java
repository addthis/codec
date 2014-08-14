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
package com.addthis.codec.config;

import java.io.IOException;
import java.io.OutputStream;

import java.math.BigDecimal;
import java.math.BigInteger;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonStreamContext;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.base.ParserMinimalBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.typesafe.config.ConfigList;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigOrigin;
import com.typesafe.config.ConfigValue;
import com.typesafe.config.ConfigValueType;

import static com.typesafe.config.ConfigValueType.LIST;
import static com.typesafe.config.ConfigValueType.OBJECT;

public class ConfigTraversingParser extends ParserMinimalBase {
    /*
    /**********************************************************
    /* Configuration
    /**********************************************************
     */

    protected ObjectCodec _objectCodec;

    /**
     * Traversal context within tree
     */
    protected ConfigNodeCursor _nodeCursor;

    /*
    /**********************************************************
    /* State
    /**********************************************************
     */

    /**
     * Sometimes parser needs to buffer a single look-ahead token; if so,
     * it'll be stored here. This is currently used for handling
     */
    protected JsonToken _nextToken;

    /**
     * Flag needed to handle recursion into contents of child
     * Array/Object nodes.
     */
    protected boolean _startContainer;

    /**
     * Flag that indicates whether parser is closed or not. Gets
     * set when parser is either closed by explicit call
     * ({@link #close}) or when end-of-input is reached.
     */
    protected boolean _closed;

    protected ConfigValue currentConfig;

    /*
    /**********************************************************
    /* Life-cycle
    /**********************************************************
     */

    public ConfigTraversingParser(ConfigValue n) { this(n, null); }

    public ConfigTraversingParser(ConfigValue n, ObjectCodec codec) {
        super(0);
        _objectCodec = codec;
        currentConfig = n;
        if (n.valueType() == LIST) {
            _nextToken = JsonToken.START_ARRAY;
            _nodeCursor = new ConfigNodeCursor.Array((ConfigList) n, null);
        } else if (n.valueType() == OBJECT) {
            _nextToken = JsonToken.START_OBJECT;
            _nodeCursor = new ConfigNodeCursor.Object((ConfigObject) n, null);
        } else { // value node
            _nodeCursor = new ConfigNodeCursor.RootValue(n, null);
        }
    }

    @Override
    public void setCodec(ObjectCodec c) {
        _objectCodec = c;
    }

    @Override
    public ObjectCodec getCodec() {
        return _objectCodec;
    }

    @Override
    public Version version() {
        return Version.unknownVersion();
    }
    
    /*
    /**********************************************************
    /* Closeable implementation
    /**********************************************************
     */

    @Override
    public void close() {
        if (!_closed) {
            _closed = true;
            _nodeCursor = null;
            _currToken = null;
        }
    }

    /*
    /**********************************************************
    /* Public API, traversal
    /**********************************************************
     */

    public ConfigValue currentConfig() {
        if (_nextToken != null) {
            // haven't read or started reading root value yet
            return null;
        }
        return currentConfig;
    }

    @Override
    public JsonToken nextToken() throws IOException, JsonParseException {
        if (_nextToken != null) {
            _currToken = _nextToken;
            _nextToken = null;
            return _currToken;
        }
        // are we to descend to a container child?
        if (_startContainer) {
            _startContainer = false;
            // minor optimization: empty containers can be skipped
            if (!_nodeCursor.currentHasChildren()) {
                _currToken = (_currToken == JsonToken.START_OBJECT) ?
                    JsonToken.END_OBJECT : JsonToken.END_ARRAY;
                return _currToken;
            }
            _nodeCursor = _nodeCursor.iterateChildren();
            _currToken = _nodeCursor.nextToken();
            if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
                _startContainer = true;
            }
            currentConfig = currentNode();
            return _currToken;
        }
        // No more content?
        if (_nodeCursor == null) {
            _closed = true; // if not already set
            currentConfig = null;
            return null;
        }
        // Otherwise, next entry from current cursor
        _currToken = _nodeCursor.nextToken();
        if (_currToken != null) {
            currentConfig = currentNode();
            if (_currToken == JsonToken.START_OBJECT || _currToken == JsonToken.START_ARRAY) {
                _startContainer = true;
            }
            return _currToken;
        }
        // null means no more children; need to return end marker
        _currToken = _nodeCursor.endToken();
        _nodeCursor = _nodeCursor.getParent();
        currentConfig = currentNode();
        return _currToken;
    }
    
    // default works well here:
    //public JsonToken nextValue() throws IOException, JsonParseException

    @Override
    public JsonParser skipChildren() throws IOException, JsonParseException {
        if (_currToken == JsonToken.START_OBJECT) {
            _startContainer = false;
            _currToken = JsonToken.END_OBJECT;
        } else if (_currToken == JsonToken.START_ARRAY) {
            _startContainer = false;
            _currToken = JsonToken.END_ARRAY;
        }
        return this;
    }

    @Override
    public boolean isClosed() {
        return _closed;
    }

    /*
    /**********************************************************
    /* Public API, token accessors
    /**********************************************************
     */

    @Override
    public String getCurrentName() {
        if (_nodeCursor == null) {
            return null;
        } else {
            return _nodeCursor.getCurrentName();
        }
    }

    @Override
    public void overrideCurrentName(String name) {
        if (_nodeCursor != null) {
            _nodeCursor.overrideCurrentName(name);
        }
    }
    
    @Override
    public JsonStreamContext getParsingContext() {
        return _nodeCursor;
    }

    @Override
    public JsonLocation getTokenLocation() {
        ConfigValue current = currentConfig();
        if (current == null) {
            return JsonLocation.NA;
        }
        ConfigOrigin nodeOrigin = current.origin();
        return new JsonLocation(current, -1, nodeOrigin.lineNumber(), -1);
    }

    @Override
    public JsonLocation getCurrentLocation() {
        return getTokenLocation();
    }

    /*
    /**********************************************************
    /* Public API, access to textual content
    /**********************************************************
     */

    @Override
    public String getText() {
        if (_closed) {
            return null;
        }
        // need to separate handling a bit...
        switch (_currToken) {
        case FIELD_NAME:
            return _nodeCursor.getCurrentName();
        case VALUE_STRING:
            return (String) currentNode().unwrapped();
        case VALUE_NUMBER_INT:
        case VALUE_NUMBER_FLOAT:
            return String.valueOf(currentNode().unwrapped());
        case VALUE_EMBEDDED_OBJECT:
            // not supported and shouldn't be called, but emulating the 'null' result by not throwing
        default:
            if (_currToken == null) {
                return null;
            } else {
                return _currToken.asString();
            }
        }
    }

    @Override
    public char[] getTextCharacters() throws IOException, JsonParseException {
        return getText().toCharArray();
    }

    @Override
    public int getTextLength() throws IOException, JsonParseException {
        return getText().length();
    }

    @Override
    public int getTextOffset() throws IOException, JsonParseException {
        return 0;
    }

    @Override
    public boolean hasTextCharacters() {
        // generally we do not have efficient access as char[], hence:
        return false;
    }
    
    /*
    /**********************************************************
    /* Public API, typed non-text access
    /**********************************************************
     */

    //public byte getByteValue() throws IOException, JsonParseException

    @Override
    public NumberType getNumberType() throws IOException, JsonParseException {
        JsonNode n = currentNumericNode();
        return (n == null) ? null : n.numberType();
    }

    @Override
    public BigInteger getBigIntegerValue() throws IOException, JsonParseException {
        return currentNumericNode().bigIntegerValue();
    }

    @Override
    public BigDecimal getDecimalValue() throws IOException, JsonParseException {
        return currentNumericNode().decimalValue();
    }

    @Override
    public double getDoubleValue() throws IOException, JsonParseException {
        return currentNumericNode().doubleValue();
    }

    @Override
    public float getFloatValue() throws IOException, JsonParseException {
        return (float) currentNumericNode().doubleValue();
    }

    @Override
    public long getLongValue() throws IOException, JsonParseException {
        return currentNumericNode().longValue();
    }

    @Override
    public int getIntValue() throws IOException, JsonParseException {
        JsonNode numericNode = currentNumericNode();
        if (numericNode.canConvertToInt()) {
            return currentNumericNode().intValue();
        }
        throw _constructError("Numeric value ("+getText()+") out of range of Java short");
    }

    @Override
    public Number getNumberValue() throws IOException, JsonParseException {
        return currentNumericNode().numberValue();
    }

    @Override
    public Object getEmbeddedObject() {
        return null;
    }

    /*
    /**********************************************************
    /* Public API, typed binary (base64) access
    /**********************************************************
     */

    @Override
    public byte[] getBinaryValue(Base64Variant b64variant) throws IOException, JsonParseException {
        // otherwise return null to mark we have no binary content
        return null;
    }


    @Override
    public int readBinaryValue(Base64Variant b64variant, OutputStream out) throws IOException, JsonParseException {
        return 0;
    }

    /*
    /**********************************************************
    /* Internal methods
    /**********************************************************
     */

    protected ConfigValue currentNode() {
        if (_closed || _nodeCursor == null) {
            return null;
        }
        return _nodeCursor.currentNode();
    }

    protected JsonNode currentNumericNode() throws JsonParseException {
        ConfigValue configValue = currentNode();
        if ((configValue == null) || (configValue.valueType() != ConfigValueType.NUMBER)) {
            JsonToken t = (configValue == null) ? null : ConfigNodeCursor.forConfigValue(configValue);
            throw _constructError("Current token ("+t+") not numeric, can not use numeric value accessors");
        }
        Number value = (Number) configValue.unwrapped();
        if (value instanceof Double) {
            return JsonNodeFactory.instance.numberNode((Double) value);
        }
        if (value instanceof Long) {
            return JsonNodeFactory.instance.numberNode((Long) value);
        }
        if (value instanceof Integer) {
            return JsonNodeFactory.instance.numberNode((Integer) value);
        }
        // only possible if Config has since added more numeric types
        throw _constructError(value.getClass() + " is not a supported numeric config type");
    }

    @Override
    protected void _handleEOF() throws JsonParseException {
        _throwInternal(); // should never get called
    }
}
