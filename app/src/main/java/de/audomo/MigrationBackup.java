package de.audomo;

import static de.audomo.BackupArchive.Scope;

import android.database.sqlite.SQLiteDatabase;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;

final class MigrationBackup {
    private static final String PENDING = "migration-pending-v1";
    private static final String TOKEN_TABLE = "migration_commit";
    private static final int MAX_CONFIG = 16 * 1024 * 1024;

    private static byte[] bytes(JSONObject j) {
        return j.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static JSONObject read(File f) throws Exception {
        if (f.length() > MAX_CONFIG) throw new IOException("Sicherungsmetadaten zu groß");
        return new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    }

    private static void write(File f, JSONObject j) throws Exception {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(bytes(j));
            out.getFD().sync();
        }
    }

    private static void begin(Audomo app) throws IOException {
        synchronized (app) {
            if (!app.ready())
                throw new IOException("Bitte die laufende Aufnahme oder Sicherung zuerst beenden");
            app.backupBusy = true;
        }
        if (UploadQueue.get(app).transferring()) {
            app.backupBusy = false;
            throw new IOException("Bitte den laufenden Upload zuerst abschließen");
        }
    }

    static void export(Audomo app, OutputStream dest, Scope scope, char[] password)
            throws Exception {
        begin(app);
        File temp = null;
        try {
            temp = Files.createTempDirectory(app.getCacheDir().toPath(), "export-").toFile();
            JSONObject settings;
            Map<String, SessionStore.Entry> cloud = Map.of();
            List<UploadQueue.Job> copies = List.of();
            UploadQueue queue = UploadQueue.get(app);
            synchronized (app.sessions) {
                synchronized (queue) {
                    settings = BackupPreferences.export(app, scope);
                    if (scope == Scope.FULL) {
                        cloud = new CloudLibrary(app.sessions).known();
                        copies = queue.jobs();
                        SQLiteDatabase db = queue.getReadableDatabase();
                        db.beginTransactionNonExclusive();
                        try {
                            for (String t : BackupDatabase.TABLES)
                                BackupDatabase.exportTable(db, t, new File(temp, t + ".jsonl"));
                            db.setTransactionSuccessful();
                        } finally {
                            db.endTransaction();
                        }
                        snapshotSessions(app, temp);
                        synchronized (app) {
                            File log = new File(app.getFilesDir(), "diagnose.txt");
                            if (log.isFile())
                                Files.copy(log.toPath(), new File(temp, "diagnose.txt").toPath());
                        }
                    }
                }
            }
            if (scope == Scope.FULL) {
                includeCloudAudio(app, temp, cloud, copies);
                for (File dir : children(new File(temp, "sessions")))
                    requireAudio(dir, read(new File(dir, "session.json")));
            }
            BackupArchive archive = BackupArchive.create(dest, scope);
            try (DataOutputStream out = archive.encrypt(dest, password)) {
                BackupArchive.data(out, "settings", bytes(settings));
                if (scope == Scope.FULL) {
                    for (String t : BackupDatabase.TABLES)
                        BackupArchive.file(out, t, new File(temp, t + ".jsonl"));
                    File log = new File(temp, "diagnose.txt");
                    if (log.exists()) BackupArchive.file(out, "diagnose", log);
                    for (File session : children(new File(temp, "sessions")))
                        for (File file : children(session))
                            BackupArchive.file(
                                    out,
                                    "sessions/" + session.getName() + "/" + file.getName(),
                                    file);
                }
                out.writeUTF("");
            }
        } finally {
            app.backupBusy = false;
            if (temp != null) remove(temp);
        }
    }

