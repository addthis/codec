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

import com.addthis.codec.util.CodableStatistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class Codec {

    private static final Logger log = LoggerFactory.getLogger(Codec.class);

    /**
     * all codecs must implement this
     */
    public abstract byte[] encode(Object obj) throws Exception;

    /**
     * all codecs must implement this
     */
    public abstract CodableStatistics statistics(Object obj) throws Exception;

    /**
     * all codecs must implement this
     */
    public abstract <T> T decode(T shell, byte[] data) throws Exception;

    /**
     * all codecs must implement this
     */
    public abstract boolean storesNull(byte[] data);

    /**
     * helper to decode into a class instead of object shell
     */
    public <T> T decode(Class<T> type, byte[] data) throws Exception {
        return decode(type.newInstance(), data);
    }
}
