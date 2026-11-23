package de.audomo;

import static org.junit.Assert.*;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.*;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.view.*;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.*;

@RunWith(AndroidJUnit4.class)
public class CalendarDayDeviceTest {
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();

    private View find(View root, String label) {
        CharSequence desc = root.getContentDescription();
        if (root.isShown()
                && ((desc != null && desc.toString().contains(label))
                        || root instanceof TextView t && label.equals(t.getText().toString())))
            return root;
        if (root instanceof ViewGroup g)
            for (int i = 0; i < g.getChildCount(); i++) {
                View item = find(g.getChildAt(i), label);
                if (item != null) return item;
            }
        return null;
    }

    private void await(String label, BooleanSupplier ready) {
        long end = SystemClock.elapsedRealtime() + 15000;
        while (!ready.getAsBoolean() && SystemClock.elapsedRealtime() < end) SystemClock.sleep(75);
        assertTrue(label, ready.getAsBoolean());
    }

    private View view(Activity screen, String label) {
        AtomicReference<View> result = new AtomicReference<>();
        await(
                label,
                () -> {
                    inst.runOnMainSync(
                            () -> result.set(find(screen.getWindow().getDecorView(), label)));
                    return result.get() != null;
                });
        return result.get();
    }

    private void tap(Activity screen, String label) {
        View v = view(screen, label);
        inst.runOnMainSync(v::performClick);
    }

    private CalendarDayActivity open(MainActivity main) throws Exception {
        await("Dashboard im Vordergrund", main::hasWindowFocus);
        var monitor = inst.addMonitor(CalendarDayActivity.class.getName(), null, false);
        try {
            tap(main, "Tagesansicht öffnen");
            CalendarDayActivity day =
                    (CalendarDayActivity) monitor.waitForActivityWithTimeout(5000);
            assertNotNull(day);
            await("Tagesansicht im Vordergrund", day::hasWindowFocus);
            inst.getUiAutomation().waitForIdle(150, 3000);
            return day;
        } finally {
            inst.removeMonitor(monitor);
        }
    }

    private void screenshot(Context ctx, String file) throws Exception {
        inst.waitForIdleSync();
        SystemClock.sleep(400);
        try (FileOutputStream out = new FileOutputStream(new File(ctx.getCacheDir(), file))) {
            assertTrue(
                    inst.getUiAutomation()
                            .takeScreenshot()
                            .compress(Bitmap.CompressFormat.PNG, 100, out));
        }
    }

