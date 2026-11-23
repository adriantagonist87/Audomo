package de.audomo;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.*;

import org.json.JSONObject;

import java.io.*;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;

final class CloudLibrary {
    static final OffsetDateTime UNKNOWN_DATE = OffsetDateTime.MIN;
    final SessionStore store;
    final UploadQueue queue;

    CloudLibrary(SessionStore store) {
        this.store = store;
        queue = UploadQueue.get(store.context);
    }

    static void remember(SQLiteDatabase db, SessionStore.Entry e) {
        db.execSQL(
                "INSERT OR REPLACE INTO recordings(rec,name,folder,start,frames,bytes)"
                        + " VALUES(?,?,?,?,?,?)",
                new Object[] {
                    e.id(), e.name(), e.folder(), e.start().toString(), e.frames(), e.bytes()
                });
    }

    // Older releases kept verified hashes and paths in the queue but indexed only local audio.
    private void migrate(List<UploadQueue.Job> jobs, Map<String, SessionStore.Entry> records)
            throws Exception {
        Map<String, CloudConfig> targets = new HashMap<>();
        for (CloudConfig t : CloudConfig.all(store.context)) targets.put(t.id(), t);
        SQLiteDatabase db = queue.getWritableDatabase();
        for (UploadQueue.Job j : jobs) {
            if (!j.knownCopy()) continue;
            CloudConfig target = targets.get(j.target());
            if (j.provider().isEmpty() && target != null)
                db.execSQL(
                        "UPDATE jobs SET provider=? WHERE id=?",
                        new Object[] {target.protocol(), j.id()});
            if (records.containsKey(j.rec())) continue;
            String path = j.confirmedPath();
            String name = RemoteFiles.leaf(path);
            String folder = "";
            if (target != null
                    && path.startsWith(target.base().equals("/") ? "/" : target.base() + "/")) {
                String relative =
                        path.substring(target.base().equals("/") ? 1 : target.base().length() + 1);
                int slash = relative.lastIndexOf('/');
                if (slash >= 0) folder = relative.substring(0, slash);
            }
            if (!LibraryFiles.validFolderPath(folder)) folder = "";
            SessionStore.Entry row = cloudEntry(j.rec(), name, folder, UNKNOWN_DATE, -1, -1);
            File dir = row.file().getParentFile();
            if (new File(dir, "session.json").isFile()) {
                try {
                    JSONObject meta = SessionStore.metadata(dir);
                    File audio = new File(dir, meta.getString("name"));
                    row =
                            cloudEntry(
                                    j.rec(),
                                    meta.optString("displayName", audio.getName()),
                                    meta.optString("folder"),
                                    OffsetDateTime.parse(meta.getString("start")),
                                    meta.optLong("frames", -1),
                                    audio.isFile() ? audio.length() : -1);
                } catch (IOException
                        | org.json.JSONException
                        | java.time.DateTimeException damaged) {
                    if (store.context instanceof Audomo app)
                        app.diagnostic(
                                "Warnung · Cloudpfad für nicht lesbare Sitzungsdaten übernommen");
                }
            }
            remember(db, row);
            records.put(j.rec(), row);
            new LibraryFiles(store).ensureFolder(row.folder());
        }
    }

    private SessionStore.Entry cloudEntry(
            String id, String name, String folder, OffsetDateTime date, long frames, long bytes)
            throws IOException {
        if (id.isEmpty()
                || id.startsWith(".")
                || id.contains("/")
                || !LibraryFiles.validFolderPath(folder))
            throw new IOException("Ungültiger Katalogeintrag");
        File dir = new File(store.root, id);
        return new SessionStore.Entry(
                new File(dir, "audio" + LibraryFiles.extension(name)),
                frames,
                date,
                name,
                folder,
                0,
                true,
                bytes);
    }

