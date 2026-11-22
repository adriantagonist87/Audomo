package de.audomo;

import static org.junit.Assert.*;

import org.junit.Test;

public class DigitalGainTest {
    @Test
    public void fiveDecibelsAndOutputClipping() {
        short[] samples = {1000, -1000, 25000, -25000, 17};
        double gain = Math.pow(10, 5 / 20d);
        assertEquals(44457, CaptureEngine.applyGain(samples, 4, gain));
        assertArrayEquals(new short[] {1778, -1778, 32767, -32768, 17}, samples);
        assertEquals(32768, CaptureEngine.applyGain(samples, 4, 1));
        assertArrayEquals(new short[] {1778, -1778, 32767, -32768, 17}, samples);
    }
}
