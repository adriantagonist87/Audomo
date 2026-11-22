package de.audomo;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.system.Os;
import android.system.OsConstants;

import org.json.JSONObject;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

final class SessionStore {
    static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);
    final File root;

    SessionStore(Context context) {
        root = new File(context.getFilesDir(), "sessions");
    }

    static String fileName(String input, OffsetDateTime start) {
        String clean =
                input == null
                        ? ""
                        : input.strip()
                                .replaceAll("(?i)\\.ogg$", "")
                                .replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_")
                                .replaceAll("^[. ]+|[. ]+$", "");
        if (clean.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) clean = "_" + clean;
        if (clean.isBlank()) clean = STAMP.format(start) + "_Aufnahme";
        while (clean.getBytes(StandardCharsets.UTF_8).length > 220) {
            clean = clean.substring(0, clean.offsetByCodePoints(clean.length(), -1));
        }
        return clean + ".ogg";
    }

    File create(String requested, Profile profile, OffsetDateTime start) throws Exception {
        Files.createDirectories(root.toPath());
        File dir = new File(root, UUID.randomUUID().toString());
        Files.createDirectory(dir.toPath());
        syncDirectory(root);
        String unique = uniqueName(fileName(requested, start), dir);
        JSONObject meta =
                new JSONObject()
                        .put("name", unique)
                        .put("start", start.toString())
                        .put("profile", profile.name())
                        .put("bitrate", profile.kbps())
                        .put("gainDb", profile.gain())
                        .put("wantedMicrophone", profile.mic())
                        .put("profileSnapshot", profile.json());
        storeMeta(dir, meta);
        try (FileOutputStream reserve = new FileOutputStream(new File(dir, "reserve"))) {
            reserve.write(new byte[128 * 1024]);
            reserve.getFD().sync();
        }
        return dir;
    }

    String uniqueName(String base, File dir) throws Exception {

        Set<String> names = new HashSet<>();
        for (File other : directories()) {
            if (other.equals(dir)) continue;
            File meta = new File(other, "session.json");
            if (meta.isFile()) names.add(metadata(other).getString("name"));
        }
        String unique = base;
        for (int i = 2; names.contains(unique); i++)
            unique = base.substring(0, base.length() - 4) + " (" + i + ").ogg";
        return unique;
    }

    static void storeMeta(File dir, JSONObject meta) throws Exception {
        File tmp = new File(dir, "session.json.tmp");
        try (FileOutputStream stream = new FileOutputStream(tmp)) {
            stream.write(meta.toString().getBytes(StandardCharsets.UTF_8));
            stream.getFD().sync();
        }
        Files.move(
                tmp.toPath(),
                new File(dir, "session.json").toPath(),
                StandardCopyOption.ATOMIC_MOVE);
        syncDirectory(dir);
    }

    static JSONObject metadata(File dir) throws Exception {
        return new JSONObject(
                new String(
                        Files.readAllBytes(new File(dir, "session.json").toPath()),
                        StandardCharsets.UTF_8));
    }

    File[] directories() throws IOException {
        if (!root.exists()) return new File[0];
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) throw new IOException("Aufnahmeordner ist nicht lesbar");
        return dirs;
    }

    record Entry(File file, long frames, OffsetDateTime start) {}

    List<Entry> list() throws Exception {
        List<Entry> items = new ArrayList<>();
        for (File dir : directories()) {
            if (!new File(dir, "session.json").isFile()) continue;
            JSONObject meta = metadata(dir);
            File file = new File(dir, meta.getString("name"));
            if (file.isFile() && meta.has("frames"))
                items.add(
                        new Entry(
                                file,
                                meta.getLong("frames"),
                                OffsetDateTime.parse(meta.getString("start"))));
        }
        items.sort(Comparator.comparing(Entry::start).reversed());
        return items;
    }

    static File publish(File dir, File closed, long frames, boolean recovered) throws Exception {
        JSONObject meta = metadata(dir);
        verify(closed);
        File target = new File(dir, meta.getString("name"));
        meta.put("frames", frames).put("recovered", recovered);
        storeMeta(dir, meta);
        Files.move(closed.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        syncDirectory(dir);
        return target;
    }

    static void verify(File file) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getPath());
            if (extractor.getTrackCount() != 1
                    || !"audio/opus"
                            .equals(extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME))) {
                throw new IOException("Opus-Spur nicht lesbar");
            }
            extractor.selectTrack(0);
            if (extractor.readSampleData(ByteBuffer.allocate(65536), 0) <= 0)
                throw new IOException("Audiodaten fehlen");
        } finally {
            extractor.release();
        }
    }

    String recoverAll() throws Exception {
        List<String> notes = new ArrayList<>();
        for (File dir : directories()) {
            Files.deleteIfExists(new File(dir, "reserve").toPath());
            File source = new File(dir, "capture.part");
            if (!source.exists()) continue;
            try {
                JSONObject meta = metadata(dir);
                File target = new File(dir, meta.getString("name"));
                if (!target.exists()) {
                    File copy = new File(dir, "repair.tmp");
                    long frames = OggOpus.recover(source, copy);
                    publish(dir, copy, frames, true);
                    notes.add(
                            "Wiederhergestellt · "
                                    + Audomo.duration(frames)
                                    + " · "
                                    + target.getName());
                }
                verify(target);
                Files.delete(source.toPath());
                syncDirectory(dir);
            } catch (Exception e) {
                notes.add("Unterbrochene Sitzung bleibt zur Rettung erhalten · " + Audomo.cause(e));
            }
        }
        return String.join("\n", notes);
    }

    static void syncDirectory(File directory) throws Exception {
        FileDescriptor fd = Os.open(directory.getPath(), OsConstants.O_RDONLY, 0);
        try {
            Os.fsync(fd);
        } finally {
            Os.close(fd);
        }
    }
}
