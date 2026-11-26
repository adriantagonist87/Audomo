package de.audomo;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.os.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.*;
import org.junit.runner.RunWith;

import java.util.*;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public class CaptureControlDeviceTest {
    void await(BooleanSupplier condition) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 9000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline)
            Thread.sleep(40);
        assertTrue(condition.getAsBoolean());
    }

    @Test
    public void simultaneousActionsAndQuietRuleRoundTrip() throws Exception {
        var inst = InstrumentationRegistry.getInstrumentation();
        Context c = inst.getTargetContext();
        Assume.assumeTrue(
                "Nur am Wegwerf-AVD",
                android.os.Build.MODEL.contains("sdk_gphone")
                        && "true"
                                .equals(
                                        InstrumentationRegistry.getArguments()
                                                .getString("qaRecorder")));
        Audomo a = (Audomo) c.getApplicationContext();
        MainActivity activity =
                (MainActivity)
                        inst.startActivitySync(
                                new Intent(c, MainActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        await(() -> a.ready() && activity.hasWindowFocus());
        int before = a.sessions.list().size();
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        int filter = manager.getCurrentInterruptionFilter();
        boolean allowed = manager.isNotificationPolicyAccessGranted();
        try {
            inst.runOnMainSync(() -> {
                a.command("START", "QA-Befehle");
                a.command("START", "QA-Befehle");
            });
            await(() -> a.state == Audomo.State.RECORDING);
            await(() -> brandedNotification(manager, c, "die App ist aktiv und verrichtet ihren Dienst"));
            if (allowed)
                await(() ->
                        manager.getCurrentInterruptionFilter()
                                == NotificationManager.INTERRUPTION_FILTER_PRIORITY);
            else assertTrue(a.systemNote.contains("Zugriff fehlt"));
            for (int i = 0; i < 2; i++) {
                inst.runOnMainSync(() -> {
                    a.command("PAUSE", null);
                    a.command("PAUSE", null);
                });
                await(() -> a.state == Audomo.State.PAUSED);
                await(() -> brandedNotification(manager, c, "Aufnahme pausiert"));
                long saved = a.frames;
                Thread.sleep(300);
                assertEquals(saved, a.frames);
                inst.runOnMainSync(() -> {
                    a.command("RESUME", null);
                    a.command("RESUME", null);
                });
                await(() -> a.state == Audomo.State.RECORDING);
            }
            inst.runOnMainSync(() -> {
                a.command("STOP", null);
                a.command("STOP", null);
            });
            await(() -> a.state == Audomo.State.SAVED);
            assertEquals(before + 1, a.sessions.list().size());
            assertEquals(
                    0,
                    Arrays.stream(a.sessions.directories())
                            .filter(f -> new java.io.File(f, "capture.part").exists())
                            .count());
            if (allowed) await(() -> manager.getCurrentInterruptionFilter() == filter);
        } finally {
            if (a.active()) {
                inst.runOnMainSync(() -> a.command("STOP", null));
                await(a::ready);
            }
            inst.runOnMainSync(activity::finish);
        }
    }

    private boolean brandedNotification(NotificationManager manager, Context context, String title) {
        return Arrays.stream(manager.getActiveNotifications())
                .map(status -> status.getNotification())
                .anyMatch(n -> title.equals(n.extras.getCharSequence(Notification.EXTRA_TITLE))
                        && n.getSmallIcon().getResId() == R.drawable.ic_audomo_notification
                        && n.color == context.getColor(R.color.audomo_purple));
    }

    @Test
    public void launcherToggleStopsAPausedRecordingAndReturnsToPreviousTask() throws Exception {
        var inst = InstrumentationRegistry.getInstrumentation();
        Assume.assumeTrue(Build.MODEL.contains("sdk_gphone")
                && "true".equals(InstrumentationRegistry.getArguments().getString("qaRecorder")));
        Context ctx = inst.getTargetContext();
        Audomo app = (Audomo) ctx.getApplicationContext();
        MainActivity previous = (MainActivity) inst.startActivitySync(
                new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Intent toggle = new Intent(ctx, TileStartActivity.class).setAction(TileStartActivity.TOGGLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            await(() -> app.ready() && previous.hasWindowFocus());
            int before = app.sessions.list().size();
            inst.runOnMainSync(() -> previous.startActivity(toggle));
            await(() -> app.state == Audomo.State.RECORDING && previous.hasWindowFocus());
            await(() -> app.frames > 0);
            inst.runOnMainSync(() -> app.command("PAUSE", null));
            await(() -> app.state == Audomo.State.PAUSED);
            inst.runOnMainSync(() -> previous.startActivity(toggle));
            await(() -> app.state == Audomo.State.SAVED && previous.hasWindowFocus());
            assertEquals(before + 1, app.sessions.list().size());
        } finally {
            if (app.active()) {
                inst.runOnMainSync(() -> app.command("STOP", null));
                await(app::ready);
            }
            inst.runOnMainSync(previous::finish);
        }
    }
}
