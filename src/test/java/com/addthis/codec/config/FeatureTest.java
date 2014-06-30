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

import com.addthis.codec.plugins.Greeter;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import org.junit.Assert;
import org.junit.Test;

public class FeatureTest {

    @Test
    public void greetDefault() throws Exception {
        Config greet = ConfigFactory.parseResources("config/defaultgreeter.conf");
        Greeter greeterObject = CodecConfig.getDefault().decodeObject(greet);
        Assert.assertEquals("Hello World! What a pleasant default suffix we are having!",
                            greeterObject.greet());
    }

    @Test
    public void greetArray() throws Exception {
        Config greet = ConfigFactory.parseResources("config/arraygreet.conf");
        Greeter greeterObject = CodecConfig.getDefault().decodeObject(greet);
        String expected = "Hello World! What a pleasant default suffix we are having!";
        expected = expected + expected;
        Assert.assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void autoArray() throws Exception {
        Config greet = ConfigFactory.parseResources("config/autoarraygreet.conf");
        Greeter greeterObject = CodecConfig.getDefault().decodeObject(greet);
        String expected = "Hello World! Where are all my friends?";
        Assert.assertEquals(expected, greeterObject.greet());
    }

    @Test
    public void autoCollection() throws Exception {
        Config greet = ConfigFactory.parseString("greet.list {}");
        Greeter greeterObject = CodecConfig.getDefault().decodeObject(greet);
        String expected = "Hello World! Where are all my friends?";
        Assert.assertEquals(expected, greeterObject.greet());
    }
}