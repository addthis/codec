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
package com.addthis.codec.binary;

import javax.annotation.Nullable;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.addthis.basis.util.LessBytes;

import com.addthis.codec.Codec;
import com.addthis.codec.codables.Codable;
import com.addthis.codec.codables.ConcurrentCodable;
import com.addthis.codec.codables.SuperCodable;
import com.addthis.codec.reflection.CodableClassInfo;
import com.addthis.codec.reflection.CodableFieldInfo;
import com.addthis.codec.reflection.Fields;

import com.google.common.base.Strings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Like CodecBin1 but does not support upgrade/downgrade of objects to prev/later versions.
 * Stores all fields, does not use a map.  This is generally faster while using less space.
 */
public final class CodecBin2 implements Codec {

    private static final Logger log = LoggerFactory.getLogger(CodecBin2.class);

    public static final CodecBin2 INSTANCE            = new CodecBin2(false);
    public static final int       CODEC_VERSION       = 2;

    private final boolean charstring;

    private CodecBin2(boolean cs) { this.charstring = cs; }

    @Override
    public byte[] encode(Object obj) throws Exception {
        return encodeBytes(obj);
    }

    @Override
    public Object decode(Class type, byte[] data) throws Exception {
        return decode(type.newInstance(), data);
    }

    @Override
    public Object decode(Object shell, byte[] data) throws Exception {
        return decodeBytes(shell, data);
    }

    @Override
    public boolean storesNull(byte[] data) {
        return (data.length == 5) && (data[4] == 0);
    }

    public static byte[] encodeBytes(Object object) throws Exception {
        BufferOut buf = new BufferOut();
        LessBytes.writeInt(CODEC_VERSION, buf.out());
        INSTANCE.encodeObject(object, buf);
        return buf.out.toByteArray();
    }

    @Nullable @SuppressWarnings("unchecked")
    public static Object decodeBytes(Object object, byte[] data) throws Exception {
        BufferIn buf = new BufferIn(data);
        int ver = LessBytes.readInt(buf.in);
        require(ver == CODEC_VERSION, "version mismatch " + ver + " != " + CODEC_VERSION);
        return INSTANCE.decodeObject(Fields.getClassFieldMap(object.getClass()), object, buf);
    }

    private void encodeObject(Object object, BufferOut buf)
            throws Exception {
        log.trace("encodeObject: {} {}", object, buf);
        if (object == null) {
            buf.out.write(0);
            return;
        }
        boolean lock = object instanceof ConcurrentCodable;
        if (lock) {
            ((ConcurrentCodable) object).encodeLock();
        }
        try {
            if (object instanceof SuperCodable) {
                ((SuperCodable) object).preEncode();
            }
            Class objectClass = object.getClass();
            CodableClassInfo classInfo = Fields.getClassFieldMap(objectClass);
            if (objectClass.isArray()) {
                encodeArray(object, objectClass, buf);
            } else if ((classInfo.size() == 0) && !(object instanceof Codable)) {
                encodeNative(object, buf);
            } else {
                buf.out.write(1);
                writeStringHelper(classInfo.getClassName(object), buf.out());
                for (CodableFieldInfo field : classInfo.values()) {
                    encodeField(field.get(object), field, buf);
                }
            }
        } finally {
            if (lock) {
                ((ConcurrentCodable) object).encodeUnlock();
            }
        }
    }

    @Nullable private Object decodeObject(Class<?> type, BufferIn buf) throws Exception {
        log.trace("decodeObject: {} {}", type, buf);
        if (Fields.isNative(type)) {
            return decodeNative(type, buf);
        } else {
            CodableClassInfo classInfo = Fields.getClassFieldMap(type);
            return decodeObject(classInfo, null, buf);
        }
    }

    @Nullable private Object decodeObject(CodableClassInfo classInfo, @Nullable Object object, BufferIn buf) throws Exception {
        int ck = buf.in.read();
        if (ck == 0) {
            return null;
        }
        Class<?> type = classInfo.getBaseClass();
        log.trace("decodeObject: {} {} {}", classInfo, object, buf);
        String stype = readStringHelper(buf.in);
        if (!Strings.isNullOrEmpty(stype)) {
            Class<?> atype = classInfo.getClass(stype);
            if (type != atype) {
                classInfo = Fields.getClassFieldMap(atype);
                type = atype;
            }
        }
        if (object == null) {
            object = type.newInstance();
        }
        for (CodableFieldInfo field : classInfo.values()) {
            field.set(object, decodeField(field, buf));
        }
        if (object instanceof SuperCodable) {
            ((SuperCodable) object).postDecode();
        }
        return object;
    }

