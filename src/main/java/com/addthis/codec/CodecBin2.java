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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.lang.reflect.Array;
import java.lang.reflect.Modifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
 * Like CodecBin1 but does not support upgrade/downgrade of objects to prev/later versions.
 * Stores all fields, does not use a map.  This is generally faster while using less space.
 */
public final class CodecBin2 extends Codec {

    private static final Logger log = LoggerFactory.getLogger(CodecBin2.class);

    private static final int CODEC_VERSION = 2;
    private static final CodecBin2 singleton = new CodecBin2(false);
    private static final CodecBin2 singletonCharstring = new CodecBin2(true);

    private final boolean charstring;

    private CodecBin2(boolean cs) { this.charstring = cs; }

    @SuppressWarnings("unused")
    public static CodecBin2 getSingleton() { return singleton; }

    @SuppressWarnings("unused")
    public static CodecBin2 getSingleton(boolean charstring) { return charstring ? singletonCharstring : singleton; }

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

        @Override
        public String toString() {
            return "BufferOut:" + (out != null ? out.size() : -1);
        }
    }

    private static final class BufferIn {

        private ByteArrayInputStream in;
        private Stack<ByteArrayInputStream> stack;

        BufferIn(final byte data[]) throws IOException {
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

        @Override
        public String toString() {
            return "BufferIn:" + (in != null ? in.available() : -1);
        }
    }

    @Override
    public byte[] encode(Object obj) throws Exception {
        return encodeBytes(obj);
    }

    @Override
    public CodableStatistics statistics(Object obj) throws Exception {
        return encodeStatistics(obj);
    }

    @Override
    public Object decode(Object shell, byte data[]) throws Exception {
        return decodeBytes(shell, data);
    }

    @Override
    public boolean storesNull(byte data[]) {
        return (data.length == 5) && (data[4] == 0);
    }

    public static CodableStatistics encodeStatistics(Object object) throws Exception {
        BufferOut buf = new BufferOut();
        Bytes.writeInt(CODEC_VERSION, buf.out());
        CodableStatistics statistics = new CodableStatistics();
        singleton.encodeObject(object, buf, statistics);
        statistics.setTotalSize(buf.out.size());
        statistics.export();
        return statistics;
    }

    public static byte[] encodeBytes(Object object) throws Exception {
        BufferOut buf = new BufferOut();
        Bytes.writeInt(CODEC_VERSION, buf.out());
        singleton.encodeObject(object, buf, null);
        return buf.out.toByteArray();
    }

    @SuppressWarnings("unchecked")
    public static Object decodeBytes(Object object, byte data[]) throws Exception {
        BufferIn buf = new BufferIn(data);
        int ver = Bytes.readInt(buf.in);
        require(ver == CODEC_VERSION, "version mismatch " + ver + " != " + CODEC_VERSION);
        return singleton.decodeObject(getClassFieldMap(object.getClass()), object, buf);
    }

    private void encodeObject(Object object, BufferOut buf, CodableStatistics statistics) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("encodeObject: " + object + " " + buf);
        }
        if (object == null) {
            buf.out.write(0);
            return;
        }
        boolean lock = object instanceof Codec.ConcurrentCodable;
        if (lock) {
            ((Codec.ConcurrentCodable) object).encodeLock();
        }
        try {
            if (object instanceof Codec.SuperCodable) {
                ((Codec.SuperCodable) object).preEncode();
            }
            Class objectClass = object.getClass();
            CodableClassInfo classInfo = Codec.getClassFieldMap(objectClass);
            if (objectClass.isArray()) {
                encodeArray(object, objectClass, buf);
            } else if (classInfo.size() == 0 && !(object instanceof Codable)) {
                encodeNative(object, buf);
            } else {
                buf.out.write(1);
                writeStringHelper(classInfo.getClassName(object), buf.out());
                for (Iterator<CodableFieldInfo> fields = classInfo.values().iterator(); fields.hasNext();) {
                    CodableFieldInfo field = fields.next();
                    long beginSize = buf.out.size();
                    encodeField(field.get(object), field, buf, statistics);
                    if (statistics != null) {
                        long endSize = buf.out.size();
                        statistics.getData().put(field.getName(), endSize - beginSize);
                    }
                }
            }
        } finally {
            if (lock) {
                ((Codec.ConcurrentCodable) object).encodeUnlock();
            }
        }
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
        int ck = buf.in.read();
        if (ck == 0) {
            return null;
        }
        Class<?> type = classInfo.getBaseClass();
        if (log.isTraceEnabled()) {
            log.trace("decodeObject: " + classInfo + " " + object + " " + buf);
        }
        String stype = readStringHelper(buf.in);
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
        for (Iterator<CodableFieldInfo> fields = classInfo.values().iterator(); fields.hasNext();) {
            CodableFieldInfo field = fields.next();
            field.set(object, decodeField(field, buf));
        }
        if (object instanceof SuperCodable) {
            ((SuperCodable) object).postDecode();
        }
        return object;
    }

    private void encodeArray(Object value, Class<?> type, BufferOut buf) throws Exception {
        int len = Array.getLength(value);
        if (log.isTraceEnabled()) {
            log.trace("encodeArray: " + value + " " + type + " " + buf + " len=" + len);
        }
        Bytes.writeLength(len, buf.out());
        if (type == byte.class || type == Byte.class) {
            buf.out.write((byte[]) value);
        } else if (type == int.class || type == Integer.class) {
            int val[] = (int[]) value;
            for (int i = 0; i < len; i++) {
                Bytes.writeInt(val[i], buf.out());
            }
        } else if (type == long.class || type == Long.class) {
            long val[] = (long[]) value;
            for (int i = 0; i < len; i++) {
                Bytes.writeLong(val[i], buf.out());
            }
        } else if (type.isEnum()) {
            for (int i = 0; i < len; i++) {
                encodeNative(Array.get(value, i).toString(), buf);
            }
        } else {
            for (int i = 0; i < len; i++) {
                encodeObject(Array.get(value, i), buf, null);
            }
        }
    }

    private Object decodeArray(Class<?> type, BufferIn buf) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("decodeArray: " + type + " " + buf);
        }
        int len = (int) Bytes.readLength(buf.in);
        Object value = null;
        if (len > 0) {
            value = Array.newInstance(type, len);
            if (type == byte.class || type == Byte.class) {
                buf.in.read((byte[]) value);
            } else if (type == int.class || type == Integer.class) {
                int val[] = (int[]) value;
                for (int i = 0; i < len; i++) {
                    val[i] = Bytes.readInt(buf.in);
                }
                value = val;
            } else if (type == long.class || type == Long.class) {
                long val[] = (long[]) value;
                for (int i = 0; i < len; i++) {
                    val[i] = Bytes.readLong(buf.in);
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

    private final void addMapStatistics(CodableStatistics statistics, CodableFieldInfo field,
            Object key, long size) {
        if (statistics == null) {
            return;
        }
        Map<Object, Long> innerData = statistics.getMapData().get(field.getName());
        if (innerData == null) {
            innerData = new HashMap<Object, Long>();
            statistics.getMapData().put(field.getName(), innerData);
        }
        innerData.put(key, size);
    }

    private void encodeField(Object value, CodableFieldInfo field, BufferOut buf, CodableStatistics statistics) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("encodeField: " + value + " " + field + " " + buf);
        }
        if (value != null) {
            try {
                buf.out.write(1);
                if (field.isArray()) {
                    encodeArray(value, field.getType(), buf);
                } else if (field.isNative()) {
                    encodeNative(value, buf);
                } else if (field.isMap()) {
                    Map<?, ?> map = (Map<?, ?>) value;
                    Bytes.writeLength(map.size(), buf.out());
                    for (Entry<?, ?> entry : map.entrySet()) {
                        Object key = entry.getKey();
                        encodeObject(key, buf, null);
                        long startValueSize = buf.out.size();
                        encodeObject(entry.getValue(), buf, null);
                        long endValueSize = buf.out.size();
                        addMapStatistics(statistics, field, key, endValueSize - startValueSize);
                    }
                } else if (field.isCollection()) {
                    Collection<?> coll = (Collection<?>) value;
                    Bytes.writeLength(coll.size(), buf.out());
                    for (Iterator<?> iter = coll.iterator(); iter.hasNext();) {
                        encodeObject(iter.next(), buf, null);
                    }
                } else if (field.isCodable()) {
                    encodeObject(value, buf, null);
                } else if (field.isEnum()) {
                    encodeNative(value.toString(), buf);
                } else {
                    log.warn("[encodeField] unhandled field : " + value + " " + field);
                }
            } catch (Exception ex) {
                log.warn("failed encoding " + value + " class " + value.getClass() + " type " + field + " with " + ex, ex);
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

    private static final boolean isNotConcrete(Class<?> type) {
        int mod = type.getModifiers();
        return Modifier.isAbstract(mod) || Modifier.isInterface(mod);
    }

    @SuppressWarnings("unchecked")
    private static final Map<Object, Object> newMap(Class<?> type) throws InstantiationException, IllegalAccessException {
        return isNotConcrete(type) ? new HashMap<Object, Object>() : (Map<Object, Object>) type.newInstance();
    }

    @SuppressWarnings("unchecked")
    private static final Collection<Object> newCollection(Class<?> type, int size) throws InstantiationException, IllegalAccessException {
        return isNotConcrete(type) ? new ArrayList<Object>(size) : (Collection<Object>) type.newInstance();
    }

    private Object decodeField(CodableFieldInfo field, BufferIn buf) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("decodeField: " + field + " " + buf);
        }
        int ck = buf.in.read();
        if (ck == 0) {
            return null;
        }
        Class<?> type = field.getType();
        if (field.isArray()) {
            return decodeArray(type, buf);
        } else if (field.isMap()) {
            Map<Object, Object> map = (Map<Object, Object>) newMap(type);
            int elements = (int) Bytes.readLength(buf.in);
            if (elements == 0) {
                return map;
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
            int elements = (int) Bytes.readLength(buf.in);
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
            log.warn("unhandled decode " + field);
            return null;
        }
    }

    private void encodeNative(Object value, BufferOut buf) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("encodeNative: " + value + " " + buf);
        }
        Class<?> type = value.getClass();
        if (type == String.class) {
            writeStringHelper(value.toString(), buf.out());
        } else if (type == Integer.class || type == int.class) {
            Bytes.writeInt((Integer) value, buf.out());
        } else if (type == Long.class || type == long.class) {
            Bytes.writeLong((Long) value, buf.out());
        } else if (type == Short.class || type == short.class) {
            Bytes.writeShort((Short) value, buf.out());
        } else if (type == Boolean.class || type == boolean.class) {
            buf.out.write((Boolean) value ? 1 : 0);
        } else if (type == Float.class || type == float.class) {
            Bytes.writeInt(Float.floatToIntBits(((Float) value)), buf.out());
        } else if (type == Double.class || type == double.class) {
            Bytes.writeLong(Double.doubleToLongBits(((Double) value)), buf.out());
        } else if (type == AtomicLong.class) {
            Bytes.writeLong(((AtomicLong) value).get(), buf.out());
        } else if (type == AtomicInteger.class) {
            Bytes.writeInt(((AtomicInteger) value).get(), buf.out());
        } else if (type == AtomicBoolean.class) {
            buf.out.write(((AtomicBoolean) value).get() ? 1 : 0);
        } else {
            log.warn("skip native encode for " + value + " / " + value.getClass());
        }
    }

    private Object decodeEnum(Class<Enum> type, BufferIn buf) throws Exception {
        String val = readStringHelper(buf.in);
        return Enum.valueOf(type, val);
    }

    private Object decodeNative(Class<?> type, BufferIn buf) throws Exception {
        Object result = null;
        if (type == String.class) {
            result = readStringHelper(buf.in);
        } else if (type == Integer.class || type == int.class) {
            result = Bytes.readInt(buf.in);
        } else if (type == Long.class || type == long.class) {
            result = Bytes.readLong(buf.in);
        } else if (type == Short.class || type == short.class) {
            result = Bytes.readShort(buf.in);
        } else if (type == Boolean.class || type == boolean.class) {
            result = buf.in.read() != 0 ? true : false;
        } else if (type == String.class) {
            result = readStringHelper(buf.in);
        } else if (type == Double.class || type == double.class) {
            result = Double.longBitsToDouble(Bytes.readLong(buf.in));
        } else if (type == Float.class || type == float.class) {
            result = Float.intBitsToFloat(Bytes.readInt(buf.in));
        } else if (type == AtomicLong.class) {
            result = new AtomicLong(Bytes.readLong(buf.in));
        } else if (type == AtomicInteger.class) {
            result = new AtomicInteger(Bytes.readInt(buf.in));
        } else if (type == AtomicBoolean.class) {
            result = buf.in.read() != 0 ? new AtomicBoolean(true) : new AtomicBoolean(false);
        } else {
            log.warn("unhandled native decode " + type);
        }
        return result;
    }

    private static void require(boolean bool, String msg) throws Exception {
        if (!bool) {
            throw new Exception(msg);
        }
    }

    private String readStringHelper(InputStream in) throws Exception {
        if (charstring) {
            return Bytes.readCharString(in);
        } else {
            return Bytes.readString(in);
        }
    }

    private void writeStringHelper(String str, OutputStream out) throws Exception {
        if (charstring) {
            Bytes.writeCharString(str, out);
        } else {
            Bytes.writeString(str, out);
        }
    }
}