    private static void includeCloudAudio(
            Audomo app,
            File stage,
            Map<String, SessionStore.Entry> cloud,
            List<UploadQueue.Job> copies)
            throws Exception {
        CloudLibrary library = new CloudLibrary(app.sessions);
        for (SessionStore.Entry entry : cloud.values()) {
            if (!sessionId(entry.id()))
                throw new IOException("Ungültige Aufnahme-ID im Cloudkatalog");
            File dir = new File(new File(stage, "sessions"), entry.id());
            File description = new File(dir, "session.json");
            JSONObject meta = description.isFile() ? read(description) : new JSONObject();
            String name = meta.optString("name", entry.file().getName());
            if (!leaf(name)) throw new IOException("Ungültiger Dateiname im Cloudkatalog");
            File audio = new File(dir, name);
            if (audio.isFile()) continue;
            Files.createDirectories(dir.toPath());
            boolean downloaded = false;
            for (UploadQueue.Job job : copies) {
                if (!job.rec().equals(entry.id()) || !job.knownCopy()) continue;
                try {
                    library.download(job, audio);
                    downloaded = true;
                    break;
                } catch (Exception unavailable) {
                    Files.deleteIfExists(audio.toPath());
                }
            }
            if (!downloaded)
                throw new IOException(
                        "Vollsicherung unvollständig: Eine reine Cloudaufnahme konnte nicht"
                            + " verifiziert geladen werden. Verbindung und Netzregeln prüfen.");
            meta.put("name", name)
                    .put("displayName", entry.name())
                    .put("folder", entry.folder())
                    .put("start", entry.start().toString())
                    .put("frames", entry.frames())
                    .put("autoQueued", true);
            // Snapshot metadata can be hard-linked to the source. Publish a new inode before
            // writing.
            Files.deleteIfExists(description.toPath());
            write(description, meta);
        }
    }

    private static void snapshotSessions(Audomo app, File temp) throws Exception {
        File target = new File(temp, "sessions");
        Files.createDirectory(target.toPath());
        if (!app.sessions.root.exists()) return;
        for (File dir : children(app.sessions.root)) {
            if (!sessionId(dir.getName())
                    || !dir.isDirectory()
                    || Files.isSymbolicLink(dir.toPath()))
                throw new IOException(
                        "Unvollständiger Bibliotheksvorgang. Audomo neu öffnen und erneut"
                            + " sichern.");
            File copy = new File(target, dir.getName());
            Files.createDirectory(copy.toPath());
            for (File f : children(dir)) {
                if (!leaf(f.getName())
                        || !f.isFile()
                        || Files.isSymbolicLink(f.toPath())
                        || f.getName().equals("capture.part"))
                    throw new IOException("Unabgeschlossene Aufnahme in der Bibliothek");
                try {
                    Files.createLink(new File(copy, f.getName()).toPath(), f.toPath());
                } catch (IOException | UnsupportedOperationException unavailable) {
                    Files.copy(f.toPath(), new File(copy, f.getName()).toPath());
                }
            }
        }
    }

    static void restore(Audomo app, InputStream source, Scope requested, char[] password)
            throws Exception {
        begin(app);
        File stage = null;
        try {
            File home = app.getNoBackupFilesDir();
            stage = Files.createTempDirectory(home.toPath(), "migration-stage-").toFile();
            BackupArchive archive = BackupArchive.read(source);
            if (requested.ordinal() > archive.scope.ordinal())
                throw new IOException("Diese Daten sind in der Sicherung nicht enthalten");
            JSONObject values;
            try (DataInputStream in = archive.decrypt(source, password)) {
                values = unpack(app, stage, in, archive.scope, requested);
            }
            if (requested == Scope.FULL) {
                validateSessions(app, stage);
                BackupDatabase.prepare(stage);
            }
            UploadQueue queue = UploadQueue.get(app);
            synchronized (app.sessions) {
                synchronized (queue) {
                    synchronized (app) {
                        JSONObject before = new JSONObject();
                        for (String group : BackupPreferences.GROUPS)
                            if (values.has(group))
                                before.put(group, BackupPreferences.read(app, group));
                        JSONObject journal =
                                new JSONObject()
                                        .put("before", before)
                                        .put("id", UUID.randomUUID().toString())
                                        .put("full", requested == Scope.FULL)
                                        .put("sessions", app.sessions.root.exists())
                                        .put(
                                                "log",
                                                new File(app.getFilesDir(), "diagnose.txt")
                                                        .exists());
                        write(new File(stage, "journal.json"), journal);
                        File pending = new File(home, PENDING);
                        Files.move(
                                stage.toPath(), pending.toPath(), StandardCopyOption.ATOMIC_MOVE);
                        SessionStore.syncDirectory(home);
                        stage = null;
                        try {
                            apply(app, queue.getWritableDatabase(), pending, values, journal);
                        } finally {
                            finishJournal(app, pending);
                        }
                        app.draft = new RecordingDraft(app);
                    }
                }
            }
        } finally {
            app.backupBusy = false;
            if (stage != null) remove(stage);
        }
        UploadQueue q = UploadQueue.get(app);
        q.reconcile();
    }

