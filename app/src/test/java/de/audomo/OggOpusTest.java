package de.audomo;

import static org.junit.Assert.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.*;
import java.nio.file.Files;
import java.util.Arrays;

public class OggOpusTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private File stream() throws Exception {
        File file = folder.newFile();
        byte[] header =
                ByteBuffer.allocate(19)
                        .order(ByteOrder.LITTLE_ENDIAN)
                        .put("OpusHead".getBytes("US-ASCII"))
                        .put((byte) 1)
                        .put((byte) 1)
                        .putShort((short) 312)
                        .putInt(48000)
                        .putShort((short) 0)
                        .put((byte) 0)
                        .array();
        byte[] packet = new byte[255];
        packet[0] = (byte) 0xf8;
        try (OggOpus writer = new OggOpus(file, 42)) {
            writer.headers(header);
            for (int i = 0; i < 26; i++) writer.packet(packet, -1);
            writer.finish(26L * 960 - 312 - 100);
        }
        return file;
    }

    @Test
    public void completeRecoveryPreservesPacketsAndTrim() throws Exception {
        File original = stream(), result = folder.newFile();
        assertEquals(26L * 960 - 312 - 100, OggOpus.recover(original, result));
        assertArrayEquals(
                Files.readAllBytes(original.toPath()), Files.readAllBytes(result.toPath()));
    }

    @Test
    public void tornOrCorruptFinalPageKeepsPreviousCheckpoint() throws Exception {
        File source = stream(), copy = folder.newFile(), repeat = folder.newFile();
        byte[] all = Files.readAllBytes(source.toPath());
        for (int i = 0; i < 2; i++) {
            byte[] broken = i == 0 ? Arrays.copyOf(all, all.length - 40) : all.clone();
            if (i == 1) broken[broken.length - 3] ^= 1;
            Files.write(source.toPath(), broken);
            long recovered = OggOpus.recover(source, copy);
            assertEquals(20L * 960 - 312, recovered);
            assertEquals(recovered, OggOpus.recover(copy, repeat));
            assertArrayEquals(
                    Files.readAllBytes(copy.toPath()), Files.readAllBytes(repeat.toPath()));
            assertArrayEquals(broken, Files.readAllBytes(source.toPath()));
        }
    }

    @Test
    public void headersAloneNeverReportSaved() throws Exception {
        File source = stream();
        try (RandomAccessFile f = new RandomAccessFile(source, "rw")) {
            f.setLength(96);
        }
        File output = folder.newFile();
        assertThrows(IOException.class, () -> OggOpus.recover(source, output));
    }

    @Test
    public void invalidPacketDurationRejected() {
        assertThrows(IOException.class, () -> OggOpus.samples(new byte[] {(byte) 0xfb, 0}));
        assertThrows(IOException.class, () -> OggOpus.samples(new byte[] {(byte) 0xfb, 63}));
    }
}
