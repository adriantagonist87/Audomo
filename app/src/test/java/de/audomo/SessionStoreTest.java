package de.audomo;

import static org.junit.Assert.*;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

public class SessionStoreTest {
    private final OffsetDateTime start = OffsetDateTime.parse("2026-11-22T10:01:02+01:00");

    @Test
    public void stableFallbackAndProtectedExtension() {
        assertEquals("2026-11-22_10-01-02_Aufnahme.ogg", SessionStore.fileName("...", start));
        assertEquals("A_B_C.ogg", SessionStore.fileName("A/B:C.OGG", start));
    }

    @Test
    public void unicodeBoundedAtCodePointBoundary() {
        String name = SessionStore.fileName("🎵".repeat(100), start);
        assertTrue(name.getBytes(StandardCharsets.UTF_8).length <= 224);
        assertFalse(name.contains("�"));
    }
}
