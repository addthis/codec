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
package com.addthis.codec;

import java.io.IOException;
import java.io.InputStream;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.addthis.basis.util.Bytes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("unchecked")
public class CodableObjectReader<T> implements Iterator {

    private static final Logger log = LoggerFactory.getLogger(CodableObjectReader.class);

    /** */
    public CodableObjectReader(Codec codec, Class<? extends T> type, InputStream in) {
        this.codec = codec;
        this.type = type;
        this.in = in;
    }

    Codec codec;
    Class<? extends T> type;
    InputStream in;
    T next;

    public void close() throws IOException {
        in.close();
    }

    public boolean hasNext() {
        try {
            if (next == null && in.available() > 0) {
                try {
                    int len = (int) Bytes.readLength(in);
                    byte data[] = Bytes.readBytes(in, len);
                    next = codec.decode(type, data);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        } catch (IOException e) {
            log.warn("hasNext(): " + e.toString(), e);
        }
        return next != null;
    }

    public T next() {
        if (hasNext()) {
            T ret = next;
            next = null;
            return ret;
        } else {
            throw new NoSuchElementException();
        }
    }

    public void remove() {
    }
}
