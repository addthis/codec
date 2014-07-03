package com.addthis.codec.reflection;

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

    public static boolean isNative(Class<?> type) {
        return (type == String.class) || (type == AtomicBoolean.class) ||
               (type == Boolean.class) || type.isPrimitive() || Number.class.isAssignableFrom(type);
    }
}
