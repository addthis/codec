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

import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;

import com.addthis.codec.json.CodecExceptionLineNumber;
import com.addthis.maljson.LineNumberInfo;

import com.addthis.codec.validation.ValidationException;
import com.addthis.codec.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * information about a field in a class - expensive to get so runs and gets
 * cached
 */
public final class CodableFieldInfo<T> {

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
    public static final int INTERN     = 1 << 11;

    private Field     field;
    private Class<T>  type;
    private int       bits;
    private Validator validator;
    private Type[]    genTypes;
    private boolean[] genArray;

    public void setField(Field field) {
        this.field = field;
    }

    public Field getField() {
        return field;
    }

    public void updateBits(int bits) {
        this.bits |= bits;
    }

    public String getName() {
        return field.getName();
    }

    public void setType(Class<T> type) {
        this.type = type;
    }

    public Class<T> getType() {
        return type;
    }

    public Type[] getGenericTypes() {
        return genTypes;
    }

    public void setGenericTypes(final Type[] genTypes) {
        if (genTypes == null) {
            return;
        }
        boolean[] gen = new boolean[genTypes.length];
        for (int i = 0; i < genTypes.length; i++) {
            Type currentType = genTypes[i];
            if (currentType instanceof GenericArrayType) {
                gen[i] = true;
                genTypes[i] = ((GenericArrayType) currentType).getGenericComponentType();
            } else if ((currentType instanceof Class) && ((Class) currentType).isArray()) {
                gen[i] = true;
                genTypes[i] = ((Class) currentType).getComponentType();
            } else {
                gen[i] = false;
            }
        }
        this.genTypes = genTypes;
        this.genArray = gen;
    }

    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    public Object newInstance() throws Exception {
        return type.newInstance();
    }

    public Class<?> getCollectionClass() {
        return (genTypes != null && genTypes.length == 1) ? (Class<?>) genTypes[0] : null;
    }

    public Class<?> getMapKeyClass() {
        return (genTypes != null && genTypes.length == 2) ? (Class<?>) genTypes[0] : null;
    }

    public Class<?> getMapValueClass() {
        return (genTypes != null && genTypes.length == 2) ? (Class<?>) genTypes[1] : null;
    }

    public boolean isCollectionArray() {
        return (genArray != null && genArray.length == 1) ? genArray[0] : false;
    }

    public boolean isMapKeyArray() {
        return (genArray != null && genArray.length == 2) ? genArray[0] : false;
    }

    public boolean isMapValueArray() {
        return (genArray != null && genArray.length == 2) ? genArray[1] : false;
    }

    public boolean validate(Object value) {
        return validator != null ? validator.validate(this, value) : true;
    }

    public Object get(Object src) {
        try {
            return field.get(src);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Variation of {@link #set(Object, Object)} with line numbers.
     */
    public void set(Object dst, LineNumberInfo dstInfo, Object value,
            LineNumberInfo valInfo) throws CodecExceptionLineNumber {

        if (value == null) {
            Object currentValue;
            try {
                currentValue = get(dst);
            } catch (Exception ex) {
                throw new CodecExceptionLineNumber(ex, dstInfo);
            }

            if (isRequired() && currentValue == null) {
                Exception ex = new RequiredFieldException("missing required field '" +
                    this.getName() + "' for " + dst, getName());
                throw new CodecExceptionLineNumber(ex, dstInfo);
            }
            return;
        }
        if (!validate(value)) {
            Exception ex = new ValidationException("invalid field value '" + value +
                "' for " + this.getName() + " in " + dst, getName());
            throw new CodecExceptionLineNumber(ex, valInfo);
        }
        try {
            if (value.getClass() == String.class && isInterned()) {
                value = ((String) value).intern();
            }
            field.set(dst, value);
        } catch (Exception ex) {
            throw new CodecExceptionLineNumber(ex.getMessage(), valInfo);
        }
    }

    public void set(Object dst, Object value) {
        if (value == null) {
            if (isRequired() && get(dst) == null) {
                throw new RequiredFieldException("missing required field '" +
                    this.getName() + "' for " + dst, getName());
            }
            return;
        }
        if (!validate(value)) {
            throw new ValidationException("invalid field value '" + value + "' for " +
                this.getName() + " in " + dst, getName());
        }
        try {
            if (value.getClass() == String.class && isInterned()) {
                value = ((String) value).intern();
            }
            field.set(dst, value);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("error setting (" + value + ")(" + value.getClass() +
                ") on (" + dst + ") in " + toString());
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

    public boolean isWriteOnly() {
        return (bits & WRITEONLY) == WRITEONLY;
    }

    public boolean isInterned() {
        return (bits & INTERN) == INTERN;
    }

    public String toString() {
        return "[" + getName() + "," + type + (isArray() ? "[]," : ",") + Integer.toString(bits, 2) + "]";
    }
}
