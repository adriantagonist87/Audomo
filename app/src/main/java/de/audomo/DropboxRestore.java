package de.audomo;

import android.database.sqlite.SQLiteDatabase;
import android.media.MediaMetadataRetriever;
import android.util.Base64;

import org.json.JSONObject;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Consumer;

/** Rebuild the local library from the selected Dropbox tree, without issuing remote writes. */
final class DropboxRestore {
    private final SessionStore store;
    private final LibraryFiles library;
    private final UploadQueue queue;
    private final DropboxFiles remote;

    private final Map<String, String> localIds = new HashMap<>();

    private String key(String folder, String name) {
        return (folder + "/" + name).toLowerCase(Locale.ROOT);
    }

    DropboxRestore(SessionStore store, DropboxFiles remote) {
        this.store = store;
        library = new LibraryFiles(store);
        queue = UploadQueue.get(store.context);
        this.remote = remote;
    }

    record Item(
            String path,
            String folder,
            String name,
            String revision,
            String hash,
            long bytes,
            OffsetDateTime modified) {}

    record Result(int added, int present, List<String> skipped) {
        String summary() {
            return added
                    + " heruntergeladen · "
                    + present
                    + " bereits vorhanden"
                    + (skipped.isEmpty()
                            ? ""
                            : "\n\nÜbersprungen ("
                                    + skipped.size()
                                    + ")\n"
                                    + String.join("\n", skipped));
        }
    }

    Result run(Consumer<String> update) throws Exception {
        List<Item> files = new ArrayList<>();
        List<String> folders = new ArrayList<>(), skipped = new ArrayList<>();
        String base = remote.config.base();
        String prefix = base.equals("/") ? "/" : base + "/";
        remote.list(
                base,
                true,
                entry -> {
                    String kind = entry.getString(".tag");
                    if (!kind.equals("file") && !kind.equals("folder")) return;
                    String path = entry.getString("path_display");
                    if (!path.regionMatches(true, 0, prefix, 0, prefix.length()))
                        throw new IOException("Unerwarteter Dropbox-Pfad");
                    String relative = path.substring(prefix.length());
                    String name = RemoteFiles.leaf(relative);
                    if (kind.equals("folder")) {
                        if (LibraryFiles.validFolderPath(relative)) folders.add(relative);
                        else skipped.add(relative + " · Ordnername nicht unterstützt");
                        return;
                    }
                    String ext = LibraryFiles.extension(name).toLowerCase(Locale.ROOT);
                    String mime = LibraryFiles.mime(name);
                    if (!(mime.startsWith("audio/") && !mime.equals("audio/*"))
                            && !Set.of(".ogg", ".opus", ".m4a", ".flac", ".wav", ".aac", ".mp3")
                                    .contains(ext)) return;
                    String folder = relative.substring(0, Math.max(0, relative.lastIndexOf('/')));
                    if (!LibraryFiles.validFolderPath(folder)
                            || name.startsWith(".")
                            || name.contains("\\")
                            || name.chars().anyMatch(Character::isISOControl)) {
                        skipped.add(relative + " · Pfad nicht unterstützt");
                        return;
                    }
                    files.add(
                            new Item(
                                    path,
                                    folder,
                                    name,
                                    entry.getString("rev"),
                                    entry.getString("content_hash"),
                                    entry.getLong("size"),
                                    OffsetDateTime.parse(entry.getString("client_modified"))
                                            .atZoneSameInstant(ZoneId.systemDefault())
                                            .toOffsetDateTime()));
                });
        synchronized (store) {
            localIds.clear();
            for (SessionStore.Entry e : store.list()) {
                if (e.trashed() == 0)
                    localIds.merge(key(e.folder(), e.name()), e.id(), (a, b) -> "");
            }
        }
        for (String folder : folders) {
            remote.progress.check(0);
            FolderManager.checkWritable(library.prefs, folder);
            library.ensureFolder(folder);
        }
        int added = 0, present = 0, i = 0;
        for (Item item : files) {
            remote.progress.check(0);
            update.accept(++i + " von " + files.size() + "\n" + item.name());
            try {
                if (restore(item)) added++;
                else present++;
            } catch (IOException e) {
                remote.progress.check(0);
                skipped.add(item.path() + " · " + Audomo.cause(e));
            }
        }
        return new Result(added, present, skipped);
    }

    // Dropbox hashes the concatenated binary SHA-256 digests of consecutive 4 MiB blocks.
    record Hashes(String dropbox, String sha256) {}

