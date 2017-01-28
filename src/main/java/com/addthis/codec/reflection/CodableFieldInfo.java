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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

import java.util.Collection;
import java.util.Map;

import com.addthis.codec.annotations.FieldConfig;
import com.addthis.codec.codables.Codable;

import com.google.common.annotations.Beta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * information about a field in a class - expensive to get so runs and gets cached
 */
@Beta
public final class CodableFieldInfo {

    private static final Logger log = LoggerFactory.getLogger(CodableFieldInfo.class);

    public static final int ARRAY      = 1 << 0;
    public static final int CODABLE    = 1 << 1;
    public static final int COLLECTION = 1 << 2;
    public static final int GENERIC    = 1 << 3;
    public static final int NATIVE     = 1 << 4;
    public static final int MAP        = 1 << 5;
    public static final int NUMBER     = 1 << 6;
    public static final int REQUIRED   = 1 << 7;
    public static final int READONLY   = 1 << 8;
    public static final int WRITEONLY  = 1 << 9;
    public static final int ENUM       = 1 << 10;
    public static final int NARROW     = 1 << 11;

    @Nonnull private final Field    field;
    @Nonnull private final Class<?> typeOrComponentType;
    private final int bits;

    @Nullable private final FieldConfig fieldConfig;
    @Nullable private final Type[]      genTypes;
    @Nullable private final boolean[]   genArray;

    public CodableFieldInfo(@Nonnull Field field) {
        this.field = field;
        field.setAccessible(true);
        fieldConfig = field.getAnnotation(FieldConfig.class);

        Class<?> type = field.getType();
        boolean array = type.isArray();
        if (array) {
            typeOrComponentType = type.getComponentType();
            this.bits = cacheFlags(CodableFieldInfo.ARRAY);
        } else {
            typeOrComponentType = type;
            this.bits = cacheFlags(0);
        }
        // extract generics info
        if (!Fields.isNative(typeOrComponentType)) {
            genTypes = Fields.collectTypes(typeOrComponentType, field.getGenericType());
        } else {
            genTypes = null;
        }
        if (genTypes == null) {
            genArray = null;
        } else {
            genArray = new boolean[genTypes.length];
            mutateGenericTypes(genTypes, genArray);
        }
    }

    private int cacheFlags(int externalBits) {
        int partialBits = externalBits;
        if (Codable.class.isAssignableFrom(typeOrComponentType)) {
            partialBits |= CodableFieldInfo.CODABLE;
        }
        if (Collection.class.isAssignableFrom(typeOrComponentType)) {
            partialBits |= CodableFieldInfo.COLLECTION;
        }
        if (Map.class.isAssignableFrom(typeOrComponentType)) {
            partialBits |= CodableFieldInfo.MAP;
        }
        if (typeOrComponentType.isEnum()) {
            partialBits |= CodableFieldInfo.ENUM;
        }
        if (Number.class.isAssignableFrom(typeOrComponentType)) {
            partialBits |= CodableFieldInfo.NUMBER;
        }
        if (Fields.isNative(typeOrComponentType)) {
            partialBits |= CodableFieldInfo.NATIVE;
        }
        if (fieldConfig != null) {
            if (fieldConfig.readonly()) {
                partialBits |= CodableFieldInfo.READONLY;
            }
            if (fieldConfig.writeonly()) {
                partialBits |= CodableFieldInfo.WRITEONLY;
            }
            if (fieldConfig.codable()) {
                partialBits |= CodableFieldInfo.CODABLE;
            }
            if (fieldConfig.required()) {
                partialBits |= CodableFieldInfo.REQUIRED;
            }
            if (fieldConfig.narrow()) {
                partialBits |= CodableFieldInfo.NARROW;
            }
        }
        return partialBits;
    }

    @Nonnull public Field getField() {
        return field;
    }

    public String getName() {
        return field.getName();
    }

    @Nonnull public Class<?> getTypeOrComponentType() {
        return typeOrComponentType;
    }

