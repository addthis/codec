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
package com.addthis.codec.util;

import java.io.IOException;
import java.io.OutputStream;

import java.util.zip.GZIPOutputStream;

import com.addthis.basis.util.Bytes;

import com.addthis.codec.Codec;

public class CodableObjectWriter<T> {

    /** */
    public CodableObjectWriter(Codec codec, OutputStream out) {
        this.codec = codec;
        this.out = out;
    }

    private Codec codec;
    private OutputStream out;

    public void write(T obj) throws Exception {
        byte data[] = codec.encode(obj);
        Bytes.writeLength(data.length, out);
        out.write(data);
    }

    public void close() throws IOException {
        if (out instanceof GZIPOutputStream) {
            ((GZIPOutputStream) out).finish();
        }
        out.close();
    }
}