    static Hashes hashes(File audio, RemoteFiles.Progress progress) throws Exception {
        MessageDigest blocks = MessageDigest.getInstance("SHA-256"),
                block = MessageDigest.getInstance("SHA-256"),
                whole = MessageDigest.getInstance("SHA-256");
        int blockSize = 4 * 1024 * 1024, used = 0;
        byte[] buffer = new byte[65536];
        try (InputStream in = new FileInputStream(audio)) {
            for (int n;
                    (n = in.read(buffer, 0, Math.min(buffer.length, blockSize - used))) != -1; ) {
                progress.check(0);
                whole.update(buffer, 0, n);
                block.update(buffer, 0, n);
                used += n;
                if (used == blockSize) {
                    blocks.update(block.digest());
                    used = 0;
                }
            }
        }
        if (used > 0) blocks.update(block.digest());
        StringBuilder hex = new StringBuilder(64);
        for (byte b : blocks.digest())
            hex.append(Character.forDigit((b & 255) >>> 4, 16))
                    .append(Character.forDigit(b & 15, 16));
        return new Hashes(hex.toString(), Base64.encodeToString(whole.digest(), Base64.NO_WRAP));
    }

    private record Match(SessionStore.Entry local, UploadQueue.Job job) {}

    // Called under store then queue lock. Include hidden jobs that may still be resolving a write.
    private Match match(Item item) throws Exception {
        String id = localIds.get(key(item.folder(), item.name()));
        if ("".equals(id)) throw new IOException("Mehrere lokale Dateien passen");
        SessionStore.Entry local = id == null ? null : store.byId(id);
        if (id != null
                && (local == null
                        || local.trashed() != 0
                        || !key(local.folder(), local.name())
                                .equals(key(item.folder(), item.name()))))
            throw new IOException("Lokale Aufnahme inzwischen geändert");
        List<UploadQueue.Job> matches =
                queue.restoreCandidates(
                        remote.config.id(), item.path(), local == null ? "" : local.id());
        if (matches.isEmpty()) return new Match(local, null);
        UploadQueue.Job j = matches.get(0);
        if (matches.size() != 1
                || !j.knownCopy()
                || !j.state().equals("Hochgeladen")
                || !j.confirmedPath().equalsIgnoreCase(item.path())
                || local != null && !local.id().equals(j.rec()))
            throw new IOException("Abweichende Zuordnung oder offener Cloudauftrag");
        SessionStore.Entry existing = store.byId(j.rec());
        if (existing != null && existing.trashed() != 0)
            throw new IOException("Lokal im Papierkorb");
        return new Match(existing, j);
    }

    private boolean restore(Item item) throws Exception {
        Match before;
        synchronized (store) {
            synchronized (queue) {
                before = match(item);
            }
        }
        SessionStore.Entry existing = before.local();
        File temp = null;
        try {
            File source;
            if (existing != null) source = existing.file();
            else {
                temp = File.createTempFile("dropbox-", ".part", store.context.getCacheDir());
                try (FileOutputStream out = new FileOutputStream(temp)) {
                    if (!remote.read("rev:" + item.revision(), out))
                        throw new IOException("Version nicht mehr verfügbar");
                    out.getFD().sync();
                }
                source = temp;
            }
            if (source.length() != item.bytes()) throw new IOException("Abweichende Dateigröße");
            Hashes hash = hashes(source, remote.progress);
            if (!hash.dropbox().equals(item.hash()))
                throw new IOException(
                        existing == null
                                ? "Dropbox-Prüfsumme stimmt nicht überein"
                                : "Lokaler Inhalt weicht ab");
            if (before.job() != null && !hash.sha256().equals(before.job().verified()))
                throw new IOException("Inhalt weicht von der bestätigten Cloudkopie ab");
            long frames = existing == null ? frames(source, item.name()) : existing.frames();
            remote.progress.beforeCommit();
            synchronized (store) {
                SessionStore.Entry row;
                synchronized (queue) {
                    if (!remote.config.equals(CloudConfig.byId(store.context, remote.config.id()))
                            || !before.equals(match(item)))
                        throw new IOException("Zuordnung inzwischen geändert");
                    row =
                            existing == null
                                    ? publish(item, before.job(), source, frames, hash.sha256())
                                    : existing;
                    SQLiteDatabase db = queue.getWritableDatabase();
                    db.beginTransaction();
                    try {
                        CloudLibrary.remember(db, row);
                        if (before.job() == null)
                            db.execSQL(
                                    "INSERT INTO"
                                        + " jobs(rec,target,path,state,progress,verified,provider,inQueue,lastChecked)"
                                        + " VALUES(?,?,?,'Hochgeladen',100,?,'Dropbox',0,?) ON"
                                        + " CONFLICT(rec,target) DO UPDATE SET"
                                        + " path=excluded.path,state='Hochgeladen',"
                                        + "progress=100,verified=excluded.verified,provider='Dropbox',inQueue=0,lastChecked=excluded.lastChecked,"
                                        + "availability='',action='upload',origin='',error='',replacement=NULL,publicationHash=''",
                                    new Object[] {
                                        row.id(),
                                        remote.config.id(),
                                        item.path(),
                                        hash.sha256(),
                                        System.currentTimeMillis()
                                    });
                        else
                            db.execSQL(
                                    "UPDATE jobs SET availability='',lastChecked=? WHERE id=?",
                                    new Object[] {System.currentTimeMillis(), before.job().id()});
                        db.setTransactionSuccessful();
                    } finally {
                        db.endTransaction();
                    }
                }
                localIds.put(key(row.folder(), row.name()), row.id());
                DocumentStorage.deliver(store.context, row.file());
            }
            return existing == null;
        } finally {
            if (temp != null) Files.deleteIfExists(temp.toPath());
        }
    }

