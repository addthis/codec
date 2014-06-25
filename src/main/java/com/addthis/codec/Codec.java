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

import javax.annotation.Nonnull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.addthis.maljson.LineNumberInfo;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.buffer.ByteBuf;

public abstract class Codec {

    private static final Logger log = LoggerFactory.getLogger(Codec.class);
    private static final ConcurrentHashMap<Class<?>, CodableClassInfo> fieldMaps = new ConcurrentHashMap<Class<?>, CodableClassInfo>();

    public static enum TYPE {
        BIN2(CodecBin2.class), BIN1(CodecBin1.class), JSON(CodecJSON.class), KV(CodecKV.class);

        private TYPE(Class<?> type) {
            this.type = type;
        }

        private Class<?> type;

        public Codec getInstance() {
            try {
                return (Codec) type.newInstance();
            } catch (Exception ex) {
                return null;
            }
        }
    }

    /**
     * all codecs must implement this
     */
    public abstract byte[] encode(Object obj) throws Exception;

    /**
     * all codecs must implement this
     */
    public abstract CodableStatistics statistics(Object obj) throws Exception;

    /**
     * all codecs must implement this
     */
    public abstract <T> T decode(T shell, byte data[]) throws Exception;

    /**
     * all codecs must implement this
     */
    public abstract boolean storesNull(byte data[]);

    /**
     * helper to decode into a class instead of object shell
     */
    public <T> T decode(Class<T> type, byte data[]) throws Exception {
        return decode(type.newInstance(), data);
    }

    /**
     * used to flag an object type as codable when it's a field in another
     * object
     */
    public static interface Codable {

    }

    /**
     * used by classes that could be modified during encoding
     */
    public static interface ConcurrentCodable extends Codable {

        public boolean encodeLock();

        public void encodeUnlock();
    }

    /**
     * used to run code to 'transcode' values into codable primitives
     */
    public static interface SuperCodable extends Codable {

        public void postDecode();

        public void preEncode();
    }

    /**
     * for classes that want to handle their own direct serialization
     */
    public static interface BytesCodable extends Codable {

        public byte[] bytesEncode(long version);

        public void bytesDecode(byte b[], long version);
    }

    /**
     * for classes that want to handle their own direct serialization and prefer to not
     * use and/or allocate byte arrays
     */
    public static interface ByteBufCodable extends Codable {

        public void writeBytes(ByteBuf buf);

        public void readBytes(ByteBuf buf);
    }

    @SuppressWarnings("serial")
    public static class PolicyException extends Exception {

        public PolicyException(String msg) {
            super(msg);
        }
    }

    @SuppressWarnings("serial")
    public static class RequiredFieldException extends PolicyException {

        private String field;

        public RequiredFieldException(String msg, String field) {
            super(msg);
            this.field = field;
        }

        public String getField() {
            return field;
        }
    }

    @SuppressWarnings("serial")
    public static class ValidationException extends PolicyException {

        private String field;

        public ValidationException(String msg, String field) {
            super(msg);
            this.field = field;
        }

        public String getField() {
            return field;
        }
    }

    @SuppressWarnings("serial")
    public static class UnrecognizedFieldException extends CodecExceptionLineNumber {

        private String field;
        private Class clazz;

        public UnrecognizedFieldException(String msg, @Nonnull LineNumberInfo info, String field, Class clazz) {
            super(msg, info);
            this.field = field;
            this.clazz = clazz;
        }

        public String getField() {
            return field;
        }

        public Class getErrorClass() {
            return clazz;
        }
    }

    public static interface Validator {

        public boolean validate(CodableFieldInfo field, Object value);
    }

    public static class Truthinator implements Validator {

        public boolean validate(CodableFieldInfo field, Object value) {
            return true;
        }
    }

    public static interface ClassMapFactory {

        public ClassMap getClassMap();
    }

    /* A bi-directional map between Strings and Classes. */
    public static class ClassMap {

