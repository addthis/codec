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

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.addthis.codec.Codec.ClassMap;
import com.addthis.codec.Codec.ClassMapFactory;
import com.addthis.codec.Codec.Codable;
import com.addthis.codec.Codec.Set;
import com.addthis.codec.Codec.Validator;

import com.google.common.collect.ImmutableSortedMap;

@SuppressWarnings("serial")
public final class CodableClassInfo {

    private final Class<?> baseClass;
    private final ClassMap classMap;
    private final ImmutableSortedMap<String, CodableFieldInfo> classData;

    public Class<?> getBaseClass() {
        return baseClass;
    }

    public ClassMap getClassMap() {
        return classMap;
    }

    public String getClassField() {
        return classMap != null ? classMap.getClassField() : "class";
    }

    public String getClassName(Object val) {
        if (classMap != null && val.getClass() != baseClass) {
            return classMap.getClassName(val.getClass());
        } else {
            return null;
        }
    }

    public Class<?> getClass(String name) throws Exception {
        return classMap != null ? classMap.getClass(name) : null;
    }

    public CodableClassInfo(Class<?> clazz) {
        SortedMap<String, CodableFieldInfo> buildClassData = new TreeMap<String, CodableFieldInfo>();

        // skip native classes
        if (Codec.isNative(clazz)) {
            classData = ImmutableSortedMap.
                    <String, CodableFieldInfo>naturalOrder().
                    putAll(buildClassData).build();
            baseClass = null;
            classMap = null;
            return;
        }

        Class<?> findBaseClass = clazz;
        ClassMap findClassMap = null;

        // get class annotations
        Class<?> ptr = clazz;
        while (ptr != null) {
            Annotation classpolicy = ptr.getAnnotation(Set.class);
            if (classpolicy != null) {
                Class<? extends ClassMapFactory> cmf = ((Set) classpolicy).classMapFactory();
                if (cmf != null && cmf != ClassMapFactory.class) {
                    try {
                        findClassMap = cmf.newInstance().getClassMap();
                        findBaseClass = ptr;
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                Class<? extends ClassMap> cm = ((Set) classpolicy).classMap();
                if (cm != null) {
                    try {
                        findClassMap = cm.newInstance();
                        findBaseClass = ptr;
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            ptr = ptr.getSuperclass();
        }
        HashMap<String, Field> fields = new HashMap<String, Field>();
        Class<?> clazzptr = clazz;
        while (clazzptr != null) {
            for (Field field : clazzptr.getDeclaredFields()) {
                if (fields.get(field.getName()) == null) {
                    fields.put(field.getName(), field);
                }
            }
            clazzptr = clazzptr.getSuperclass();
        }
        for (Field field : fields.values()) {
            int mod = field.getModifiers();
            boolean store = ((mod & Modifier.FINAL) == 0 && (mod & Modifier.PUBLIC) != 0);
            boolean codable = false;
            boolean readonly = false;
            boolean writeonly = false;
            boolean required = false;
            boolean intern = false;
            Class<? extends Validator> validator = null;
            // extract annotations
            Annotation policy = field.getAnnotation(Set.class);
            if (policy != null) {
                Set setPolicy = (Set) policy;
                codable = setPolicy.codable();
                readonly = setPolicy.readonly();
                writeonly = setPolicy.writeonly();
                required = setPolicy.required();
                intern = setPolicy.intern();
                validator = setPolicy.validator();
                field.setAccessible(true);
                if (!codable) {
                    continue;
                }
            }
            // field must be public and non-final or annotated with a store
            // policy
            if (!(store || codable)) {
                continue;
            }
            Class<?> type = field.getType();
            boolean array = type.isArray();
            if (array) {
                type = type.getComponentType();
                if (type == null) {
                    System.out.println("!! null array type for " + field + " !!");
                }
            }
            CodableFieldInfo info = new CodableFieldInfo();
            info.setField(field);
            // extract info bits
            if (array) {
                info.updateBits(CodableFieldInfo.ARRAY);
            }
            if (readonly) {
                info.updateBits(CodableFieldInfo.READONLY);
            }
            if (writeonly) {
                info.updateBits(CodableFieldInfo.WRITEONLY);
            }
            if (codable || Codable.class.isAssignableFrom(type)) {
                info.updateBits(CodableFieldInfo.CODABLE);
            }
            if (Collection.class.isAssignableFrom(type)) {
                info.updateBits(CodableFieldInfo.COLLECTION);
            }
            if (Map.class.isAssignableFrom(type)) {
                info.updateBits(CodableFieldInfo.MAP);
            }
            if (type.isEnum()) {
                info.updateBits(CodableFieldInfo.ENUM);
            }
            if (Number.class.isAssignableFrom(type)) {
                info.updateBits(CodableFieldInfo.NUMBER);
            }
            if (Codec.isNative(type)) {
                info.updateBits(CodableFieldInfo.NATIVE);
            }
            if (required) {
                info.updateBits(CodableFieldInfo.REQUIRED);
            }
            if (intern) {
                info.updateBits(CodableFieldInfo.INTERN);
            }
            if (validator != null) {
                try {
                    info.setValidator(validator.newInstance());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            info.setType(type);
            // extract generics info
            if (!Codec.isNative(type)) {
                info.setGenericTypes(Codec.collectTypes(type, field.getGenericType()));
            }
            buildClassData.put(field.getName(), info);
        }
        classData = ImmutableSortedMap.
                <String, CodableFieldInfo>naturalOrder().
                putAll(buildClassData).build();
        baseClass = findBaseClass;
        classMap = findClassMap;
    }

    public int size() {
        return classData.size();
    }

    public Collection<CodableFieldInfo> values() {
        return classData.values();
    }
}
