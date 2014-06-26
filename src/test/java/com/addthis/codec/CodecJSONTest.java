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

import com.addthis.codec.annotations.ArraySugar;
import com.addthis.codec.annotations.Field;
import com.addthis.codec.codables.Codable;
import com.addthis.codec.json.CodecExceptionLineNumber;
import com.addthis.codec.json.CodecJSON;
import com.addthis.codec.plugins.ClassMap;
import com.addthis.codec.plugins.ClassMapFactory;
import com.addthis.maljson.JSONException;
import com.addthis.maljson.JSONObject;

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

    @Field(classMapFactory = LetterMapFactory.class)
    public abstract static class AbstractLetter implements Codable {
    }

    private static final ClassMap letterMap = new ClassMap();

    public static class LetterMapFactory implements ClassMapFactory {

        public ClassMap getClassMap() {
            return letterMap;
        }
    }

    public static class C extends AbstractLetter {

        public int intField;
    }

    @ArraySugar
    public static class D extends AbstractLetter {

        public AbstractLetter[] letters;
    }

    public static class Holder implements Codable {

        public AbstractLetter thing;
    }

    @Test
    public void testPrimitiveArrayTypeMismatch() {
        boolean caught = false;
        try {
            CodecJSON.decodeObject(A.class, new JSONObject("{field1: [\"1\",{},3]}"));
        } catch (JSONException ex) {
        } catch (CodecExceptionLineNumber ex) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testObjectArrayTypeMismatch() {

        boolean caught = false;
        try {
            CodecJSON.decodeObject(B.class, new JSONObject("{field4: [[]]}"));
        } catch (JSONException ex) {
        } catch (CodecExceptionLineNumber ex) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void typeSugar() throws Exception {
        Holder object = CodecJSON.decodeObject(Holder.class, new JSONObject(
                "{thing: {com.addthis.codec.CodecJSONTest$C: {intField: 5}}}"));
        C asC = (C) object.thing;
        assertEquals(5, asC.intField);
    }

    @Test
    public void arraySugar() throws Exception {
        AbstractLetter.class.getAnnotation(Field.class)
                            .classMapFactory().newInstance().getClassMap()
                            .add(C.class).add(D.class);
        Holder object = CodecJSON.decodeObject(Holder.class, new JSONObject(
                "{thing: [" +
                "{C: {intField: 5}}, " +
                "{C: {intField: 6}}, " +
                "{C: {intField: 7}}, " +
                "]}"));
        D asD = (D) object.thing;
        assertEquals(6, ((C) asD.letters[1]).intField);
    }

}