        private BiMap<String, Class<?>> map = HashBiMap.create();
        private Class<?> arraySugar;

        public String getClassField() {
            return "class";
        }

        public Class<?> setArraySugar(Class<?> newArraySugar) {
            Class<?> prev = this.arraySugar;
            if (prev != null) {
                log.warn("warning: overriding class map array sugar class {} with old type {} " +
                         "and new type {}", prev, prev, newArraySugar);
            }
            this.arraySugar = newArraySugar;
            return prev;
        }

        public Class<?> getArraySugar() {
            return arraySugar;
        }

        public String getCategory() {
            return null;
        }

        public ClassMap misnomerMap() {
            return null;
        }

        public ClassMap add(Class<?> type) {
            return add(type.getSimpleName(), type);
        }

        public java.util.Set<String> getNames() {
            return map.keySet();
        }

        public ClassMap add(String name, Class<?> type) {
            Class prev = map.put(name, type);
            if (prev != null) {
                log.warn("warning: overriding class map for key "
                         + name + " with old type " + prev + " and new type " + type);
            }
            if (type.getAnnotation(ArraySugar.class) != null) {
                setArraySugar(type);
            }
            return this;
        }

        public ClassMap remove(Class<?> type) {
            map.inverse().remove(type);
            return this;
        }

        public ClassMap remove(String name, Class<?> type) {
            map.remove(name);
            return this;
        }

        public boolean contains(String name) {
            return map.containsKey(name);
        }

        public boolean contains(Class<?> type) {
            return map.containsValue(type);
        }

        public String getClassName(Class<?> type) {
            String alt = map.inverse().get(type);
            return alt != null ? alt : type.getName();
        }

        public Class<?> getClass(String type) throws Exception {
            Class<?> alt = map.get(type);
            if (alt != null) {
                return alt;
            }
            try {
                Class theClass = Class.forName(type);
                return (theClass);
            } catch (ClassNotFoundException ex) {
                throw classNameSuggestions(type, ex);
            }
        }

        private Exception classNameSuggestions(String type, ClassNotFoundException ex) {
            java.util.Set<String> classNames = this.getNames();
            String category = this.getCategory();
            ClassMap misnomerMap = this.misnomerMap();
            if (classNames.isEmpty()) {
                return ex;
            }
            StringBuilder builder = new StringBuilder();

            if (category != null) {
                builder.append("Could not instantiate an instance of the ");
                builder.append(category);
                builder.append(" category that you have specified");
            } else {
                builder.append("Could not instantiate something you have specified");
            }
            builder.append(" with \"");
            builder.append(type);
            builder.append("\".");
            if (misnomerMap != null && misnomerMap.getCategory() != null && misnomerMap.contains(type)) {
                builder.append("\nIt looks like you tried to instantiate a ");
                builder.append(misnomerMap.getCategory());
                builder.append(" and I am expecting a ");
                builder.append(category);
                builder.append(".");
            } else {
                builder.append("\nPerhaps you intended one of the following: ");
                Iterator<String> iterator = classNames.iterator();
                while (iterator.hasNext()) {
                    String name = iterator.next();

                    if (!iterator.hasNext() && classNames.size() > 1) {
                        builder.append("or ");
                    }

                    builder.append('"');
                    builder.append(name);
                    builder.append('"');
                    builder.append(" ");
                }
                builder.append("?\n");
            }
            return new Exception(builder.toString(), ex);
        }
    }


    /** marker annotation suggesting this class is an array-wrapper over its fellows */
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface ArraySugar {}

    /**
     * control coding parameters for fields. allows code to dictate non-codable
     * fields as codable
     */
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Set {

        boolean codable() default true;

        boolean readonly() default false;

        boolean writeonly() default false;

        boolean required() default false;

        boolean intern() default false;

        Class<? extends Validator> validator() default Truthinator.class;

        Class<? extends ClassMap> classMap() default ClassMap.class;

        Class<? extends ClassMapFactory> classMapFactory() default ClassMapFactory.class;
    }

