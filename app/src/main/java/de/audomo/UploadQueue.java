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
    static final int DELAY_TIMER = 75;
    private static final String PENDING = "state IN ('Wartet','Fehler','Upload läuft')";
    private static UploadQueue singleton;
    final Context context;
    private final Map<Long, Attempt> active = new HashMap<>();
    private boolean restartAfterAccessChange;

    static final class Attempt {
        final long id;
        volatile boolean cancelled;
        boolean committing, finishOnly;
        String expectedHash = "";

        Attempt(long id) {
            this.id = id;
        }

        void check() throws IOException {
            if (cancelled) throw new IOException("Auftrag beendet");
        }
    }

    static synchronized UploadQueue get(Context c) {
        if (singleton == null) singleton = new UploadQueue(c.getApplicationContext());
        return singleton;
    }

    private UploadQueue(Context c) {
        super(c, "uploads.db", null, 7);
        context = c;
    }

    public void onCreate(SQLiteDatabase db) {
        createJobs(db);
        createRecordings(db);
    }

    private static final String OLD_COLUMNS =
            "id,rec,target,path,state,progress,attempted,replacement,error,"
                + "action,origin,verified,provider,availability,notBefore,attempts,lastAttempt,lastChecked";

    static void createJobs(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE jobs(id INTEGER PRIMARY KEY AUTOINCREMENT,rec TEXT NOT NULL,target"
                    + " TEXT NOT NULL,path TEXT NOT NULL,state TEXT NOT NULL,progress INTEGER"
                    + " DEFAULT 0,attempted INTEGER DEFAULT 0,replacement TEXT,error TEXT DEFAULT"
                    + " '',action TEXT DEFAULT 'upload',origin TEXT DEFAULT '',verified TEXT"
                    + " DEFAULT '',provider TEXT NOT NULL DEFAULT '',availability TEXT NOT NULL"
                    + " DEFAULT '',notBefore INTEGER NOT NULL DEFAULT 0,attempts INTEGER NOT NULL"
                    + " DEFAULT 0,lastAttempt INTEGER NOT NULL DEFAULT 0,lastChecked INTEGER NOT"
                    + " NULL DEFAULT 0,priority INTEGER NOT NULL DEFAULT 0,inQueue INTEGER NOT NULL"
                    + " DEFAULT 1,publicationHash TEXT DEFAULT '',UNIQUE(rec,target))");
    }

    static void createRecordings(SQLiteDatabase db) {
        db.execSQL(
                "CREATE TABLE recordings(rec TEXT PRIMARY KEY,name TEXT NOT NULL,folder TEXT NOT"
                    + " NULL,start TEXT NOT NULL,frames INTEGER NOT NULL,bytes INTEGER NOT NULL)");
    }

    private void addCatalog(SQLiteDatabase db) {
        createRecordings(db);
        db.execSQL("ALTER TABLE jobs ADD COLUMN provider TEXT NOT NULL DEFAULT ''");
        db.execSQL("ALTER TABLE jobs ADD COLUMN availability TEXT NOT NULL DEFAULT ''");
    }

    private void addDelay(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE jobs ADD COLUMN notBefore INTEGER NOT NULL DEFAULT 0");
    }

    private void addHistory(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE jobs ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE jobs ADD COLUMN lastAttempt INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE jobs ADD COLUMN lastChecked INTEGER NOT NULL DEFAULT 0");
    }

    public void onUpgrade(SQLiteDatabase db, int from, int to) {
        if (from < 1 || to != 7) throw new IllegalStateException("Unbekannte Datenbankversion");
        if (from == 1) {
            db.execSQL("ALTER TABLE jobs ADD COLUMN action TEXT DEFAULT 'upload'");
            db.execSQL("ALTER TABLE jobs ADD COLUMN origin TEXT DEFAULT ''");
            db.execSQL("ALTER TABLE jobs ADD COLUMN verified TEXT DEFAULT ''");
        }
        if (from <= 2) addCatalog(db);
        if (from <= 3) addDelay(db);
        if (from <= 4) addHistory(db);
        if (from < 6) {
            db.execSQL("ALTER TABLE jobs RENAME TO old_jobs");
            createJobs(db);
            db.execSQL("INSERT INTO jobs(" + OLD_COLUMNS + ") SELECT " + OLD_COLUMNS + " FROM old_jobs");
            db.execSQL("DROP TABLE old_jobs");
        } else {
            db.execSQL("ALTER TABLE jobs ADD COLUMN publicationHash TEXT DEFAULT ''");
        }
        // Old versions marked a send before transferring bytes, without persisting the commit boundary.
        db.execSQL("UPDATE jobs SET publicationHash=NULL WHERE action='upload' AND attempted=1"
                + " AND state NOT IN ('Hochgeladen','Cloudkopie gelöscht','Konflikt')");
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
            String verified,
            String provider,
            String availability,
            long notBefore,
            int attempts,
            long lastAttempt,
            long lastChecked,
            long priority,
            boolean inQueue,
            String publicationHash) {
        boolean disposableUpload() {
            return inQueue && action.equals("upload") && !state.equals("Hochgeladen")
                    && !state.equals("Cloudkopie gelöscht");
        }

        boolean canPrioritize() {
            return inQueue && (state.equals("Wartet") || state.equals("Fehler"));
        }

        boolean knownCopy() {
            return !verified.isEmpty() && !state.equals("Cloudkopie gelöscht");
        }

        boolean open() {
            return List.of("Wartet", "Upload läuft", "Fehler", "Konflikt").contains(state);
        }

        boolean needsAttention() {
            return state.equals("Fehler") || state.equals("Konflikt") || !availability.isEmpty();
        }

        boolean finished() {
            return !open() && !needsAttention();
        }

        boolean canRename() {
            return (knownCopy() && state.equals("Hochgeladen") && availability.isEmpty())
                    || (!knownCopy() && action.equals("upload") && !attempted
                            && (state.equals("Wartet") || state.equals("Fehler")));
        }

        String confirmedPath() {
            return knownCopy() && !origin.isEmpty() ? origin : path;
        }
    }

    private static final String SELECT = "SELECT " + OLD_COLUMNS + ",priority,inQueue,publicationHash FROM jobs";

    private static Job readJob(Cursor c) {
        return new Job(
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
                c.getString(11),
                c.getString(12),
                c.getString(13),
                c.getLong(14),
                c.getInt(15),
                c.getLong(16),
                c.getLong(17),
                c.getLong(18),
                c.getInt(19) != 0, c.getString(20));
    }

    List<Job> listedJobs() {
        return readJobs(" WHERE inQueue=1");
    }

    List<Job> jobs() {
        return readJobs(" WHERE inQueue=1 OR (verified!='' AND state!='Cloudkopie gelöscht')");
    }

    private synchronized List<Job> readJobs(String condition) {
        ArrayList<Job> result = new ArrayList<>();
        try (Cursor c =
                getReadableDatabase().rawQuery(SELECT + condition + " ORDER BY id DESC", null)) {
            while (c.moveToNext()) result.add(readJob(c));
        }
        return result;
    }

    synchronized List<Job> restoreCandidates(String target, String path, String rec) {
        List<Job> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery(SELECT
                + " WHERE target=? AND (rec=? OR path=? COLLATE NOCASE OR origin=? COLLATE NOCASE)"
                + " AND state!='Cloudkopie gelöscht'", new String[]{target, rec, path, path})) {
            while (c.moveToNext()) result.add(readJob(c));
        }
        return result;
    }

    synchronized Job job(String rec) {
        for (Job j : jobs()) if (rec.equals(j.rec)) return j;
        return null;
    }

    void enqueue(SessionStore.Entry entry, CloudConfig target) {
        enqueueBatch(List.of(entry), List.of(target), 0, true);
    }

    int enqueueSelected(SessionStore store, Collection<String> ids, CloudConfig target)
            throws Exception {
        return enqueueSelected(store, ids, List.of(target));
    }

    int enqueueSelected(SessionStore store, Collection<String> ids, Collection<CloudConfig> targets)
            throws Exception {
        synchronized (store) {
            if (targets.isEmpty()) throw new IOException("Kein Uploadziel ausgewählt");
            if (ids.isEmpty()) throw new IOException("Keine Aufnahmen ausgewählt");
            List<SessionStore.Entry> selected = new ArrayList<>();
            for (String id : new LinkedHashSet<>(ids)) {
                SessionStore.Entry entry = store.byId(id);
                if (entry == null || entry.trashed() != 0)
                    throw new IOException(
                            "Die Auswahl hat sich geändert. Bitte Aufnahmen erneut auswählen.");
                selected.add(entry);
            }
            Set<CloudConfig> checked = new LinkedHashSet<>(targets);
            for (CloudConfig target : checked) {
                CloudConfig current = CloudConfig.byId(context, target.id());
                if (!target.equals(current) || !current.hasCredentials(context))
                    throw new IOException("Uploadziel erneut verbinden");
            }
            return enqueueBatch(selected, checked, 0, true);
        }
    }

    private synchronized int enqueueBatch(
            List<SessionStore.Entry> entries,
            Collection<CloudConfig> targets,
            long notBefore,
            boolean manual) {
        SQLiteDatabase db = getWritableDatabase();
        boolean mirror = Profile.prefs(context).getBoolean("mirrorFolders", true);
        int added = 0;
        db.beginTransaction();
        try (SQLiteStatement insert =
                        db.compileStatement(
                                "INSERT OR IGNORE INTO"
                                        + " jobs(rec,target,path,state,provider,notBefore)"
                                        + " VALUES(?,?,?,'Wartet',?,?)");
                SQLiteStatement restart =
                        db.compileStatement(
                                "UPDATE jobs SET path=CASE WHEN state='Hochgeladen' THEN path ELSE"
                                    + " ? END,inQueue=1,priority=0,notBefore=?,action='upload',state='Wartet',origin='',replacement=NULL,verified=CASE"
                                    + " WHEN state='Hochgeladen' THEN verified ELSE ''"
                                    + " END,publicationHash='',attempted=0,error='',progress=0 WHERE rec=? AND"
                                    + " target=? AND (state IN ('Cloudkopie gelöscht') OR"
                                    + " (action='upload' AND state IN"
                                    + " ('Ausgelassen','Abgebrochen')) OR (state='Hochgeladen' AND"
                                    + " availability='Cloudkopie fehlt'))")) {
            for (CloudConfig target : targets) {
                for (SessionStore.Entry e : entries) {
                    if (e.trashed() != 0)
                        throw new IllegalArgumentException("Aufnahme liegt im Papierkorb");
                    String folder = mirror ? e.folder() : "";
                    String path = CloudConfig.path(target.base() + "/" + folder + "/" + e.name());
                    insert.bindString(1, e.id());
                    insert.bindString(2, target.id());
                    insert.bindString(3, path);
                    insert.bindString(4, target.protocol());
                    insert.bindLong(5, notBefore);
                    CloudLibrary.remember(db, e);
                    if (insert.executeInsert() != -1) {
                        added++;
                    } else {
                        restart.bindString(1, path);
                        restart.bindLong(2, notBefore);
                        restart.bindString(3, e.id());
                        restart.bindString(4, target.id());
                        int restarted = restart.executeUpdateDelete();
                        added += restarted;
                        if (manual && restarted == 0) {
                            db.execSQL(
                                    "UPDATE jobs SET inQueue=1 WHERE rec=? AND target=? AND"
                                            + " inQueue=0 AND state='Hochgeladen'",
                                    new Object[] {e.id(), target.id()});
                            ContentValues ready = new ContentValues();
                            ready.put("notBefore", 0);
                            added +=
                                    db.update(
                                            "jobs",
                                            ready,
                                            "rec=? AND target=? AND state='Wartet' AND notBefore>?",
                                            new String[] {
                                                e.id(),
                                                target.id(),
                                                Long.toString(System.currentTimeMillis())
                                            });
                        }
                    }
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (added > 0 || pending()) schedule(false);
        return added;
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
                                + " action=?,origin=path,path=?,inQueue=1,priority=0,notBefore=0,progress=0,attempted=0,replacement=NULL,error='',state='Wartet'"
                                + " WHERE id=?",
                            new Object[] {delete ? "delete" : "move", next, j.id()});
        }
        if (pending()) schedule(false);
    }

    synchronized void checkRenameTargets(String recording, List<Job> selected) throws IOException {
        for (Job shown : selected) {
            Job current = byId(shown.id);
            if (current == null || !current.rec.equals(recording) || !current.canRename()
                    || active.containsKey(current.id) || !current.confirmedPath().equals(shown.confirmedPath())
                    || !current.verified.equals(shown.verified))
                throw new IOException("Der Cloudauftrag hat sich geändert. Speicherorte erneut öffnen.");
        }
    }

    synchronized int renameTargets(String recording, List<Job> selected, String name) throws IOException {
        checkRenameTargets(recording, selected);
        int changed = 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (Job shown : selected) {
                Job job = byId(shown.id);
                String path = CloudConfig.path(RemoteFiles.parent(job.confirmedPath()) + "/" + name);
                if (job.path.equals(path)) continue;
                if (job.knownCopy()) {
                    db.execSQL("UPDATE jobs SET action='rename',origin=path,path=?,inQueue=1,priority=0,"
                                    + "notBefore=0,progress=0,attempted=0,replacement=NULL,error='',state='Wartet'"
                                    + " WHERE id=?", new Object[]{path, job.id});
                } else {
                    db.execSQL("UPDATE jobs SET path=?,state='Wartet',error='' WHERE id=?",
                            new Object[]{path, job.id});
                }
                changed++;
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (changed > 0) schedule(false);
        return changed;
    }

    void finishRename(SessionStore store, Job job, Attempt attempt) throws Exception {
        synchronized (store) {
            synchronized (this) {
                attempt.check();
                SQLiteDatabase db = getWritableDatabase();
                db.beginTransaction();
                try {
                    updateCloudName(store, job);
                    verified(job.id, job.verified);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
        }
    }

    private void updateCloudName(SessionStore store, Job job) throws Exception {
        SessionStore.Entry local = store.byId(job.rec);
        if (local == null || local.trashed() != 0)
            getWritableDatabase().execSQL("UPDATE recordings SET name=? WHERE rec=?",
                    new Object[]{RemoteFiles.leaf(job.path), job.rec});
    }

    boolean hasCloud(String id) {
        return jobs().stream().anyMatch(j -> j.rec().equals(id) && j.knownCopy());
    }

    void verified(long id, String hash) {
        getWritableDatabase()
                .execSQL(
                        "UPDATE jobs SET"
                            + " state='Hochgeladen',action='upload',origin='',replacement=NULL,publicationHash='',verified=?,availability='',lastChecked=?,progress=100,error=''"
                            + " WHERE id=?",
                        new Object[] {hash, System.currentTimeMillis(), id});
        clearErrorNotice(id);
    }

    static String afterCapture(Audomo app, File f) throws Exception {
        synchronized (app.sessions) {
            org.json.JSONObject meta = SessionStore.metadata(f.getParentFile());
            String id = meta.optString("autoTarget");
            if (id.isEmpty() || meta.optBoolean("autoQueued")) return "";
            String planned = "";
            if (Profile.prefs(app).getBoolean("autoUpload", false)) {
                CloudConfig t = CloudConfig.byId(app, id);
                SessionStore.Entry e = SessionStore.entry(f.getParentFile());
                if (e == null || e.trashed() != 0) return "";
                long due = meta.optLong("uploadNotBefore", 0);
                int added = get(app).enqueueBatch(List.of(e), List.of(t), due, false);
                if (added > 0) planned = t.protocol() + "-Upload eingereiht"
                        + (due > System.currentTimeMillis() ? " · frühestens "
                                + java.time.Instant.ofEpochMilli(due).atZone(java.time.ZoneId.systemDefault())
                                        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) : "");
            }
            meta.put("autoQueued", true);
            SessionStore.storeMeta(f.getParentFile(), meta);
            return planned;
        }
    }

    void schedule(boolean replace) {
        plan(replace, false);
    }

    // The timer only wakes the queue. It never transfers audio or replaces a running upload.
    synchronized void plan(boolean replace, boolean timerFired) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        long now = System.currentTimeMillis();
        long earliest, next;
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT MIN(notBefore), MIN(CASE WHEN notBefore>? THEN notBefore"
                                        + " END) FROM jobs WHERE "
                                        + PENDING,
                                new String[] {Long.toString(now)})) {
            c.moveToFirst();
            earliest = c.isNull(0) ? Long.MAX_VALUE : c.getLong(0);
            next = c.getLong(1);
        }
        ComponentName service = new ComponentName(context, UploadWorker.class);
        if (earliest <= now && (replace || scheduler.getPendingJob(JOB) == null)) {
            NetworkRequest.Builder network =
                    new NetworkRequest.Builder()
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            if (Profile.prefs(context).getBoolean("wifiOnly", true))
                network.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
            scheduler.schedule(
                    new JobInfo.Builder(JOB, service)
                            .setRequiredNetwork(network.build())
                            .setPersisted(true)
                            .setBackoffCriteria(30000, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                            .build());
        }
        if (earliest > now && active.isEmpty() && scheduler.getPendingJob(JOB) != null)
            scheduler.cancel(JOB);
        JobInfo timer = scheduler.getPendingJob(DELAY_TIMER);
        if (next > 0) {
            if (timerFired || timer == null || timer.getExtras().getLong("due") != next) {
                android.os.PersistableBundle extras = new android.os.PersistableBundle();
                extras.putLong("due", next);
                long delay = Math.max(0, next - System.currentTimeMillis());
                scheduler.schedule(
                        new JobInfo.Builder(DELAY_TIMER, service)
                                .setPersisted(true)
                                .setMinimumLatency(delay)
                                .setOverrideDeadline(delay)
                                .setExtras(extras)
                                .build());
            }
        } else if (!timerFired && timer != null) scheduler.cancel(DELAY_TIMER);
    }

    long attentionCount() {
        return android.database.DatabaseUtils.longForQuery(getReadableDatabase(),
                "SELECT COUNT(*) FROM jobs WHERE inQueue=1 AND (state IN ('Fehler','Konflikt')"
                        + " OR availability!='')", null);
    }

    boolean pendingNow() {
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                "SELECT 1 FROM jobs WHERE " + PENDING + " AND notBefore<=? LIMIT 1",
                                new String[] {Long.toString(System.currentTimeMillis())})) {
            return c.moveToFirst();
        }
    }

    Job byId(long id) {
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(SELECT + " WHERE id=?", new String[] {Long.toString(id)})) {
            return c.moveToFirst() ? readJob(c) : null;
        }
    }

    synchronized Job nextReady(Set<Long> attempted) {
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery(
                                SELECT
                                        + " WHERE "
                                        + PENDING
                                        + " AND notBefore<=? ORDER BY priority DESC,id",
                                new String[] {Long.toString(System.currentTimeMillis())})) {
            while (c.moveToNext()) if (!attempted.contains(c.getLong(0))) return readJob(c);
        }
        return null;
    }

    synchronized boolean transferring() { return !active.isEmpty(); }

    synchronized Attempt claim(Job job) {
        if (((Audomo) context.getApplicationContext()).backupBusy) return null;
        if (active.containsKey(job.id) || !job.equals(byId(job.id))) return null;
        if (!List.of("Wartet", "Upload läuft", "Fehler").contains(job.state)
                || job.notBefore > System.currentTimeMillis()) return null;
        Attempt attempt = new Attempt(job.id);
        attempt.expectedHash = job.verified;
        active.put(job.id, attempt);
        getWritableDatabase()
                .execSQL(
                        "UPDATE jobs SET state='Upload"
                                + " läuft',attempts=attempts+1,lastAttempt=?,progress=0 WHERE id=?",
                        new Object[] {System.currentTimeMillis(), job.id});
        return attempt;
    }

    synchronized int prioritize(Collection<Long> ids) {
        SQLiteDatabase db = getWritableDatabase();
        List<Long> ordered = new ArrayList<>(new LinkedHashSet<>(ids));
        Collections.reverse(ordered);
        int changed = 0;
        db.beginTransaction();
        try (SQLiteStatement update =
                db.compileStatement(
                        "UPDATE jobs SET priority=? WHERE id=? AND inQueue=1 AND state IN"
                                + " ('Wartet','Fehler')")) {
            long rank =
                    android.database.DatabaseUtils.longForQuery(
                            db, "SELECT COALESCE(MAX(priority),0) FROM jobs", null);
            for (long id : ordered) {
                update.bindLong(1, ++rank);
                update.bindLong(2, id);
                changed += update.executeUpdateDelete();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (changed > 0) schedule(false);
        return changed;
    }

    // The caller owns the session lock. Hash large legacy files outside the queue lock.
    String legacyUploadHash(SessionStore.Entry entry) throws Exception {
        try (Cursor rows = getReadableDatabase().rawQuery(
                "SELECT 1 FROM jobs WHERE rec=? AND inQueue=1 AND action='upload'"
                        + " AND publicationHash IS NULL LIMIT 1", new String[]{entry.id()})) {
            if (!rows.moveToFirst()) return null;
        }
        return LibraryFiles.digest(new FileInputStream(entry.file()));
    }

    void discardUploadsForTrash(SessionStore.Entry entry) throws Exception {
        discardUploadsForTrash(entry.id(), legacyUploadHash(entry));
    }

    synchronized void discardUploadsForTrash(String rec, String legacyHash) {
        if (legacyHash != null)
            getWritableDatabase().execSQL("UPDATE jobs SET publicationHash=? WHERE rec=? AND publicationHash IS NULL",
                    new Object[]{legacyHash, rec});
        List<Long> ids = new ArrayList<>();
        try (Cursor rows = getReadableDatabase().rawQuery(SELECT + " WHERE rec=? AND inQueue=1",
                new String[]{rec})) {
            while (rows.moveToNext()) {
                Job job = readJob(rows);
                if (job.disposableUpload()) ids.add(job.id);
            }
        }
        if (!ids.isEmpty()) remove(ids);
    }

    void reconcileTrash(SessionStore store) {
        Set<String> candidates = new HashSet<>();
        for (Job job : listedJobs()) if (job.disposableUpload()) candidates.add(job.rec);
        for (String id : candidates) {
            synchronized (store) {
                try {
                    SessionStore.Entry entry = store.byId(id);
                    if (entry != null && entry.trashed() != 0) discardUploadsForTrash(entry);
                } catch (Exception error) {
                    ((Audomo)context).diagnostic("Warnung · Uploadauftrag im Papierkorb konnte nicht geprüft werden");
                }
            }
        }
    }

    record Removal(int removed, int finishing) {}

    Removal remove(Collection<Long> ids) {
        return remove(ids, false);
    }

    synchronized Removal remove(Collection<Long> ids, boolean finishedOnly) {
        SQLiteDatabase db = getWritableDatabase();
        List<Long> removed = new ArrayList<>();
        int finishing = 0;
        db.beginTransaction();
        try {
            for (long id : new LinkedHashSet<>(ids)) {
                Job job = byId(id);
                if (job == null || !job.inQueue || (finishedOnly && !job.finished())) continue;
                boolean mayHaveRenamed = job.action.equals("rename") && job.attempted && !job.state.equals("Konflikt");
                boolean mayHaveUploaded = job.disposableUpload() && job.publicationHash != null
                        && !job.publicationHash.isEmpty();
                if ((job.state.equals("Upload läuft") && committing(id)) || mayHaveRenamed || mayHaveUploaded) {
                    // The server may already have published the file. Any recovery must only read
                    // it.
                    String expected =
                            mayHaveUploaded ? job.publicationHash : mayHaveRenamed ? job.verified
                                    : job.action.equals("delete") ? "" : active.get(id).expectedHash;
                    db.execSQL(
                            "UPDATE jobs SET"
                                + " inQueue=0,priority=0,notBefore=0,state='Wartet',action='verifyRemoval',replacement=? WHERE"
                                + " id=?",
                            new Object[] {expected, id});
                    finishing++;
                } else {
                    forget(db, job);
                }
                removed.add(id);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        for (long id : removed) {
            Attempt attempt = active.get(id);
            if (attempt != null) {
                if (attempt.committing) attempt.finishOnly = true;
                else attempt.cancelled = true;
            }
            clearErrorNotice(id);
        }
        if (!removed.isEmpty()) schedule(false);
        return new Removal(removed.size(), finishing);
    }

    private void forget(SQLiteDatabase db, Job job) {
        if (job.knownCopy()) {
            db.execSQL(
                    "UPDATE jobs SET"
                        + " inQueue=0,priority=0,state='Hochgeladen',action='upload',path=?,origin='',replacement=NULL,publicationHash='',error='',progress=100,notBefore=0"
                        + " WHERE id=?",
                    new Object[] {job.confirmedPath(), job.id});
        } else {
            db.delete("jobs", "id=?", new String[] {Long.toString(job.id)});
        }
    }

    void finishRemoval(Job job, String destinationHash, String previousHash) throws Exception {
        SessionStore store = ((Audomo) context).sessions;
        synchronized (store) {
            synchronized (this) {
                finishRemoval(store, job, destinationHash, previousHash);
            }
        }
    }

    private void finishRemoval(SessionStore store, Job job, String destinationHash, String previousHash)
            throws Exception {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            boolean originalStillPresent =
                    !job.origin.isEmpty()
                            && !job.origin.equals(job.path)
                            && job.verified.equals(previousHash);
            if (!originalStillPresent
                    && !job.replacement.isEmpty()
                    && job.replacement.equals(destinationHash)) {
                if (!job.origin.isEmpty()) updateCloudName(store, job);
                verified(job.id, destinationHash);
            } else if (job.replacement.isEmpty() && previousHash == null) {
                db.delete("jobs", "id=?", new String[] {Long.toString(job.id)});
            } else {
                forget(db, job);
                if (job.knownCopy()) {
                    String issue =
                            previousHash == null
                                    ? "Cloudkopie fehlt"
                                    : job.verified.equals(previousHash)
                                            ? ""
                                            : "Cloudkopie geändert";
                    db.execSQL(
                            "UPDATE jobs SET availability=?,lastChecked=? WHERE id=?",
                            new Object[] {issue, System.currentTimeMillis(), job.id});
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    synchronized boolean committing(long id) {
        Attempt attempt = active.get(id);
        return attempt != null && attempt.committing;
    }

    synchronized void beforeCommit(Attempt attempt) throws IOException {
        attempt.check();
        if (attempt.finishOnly) throw new IOException("Auftrag entfernt");
        if (!attempt.committing) {
            getWritableDatabase().execSQL("UPDATE jobs SET publicationHash=? WHERE id=? AND action='upload'",
                    new Object[]{attempt.expectedHash, attempt.id});
            attempt.committing = true;
        }
    }

    synchronized void release(Attempt attempt) {
        active.remove(attempt.id, attempt);
        if (attempt.finishOnly) {
            getWritableDatabase()
                    .delete(
                            "jobs",
                            "id=? AND inQueue=0 AND state='Cloudkopie gelöscht'",
                            new String[] {Long.toString(attempt.id)});
        }
    }

    synchronized String stop(long id, boolean skip) throws IOException {
        Job job = byId(id);
        if (job == null || !job.open()) throw new IOException("Der Auftrag ist bereits beendet.");
        Attempt attempt = active.get(id);
        if (attempt != null && attempt.committing)
            throw new IOException(
                    "Das Ziel bestätigt den Abschluss. Ein Abbruch ist jetzt nicht mehr möglich.");
        if (attempt != null) attempt.cancelled = true;
        String next = skip ? "Ausgelassen" : "Abgebrochen";
        getWritableDatabase()
                .execSQL(
                        "UPDATE jobs SET state=?,error='',replacement=NULL WHERE id=?",
                        new Object[] {next, id});
        schedule(false);
        clearErrorNotice(id);
        return next;
    }

    synchronized String resume(long id) throws IOException {
        Job job = byId(id);
        if (job == null
                || !List.of("Abgebrochen", "Ausgelassen", "Wartet", "Fehler").contains(job.state))
            throw new IOException("Der Auftragsstatus hat sich geändert.");
        getWritableDatabase()
                .execSQL(
                        "UPDATE jobs SET state='Wartet',error='',notBefore=0,progress=0 WHERE id=?",
                        new Object[] {id});
        schedule(active.isEmpty());
        return "Auftrag freigegeben";
    }

    private void clearErrorNotice(long id) {
        context.getSystemService(NotificationManager.class).cancel("upload", (int) id);
    }

    synchronized void state(long id, String state, String err) {
        getWritableDatabase()
                .execSQL(
                        "UPDATE jobs SET state=?,error=? WHERE id=?",
                        new Object[] {state, err, id});
        if (state.equals("Cloudkopie gelöscht")) clearErrorNotice(id);
    }

    void sent(long id) {
        getWritableDatabase().execSQL("UPDATE jobs SET attempted=1 WHERE id=?", new Object[] {id});
    }

    void progress(long id, int value) {
        getWritableDatabase()
                .execSQL("UPDATE jobs SET progress=? WHERE id=?", new Object[] {value, id});
    }

    synchronized void resolve(Job j, int choice) throws Exception {
        if (!"Konflikt".equals(j.state) || !j.equals(byId(j.id)))
            throw new IOException("Kein offener Konflikt");
        if (choice == 2) {
            if (j.action.equals("move") || j.action.equals("rename"))
                getWritableDatabase()
                        .execSQL(
                                "UPDATE jobs SET"
                                    + " path=origin,origin='',action='upload',state='Hochgeladen',error='',progress=100"
                                    + " WHERE id=?",
                                new Object[] {j.id});
            else stop(j.id, true);
            return;
        }
        if (choice == 1 && j.action.equals("rename"))
            throw new IOException("Für die Umbenennung einen freien Namen wählen.");
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
                        "UPDATE jobs SET path=?,attempted=0,publicationHash='',state='Wartet',replacement=?,error=''"
                                + " WHERE id=? AND state='Konflikt'",
                        new Object[] {path, choice == 1 ? j.replacement : null, j.id});
        schedule(false);
    }

    void conflict(long id, String hash) {
        getWritableDatabase()
                .execSQL(
                        "UPDATE jobs SET state=CASE WHEN inQueue=0 THEN 'Wartet' ELSE 'Konflikt'"
                                + " END,replacement=CASE WHEN inQueue=0 THEN replacement ELSE ?"
                                + " END,error='Namenskonflikt' WHERE id=?",
                        new Object[] {hash, id});
    }

    synchronized void accessRenewed(String target) {
        ContentValues ready = new ContentValues();
        ready.put("state", "Wartet");
        ready.put("error", "");
        int changed = getWritableDatabase().update("jobs", ready,
                "target=? AND inQueue=1 AND state='Fehler'", new String[]{target});
        if (changed == 0) return;
        if (active.isEmpty()) schedule(true);
        else restartAfterAccessChange = true;
    }

    synchronized void applyAccessChangeAfterBatch() {
        if (!restartAfterAccessChange) return;
        restartAfterAccessChange = false;
        schedule(true);
    }

    synchronized int retryErrors(Collection<Long> ids) {
        SQLiteDatabase db = getWritableDatabase();
        int count = 0;
        db.beginTransaction();
        try (SQLiteStatement update = db.compileStatement("UPDATE jobs SET state='Wartet',error='',"
                + "notBefore=0,progress=0 WHERE id=? AND inQueue=1 AND state='Fehler'")) {
            for (long id : ids) {
                update.bindLong(1, id);
                count += update.executeUpdateDelete();
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (count > 0) schedule(active.isEmpty());
        return count;
    }

    synchronized void retry(long id) {
        getWritableDatabase()
                .execSQL(
                        "UPDATE jobs SET state='Wartet',error='' WHERE id=? AND state='Fehler'",
                        new Object[] {id});
        schedule(active.isEmpty());
    }

    boolean pending() {
        try (Cursor c =
                getReadableDatabase()
                        .rawQuery("SELECT 1 FROM jobs WHERE " + PENDING + " LIMIT 1", null)) {
            return c.moveToFirst();
        }
    }

    void reconcile() {
        if (pending()) schedule(false);
    }
}
