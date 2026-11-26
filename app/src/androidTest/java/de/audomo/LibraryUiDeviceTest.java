package de.audomo;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.*;
import android.os.SystemClock;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.*;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

@RunWith(AndroidJUnit4.class)
public class LibraryUiDeviceTest {
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();

    private <T extends View> T find(View root, Class<T> cls, String label) {
        if (root.getParent() instanceof DashboardPager
                && root.getImportantForAccessibility() == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) return null;
        if (cls.isInstance(root)
                && (label == null
                        || label.equals(root.getContentDescription())
                        || (root instanceof TextView t && label.contentEquals(t.getText()))))
            return cls.cast(root);
        if (root instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) {
                T v = find(group.getChildAt(i), cls, label);
                if (v != null) return v;
            }
        return null;
    }

    private void ui(Runnable action) {
        inst.runOnMainSync(action);
    }

    private void await(BooleanSupplier done) {
        long limit = SystemClock.elapsedRealtime() + 8000;
        while (SystemClock.elapsedRealtime() < limit) {
            AtomicReference<Boolean> ok = new AtomicReference<>();
            ui(() -> ok.set(done.getAsBoolean()));
            if (ok.get()) return;
            SystemClock.sleep(50);
        }
        fail("Bedienzustand fehlt");
    }

    private void choice(String text) throws Exception {
        inst.getUiAutomation().waitForIdle(250, 5000);
        long stop = SystemClock.elapsedRealtime() + 5000;
        while (SystemClock.elapsedRealtime() < stop) {
            var window = inst.getUiAutomation().getRootInActiveWindow();
            if (window != null)
                for (var node : window.findAccessibilityNodeInfosByText(text)) {
                    if (!text.equalsIgnoreCase(node.getText() == null ? "" : node.getText().toString())
                            && !text.equals(node.getContentDescription())
                            && !(text + ", ausgewählt").equals(node.getContentDescription()))
                        continue;
                    if (!node.isEnabled() || !node.isVisibleToUser()) continue;
                    AccessibilityNodeInfo actionNode = node;
                    while (actionNode != null && !actionNode.isClickable())
                        actionNode = actionNode.getParent();
                    if (actionNode != null && actionNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        inst.waitForIdleSync();
                        inst.getUiAutomation().waitForIdle(250, 5000);
                        return;
                    }
                    android.graphics.Rect bounds = new android.graphics.Rect();
                    node.getBoundsInScreen(bounds);
                    if (bounds.isEmpty()) continue;
                    long down = SystemClock.uptimeMillis();
                    for (int action : new int[] {MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
                        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(),
                                action, bounds.centerX(), bounds.centerY(), 0);
                        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
                        inst.sendPointerSync(event);
                        event.recycle();
                    }
                    inst.waitForIdleSync();
                    inst.getUiAutomation().waitForIdle(250, 5000);
                    return;
                }
            Thread.sleep(80);
        }
        fail("Auswahl fehlt: " + text);
    }

