package de.audomo;

import com.google.crypto.tink.StreamingAead;
import com.google.crypto.tink.subtle.AesGcmHkdfStreaming;

import java.io.*;
import java.security.SecureRandom;
import java.util.*;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Authenticated, length-delimited records. Audio never has to fit in RAM. */
final class BackupArchive {
    enum Scope {
        SETTINGS,
        CONNECTIONS,
        FULL
    }

    private static final byte[] MAGIC = {'A', 'U', 'D', 'O', 'M', 'O', 'B', 1};
    private static final int SALT = 16;
    private final byte[] header;
    final Scope scope;

    private BackupArchive(byte[] header, Scope scope) {
        this.header = header;
        this.scope = scope;
    }

    static BackupArchive create(OutputStream out, Scope scope) throws IOException {
        byte[] header = new byte[MAGIC.length + 1 + SALT];
        new SecureRandom().nextBytes(header);
        System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
        header[MAGIC.length] = (byte) scope.ordinal();
        out.write(header);
        return new BackupArchive(header, scope);
    }

    static BackupArchive read(InputStream in) throws IOException {
        byte[] header = in.readNBytes(MAGIC.length + 1 + SALT);
        if (header.length != MAGIC.length + 1 + SALT
                || !Arrays.equals(MAGIC, Arrays.copyOf(header, MAGIC.length)))
            throw new IOException("Unbekanntes Sicherungsformat");
        int level = Byte.toUnsignedInt(header[MAGIC.length]);
        if (level >= Scope.values().length) throw new IOException("Unbekannte Sicherungsstufe");
        return new BackupArchive(header, Scope.values()[level]);
    }

    private StreamingAead cipher(char[] password) throws Exception {
        PBEKeySpec spec =
                new PBEKeySpec(
                        password,
                        Arrays.copyOfRange(header, MAGIC.length + 1, header.length),
                        600_000,
                        256);
        byte[] key = null;
        try {
            key =
                    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                            .generateSecret(spec)
                            .getEncoded();
            return new AesGcmHkdfStreaming(key, "HmacSha256", 32, 1 << 20, 0);
        } finally {
            spec.clearPassword();
            if (key != null) Arrays.fill(key, (byte) 0);
        }
    }

    DataOutputStream encrypt(OutputStream out, char[] password) throws Exception {
        return new DataOutputStream(
                new BufferedOutputStream(cipher(password).newEncryptingStream(out, header)));
    }

    DataInputStream decrypt(InputStream in, char[] password) throws Exception {
        return new DataInputStream(
                new BufferedInputStream(cipher(password).newDecryptingStream(in, header)));
    }

    static void file(DataOutputStream out, String name, File file) throws IOException {
        out.writeUTF(name);
        long bytes = file.length();
        out.writeLong(bytes);
        try (InputStream in = new FileInputStream(file)) {
            if (in.transferTo(out) != bytes)
                throw new IOException("Datei während der Sicherung verändert");
        }
    }

    static void data(DataOutputStream out, String name, byte[] bytes) throws IOException {
        out.writeUTF(name);
        out.writeLong(bytes.length);
        out.write(bytes);
    }

    static void copy(DataInputStream in, OutputStream out, long length) throws IOException {
        if (length < 0) throw new IOException("Ungültige Datengröße");
        byte[] buffer = new byte[64 * 1024];
        while (length > 0) {
            int n = in.read(buffer, 0, (int) Math.min(length, buffer.length));
            if (n < 0) throw new EOFException("Unvollständige Sicherung");
            out.write(buffer, 0, n);
            length -= n;
        }
    }
}
