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

import com.addthis.codec.jackson.MissingPropertyException;
import com.addthis.codec.letters.CC;
import com.addthis.codec.letters.F;
import com.addthis.codec.letters.RC;
import com.addthis.codec.reflection.RequiredFieldException;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public abstract class CodecBasicTests {

    protected Codec codec;
    protected CC sampleBean;
    protected byte[] byteEncodedSample;
    protected CC decodedBean;
    protected byte[] byteEncodedAgain;

    public abstract Codec getCodec();

    @Test
    public void encodeDecode() {
         assertEquals(sampleBean, decodedBean);
    }

    @Test
    public void codes() {
        assertArrayEquals(byteEncodedSample, byteEncodedAgain);
        assertTrue(decodedBean.check());
    }

    @Test
    public void subclasses() throws Exception {
        // check subclassing
        F f = new F().set();
        byte[] fbec = codec.encode(f);
        f = codec.decode(F.class, fbec);
        assertTrue(f.check());
    }

    @Test
    public void requiredFields() throws Exception {
        try {
            RC rc = new RC();
            codec.decode(RC.class, codec.encode(rc));
        } catch (Exception ex) {
            if ((ex instanceof RequiredFieldException)
                || (ex.getCause() instanceof RequiredFieldException)) {
                return;
            }
            if ((ex instanceof MissingPropertyException)
                || (ex.getCause() instanceof MissingPropertyException)) {
                return;
            }
        }
        fail("did not throw an exception for required field");
    }

    @Before
    public void setup() throws Exception {
        codec = getCodec();
        sampleBean = new CC().set();
        String cn = codec.getClass().getName();
        // encode
        byteEncodedSample = codec.encode(sampleBean);
        String s1 = "C->" + cn + " = " + bytesToString(byteEncodedSample, false);
        String s2 = "C->" + cn + " = " + bytesToString(byteEncodedSample, true);
        // decode
        decodedBean = codec.decode(new CC(), byteEncodedSample);
        byteEncodedAgain = codec.encode(decodedBean);
        String s3 = cn + "->C = " + bytesToString(byteEncodedAgain, false);
        String s4 = cn + "->C = " + bytesToString(byteEncodedAgain, true);
    }

    static String bytesToString(byte[] data, boolean printable) {
        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            String hex = Integer.toHexString((int) b);
            if (printable) {
                if (b > 32 && b < 127) {
                    sb.append((char) b);
                } else {
                    sb.append(' ');
                }
                if (hex.length() > 1) {
                    sb.append(' ');
                }
            } else {
                sb.append(hex);
            }
            sb.append(' ');
        }
        return sb.toString();
    }
}
