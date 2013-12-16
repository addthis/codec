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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import java.lang.reflect.Array;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.addthis.basis.util.Bytes;
import com.addthis.basis.util.Strings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TODO add flag to encode/not encode field names (long term storage, object versioning)
 */
public final class CodecBin1 extends Codec {

    private static final Logger log = LoggerFactory.getLogger(CodecBin1.class);

    private static final int CODEC_VERSION = 1;
    private static final CodecBin1 defaultCodec = new CodecBin1();

    private static final class BufferOut {

        private ByteArrayOutputStream out;
        private Stack<ByteArrayOutputStream> stack;

        BufferOut() {
            stack = new Stack<ByteArrayOutputStream>();
            push();
        }

        public OutputStream out() {
            return out;
        }

        public void push() {
            stack.push(new ByteArrayOutputStream());
            out = stack.peek();
        }

        public void pop() throws IOException {
            ByteArrayOutputStream last = stack.pop();
            out = stack.peek();
            Bytes.writeLength(last.size(), out());
            last.writeTo(out());
        }

        public String toString() {
            return "BufOut[" + out.size() + "," + stack.size() + "]";
        }
    }

    private static final class BufferIn {

        private ByteArrayInputStream in;
        private Stack<ByteArrayInputStream> stack;

        BufferIn(byte data[]) throws IOException {
            stack = new Stack<ByteArrayInputStream>();
            in = new ByteArrayInputStream(data);
        }

        public void push() throws IOException {
            int len = (int) Bytes.readLength(in);
            byte ndat[] = Bytes.readBytes(in, len);
            stack.push(in);
            in = new ByteArrayInputStream(ndat);
        }

        public void pop() {
            in = stack.pop();
        }

        public String toString() {
            return "BufIn[" + in.available() + "," + stack.size() + "]";
        }
    }

    @Override
    public byte[] encode(Object obj) throws Exception {
        return encodeBytes(obj);
    }

    @Override
    public Object decode(Object shell, byte data[]) throws Exception {
        return decodeBytes(shell, data);
    }

    @Override
    public CodableStatistics statistics(Object obj) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean storesNull(byte data[]) {
        return (data.length == 5) && (data[4] == 0);
    }

    public static byte[] encodeBytes(Object object) throws Exception {
        BufferOut buf = new BufferOut();
        Bytes.writeInt(CODEC_VERSION, buf.out());
        defaultCodec.encodeObject(object, buf);
        return buf.out.toByteArray();
    }

    private void encodeObject(Object object, BufferOut buf) throws Exception {
        log.trace("encodeObject: {} {}", new Object[]{object, buf});
        if (object == null) {
            buf.push();
            buf.pop();
            return;
        }
        boolean lock = object instanceof Codec.ConcurrentCodable;
        if (lock && !((Codec.ConcurrentCodable) object).encodeLock()) {
            throw new Exception("Unable to acquire encoding lock on " + object);
        }
        try {
            if (object instanceof Codec.SuperCodable) {
                ((Codec.SuperCodable) object).preEncode();
            }
            Class<?> type = object.getClass();
            if (type.isArray()) {
                encodeArray(object, type.getComponentType(), buf);
                return;
            }
            CodableClassInfo classInfo = Codec.getClassFieldMap(object.getClass());
            if (classInfo.size() == 0 && !(object instanceof Codable)) {
                encodeNative(object, buf);
            } else {
                Bytes.writeString(classInfo.getClassName(object), buf.out());
                buf.push();
                for (Iterator<CodableFieldInfo> fields = classInfo.values().iterator(); fields.hasNext();) {
                    CodableFieldInfo field = fields.next();
                    encodeField(field.get(object), field, buf);
                }
                buf.pop();
            }
        } finally {
            if (lock) {
                ((Codec.ConcurrentCodable) object).encodeUnlock();
            }
        }
    }

    private void encodeArray(Object value, Class<?> type, BufferOut buf) throws Exception {
        int len = Array.getLength(value);
        if (log.isTraceEnabled()) {
            log.trace("encodeArray: " + value + " " + type + " " + buf + " len=" + len);
        }
        Bytes.writeLength(len, buf.out());
        if (type == byte.class || type == Byte.class) {
            buf.out.write((byte[]) value);
        } else {
            for (int i = 0; i < len; i++) {
                encodeObject(Array.get(value, i), buf);
            }
        }
    }

