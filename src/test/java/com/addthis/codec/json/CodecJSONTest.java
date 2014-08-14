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

import com.addthis.codec.annotations.Pluggable;
import com.addthis.codec.codables.Codable;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CodecJSONTest {

    public static class A implements Codable {
        public String[] field1;
    }

    public static class B implements Codable {
        public A[] field4;
    }

    @Pluggable("letter")
    public abstract static class AbstractLetter implements Codable {
    }

    public static class C extends AbstractLetter {

        public int intField;
    }

    public static class D extends AbstractLetter {

        public AbstractLetter[] letters;
    }

    public static class Holder implements Codable {

        public AbstractLetter thing;
    }

    @Test
    public void testPrimitiveArrayTypeMismatch() throws IOException {
        boolean caught = false;
        try {
            CodecJSON.decodeString(A.class, "{field1: [\"1\",{},3]}");
        } catch (IOException ex) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testObjectArrayTypeMismatch() throws IOException {
        boolean caught = false;
        try {
            CodecJSON.decodeString(B.class, "{field4: [[]]}");
        } catch (IOException ex) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void typeSugar() throws Exception {
        Holder object = CodecJSON.decodeString(Holder.class,
                "{thing: {\"com.addthis.codec.json.CodecJSONTest$C\": {intField: 5}}}");
        C asC = (C) object.thing;
        assertEquals(5, asC.intField);
    }

    @Test
    public void arraySugar() throws Exception {
        Holder object = CodecJSON.decodeString(new Holder(),
                                               "{thing: [" +
                                               "{C: {intField: 5}}, " +
                                               "{C: {intField: 6}}, " +
                                               "{C: {intField: 7}} " +
                                               "]}");
        D asD = (D) object.thing;
        assertEquals(6, ((C) asD.letters[1]).intField);
    }

}
