package de.audomo;

import static org.junit.Assert.*;

import android.content.*;
import android.net.*;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;

@RunWith(AndroidJUnit4.class)
public class UploadQueueDeviceTest {
    @Test
    public void verifiedUploadConflictRetryCloudMoveAndTrash() throws Exception {
        var inst = InstrumentationRegistry.getInstrumentation();
        Context c = inst.getTargetContext();
        Assume.assumeTrue(
                android.os.Build.MODEL.contains("sdk_gphone")
                        && "true"
                                .equals(
                                        InstrumentationRegistry.getArguments()
                                                .getString("localServers")));
        JSONObject setup =
                new JSONObject(
                        new String(
                                Files.readAllBytes(
                                        new File(c.getCacheDir(), "qa-protocols.json").toPath()),
                                StandardCharsets.UTF_8));
        Audomo app = (Audomo) c.getApplicationContext();
        UploadQueue queue = UploadQueue.get(c);
        SharedPreferences prefs = Profile.prefs(c);
        String original = prefs.getString("targets", "[]"),
                standard = prefs.getString("defaultTarget", "");
        boolean wifi = prefs.getBoolean("wifiOnly", true),
                auto = prefs.getBoolean("cleanupUploaded", false),
                autoUpload = prefs.getBoolean("autoUpload", false);
        String id = UUID.randomUUID().toString();
        String path = "qa-" + UUID.randomUUID() + ".ogg";
        CloudConfig target =
                new CloudConfig(
                        UUID.randomUUID().toString(),
                        "FTP",
                        setup.optString("host", "10.0.2.2"),
                        setup.getJSONObject("ports").getInt("FTP"),
                        "qa",
                        "/",
                        "");
        File dir = new File(app.sessions.root, id);
        Files.createDirectories(dir.toPath());
        File file = new File(dir, path);
        try (InputStream in = inst.getContext().getAssets().open("testsignal.ogg")) {
            Files.copy(in, file.toPath());
        }
        SessionStore.storeMeta(
                dir,
                new JSONObject()
                        .put("start", OffsetDateTime.now().toString())
                        .put("frames", 48317)
                        .put("name", path));
        try {
            prefs.edit()
                    .putBoolean("wifiOnly", false)
                    .putBoolean("cleanupUploaded", false)
                    .commit();
            target.save(c, setup.getString("password"));
            SessionStore.Entry e = SessionStore.entry(dir);
            Network network = c.getSystemService(ConnectivityManager.class).getActiveNetwork();
            assertNotNull(network);
            try (RemoteFiles ftp =
                    RemoteFiles.open(target, setup.getString("password"), network, n -> {})) {
                File foreign = File.createTempFile("qa-transfer-", ".bin", c.getCacheDir());
                try {
                    Files.write(foreign.toPath(), new byte[] {1, 2, 3});
                    ftp.put(foreign, "/" + path, false, null);
                } finally {
                    foreign.delete();
                }
                assertThrows(IOException.class, () -> queue.enqueueSelected(
                        app.sessions, List.of(id, UUID.randomUUID().toString()), target));
                assertNull(queue.job(id));
                long due = System.currentTimeMillis() + 300_000;
                prefs.edit().putBoolean("autoUpload", true).commit();
                SessionStore.storeMeta(dir, SessionStore.metadata(dir)
                        .put("autoTarget", target.id()).put("uploadNotBefore", due));
                UploadQueue.afterCapture(app, file);
                long originalJob = queue.job(id).id();
                UploadQueue.afterCapture(app, file);
                assertEquals(originalJob, queue.job(id).id());
                assertEquals(due, queue.job(id).notBefore());
                LibraryFiles naming = new LibraryFiles(app.sessions);
                naming.rename(e, "Später", false, List.of(queue.job(id)));
                assertEquals("/Später.ogg", queue.job(id).path());
                assertEquals(due, queue.job(id).notBefore());
                assertEquals(path, app.sessions.byId(id).name());
                naming.rename(e, path, false, List.of(queue.job(id)));
                assertEquals(1, queue.enqueueSelected(app.sessions, List.of(id, id), target));
                assertEquals(0, queue.job(id).notBefore());
                assertEquals(originalJob, queue.job(id).id());
                assertEquals(0, queue.enqueueSelected(app.sessions, List.of(id), target));
                drive(queue, id, "Konflikt");
                assertEquals("Konflikt", queue.job(id).state());
                assertTrue(file.isFile());
                queue.resolve(queue.job(id), 2);
                assertEquals("Ausgelassen", queue.job(id).state());
                assertTrue(file.isFile());
                queue.resume(queue.job(id).id());
                drive(queue, id, "Konflikt");
                assertNotEquals(LibraryFiles.digest(new FileInputStream(file)), ftp.hash("/" + path));
                queue.resolve(queue.job(id), 2);
                queue.enqueue(e, target);
                drive(queue, id, "Konflikt");
                queue.resolve(queue.job(id), 0);
                drive(queue, id, "Hochgeladen");
                UploadQueue.Job verified = queue.job(id);
                String local = LibraryFiles.digest(new FileInputStream(file));
                assertEquals(local, ftp.hash(verified.path()));
                checkRemovedPublication(c, queue, network, target, verified.path(), local, true);
                String absent = "/absent-" + id + ".ogg";
                checkRemovedPublication(c, queue, network, target, absent, local, false);
                assertNull(ftp.hash(absent));
                assertTrue(verified.attempts() >= 3);
                assertTrue(verified.lastAttempt() > 0 && verified.lastChecked() > 0);
                assertTrue(CloudReconcile.verify(app.sessions, verified).contains("stimmt überein"));
                assertFalse(local.equals(ftp.hash("/" + path)));
                LibraryFiles lib = new LibraryFiles(app.sessions);
                lib.change(e, "Umbenannt-" + id, "", false);
                e = SessionStore.entry(dir);
                queue.followLocalChange(e, false);
                drive(queue, id, "Hochgeladen");
                String moved = queue.job(id).path();
                assertNotEquals(verified.path(), moved);
                assertNull(ftp.hash(verified.path()));
                assertEquals(local, ftp.hash(moved));
                lib.change(e, null, "", true);
                queue.followLocalChange(SessionStore.entry(dir), true);
                drive(queue, id, "Cloudkopie gelöscht");
                assertNull(ftp.hash(moved));
                assertTrue(file.isFile());
                assertTrue(SessionStore.entry(dir).trashed() > 0);
                lib.change(
                        SessionStore.entry(dir), path.substring(0, path.length() - 4), "", false);
                e = SessionStore.entry(dir);
                prefs.edit().putBoolean("cleanupUploaded", true).commit();
                queue.enqueue(e, target);
                drive(queue, id, "Konflikt");
                assertEquals(0, SessionStore.entry(dir).trashed());
                queue.resolve(queue.job(id), 1);
                drive(queue, id, "Hochgeladen");
                long limit = SystemClock.elapsedRealtime() + 5000;
                while (SessionStore.entry(dir).trashed() == 0
                        && SystemClock.elapsedRealtime() < limit) SystemClock.sleep(60);
                assertTrue(SessionStore.entry(dir).trashed() > 0);
                assertEquals(local, ftp.hash("/" + path));
                assertTrue(file.isFile());
                ftp.deleteIfMatches("/" + path, local);
            }
        } finally {
            queue.getWritableDatabase().delete("jobs", "rec=?", new String[] {id});
            prefs.edit()
                    .putString("targets", original)
                    .putString("defaultTarget", standard)
                    .putBoolean("wifiOnly", wifi)
                    .putBoolean("cleanupUploaded", auto)
                    .putBoolean("autoUpload", autoUpload)
                    .commit();
            c.getSharedPreferences("credentials", 0).edit().remove(target.id()).commit();
            try (var files = Files.walk(dir.toPath())) {
                for (Path p :
                        files.sorted(Comparator.reverseOrder())
                                .collect(java.util.stream.Collectors.toList()))
                    Files.deleteIfExists(p);
            }
        }
    }

