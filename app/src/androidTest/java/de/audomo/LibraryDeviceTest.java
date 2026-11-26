package de.audomo;

import static org.junit.Assert.*;

import android.content.*;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@RunWith(AndroidJUnit4.class)
public class LibraryDeviceTest {
    Context ctx;
    File workspace;
    String prefix;
    SessionStore store;
    LibraryFiles lib;
    final List<Uri> sources = new ArrayList<>();

    @Before
    public void setup() throws Exception {
        Context real = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefix = "qa-" + UUID.randomUUID();
        workspace = new File(real.getCacheDir(), prefix);
        Files.createDirectory(workspace.toPath());
        ctx =
                new ContextWrapper(real) {
                    @Override
                    public Context getApplicationContext() {
                        return this;
                    }

                    @Override
                    public File getFilesDir() {
                        return workspace;
                    }

                    @Override
                    public SharedPreferences getSharedPreferences(String name, int mode) {
                        return super.getSharedPreferences(prefix + name, mode);
                    }
                };
        store = new SessionStore(ctx);
        lib = new LibraryFiles(store);
    }

    @After
    public void cleanup() throws Exception {
        for (Uri uri : sources) ctx.getContentResolver().delete(uri, null, null);
        for (String key : List.of("options", "installation", "credentials"))
            ((ContextWrapper) ctx).getBaseContext().deleteSharedPreferences(prefix + key);
        try (var paths = Files.walk(workspace.toPath())) {
            for (Path path :
                    paths.sorted(Comparator.reverseOrder())
                            .collect(java.util.stream.Collectors.toList()))
                Files.deleteIfExists(path);
        }
    }