    @Nullable public Type[] getGenericTypes() {
        return genTypes;
    }

    // interacts with the ill-defined generic support
    private void mutateGenericTypes(@Nonnull final Type[] collectedTypes,
                                    @Nonnull final boolean[] genericFlags) {
        for (int i = 0; i < collectedTypes.length; i++) {
            Type currentType = collectedTypes[i];
            if (currentType instanceof GenericArrayType) {
                genericFlags[i] = true;
                collectedTypes[i] = ((GenericArrayType) currentType).getGenericComponentType();
            } else if ((currentType instanceof Class) && ((Class) currentType).isArray()) {
                genericFlags[i] = true;
                collectedTypes[i] = ((Class) currentType).getComponentType();
            } else {
                genericFlags[i] = false;
            }
        }
    }

    public Object newInstance() throws Exception {
        return typeOrComponentType.newInstance();
    }

    @Nullable public Class<?> getCollectionClass() {
        return ((genTypes != null) && (genTypes.length == 1)) ? (Class<?>) genTypes[0] : null;
    }

    @Nullable public Class<?> getMapKeyClass() {
        return ((genTypes != null) && (genTypes.length == 2)) ? (Class<?>) genTypes[0] : null;
    }

    @Nullable public Class<?> getMapValueClass() {
        return ((genTypes != null) && (genTypes.length == 2)) ? (Class<?>) genTypes[1] : null;
    }

    public boolean isCollectionArray() {
        return ((genArray != null) && (genArray.length == 1)) ? genArray[0] : false;
    }

    public boolean isMapKeyArray() {
        return ((genArray != null) && (genArray.length == 2)) ? genArray[0] : false;
    }

    public boolean isMapValueArray() {
        return ((genArray != null) && (genArray.length == 2)) ? genArray[1] : false;
    }

    public Object get(Object src) {
        try {
            return field.get(src);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sets the field value for the destination object if and only if it is non-null and passes the field's
     * validation method (if any). If the value is null and the field is marked as required, then an exception
     * is thrown, otherwise the null value is ignored and the current value (if any) is kept.
     */
    public void setStrict(@Nonnull Object dst, @Nullable Object value) throws IllegalAccessException {
        if (value == null) {
            if (isRequired()) {
                throw new RequiredFieldException("missing required field '" +
                                                 this.getName() + "' for " + dst, getName());
            }
            return;
        }
        field.set(dst, value);
    }

    public void set(@Nonnull Object dst, @Nullable Object value) {
        if (value == null) {
            if (isRequired() && (get(dst) == null)) {
                throw new RequiredFieldException("missing required field '" +
                    this.getName() + "' for " + dst, getName());
            }
            return;
        }
        try {
            field.set(dst, value);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("error setting ({})({}) on ({}) in {}", value, value.getClass(), dst, toString());
            throw new RuntimeException(ex);
        }
    }

    public boolean isArray() {
        return (bits & ARRAY) == ARRAY;
    }

    public boolean isCodable() {
        return (bits & CODABLE) == CODABLE;
    }

    public boolean isCollection() {
        return (bits & COLLECTION) == COLLECTION;
    }

    public boolean isGeneric() {
        return (bits & GENERIC) == GENERIC;
    }

    public boolean isMap() {
        return (bits & MAP) == MAP;
    }

    public boolean isEnum() {
        return (bits & ENUM) == ENUM;
    }

    public boolean isNative() {
        return (bits & NATIVE) == NATIVE;
    }

    public boolean isRequired() {
        return (bits & REQUIRED) == REQUIRED;
    }

    public boolean isReadOnly() {
        return (bits & READONLY) == READONLY;
    }

    public boolean isNarrow() { return (bits & NARROW) == NARROW; }

    public boolean isWriteOnly() {
        return (bits & WRITEONLY) == WRITEONLY;
    }

    public String toString() {
        return "[" + getName() + "," + typeOrComponentType + (isArray() ? "[]," : ",") + Integer.toString(bits, 2) + "]";
    }
}
