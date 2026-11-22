package de.audomo;

import android.app.*;
import android.app.job.*;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.net.*;

import java.io.*;
import java.util.*;

final class UploadQueue extends SQLiteOpenHelper {
    static final int JOB = 74;
    private static UploadQueue singleton;
    final Context context;

    static synchronized UploadQueue get(Context c) {
        if (singleton == null) singleton = new UploadQueue(c.getApplicationContext());
        return singleton;
    }

    private UploadQueue(Context c) {
        super(c, "uploads.db", null, 2);
        context = c;
    }

    public void onCreate(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE jobs(id INTEGER PRIMARY KEY, rec TEXT NOT NULL,target TEXT NOT"
                    + " NULL,path TEXT NOT NULL,state TEXT NOT NULL,progress INTEGER DEFAULT"
                    + " 0,attempted INTEGER DEFAULT 0,replacement TEXT,error TEXT DEFAULT '',action"
                    + " TEXT DEFAULT 'upload',origin TEXT DEFAULT '',verified TEXT DEFAULT"
                    + " '',UNIQUE(rec,target))");
    }

    public void onUpgrade(SQLiteDatabase db, int from, int to) {
        if (from == 1 && to == 2) {
            db.execSQL("ALTER TABLE jobs ADD COLUMN action TEXT DEFAULT 'upload'");
            db.execSQL("ALTER TABLE jobs ADD COLUMN origin TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE jobs ADD COLUMN verified TEXT DEFAULT ''");
        } else throw new IllegalStateException("Unbekannte Datenbankversion");
    }

    record Job(
            long id,
            String rec,
            String target,
            String path,
            String state,
            int progress,
            boolean attempted,
            String replacement,
            String error,
            String action,
            String origin,
            String verified) {}