    @Test
    public void trashRetiresEachTargetAccordingToItsPublicationState() throws Exception {
        var inst = InstrumentationRegistry.getInstrumentation();
        Audomo app = (Audomo)inst.getTargetContext().getApplicationContext();
        Assume.assumeTrue(android.os.Build.MODEL.contains("sdk_gphone"));
        UploadQueue queue = UploadQueue.get(app);
        LibraryFiles files = new LibraryFiles(app.sessions);
        String rec = UUID.randomUUID().toString();
        File dir = new File(app.sessions.root, rec);
        Files.createDirectories(dir.toPath());
        File source = new File(dir, "Besprechung.ogg");
        try (InputStream in = inst.getContext().getAssets().open("testsignal.ogg")) {
            Files.copy(in, source.toPath());
        }
        SessionStore.storeMeta(dir, new JSONObject().put("name", source.getName())
                .put("frames", 48317).put("start", OffsetDateTime.now().toString()));
        String hash = LibraryFiles.digest(new FileInputStream(source));
        var db = queue.getWritableDatabase();
        synchronized (app.sessions) {
            synchronized (queue) {
                try {
                    long[] ids = new long[5];
                    for (int i = 0; i < ids.length; i++) {
                        db.execSQL("INSERT INTO jobs(rec,target,path,state) VALUES(?,?,?,'Wartet')",
                                new Object[]{rec, "Ziel-" + i, "/Besprechung.ogg"});
                        ids[i] = queue.job(rec).id();
                    }
                    queue.verified(ids[0], hash);
                    var streaming = queue.claim(queue.byId(ids[1]));
                    queue.sent(ids[1]);
                    var publishing = queue.claim(queue.byId(ids[2]));
                    publishing.expectedHash = hash;
                    queue.beforeCommit(publishing);
                    var interrupted = queue.claim(queue.byId(ids[3]));
                    interrupted.expectedHash = hash;
                    queue.beforeCommit(interrupted);
                    queue.release(interrupted); // Lost server response, no live attempt left.
                    queue.state(ids[3], "Fehler", "Zeitüberschreitung");
                    queue.state(ids[4], "Konflikt", "Namenskonflikt");
                    files.change(app.sessions.byId(rec), null, "", true);
                    assertThrows(IOException.class, streaming::check);
                    queue.release(streaming);
                    queue.release(publishing);
                    assertNull(queue.byId(ids[1]));
                    assertNull(queue.byId(ids[4]));
                    assertEquals("Hochgeladen", queue.byId(ids[0]).state());
                    assertEquals(1, queue.listedJobs().stream().filter(j -> rec.equals(j.rec())).count());
                    for (int i : new int[]{2, 3}) {
                        UploadQueue.Job check = queue.byId(ids[i]);
                        assertEquals("verifyRemoval", check.action());
                        assertFalse(check.inQueue());
                        assertEquals(hash, check.replacement());
                        queue.finishRemoval(check, i == 2 ? hash : null, null);
                    }
                    assertTrue(queue.byId(ids[2]).knownCopy());
                    assertNull(queue.byId(ids[3]));
                    files.purge(app.sessions.byId(rec));
                    assertTrue(queue.byId(ids[0]).knownCopy());
                    assertTrue(queue.byId(ids[2]).knownCopy());
                    assertNull(app.sessions.byId(rec));
                } finally {
                    db.delete("jobs", "rec=?", new String[]{rec});
                    db.delete("recordings", "rec=?", new String[]{rec});
                    if (dir.exists()) try (var entries = Files.walk(dir.toPath())) {
                        for (Path file : entries.sorted(Comparator.reverseOrder()).collect(java.util.stream.Collectors.toList())) Files.deleteIfExists(file);
                    }
                }
            }
        }
    }

