package de.audomo;

import static org.junit.Assert.*;

import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.*;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;

@RunWith(AndroidJUnit4.class)
public class MigrationDeviceTest {
    private Audomo app;
    private final char[] password = "nur-fuer-den-emulator".toCharArray();
    private String id, target;
    private byte[] audio;

    @Before
    public void prepare() throws Exception {
        Assume.assumeTrue(
                Build.MODEL.contains("sdk_gphone")
                        && "true"
                                .equals(
                                        InstrumentationRegistry.getArguments()
                                                .getString("migrationQA")));
        var inst = InstrumentationRegistry.getInstrumentation();
        app = (Audomo) inst.getTargetContext().getApplicationContext();
        for (int i = 0; i < 100 && !app.ready(); i++) Thread.sleep(50);
        assertTrue(app.ready());
        id = UUID.randomUUID().toString();
        target = UUID.randomUUID().toString();
        try (InputStream in = inst.getContext().getAssets().open("testsignal.ogg")) {
            audio = in.readAllBytes();
        }
        Profile.prefs(app)
                .edit()
                .putBoolean("cleanTrash", true)
                .putInt("skipSeconds", 15)
                .putStringSet("folders", Set.of("Projekt"))
                .commit();
        new CloudConfig(target, "WebDAV", "example.invalid", 443, "qa", "/fixture", "")
                .save(app, "testzugang-quelle");
    }