    private SessionStore.Entry publish(
            Item item, UploadQueue.Job job, File temp, long frames, String sha) throws Exception {
        String id = job == null ? UUID.randomUUID().toString() : job.rec();
        if (id.startsWith(".") || id.isEmpty() || id.contains("/"))
            throw new IOException("Ungültige Aufnahme-ID");
        File dir = new File(store.root, id);
        JSONObject meta =
                new File(dir, "session.json").isFile()
                        ? SessionStore.metadata(dir)
                        : new JSONObject();
        if (meta.optLong("trashed") != 0 || new File(dir, "capture.part").exists())
            throw new IOException("Sitzung aktiv oder im Papierkorb");
        if (!store.availableName(item.name(), item.folder(), dir).equals(item.name()))
            throw new IOException("Lokaler Dateiname belegt");
        FolderManager.checkWritable(library.prefs, item.folder());
        library.ensureFolder(item.folder());
        Files.createDirectories(store.root.toPath());
        String name = meta.optString("name", "audio" + LibraryFiles.extension(item.name()));
        if (!RemoteFiles.leaf(name).equals(name) || name.startsWith("."))
            throw new IOException("Ungültiger Dateiname");
        File audio = new File(dir, name);
        if (audio.exists()) throw new IOException("Lokale Datei bereits vorhanden");
        String start = meta.optString("start", item.modified().toString());
        if (!meta.has("start") && job != null) {
            try (android.database.Cursor c =
                    queue.getReadableDatabase()
                            .rawQuery(
                                    "SELECT start FROM recordings WHERE rec=?",
                                    new String[] {id})) {
                if (c.moveToFirst() && !CloudLibrary.UNKNOWN_DATE.toString().equals(c.getString(0)))
                    start = c.getString(0);
            }
        }
        meta.put("name", name)
                .put("start", start)
                .put("frames", frames)
                .put("folder", item.folder())
                .put("displayName", item.name())
                .put("sha256", sha)
                .put("autoQueued", true)
                .put("trashed", 0)
                .put("documentTree", library.prefs.getString("documentTree", ""));
        if (dir.exists()) {
            // Preserve the identity of a known cloud-only recording. Metadata is durable before
            // the audio is visible to background recovery; an interrupted restore can be retried.
            SessionStore.storeMeta(dir, meta);
            Files.move(temp.toPath(), audio.toPath(), StandardCopyOption.ATOMIC_MOVE);
            SessionStore.syncDirectory(dir);
        } else {
            File stage =
                    Files.createTempDirectory(
                                    store.context.getCacheDir().toPath(), "dropbox-publish-")
                            .toFile();
            try {
                Files.move(
                        temp.toPath(),
                        new File(stage, name).toPath(),
                        StandardCopyOption.ATOMIC_MOVE);
                SessionStore.storeMeta(stage, meta);
                Files.move(stage.toPath(), dir.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } finally {
                if (stage.exists()) LibraryFiles.removePrivateDirectory(stage);
            }
        }
        SessionStore.syncDirectory(store.root);
        return SessionStore.entry(dir);
    }

    private static long frames(File source, String name) throws Exception {
        if (name.toLowerCase(Locale.ROOT).matches(".*\\.(ogg|opus)"))
            OggOpus.verifyCompleteOgg(source);
        try (MediaMetadataRetriever media = new MediaMetadataRetriever()) {
            media.setDataSource(source.getPath());
            long ms =
                    Long.parseLong(
                            media.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            if (ms <= 0
                    || !"yes"
                            .equals(
                                    media.extractMetadata(
                                            MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)))
                throw new IOException("Keine abspielbare Audiospur");
            return ms * 48;
        } catch (RuntimeException failure) {
            throw new IOException("Audiodatei nicht lesbar", failure);
        }
    }
}
