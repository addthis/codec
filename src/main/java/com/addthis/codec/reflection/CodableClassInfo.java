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
package com.addthis.codec.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import com.addthis.codec.annotations.ClassConfig;
import com.addthis.codec.annotations.FieldConfig;
import com.addthis.codec.plugins.ClassMap;
import com.addthis.codec.plugins.ClassMapFactory;
import com.addthis.codec.codables.Codable;
import com.addthis.codec.validation.Validator;

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

    public Class<?> getArraySugar() {
        return (classMap != null) ? classMap.getArraySugar() : null;
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
        SortedMap<String, CodableFieldInfo> buildClassData = new TreeMap<>();

        // skip native classes
        if (Fields.isNative(clazz)) {
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
            ClassConfig classpolicy = ptr.getAnnotation(ClassConfig.class);
            if (classpolicy != null) {
                Class<? extends ClassMapFactory> cmf = classpolicy.classMapFactory();
                if ((cmf != null) && (cmf != ClassMapFactory.class)) {
                    try {
                        findClassMap = cmf.newInstance().getClassMap();
                        findBaseClass = ptr;
                        break;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                Class<? extends ClassMap> cm = classpolicy.classMap();
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
        Map<String, Field> fields = new HashMap<>();
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
            FieldConfig fieldConfigPolicy = field.getAnnotation(FieldConfig.class);
            if (fieldConfigPolicy != null) {
                codable = fieldConfigPolicy.codable();
                readonly = fieldConfigPolicy.readonly();
                writeonly = fieldConfigPolicy.writeonly();
                required = fieldConfigPolicy.required();
                intern = fieldConfigPolicy.intern();
                validator = fieldConfigPolicy.validator();
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
            if (Fields.isNative(type)) {
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
            if (!Fields.isNative(type)) {
                info.setGenericTypes(collectTypes(type, field.getGenericType()));
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

    public static Type[] collectTypes(Class<?> type, Type node) {
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
}