    private static JSONObject unpack(
            Audomo app, File stage, DataInputStream in, Scope stored, Scope selected)
            throws Exception {
        Set<String> seen = new HashSet<>();
        JSONObject values = null;
        if (selected == Scope.FULL) Files.createDirectory(new File(stage, "sessions").toPath());
        while (true) {
            String key = in.readUTF();
            if (key.isEmpty()) break;
            if (!seen.add(key)) throw new IOException("Doppelter Archiveintrag");
            long length = in.readLong();
            if (length < 0) throw new IOException("Ungültige Archivgröße");
            if (key.equals("settings")) {
                if (length > MAX_CONFIG) throw new IOException("Einstellungsdaten zu groß");
                byte[] raw = new byte[(int) length];
                in.readFully(raw);
                try {
                    values =
                            BackupPreferences.prepare(
                                    app,
                                    new JSONObject(new String(raw, StandardCharsets.UTF_8)),
                                    selected);
                } finally {
                    Arrays.fill(raw, (byte) 0);
                }
                continue;
            }
            if (stored != Scope.FULL) throw new IOException("Unerwartete Archivdaten");
            File file;
            if (BackupDatabase.TABLES.contains(key)) file = new File(stage, key + ".jsonl");
            else if (key.equals("diagnose")) file = new File(stage, "diagnose.txt");
            else {
                String[] parts = key.split("/", -1);
                if (parts.length != 3
                        || !parts[0].equals("sessions")
                        || !sessionId(parts[1])
                        || !leaf(parts[2])) throw new IOException("Ungültiger Dateipfad im Archiv");
                file = new File(stage, key);
            }
            if (selected != Scope.FULL) {
                BackupArchive.copy(in, OutputStream.nullOutputStream(), length);
                continue;
            }
            if (length > stage.getUsableSpace())
                throw new IOException("Zu wenig Speicherplatz für Wiederherstellung");
            Files.createDirectories(file.getParentFile().toPath());
            try (FileOutputStream out = new FileOutputStream(file)) {
                BackupArchive.copy(in, out, length);
                out.getFD().sync();
            }
        }
        if (values == null
                || stored == Scope.FULL && !seen.containsAll(BackupDatabase.TABLES)
                || in.read() != -1) throw new IOException("Unvollständiges Sicherungsarchiv");
        return values;
    }

    private static void requireAudio(File dir, JSONObject meta) throws Exception {
        String name = meta.getString("name");
        if (!leaf(name) || name.equals("session.json") || !new File(dir, name).isFile())
            throw new IOException("Zu einem Bibliothekseintrag fehlt die Audiodatei");
    }

    private static void validateSessions(Audomo app, File stage) throws Exception {
        for (File dir : children(new File(stage, "sessions"))) {
            File f = new File(dir, "session.json");
            JSONObject m = read(f);
            requireAudio(dir, m);
            if (!LibraryFiles.validFolderPath(m.optString("folder")))
                throw new IOException("Ungültige Aufnahmedaten");
            OffsetDateTime.parse(m.getString("start"));
            if (!BackupPreferences.allowedTree(app, m.optString("documentTree"))) {
                JSONObject detached = new JSONObject();
                for (String key :
                        List.of(
                                "documentTree",
                                "deliveredUri",
                                "obsoleteUri",
                                "pendingUri",
                                "deliveredHash")) {
                    if (m.has(key)) detached.put(key, m.remove(key));
                }
                if (detached.has("documentTree")) m.put("previousDocumentStorage", detached);
                write(f, m);
            }
            SessionStore.syncDirectory(dir);
        }
        SessionStore.syncDirectory(new File(stage, "sessions"));
    }

