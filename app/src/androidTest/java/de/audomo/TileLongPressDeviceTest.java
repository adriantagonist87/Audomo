package de.audomo;

import static org.junit.Assert.*;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.*;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public class TileLongPressDeviceTest {
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();

    private void shell(String command) throws IOException {
        try (var in =
                new ParcelFileDescriptor.AutoCloseInputStream(
                        inst.getUiAutomation().executeShellCommand(command))) {
            in.readAllBytes();
        }
    }

    private void await(String message, BooleanSupplier condition) {
        long deadline = SystemClock.elapsedRealtime() + 7000;
        while (!condition.getAsBoolean() && SystemClock.elapsedRealtime() < deadline)
            SystemClock.sleep(100);
        assertTrue(message, condition.getAsBoolean());
    }

    private Rect tileBounds() {
        AccessibilityNodeInfo root = inst.getUiAutomation().getRootInActiveWindow();
        if (root == null) return null;
        for (AccessibilityNodeInfo label : root.findAccessibilityNodeInfosByText("Audomo")) {
            if (!"com.android.systemui".contentEquals(String.valueOf(label.getPackageName()))
                    || !label.isVisibleToUser()) continue;
            Rect bounds = new Rect();
            for (AccessibilityNodeInfo tile = label; tile != null; tile = tile.getParent()) {
                if (tile.isLongClickable() && tile.isEnabled()) {
                    tile.getBoundsInScreen(bounds);
                    if (!bounds.isEmpty()) return bounds;
                }
            }
            // AOSP can expose only the tile's text to accessibility during a panel update.
            label.getBoundsInScreen(bounds);
            if (!bounds.isEmpty() && label.isEnabled()) return bounds;
        }
        return null;
    }

    private Rect findTile(Rect display) throws IOException {
        for (int page = 0; page < 5; page++) {
            SystemClock.sleep(900); // SystemUI animiert Panel und Seitenwechsel.
            long deadline = SystemClock.elapsedRealtime() + 1800;
            do {
                Rect tile = tileBounds();
                if (tile != null) return tile;
                SystemClock.sleep(100);
            } while (SystemClock.elapsedRealtime() < deadline);
            int y = display.top + display.height() * 2 / 5;
            shell(
                    "input swipe "
                            + (display.right - display.width() / 8)
                            + " "
                            + y
                            + " "
                            + (display.left + display.width() / 8)
                            + " "
                            + y
                            + " 350");
        }
        throw new AssertionError("Audomo-Kachel nicht im geöffneten Systempanel gefunden");
    }

    @Test
    public void longPressRestoresExistingActivityAndPreservesRecordingState() throws Exception {
        Assume.assumeTrue(
                "Nur für entsperrtes Testgerät mit eingerichteter Kachel",
                "true".equals(InstrumentationRegistry.getArguments().getString("tileGesture")));
        boolean recording =
                "true".equals(InstrumentationRegistry.getArguments().getString("tileRecording"));
        if (recording) Assume.assumeTrue(Build.MODEL.contains("sdk_gphone"));
        Context c = inst.getTargetContext();
        assertFalse(c.getSystemService(KeyguardManager.class).isKeyguardLocked());
        shell("cmd statusbar collapse");
        MainActivity main =
                (MainActivity)
                        inst.startActivitySync(
                                Intent.makeMainActivity(new ComponentName(c, MainActivity.class))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Audomo app = (Audomo) c.getApplicationContext();
        await("Audomo nicht bereit", () -> main.hasWindowFocus() && app.ready());
        int previous = app.sessions.list().size();
        Rect display = main.getWindowManager().getCurrentWindowMetrics().getBounds();
        try {
            shell("input keyevent KEYCODE_HOME");
            await("Audomo nicht im Hintergrund", () -> !main.hasWindowFocus());
            if (recording) {
                shell("cmd statusbar expand-settings");
                Rect start = findTile(display);
                shell("input tap " + start.centerX() + " " + start.centerY());
                await("Kurztipp startet keine Testsitzung", () -> app.active() && app.frames > 0);
            }
            long before = app.frames;
            String session = app.token;
            shell("cmd statusbar expand-settings");
            Rect target = findTile(display);
            int x = target.centerX(), y = target.centerY();
            shell("input swipe " + x + " " + y + " " + x + " " + y + " 1100");
            await("Langdruck hat die vorhandene Ansicht nicht zurückgeholt", main::hasWindowFocus);
            if (recording) {
                assertEquals(session, app.token);
                assertTrue(app.active());
                await("Audioframes fehlen nach dem Langdruck", () -> app.frames > before);
                shell("cmd statusbar expand-settings");
                Rect stop = findTile(display);
                shell("input tap " + stop.centerX() + " " + stop.centerY());
                await(
                        "Kurztipp beendet die Testsitzung nicht",
                        () -> app.state == Audomo.State.SAVED);
                assertEquals(previous + 1, app.sessions.list().size());
            } else {
                assertTrue(app.ready());
                assertEquals(previous, app.sessions.list().size());
            }
        } finally {
            if (recording && app.active()) {
                inst.runOnMainSync(() -> app.command("STOP", null));
                await("Testsitzung nicht beendet", app::ready);
            }
            shell("cmd statusbar collapse");
        }
    }
}
