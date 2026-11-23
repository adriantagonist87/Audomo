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
                    if (!text.contentEquals(node.getText() == null ? "" : node.getText())
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

    private MainActivity openTab(MainActivity from, String label, Class<? extends MainActivity> target,
            List<MainActivity> opened) {
        Instrumentation.ActivityMonitor monitor = inst.addMonitor(target.getName(), null, false);
        try {
            ui(() -> find(from.getWindow().getDecorView(), TextView.class, label).performClick());
            MainActivity to = (MainActivity) inst.waitForMonitorWithTimeout(monitor, 5000);
            assertNotNull("Android hat den Reiter nicht geöffnet: " + label, to);
            opened.add(to);
            await(to::hasWindowFocus);
            assertEquals(from.getTaskId(), to.getTaskId());
            return to;
        } finally {
            inst.removeMonitor(monitor);
        }
    }

    private void backTo(MainActivity expected, String label) {
        inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        await(() -> expected.hasWindowFocus()
                && find(expected.getWindow().getDecorView(), View.class, label) != null);
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
        float originalSpeed = prefs.getFloat("speed", 1);
        prefs.edit()
                .putInt("skipSeconds", 5)
                .putInt("sort", 1)
                .putBoolean("descending", true)
                .putFloat("speed", 1)
                .putBoolean("skipSilence", false)
                .commit();
        List<MainActivity> openedTabs = new ArrayList<>();
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
            MainActivity activity = openTab(recorder, "Wiedergeben", LibraryActivity.class, openedTabs);
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
            shot(ctx, "dateiaktionen");
            ui(() -> find(root, ImageButton.class, "Abspielen").performClick());
            await(() -> find(root, ImageButton.class, "Pause") != null);
            ui(() -> find(root, ImageButton.class, "Pause").performClick());
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
            ui(() -> list.performItemClick(list.getChildAt(1), 1, 1));
            await(() -> find(root, TextView.class, "2 ausgewählt") != null);
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
            MainActivity nextRecorder = openTab(activity, "Aufnehmen", MainActivity.class, openedTabs);
            MainActivity nextLibrary = openTab(nextRecorder, "Wiedergeben", LibraryActivity.class, openedTabs);
            View nextRoot = nextLibrary.getWindow().getDecorView();
            await(() -> find(nextRoot, ImageButton.class, "Aufnahmen sortieren") != null);
            ui(() -> find(nextRoot, ImageButton.class, "Aufnahmen sortieren").performClick());
            assertSortMenu("Endung", "Z bis A");
            choice("Z bis A");
            assertEquals(501, fixtures.list().size());

            ListView reopened = find(nextRoot, ListView.class, null);
            ui(() -> reopened.performItemClick(reopened.getChildAt(0), 0, reopened.getItemIdAtPosition(0)));
            await(() -> find(nextRoot, ImageButton.class, "Abspielen") != null);
            choice("Weitere Optionen");
            choice("Einstellungen");
            choice("Papierkorb");
            await(() -> find(nextRoot, TextView.class, "Der Papierkorb ist leer") != null);
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            choice("Speicher und Import");
            choice("Zurück");
            choice("Zurück");
            await(() -> nextLibrary.hasWindowFocus() && find(nextRoot, ImageButton.class, "Abspielen") != null);
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            await(() -> find(nextRoot, ImageButton.class, "Abspielen") == null);
            assertNotNull(find(nextRoot, ListView.class, null));
            openTab(nextLibrary, "Aufnehmen", MainActivity.class, openedTabs);
            backTo(nextLibrary, "Aufnahmen sortieren");
            backTo(nextRecorder, "Aufnahme starten");
            backTo(activity, "Aufnahmen sortieren");
            backTo(recorder, "Aufnahme starten");
        } finally {
            Collections.reverse(openedTabs);
            for (MainActivity tab : openedTabs)
                ui(() -> { if (!tab.isDestroyed()) tab.finish(); });
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