    private Uri source(String name, byte[] audio) throws Exception {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Audio.Media.DISPLAY_NAME, name);
        values.put(
                MediaStore.Audio.Media.MIME_TYPE,
                name.endsWith("ogg") ? "audio/ogg" : "audio/mpeg");
        values.put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/" + prefix);
        values.put(MediaStore.Audio.Media.IS_PENDING, 1);
        Uri uri =
                ctx.getContentResolver()
                        .insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);
        sources.add(uri);
        try (OutputStream out = ctx.getContentResolver().openOutputStream(uri)) {
            out.write(audio);
        }
        values.clear();
        values.put(MediaStore.Audio.Media.IS_PENDING, 0);
        ctx.getContentResolver().update(uri, values, null, null);
        return uri;
    }

    byte[] asset(String n) throws Exception {
        try (InputStream in =
                InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(n)) {
            return in.readAllBytes();
        }
    }

    @Test
    public void importRenameCopyMoveTrashRestorePreservesBytesAndTime() throws Exception {
        byte[] bytes = asset("testsignal.mp3");
        Uri source = source("Altaufnahme.mp3", bytes);
        lib.importUri(source, "");
        SessionStore.Entry original = store.list().get(0);
        assertTrue(original.frames() > 90000);
        assertThrows(IOException.class, () -> lib.importUri(source, ""));
        Uri ogg = source("Altaufnahme.ogg", asset("testsignal.ogg"));
        lib.importUri(ogg, "");
        Uri bad = source("Defekt.mp3", "keine Audioframes".getBytes(StandardCharsets.UTF_8));
        assertThrows(Exception.class, () -> lib.importUri(bad, ""));
        byte[] damaged = asset("testsignal.ogg");
        damaged[damaged.length - 2] ^= 3;
        Uri corrupt = source("Schadhaft.ogg", damaged);
        assertThrows(IOException.class, () -> lib.importUri(corrupt, ""));
        assertEquals(2, store.list().size());
        String folder = lib.addFolder("", "Gespräche");
        lib.change(original, "Sitzung", folder, false);
        SessionStore.Entry renamed = find(original.id());
        assertEquals("Sitzung.mp3", renamed.name());
        lib.copy(renamed, folder);
        assertTrue(store.list().stream().anyMatch(e -> e.name().equals("Sitzung (2).mp3")));
        lib.change(renamed, null, folder, true);
        SessionStore.Entry trashed = find(original.id());
        assertTrue(trashed.trashed() > 0);
        lib.cleanOld();
        assertTrue(find(original.id()).file().isFile());
        lib.copy(
                store.list().stream()
                        .filter(e -> e.name().equals("Sitzung (2).mp3"))
                        .findFirst()
                        .get(),
                folder);
        lib.change(trashed, null, "", false);
        SessionStore.Entry restored = find(original.id());
        assertEquals(original.start(), restored.start());
        assertEquals(original.frames(), restored.frames());
        assertEquals(0, restored.trashed());
        assertArrayEquals(bytes, Files.readAllBytes(restored.file().toPath()));
        try (InputStream in = ctx.getContentResolver().openInputStream(source)) {
            assertArrayEquals(bytes, in.readAllBytes());
        }
        assertThrows(IOException.class, () -> lib.purge(restored));
        lib.change(restored, null, "", true);
        lib.purge(find(restored.id()));
        assertFalse(restored.file().exists());
    }

    @Test
    public void damagedEntryDoesNotBlockHealthyAudioOrNames() throws Exception {
        File broken = new File(store.root, "previous-damaged");
        Files.createDirectories(broken.toPath());
        Files.write(new File(broken, "session.json").toPath(), new byte[] {1, 2, 3});
        File previous = new File(broken, "Prüfung.ogg");
        Files.write(previous.toPath(), asset("testsignal.ogg"));
        lib.importUri(source("Prüfung.ogg", asset("testsignal.ogg")), "");
        assertEquals(1, store.list().size());
        assertEquals("Prüfung (2).ogg", store.list().get(0).name());
        assertArrayEquals(asset("testsignal.ogg"), Files.readAllBytes(previous.toPath()));
    }

    SessionStore.Entry find(String id) throws Exception {
        return new SessionStore(ctx)
                .list().stream().filter(e -> e.id().equals(id)).findFirst().get();
    }

    @Test
    public void legacyNamesAndAutomaticRetentionUseDeletionTime() throws Exception {
        lib.importUri(source("Prüfung.ogg", asset("testsignal.ogg")), "");
        SessionStore.Entry a = store.list().get(0);
        File dir = a.file().getParentFile();
        JSONObject m = SessionStore.metadata(dir);
        m.remove("displayName");
        m.remove("folder");
        m.remove("trashed");
        SessionStore.storeMeta(dir, m);
        assertEquals(a.file().getName(), find(a.id()).name());
        lib.change(find(a.id()), null, "", true);
        m = SessionStore.metadata(dir);
        m.put("trashed", System.currentTimeMillis() - 31L * 86400000);
        SessionStore.storeMeta(dir, m);
        lib.cleanOld();
        assertTrue(a.file().exists());
        lib.prefs.edit().putBoolean("cleanTrash", true).commit();
        lib.cleanOld();
        assertFalse(a.file().exists());
    }

    @Test
    public void renameAndDeleteRespectSelectionAndExistingBuiltinOverrides() throws Exception {
        Profile meeting = Profile.standard("Besprechung", 64);
        Profile.save(ctx, meeting, "Besprechung");
        Profile.save(ctx, Profile.standard("Probe", 96), null);
        Profile renamed = Profile.standard("Außentermin", 96);
        Profile.save(ctx, renamed, "Probe");
        assertEquals(renamed, Profile.selected(ctx));
        assertFalse(Profile.profiles(ctx).stream().anyMatch(p -> p.name().equals("Probe")));
        assertThrows(IllegalArgumentException.class, () -> Profile.save(ctx, meeting, renamed.name()));
        assertThrows(IllegalArgumentException.class, () -> Profile.save(ctx, renamed, meeting.name()));
        assertThrows(IllegalArgumentException.class, () -> Profile.delete(ctx, meeting.name()));
        assertEquals(renamed, Profile.selected(ctx));
        Profile.save(ctx, Profile.standard("Besuch", 48), null);
        Profile.delete(ctx, renamed.name());
        assertEquals("Besuch", Profile.selected(ctx).name());
        Profile.delete(ctx, "Besuch");
        assertEquals(meeting, Profile.selected(ctx));
        assertEquals(3, Profile.profiles(ctx).size());
    }

    @Test
    public void profileAndBackupRoundTripAndRejectInvalidSchema() throws Exception {
        assertEquals(
                List.of(48, 80, 32),
                Profile.profiles(ctx).stream()
                        .map(Profile::kbps)
                        .collect(java.util.stream.Collectors.toList()));
        Profile.save(ctx, Profile.standard("Besprechung", 96), "Besprechung");
        Profile.save(ctx, Profile.standard("Draußen", 32), null);
        assertEquals(4, Profile.profiles(ctx).size());
        assertEquals(96, Profile.profiles(ctx).get(0).kbps());
        lib.prefs
                .edit()
                .putString("template", "{kalender}_{datum}")
                .putString("recordingFolderMode", "CALENDAR")
                .putString("recordingBase", "Archiv/Termine")
                .putString(UploadDelay.KEY, "0.5")
                .putString("calendars", "11,29")
                .putInt("before", 17)
                .commit();
        CloudConfig t =
                new CloudConfig(
                        UUID.randomUUID().toString(),
                        "WebDAV",
                        "example.invalid",
                        443,
                        "qa",
                        "/test",
                        "");
        t.save(ctx, "synthetic-password-do-not-export");
        byte[] backup = SettingsBackup.export(ctx);
        String raw = new String(backup, StandardCharsets.UTF_8);
        assertFalse(raw.contains("synthetic-password"));
        assertFalse(raw.contains("content://"));
        assertFalse(
                ctx.getSharedPreferences("credentials", 0)
                        .getString(t.id(), "")
                        .contains("synthetic-password"));
        assertEquals("synthetic-password-do-not-export", CloudConfig.secret(ctx, t.id()));
        lib.prefs.edit().clear().commit();
        SettingsBackup.restore(ctx, new ByteArrayInputStream(backup));
        assertEquals(17, lib.prefs.getInt("before", 0));
        assertEquals(31_000, UploadDelay.notBefore(lib.prefs, 1_000));
        assertEquals("0,5", UploadDelay.display(lib.prefs));
        assertEquals(List.of(11L, 29L), CalendarNames.ids(ctx));
        assertEquals(CalendarNames.FolderMode.CALENDAR, CalendarNames.folderMode(ctx));
        assertEquals("Archiv/Termine", CalendarNames.baseFolder(ctx, CalendarNames.folderMode(ctx)));
        assertEquals("{kalender}_{datum}", CalendarNames.template(ctx));
        assertEquals("Draußen", Profile.selected(ctx).name());
        assertEquals(1, CloudConfig.all(ctx).size());
        assertFalse(lib.prefs.getBoolean("autoUpload", true));
        JSONObject edited = new JSONObject(raw);
        edited.getJSONObject("audomo").put("installation", "other");
        SettingsBackup.restore(
                ctx, new ByteArrayInputStream(edited.toString().getBytes(StandardCharsets.UTF_8)));
        assertTrue(CalendarNames.ids(ctx).isEmpty());
        edited.getJSONObject("audomo").remove("recordingFolderMode");
        edited.getJSONObject("audomo").remove("recordingBase");
        edited.getJSONObject("audomo").remove(UploadDelay.KEY);
        SettingsBackup.restore(ctx, new ByteArrayInputStream(edited.toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(CalendarNames.FolderMode.CURRENT, CalendarNames.folderMode(ctx));
        assertEquals(0, UploadDelay.notBefore(lib.prefs, 1_000));
        edited.put("schema", 999);
        assertThrows(
                IOException.class,
                () ->
                        SettingsBackup.restore(
                                ctx,
                                new ByteArrayInputStream(
                                        edited.toString().getBytes(StandardCharsets.UTF_8))));
    }
}