    private void encodeArray(Object value, Class<?> type, BufferOut buf) throws Exception {
        int len = Array.getLength(value);
        log.trace("encodeArray: {} {} {} len={}", value, type, buf, len);
        LessBytes.writeLength(len, buf.out());
        if ((type == byte.class) || (type == Byte.class)) {
            buf.out.write((byte[]) value);
        } else if ((type == int.class) || (type == Integer.class)) {
            int[] val = (int[]) value;
            for (int i = 0; i < len; i++) {
                LessBytes.writeInt(val[i], buf.out());
            }
        } else if ((type == long.class) || (type == Long.class)) {
            long[] val = (long[]) value;
            for (int i = 0; i < len; i++) {
                LessBytes.writeLong(val[i], buf.out());
            }
        } else if (type.isEnum()) {
            for (int i = 0; i < len; i++) {
                encodeNative(Array.get(value, i).toString(), buf);
            }
        } else {
            for (int i = 0; i < len; i++) {
                encodeObject(Array.get(value, i), buf);
            }
        }
    }

    @Nullable private Object decodeArray(Class<?> type, BufferIn buf) throws Exception {
        log.trace("decodeArray: {} {}", type, buf);
        int len = (int) LessBytes.readLength(buf.in);
        Object value = null;
        if (len > 0) {
            value = Array.newInstance(type, len);
            if ((type == byte.class) || (type == Byte.class)) {
                buf.in.read((byte[]) value);
            } else if ((type == int.class) || (type == Integer.class)) {
                int[] val = (int[]) value;
                for (int i = 0; i < len; i++) {
                    val[i] = LessBytes.readInt(buf.in);
                }
                value = val;
            } else if ((type == long.class) || (type == Long.class)) {
                long[] val = (long[]) value;
                for (int i = 0; i < len; i++) {
                    val[i] = LessBytes.readLong(buf.in);
                }
                value = val;
            } else if (type.isEnum()) {
                for (int i = 0; i < len; i++) {
                    Array.set(value, i, decodeEnum((Class<Enum>) type, buf));
                }
            } else {
                for (int i = 0; i < len; i++) {
                    Array.set(value, i, decodeObject(type, buf));
                }
            }
        }
        return value;
    }

    private void encodeField(Object value, CodableFieldInfo field, BufferOut buf) throws Exception {
        log.trace("encodeField: {} {} {}", value, field, buf);
        if (value != null) {
            try {
                buf.out.write(1);
                if (field.isArray()) {
                    encodeArray(value, field.getTypeOrComponentType(), buf);
                } else if (field.isNative()) {
                    encodeNative(value, buf);
                } else if (field.isMap()) {
                    Map<?, ?> map = (Map<?, ?>) value;
                    LessBytes.writeLength(map.size(), buf.out());
                    for (Entry<?, ?> entry : map.entrySet()) {
                        Object key = entry.getKey();
                        encodeObject(key, buf);
                        encodeObject(entry.getValue(), buf);
                    }
                } else if (field.isCollection()) {
                    Collection<?> coll = (Collection<?>) value;
                    LessBytes.writeLength(coll.size(), buf.out());
                    for (Object aColl : coll) {
                        encodeObject(aColl, buf);
                    }
                } else if (field.isCodable()) {
                    encodeObject(value, buf);
                } else if (field.isEnum()) {
                    encodeNative(value.toString(), buf);
                } else {
                    log.warn("[encodeField] unhandled field : {} {}", value, field);
                }
            } catch (Exception ex) {
                log.warn("failed encoding {} class {} type {}", value, value.getClass(), field, ex);
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                ex.printStackTrace(pw);
                log.warn(sw.toString());
                throw ex;
            }
        } else {
            buf.out.write(0);
        }
    }

