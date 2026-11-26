package de.audomo;

import static org.junit.Assert.*;

import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;

import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Exercise the ordinary recording service and OS scheduler without forcing a job or tapping retry. */
public class AutomaticUploadDeviceTest {
    private static void await(String step, long seconds, BooleanSupplier reached) {
        long end = SystemClock.elapsedRealtime() + seconds * 1000;
        while (!reached.getAsBoolean() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(100);
        assertTrue(step, reached.getAsBoolean());
    }

    @Test
    public void trashBeforeDeadlineRemovesUploadWithoutNotificationOrRemoteFile() throws Exception {
        var inst = InstrumentationRegistry.getInstrumentation();
        Assume.assumeTrue(android.os.Build.MODEL.contains("sdk_gphone")
                && "true".equals(InstrumentationRegistry.getArguments().getString("localServers"))
                && "true".equals(InstrumentationRegistry.getArguments().getString("qaRecorder")));
        Audomo app = (Audomo)inst.getTargetContext().getApplicationContext();
        JSONObject fixture = new JSONObject(new String(Files.readAllBytes(
                new File(app.getCacheDir(), "qa-protocols.json").toPath()), java.nio.charset.StandardCharsets.UTF_8));
        CloudConfig target = new CloudConfig(UUID.randomUUID().toString(), "FTP", fixture.getString("host"),
                fixture.getJSONObject("ports").getInt("FTP"), "qa", "/", "");
        UploadQueue queue = UploadQueue.get(app);
        SharedPreferences p = Profile.prefs(app);
        String targets = p.getString("targets", "[]"), standard = p.getString("defaultTarget", "");
        String delay = p.getString(UploadDelay.KEY, "0");
        boolean auto = p.getBoolean("autoUpload", false);
        MainActivity activity = (MainActivity)inst.startActivitySync(
                new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            await("Aufnahmebereit", 12, () -> app.ready() && activity.hasWindowFocus());
            target.save(app, fixture.getString("password"));
            p.edit().putString("defaultTarget", target.id()).putBoolean("autoUpload", true)
                    .putString(UploadDelay.KEY, "0.15").commit();
            inst.runOnMainSync(() -> app.command("START", "Papierkorb-" + UUID.randomUUID()));
            await("Aufnahme gestartet", 12, () -> app.state == Audomo.State.RECORDING);
            SystemClock.sleep(450);
            inst.runOnMainSync(() -> app.command("STOP", null));
            await("Lokal gespeichert", 12, () -> app.state == Audomo.State.SAVED);
            UploadQueue.Job job = queue.listedJobs().stream().filter(j -> j.target().equals(target.id()))
                    .findFirst().orElseThrow();
            SessionStore.Entry recording = app.sessions.byId(job.rec());
            LibraryFiles files = new LibraryFiles(app.sessions);
            inst.runOnMainSync(() -> {
                activity.openLibrary();
                new LibraryActions(activity, files).trash(java.util.List.of(recording), false);
            });
            inst.runOnMainSync(() -> {
                for (android.view.View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    android.widget.Button confirm = window.findViewById(android.R.id.button1);
                    if (confirm != null && "In den Papierkorb".contentEquals(confirm.getText())) {
                        confirm.performClick();
                        return;
                    }
                }
                fail("Papierkorb-Bestätigung fehlt");
            });
            await("Auftrag vor Fälligkeit entfernt", 5, () -> queue.byId(job.id()) == null);
            assertTrue(System.currentTimeMillis() < job.notBefore());
            SystemClock.sleep(job.notBefore() - System.currentTimeMillis() + 1200);
            assertNull(queue.byId(job.id()));
            assertTrue(app.sessions.byId(job.rec()).trashed() > 0);
            var notices = app.getSystemService(NotificationManager.class).getActiveNotifications();
            assertFalse(Arrays.stream(notices).anyMatch(n -> n.getId() == job.id() && "upload".equals(n.getTag())));
            try (RemoteFiles remote = RemoteFiles.open(target, fixture.getString("password"),
                    CloudLibrary.network(app), ignored -> {})) {
                assertNull(remote.hash(job.path()));
            }
            files.change(app.sessions.byId(job.rec()), null, recording.folder(), false);
            assertEquals("", UploadQueue.afterCapture(app, recording.file()));
            assertNull(queue.job(job.rec()));
        } finally {
            p.edit().putString("targets", targets).putString("defaultTarget", standard)
                    .putBoolean("autoUpload", auto).putString(UploadDelay.KEY, delay).commit();
            queue.getWritableDatabase().delete("jobs", "target=?", new String[]{target.id()});
            app.getSharedPreferences("credentials", 0).edit().remove(target.id()).commit();
            inst.runOnMainSync(activity::finish);
        }
    }

    @Test
    public void scheduledRecordingNotifiesOnFailureAndRecoversAutomatically() throws Exception {
        var inst = InstrumentationRegistry.getInstrumentation();
        Assume.assumeTrue(android.os.Build.MODEL.contains("sdk_gphone")
                && "true".equals(InstrumentationRegistry.getArguments().getString("localServers"))
                && "true".equals(InstrumentationRegistry.getArguments().getString("qaRecorder")));
        Audomo app = (Audomo) inst.getTargetContext().getApplicationContext();
        JSONObject fixture = new JSONObject(new String(
                Files.readAllBytes(new File(app.getCacheDir(), "qa-protocols.json").toPath()),
                java.nio.charset.StandardCharsets.UTF_8));
        CloudConfig target = new CloudConfig(UUID.randomUUID().toString(), "FTP", fixture.getString("host"),
                fixture.getJSONObject("ports").getInt("FTP"), "qa", "/", "");
        UploadQueue queue = UploadQueue.get(app);
        SharedPreferences p = Profile.prefs(app);
        String targets = p.getString("targets", "[]"), standard = p.getString("defaultTarget", "");
        String delay = p.getString(UploadDelay.KEY, "0");
        boolean auto = p.getBoolean("autoUpload", false), cleanup = p.getBoolean("cleanupUploaded", false);
        var nm = app.getSystemService(NotificationManager.class);
        MainActivity activity = (MainActivity) inst.startActivitySync(
                new Intent(app, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await("Aufnahmebereit", 12, () -> app.ready() && activity.hasWindowFocus());
        try {
            target.save(app, "qa-only-wrong-password");
            p.edit().putString("defaultTarget", target.id()).putBoolean("autoUpload", true)
                    .putBoolean("cleanupUploaded", false).putString(UploadDelay.KEY, "0.12").commit();
            inst.runOnMainSync(() -> app.command("START", "Automatik-" + UUID.randomUUID()));
            await("Aufnahme gestartet", 12, () -> app.state == Audomo.State.RECORDING);
            SystemClock.sleep(500);
            inst.runOnMainSync(() -> app.command("STOP", null));
            await("Lokal gespeichert", 12, () -> app.state == Audomo.State.SAVED);
            UploadQueue.Job planned = queue.listedJobs().stream()
                    .filter(job -> job.target().equals(target.id())).findFirst().orElseThrow();
            assertEquals("Wartet", planned.state());
            assertTrue(planned.notBefore() > System.currentTimeMillis());
            assertTrue(app.note.contains("FTP-Upload eingereiht"));
            await("Fehler nach Ablauf der Wartezeit", 100, () -> queue.byId(planned.id()).state().equals("Fehler"));
            assertTrue(queue.attentionCount() > 0);
            await("Fehlermeldung auf Android", 5, () -> Arrays.stream(nm.getActiveNotifications())
                    .anyMatch(n -> n.getId() == planned.id() && "upload".equals(n.getTag())
                            && n.getNotification().extras.getCharSequence("android.bigText", "").length() > 0));
            target.save(app, fixture.getString("password"));
            await("Automatischer Wiederanlauf", 150, () -> queue.byId(planned.id()).state().equals("Hochgeladen"));
            UploadQueue.Job done = queue.byId(planned.id());
            assertTrue(done.attempts() >= 2);
            assertEquals(LibraryFiles.digest(new FileInputStream(app.sessions.byId(done.rec()).file())), done.verified());
            await("Fehlerbenachrichtigung aufgehoben", 5, () -> Arrays.stream(nm.getActiveNotifications())
                    .noneMatch(n -> n.getId() == planned.id() && "upload".equals(n.getTag())));
        } finally {
            if (app.active()) inst.runOnMainSync(() -> app.command("STOP", null));
            p.edit().putString("targets", targets).putString("defaultTarget", standard).putBoolean("autoUpload", auto)
                    .putBoolean("cleanupUploaded", cleanup).putString(UploadDelay.KEY, delay).commit();
            queue.getWritableDatabase().delete("jobs", "target=?", new String[]{target.id()});
            app.getSharedPreferences("credentials", 0).edit().remove(target.id()).commit();
            inst.runOnMainSync(activity::finish);
        }
    }
}