    Map<String, SessionStore.Entry> known() throws Exception {
        synchronized (store) {
            List<UploadQueue.Job> jobs = queue.jobs();
            Map<String, SessionStore.Entry> records = new LinkedHashMap<>();
            try (Cursor c =
                    queue.getReadableDatabase()
                            .rawQuery(
                                    "SELECT rec,name,folder,start,frames,bytes FROM recordings"
                                            + " WHERE EXISTS (SELECT 1 FROM jobs WHERE"
                                            + " jobs.rec=recordings.rec AND verified!='' AND"
                                            + " state!='Cloudkopie gelöscht')",
                                    null)) {
                while (c.moveToNext()) {
                    SessionStore.Entry e =
                            cloudEntry(
                                    c.getString(0),
                                    c.getString(1),
                                    c.getString(2),
                                    OffsetDateTime.parse(c.getString(3)),
                                    c.getLong(4),
                                    c.getLong(5));
                    records.put(e.id(), e);
                }
            }
            migrate(jobs, records);
            return records;
        }
    }

    List<SessionStore.Entry> library() throws Exception {
        synchronized (store) {
            List<SessionStore.Entry> local = store.list();
            Map<String, SessionStore.Entry> cloud = known();
            for (SessionStore.Entry e : local) if (e.trashed() == 0) cloud.remove(e.id());
            local.addAll(cloud.values());
            return local;
        }
    }

    static Network network(Context c) throws IOException {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        Network net = cm.getActiveNetwork();
        checkNetwork(c, net);
        return net;
    }

    static void checkNetwork(Context c, Network net) throws IOException {
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        NetworkCapabilities caps = net == null ? null : cm.getNetworkCapabilities(net);
        if (caps == null
                || Profile.prefs(c).getBoolean("wifiOnly", true)
                        && !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
            throw new IOException("Netzregel erlaubt keine Übertragung");
    }

    void restore(SessionStore.Entry e, UploadQueue.Job j) throws Exception {
        CloudConfig t = CloudConfig.byId(store.context, j.target());
        Network net = network(store.context);
        File temp = File.createTempFile("cloud-", ".part", store.context.getCacheDir());
        try {
            try (RemoteFiles remote =
                    RemoteFiles.open(
                            t,
                            CloudConfig.secret(store.context, t.id()),
                            net,
                            bytes -> checkNetwork(store.context, net))) {
                remote.downloadVerified(j.confirmedPath(), j.verified(), temp);
            }
            synchronized (store) {
                if (!queue.jobs().contains(j))
                    throw new IOException("Cloudpfad inzwischen geändert");
                File dir = e.file().getParentFile();
                Files.createDirectories(dir.toPath());
                if (new File(dir, "capture.part").exists())
                    throw new IOException("Aufnahme ist aktiv");
                JSONObject meta =
                        new File(dir, "session.json").exists()
                                ? SessionStore.metadata(dir)
                                : new JSONObject();
                File audio = new File(dir, meta.optString("name", e.file().getName()));
                if (audio.exists()
                        && !LibraryFiles.digest(new FileInputStream(audio)).equals(j.verified()))
                    throw new IOException("Eine abweichende lokale Datei ist vorhanden");
                new LibraryFiles(store).ensureFolder(e.folder());
                if (!audio.exists())
                    Files.move(temp.toPath(), audio.toPath(), StandardCopyOption.ATOMIC_MOVE);
                long frames = e.frames();
                if (frames < 0) {
                    android.media.MediaMetadataRetriever media =
                            new android.media.MediaMetadataRetriever();
                    try {
                        media.setDataSource(audio.getPath());
                        frames =
                                Long.parseLong(
                                                media.extractMetadata(
                                                        android.media.MediaMetadataRetriever
                                                                .METADATA_KEY_DURATION))
                                        * 48;
                    } finally {
                        media.release();
                    }
                }
                meta.put("name", audio.getName())
                        .put("displayName", store.availableName(e.name(), e.folder(), dir))
                        .put("start", e.start().toString())
                        .put("frames", frames)
                        .put("folder", e.folder())
                        .put("trashed", 0);
                SessionStore.storeMeta(dir, meta);
                remember(queue.getWritableDatabase(), SessionStore.entry(dir));
                DocumentStorage.deliver(store.context, audio);
            }
        } finally {
            Files.deleteIfExists(temp.toPath());
        }
    }
}
