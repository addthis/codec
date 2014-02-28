package com.addthis.codec;

import com.addthis.maljson.JSONException;
import com.addthis.maljson.JSONObject;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CodecJSONTest {

    public static class A implements Codec.Codable {
        public String[] field1;
    }

    public static class B implements Codec.Codable {
        public A[] field4;
    }


    @Test
    public void testPrimitiveArrayTypeMismatch() {

        boolean caught = false;
        try {
            CodecJSON.decodeObject(A.class, new JSONObject("{field1: [\"1\",{},3]}"));
        } catch(JSONException ex) {
        } catch(CodecExceptionLineNumber ex) {
            caught = true;
        }
        assertTrue(caught);
    }

    @Test
    public void testObjectArrayTypeMismatch() {

        boolean caught = false;
        try {
            CodecJSON.decodeObject(B.class, new JSONObject("{field4: [[]]}"));
        } catch(JSONException ex) {
        } catch(CodecExceptionLineNumber ex) {
            caught = true;
        }
        assertTrue(caught);
    }


}