    private static boolean isNotConcrete(Class<?> type) {
        int mod = type.getModifiers();
        return Modifier.isAbstract(mod) || Modifier.isInterface(mod);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> newMap(Class<?> type) throws InstantiationException, IllegalAccessException {
        return isNotConcrete(type) ? new HashMap<>() : (Map<Object, Object>) type.newInstance();
    }

    @SuppressWarnings("unchecked")
    private static Collection<Object> newCollection(Class<?> type, int size) throws InstantiationException, IllegalAccessException {
        return isNotConcrete(type) ? new ArrayList<>(size) : (Collection<Object>) type.newInstance();
    }

    @Nullable private Object decodeField(CodableFieldInfo field, BufferIn buf) throws Exception {
        log.trace("decodeField: {} {}", field, buf);
        int ck = buf.in.read();
        if (ck == 0) {
            return null;
        }
        Class<?> type = field.getTypeOrComponentType();
        if (field.isArray()) {
            return decodeArray(type, buf);
        } else if (field.isMap()) {
            Map<Object, Object> map = (Map<Object, Object>) newMap(type);
            int elements = (int) LessBytes.readLength(buf.in);
            if (elements == 0) {
                return map;
            }
            // value type, assume key is String
            Class<?> kc = field.getMapKeyClass();
            Class<?> vc = field.getMapValueClass();
            boolean ka = field.isMapKeyArray();
            boolean va = field.isMapValueArray();
            for (int i = 0; i < elements; i++) {
                if (ka) {
                    if (va) {
                        map.put(decodeArray(kc, buf), decodeArray(vc, buf));
                    } else {
                        map.put(decodeArray(kc, buf), decodeObject(vc, buf));
                    }
                } else {
                    if (va) {
                        map.put(decodeObject(kc, buf), decodeArray(vc, buf));
                    } else {
                        map.put(decodeObject(kc, buf), decodeObject(vc, buf));
                    }
                }
            }
            return map;
        } else if (field.isCollection()) {
            int elements = (int) LessBytes.readLength(buf.in);
            Collection<Object> coll = (Collection<Object>) newCollection(type, elements);
            if (elements == 0) {
                return coll;
            }
            Class<?> vc = field.getCollectionClass();
            boolean va = field.isCollectionArray();
            for (int i = 0; i < elements; i++) {
                coll.add(va ? decodeArray(vc, buf) : decodeObject(vc, buf));
            }
            return coll;
        } else if (field.isCodable()) {
            return decodeObject(type, buf);
        } else if (field.isEnum()) {
            return decodeEnum((Class<Enum>) type, buf);
        } else if (field.isNative()) {
            return decodeNative(type, buf);
        } else {
            log.warn("unhandled decode {}", field);
            return null;
        }
    }

    private void encodeNative(Object value, BufferOut buf) throws Exception {
        log.trace("encodeNative: {} {}", value, buf);
        Class<?> type = value.getClass();
        if (type == String.class) {
            writeStringHelper(value.toString(), buf.out());
        } else if ((type == Integer.class) || (type == int.class)) {
            LessBytes.writeInt((Integer) value, buf.out());
        } else if ((type == Long.class) || (type == long.class)) {
            LessBytes.writeLong((Long) value, buf.out());
        } else if ((type == Short.class) || (type == short.class)) {
            LessBytes.writeShort((Short) value, buf.out());
        } else if ((type == Boolean.class) || (type == boolean.class)) {
            buf.out.write((Boolean) value ? 1 : 0);
        } else if ((type == Float.class) || (type == float.class)) {
            LessBytes.writeInt(Float.floatToIntBits(((Float) value)), buf.out());
        } else if ((type == Double.class) || (type == double.class)) {
            LessBytes.writeLong(Double.doubleToLongBits(((Double) value)), buf.out());
        } else if (type == AtomicLong.class) {
            LessBytes.writeLong(((AtomicLong) value).get(), buf.out());
        } else if (type == AtomicInteger.class) {
            LessBytes.writeInt(((AtomicInteger) value).get(), buf.out());
        } else if (type == AtomicBoolean.class) {
            buf.out.write(((AtomicBoolean) value).get() ? 1 : 0);
        } else {
            log.warn("skip native encode for {} / {}", value, value.getClass());
        }
    }

    private Object decodeEnum(Class<Enum> type, BufferIn buf) throws Exception {
        String val = readStringHelper(buf.in);
        return Enum.valueOf(type, val);
    }

    @Nullable private Object decodeNative(Class<?> type, BufferIn buf) throws Exception {
        Object result = null;
        if (type == String.class) {
            result = readStringHelper(buf.in);
        } else if ((type == Integer.class) || (type == int.class)) {
            result = LessBytes.readInt(buf.in);
        } else if ((type == Long.class) || (type == long.class)) {
            result = LessBytes.readLong(buf.in);
        } else if ((type == Short.class) || (type == short.class)) {
            result = LessBytes.readShort(buf.in);
        } else if ((type == Boolean.class) || (type == boolean.class)) {
            result = buf.in.read() != 0 ? true : false;
        } else if ((type == Double.class) || (type == double.class)) {
            result = Double.longBitsToDouble(LessBytes.readLong(buf.in));
        } else if ((type == Float.class) || (type == float.class)) {
            result = Float.intBitsToFloat(LessBytes.readInt(buf.in));
        } else if (type == AtomicLong.class) {
            result = new AtomicLong(LessBytes.readLong(buf.in));
        } else if (type == AtomicInteger.class) {
            result = new AtomicInteger(LessBytes.readInt(buf.in));
        } else if (type == AtomicBoolean.class) {
            result = buf.in.read() != 0 ? new AtomicBoolean(true) : new AtomicBoolean(false);
        } else {
            log.warn("unhandled native decode {}", type);
        }
        return result;
    }

    private static void require(boolean bool, String msg) throws Exception {
        if (!bool) {
            throw new Exception(msg);
        }
    }

    @Nullable private String readStringHelper(InputStream in) throws Exception {
        if (charstring) {
            return LessBytes.readCharString(in);
        } else {
            return LessBytes.readString(in);
        }
    }

    private void writeStringHelper(String str, OutputStream out) throws Exception {
        if (charstring) {
            LessBytes.writeCharString(str, out);
        } else {
            LessBytes.writeString(str, out);
        }
    }
}
