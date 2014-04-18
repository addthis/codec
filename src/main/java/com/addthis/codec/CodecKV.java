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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.addthis.basis.kv.KVPair;
import com.addthis.basis.kv.KVPairs;
import com.addthis.basis.util.Base64;
import com.addthis.basis.util.Bytes;

public class CodecKV extends Codec {

    private static final CodecKV singleton = new CodecKV();

    private CodecKV() { }

    @SuppressWarnings("unused")
    public static CodecKV getSingleton() { return singleton; }

    @Override
    public Object decode(Object shell, byte[] data) throws Exception {
        return decodeString(getClassFieldMap(shell.getClass()), shell, new KVPairs(Bytes.toString(data)));
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
    public boolean storesNull(byte data[]) {
        throw new UnsupportedOperationException();
    }

    //    @SuppressWarnings("unchecked")
    public static String encodeString(Object object) throws Exception {
        if (object instanceof SuperCodable) {
            ((SuperCodable) object).preEncode();
        }
        KVPairs kv = new KVPairs();
        CodableClassInfo fieldMap = getClassFieldMap(object.getClass());
        if (fieldMap.size() == 0) {
            return object.toString();
        }
        String stype = fieldMap.getClassName(object);
        if (stype != null) {
            kv.putValue(fieldMap.getClassField(), stype);
        }
        for (Iterator<CodableFieldInfo> fields = fieldMap.values().iterator(); fields.hasNext();) {
            CodableFieldInfo field = fields.next();
            Object value = field.get(object);
            if (value == null) {
                continue;
            }
            String name = field.getName();
            if (field.isArray()) {
                int len = Array.getLength(value);
                if (field.getType() == byte.class) {
                    kv.putValue(name, new String(Base64.encode((byte[]) value)));
                } else {
                    for (int i = 0; i < len; i++) {
                        Object av = Array.get(value, i);
                        kv.putValue(name + i, encodeString(av));
                    }
                }
            } else if (field.isCollection()) {
                Collection<?> coll = (Collection<?>) value;
                int idx = 0;
                for (Iterator<?> iter = coll.iterator(); iter.hasNext(); idx++) {
                    kv.putValue(name + idx, encodeString(iter.next()));
                }
            } else if (field.isMap()) {
                Map<?, ?> map = (Map<?, ?>) value;
                KVPairs nv = new KVPairs();
                for (Entry<?, ?> entry : map.entrySet()) {
                    nv.putValue(entry.getKey().toString(), encodeString(entry.getValue()));
                }
                kv.putValue(field.getName(), nv.toString());
            } else if (field.isNative()) {
                kv.putValue(name, value.toString());
            } else if (field.isEnum()) {
                kv.putValue(name, value.toString());
            } else if (field.isCodable()) {
                kv.putValue(name, encodeString(value));
            }
        }
        return kv.toString();
    }

    public static Object decodeArray(Class<?> type, KVPairs data, String name) throws ArrayIndexOutOfBoundsException, IllegalArgumentException, Exception {
        if (type == byte.class) {
            String text = data.getValue(name);
            if (text != null) {
                return Base64.decode(text.toCharArray());
            }
        } else {
            List<String> arr = getList(data, name);
            int size = arr.size();
            if (size > 0) {
                Object value = Array.newInstance(type, size);
                for (int i = 0; i < size; i++) {
                    Array.set(value, i, decodeString(type, arr.get(i)));
                }
                return value;
            }
        }
        return null;
    }

    public static Object decodeString(Class<?> type, String kv) throws Exception {
        if (isNative(type)) {
            return decodeNative(type, kv);
        } else {
            return decodeString(getClassFieldMap(type), type, kv);
        }
    }

    public static Object decodeString(CodableClassInfo fieldMap, Class<?> type, String kv) throws Exception {
        if (fieldMap.size() == 0) {
            return decodeNative(type, kv);
        }
        return decodeKV(fieldMap, type, new KVPairs(kv));
    }

    public static Object decodeKV(Class<?> type, KVPairs data) throws Exception {
        return decodeString(getClassFieldMap(type), type, data);
    }

    public static Object decodeKV(CodableClassInfo fieldMap, Class<?> type, KVPairs data) throws Exception {
        String stype = data.getValue(fieldMap.getClassField());
        Class<?> atype = stype != null ? fieldMap.getClass(stype) : type;
        if (atype != null && atype != type) {
            fieldMap = getClassFieldMap(atype);
            type = atype;
        }
        Object object = type.newInstance();
        return decodeString(fieldMap, object, data);
    }

    public static Object decodeString(CodableClassInfo fieldMap, Object object, KVPairs data) throws Exception {
        for (Iterator<CodableFieldInfo> fields = fieldMap.values().iterator(); fields.hasNext();) {
            CodableFieldInfo field = fields.next();
            String name = field.getName();
            if (field.isArray()) {
                field.set(object, decodeArray(field.getType(), data, name));
            } else if (field.isCollection()) {
                List<String> arr = getList(data, name);
                int size = arr.size();
                if (size > 0) {
                    Collection<Object> value = (Collection<Object>) field.getType().newInstance();
                    Class<?> vc = field.getCollectionClass();
                    boolean va = field.isCollectionArray();
                    for (int i = 0; i < size; i++) {
                        value.add(decodeString(vc, arr.get(i)));
                    }
                    field.set(object, value);
                }
            } else if (field.isMap()) {
                Map<String, Object> map = (Map<String, Object>) field.getType().newInstance();
                // value type, assume key is String
                Class<?> kc = field.getMapKeyClass();
                Class<?> vc = field.getMapValueClass();
                for (KVPair p : new KVPairs(data.getValue(name))) {
                    map.put(p.getKey(), decodeString(vc, p.getValue()));
                }
                field.set(object, map);
            } else if (field.isCodable()) {
                field.set(object, decodeString(field.getType(), data.getValue(name)));
            } else if (field.isEnum()) {
                field.set(object, decodeEnum((Class<Enum>) field.getType(), data.getValue(name)));
            } else if (field.isNative()) {
                field.set(object, decodeNative(field.getType(), data.getValue(name)));
            }
        }
        if (object instanceof SuperCodable) {
            ((SuperCodable) object).postDecode();
        }
        return object;
    }

    private static List<String> getList(KVPairs data, String name) {
        List<String> arr = new ArrayList<String>();
        for (int i = 0;; i++) {
            String val = data.getValue(name + i);
            if (val != null) {
                arr.add(val);
            } else {
                break;
            }
        }
        return arr;
    }

    // compresses nested kv strings by tick-level-encoding
    public static String tickCode(String s) {
        char c[] = s.toCharArray();
        int mod;
        int pos;
        int pass = 0;
        boolean done;
        do {
            done = true;
            mod = 1;
            pos = 0;
            char nc[] = new char[c.length + 1];
            for (int i = 0; i < c.length; i++) {
                if (i > 0 && c[i] == '\0') {
                    break;
                }
                if (i == 0 && pass == 0) {
                    nc[pos++] = '&';
                }
                if (c[i] == '%' && i < c.length - 2) {
                    int hd1 = Bytes.hex2dec(c[i + 1]);
                    int hd2 = Bytes.hex2dec(c[i + 2]);
                    if (hd1 >= 0 && hd2 >= 0) {
                        char ch = (char) ((hd1 << 4) | hd2);
                        switch (ch) {
                            case '&':
                                for (int k = 0; k < pass + 1; k++) {
                                    nc[pos++] = '`';
                                }
                                nc[pos++] = '1';
                                mod++;
                                break;
                            case '=':
                                for (int k = 0; k < pass + 1; k++) {
                                    nc[pos++] = '`';
                                }
                                nc[pos++] = '2';
                                mod++;
                                break;
                            case '`':
                                nc[pos++] = '`';
                                nc[pos++] = '`';
                                break;
                            case '%':
                                mod++;
                                done = false;
                            default:
                                nc[pos++] = ch;
                                break;
                        }
                        i += 2;
                    } else {
                        nc[pos++] = c[i];
                    }
                } else if (c[i] == '+') {
                    nc[pos++] = ' ';
                    mod++;
                } else {
                    nc[pos++] = c[i];
                }
            }
            if (mod > 0) {
                c = nc;
            }
            pass++;
//            System.out.println(pass+" pass [ "+new String(nc,0,pos)+" ] "+c.length+" "+pos+" "+mod+" "+done);
        }
        while (!done);
//        c[pos++] = ' ';
        return new String(c, 0, pos);
    }

}
