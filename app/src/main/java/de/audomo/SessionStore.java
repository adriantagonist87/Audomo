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
    final Context context;
    private final Set<String> reportedMetadata = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void reportMetadata(File dir) {
        if (context instanceof Audomo app && reportedMetadata.add(dir.getName()))
            app.diagnostic(
                    "Warnung · Metadaten einer Sitzung unlesbar. Audiodateien bleiben erhalten.");
    }

    SessionStore(Context context) {
        this.context = context.getApplicationContext();
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

    synchronized File create(String requested, Profile profile, OffsetDateTime start)
            throws Exception {
        Files.createDirectories(root.toPath());
        File dir = new File(root, UUID.randomUUID().toString());
        Files.createDirectory(dir.toPath());
        syncDirectory(root);
        String unique = uniqueName(fileName(requested, start), dir);
        JSONObject meta =
                new JSONObject()
                        .put("name", unique)
                        .put("folder", Profile.prefs(context).getString("folder", ""))
                        .put("start", start.toString())
                        .put("documentTree", Profile.prefs(context).getString("documentTree", ""))
                        .put(
                                "autoTarget",
                                Profile.prefs(context).getBoolean("autoUpload", false)
                                        ? Profile.prefs(context).getString("defaultTarget", "")
                                        : "")
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

    synchronized String uniqueName(String base, File directory) throws Exception {
        File meta = new File(directory, "session.json");
        String folder =
                meta.exists()
                        ? metadata(directory).optString("folder")
                        : Profile.prefs(context).getString("folder", "");
        return availableName(base, folder, directory);
    }

    String availableName(String base, String folder, File exclude) throws Exception {
        Set<String> names = new HashSet<>();
        for (File dir : directories()) {
            if (dir.equals(exclude) || !new File(dir, "session.json").isFile()) continue;
            try {
                JSONObject meta = metadata(dir);
                if (meta.optLong("trashed") == 0 && folder.equals(meta.optString("folder")))
                    names.add(
                            meta.optString("displayName", meta.getString("name"))
                                    .toLowerCase(Locale.ROOT));
            } catch (IOException | org.json.JSONException damaged) {
                reportMetadata(dir);
                File[] files = dir.listFiles(File::isFile);
                if (files != null)
                    for (File file : files) names.add(file.getName().toLowerCase(Locale.ROOT));
            }
        }
        int dot = base.lastIndexOf('.');
        String stem = dot > 0 ? base.substring(0, dot) : base;
        String ext = dot > 0 ? base.substring(dot) : "";
        String result = base;
        for (int n = 2; names.contains(result.toLowerCase(Locale.ROOT)); n++)
            result = stem + " (" + n + ")" + ext;
        return result;
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
        File[] dirs = root.listFiles(f -> f.isDirectory() && !f.getName().startsWith("."));
        if (dirs == null) throw new IOException("Aufnahmeordner ist nicht lesbar");
        return dirs;
    }

    record Entry(
            File file,
            long frames,
            OffsetDateTime start,
            String name,
            String folder,
            long trashed) {
        String id() {
            return file.getParentFile().getName();
        }
    }

    static Entry entry(File dir) throws Exception {
        if (!new File(dir, "session.json").isFile()) return null;
        JSONObject meta = metadata(dir);
        File file = new File(dir, meta.getString("name"));
        if (!file.isFile() || !meta.has("frames")) return null;
        return new Entry(
                file,
                meta.getLong("frames"),
                OffsetDateTime.parse(meta.getString("start")),
                meta.optString("displayName", file.getName()),
                meta.optString("folder"),
                meta.optLong("trashed"));
    }

    List<Entry> list() throws Exception {
        List<Entry> items = new ArrayList<>();
        for (File dir : directories()) {
            try {
                Entry e = entry(dir);
                if (e != null) items.add(e);
            } catch (IOException | org.json.JSONException | java.time.DateTimeException damaged) {
                reportMetadata(dir);
            }
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
