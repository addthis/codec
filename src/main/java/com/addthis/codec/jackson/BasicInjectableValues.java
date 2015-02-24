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
package com.addthis.codec.jackson;

import java.lang.reflect.Type;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.InjectableValues;

public class BasicInjectableValues extends InjectableValues {
    @Override public Object findInjectableValue(Object valueId,
                                                DeserializationContext ctxt,
                                                BeanProperty forProperty,
                                                Object beanInstance) {
        if (TypeReference.class.getName().equals(valueId)) {
            return new StoredTypeReference(forProperty.getType().containedType(0));
        }
        return null;
    }

    private static class StoredTypeReference extends TypeReference<Void> {
        private final Type containedType;

        public StoredTypeReference(Type containedType) {
            this.containedType = containedType;
        }

        @Override public Type getType() {
            return containedType;
        }
    }
}
