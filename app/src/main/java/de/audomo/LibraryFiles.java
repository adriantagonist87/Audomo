package de.audomo;

import android.content.*;
import android.database.Cursor;
import android.media.*;
import android.net.Uri;
import android.provider.DocumentsContract.Document;
import android.provider.OpenableColumns;

import org.json.JSONObject;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;

final class LibraryFiles {
    final SessionStore store;
    final SharedPreferences prefs;

    LibraryFiles(SessionStore store) {
        this.store = store;
        prefs = Profile.prefs(store.context);
    }

    static String title(String value) {
        String result = SessionStore.fileName(value, OffsetDateTime.now());
        if (value.isBlank() || value.equals(".") || value.equals(".."))
            throw new IllegalArgumentException("Name fehlt");
        return result.substring(0, result.length() - 4);
    }

    Set<String> folders() {
        TreeSet<String> all = new TreeSet<>(prefs.getStringSet("folders", Set.of()));
        all.add("");
        return all;
    }

    String addFolder(String parent, String value) throws IOException {
        synchronized (store) {
            FolderManager.checkWritable(prefs, parent);
            String name = title(value);
            String path = parent.isEmpty() ? name : parent + "/" + name;
            Set<String> all = folders();
            if (!all.contains(parent) || !all.add(path))
                throw new IOException("Ordner fehlt oder Name schon belegt");
            if (!prefs.edit().putStringSet("folders", all).commit())
                throw new IOException("Ordner nicht gesichert");
            return path;
        }
    }

    static boolean validFolderPath(String path) {
        if (path.isEmpty()) return true;
        for (String segment : path.split("/", -1))
            if (segment.isEmpty() || !segment.equals(SessionStore.cleanComponent(segment))) return false;
        return true;
    }

    void ensureFolder(String path) throws IOException {
        if (path.isEmpty()) return;
        synchronized (store) {
            Set<String> all = folders();
            if (all.contains(path)) return;
            if (!validFolderPath(path)) throw new IOException("Ungültiger Aufnahmeordner");
            String parent = "";
            for (String segment : path.split("/")) {
                parent = parent.isEmpty() ? segment : parent + "/" + segment;
                all.add(parent);
            }
            if (!prefs.edit().putStringSet("folders", all).commit())
                throw new IOException("Aufnahmeordner nicht gesichert");
        }
    }

    private JSONObject checked(SessionStore.Entry entry) throws Exception {
        File dir = entry.file().getParentFile();
        JSONObject meta = SessionStore.metadata(dir);
        if (!entry.file().isFile()
                || !meta.has("frames")
                || new File(dir, "capture.part").exists()
                || !entry.name().equals(meta.optString("displayName", meta.getString("name")))
                || !entry.folder().equals(meta.optString("folder"))
                || entry.trashed() != meta.optLong("trashed"))
            throw new IOException("Datei wurde inzwischen geändert");
        return meta;
    }

    static String renameStem(String input, String extension) {
        String stem = input.strip();
        if (!extension.isEmpty() && stem.toLowerCase(Locale.ROOT).endsWith(extension.toLowerCase(Locale.ROOT)))
            stem = stem.substring(0, stem.length() - extension.length());
        if (stem.isBlank()) throw new IllegalArgumentException("Bitte einen Dateinamen eingeben");
        if (!stem.equals(SessionStore.cleanComponent(stem)))
            throw new IllegalArgumentException("Ungültiger oder zu langer Dateiname");
        return stem;
    }