    private void encodeField(Object value, CodableFieldInfo field, BufferOut buf) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("encodeField: " + value + " " + field + " " + buf);
        }
        if (value == null || field.isReadOnly()) {
            return;
        }
        buf.push();
        Bytes.writeString(field.getName(), buf.out());
        if (field.isArray()) {
            encodeArray(value, field.getType(), buf);
        } else if (field.isNative()) {
            encodeNative(value, buf);
        } else if (field.isMap()) {
            Map<?, ?> map = (Map<?, ?>) value;
            Bytes.writeLength(map.size(), buf.out());
            for (Entry<?, ?> entry : map.entrySet()) {
                encodeObject(entry.getKey(), buf);
                encodeObject(entry.getValue(), buf);
            }
        } else if (field.isCollection()) {
            Collection<?> coll = (Collection<?>) value;
            Bytes.writeLength(coll.size(), buf.out());
            for (Iterator<?> iter = coll.iterator(); iter.hasNext();) {
                encodeObject(iter.next(), buf);
            }
        } else if (field.isCodable()) {
            encodeObject(value, buf);
        }
        buf.pop();
    }

    private void encodeNative(Object value, BufferOut buf) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("encodeNative: " + value + " " + buf);
        }
        Class<?> type = value.getClass();
        if (type == String.class) {
            Bytes.writeString((String) value, buf.out());
        } else if (type == Integer.class || type == int.class) {
            Bytes.writeInt((Integer) value, buf.out());
        } else if (type == Long.class || type == long.class) {
            Bytes.writeLong((Long) value, buf.out());
        } else if (type == Short.class || type == short.class) {
            Bytes.writeShort((Short) value, buf.out());
        } else if (type == Boolean.class || type == boolean.class) {
            buf.out.write(((Boolean) value).booleanValue() ? 1 : 0);
        } else if (type == AtomicBoolean.class) {
            buf.out.write(((AtomicBoolean) value).get() ? 1 : 0);
        } else if (type == AtomicLong.class) {
            Bytes.writeLong(((AtomicLong) value).get(), buf.out());
        } else if (type == AtomicInteger.class) {
            Bytes.writeInt(((AtomicInteger) value).get(), buf.out());
        } else if (type == Double.class || type == double.class) {
            Bytes.writeLong(Double.doubleToLongBits(((Double) value)), buf.out());
        } else if (type == Float.class || type == float.class) {
            Bytes.writeInt(Float.floatToIntBits(((Float) value)), buf.out());
        }
    }

    private static void require(boolean bool, String msg) throws Exception {
        if (!bool) {
            throw new Exception(msg);
        }
    }

    @SuppressWarnings("unchecked")
    public static Object decodeBytes(Object object, byte data[]) throws Exception {
        BufferIn buf = new BufferIn(data);
        int ver = Bytes.readInt(buf.in);
        require(ver == CODEC_VERSION, "version mismatch " + ver + " != " + CODEC_VERSION + " [" + Strings.printable(data) + "]");
        return defaultCodec.decodeObject(getClassFieldMap(object.getClass()), object, buf);
    }

    private Object decodeObject(Class<?> type, BufferIn buf) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("decodeObject: " + type + " " + buf);
        }
        if (isNative(type)) {
            return decodeNative(type, buf);
        } else {
            CodableClassInfo classInfo = getClassFieldMap(type);
            if (classInfo.size() == 0 && classInfo.getClassMap() == null) {
                return decodeNative(type, buf);
            }
            return decodeObject(classInfo, null, buf);
        }
    }

    private Object decodeObject(CodableClassInfo classInfo, Object object, BufferIn buf) throws Exception {
        Class<?> type = classInfo.getBaseClass();
        if (log.isTraceEnabled()) {
            log.trace("decodeObject: " + classInfo + " " + object + " " + buf);
        }
        String stype = Bytes.readString(buf.in);
        if (!Strings.isEmpty(stype)) {
            Class<?> atype = classInfo.getClass(stype);
            if (atype != null && type != atype) {
                classInfo = getClassFieldMap(atype);
                type = atype;
            }
        }
        if (object == null) {
            object = type.newInstance();
        }
        buf.push();
        if (buf.in.available() == 0) {
            buf.pop();
            return null;
        }
        floop:
        for (Iterator<CodableFieldInfo> fields = classInfo.values().iterator(); fields.hasNext();) {
            CodableFieldInfo field = fields.next();
            if (buf.in.available() == 0) {
                field.set(object, null);
                break;
            }
            buf.push();
            String fieldName = Bytes.readString(buf.in);
            while (!fieldName.equals(field.getName())) {
                if (fieldName.compareTo(field.getName()) < 0) {
                    buf.pop();
                    buf.push();
                    fieldName = Bytes.readString(buf.in);
                } else if (fields.hasNext()) {
                    field.set(object, null);
                    field = fields.next();
                } else {
                    field.set(object, null);
                    buf.pop();
                    break floop;
                }
            }
            field.set(object, decodeField(field, buf));
            buf.pop();
        }
        if (object instanceof SuperCodable) {
            ((SuperCodable) object).postDecode();
        }
        buf.pop();
        return object;
    }

    private Object decodeArray(Class<?> type, BufferIn buf) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("decodeArray: " + type + " " + buf);
        }
        int length = (int) Bytes.readLength(buf.in);
        Object value = null;
        if (length > 0) {
            value = Array.newInstance(type, length);
            if (type == byte.class || type == Byte.class) {
                buf.in.read((byte[]) value);
            } else {
                for (int i = 0; i < length; i++) {
                    Array.set(value, i, decodeObject(type, buf));
                }
            }
        }
        return value;
    }

    private Object decodeField(CodableFieldInfo field, BufferIn buf) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("decodeField: " + field + " " + buf);
        }
        Class<?> type = field.getType();
        if (field.isArray()) {
            return decodeArray(type, buf);
        } else if (field.isMap()) {
            Map<Object, Object> map = (Map<Object, Object>) type.newInstance();
            int elements = (int) Bytes.readLength(buf.in);
            if (elements == 0) {
                return null;
            }
            // value type, assume key is String
            Class<?> kc = field.getMapKeyClass();
            Class<?> vc = field.getMapValueClass();
            boolean ka = field.isMapKeyArray();
            boolean va = field.isMapValueArray();
            for (int i = 0; i < elements; i++) {
                map.put(ka ? decodeArray(kc, buf) : decodeObject(kc, buf), va ? decodeArray(vc, buf) : decodeObject(vc, buf));
            }
            return map;
        } else if (field.isCollection()) {
            Collection<Object> coll = (Collection<Object>) type.newInstance();
            Class<?> vc = field.getCollectionClass();
            boolean va = field.isCollectionArray();
            int elements = (int) Bytes.readLength(buf.in);
            if (elements == 0) {
                return null;
            }
            for (int i = 0; i < elements; i++) {
                coll.add(va ? decodeArray(vc, buf) : decodeObject(vc, buf));
            }
            return coll;
        } else if (field.isCodable()) {
            return decodeObject(type, buf);
        } else if (field.isNative()) {
            return decodeNative(type, buf);
        } else {
            return null;
        }
    }

    private Object decodeNative(Class<?> type, BufferIn buf) throws Exception {
        Object result = null;
        if (type == String.class) {
            result = Bytes.readString(buf.in);
        } else if (type == Integer.class || type == int.class) {
            result = Bytes.readInt(buf.in);
        } else if (type == Long.class || type == long.class) {
            result = Bytes.readLong(buf.in);
        } else if (type == Short.class || type == short.class) {
            result = Bytes.readShort(buf.in);
        } else if (type == Boolean.class || type == boolean.class) {
            result = buf.in.read() != 0 ? true : false;
        } else if (type == String.class) {
            result = Bytes.readString(buf.in);
        } else if (type == AtomicBoolean.class) {
            result = buf.in.read() != 0 ? new AtomicBoolean(true) : new AtomicBoolean(false);
        } else if (type == AtomicLong.class) {
            result = new AtomicLong(Bytes.readLong(buf.in));
        } else if (type == AtomicInteger.class) {
            result = new AtomicInteger(Bytes.readInt(buf.in));
        } else if (type == Double.class || type == double.class) {
            result = Double.longBitsToDouble(Bytes.readLong(buf.in));
        } else if (type == Float.class || type == float.class) {
            result = Float.intBitsToFloat(Bytes.readInt(buf.in));
        }
        return result;
    }
}
