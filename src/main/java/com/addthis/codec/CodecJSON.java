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
package com.addthis.codec;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.addthis.basis.util.Bytes;

import com.addthis.maljson.JSONArray;
import com.addthis.maljson.JSONException;
import com.addthis.maljson.JSONObject;
import com.addthis.maljson.LineNumberInfo;

public class CodecJSON extends Codec {

    public static interface JSONCodable extends Codable {

        public JSONObject toJSONObject() throws Exception;

        public void fromJSONObject(JSONObject jo) throws Exception;
    }

    public CodecJSON() {
    }

    @Override
    public byte[] encode(Object obj) throws Exception {
        return Bytes.toBytes(encodeString(obj));
    }

    @Override
    public CodableStatistics statistics(Object obj) {
        throw new UnsupportedOperationException();
    }


    @Override
    public <T> T decode(T shell, byte data[]) throws CodecExceptionLineNumber, JSONException {
        return decodeString(shell, Bytes.toString(data));
    }

    public <T> T decode(T shell, byte data[], List<CodecExceptionLineNumber> warnings) throws Exception {
        return decodeString(shell, Bytes.toString(data), warnings);
    }

    @Override
    public boolean storesNull(byte data[]) {
        throw new UnsupportedOperationException();
    }

    public static JSONObject encodeJSON(Object object) throws Exception {
        return (JSONObject) encodeObject(object);
    }

    public static String encodeString(Object object) {
        return encodeString(object, 0);
    }

