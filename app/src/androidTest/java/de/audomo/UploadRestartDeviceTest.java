package de.audomo;

import static org.junit.Assert.*;

import android.app.job.*;
import android.content.*;
import android.net.*;

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
public class UploadRestartDeviceTest {
    @Test
    public void autoUploadAcrossEmulatorRestart() throws Exception {
        var inst = InstrumentationRegistry.getInstrumentation();
        String stage = InstrumentationRegistry.getArguments().getString("restartStage", "");
        Assume.assumeTrue(
                android.os.Build.MODEL.contains("sdk_gphone")
                        && List.of("prepare", "verify").contains(stage));
        Context c = inst.getTargetContext();
        Audomo app = (Audomo) c.getApplicationContext();
        UploadQueue queue = UploadQueue.get(c);
        SharedPreferences prefs = Profile.prefs(c);
        JSONObject setup =
                new JSONObject(
                        new String(
                                Files.readAllBytes(
                                        new File(c.getCacheDir(), "qa-protocols.json").toPath()),
                                StandardCharsets.UTF_8));
        Path state = new File(c.getCacheDir(), "qa-upload-restart.json").toPath();
        if (stage.equals("prepare")) {
            assertFalse(Files.exists(state));
            assertTrue(CloudConfig.all(c).isEmpty());
            assertTrue(queue.jobs().isEmpty());
            ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
            Network active = cm.getActiveNetwork();
            assertTrue(
                    active == null
                            || !cm.getNetworkCapabilities(active)
                                    .hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
            String id = UUID.randomUUID().toString();
            CloudConfig target =
                    new CloudConfig(
                            UUID.randomUUID().toString(),
                            "FTP",
                            "10.0.2.2",
                            setup.getJSONObject("ports").getInt("FTP"),
                            "qa",
                            "/",
                            "");
            JSONObject restore =
                    new JSONObject()
                            .put("id", id)
                            .put("target", target.id())
                            .put("auto", prefs.getBoolean("autoUpload", false))
                            .put("wifi", prefs.getBoolean("wifiOnly", true))
                            .put("cleanup", prefs.getBoolean("cleanupUploaded", false))
                            .put("default", prefs.getString("defaultTarget", ""));
            Files.write(state, restore.toString().getBytes(StandardCharsets.UTF_8));
            target.save(c, setup.getString("password"));
            prefs.edit()
                    .putBoolean("wifiOnly", true)
                    .putBoolean("autoUpload", true)
                    .putBoolean("cleanupUploaded", false)
                    .commit();
            File dir = new File(app.sessions.root, id);
            Files.createDirectories(dir.toPath());
            File f = new File(dir, "QA-Neustart-" + id + ".ogg");
            try (InputStream in = inst.getContext().getAssets().open("testsignal.ogg")) {
                Files.copy(in, f.toPath());
            }
            SessionStore.storeMeta(
                    dir,
                    new JSONObject()
                            .put("name", f.getName())
                            .put("frames", 48317)
                            .put("start", OffsetDateTime.now().toString())
                            .put("autoTarget", target.id()));
            UploadQueue.afterCapture(app, f);
            UploadQueue.afterCapture(app, f);
            assertEquals(1, queue.jobs().size());
            assertEquals("Wartet", queue.job(id).state());
            JobInfo job = c.getSystemService(JobScheduler.class).getPendingJob(UploadQueue.JOB);
            assertNotNull(job);
            assertTrue(job.isPersisted());
            assertTrue(job.getRequiredNetwork().hasTransport(NetworkCapabilities.TRANSPORT_WIFI));
        } else {
            JSONObject restore =
                    new JSONObject(new String(Files.readAllBytes(state), StandardCharsets.UTF_8));
            String id = restore.getString("id");
            File dir = new File(app.sessions.root, id);
            CloudConfig target = CloudConfig.byId(c, restore.getString("target"));
            try {
                new UploadQueueDeviceTest().drive(queue, id, "Hochgeladen");
                assertEquals(1, queue.jobs().size());
                SessionStore.Entry e = SessionStore.entry(dir);
                String local = LibraryFiles.digest(new FileInputStream(e.file()));
                assertEquals(local, queue.job(id).verified());
                try (RemoteFiles ftp =
                        RemoteFiles.open(
                                target, CloudConfig.secret(c, target.id()), null, n -> {})) {
                    String path = queue.job(id).path();
                    assertEquals(local, ftp.hash(path));
                    ftp.deleteIfMatches(path, local);
                }
            } finally {
                queue.getWritableDatabase().delete("jobs", "rec=?", new String[] {id});
                c.getSharedPreferences("credentials", 0).edit().remove(target.id()).commit();
                prefs.edit()
                        .putString("targets", "[]")
                        .putString("defaultTarget", restore.getString("default"))
                        .putBoolean("autoUpload", restore.getBoolean("auto"))
                        .putBoolean("wifiOnly", restore.getBoolean("wifi"))
                        .putBoolean("cleanupUploaded", restore.getBoolean("cleanup"))
                        .commit();
                try (var files = Files.walk(dir.toPath())) {
                    for (Path p :
                            files.sorted(Comparator.reverseOrder())
                                    .collect(java.util.stream.Collectors.toList()))
                        Files.deleteIfExists(p);
                }
                Files.deleteIfExists(state);
            }
        }
    }
}
