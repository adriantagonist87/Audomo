package de.audomo;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.nio.file.Files;

/**
 * Exercises the actual platform codec on the AVD, independent of microphone hardware and
 * permissions.
 */
@RunWith(AndroidJUnit4.class)
public class OpusProfileDeviceTest {
    @Test
    public void encodeEveryOfferedSampleRateAndBitrate() throws Exception {
        File cache = InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir();
        for (int rate : Profile.RATES) {
            for (int kbps : Profile.BITRATES) {
                File original = File.createTempFile("opus-test-", ".ogg", cache);
                File checked = File.createTempFile("opus-verify-", ".ogg", cache);
                try {
                    Profile profile =
                            new Profile(
                                    "Messung",
                                    rate,
                                    kbps,
                                    0,
                                    Profile.SYSTEM,
                                    Profile.SYSTEM,
                                    Profile.SYSTEM);
                    int frames = rate + 317;
                    try (OggOpus writer = new OggOpus(original, 42);
                            OpusEncoder encoder = new OpusEncoder(writer, profile)) {
                        short[] buffer = new short[rate / 50];
                        for (int offset = 0; offset < frames; offset += buffer.length) {
                            int count = Math.min(buffer.length, frames - offset);
                            for (int i = 0; i < count; i++)
                                buffer[i] =
                                        (short)
                                                Math.round(
                                                        6000
                                                                * Math.sin(
                                                                        2
                                                                                * Math.PI
                                                                                * 440
                                                                                * (offset + i)
                                                                                / rate));
                            encoder.write(buffer, count);
                        }
                        encoder.finish();
                    }
                    SessionStore.verify(original);
                    long end = OggOpus.recover(original, checked);
                    assertEquals(
                            "Granule at " + rate + " Hz / " + kbps + " kbit/s",
                            frames * (48000L / rate),
                            end);
                    assertArrayEquals(
                            Files.readAllBytes(original.toPath()),
                            Files.readAllBytes(checked.toPath()));
                } catch (Exception e) {
                    throw new AssertionError("Profil " + rate + " Hz / " + kbps + " kbit/s", e);
                } finally {
                    Files.deleteIfExists(original.toPath());
                    Files.deleteIfExists(checked.toPath());
                }
            }
        }
    }
}
