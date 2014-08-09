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

import java.util.Collection;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.exc.PropertyBindingException;

public class MissingPropertyException extends PropertyBindingException {

    public MissingPropertyException(String msg,
                                    JsonLocation loc,
                                    Class<?> referringClass,
                                    String propName,
                                    Collection<Object> propertyIds) {
        super(msg, loc, referringClass, propName, propertyIds);
    }

    public static MissingPropertyException from(JsonParser jp,
                                                Object fromObjectOrClass, String propertyName,
                                                Collection<Object> propertyIds) {
        if (fromObjectOrClass == null) {
            throw new IllegalArgumentException();
        }
        Class<?> ref;
        if (fromObjectOrClass instanceof Class<?>) {
            ref = (Class<?>) fromObjectOrClass;
        } else {
            ref = fromObjectOrClass.getClass();
        }
        String msg = "Missing field \""+propertyName+"\" (class "+ref.getName()+"), marked as required";
        MissingPropertyException e = new MissingPropertyException(
                msg, jp.getCurrentLocation(), ref, propertyName, propertyIds);
        // but let's also ensure path includes this last (missing) segment
        e.prependPath(fromObjectOrClass, propertyName);
        return e;
    }
}