    private static void apply(
            Audomo app, SQLiteDatabase db, File pending, JSONObject values, JSONObject journal)
            throws Exception {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TOKEN_TABLE + " (id TEXT PRIMARY KEY)");
        db.beginTransaction();
        try {
            if (journal.getBoolean("full")) {
                BackupDatabase.replace(db, pending);
                swap(
                        app.sessions.root,
                        new File(pending, "sessions"),
                        new File(pending, "old-sessions"));
                swap(
                        new File(app.getFilesDir(), "diagnose.txt"),
                        new File(pending, "diagnose.txt"),
                        new File(pending, "old-log"));
                SessionStore.syncDirectory(app.getFilesDir());
                SessionStore.syncDirectory(pending);
            }
            for (String group : BackupPreferences.GROUPS)
                if (values.has(group))
                    BackupPreferences.write(app, group, values.getJSONObject(group));
            db.delete(TOKEN_TABLE, null, null);
            db.execSQL(
                    "INSERT INTO " + TOKEN_TABLE + "(id) VALUES (?)",
                    new Object[] {journal.getString("id")});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private static void swap(File live, File incoming, File old) throws IOException {
        if (live.exists()) Files.move(live.toPath(), old.toPath(), StandardCopyOption.ATOMIC_MOVE);
        if (incoming.exists())
            Files.move(incoming.toPath(), live.toPath(), StandardCopyOption.ATOMIC_MOVE);
    }

    static void recover(Audomo app) {
        File home = app.getNoBackupFilesDir(), pending = new File(home, PENDING);
        try {
            if (pending.isDirectory()) finishJournal(app, pending);
            File[] exports =
                    app.getCacheDir()
                            .listFiles(
                                    (d, n) ->
                                            (n.startsWith("export-")
                                                            && new File(d, n).isDirectory())
                                                    || (n.startsWith("audomo-encrypted-")
                                                            && n.endsWith(".tmp")));
            if (exports != null) for (File f : exports) remove(f);
            File[] stale =
                    home.listFiles(
                            (d, n) ->
                                    n.startsWith("migration-stage-")
                                            || n.startsWith("migration-done-"));
            if (stale != null) for (File f : stale) remove(f);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Wiederherstellung konnte nicht abgeschlossen werden", e);
        }
    }

    private static void finishJournal(Audomo app, File pending) throws Exception {
        JSONObject j = read(new File(pending, "journal.json"));
        SQLiteDatabase db = UploadQueue.get(app).getWritableDatabase();
        db.execSQL("CREATE TABLE IF NOT EXISTS " + TOKEN_TABLE + " (id TEXT PRIMARY KEY)");
        boolean committed;
        try (var c =
                db.rawQuery(
                        "SELECT 1 FROM " + TOKEN_TABLE + " WHERE id=?",
                        new String[] {j.getString("id")})) {
            committed = c.moveToFirst();
        }
        if (!committed) {
            if (j.getBoolean("full")) {
                undo(
                        app.sessions.root,
                        new File(pending, "old-sessions"),
                        j.getBoolean("sessions"));
                undo(
                        new File(app.getFilesDir(), "diagnose.txt"),
                        new File(pending, "old-log"),
                        j.getBoolean("log"));
            }
            JSONObject old = j.getJSONObject("before");
            for (String group : BackupPreferences.GROUPS)
                if (old.has(group)) BackupPreferences.write(app, group, old.getJSONObject(group));
            SessionStore.syncDirectory(app.getFilesDir());
        }
        File done = new File(pending.getParentFile(), "migration-done-" + j.getString("id"));
        Files.move(pending.toPath(), done.toPath(), StandardCopyOption.ATOMIC_MOVE);
        SessionStore.syncDirectory(done.getParentFile());
        remove(done);
    }

    private static void undo(File live, File old, boolean existed) throws IOException {
        if (old.exists()) {
            remove(live);
            Files.move(old.toPath(), live.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } else if (!existed) remove(live);
    }

    private static boolean leaf(String name) {
        return !name.isBlank()
                && !name.equals(".")
                && !name.equals("..")
                && !name.contains("/")
                && !name.contains("\\")
                && name.chars().noneMatch(Character::isISOControl);
    }

    private static boolean sessionId(String id) {
        try {
            return UUID.fromString(id).toString().equals(id);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static File[] children(File dir) throws IOException {
        File[] list = dir.listFiles();
        if (list == null) throw new IOException("Bibliotheksverzeichnis nicht lesbar");
        return list;
    }

    private static void remove(File f) throws IOException {
        if (!f.exists()) return;
        if (f.isDirectory() && !Files.isSymbolicLink(f.toPath()))
            for (File child : children(f)) remove(child);
        Files.deleteIfExists(f.toPath());
    }
}