    private byte[] save(BackupArchive.Scope scope) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MigrationBackup.export(app, out, scope, password);
        byte[] data = out.toByteArray();
        assertFalse(
                new String(data, java.nio.charset.StandardCharsets.ISO_8859_1)
                        .contains("testzugang-quelle"));
        return data;
    }

    private void load(byte[] b, BackupArchive.Scope scope, char[] key) throws Exception {
        MigrationBackup.restore(app, new ByteArrayInputStream(b), scope, key);
    }

    private File session(String sid, boolean trash) throws Exception {
        File dir = new File(app.sessions.root, sid);
        Files.createDirectories(dir.toPath());
        Files.write(new File(dir, "audio.ogg").toPath(), audio);
        SessionStore.storeMeta(
                dir,
                new JSONObject()
                        .put("name", "audio.ogg")
                        .put("displayName", "Prüfton.ogg")
                        .put("start", "2026-11-25T10:00:00+01:00")
                        .put("frames", 48000)
                        .put("folder", "Projekt")
                        .put("trashed", trash ? System.currentTimeMillis() : 0));
        return dir;
    }

    @Test
    public void settingsOnlyPreservesDestinationSecretsAndLibrary() throws Exception {
        byte[] b = save(BackupArchive.Scope.SETTINGS);
        File existing = session(id, false);
        CloudConfig.seal(app, target, "ziel-passwort");
        Profile.prefs(app)
                .edit()
                .putInt("skipSeconds", 30)
                .putBoolean("cleanTrash", false)
                .commit();
        load(b, BackupArchive.Scope.SETTINGS, password);
        assertEquals(15, Profile.prefs(app).getInt("skipSeconds", 0));
        assertTrue(Profile.prefs(app).getBoolean("cleanTrash", false));
        assertEquals("ziel-passwort", CloudConfig.secret(app, target));
        assertArrayEquals(audio, Files.readAllBytes(new File(existing, "audio.ogg").toPath()));
        assertThrows(IOException.class, () -> load(b, BackupArchive.Scope.CONNECTIONS, password));
    }

    @Test
    public void credentialsTravelToFreshKeystoreAndCanBeExcluded() throws Exception {
        byte[] b = save(BackupArchive.Scope.CONNECTIONS);
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        ks.deleteEntry("audomo.upload.v1");
        app.getSharedPreferences("credentials", 0).edit().clear().commit();
        load(b, BackupArchive.Scope.SETTINGS, password);
        assertFalse(CloudConfig.byId(app, target).hasCredentials(app));
        load(b, BackupArchive.Scope.CONNECTIONS, password);
        assertEquals("testzugang-quelle", CloudConfig.secret(app, target));
        assertTrue(ks.containsAlias("audomo.upload.v1"));
    }

    @Test
    public void fullRestoreKeepsAudioTrashIdentitiesHistoryAndLogs() throws Exception {
        File dir = session(id, false);
        String trashed = UUID.randomUUID().toString();
        session(trashed, true);
        SQLiteDatabase db = UploadQueue.get(app).getWritableDatabase();
        db.execSQL(
                "INSERT INTO recordings(rec,name,folder,start,frames,bytes) VALUES"
                        + " (?,?,?,'2026-11-25T10:00:00+01:00',48000,?)",
                new Object[] {id, "Prüfton.ogg", "Projekt", audio.length});
        String expectedHash = LibraryFiles.digest(new ByteArrayInputStream(audio));
        db.execSQL(
                "INSERT INTO jobs(rec,target,path,state,verified,provider,attempts,inQueue)"
                        + " VALUES(?,?,?,'Hochgeladen',?,'WebDAV',2,0)",
                new Object[] {id, target, "/fixture/Prüfton.ogg", expectedHash});
        app.diagnostic("Migration-Prüfereignis");
        byte[] b = save(BackupArchive.Scope.FULL);
        db.delete("jobs", "rec=?", new String[] {id});
        db.delete("recordings", "rec=?", new String[] {id});
        Files.delete(new File(dir, "audio.ogg").toPath());
        Files.write(new File(app.getFilesDir(), "diagnose.txt").toPath(), new byte[0]);
        String later = UUID.randomUUID().toString();
        session(later, false);
        load(b, BackupArchive.Scope.FULL, password);
        assertFalse(new File(app.sessions.root, later).exists());
        assertArrayEquals(audio, Files.readAllBytes(app.sessions.byId(id).file().toPath()));
        assertTrue(app.sessions.byId(trashed).trashed() > 0);
        assertEquals("Projekt", app.sessions.byId(id).folder());
        assertTrue(
                new String(Files.readAllBytes(new File(app.getFilesDir(), "diagnose.txt").toPath()))
                        .contains("Migration-Prüfereignis"));
        try (var c =
                db.rawQuery(
                        "SELECT verified,attempts,inQueue FROM jobs WHERE rec=?",
                        new String[] {id})) {
            assertTrue(c.moveToFirst());
            assertEquals(expectedHash, c.getString(0));
            assertEquals(2, c.getInt(1));
            assertEquals(0, c.getInt(2));
        }
        assertEquals("testzugang-quelle", CloudConfig.secret(app, target));
        load(b, BackupArchive.Scope.FULL, password);
        assertEquals("Prüfton.ogg", app.sessions.byId(id).name());
    }

    @Test
    public void failedCommitRestoresPreviousLibrarySettingsAndLog() throws Exception {
        session(id, false);
        byte[] archive = save(BackupArchive.Scope.FULL);
        String newer = UUID.randomUUID().toString();
        session(newer, true);
        Profile.prefs(app).edit().putInt("skipSeconds", 30).commit();
        app.diagnostic("Rücknahme-Prüfereignis");
        SQLiteDatabase db = UploadQueue.get(app).getWritableDatabase();
        db.execSQL("CREATE TABLE IF NOT EXISTS migration_commit(id TEXT PRIMARY KEY)");
        db.execSQL(
                "CREATE TRIGGER qa_restore BEFORE INSERT ON migration_commit BEGIN SELECT"
                        + " RAISE(ABORT,'QA'); END");
        try {
            assertThrows(Exception.class, () -> load(archive, BackupArchive.Scope.FULL, password));
            assertArrayEquals(audio, Files.readAllBytes(app.sessions.byId(newer).file().toPath()));
            assertTrue(app.sessions.byId(newer).trashed() > 0);
            assertEquals(30, Profile.prefs(app).getInt("skipSeconds", 0));
            assertTrue(
                    new String(
                                    Files.readAllBytes(
                                            new File(app.getFilesDir(), "diagnose.txt").toPath()))
                            .contains("Rücknahme-Prüfereignis"));
            assertFalse(new File(app.getNoBackupFilesDir(), "migration-pending-v1").exists());
        } finally {
            db.execSQL("DROP TRIGGER qa_restore");
        }
    }

    @Test
    public void audioCrossesEncryptionSegmentsAndLowerImportAuthenticatesTheWholeArchive()
            throws Exception {
        File original = session(id, false);
        byte[] samples = new byte[3 * 1024 * 1024 + 479];
        new java.util.Random(14034).nextBytes(samples);
        Files.write(new File(original, "audio.ogg").toPath(), samples);
        byte[] archive = save(BackupArchive.Scope.FULL);
        byte[] damaged = archive.clone();
        damaged[2 * 1024 * 1024] ^= 1;
        Profile.prefs(app).edit().putInt("skipSeconds", 30).commit();
        assertThrows(
                IOException.class, () -> load(damaged, BackupArchive.Scope.SETTINGS, password));
        assertEquals(30, Profile.prefs(app).getInt("skipSeconds", 0));
        Files.write(new File(original, "audio.ogg").toPath(), audio);
        load(archive, BackupArchive.Scope.FULL, password);
        assertArrayEquals(samples, Files.readAllBytes(app.sessions.byId(id).file().toPath()));
    }

    @Test
    public void damagedOrWrongPasswordMakesNoChanges() throws Exception {
        byte[] b = save(BackupArchive.Scope.FULL);
        Profile.prefs(app).edit().putInt("skipSeconds", 30).commit();
        File previous = session(id, false);
        assertThrows(
                Exception.class,
                () -> load(b, BackupArchive.Scope.FULL, "falsches-passwort".toCharArray()));
        assertThrows(
                Exception.class,
                () -> load(Arrays.copyOf(b, b.length - 3), BackupArchive.Scope.FULL, password));
        b[b.length - 1] ^= 1;
        assertThrows(Exception.class, () -> load(b, BackupArchive.Scope.FULL, password));
        assertEquals(30, Profile.prefs(app).getInt("skipSeconds", 0));
        assertArrayEquals(audio, Files.readAllBytes(new File(previous, "audio.ogg").toPath()));
    }
}