    synchronized List<Job> jobs() {
        ArrayList<Job> result = new ArrayList<>();
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT"
                                    + " id,rec,target,path,state,progress,attempted,replacement,error,action,origin,verified"
                                    + " FROM jobs ORDER BY id DESC",
                                null)) {
            while (c.moveToNext())
                result.add(
                        new Job(
                                c.getLong(0),
                                c.getString(1),
                                c.getString(2),
                                c.getString(3),
                                c.getString(4),
                                c.getInt(5),
                                c.getInt(6) != 0,
                                c.getString(7),
                                c.getString(8),
                                c.getString(9),
                                c.getString(10),
                                c.getString(11)));
        }
        return result;
    }

    synchronized Job job(String rec) {
        for (Job j : jobs()) if (rec.equals(j.rec)) return j;
        return null;
    }

    synchronized void enqueue(SessionStore.Entry e, CloudConfig target) {
        if (e.trashed() != 0) throw new IllegalArgumentException("Aufnahme liegt im Papierkorb");
        String folder = Profile.prefs(context).getBoolean("mirrorFolders", true) ? e.folder() : "";
        String path = CloudConfig.path(target.base() + "/" + folder + "/" + e.name());
        getWritableDatabase()
                .execSQL(
                        "INSERT OR IGNORE INTO jobs(rec,target,path,state) VALUES(?,?,?,'Wartet')",
                        new Object[] {e.id(), target.id(), path});
        getWritableDatabase()
                .execSQL(
                        "UPDATE jobs SET"
                            + " path=?,action='upload',state='Wartet',origin='',replacement=NULL,verified='',attempted=0,error='',progress=0"
                            + " WHERE rec=? AND target=? AND state IN ('Ausgelassen','Cloudkopie"
                            + " gelöscht')",
                        new Object[] {path, e.id(), target.id()});
        schedule(false);
    }

    synchronized void followLocalChange(SessionStore.Entry entry, boolean delete) throws Exception {
        for (Job j : jobs()) {
            if (!j.rec().equals(entry.id())
                    || !j.state().equals("Hochgeladen")
                    || j.verified().isEmpty()) continue;
            CloudConfig target = CloudConfig.byId(context, j.target());
            String folder =
                    Profile.prefs(context).getBoolean("mirrorFolders", true) ? entry.folder() : "";
            String next =
                    delete
                            ? j.path()
                            : CloudConfig.path(target.base() + "/" + folder + "/" + entry.name());
            if (!delete && next.equals(j.path())) continue;
            getWritableDatabase()
                    .execSQL(
                            "UPDATE jobs SET"
                                + " action=?,origin=path,path=?,progress=0,attempted=0,replacement=NULL,error='',state='Wartet'"
                                + " WHERE id=?",
                            new Object[] {delete ? "delete" : "move", next, j.id()});
        }
        if (pending()) schedule(false);
    }

    boolean hasCloud(String id) {
        return jobs().stream()
                .anyMatch(j ->
                        j.rec().equals(id)
                                && j.state().equals("Hochgeladen")
                                && !j.verified().isEmpty());
    }

    void verified(long id, String hash) {
        getWritableDatabase()
                .execSQL(
                        "UPDATE jobs SET"
                            + " state='Hochgeladen',action='upload',origin='',verified=?,progress=100,error=''"
                            + " WHERE id=?",
                        new Object[] {hash, id});
    }

    static void afterCapture(Audomo app, File f) throws Exception {
        synchronized (app.sessions) {
            org.json.JSONObject meta = SessionStore.metadata(f.getParentFile());
            String id = meta.optString("autoTarget");
            if (id.isEmpty() || meta.optBoolean("autoQueued")) return;
            if (Profile.prefs(app).getBoolean("autoUpload", false)) {
                CloudConfig t = CloudConfig.byId(app, id);
                SessionStore.Entry e = SessionStore.entry(f.getParentFile());
                if (e == null || e.trashed() != 0) return;
                get(app).enqueue(e, t);
            }
            meta.put("autoQueued", true);
            SessionStore.storeMeta(f.getParentFile(), meta);
        }
    }

    void schedule(boolean replace) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (!replace && jobs.getPendingJob(JOB) != null) return;
        NetworkRequest.Builder network =
                new NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        if (Profile.prefs(context).getBoolean("wifiOnly", true))
            network.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        JobInfo job =
                new JobInfo.Builder(JOB, new ComponentName(context, UploadWorker.class))
                        .setRequiredNetwork(network.build())
                        .setPersisted(true)
                        .setBackoffCriteria(30000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                        .build();
        jobs.schedule(job);
    }

    synchronized void state(long id, String state, String err) {
        getWritableDatabase()
                .execSQL(
                        "UPDATE jobs SET state=?,error=? WHERE id=?",
                        new Object[] {state, err, id});
    }

    void sent(long id) {
        getWritableDatabase().execSQL("UPDATE jobs SET attempted=1 WHERE id=?", new Object[] {id});
    }

    void progress(long id, int value) {
        getWritableDatabase()
                .execSQL("UPDATE jobs SET progress=? WHERE id=?", new Object[] {value, id});
    }

    synchronized void resolve(Job j, int choice) throws Exception {
        if (!"Konflikt".equals(j.state)) throw new IOException("Kein offener Konflikt");
        if (choice == 2) {
            if (j.action.equals("move"))
                getWritableDatabase()
                        .execSQL(
                                "UPDATE jobs SET"
                                    + " path=origin,origin='',action='upload',state='Hochgeladen',error='',progress=100"
                                    + " WHERE id=?",
                                new Object[] {j.id});
            else state(j.id, "Ausgelassen", "");
            return;
        }
        String path = j.path;
        if (choice == 0) {
            String ext = LibraryFiles.extension(path);
            path =
                    path.substring(0, path.length() - ext.length())
                            + " ("
                            + UUID.randomUUID().toString().substring(0, 8)
                            + ")"
                            + ext;
        }
        getWritableDatabase()
                .execSQL(
                        "UPDATE jobs SET path=?,attempted=0,state='Wartet',replacement=?,error=''"
                                + " WHERE id=? AND state='Konflikt'",
                        new Object[] {path, choice == 1 ? j.replacement : null, j.id});
        schedule(false);
    }

    void conflict(long id, String hash) {
        getWritableDatabase()
                .execSQL(
                        "UPDATE jobs SET state='Konflikt',replacement=?,error='Namenskonflikt'"
                                + " WHERE id=?",
                        new Object[] {hash, id});
    }

    void retry(long id) {
        getWritableDatabase()
                .execSQL(
                        "UPDATE jobs SET state='Wartet',error='' WHERE id=? AND state='Fehler'",
                        new Object[] {id});
        schedule(false);
    }

    boolean pending() {
        return jobs().stream()
                .anyMatch(j -> List.of("Fehler", "Wartet", "Upload läuft").contains(j.state));
    }

    void reconcile() {
        if (pending()) schedule(false);
    }
}
