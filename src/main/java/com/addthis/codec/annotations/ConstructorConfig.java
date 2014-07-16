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
package com.addthis.codec.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Codec settings for constructors. By default, most constructors will be ignored. Constructors
 * with special parameter types like "exactly one parameter that is a Config object" may be
 * automatically used by codecs that support them. In that case, such constructors will be expected
 * to a) optional for other codecs (if ever used) and b) provide complete initialization of the
 * class.
 *
 * If any constructor is given this annotation, then using one of them is considered required. Only
 * one constructor should be annotated although it is generally okay to annotate additional
 * constructors that meet special forms. This annotation takes an array of strings that define
 * a) the field names to be used to populate the parameters (in order) and/ or b) the field names to
 * be considered 'consumed' from the source. Special form constructors may use the default empty
 * array to signify "all". Any unconsumed source fields will try to be assigned to matching class
 * fields as normal.
 *
 * Note that the list of field to parameter names cannot be defaulted to the list of parameter names
 * given in the source constructor arguments because that information is difficult/ impossible to
 * obtain at runtime.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.CONSTRUCTOR)
public @interface ConstructorConfig {

    String[] value() default {};
}