    String rename(SessionStore.Entry entry, String input, boolean local, List<UploadQueue.Job> targets)
            throws Exception {
        String ext = extension(entry.name());
        String stem = renameStem(input, ext);
        String name = stem + ext;
        synchronized (store) {
            if (!local && targets.isEmpty()) throw new IOException("Bitte einen Speicherort auswählen");
            if (entry.trashed() != 0 || (local && entry.cloudOnly()))
                throw new IOException("Aufnahme an diesem Speicherort nicht verfügbar");
            if (local) checked(entry);
            if (local || entry.cloudOnly()) {
                if (!name.equals(store.availableName(name, entry.folder(), entry.file().getParentFile())))
                    throw new IOException("Dieser Name ist im Aufnahmeordner bereits vergeben");
            }
            UploadQueue queue = UploadQueue.get(store.context);
            queue.checkRenameTargets(entry.id(), targets);
            boolean changedLocal = local && !entry.name().equals(name);
            if (changedLocal) change(entry, stem, entry.folder(), false);
            int count;
            try {
                count = queue.renameTargets(entry.id(), targets, name);
            } catch (Exception error) {
                if (changedLocal) throw new IOException("Lokal umbenannt. Cloudauftrag fehlgeschlagen: "
                        + Audomo.cause(error), error);
                throw error;
            }
            List<String> result = new ArrayList<>();
            if (changedLocal) result.add("Auf diesem Gerät umbenannt");
            if (count > 0) result.add(count == 1 ? "1 Cloudauftrag in der Warteschlange"
                    : count + " Cloudaufträge in der Warteschlange");
            return result.isEmpty() ? "Die ausgewählten Dateien heißen bereits so" : String.join(" · ", result);
        }
    }

    void change(SessionStore.Entry e, String stem, String folder, boolean trash) throws Exception {
        synchronized (store) {
            FolderManager.checkWritable(prefs, e.folder());
            FolderManager.checkWritable(prefs, folder);
            JSONObject meta = checked(e);
            if (meta.has("obsoleteUri") || meta.has("pendingUri")) {
                DocumentStorage.deliver(store.context, e.file());
                meta = checked(e);
            }
            String name = e.name();
            if (stem != null) name = stem + extension(name);
            if (!folders().contains(folder)) throw new IOException("Zielordner fehlt");
            if (!trash) name = store.availableName(name, folder, e.file().getParentFile());
            meta.put("displayName", name)
                    .put("folder", folder)
                    .put("trashed", trash ? System.currentTimeMillis() : 0);
            UploadQueue queue = UploadQueue.get(store.context);
            try {
                if (trash || e.trashed() != 0) {
                    String legacyHash = queue.legacyUploadHash(e);
                    meta.put("autoQueued", true);
                    synchronized (queue) {
                        // Restore only after stale uploads are retired. Trash only after the marker is durable.
                        if (!trash) queue.discardUploadsForTrash(e.id(), legacyHash);
                        DocumentStorage.storeLocation(e.file(), meta);
                        if (trash) queue.discardUploadsForTrash(e.id(), legacyHash);
                    }
                } else DocumentStorage.storeLocation(e.file(), meta);
                DocumentStorage.deliver(store.context, e.file());
            } finally {
                SessionStore.Entry updated = SessionStore.entry(e.file().getParentFile());
                if (updated != null)
                    UploadQueue.get(store.context).getWritableDatabase().execSQL(
                            "UPDATE recordings SET name=?,folder=? WHERE rec=?",
                            new Object[]{updated.name(),updated.folder(),e.id()});
            }
        }
    }

    void purge(SessionStore.Entry e) throws Exception {
        synchronized (store) {
            JSONObject meta = checked(e);
            if (meta.has("obsoleteUri") || meta.has("pendingUri") || meta.has("deliveredUri"))
                throw new IOException("Ordnerkopie zuerst prüfen");
            if (e.trashed() == 0) throw new IOException("Datei liegt nicht im Papierkorb");
            UploadQueue.get(store.context).discardUploadsForTrash(e);
            if (UploadQueue.get(store.context).jobs().stream()
                    .anyMatch(j ->
                            j.rec().equals(e.id())
                                    && List.of("Wartet", "Upload läuft", "Konflikt", "Fehler")
                                            .contains(j.state())))
                throw new IOException("Ein Cloudauftrag benötigt diese Datei noch");
            File dir = e.file().getParentFile();
            if (UploadQueue.get(store.context).hasCloud(e.id())) new CloudLibrary(store).known();
            File pending = new File(store.root, ".purge-" + e.id());
            Files.move(dir.toPath(), pending.toPath(), StandardCopyOption.ATOMIC_MOVE);
            SessionStore.syncDirectory(store.root);
            removePrivateDirectory(pending);
        }
    }

