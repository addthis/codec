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
package com.addthis.codec.config;

import com.addthis.codec.annotations.Bytes;
import com.addthis.codec.jackson.CodecJackson;
import com.addthis.codec.json.CodecJSON;
import com.addthis.codec.plugins.Greeter;
import com.addthis.codec.plugins.ParseGreetSub;
import com.addthis.maljson.JSONObject;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigObject;
import com.typesafe.config.ConfigValue;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.addthis.codec.config.Configs.decodeObject;
import static com.typesafe.config.ConfigFactory.parseResources;
import static com.typesafe.config.ConfigFactory.parseString;
import static org.junit.Assert.assertEquals;

public class FeatureTest {

    private static final Logger log = LoggerFactory.getLogger(FeatureTest.class);

    @Test
    public void greetDefault() throws Exception {
        Config greet = parseResources("config/defaultgreeter.conf");
        Greeter greeterObject = decodeObject(greet);
        assertEquals("Hello World! What a pleasant default-alias suffix we are having!",
                     greeterObject.greet());
    }

    @Test
    public void expandDefault() throws Exception {
        Config greet = parseResources("config/defaultgreeter.conf");
        ConfigObject resolved = (ConfigObject) Configs.expandSugar(greet, CodecConfig.getDefault());
        log.info("unresolved {}", greet.root().render());
        log.info("resolved {}", resolved.render());
        Greeter greeterObject = decodeObject(Greeter.class, resolved.toConfig());
        assertEquals("Hello World! What a pleasant default-alias suffix we are having!",
                     greeterObject.greet());
    }

    @Test
    public void greetArray() throws Exception {
        Config greet = parseResources("config/arraygreet.conf");
        Greeter greeterObject = decodeObject(greet);
        String expected = "Hello World! What a pleasant default suffix we are having!";
        expected = expected + expected;
        assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void autoArray() throws Exception {
        Config greet = parseResources("config/autoarraygreet.conf");
        Greeter greeterObject = decodeObject(greet);
        String expected = "Hello World! Where are all my friends?";
        assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void autoCollection() throws Exception {
        Config greet = parseString("greet.list {}");
        Greeter greeterObject = decodeObject(greet);
        String expected = "Hello World! Where are all my friends?";
        assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void alias() throws Exception {
        Config greet = parseString("greet.simpler {}");
        Greeter greeterObject = decodeObject(greet);
        String expected = "Hello World even simpler";
        assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void primary() throws Exception {
        Config greet = parseString("greet.multi-simple-primary: \" even simpler\" ");
        Greeter greeterObject = decodeObject(greet);
        String expected = "Hello World even simpler";
        assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void primaryArray() throws Exception {
        Config greet = parseString("greet.multi-array-primary: [a, b, c]");
        Greeter greeterObject = decodeObject(greet);
        String expected = "listing parts: [a, b, c]";
        assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void primaryNestedObject() throws Exception {
        Config greet = parseString("greet.parse-primary {simple.suffix = WOW}");
        Greeter greeterObject = decodeObject(greet);
        String expected = "extra extra! other Hello WorldWOW bytes: 2048 millis: 1000";
        assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void primaryNested() throws Exception {
        Config greet = parseString("greet.parse-simple: WOW");
        Greeter greeterObject = decodeObject(greet);
        String expected = "extra extra! other Hello WorldWOW bytes: 2048 millis: 1000";
        assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void inline() throws Exception {
        Config greet = parseString(
                "greet { multi-simple-primary: \" even simpler\", message: \" INLINED\" }");
        Greeter greeterObject = decodeObject(greet);
        String expected = "Hello World INLINED even simpler";
        assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void aliasInheritance() throws Exception {
        Config greet = parseString("greet.simplerer {}");
        Greeter greeterObject = decodeObject(greet);
        String expected = "Hello World even simpler";
        assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void arraySingle() throws Exception {
        Config greet = parseString("greet.ArrayGreet.phrases: [\"heya\"] ");
        Greeter greeterObject = decodeObject(greet);
        String expected = "heya";
        assertEquals(expected, greeterObject.greet());
    }

    static class BytesHolder {
        int bytes;

        public BytesHolder(@Bytes @JsonProperty("bytesParam") int bytes) {
            this.bytes = bytes;
        }

        @Bytes public void setBytes(int bytes) {
            this.bytes = bytes;
        }
    }

    @Test
    public void bytesSetter() throws Exception {
        Config byteHolderConfig = parseString("bytes: 1GB");
        BytesHolder bytesHolder = decodeObject(BytesHolder.class, byteHolderConfig);
        assertEquals(1073741824, bytesHolder.bytes);
    }

    @Test
    public void bytesConstructor() throws Exception {
        Config byteHolderConfig = parseString("bytesParam: 1GB");
        BytesHolder bytesHolder = decodeObject(BytesHolder.class, byteHolderConfig);
        assertEquals(1073741824, bytesHolder.bytes);
    }

    @Test
    public void bytesAndTime() throws Exception {
        Config greet = parseString("greet.parse {bytes: 1GB, millis: 5s}");
        Greeter greeterObject = decodeObject(greet);
        String expected = "bytes: 1073741824 millis: 5000";
        assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void enums() throws Exception {
        Config greet = parseString("greet.enum {timeUnit: [milliseconds, hours]}");
        Greeter greeterObject = decodeObject(greet);
        String expected = "[MILLISECONDS, HOURS]";
        assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void inheritance() throws Exception {
        Config greet = parseString(
                "bytes: 512KB, other: {enum.timeUnit: SECONDS}");
        Greeter greeterObject = decodeObject(ParseGreetSub.class, greet);
        String expected = "extra extra! other [SECONDS] bytes: 524288 millis: 1000";
        assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void concreteValue() throws Exception {
        ConfigValue greet = parseString("val = hi friend").getValue("val");
        Greeter greeterObject = CodecJackson.getDefault().decodeObject(ParseGreetSub.class, greet);
        String expected = "extra hi friend other Hello Worldnull bytes: 0 millis: 0";
        assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void inheritanceJson() throws Exception {
        Greeter greeterObject = CodecJSON.decodeObject(
                ParseGreetSub.class,
                new JSONObject("{bytes: 512, other: {enum: {timeUnit: [SECONDS]}}}"));
        String expected = "extra extra! other [SECONDS] bytes: 512 millis: 1000";
        assertEquals(expected, greeterObject.greet());
    }
}