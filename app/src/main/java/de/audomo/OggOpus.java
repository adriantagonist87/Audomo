package de.audomo;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** One logical mono stream. Checkpoints contain complete, independently CRC-checked pages. */
final class OggOpus implements Closeable {
    private static final byte[] CAPTURE = ascii("OggS");
    private static final byte[] HEAD = ascii("OpusHead");
    private static final byte[] TAGS = ascii("OpusTags");
    private static final int[] CRC = new int[256];

    static {
        for (int i = 0; i < CRC.length; i++) {
            int value = i << 24;
            for (int j = 0; j < 8; j++) {
                value = (value << 1) ^ (value < 0 ? 0x04c11db7 : 0);
            }
            CRC[i] = value;
        }
    }

    private final FileOutputStream output;
    private final int serial;
    private final ByteArrayOutputStream laces = new ByteArrayOutputStream();
    private final ByteArrayOutputStream data = new ByteArrayOutputStream();
    private int sequence;
    private long decoded;
    private long durable;
    private int preSkip;
    private boolean initialized;

    OggOpus(File file, int serial) throws IOException {
        this.serial = serial;
        output = new FileOutputStream(file);
    }

    void headers(byte[] config) throws IOException {
        if (initialized) return;
        for (int i = 0; i + 19 <= config.length; i++) {
            if (!starts(config, i, HEAD)) continue;
            byte[] head = Arrays.copyOfRange(config, i, i + 19);
            if (head[8] != 1 || head[9] != 1 || head[18] != 0) break;
            preSkip = (head[10] & 255) | ((head[11] & 255) << 8);
            append(head);
            page(2, 0);
            byte[] vendor = ascii("Audomo");
            append(
                    little(16 + vendor.length)
                            .put(TAGS)
                            .putInt(vendor.length)
                            .put(vendor)
                            .putInt(0)
                            .array());
            page(0, 0);
            initialized = true;
            return;
        }
        throw new IOException("Ungültiger Opus-Encoderheader");
    }

    void packet(byte[] packet, long finalFrames) throws IOException {
        if (!initialized) throw new IOException("Opus-Header fehlt");
        // At EOS the encoder may emit extra padding. Keep only packets needed for the final
        // granule.
        if (finalFrames >= 0 && decoded >= finalFrames + preSkip) return;
        if (data.size() > 0
                && (decoded - durable >= 9600 || laces.size() + packet.length / 255 + 1 > 255)) {
            page(0, decoded);
            output.getFD().sync();
            durable = decoded;
        }
        append(packet);
        decoded += samples(packet);
    }

    long durableFrames() {
        return Math.max(0, durable - preSkip);
    }

    int preSkip() {
        return preSkip;
    }

    void finish(long frames) throws IOException {
        long end = frames + preSkip;
        if (frames <= 0 || decoded < end || end < durable || data.size() == 0) {
            throw new IOException("Unvollständiger Opus-Abschluss");
        }
        page(4, end);
        output.getFD().sync();
    }

    static int samples(byte[] packet) throws IOException {
        if (packet.length == 0) throw new IOException("Leeres Opus-Paket");
        int toc = packet[0] & 255;
        int code = toc & 3;
        int count = code == 0 ? 1 : 2;
        if (code == 3) count = packet.length > 1 ? packet[1] & 63 : 0;
        int config = toc >> 3;
        int unit;
        if (config >= 16) unit = 120 << (config & 3);
        else if (config >= 12) unit = 480 << (config & 1);
        else unit = (config & 3) == 3 ? 2880 : 480 << (config & 3);
        if (count == 0 || count * unit > 5760) throw new IOException("Ungültige Opus-Paketdauer");
        return count * unit;
    }

    private void append(byte[] packet) {
        for (int n = packet.length; n >= 255; n -= 255) laces.write(255);
        laces.write(packet.length % 255);
        data.write(packet, 0, packet.length);
    }

    private void page(int flags, long granule) throws IOException {
        byte[] bytes =
                little(27 + laces.size() + data.size())
                        .put(CAPTURE)
                        .put((byte) 0)
                        .put((byte) flags)
                        .putLong(granule)
                        .putInt(serial)
                        .putInt(sequence++)
                        .putInt(0)
                        .put((byte) laces.size())
                        .put(laces.toByteArray())
                        .put(data.toByteArray())
                        .array();
        checksum(bytes);
        output.write(bytes);
        laces.reset();
        data.reset();
    }

    /** Copy the maximal complete prefix, repairing only the last page's EOS flag and CRC. */
    static long recover(File source, File destination) throws IOException {
        try (InputStream input = new BufferedInputStream(new FileInputStream(source));
                FileOutputStream out = new FileOutputStream(destination)) {
            int sequence = 0, serial = 0, skip = 0;
            long granule = 0;
            byte[] pending = null;
            while (true) {
                byte[] page = readPage(input);
                if (page == null) break;
                ByteBuffer fields = ByteBuffer.wrap(page).order(ByteOrder.LITTLE_ENDIAN);
                int flags = page[5] & 255;
                int start = 27 + (page[26] & 255);
                long next = fields.getLong(6);
                if (fields.getInt(18) != sequence
                        || (sequence > 0 && fields.getInt(14) != serial)
                        || next < granule
                        || (flags & ~6) != 0
                        || ((flags & 2) != 0) != (sequence == 0)) break;
                if (sequence == 0) {
                    if (!starts(page, start, HEAD) || page.length - start != 19) break;
                    serial = fields.getInt(14);
                    skip = fields.getShort(start + 10) & 65535;
                }
                if (sequence == 1 && !starts(page, start, TAGS)) break;
                if (pending != null) out.write(pending);
                pending = page;
                granule = next;
                sequence++;
                if ((flags & 4) != 0) break;
            }
            if (sequence < 3 || granule <= skip)
                throw new IOException("Kein vollständiger Audio-Schreibpunkt");
            pending[5] |= 4;
            checksum(pending);
            out.write(pending);
            out.getFD().sync();
            return granule - skip;
        }
    }

    // null denotes a truncated or invalid page. I/O errors still propagate to the caller.
    private static byte[] readPage(InputStream input) throws IOException {
        byte[] header = input.readNBytes(27);
        if (header.length != 27 || !starts(header, 0, CAPTURE) || header[4] != 0) return null;
        int count = header[26] & 255;
        byte[] segments = input.readNBytes(count);
        if (count == 0 || segments.length != count || (segments[count - 1] & 255) == 255)
            return null;
        int size = 0;
        for (byte lace : segments) size += lace & 255;
        byte[] payload = input.readNBytes(size);
        if (payload.length != size) return null;
        byte[] bytes = little(27 + count + size).put(header).put(segments).put(payload).array();
        int expected = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).getInt(22);
        return checksum(bytes) == expected ? bytes : null;
    }

    private static int checksum(byte[] page) {
        Arrays.fill(page, 22, 26, (byte) 0);
        int crc = 0;
        for (byte b : page) crc = (crc << 8) ^ CRC[((crc >>> 24) ^ b) & 255];
        ByteBuffer.wrap(page).order(ByteOrder.LITTLE_ENDIAN).putInt(22, crc);
        return crc;
    }

    private static ByteBuffer little(int size) {
        return ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static boolean starts(byte[] data, int offset, byte[] magic) {
        if (offset + magic.length > data.length) return false;
        for (int i = 0; i < magic.length; i++) if (data[offset + i] != magic[i]) return false;
        return true;
    }

    @Override
    public void close() throws IOException {
        output.close();
    }
}
