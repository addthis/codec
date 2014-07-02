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

import javax.annotation.Nullable;

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

import com.addthis.codec.annotations.FieldConfig;
import com.addthis.codec.annotations.Pluggable;
import com.addthis.codec.plugins.PluginMap;
import com.addthis.codec.plugins.PluginRegistry;

import com.google.common.collect.ImmutableSortedMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("serial")
public final class CodableClassInfo {

    private static final Logger log = LoggerFactory.getLogger(CodableClassInfo.class);

    private final Class<?>  baseClass;
    private final PluginMap pluginMap;

    private final ImmutableSortedMap<String, CodableFieldInfo> classData;

    public Class<?> getBaseClass() {
        return baseClass;
    }

    public PluginMap getPluginMap() {
        return pluginMap;
    }

    @Nullable public Class<?> getArraySugar() {
        return pluginMap.arraySugar();
    }

    @Nullable public Class<?> getDefaultSugar() {
        return pluginMap.defaultSugar();
    }

    public String getClassField() {
        return pluginMap.classField();
    }

    public String getClassName(Object val) {
        if ((baseClass != null) && (val.getClass() != baseClass)) {
            return pluginMap.getClassName(val.getClass());
        } else {
            return null;
        }
    }

    public Class<?> getClass(String name) throws ClassNotFoundException {
        return pluginMap.getClass(name);
    }

    public CodableClassInfo(Class<?> clazz) {
        this(clazz, PluginRegistry.defaultRegistry());
    }

    public CodableClassInfo(Class<?> clazz, PluginRegistry pluginRegistry) {
        SortedMap<String, CodableFieldInfo> buildClassData = new TreeMap<>();

        // skip native classes
        if (Fields.isNative(clazz)) {
            classData = ImmutableSortedMap.<String, CodableFieldInfo>naturalOrder()
                                          .putAll(buildClassData).build();
            baseClass = null;
            pluginMap = PluginMap.EMPTY;
            return;
        }

        Class<?> findBaseClass = clazz;
        PluginMap findPluginMap = PluginMap.EMPTY;

        // get class annotations
        Class<?> ptr = clazz;
        while (ptr != null) {
            Pluggable pluggable = ptr.getAnnotation(Pluggable.class);
            if (pluggable != null) {
                String category = pluggable.value();
                findPluginMap = pluginRegistry.asMap().get(category);
                if (findPluginMap != null) {
                    findBaseClass = ptr;
                    break;
                } else {
                    log.warn("missing plugin map for {}, reached from {}", ptr, clazz);
                    findPluginMap = PluginMap.EMPTY;
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
            boolean store = ((mod & Modifier.FINAL) == 0) && ((mod & Modifier.PUBLIC) != 0);
            // extract annotations
            FieldConfig fieldConfigPolicy = field.getAnnotation(FieldConfig.class);
            if (fieldConfigPolicy != null) {
                field.setAccessible(true);
                store |= fieldConfigPolicy.codable();
            }
            // field must be public and non-final or annotated with a store policy
            if (!store) {
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
            CodableFieldInfo info = new CodableFieldInfo(field, type, fieldConfigPolicy);
            // extract info bits
            if (array) {
                info.updateBits(CodableFieldInfo.ARRAY);
            }
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
        pluginMap = findPluginMap;
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
            Type[] t = new Type[l.size()];
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
