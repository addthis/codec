package com.addthis.codec;

import com.addthis.codec.annotations.FieldConfig;
import com.addthis.codec.binary.CodecBin2;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class CodecBin2NarrowFieldTest {

    private static long[] legalValues = {Integer.MIN_VALUE, -1, 0, 1, Integer.MAX_VALUE};

    private static long illegalValue = Integer.MAX_VALUE + 1l;

    private static interface NarrowInterface {

        public long getUnboxed();

        public void setUnboxed(long unboxed);

        public Long getBoxed();

        public void setBoxed(Long boxed);

        public NarrowInterface create();

    }

    private static class NarrowField implements NarrowInterface {

        @FieldConfig(narrow = true)
        private long unboxed;

        @FieldConfig(narrow = true)
        private Long boxed;

        public long getUnboxed() {
            return unboxed;
        }

        public void setUnboxed(long unboxed) {
            this.unboxed = unboxed;
        }

        public Long getBoxed() {
            return boxed;
        }

        public void setBoxed(Long boxed) {
            this.boxed = boxed;
        }

        public NarrowField create() {
            return new NarrowField();
        }

    }

    /**
     * Sanity test to verify regular Long fields continue to work.
     */
    private static class WideField implements NarrowInterface {

        @FieldConfig
        private long unboxed;

        @FieldConfig
        private Long boxed;

        public long getUnboxed() {
            return unboxed;
        }

        public void setUnboxed(long unboxed) {
            this.unboxed = unboxed;
        }

        public Long getBoxed() {
            return boxed;
        }

        public void setBoxed(Long boxed) {
            this.boxed = boxed;
        }

        public WideField create() {
            return new WideField();
        }

    }

    @Test
    public void narrowEmptyValues() throws Exception {
        NarrowField inputN = new NarrowField();
        NarrowField outputN = new NarrowField();
        WideField inputW = new WideField();
        WideField outputW = new WideField();
        narrowEmptyValues(inputN, outputN);
        narrowEmptyValues(inputW, outputW);
    }

    private void narrowEmptyValues(NarrowInterface input, NarrowInterface output) throws Exception {
        byte[] serialization;
        serialization = CodecBin2.encodeBytes(input);
        CodecBin2.decodeBytes(output, serialization);
        assertEquals(input.getBoxed(), output.getBoxed());
        assertEquals(input.getUnboxed(), output.getUnboxed());
    }

    @Test
    public void narrowLegalValues() throws Exception {
        NarrowField inputN = new NarrowField();
        WideField inputW = new WideField();
        narrowLegalValues(inputN);
        narrowLegalValues(inputW);
    }

    private void narrowLegalValues(NarrowInterface input) throws Exception {
        byte[] serialization;
        for (int i = 0; i < legalValues.length; i++) {
            NarrowInterface output = input.create();
            input.setBoxed(legalValues[i]);
            input.setUnboxed(legalValues[i]);
            serialization = CodecBin2.encodeBytes(input);
            CodecBin2.decodeBytes(output, serialization);
            assertEquals(input.getBoxed(), output.getBoxed());
            assertEquals(input.getUnboxed(), output.getUnboxed());
        }
    }

    @Test
    public void narrowIllegalValues() throws Exception {
        NarrowField inputN = new NarrowField();
        NarrowField outputN = new NarrowField();
        WideField inputW = new WideField();
        WideField outputW = new WideField();
        narrowIllegalValues(inputN, outputN, true);
        narrowIllegalValues(inputW, outputW, false);
    }

    private void narrowIllegalValues(NarrowInterface input, NarrowInterface output, boolean narrow) throws Exception {
        input.setBoxed(illegalValue);
        input.setUnboxed(illegalValue);
        byte[] serialization;
        serialization = CodecBin2.encodeBytes(input);
        CodecBin2.decodeBytes(output, serialization);
        if (narrow) {
            assertNotEquals(input.getBoxed(), output.getBoxed());
            assertNotEquals(input.getUnboxed(), output.getUnboxed());
            assertEquals(new Long(Integer.MIN_VALUE), output.getBoxed());
            assertEquals(new Long(Integer.MIN_VALUE).longValue(), output.getUnboxed());
        } else {
            assertEquals(input.getBoxed(), output.getBoxed());
            assertEquals(input.getUnboxed(), output.getUnboxed());
            assertEquals(new Long(illegalValue), output.getBoxed());
            assertEquals(illegalValue, output.getUnboxed());
        }
    }
}
