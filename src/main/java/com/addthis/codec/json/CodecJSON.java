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

import java.io.IOException;

import com.addthis.codec.Codec;
import com.addthis.codec.jackson.CodecJackson;
import com.addthis.codec.util.CodableStatistics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CodecJSON extends Codec {
    private CodecJSON() {}

    private static final Logger log = LoggerFactory.getLogger(CodecJSON.class);

    public static final CodecJSON INSTANCE = new CodecJSON();

    @Override
    public byte[] encode(Object obj) throws Exception {
        return CodecJackson.getDefault().getObjectMapper().writeValueAsBytes(obj);
    }

    @Override
    public CodableStatistics statistics(Object obj) {
        throw new UnsupportedOperationException();
    }

    @Override
    public <T> T decode(T shell, byte[] data) throws IOException {
        return CodecJackson.getDefault().getObjectMapper().readerForUpdating(shell).readValue(data);
    }

    @Override
    public <T> T decode(Class<T> type, byte[] data) throws Exception {
        return CodecJackson.getDefault().getObjectMapper().reader(type).readValue(data);
    }

    @Override
    public boolean storesNull(byte[] data) {
        throw new UnsupportedOperationException();
    }

    public static JsonNode encodeJSON(Object object) throws Exception {
        return CodecJackson.getDefault().getObjectMapper().valueToTree(object);
    }

    public static String encodeString(Object object) throws JsonProcessingException {
        return CodecJackson.getDefault().getObjectMapper().writeValueAsString(object);
    }

    public static String tryEncodeString(Object object, String defaultValue) {
        try {
            return CodecJackson.getDefault().getObjectMapper().writeValueAsString(object);
        } catch (JsonProcessingException ex) {
            return defaultValue;
        }
    }

    public static <T> T decodeString(T object, String json) throws IOException {
        return CodecJackson.getDefault().getObjectMapper().readerForUpdating(object).readValue(json);
    }

    public static <T> T decodeString(Class<T> type, String json) throws IOException {
        return CodecJackson.getDefault().getObjectMapper().reader(type).readValue(json);
    }

    public static <T> T decodeJSON(T object, JsonNode json) throws IOException {
        return CodecJackson.getDefault().getObjectMapper().readerForUpdating(object).readValue(json);
    }
}