    void cleanOld() throws Exception {
        if (!prefs.getBoolean("cleanTrash", false)) return;
        long threshold = System.currentTimeMillis() - Duration.ofDays(30).toMillis();
        for (SessionStore.Entry e : store.list())
            if (e.trashed() > 0 && e.trashed() <= threshold) purge(e);
    }

    static String extension(String name) {
        int at = name.lastIndexOf('.');
        return at > 0 ? name.substring(at) : "";
    }

    static String mime(String name) {
        String type =
                android.webkit.MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(
                                extension(name).replace(".", "").toLowerCase(Locale.ROOT));
        return type == null ? "audio/*" : type;
    }

    static String digest(InputStream raw) throws Exception {
        if (raw == null) throw new IOException("Inhalt nicht lesbar");
        try (InputStream in = raw) {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] chunk = new byte[65536];
            for (int n; (n = in.read(chunk)) != -1; ) sha.update(chunk, 0, n);
            return android.util.Base64.encodeToString(sha.digest(), android.util.Base64.NO_WRAP);
        }
    }

    void copy(SessionStore.Entry e, String folder) throws Exception {
        JSONObject meta = checked(e);
        meta.remove("deliveredUri");
        meta.remove("obsoleteUri");
        meta.remove("pendingUri");
        meta.remove("autoTarget");
        meta.remove("autoQueued");
        meta.remove("uploadNotBefore");
        stage(new FileInputStream(e.file()), e.name(), folder, meta, false, e.start());
    }

    void importUri(Uri uri, String folder) throws Exception {
        ContentResolver resolver = store.context.getContentResolver();
        String name = null;
        OffsetDateTime date = OffsetDateTime.now();
        try (Cursor c = resolver.query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME),
                        t = c.getColumnIndex(Document.COLUMN_LAST_MODIFIED),
                        mediaTime =
                                c.getColumnIndex(
                                        android.provider.MediaStore.MediaColumns.DATE_MODIFIED);
                if (n >= 0) name = c.getString(n);
                Instant modified =
                        t >= 0 && c.getLong(t) > 0
                                ? Instant.ofEpochMilli(c.getLong(t))
                                : mediaTime >= 0 && c.getLong(mediaTime) > 0
                                        ? Instant.ofEpochSecond(c.getLong(mediaTime))
                                        : null;
                if (modified != null)
                    date = modified.atZone(ZoneId.systemDefault()).toOffsetDateTime();
            }
        }
        if (name == null || name.isBlank()) throw new IOException("Quelldateiname fehlt");
        String ext = extension(name);
        if (!ext.matches("\\.[a-zA-Z0-9]{1,10}")) throw new IOException("Audio-Dateiendung fehlt");
        if (name.contains("/")
                || name.contains("\\")
                || name.chars().anyMatch(Character::isISOControl))
            throw new IOException("Quellname enthält unzulässige Pfadzeichen");
        InputStream in = resolver.openInputStream(uri);
        if (in == null) throw new IOException("Quelldatei nicht lesbar");
        stage(in, name, folder, new JSONObject().put("importedName", name), true, date);
    }

    private void stage(
            InputStream source,
            String name,
            String folder,
            JSONObject meta,
            boolean importing,
            OffsetDateTime date)
            throws Exception {
        try (InputStream in = source) {
            Files.createDirectories(store.root.toPath());
            String id = UUID.randomUUID().toString();
            File temp = new File(store.root, ".copy-" + id);
            Files.createDirectory(temp.toPath());
            try {
                String physical = "audio" + extension(name);
                File audio = new File(temp, physical);
                MessageDigest incoming = MessageDigest.getInstance("SHA-256");
                try (FileOutputStream out = new FileOutputStream(audio)) {
                    new DigestInputStream(in, incoming).transferTo(out);
                    out.getFD().sync();
                }
                String hash =
                        android.util.Base64.encodeToString(
                                incoming.digest(), android.util.Base64.NO_WRAP);
                if (!hash.equals(digest(new FileInputStream(audio))))
                    throw new IOException("Kopie unvollständig");
                if (importing) {
                    if (extension(name).equalsIgnoreCase(".ogg")
                            || extension(name).equalsIgnoreCase(".opus"))
                        OggOpus.verifyCompleteOgg(audio);
                    meta.put("frames", audioFrames(audio));
                }
                synchronized (store) {
                    FolderManager.checkWritable(prefs, folder);
                    if (!folders().contains(folder)) throw new IOException("Zielordner fehlt");
                    if (importing)
                        for (SessionStore.Entry e : store.list()) {
                            if (e.file().length() == audio.length()
                                    && hash.equals(digest(new FileInputStream(e.file()))))
                                throw new IOException("Bereits vorhanden · " + e.name());
                        }
                    String display = store.availableName(name, folder, null);
                    meta.put("name", physical)
                            .put("documentTree", prefs.getString("documentTree", ""))
                            .put("displayName", display)
                            .put("folder", folder)
                            .put("trashed", 0)
                            .put("start", date.toString())
                            .put("sha256", hash);
                    SessionStore.storeMeta(temp, meta);
                    Files.move(
                            temp.toPath(),
                            new File(store.root, id).toPath(),
                            StandardCopyOption.ATOMIC_MOVE);
                    SessionStore.syncDirectory(store.root);
                    DocumentStorage.deliver(
                            store.context, new File(new File(store.root, id), physical));
                }
            } finally {
                if (temp.exists()) removePrivateDirectory(temp);
            }
        }
    }

    private static long audioFrames(File file) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        try {
            extractor.setDataSource(file.getAbsolutePath());
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime == null || !mime.startsWith("audio/")) continue;
                extractor.selectTrack(i);
                MediaCodec decoder = MediaCodec.createDecoderByType(mime);
                try {
                    decoder.configure(format, null, null, 0);
                    decoder.start();
                    long frames = decodedFrames(extractor, decoder, format);
                    if (frames <= 0) throw new IOException("Keine dekodierbaren Audioframes");
                    return frames;
                } finally {
                    decoder.release();
                }
            }
            throw new IOException("Audiospur fehlt");
        } finally {
            extractor.release();
        }
    }

    private static long decodedFrames(MediaExtractor input, MediaCodec decoder, MediaFormat format)
            throws IOException {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        boolean inputEnded = false;
        int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                width = 2;
        long scaled = 0, segment = 0, last = android.os.SystemClock.elapsedRealtime();
        while (true) {
            if (!inputEnded) {
                int index = decoder.dequeueInputBuffer(10000);
                if (index >= 0) {
                    ByteBuffer buf = decoder.getInputBuffer(index);
                    buf.clear();
                    int n = input.readSampleData(buf, 0);
                    inputEnded = n < 0;
                    decoder.queueInputBuffer(
                            index,
                            0,
                            Math.max(0, n),
                            inputEnded ? 0 : input.getSampleTime(),
                            inputEnded ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                    if (!inputEnded) input.advance();
                    last = android.os.SystemClock.elapsedRealtime();
                }
            }
            int index = decoder.dequeueOutputBuffer(info, 10000);
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                scaled += Math.round(segment * 48000.0 / rate);
                segment = 0;
                MediaFormat pcm = decoder.getOutputFormat();
                rate = pcm.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                channels = pcm.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                width =
                        switch (pcm.getInteger(
                                MediaFormat.KEY_PCM_ENCODING, AudioFormat.ENCODING_PCM_16BIT)) {
                            case AudioFormat.ENCODING_PCM_8BIT -> 1;
                            case AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3;
                            case AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT ->
                                    4;
                            default -> 2;
                        };
            } else if (index >= 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0)
                    segment += info.size / (channels * width);
                decoder.releaseOutputBuffer(index, false);
                last = android.os.SystemClock.elapsedRealtime();
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                    return scaled + Math.round(segment * 48000.0 / rate);
            }
            if (android.os.SystemClock.elapsedRealtime() - last > 15000)
                throw new IOException("Audiodecoder antwortet nicht");
        }
    }

    static void removePrivateDirectory(File dir) throws IOException {
        File[] children = dir.listFiles();
        if (children == null) throw new IOException("Arbeitsordner nicht lesbar");
        for (File child : children) Files.delete(child.toPath());
        Files.delete(dir.toPath());
    }
}
