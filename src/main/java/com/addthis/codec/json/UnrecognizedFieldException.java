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
package com.addthis.codec.json;

import javax.annotation.Nonnull;

import com.addthis.maljson.LineNumberInfo;

@SuppressWarnings("serial")
public class UnrecognizedFieldException extends CodecExceptionLineNumber {

    private String field;
    private Class clazz;

    public UnrecognizedFieldException(String msg,
                                      @Nonnull LineNumberInfo info,
                                      String field,
                                      Class clazz) {
        super(msg, info);
        this.field = field;
        this.clazz = clazz;
    }

    public String getField() {
        return field;
    }

    public Class getErrorClass() {
        return clazz;
    }
}