    public static String encodeString(Object object, int nest) {
        try {
            Object ret = encodeObject(object);
            if (ret instanceof JSONObject) {
                return nest > 0 ? ((JSONObject) ret).toString(nest) : ((JSONObject) ret).toString();
            } else {
                return ret.toString();
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Object encodeArray(Object value) throws Exception {
        JSONArray arr = new JSONArray();
        int len = Array.getLength(value);
        for (int i = 0; i < len; i++) {
            arr.put(encodeObject(Array.get(value, i)));
        }
        return arr;
    }

    private static Object encodeObject(Object object) throws Exception {
        if (object == null) {
            return JSONObject.NULL;
        }
        if (object.getClass().isArray()) {
            return encodeArray(object);
        }
        if (object instanceof JSONCodable) {
            return ((JSONCodable) object).toJSONObject();
        }
        JSONObject obj = null;
        boolean lock = object instanceof Codec.ConcurrentCodable;
        if (lock && !((Codec.ConcurrentCodable) object).encodeLock()) {
            throw new Exception("Unable to acquire encoding lock on " + object);
        }
        try {
            if (object instanceof SuperCodable) {
                ((SuperCodable) object).preEncode();
            }
            CodableClassInfo classInfo = getClassFieldMap(object.getClass());
            if (classInfo.size() == 0 && !(object instanceof Codable)) {
                return object;
            }
            obj = new JSONObject();
            String altType = classInfo.getClassName(object);
            if (altType != null) {
                obj.put(classInfo.getClassField(), altType);
            }
            for (Iterator<CodableFieldInfo> fields = classInfo.values().iterator(); fields.hasNext();) {
                CodableFieldInfo field = fields.next();
                Object value = field.get(object);
                if (value == null || value == JSONObject.NULL || field.isReadOnly()) {
                    continue;
                }
                if (CodecJSON.JSONCodable.class.isAssignableFrom(field.getType())) {
                    value = ((CodecJSON.JSONCodable) value).toJSONObject();
                    obj.put(field.getName(), value);
                } else if (field.isArray()) {
                    obj.put(field.getName(), encodeArray(value));
                } else if (field.isMap()) {
                    Map<?, ?> map = (Map<?, ?>) value;
                    JSONObject jmap = new JSONObject();
                    for (Entry<?, ?> entry : map.entrySet()) {
                        Object mval = entry.getValue();
                        // TODO fails with null keys
                        jmap.put(entry.getKey().toString(), encodeObject(mval));
                    }
                    obj.put(field.getName(), jmap);
                } else if (field.isCollection()) {
                    JSONArray jarr = new JSONArray();
                    for (Iterator<?> iter = ((Collection<?>) value).iterator(); iter.hasNext();) {
                        jarr.put(encodeObject(iter.next()));
                    }
                    obj.put(field.getName(), jarr);
                } else if (field.isCodable()) {
                    obj.put(field.getName(), encodeObject(value));
                } else if (field.isEnum()) {
                    obj.put(field.getName(), value.toString());
                } else if (field.isNative()) {
                    obj.put(field.getName(), value);
                } else {
                    System.out.println("unmatched field '" + field.getName() + "' = " + field);
                }
            }
        } finally {
            if (lock) {
                ((Codec.ConcurrentCodable) object).encodeUnlock();
            }
        }
        return obj;
    }

    public static <T> T decodeString(T object, String json)
            throws CodecExceptionLineNumber, JSONException {

        return decodeJSON(object, new JSONObject(json));
    }

    public static <T> T decodeString(T object, String json, List<CodecExceptionLineNumber> warnings)
            throws CodecExceptionLineNumber, JSONException {

        JSONObject jsonObj = new JSONObject(json);
        return decodeJSONInternal(getClassFieldMap(object.getClass()), object, jsonObj, warnings);
    }

    public static <T> T decodeArray(Class<T> type, Object object)
            throws CodecExceptionLineNumber, JSONException {

        return decodeArrayInternal(type, object, LineNumberInfo.MissingInfo, null);
    }

    public static <T> T decodeArray(Class<T> type, Object object, List<CodecExceptionLineNumber> warnings)
            throws CodecExceptionLineNumber, JSONException {

        return decodeArrayInternal(type, object, LineNumberInfo.MissingInfo, warnings);
    }

    private static <T> T decodeArrayInternal(Class<T> type, Object object, LineNumberInfo info, List<CodecExceptionLineNumber> warnings)
            throws CodecExceptionLineNumber, JSONException {

        if (object == null || object == JSONObject.NULL) {
            return null;
        }
        if (object.getClass() != JSONArray.class) {
            throw new CodecExceptionLineNumber(object.toString() + " not an instance of JSONArray for class " + type, info);
        }
        JSONArray array = (JSONArray) object;
        T value = (T) Array.newInstance(type, array.length());
        if (type == byte.class || type == Byte.class) {
            for (int i = 0; i < array.length(); i++) {
                Array.set(value, i, (byte) array.getInt(i));
            }
        } else {
            for (int i = 0; i < array.length(); i++) {
                Object element = decodeObjectInternal(type, array.opt(i), array.getLineNumber(i), warnings);
                try {
                    Array.set(value, i, element);
                } catch (IllegalArgumentException ex) {
                    throw new CodecExceptionLineNumber("Element " + i + " with value " +
                        array.opt(i).toString() + " cannot be converted to " + type.toString(),
                        array.getLineNumber(i));
                }
            }
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static <T> T decodeObject(Class<T> type, Object json)
            throws CodecExceptionLineNumber, JSONException {

        return decodeObjectInternal(type, json, LineNumberInfo.MissingInfo, null);
    }

    @SuppressWarnings("unchecked")
    public static <T> T decodeObject(Class<T> type, Object json, List<CodecExceptionLineNumber> warnings)
            throws CodecExceptionLineNumber, JSONException {

        return decodeObjectInternal(type, json, LineNumberInfo.MissingInfo, warnings);
    }

    @SuppressWarnings("unchecked")
    public static <T> T decodeObjectInternal(Class<T> type, Object json,
            LineNumberInfo info, List<CodecExceptionLineNumber> warnings)
            throws CodecExceptionLineNumber, JSONException {

        if (json == null || json == JSONObject.NULL) {
            return null;
        }
        if (isNative(type) || !(json instanceof JSONObject)) {
            if (type != json.getClass()) {
                if (Number.class.isAssignableFrom(type)) {
                    Number num = (Number) json;
                    if (type == Short.class) {
                        json = new Short(num.shortValue());
                    } else if (type == Integer.class) {
                        json = new Integer(num.intValue());
                    } else if (type == Long.class) {
                        json = new Long(num.longValue());
                    } else if (type == Float.class) {
                        json = new Float(num.floatValue());
                    } else if (type == Double.class) {
                        json = new Double(num.doubleValue());
                    } else if (type == AtomicInteger.class) {
                        json = new AtomicInteger(num.intValue());
                    } else if (type == AtomicLong.class) {
                        json = new AtomicLong(num.longValue());
                    }
                } else if (type.isEnum()) {
                    /**
                     * Attempting to invoke {@link Enum#valueOf(Class, String)}
                     * fails in the downstream assignment on
                     * {@link Array#set(Object, int, Object)} so reflection is used
                     * instead.
                     */
                    try {
                        json = type.getMethod("valueOf", String.class).invoke(null, json.toString().toUpperCase());
                    } catch (InvocationTargetException ex) {
                        throw new CodecExceptionLineNumber("Could not convert the string \"" + json.toString() +
                                                           "\" to the Enum type " + type.getName(), info);
                    } catch (NoSuchMethodException ex) {
                        throw new IllegalStateException("Attempted to decode enum type", ex);
                    } catch (IllegalAccessException ex) {
                        throw new IllegalStateException("Attempted to decode enum type", ex);
                    }
                }
            }
            return (T) json;
        } else {
            JSONObject jsonObj = (JSONObject) json;

            if (info == LineNumberInfo.MissingInfo) {
                info = jsonObj.getLineNumberInfo();
            }

            CodableClassInfo classInfo = getClassFieldMap(type);
            String classField = classInfo.getClassField();
            String stype = jsonObj.optString(classField, null);
            try {
                if (stype != null) {
                    Class<?> atype = classInfo.getClass(stype);
                    classInfo = getClassFieldMap(atype);
                    type = (Class<T>) atype;
                }
            } catch (Exception ex) {
                throw new CodecExceptionLineNumber(ex, jsonObj.getValLineNumber(classField));
            }
            if (classField != null) {
                jsonObj.remove(classField);
            }
            try {
                return decodeJSONInternal(classInfo, type.newInstance(), jsonObj, warnings);
            } catch (InstantiationException ex) {
                CodecExceptionLineNumber celn = translateInstantiationException(type, info);
                throw celn;
            } catch (IllegalAccessException ex) {
                throw new CodecExceptionLineNumber("Could not access either the type or the constructor of " +
                                                   type.getName(), info);
            }
        }
    }

    private static <T> CodecExceptionLineNumber translateInstantiationException(Class<T> type, LineNumberInfo info) {
        if (type.isInterface()) {
            String msg = "Perhaps you failed to specify what type of object to create. " +
                         "Could not instantiate the interface " + type.getName() + ". ";
            return new CodecExceptionLineNumber(msg, info);
        } else if (Modifier.isAbstract(type.getModifiers())) {
            String msg = "Perhaps you failed to specify what type of object to create. " +
                         "Could not instantiate the abstract class " + type.getName() + ". ";
            return new CodecExceptionLineNumber(msg, info);
        } else {
            String msg = "Could not instantiate an instance of " + type.getName();
            return new CodecExceptionLineNumber(msg, info);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T decodeJSON(T object, JSONObject json) throws CodecExceptionLineNumber, JSONException {
        return decodeJSONInternal(getClassFieldMap(object.getClass()), object, json, null);
    }

    @SuppressWarnings("unchecked")
    public static <T> T decodeJSON(CodableClassInfo classInfo, T object, JSONObject json)
            throws CodecExceptionLineNumber, JSONException {
        return decodeJSONInternal(classInfo, object, json, null);
    }

    private static <T> T decodeJSONInternal(CodableClassInfo classInfo, T object,
            JSONObject json, List<CodecExceptionLineNumber> warnings)
            throws CodecExceptionLineNumber, JSONException {

        if (object instanceof JSONCodable) {
            try {
                ((JSONCodable) object).fromJSONObject(json);
            } catch (Exception ex) {
                throw new CodecExceptionLineNumber(ex, json.getLineNumberInfo());
            }
            return object;
        }
        java.util.Set<String> unknownFields = new HashSet<String>(json.keySet());
        for (Iterator<CodableFieldInfo> fields = classInfo.values().iterator(); fields.hasNext();) {
            CodableFieldInfo field = fields.next();
            if (field.isWriteOnly()) {
                continue;
            }
            String fieldName = field.getName();
            unknownFields.remove(fieldName);
            Class type = field.getType();
            Object value = json.opt(fieldName);
            if (value == null) {
                field.set(object, json.getLineNumberInfo(), value, LineNumberInfo.MissingInfo);
                continue;
            }
            LineNumberInfo keyInfo = json.getKeyLineNumber(fieldName);
            LineNumberInfo valInfo = json.getValLineNumber(fieldName);

            if (CodecJSON.JSONCodable.class.isAssignableFrom(type)) {
                Object oValue = value;
                try {
                    value = type.newInstance();
                } catch (InstantiationException ex) {
                    CodecExceptionLineNumber celn = translateInstantiationException(type, valInfo);
                    throw celn;
                } catch (IllegalAccessException ex) {
                    throw new CodecExceptionLineNumber("Could not access the type or the constructor of " +
                                                       type.getName(), valInfo);
                }
                try {
                    ((CodecJSON.JSONCodable) value).fromJSONObject(new JSONObject(oValue.toString()));
                } catch (Exception ex) {
                    throw new CodecExceptionLineNumber(ex, valInfo);
                }
            } else if (field.isArray()) {
                value = decodeArrayInternal(type, value, valInfo, warnings);
            } else if (field.isNative()) {
                if (value.getClass() != type) {
                    if (value.getClass() == Integer.class || value.getClass() == int.class) {
                        // upconvert integer values to long if the field requires long
                        if (type == Long.class || type == long.class || type == AtomicLong.class) {
                            value = new Long(((Integer) value).longValue());
                        }
                        // upconvert integer values to double if the field requires double
                        if (type == Double.class || type == double.class) {
                            value = new Double(((Integer) value));
                        }
                        // downconvert integer to short if the field requires short
                        if (type == Short.class || type == short.class) {
                            value = new Short(((Integer) value).shortValue());
                        }
                    }
                    // downconvert double to float if the field requires a float
                    if ((value.getClass() == double.class || value.getClass() == Double.class) &&
                        (type == float.class || type == Float.class)) {
                        value = new Float(((Double) value));
                    }
                    // upconvert long values to double if the field requires double
                    if ((value.getClass() == long.class || value.getClass() == Long.class) &&
                        (type == double.class || type == Double.class)) {
                        value = new Double(((Integer) value));
                    }
                    // upconvert float values to double if the field requires double
                    if ((value.getClass() == float.class || value.getClass() == Float.class) &&
                        (type == double.class || type == Double.class)) {
                        value = new Double(((Float) value));
                    }
                    if (value.getClass() == String.class) {
                        try {

                            // convert String values to int if the field requires int
                            if (type == Integer.class || type == int.class || type == AtomicInteger.class) {
                                value = Integer.parseInt((String) value);
                            }

                            // convert String values to long if the field requires long
                            if (type == long.class || type == Long.class || type == AtomicLong.class) {
                                value = Long.parseLong((String) value);
                            }

                            // convert String values to double if the field requires double
                            if (type == double.class || type == Double.class) {
                                value = Double.parseDouble((String) value);
                            }

                            // convert String values to boolean if the field requires boolean
                            if (type == boolean.class || type == Boolean.class || type == AtomicBoolean.class) {
                                value = Boolean.parseBoolean((String) value);
                            }
                        } catch (NumberFormatException ex) {
                            if (type == Integer.class || type == int.class || type == AtomicInteger.class) {
                                throw new CodecExceptionLineNumber("cannot convert the string to an integer", valInfo);
                            } else if (type == long.class || type == Long.class || type == AtomicLong.class) {
                                throw new CodecExceptionLineNumber("cannot convert the string to a long", valInfo);
                            } else if (type == double.class || type == Double.class) {
                                throw new CodecExceptionLineNumber("cannot convert the string to a double", valInfo);
                            } else {
                                throw new IllegalStateException("unhandled case in the NumberFormatException");
                            }
                        }
                    }
                    if (type == AtomicInteger.class) {
                        value = new AtomicInteger((Integer) value);
                    } else if (type == AtomicLong.class) {
                        value = new AtomicLong((Long) value);

                    } else if (type == AtomicBoolean.class) {
                        value = new AtomicBoolean((Boolean) value);
                    }
                }
                // this space left intentionally blank
            } else if (field.isMap()) {
                Map map;
                try {
                    map = (Map) type.newInstance();
                } catch (Exception ex) {
                    throw new CodecExceptionLineNumber(ex, keyInfo);
                }
                JSONObject jmap = (JSONObject) value;
                Class vc = (Class) field.getGenericTypes()[1];
                boolean va = field.isMapValueArray();
                for (Iterator<String> iter = jmap.keys(); iter.hasNext();) {
                    String key = iter.next();
                    if (field.isInterned()) {
                        key = key.intern();
                    }
                    map.put(key, va ? decodeArrayInternal(vc, jmap.get(key), jmap.getKeyLineNumber(key), warnings)
                                    : decodeObjectInternal(vc, jmap.get(key), jmap.getKeyLineNumber(key), warnings));
                }
                value = map;
            } else if (field.isCollection()) {
                Collection col;
                JSONArray jarr;
                try {
                    col = (Collection) type.newInstance();
                } catch (Exception ex) {
                    throw new CodecExceptionLineNumber(ex, keyInfo);
                }
                try {
                    jarr = (JSONArray) value;
                } catch (Exception ex) {
                    throw new CodecExceptionLineNumber(ex, valInfo);
                }
                Class vc = field.getCollectionClass();
                boolean ar = field.isCollectionArray();
                for (int i = 0; i < jarr.length(); i++) {
                    col.add(ar ? decodeArrayInternal(vc, jarr.get(i), jarr.getLineNumber(i), warnings) :
                            decodeObjectInternal(vc, jarr.get(i), jarr.getLineNumber(i), warnings));
                }
                value = col;
            } else if (field.isEnum()) {
                try {
                    String valString = value.toString();
                    if (valString != "") {
                        value = Enum.valueOf(type, valString.toUpperCase());
                    }
                } catch (Exception ex) {
                    throw new CodecExceptionLineNumber(ex, valInfo);
                }
            } else if (field.isCodable()) {
                value = decodeObjectInternal(type, value, valInfo, warnings);
            }
            field.set(object, json.getLineNumberInfo(), value, valInfo);
        }
        if (object instanceof SuperCodable) {
            ((SuperCodable) object).postDecode();
        }
        if (warnings != null) {
            for (String fieldName : unknownFields) {
                String msg = "Unrecognized field '" + fieldName + "' in class " + object.getClass().getName();
                warnings.add(new UnrecognizedFieldException(msg, json.getKeyLineNumber(fieldName),
                        fieldName, object.getClass()));
            }
        }
        return object;
    }

}
