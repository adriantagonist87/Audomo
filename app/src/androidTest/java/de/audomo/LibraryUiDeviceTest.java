package de.audomo;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.*;
import android.os.SystemClock;
import android.view.*;
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
        long stop = SystemClock.elapsedRealtime() + 5000;
        while (SystemClock.elapsedRealtime() < stop) {
            var window = inst.getUiAutomation().getRootInActiveWindow();
            if (window != null)
                for (var node : window.findAccessibilityNodeInfosByText(text)) {
                    if (!text.contentEquals(node.getText() == null ? "" : node.getText())) continue;
                    while (node != null && !node.isClickable()) node = node.getParent();
                    if (node != null
                            && node.performAction(
                                    android.view.accessibility.AccessibilityNodeInfo
                                            .ACTION_CLICK)) {
                        inst.waitForIdleSync();
                        return;
                    }
                }
            Thread.sleep(80);
        }
        fail("Auswahl fehlt: " + text);
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
        Audomo app = (Audomo) ctx.getApplicationContext();
        SessionStore original = app.sessions;
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
        MainActivity screen = null;
        try {
            for (int i = 0; i < 500; i++) {
                String name = String.format(Locale.ROOT, "Besprechung %03d.mp3", i);
                boolean ogg = i % 2 == 0;
                if (ogg) name = name.replace(".mp3", ".ogg");
                File dir = new File(fixtures.root, UUID.randomUUID().toString());
                Files.createDirectory(dir.toPath());
                String physical = ogg ? "audio.ogg" : "audio.mp3";
                if (ogg) {
                    try (InputStream in = inst.getContext().getAssets().open("testsignal.ogg")) {
                        Files.copy(in, new File(dir, physical).toPath());
                    }
                } else Files.copy(sample.toPath(), new File(dir, physical).toPath());
                JSONObject meta =
                        new JSONObject()
                                .put("name", physical)
                                .put("displayName", name)
                                .put("frames", ogg ? 48317 : 96000)
                                .put("start", date.plusMinutes(i).toString());
                Files.write(
                        new File(dir, "session.json").toPath(),
                        meta.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            assertEquals(500, fixtures.list().size());
            app.sessions = fixtures;
            Profile.prefs(ctx).edit().putString("folder", "").commit();
            screen =
                    (MainActivity)
                            inst.startActivitySync(
                                    new Intent(ctx, MainActivity.class)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            MainActivity activity = screen;
            View root = activity.getWindow().getDecorView();
            ui(() -> find(root, TextView.class, "Wiedergeben").performClick());
            await(() ->
                    find(root, ListView.class, null) != null
                            && find(root, ListView.class, null).getCount() == 500);
            ListView list = find(root, ListView.class, null);
            shot(ctx, "liste-500");
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
            ui(() -> {
                find(root, ImageButton.class, "Aufnahmen durchsuchen").performClick();
                find(root, EditText.class, null).setText(" 125");
            });
            await(() -> list.getCount() == 1);
            assertTrue(((SessionStore.Entry) list.getItemAtPosition(0)).name().contains("125"));
            assertNull(find(root, TextView.class, "2 ausgewählt"));
            ui(() -> find(root, EditText.class, null).setText("nicht-vorhanden"));
            await(() -> list.getCount() == 0);
            ui(() -> find(root, EditText.class, null).setText(""));
            await(() -> list.getCount() == 500);
            for (String key : List.of("Name", "Datum", "Dauer", "Größe", "Endung")) {
                ui(() -> find(root, ImageButton.class, "Aufnahmen sortieren").performClick());
                choice(key);
                for (int direction = 0; direction < 2; direction++) {
                    boolean descending = prefs.getBoolean("descending", true);
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
                    ui(() -> find(root, ImageButton.class, "Aufnahmen sortieren").performClick());
                    choice("Absteigend");
                    assertNotEquals(descending, prefs.getBoolean("descending", true));
                }
            }
            assertEquals(500, fixtures.list().size());
        } finally {
            if (screen != null) {
                MainActivity activity = screen;
                ui(activity::finish);
                inst.waitForIdleSync();
            }
            app.sessions = original;
            prefs.edit()
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
