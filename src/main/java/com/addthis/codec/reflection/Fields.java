package com.addthis.codec.reflection;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
public final class Fields {
    private Fields() {}

    private static final ConcurrentMap<Class<?>, CodableClassInfo> fieldMaps =
            new ConcurrentHashMap<>();

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

    public static boolean isNative(@Nonnull Class<?> type) {
        return (type == String.class) || (type == AtomicBoolean.class) ||
               (type == Boolean.class) || type.isPrimitive() || Number.class.isAssignableFrom(type);
    }

    @Nullable static Type[] collectTypes(Class<?> type, Type node) {
        List<Type> l = new ArrayList<>();
        collectTypes(l, type, node);
        while (!l.isEmpty()) {
            int ni = l.lastIndexOf(null);
            if (ni < 0) {
                break;
            }
            if (ni >= (l.size() - 1)) {
                l.remove(ni);
            } else {
                l.set(ni, l.get(l.size() - 1));
                l.remove(l.size() - 1);
            }
        }
        if (l.isEmpty()) {
            return null;
        } else {
            Type[] t = new Type[l.size()];
            l.toArray(t);
            return t;
        }
    }

    private static void collectTypes(@Nonnull List<Type> list, @Nullable Class<?> type, @Nullable Type node) {
        if ((type == null) && (node == null)) {
            return;
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
    }
}
