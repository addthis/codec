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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.addthis.codec.validation.Truthinator;
import com.addthis.codec.validation.Validator;

/**
 * control coding parameters for fields. allows code to dictate non-codable
 * fields as codable
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface FieldConfig {

    boolean codable() default true;

    boolean readonly() default false;

    boolean writeonly() default false;

    boolean required() default false;

    boolean intern() default false;

    Class<? extends Validator> validator() default Truthinator.class;
}
