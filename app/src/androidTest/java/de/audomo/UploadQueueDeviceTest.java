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
                auto = prefs.getBoolean("cleanupUploaded", false);
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
                queue.enqueue(e, target);
                drive(queue, id, "Konflikt");
                assertEquals("Konflikt", queue.job(id).state());
                assertTrue(file.isFile());
                queue.resolve(queue.job(id), 2);
                assertEquals("Ausgelassen", queue.job(id).state());
                assertTrue(file.isFile());
                queue.enqueue(e, target);
                drive(queue, id, "Konflikt");
                queue.resolve(queue.job(id), 0);
                drive(queue, id, "Hochgeladen");
                UploadQueue.Job verified = queue.job(id);
                String local = LibraryFiles.digest(new FileInputStream(file));
                assertEquals(local, ftp.hash(verified.path()));
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

    void drive(UploadQueue q, String id, String state) throws Exception {
        long until = SystemClock.elapsedRealtime() + 45000;
        while (SystemClock.elapsedRealtime() < until) {
            if (state.equals(q.job(id).state())) return;
            if (q.job(id).state().equals("Fehler")
                    && !q.job(id).error().equals("Netzverbindung unterbrochen"))
                fail(q.job(id).error());
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
        fail("Jobstatus " + q.job(id).state() + " statt " + state);
    }
}
