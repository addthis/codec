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

public class CodecExceptionLineNumber extends Exception {

    @Nonnull
    final LineNumberInfo info;

    public CodecExceptionLineNumber(String msg) {
        super(msg);
        this.info = LineNumberInfo.MissingInfo;
    }

    public CodecExceptionLineNumber(Throwable parent) {
        super(parent);
        this.info = LineNumberInfo.MissingInfo;
    }

    public CodecExceptionLineNumber(String msg, @Nonnull LineNumberInfo info) {
        super((info == LineNumberInfo.MissingInfo) ? msg :
              msg + " at line " + (info.getLine() + 1) +
              " and column " + (info.getColumn() + 1));
        this.info = info;
    }

    public CodecExceptionLineNumber(Throwable parent, @Nonnull LineNumberInfo info) {
        super(parent.getMessage() == null ? parent.toString() :
              (info == LineNumberInfo.MissingInfo) ? parent.getMessage() :
              parent.getMessage() + " at line " + (info.getLine() + 1) +
              " and column " + (info.getColumn() + 1), parent);
        this.info = info;
    }

    public LineNumberInfo getLineNumberInfo() {
        return info;
    }

    public int getLine() {
        return info.getLine();
    }

    public int getColumn() {
        return info.getColumn();
    }
}
