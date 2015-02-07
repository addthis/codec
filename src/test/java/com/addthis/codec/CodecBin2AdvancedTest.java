package com.addthis.codec;

import com.addthis.codec.annotations.FieldConfig;
import com.addthis.codec.binary.CodecBin2;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class CodecBin2AdvancedTest {

    private static long[] legalValues = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};

    private static class NarrowField {

        @FieldConfig(narrow = true)
        private long unboxed;

        @FieldConfig(narrow = true)
        private Long boxed;

    }

    @Test
    public void narrowEmptyValues() throws Exception {
        NarrowField input = new NarrowField();
        NarrowField output = new NarrowField();
        byte[] serialization;
        serialization = CodecBin2.encodeBytes(input);
        CodecBin2.decodeBytes(output, serialization);
        assertEquals(input.boxed, output.boxed);
        assertEquals(input.unboxed, output.unboxed);
    }

    @Test
    public void narrowLegalValues() throws Exception {
        NarrowField input = new NarrowField();
        byte[] serialization;
        for (int i = 0; i < legalValues.length; i++) {
            NarrowField output = new NarrowField();
            input.boxed = legalValues[i];
            input.unboxed = legalValues[i];
            serialization = CodecBin2.encodeBytes(input);
            CodecBin2.decodeBytes(output, serialization);
            assertEquals(input.boxed, output.boxed);
            assertEquals(input.unboxed, output.unboxed);
        }
    }

    @Test
    public void narrowIllegalValues() throws Exception {
        NarrowField input = new NarrowField();
        NarrowField output = new NarrowField();
        input.boxed = new Long(Integer.MAX_VALUE + 1l);
        input.unboxed = Integer.MAX_VALUE + 1l;
        byte[] serialization;
        serialization = CodecBin2.encodeBytes(input);
        CodecBin2.decodeBytes(output, serialization);
        assertNotEquals(input.boxed, output.boxed);
        assertNotEquals(input.unboxed, output.unboxed);
        assertEquals(new Long(Integer.MIN_VALUE), output.boxed);
        assertEquals(new Long(Integer.MIN_VALUE).longValue(), output.unboxed);
    }
}
