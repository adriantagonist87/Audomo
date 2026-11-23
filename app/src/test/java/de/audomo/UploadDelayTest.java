package de.audomo;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.List;

public class UploadDelayTest {
    @Test
    public void decimalMinutesNeverRoundDown() {
        assertEquals("0.5", UploadDelay.minutes(" 0,500 ").toPlainString());
        assertEquals(0, UploadDelay.millis(UploadDelay.minutes("0,0")));
        assertEquals(30_000, UploadDelay.millis(UploadDelay.minutes(".5")));
        assertEquals(300_000, UploadDelay.millis(UploadDelay.minutes("5")));
        assertEquals(1, UploadDelay.millis(UploadDelay.minutes("0,000001")));
    }

    @Test
    public void rejectsInvalidAndUnrepresentableValues() {
        for (String input : List.of("", ".", "-1", "1,2.3", "1e2", "NaN", "9999999999999999"))
            assertThrows(input, IllegalArgumentException.class, () -> UploadDelay.minutes(input));
    }
}