    @Test
    public void cancelAndSkipArePersistentAndRespectRemoteCommit() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue(android.os.Build.MODEL.contains("sdk_gphone"));
        UploadQueue q = UploadQueue.get(c);
        String rec = UUID.randomUUID().toString();
        synchronized (q) {
            q.getWritableDatabase().execSQL("INSERT INTO jobs(rec,target,path,state) VALUES(?,?,?,?)",
                    new Object[]{rec, "test", "/cancel.ogg", "Wartet"});
            long id = q.job(rec).id();
            try {
                UploadQueue.Attempt first = q.claim(q.byId(id));
                assertNotNull(first);
                assertEquals("Abgebrochen", q.stop(id, false));
                assertThrows(IOException.class, first::check);
                assertThrows(IOException.class, () -> q.beforeCommit(first));
                q.release(first);
                assertNull(q.claim(q.byId(id)));
                q.resume(id);
                q.stop(id, true);
                assertEquals("Ausgelassen", q.byId(id).state());
                assertNull(q.claim(q.byId(id)));
                q.resume(id);
                UploadQueue.Attempt second = q.claim(q.byId(id));
                assertNotNull(second);
                q.beforeCommit(second);
                assertThrows(IOException.class, () -> q.stop(id, false));
                assertEquals("Upload läuft", q.byId(id).state());
                q.verified(id, "synthetic");
                q.release(second);
                assertThrows(IOException.class, () -> q.resume(id));
                assertEquals(2, q.byId(id).attempts());
            } finally {
                q.getWritableDatabase().delete("jobs", "id=?", new String[]{Long.toString(id)});
            }
        }
    }

    private void checkRemovedPublication(Context c, UploadQueue q, Network net, CloudConfig target,
            String path, String expected, boolean published) throws Exception {
        String rec = UUID.randomUUID().toString();
        synchronized (q) {
            var db = q.getWritableDatabase();
            db.execSQL("INSERT INTO jobs(rec,target,path,state) VALUES(?,?,?,'Wartet')",
                    new Object[]{rec, target.id(), path});
            long id = q.job(rec).id();
            try {
                var publishing = q.claim(q.byId(id));
                publishing.expectedHash = expected;
                q.beforeCommit(publishing);
                q.remove(List.of(id));
                q.release(publishing);
                var saved = q.byId(id);
                assertEquals("verifyRemoval", saved.action());
                var resumed = q.claim(saved);
                try {
                    UploadWorker.transfer(c, q, saved, net, new java.util.concurrent.atomic.AtomicBoolean(true), resumed);
                } finally {
                    q.release(resumed);
                }
                if (published) {
                    assertEquals(expected, q.byId(id).verified());
                    assertFalse(q.byId(id).inQueue());
                } else assertNull(q.byId(id));
            } finally {
                db.delete("jobs", "rec=?", new String[]{rec});
            }
        }
    }

    @Test
    public void removeAndPromotePreserveDeadlinesCatalogAndStableIds() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Assume.assumeTrue(android.os.Build.MODEL.contains("sdk_gphone"));
        UploadQueue q = UploadQueue.get(c);
        String target = UUID.randomUUID().toString();
        synchronized (q) {
            var db = q.getWritableDatabase();
            Set<Long> ignore = new HashSet<>();
            for (UploadQueue.Job j : q.jobs()) ignore.add(j.id());
            try {
                long[] ids = new long[5];
                for (int i = 0; i < ids.length; i++) {
                    String rec = UUID.randomUUID().toString();
                    db.execSQL("INSERT INTO jobs(rec,target,path,state) VALUES(?,?,?,'Wartet')",
                            new Object[]{rec, target, "/test-" + i + ".ogg"});
                    ids[i] = q.job(rec).id();
                }
                long future = System.currentTimeMillis() + 300_000;
                db.execSQL("UPDATE jobs SET notBefore=? WHERE id=?", new Object[]{future, ids[2]});
                q.verified(ids[3], "confirmed");
                assertEquals(3, q.prioritize(List.of(ids[2], ids[4], ids[1], ids[3], ids[4])));
                assertEquals(ids[4], q.nextReady(ignore).id());
                assertEquals(future, q.byId(ids[2]).notBefore());
                UploadQueue.Attempt active = q.claim(q.byId(ids[4]));
                assertNotNull(active);
                assertEquals(0, q.prioritize(List.of(ids[4])));
                assertEquals(3, q.remove(List.of(ids[4],ids[2],ids[3],ids[4])).removed());
                assertThrows(IOException.class, active::check);
                q.release(active);
                assertNull(q.byId(ids[4]));
                assertTrue(q.byId(ids[3]).knownCopy());
                assertFalse(q.byId(ids[3]).inQueue());
                assertEquals(ids[1], q.nextReady(ignore).id());
                assertEquals(0, q.remove(List.of(ids[3])).removed());

                UploadQueue.Job first = q.byId(ids[1]);
                UploadQueue.Attempt confirming = q.claim(first);
                confirming.expectedHash = "uploaded";
                q.beforeCommit(confirming);
                assertEquals(1, q.remove(List.of(first.id())).finishing());
                assertFalse(q.byId(first.id()).inQueue());
                assertThrows(IOException.class, () -> q.beforeCommit(confirming));
                q.release(confirming);
                UploadQueue.Job check = q.byId(first.id());
                assertEquals("verifyRemoval", check.action());
                q.finishRemoval(check, "uploaded", null);
                assertEquals("uploaded", q.byId(first.id()).verified());
                assertFalse(q.listedJobs().stream().anyMatch(j -> j.id() == first.id()));
                db.execSQL("INSERT INTO jobs(rec,target,path,state) VALUES(?,?,?,'Wartet')",
                        new Object[]{first.rec() + "-new", target, "/new.ogg"});
                assertTrue(q.job(first.rec() + "-new").id() > ids[4]);
            } finally {
                db.delete("jobs", "target=?", new String[]{target});
            }
        }
    }

    void drive(UploadQueue q, String id, String state) throws Exception {
        driveJob(q, q.job(id).id(), state);
    }

    void driveJob(UploadQueue q, long jobId, String state) throws Exception {
        long until = SystemClock.elapsedRealtime() + 45000;
        while (SystemClock.elapsedRealtime() < until) {
            if (state.equals(q.byId(jobId).state())) return;
            if (q.byId(jobId).state().equals("Fehler")
                    && !q.byId(jobId).error().equals("Netzverbindung unterbrochen"))
                fail(q.byId(jobId).error());
            // Leave transient failures in the persistent queue for the scheduled retry.
            // In doze/batching, ask the test emulator to run the already scheduled job.
            try (var fd =
                    InstrumentationRegistry.getInstrumentation()
                            .getUiAutomation()
                            .executeShellCommand("cmd jobscheduler run -s de.audomo 74")) {
                new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).readAllBytes();
            }
            SystemClock.sleep(500);
        }
        fail("Jobstatus " + q.byId(jobId).state() + " statt " + state);
    }
}