    static Type[] collectTypes(Class<?> type, Type node) {
        List<Type> l = collectTypes(new ArrayList<Type>(), type, node);
        // System.out.println("collected: " +l);
        while (l.size() > 0) {
            int ni = l.lastIndexOf(null);
            if (ni < 0) {
                break;
            }
            if (ni >= l.size() - 1) {
                l.remove(ni);
            } else {
                l.set(ni, l.get(l.size() - 1));
                l.remove(l.size() - 1);
            }
        }
        // System.out.println("returned: " +l);
        if (l.size() == 0) {
            return null;
        } else {
            Type t[] = new Type[l.size()];
            l.toArray(t);
            return t;
        }
    }

    private static List<Type> collectTypes(List<Type> list, Class<?> type, Type node) {
        if ((type == null) && (node == null)) {
            return list;
        }
        if (list == null) {
            list = new LinkedList<>();
        }
        if (node instanceof Class) {
            if (type != null) {
                collectTypes(list, ((Class<?>) node).getSuperclass(), type.getGenericSuperclass());
            } else {
                collectTypes(list, ((Class<?>) node).getSuperclass(), null);
            }
        } else {
            if (type != null) {
                collectTypes(list, null, type.getGenericSuperclass());
            } else {
                collectTypes(list, null, null);
            }
        }
        if (node instanceof ParameterizedType) {
            List<Type> tl = Arrays.asList(((ParameterizedType) node).getActualTypeArguments());
            for (Type t : tl) {
                if ((t instanceof Class) || (t instanceof GenericArrayType)) {
                    list.add(t);
                } else if (t instanceof ParameterizedType) {
                    list.add(((ParameterizedType) t).getRawType());
                } else {
                    list.add(null);
                }
            }
        }
        return list;
    }

    public static CodableClassInfo getClassFieldMap(Class<?> clazz) {
        CodableClassInfo fieldMap = fieldMaps.get(clazz);
        if (fieldMap == null) {
            fieldMap = new CodableClassInfo(clazz);
            fieldMaps.put(clazz, fieldMap);
        }
        return fieldMap;
    }

    public static void flushClassFieldMaps() {
        fieldMaps.clear();
    }

    public static final boolean isNative(Class<?> type) {
        return type == String.class || type == AtomicBoolean.class ||
               type == Boolean.class || type.isPrimitive() || Number.class.isAssignableFrom(type);
    }

    public static Object decodeEnum(Class<Enum> type, String text) {
        return Enum.valueOf(type, text);
    }

    public static Object decodeNative(Class<?> type, String text) {
        if (type == String.class) {
            return text;
        } else if (type == Integer.class || type == int.class) {
            return text != null ? Integer.parseInt(text) : 0;
        } else if (type == Long.class || type == long.class) {
            return text != null ? Long.parseLong(text) : 0L;
        } else if (type == Boolean.class || type == boolean.class) {
            return text != null && Boolean.parseBoolean(text);
        } else if (type == Short.class || type == short.class) {
            return text != null ? Short.parseShort(text) : 0;
        } else if (type == Double.class || type == double.class) {
            return text != null ? Double.parseDouble(text) : 0;
        } else if (type == Float.class || type == float.class) {
            return text != null ? Float.parseFloat(text) : 0;
        } else if (type == AtomicLong.class) {
            return text != null ? new AtomicLong(Long.parseLong(text)) : new AtomicLong(0);
        } else if (type == AtomicInteger.class) {
            return text != null ? new AtomicInteger(Integer.parseInt(text)) : new AtomicInteger(0);
        } else if (type == AtomicBoolean.class) {
            return text != null ? new AtomicBoolean(Boolean.parseBoolean(text)) : new AtomicBoolean(false);
        } else if (type.isEnum()) {
            return Enum.valueOf((Class<Enum>) type, text);
        } else {
            return text;
        }
    }

}