    @Test
    public void pickFromTimelineCancelAndRecordOnce() throws Exception {
        org.junit.Assume.assumeTrue(Build.MODEL.contains("sdk_gphone"));
        Context ctx = inst.getTargetContext();
        Audomo app = (Audomo) ctx.getApplicationContext();
        await("Recorder bereit", app::ready);
        var prefs = Profile.prefs(ctx);
        Map<String, ?> previous = prefs.getAll();
        var oldDraft = app.draft.get();
        String owner = "audomo.day." + UUID.randomUUID();
        Uri table =
                Calendars.CONTENT_URI
                        .buildUpon()
                        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                        .appendQueryParameter(Calendars.ACCOUNT_NAME, owner)
                        .appendQueryParameter(
                                Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                        .build();
        List<Uri> calendars = new ArrayList<>();
        List<Activity> opened = new ArrayList<>();
        Set<String> previousIds = new HashSet<>();
        for (SessionStore.Entry e : app.sessions.list()) previousIds.add(e.id());
        LocalDate today = LocalDate.now();
        try {
            inst.getUiAutomation()
                    .adoptShellPermissionIdentity(
                            Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR);
            for (String title : List.of("Arbeit", "Privat", "Ausgeblendet")) {
                ContentValues v = new ContentValues();
                v.put(Calendars.ACCOUNT_NAME, owner);
                v.put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
                v.put(Calendars.NAME, title);
                v.put(Calendars.CALENDAR_DISPLAY_NAME, title);
                v.put(Calendars.OWNER_ACCOUNT, owner);
                v.put(Calendars.CALENDAR_COLOR, title.equals("Arbeit") ? 0xffd65c39 : 0xff8957c9);
                v.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER);
                v.put(Calendars.VISIBLE, 1);
                v.put(Calendars.SYNC_EVENTS, 1);
                calendars.add(ctx.getContentResolver().insert(table, v));
            }
            long work = ContentUris.parseId(calendars.get(0)),
                    privateId = ContentUris.parseId(calendars.get(1));
            long nine =
                    today.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            var fixture = new CalendarProviderDeviceTest();
            fixture.event(ctx, work, "Projektbesprechung", nine, 90 * 60000, null, false, false);
            fixture.event(ctx, privateId, "Musikprobe", nine, 120 * 60000, null, false, false);
            fixture.event(
                    ctx, work, "Redaktionsrunde", nine + 4 * 3600000, 3600000, null, false, false);
            fixture.event(
                    ctx,
                    privateId,
                    "Konzertabend",
                    nine + 9 * 3600000,
                    3600000,
                    null,
                    false,
                    false);
            fixture.event(
                    ctx,
                    privateId,
                    "Geburtstag",
                    today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    86400000,
                    null,
                    true,
                    false);
            fixture.event(
                    ctx,
                    ContentUris.parseId(calendars.get(2)),
                    "Nicht gewählt",
                    nine,
                    3600000,
                    null,
                    false,
                    false);
            inst.getUiAutomation().dropShellPermissionIdentity();
            prefs.edit()
                    .putString("calendars", work + "," + privateId)
                    .putString("template", "{kalender}_{termin}")
                    .putString("recordingFolderMode", "CALENDAR")
                    .putString("recordingBase", "")
                    .putBoolean("autoUpload", false)
                    .commit();
            app.draft.select(null);
            app.draft.name("Eigener Entwurf");
            MainActivity main =
                    (MainActivity)
                            inst.startActivitySync(
                                    new Intent(ctx, MainActivity.class)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            opened.add(main);
            CalendarDayActivity day = open(main);
            opened.add(day);
            View first = view(day, "Projektbesprechung,"), second = view(day, "Musikprobe,");
            view(day, "Geburtstag,");
            assertNull(find(day.getWindow().getDecorView(), "Nicht gewählt,"));
            await(
                    "Überschneidungen sichtbar",
                    () -> {
                        Rect a = new Rect(), b = new Rect();
                        return first.getGlobalVisibleRect(a)
                                && second.getGlobalVisibleRect(b)
                                && a.top == b.top
                                && a.right <= b.left;
                    });
            screenshot(ctx, "tagesansicht-0.2.10.png");
            tap(day, "Abbrechen");
            inst.waitForIdleSync();
            assertEquals("Eigener Entwurf", app.draft.get().name());
            assertNull(app.draft.get().event());
            day = open(main);
            opened.add(day);
            tap(day, "Musikprobe,");
            view(main, "Privat_Musikprobe.ogg");
            assertEquals("Musikprobe", new RecordingDraft(ctx).get().event().title());
            screenshot(ctx, "termin-vorgemerkt-0.2.10.png");
            day = open(main);
            opened.add(day);
            inst.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
            inst.waitForIdleSync();
            view(main, "Privat_Musikprobe.ogg");
            day = open(main);
            opened.add(day);
            tap(day, "Automatisch zuordnen");
            assertNull(app.draft.get().event());
            day = open(main);
            opened.add(day);
            tap(day, "Musikprobe,");
            view(main, "Privat_Musikprobe.ogg");
            tap(main, "Aufnahme starten");
            await("Aufnahme läuft", app::active);
            assertNull(app.draft.get().event());
            SystemClock.sleep(1200);
            tap(main, "Aufnahme stoppen");
            await("Aufnahme gespeichert", () -> app.state == Audomo.State.SAVED);
            var recorded =
                    app.sessions.list().stream()
                            .filter(e -> !previousIds.contains(e.id()))
                            .findFirst()
                            .orElseThrow();
            assertEquals("Privat_Musikprobe.ogg", recorded.name());
            assertEquals("Privat", recorded.folder());
            SessionStore.verify(recorded.file());
            assertNull(new RecordingDraft(ctx).get().event());
            view(main, "Aktueller Kalendertermin ·");
            prefs.edit().putString("template", "Folgeaufnahme").commit();
            tap(main, "Aufnahme starten");
            await("Folgeaufnahme läuft", app::active);
            SystemClock.sleep(800);
            tap(main, "Aufnahme stoppen");
            await("Folgeaufnahme gespeichert", () -> app.state == Audomo.State.SAVED);
            assertTrue(
                    app.sessions.list().stream()
                            .anyMatch(e ->
                                    !previousIds.contains(e.id())
                                            && e.name().equals("Folgeaufnahme.ogg")));
        } finally {
            if (app.active()) {
                app.command("STOP", null);
                await("Testaufnahme beendet", app::ready);
            }
            for (Activity screen : opened)
                inst.runOnMainSync(() -> {
                    if (!screen.isDestroyed()) screen.finish();
                });
            inst.getUiAutomation()
                    .adoptShellPermissionIdentity(
                            Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR);
            try {
                for (Uri cal : calendars)
                    ctx.getContentResolver()
                            .delete(
                                    ContentUris.withAppendedId(table, ContentUris.parseId(cal)),
                                    null,
                                    null);
            } finally {
                inst.getUiAutomation().dropShellPermissionIdentity();
            }
            for (SessionStore.Entry row : app.sessions.list())
                if (!previousIds.contains(row.id())
                        && List.of("Privat_Musikprobe.ogg", "Folgeaufnahme.ogg")
                                .contains(row.name())) {
                    LibraryFiles lib = new LibraryFiles(app.sessions);
                    lib.change(row, null, row.folder(), true);
                    lib.purge(app.sessions.byId(row.id()));
                }
            SharedPreferences.Editor restore = prefs.edit();
            for (String key :
                    List.of(
                            "calendars",
                            "template",
                            "recordingFolderMode",
                            "recordingBase",
                            "folders",
                            "autoUpload")) {
                Object value = previous.get(key);
                if (value instanceof Boolean b) restore.putBoolean(key, b);
                else if (value instanceof String s) restore.putString(key, s);
                else if (value instanceof Set<?> set)
                    restore.putStringSet(key, new HashSet<>((Set<String>) set));
                else restore.remove(key);
            }
            restore.commit();
            app.draft.select(oldDraft.event());
            app.draft.name(oldDraft.name());
        }
    }
}