    private void assertNoCheckableRows(AccessibilityNodeInfo node) {
        assertFalse("Sortiermenü enthält ein ankreuzbares Element", node.isCheckable());
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) assertNoCheckableRows(child);
        }
    }

    private AccessibilityNodeInfo windowWith(String label) {
        long deadline = SystemClock.elapsedRealtime() + 5000;
        do {
            var window = inst.getUiAutomation().getRootInActiveWindow();
            if (window != null && !window.findAccessibilityNodeInfosByText(label).isEmpty()) return window;
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        throw new AssertionError("Dialog fehlt: " + label);
    }

    private void assertSortMenu(String key, String direction) throws java.util.concurrent.TimeoutException {
        inst.waitForIdleSync();
        var automation = inst.getUiAutomation();
        automation.waitForIdle(250, 5000);
        long deadline = SystemClock.elapsedRealtime() + 5000;
        do {
            AccessibilityNodeInfo menu = automation.getRootInActiveWindow();
            if (menu != null && List.of(key, direction).stream().allMatch(label ->
                    menu.findAccessibilityNodeInfosByText(label).stream().anyMatch(node ->
                            (label + ", ausgewählt").equals(node.getContentDescription())))) {
                assertNoCheckableRows(menu);
                return;
            }
            SystemClock.sleep(50);
        } while (SystemClock.elapsedRealtime() < deadline);
        fail("Aktive Sortierung fehlt: " + key + ", " + direction);
    }

    private MainActivity openTab(MainActivity from, String label, int target) {
        ui(() -> find(from.getWindow().getDecorView(), TextView.class, label).performClick());
        await(() -> from.dashboardIndex() == target && from.pager().position() == target);
        return from;
    }

    private void backTo(MainActivity expected, String label) {
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        await(() -> expected.hasWindowFocus() && expected.pager().position() == expected.dashboardIndex()
                && find(expected.getWindow().getDecorView(), View.class, label) != null);
    }

    private void touch(float x1, float x2, float y) {
        int steps = x1 == x2 ? 1 : 8;
        long down = SystemClock.uptimeMillis();
        for (int i = 0; i <= steps; i++) {
            int action = i == 0 ? MotionEvent.ACTION_DOWN : i == steps ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                    x1 + (x2 - x1) * i / steps, y, 0);
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            inst.sendPointerSync(event);
            event.recycle();
            SystemClock.sleep(16);
        }
        inst.waitForIdleSync();
    }

    private MainActivity swipeTab(MainActivity from, String label, int target, boolean left) {
        View root = from.getWindow().getDecorView();
        TextView tab = find(root, TextView.class, label);
        int[] position = new int[2];
        ui(() -> tab.getLocationOnScreen(position));
        touch(root.getWidth() * (left ? .75f : .25f), root.getWidth() * (left ? .25f : .75f),
                position[1] + tab.getHeight() / 2f);
        await(() -> from.dashboardIndex() == target && from.pager().position() == target);
        return from;
    }

    private void pointer(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        inst.sendPointerSync(event);
        event.recycle();
    }

    private void dragAndHold(long down, float from, float to, float y) {
        for (int i = 1; i <= 8; i++) {
            SystemClock.sleep(18);
            pointer(down, MotionEvent.ACTION_MOVE, from + (to - from) * i / 8, y);
        }
        // Give input resampling two stationary samples at the end of the drag.
        for (int i = 0; i < 2; i++) {
            SystemClock.sleep(24);
            pointer(down, MotionEvent.ACTION_MOVE, to, y);
        }
        inst.waitForIdleSync();
    }

    private int[] location(View view) {
        int[] result = new int[2];
        view.getLocationOnScreen(result);
        return result;
    }

    @Test public void bodyAndUnderlineTrackHeldDragWhileHeaderStaysFixed() throws Exception {
        Context ctx = inst.getTargetContext();
        MainActivity host = (MainActivity)inst.startActivitySync(new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            await(host::hasWindowFocus);
            openTab(host, "Wiedergeben", MainActivity.LIBRARY);
            DashboardPager pager = host.pager();
            View root = host.getWindow().getDecorView();
            View[] fixed = {find(root, TextView.class, "Audomo"), find(root, ImageButton.class, "Einstellungen"),
                    find(root, TextView.class, "Aufnehmen"), find(root, TextView.class, "Wiedergeben"),
                    find(root, TextView.class, "Warteschlange")};
            int[][] before = Arrays.stream(fixed).map(this::location).toArray(int[][]::new);
            int width = pager.getWidth();
            float y = location(pager)[1] + pager.getHeight() * .6f;
            long down = SystemClock.uptimeMillis();
            pointer(down, MotionEvent.ACTION_DOWN, width * .75f, y);
            dragAndHold(down, width * .75f, width * .35f, y);
            ui(() -> assertEquals("Seitenposition bei gehaltenem Finger", 1.4f, pager.position(), .01f));
            ui(() -> {
                for (int i = 0; i < fixed.length; i++) assertArrayEquals(before[i], location(fixed[i]));
                assertEquals(-width * .4f, pager.pages[1].getTranslationX(), 2);
                assertEquals(width * .6f, pager.pages[2].getTranslationX(), 2);
                assertEquals(MainActivity.LIBRARY, host.dashboardIndex());
            });
            DashboardPager.Strip strip = find(root, DashboardPager.Strip.class, null);
            int lineY = location(strip)[1] + strip.getHeight() / 2;
            float centerStart = before[3][0] + fixed[3].getWidth() / 2f;
            float centerEnd = before[4][0] + fixed[4].getWidth() / 2f;
            android.graphics.Bitmap screenshot = inst.getUiAutomation().takeScreenshot();
            int first = -1, last = -1;
            for (int x = 0; x < screenshot.getWidth(); x++) {
                if (screenshot.getPixel(x, lineY) != MainActivity.ACCENT) continue;
                if (first < 0) first = x;
                last = x;
            }
            screenshot.recycle();
            assertTrue("Unterstrich fehlt", first >= 0);
            assertEquals(centerStart + (centerEnd - centerStart) * .4f, (first + last) / 2f, 3);
            shot(ctx, "dashboard-wischbewegung-1.1.9");
            dragAndHold(down, width * .35f, width * .65f, y);
            await(() -> Math.abs(pager.position() - 1.1f) < .01f);
            pointer(down, MotionEvent.ACTION_CANCEL, width * .65f, y);
            await(() -> pager.position() == MainActivity.LIBRARY);
            backTo(host, "Aufnahme starten");
        } finally {
            ui(host::finish);
        }
    }

    @Test public void dashboardSwipesKeepQueueFilterAfterReopening() throws Exception {
        org.junit.Assume.assumeTrue(android.os.Build.MODEL.contains("sdk_gphone"));
        Context ctx = inst.getTargetContext();
        var prefs = Profile.prefs(ctx);
        Integer original = prefs.contains("queueFilter") ? prefs.getInt("queueFilter", 1) : null;
        MainActivity host = (MainActivity)inst.startActivitySync(new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            prefs.edit().putInt("queueFilter", 0).commit();
            MainActivity recorder = host;
            await(recorder::hasWindowFocus);
            MainActivity library = swipeTab(recorder, "Aufnehmen", MainActivity.LIBRARY, true);
            MainActivity queue = swipeTab(library, "Wiedergeben", MainActivity.QUEUE, true);
            choice("Filter: Alle Aufträge");
            choice("Zu prüfen");
            await(() -> find(queue.getWindow().getDecorView(), Button.class, "Filter: Zu prüfen") != null);
            var adapter = find(queue.getWindow().getDecorView(), QueueSwipeList.class, null).getAdapter();
            assertEquals(AdapterView.INVALID_ROW_ID, adapter.getItemId(adapter.getCount()));
            MainActivity again = swipeTab(queue, "Warteschlange", MainActivity.LIBRARY, false);
            MainActivity restored = openTab(again, "Warteschlange", MainActivity.QUEUE);
            await(() -> find(restored.getWindow().getDecorView(), Button.class, "Filter: Zu prüfen") != null);
            MainActivity direct = openTab(restored, "Aufnehmen", MainActivity.RECORD);
            backTo(restored, "Filter: Zu prüfen");
            assertSame(recorder, direct);
            backTo(again, "Aufnahmen sortieren");
            backTo(queue, "Filter: Zu prüfen");
            backTo(library, "Aufnahmen sortieren");
            backTo(recorder, "Aufnahme starten");
        } finally {
            ui(host::finish);
            if (original == null) prefs.edit().remove("queueFilter").commit();
            else prefs.edit().putInt("queueFilter", original).commit();
        }
    }

    private void assertQueueView(View root, int count, String label) {
        await(() -> {
            TextView status = find(root, TextView.class, label);
            return status != null && status.isShown()
                    && find(root, QueueSwipeList.class, null).getCount() == count;
        });
    }

    private long addQueueJob(UploadQueue queue, String target, String title, String state, String issue) {
        ContentValues values = new ContentValues();
        values.put("rec", UUID.randomUUID().toString());
        values.put("target", target);
        values.put("path", "/" + title + ".ogg");
        values.put("state", state);
        values.put("provider", "Dropbox");
        values.put("availability", issue);
        values.put("notBefore", System.currentTimeMillis() + 3_600_000);
        if (state.equals("Hochgeladen")) values.put("verified", "synthetic");
        return queue.getWritableDatabase().insertOrThrow("jobs", null, values);
    }

    @Test public void queueCountsActionsAndHistoryFollowTheVisibleJobs() throws Exception {
        org.junit.Assume.assumeTrue(android.os.Build.MODEL.contains("sdk_gphone"));
        Context ctx = inst.getTargetContext();
        UploadQueue queue = UploadQueue.get(ctx);
        org.junit.Assume.assumeTrue("Leere Testwarteschlange", queue.listedJobs().isEmpty());
        var prefs = Profile.prefs(ctx);
        Integer original = prefs.contains("queueFilter") ? prefs.getInt("queueFilter", 1) : null;
        String target = "QA-Queue-" + UUID.randomUUID();
        MainActivity host = (MainActivity)inst.startActivitySync(new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            prefs.edit().remove("queueFilter").commit();
            long finished = addQueueJob(queue, target, "Planungsrunde", "Hochgeladen", "");
            addQueueJob(queue, target, "Abstimmung", "Hochgeladen", "");
            MainActivity recorder = host;
            await(recorder::hasWindowFocus);
            MainActivity dashboard = openTab(recorder, "Warteschlange", MainActivity.QUEUE);
            View root = dashboard.getWindow().getDecorView();
            assertQueueView(root, 0, "Keine offenen Aufträge");
            assertFalse(find(root, Button.class, "Auswählen").isShown());
            assertNull(find(root, TextView.class, "2 Aufträge"));
            shot(ctx, "warteschlange-offen-1.1.7");
            choice("Warteschlange verwalten");
            var menu = windowWith("Uploadziele");
            assertTrue(menu.findAccessibilityNodeInfosByText("Warteschlange leeren").isEmpty());
            choice("Uploadziele");
            assertTrue(windowWith("Nur über WLAN").findAccessibilityNodeInfosByText("Warteschlange").isEmpty());
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            assertQueueView(root, 0, "Keine offenen Aufträge");
            choice("Filter: Offene Aufträge");
            choice("Alle Aufträge");
            assertQueueView(root, 2, "2 Aufträge");
            choice("Warteschlange verwalten");
            choice("Warteschlange leeren");
            windowWith("2 Aufträge werden entfernt.");
            choice("Abbrechen");
            assertQueueView(root, 2, "2 Aufträge");
            addQueueJob(queue, target, "Verworfen", "Abgebrochen", "");
            addQueueJob(queue, target, "Ausgelassen", "Ausgelassen", "");
            long waiting = addQueueJob(queue, target, "Projektstart", "Wartet", "");
            addQueueJob(queue, target, "Transfer", "Upload läuft", "");
            long failed = addQueueJob(queue, target, "Rückblick", "Fehler", "");
            addQueueJob(queue, target, "Namenskonflikt", "Konflikt", "");
            long missing = addQueueJob(queue, target, "Cloudprüfung", "Hochgeladen", "Cloudkopie fehlt");
            choice("Filter: Alle Aufträge");
            choice("Offene Aufträge");
            assertQueueView(root, 5, "5 Aufträge");
            choice("Filter: Offene Aufträge");
            choice("Zu prüfen");
            assertQueueView(root, 3, "3 Aufträge");
            shot(ctx, "warteschlange-pruefen-1.1.7");
            choice("Warteschlange verwalten");
            windowWith("Fehlgeschlagene erneut versuchen");
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            choice("Filter: Zu prüfen");
            choice("Wartend");
            assertQueueView(root, 1, "1 Auftrag");
            choice("Warteschlange verwalten");
            assertTrue(windowWith("Uploadziele").findAccessibilityNodeInfosByText("Fehlgeschlagene erneut versuchen").isEmpty());
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            choice("Auswählen");
            choice("Alle auswählen");
            assertQueueView(root, 1, "1 ausgewählt");
            queue.state(waiting, "Hochgeladen", "");
            assertQueueView(root, 0, "Keine wartenden Aufträge");
            assertFalse(find(root, Button.class, "Entfernen").isShown());
            backTo(recorder, "Aufnahme starten");
            await(() -> find(recorder.getWindow().getDecorView(), Button.class, "3 Uploads prüfen · Warteschlange öffnen") != null);
            MainActivity review = openTab(recorder, "3 Uploads prüfen · Warteschlange öffnen", MainActivity.QUEUE);
            assertQueueView(review.getWindow().getDecorView(), 3, "3 Aufträge");
            assertEquals(3, prefs.getInt("queueFilter", -1));
            backTo(recorder, "Aufnahme starten");
            MainActivity again = openTab(recorder, "Warteschlange", MainActivity.QUEUE);
            assertQueueView(again.getWindow().getDecorView(), 0, "Keine wartenden Aufträge");
            assertEquals(1, queue.remove(List.of(finished, missing), true).removed());
            assertTrue(queue.byId(finished).knownCopy());
            assertTrue(queue.byId(missing).inQueue());
            long later = addQueueJob(queue, target, "Späterer Fehler", "Fehler", "");
            synchronized (queue) {
                assertEquals(1, queue.retryErrors(List.of(failed, missing)));
                assertEquals("Wartet", queue.byId(failed).state());
                assertEquals("Fehler", queue.byId(later).state());
            }
        } finally {
            ui(host::finish);
            queue.getWritableDatabase().delete("jobs", "target=?", new String[]{target});
            if (original == null) prefs.edit().remove("queueFilter").commit();
            else prefs.edit().putInt("queueFilter", original).commit();
        }
    }

    void shot(Context ctx, String name) throws Exception {
        if (!"true".equals(InstrumentationRegistry.getArguments().getString("screenshots"))) return;
        inst.waitForIdleSync();
        SystemClock.sleep(350);
        File dir = new File(ctx.getCacheDir(), "qa-bibliothek");
        Files.createDirectories(dir.toPath());
        try (FileOutputStream out = new FileOutputStream(new File(dir, name + ".png"))) {
            inst.getUiAutomation()
                    .takeScreenshot()
                    .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
        }
    }

    @Test
    public void fiveHundredRowsSearchSelectionPlayerAndSettings() throws Exception {
        Context ctx = inst.getTargetContext();
        org.junit.Assume.assumeTrue(android.os.Build.MODEL.contains("sdk_gphone"));
        Audomo app = (Audomo) ctx.getApplicationContext();
        UploadQueue q = UploadQueue.get(ctx);
        org.junit.Assume.assumeTrue("Isolierter Emulator ohne aktive Übertragung", !q.pending());
        String syntheticTarget = "QA-UI-" + UUID.randomUUID();
        List<String> states = List.of("Hochgeladen", "Upload läuft", "Wartet", "Fehler");
        SessionStore original = app.sessions;
        new CloudLibrary(original).known();
        Set<String> previousFolders = new HashSet<>(new LibraryFiles(original).folders());
        File temp = Files.createTempDirectory(ctx.getCacheDir().toPath(), "qa-library-").toFile();
        Context isolated =
                new ContextWrapper(ctx) {
                    public File getFilesDir() {
                        return temp;
                    }
                };
        SessionStore fixtures = new SessionStore(isolated);
        Files.createDirectories(fixtures.root.toPath());
        File sample = new File(temp, "sample.mp3");
        try (InputStream in = inst.getContext().getAssets().open("testsignal.mp3")) {
            Files.copy(in, sample.toPath());
        }
        OffsetDateTime date = OffsetDateTime.parse("2026-01-01T09:30:00+01:00");
        String previousFolder = Profile.prefs(ctx).getString("folder", "");
        var prefs = Profile.prefs(ctx);
        int originalStep = prefs.getInt("skipSeconds", 5);
        int originalSort = prefs.getInt("sort", 1);
        boolean originalDescending = prefs.getBoolean("descending", true);
        boolean originalSilence = prefs.getBoolean("skipSilence", false);
        boolean originalBluetoothOnly = prefs.getBoolean(PlaybackOutput.BLUETOOTH_ONLY, false);
        float originalSpeed = prefs.getFloat("speed", 1);
        prefs.edit()
                .putInt("skipSeconds", 5)
                .putInt("sort", 1)
                .putBoolean("descending", true)
                .putFloat("speed", 1)
                .putBoolean("skipSilence", false)
                .putBoolean(PlaybackOutput.BLUETOOTH_ONLY, false)
                .commit();
        MainActivity screen = null;
        try {
            for (int i = 0; i <= 500; i++) {
                String name = String.format(Locale.ROOT, "Besprechung %03d.mp3", i);
                boolean ogg = i % 2 == 0;
                if (ogg) name = name.replace(".mp3", ".ogg");
                File dir = new File(fixtures.root, UUID.randomUUID().toString());
                Files.createDirectory(dir.toPath());
                if (i >= 496 && i < 500)
                    q.getWritableDatabase()
                            .execSQL(
                                    "INSERT INTO jobs(rec,target,path,state,provider,verified) VALUES(?,?,?,?,?,?)",
                                    new Object[] {
                                        dir.getName(),
                                        syntheticTarget,
                                        "/QA/" + name,
                                        states.get(499 - i), "Dropbox", i == 499 ? "synthetic" : ""
                                    });
                String physical = ogg ? "audio.ogg" : "audio.mp3";
                if (ogg) {
                    try (InputStream in = inst.getContext().getAssets().open("testsignal.ogg")) {
                        Files.copy(in, new File(dir, physical).toPath());
                    }
                } else Files.copy(sample.toPath(), new File(dir, physical).toPath());
                JSONObject meta =
                        new JSONObject()
                                .put("name", physical)
                                .put("folder", syntheticTarget)
                                .put("displayName", name)
                                .put("frames", ogg ? 48317 : 96000)
                                .put("start", date.plusMinutes(i).toString());
                if (i == 500) meta.put("folder", "Archiv");
                Files.write(
                        new File(dir, "session.json").toPath(),
                        meta.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            assertEquals(501, fixtures.list().size());
            app.sessions = fixtures;
            Profile.prefs(ctx).edit().putString("folder", syntheticTarget).commit();
            screen =
                    (MainActivity)
                            inst.startActivitySync(
                                    new Intent(ctx, MainActivity.class)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            MainActivity recorder = screen;
            MainActivity activity = openTab(recorder, "Wiedergeben", MainActivity.LIBRARY);
            View root = activity.getWindow().getDecorView();
            await(() ->
                    find(root, ListView.class, null) != null
                            && find(root, ListView.class, null).getCount() == 500);
            ListView list = find(root, ListView.class, null);
            for (String state : states) {
                TextView label = find(root, TextView.class, "Lokal · " + (state.equals("Hochgeladen") ? "Dropbox" : state));
                assertNotNull(state, label);
                assertNotNull(label.getCompoundDrawables()[0]);
            }
            shot(ctx, "liste-500");
            ui(() -> find(root, ImageButton.class, "Aufnahmen sortieren").performClick());
            assertSortMenu("Datum", "Neueste zuerst");
            shot(ctx, "sortiermenue-0.2.2");
            choice("Neueste zuerst");
            assertTrue(prefs.getBoolean("descending", false));
            ui(() -> list.performItemClick(list.getChildAt(0), 0, 0));
            await(() -> find(root, Button.class, "Umbenennen") != null);
            assertNotNull(find(root, Button.class, "Upload"));
            TextView queueTab = find(root, TextView.class, "Warteschlange");
            int[] position = new int[2];
            ui(() -> queueTab.getLocationOnScreen(position));
            float x = position[0] + queueTab.getWidth() / 2f;
            touch(x, x, position[1] + queueTab.getHeight() / 2f);
            await(() -> activity.hasWindowFocus() && find(root, ImageButton.class, "Abspielen") == null);
            ui(() -> list.performItemClick(list.getChildAt(0), 0, 0));
            await(() -> find(root, Button.class, "Umbenennen") != null);
            shot(ctx, "dateiaktionen");
            ui(() -> find(root, Button.class, "Umbenennen").performClick());
            inst.getUiAutomation().waitForIdle(250, 5000);
            var dialog = windowWith("Aufnahme umbenennen");
            assertTrue(dialog.findAccessibilityNodeInfosByText("Auf diesem Gerät").stream().anyMatch(AccessibilityNodeInfo::isChecked));
            choice("Abbrechen");
            ui(() -> find(root, ImageButton.class, "Abspielen").performClick());
            // The isolated API 34 emulator uses its built-in speaker.
            shot(ctx, "kopfhoerer-rueckfrage");
            windowWith("Ohne Kopfhörer abspielen?");
            touch(root.getWidth() * .1f, root.getWidth() * .1f, root.getHeight() * .12f);
            await(activity::hasWindowFocus);
            assertNotNull(find(root, ImageButton.class, "Abspielen"));
            assertEquals(0, find(root, SeekBar.class, null).getProgress());
            ui(() -> find(root, ImageButton.class, "Abspielen").performClick());
            choice("Trotzdem abspielen");
            await(() -> find(root, ImageButton.class, "Pause") != null);
            ui(() -> find(root, ImageButton.class, "Pause").performClick());
            ui(() -> find(root, ImageButton.class, "Abspielen").performClick());
            choice("Abbrechen");
            choice("Einstellungen");
            choice("Wiedergabe");
            choice("Audioausgang");
            choice("Nur Bluetooth");
            choice("Zurück");
            choice("Zurück");
            ui(() -> find(root, ImageButton.class, "Abspielen").performClick());
            inst.getUiAutomation().waitForIdle(250, 5000);
            var strictWarning = windowWith("Bluetooth-Ausgabe erforderlich");
            assertTrue(strictWarning.findAccessibilityNodeInfosByText("Trotzdem abspielen").isEmpty());
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            await(activity::hasWindowFocus);
            ui(() -> find(root, Button.class, "Sprung 5 s").performClick());
            choice("15 Sekunden");
            await(() -> find(root, ImageButton.class, "15 Sekunden vor") != null);
            ui(() -> find(root, ImageButton.class, "15 Sekunden vor").performClick());
            assertEquals(100000, find(root, SeekBar.class, null).getProgress());
            ui(() -> find(root, ImageButton.class, "15 Sekunden zurück").performClick());
            assertEquals(0, find(root, SeekBar.class, null).getProgress());
            ui(() -> find(root, Button.class, "Tempo 1.0×").performClick());
            choice("1,5×");
            ui(() -> find(root, Switch.class, "Stille").setChecked(true));
            assertEquals(1.5f, prefs.getFloat("speed", 1), 0);
            assertTrue(prefs.getBoolean("skipSilence", false));
            ui(() ->
                    list.getOnItemLongClickListener()
                            .onItemLongClick(list, list.getChildAt(0), 0, 0));
            await(() -> find(root, TextView.class, "1 ausgewählt") != null);
            assertTrue(find(root, Button.class, "Umbenennen").isEnabled());
            ui(() -> find(root, Button.class, "Mehr").performClick());
            choice("Umbenennen");
            assertFalse(inst.getUiAutomation().getRootInActiveWindow()
                    .findAccessibilityNodeInfosByText("Aufnahme umbenennen").isEmpty());
            choice("Abbrechen");
            ui(() -> list.performItemClick(list.getChildAt(1), 1, 1));
            await(() -> find(root, TextView.class, "2 ausgewählt") != null);
            assertNull(find(root, Button.class, "Umbenennen"));
            ui(() -> find(root, Button.class, "Mehr").performClick());
            inst.getUiAutomation().waitForIdle(250, 5000);
            var multipleMenu = inst.getUiAutomation().getRootInActiveWindow();
            assertFalse(multipleMenu.findAccessibilityNodeInfosByText("Kopieren").isEmpty());
            assertTrue(multipleMenu.findAccessibilityNodeInfosByText("Umbenennen").isEmpty());
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            await(activity::hasWindowFocus);
            shot(ctx, "mehrfachauswahl");
            assertTrue(find(root, Button.class, "Cloud Upload").isEnabled());
            choice("Cloud Upload");
            inst.getUiAutomation().waitForIdle(250, 5000);
            assertFalse(inst.getUiAutomation().getRootInActiveWindow()
                    .findAccessibilityNodeInfosByText("Uploadziel für 2 Aufnahmen").isEmpty());
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            await(() -> activity.hasWindowFocus() && find(root, TextView.class, "2 ausgewählt") != null);
            assertNotNull(find(root, ImageButton.class, "Alle auswählen").getDrawable());
            shot(ctx, "alle-auswaehlen-0.2.5");
            choice("Alle auswählen");
            await(() -> find(root, TextView.class, "500 ausgewählt") != null);
            assertNotNull(find(root, ImageButton.class, "Alle abwählen").getDrawable());
            shot(ctx, "alle-abwaehlen-0.2.5");
            choice("Alle abwählen");
            await(() -> find(root, TextView.class, "0 ausgewählt") != null);
            assertFalse(find(root, Button.class, "Löschen").isEnabled());
            assertFalse(find(root, Button.class, "Cloud Upload").isEnabled());
            ui(() -> list.performItemClick(list.getChildAt(1), 1, 1));
            await(() -> find(root, TextView.class, "1 ausgewählt") != null);
            ui(() -> {
                find(root, ImageButton.class, "Aufnahmen durchsuchen").performClick();
                find(root, EditText.class, null).setText(" 49");
            });
            await(() -> list.getCount() == 10);
            ui(() -> find(root, ImageButton.class, "Alle auswählen").performClick());
            await(() -> find(root, TextView.class, "10 ausgewählt") != null);
            ui(() -> {
                find(root, EditText.class, null).setText("Besprechung");
                find(root, CheckBox.class, "Alle Ordner").setChecked(true);
            });
            await(() -> list.getCount() == 501 && find(root, TextView.class, "10 ausgewählt") != null);
            ui(() -> find(root, ImageButton.class, "Alle auswählen").performClick());
            await(() -> find(root, TextView.class, "501 ausgewählt") != null);
            ui(() -> find(root, CheckBox.class, "Alle Ordner").setChecked(false));
            await(() -> find(root, TextView.class, "500 ausgewählt") != null);
            ui(() -> find(root, EditText.class, null).setText(" 125"));
            await(() -> list.getCount() == 1);
            assertTrue(((SessionStore.Entry) list.getItemAtPosition(0)).name().contains("125"));
            assertNotNull(find(root, TextView.class, "1 ausgewählt"));
            assertNotNull(find(root, ImageButton.class, "Alle abwählen"));
            ui(() -> find(root, EditText.class, null).setText("nicht-vorhanden"));
            await(() -> list.getCount() == 0);
            assertFalse(find(root, ImageButton.class, "Alle auswählen").isEnabled());
            ui(() -> find(root, EditText.class, null).setText(""));
            await(() -> list.getCount() == 500);
            ui(() -> find(root, ImageButton.class, "Aufnahmen durchsuchen").performClick());
            await(() -> !find(root, EditText.class, null).isShown()
                    && !root.getRootWindowInsets().isVisible(WindowInsets.Type.ime()));
            inst.getUiAutomation().waitForIdle(300, 5000);
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            await(() -> find(root, ImageButton.class, "Alle auswählen") == null && list.isShown());
            for (String key : List.of("Name", "Datum", "Dauer", "Größe", "Endung")) {
                ui(() -> find(root, ImageButton.class, "Aufnahmen sortieren").performClick());
                choice(key);
                List<String> directions =
                        switch (key) {
                            case "Datum" -> List.of("Älteste zuerst", "Neueste zuerst");
                            case "Dauer" -> List.of("Kürzeste zuerst", "Längste zuerst");
                            case "Größe" -> List.of("Kleinste zuerst", "Größte zuerst");
                            default -> List.of("A bis Z", "Z bis A");
                        };
                for (int direction = 0; direction < directions.size(); direction++) {
                    boolean descending = direction == 1;
                    ui(() -> find(root, ImageButton.class, "Aufnahmen sortieren").performClick());
                    choice(directions.get(direction));
                    assertEquals(descending, prefs.getBoolean("descending", true));
                    Comparator<SessionStore.Entry> order =
                            switch (key) {
                                case "Name" ->
                                        Comparator.comparing(
                                                SessionStore.Entry::name,
                                                String.CASE_INSENSITIVE_ORDER);
                                case "Datum" -> Comparator.comparing(e -> e.start().toInstant());
                                case "Dauer" ->
                                        Comparator.comparingLong(SessionStore.Entry::frames);
                                case "Größe" -> Comparator.comparingLong(e -> e.file().length());
                                default ->
                                        Comparator.comparing(e -> LibraryFiles.extension(e.name()));
                            };
                    ui(() -> {
                        SessionStore.Entry prev = (SessionStore.Entry) list.getItemAtPosition(0);
                        for (int index = 1; index < 500; index++) {
                            SessionStore.Entry next =
                                    (SessionStore.Entry) list.getItemAtPosition(index);
                            assertTrue(
                                    key,
                                    descending
                                            ? order.compare(prev, next) >= 0
                                            : order.compare(prev, next) <= 0);
                            prev = next;
                        }
                    });
                }
            }
            MainActivity nextRecorder = openTab(activity, "Aufnehmen", MainActivity.RECORD);
            MainActivity nextLibrary = openTab(nextRecorder, "Wiedergeben", MainActivity.LIBRARY);
            View nextRoot = nextLibrary.getWindow().getDecorView();
            await(() -> find(nextRoot, ImageButton.class, "Aufnahmen sortieren") != null);
            ui(() -> find(nextRoot, ImageButton.class, "Aufnahmen sortieren").performClick());
            assertSortMenu("Endung", "Z bis A");
            choice("Z bis A");
            assertEquals(501, fixtures.list().size());

            ListView reopened = find(nextRoot, ListView.class, null);
            ui(() -> reopened.performItemClick(reopened.getChildAt(0), 0, reopened.getItemIdAtPosition(0)));
            await(() -> find(nextRoot, ImageButton.class, "Abspielen") != null);
            choice("Einstellungen");
            choice("Dateien und Ordner");
            choice("Papierkorb öffnen");
            await(() -> find(nextRoot, TextView.class, "Der Papierkorb ist leer") != null);
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            choice("Speicher und Import");
            choice("Zurück");
            choice("Zurück");
            choice("Zurück");
            await(() -> nextLibrary.hasWindowFocus() && find(nextRoot, ImageButton.class, "Abspielen") != null);
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            await(() -> find(nextRoot, ImageButton.class, "Abspielen") == null);
            assertNotNull(find(nextRoot, ListView.class, null));
            openTab(nextLibrary, "Aufnehmen", MainActivity.RECORD);
            backTo(nextLibrary, "Aufnahmen sortieren");
            backTo(nextRecorder, "Aufnahme starten");
            backTo(activity, "Aufnahmen sortieren");
            backTo(recorder, "Aufnahme starten");
        } finally {
            if (screen != null) {
                MainActivity activity = screen;
                ui(activity::finish);
                inst.waitForIdleSync();
            }
            q.getWritableDatabase().execSQL("DELETE FROM recordings WHERE rec IN (SELECT rec FROM jobs WHERE target=?)",
                    new Object[]{syntheticTarget});
            q.getWritableDatabase().delete("jobs", "target=?", new String[] {syntheticTarget});
            app.sessions = original;
            prefs.edit()
                    .putStringSet("folders", previousFolders)
                    .putString("folder", previousFolder)
                    .putInt("sort", originalSort)
                    .putBoolean("descending", originalDescending)
                    .putInt("skipSeconds", originalStep)
                    .putFloat("speed", originalSpeed)
                    .putBoolean("skipSilence", originalSilence)
                    .putBoolean(PlaybackOutput.BLUETOOTH_ONLY, originalBluetoothOnly)
                    .commit();
            try (var stream = Files.walk(temp.toPath())) {
                for (Path path :
                        stream.sorted(Comparator.reverseOrder())
                                .collect(java.util.stream.Collectors.toList()))
                    Files.deleteIfExists(path);
            }
        }
    }
}
