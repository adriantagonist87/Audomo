package de.audomo;

import static org.junit.Assert.*;

import android.content.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.*;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;

@RunWith(AndroidJUnit4.class)
public class CloudCatalogDeviceTest {
    @Test
    public void folderTreeAndCloudOnlyRecordingSurviveMovesPurgesAndExternalChanges()
            throws Exception {
        var inst = InstrumentationRegistry.getInstrumentation();
        Assume.assumeTrue(
                android.os.Build.MODEL.contains("sdk_gphone")
                        && "true"
                                .equals(
                                        InstrumentationRegistry.getArguments()
                                                .getString("localServers")));
        Audomo app = (Audomo) inst.getTargetContext().getApplicationContext();
        SessionStore store = app.sessions;
        CloudLibrary catalog = new CloudLibrary(store);
        FolderManager folders = new FolderManager(store);
        UploadQueue queue = catalog.queue;
        var prefs = Profile.prefs(app);
        String id = UUID.randomUUID().toString(),
                top = "Test-" + id.substring(0, 8),
                child = top + "/Alt";
        JSONObject fixture =
                new JSONObject(
                        new String(
                                Files.readAllBytes(
                                        new File(app.getCacheDir(), "qa-protocols.json").toPath()),
                                java.nio.charset.StandardCharsets.UTF_8));
        String secret = fixture.getString("password");
        CloudConfig t =
                new CloudConfig(
                        UUID.randomUUID().toString(),
                        "FTP",
                        fixture.getString("host"),
                        fixture.getJSONObject("ports").getInt("FTP"),
                        "qa",
                        "/" + top,
                        "");
        String oldTargets = prefs.getString("targets", "[]"),
                defaultId = prefs.getString("defaultTarget", "");
        String oldFolder = prefs.getString("folder", ""),
                oldBase = prefs.getString("recordingBase", "");
        Set<String> oldFolders = new HashSet<>(folders.files.folders());
        File dir = new File(store.root, id);
        File audio = new File(dir, "audio.ogg");
        File original = File.createTempFile("catalog-", ".ogg", app.getCacheDir());
        try (InputStream in = inst.getContext().getAssets().open("testsignal.ogg")) {
            Files.copy(in, original.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        try (RemoteFiles ftp = RemoteFiles.open(t, secret, CloudLibrary.network(app), n -> {})) {
            ftp.ensureFolder(t.base());
            t.save(app, secret);
            folders.files.ensureFolder(child);
            folders.files.ensureFolder(top + "/Ziel");
            prefs.edit().putString("folder", child).putString("recordingBase", child).commit();
            Files.createDirectories(dir.toPath());
            Files.copy(original.toPath(), audio.toPath());
            SessionStore.storeMeta(
                    dir,
                    new JSONObject()
                            .put("name", "audio.ogg")
                            .put("displayName", "Probe.ogg")
                            .put("folder", child)
                            .put("frames", 48317)
                            .put("start", OffsetDateTime.now().toString()));
            queue.enqueueSelected(store, List.of(id), t);
            UploadQueueDeviceTest driver = new UploadQueueDeviceTest();
            driver.drive(queue, id, "Hochgeladen");
            String first = queue.job(id).path(), hash = queue.job(id).verified();
            folders.change(child, top, "Neu", false, false);
            assertEquals(first, queue.job(id).path());
            assertEquals(top + "/Neu", prefs.getString("recordingBase", ""));
            assertEquals(top + "/Neu", prefs.getString("folder", ""));
            folders.change(top + "/Neu", top + "/Ziel", "Neu", false, true);
            driver.drive(queue, id, "Hochgeladen");
            assertNull(ftp.hash(first));
            String mirrored = queue.job(id).path();
            assertEquals(t.base() + "/" + top + "/Ziel/Neu/Probe.ogg", mirrored);
            assertEquals(hash, ftp.hash(mirrored));
            assertThrows(
                    IOException.class,
                    () -> folders.change(top, top + "/Ziel", "Schleife", false, false));
            folders.change(top + "/Ziel", top, "", true, false);
            assertEquals(top + "/Neu", store.byId(id).folder());
            folders.files.change(store.byId(id), null, top + "/Neu", true);
            folders.files.purge(store.byId(id));
            assertFalse(dir.exists());
            assertTrue(catalog.known().get(id).cloudOnly());
            assertEquals(original.length(), catalog.known().get(id).bytes());
            folders.change(top + "/Neu", top, "Archiv", false, true);
            driver.drive(queue, id, "Hochgeladen");
            assertNull(ftp.hash(mirrored));
            String inCloud = queue.job(id).path(), moved = t.base() + "/Unbekannt/Neu.ogg";
            ftp.ensureFolder(t.base() + "/Unbekannt");
            ftp.put(original, moved, false, null);
            ftp.deleteIfMatches(inCloud, hash);
            ftp.put(original, t.base() + "/Zweite.ogg", false, null);
            CloudReconcile.run(store, t);
            assertEquals("Zuordnung mehrdeutig", queue.job(id).availability());
            assertEquals(inCloud, queue.job(id).path());
            ftp.deleteIfMatches(t.base() + "/Zweite.ogg", hash);
            CloudReconcile.run(store, t);
            assertEquals(moved, queue.job(id).path());
            assertEquals("", queue.job(id).availability());
            SessionStore.Entry ghost = catalog.known().get(id);
            assertEquals("Unbekannt", ghost.folder());
            // An off-device rename must keep the reconciled directory, even after clearing the queue.
            queue.remove(List.of(queue.job(id).id()));
            assertFalse(queue.job(id).inQueue());
            String collision = t.base() + "/Unbekannt/Besetzt.ogg";
            ftp.put(original, collision, false, null);
            folders.files.rename(ghost, "Besetzt", false, List.of(queue.job(id)));
            driver.drive(queue, id, "Konflikt");
            assertEquals(hash, ftp.hash(moved));
            assertEquals(hash, ftp.hash(collision));
            assertEquals(ghost.name(), catalog.known().get(id).name());
            queue.resolve(queue.job(id), 2);
            assertEquals(moved, queue.job(id).path());
            ftp.deleteIfMatches(collision, hash);
            folders.files.rename(ghost, "Ergebnis.ogg", false, List.of(queue.job(id)));
            driver.drive(queue, id, "Hochgeladen");
            assertNull(ftp.hash(moved));
            moved = t.base() + "/Unbekannt/Ergebnis.ogg";
            assertEquals(moved, queue.job(id).path());
            assertEquals(hash, ftp.hash(moved));
            ghost = catalog.known().get(id);
            assertEquals("Ergebnis.ogg", ghost.name());
            assertNull(store.byId(id));
            // Simulate a lost response after the server completed the move. Recovery only reads.
            String afterDisconnect = t.base() + "/Unbekannt/Wiedergefunden.ogg";
            ftp.renameFile(moved, afterDisconnect, hash);
            synchronized (queue) {
                folders.files.rename(ghost, "Wiedergefunden", false, List.of(queue.job(id)));
                queue.sent(queue.job(id).id());
            }
            driver.drive(queue, id, "Hochgeladen");
            assertEquals(afterDisconnect, queue.job(id).path());
            assertEquals("Wiedergefunden.ogg", catalog.known().get(id).name());
            assertEquals(hash, ftp.hash(afterDisconnect));
            moved = afterDisconnect;
            ghost = catalog.known().get(id);
            ftp.deleteIfMatches(moved, hash);
            CloudReconcile.run(store, t);
            assertEquals("Cloudkopie fehlt", queue.job(id).availability());
            assertTrue(catalog.known().containsKey(id));
            File foreign = File.createTempFile("changed-", ".ogg", app.getCacheDir());
            try {
                Files.write(foreign.toPath(), new byte[] {8, 9, 10});
                ftp.put(foreign, moved, false, null);
                CloudReconcile.run(store, t);
                assertEquals("Cloudkopie geändert", queue.job(id).availability());
                SessionStore.Entry missing = ghost;
                assertThrows(IOException.class, () -> catalog.restore(missing, queue.job(id)));
                assertNull(store.byId(id));
                ftp.deleteIfMatches(moved, LibraryFiles.digest(new FileInputStream(foreign)));
            } finally {
                Files.deleteIfExists(foreign.toPath());
            }
            ftp.put(original, moved, false, null);
            CloudReconcile.run(store, t);
            // A full backup must fetch the only remaining copy and verify it before reporting success.
            char[] password = "emulator-sicherung".toCharArray();
            java.io.ByteArrayOutputStream backup = new java.io.ByteArrayOutputStream();
            MigrationBackup.export(app,backup,BackupArchive.Scope.FULL,password);
            assertNull(store.byId(id));
            MigrationBackup.restore(app,new java.io.ByteArrayInputStream(backup.toByteArray()),BackupArchive.Scope.FULL,password);
            assertEquals(hash,LibraryFiles.digest(new FileInputStream(store.byId(id).file())));
            folders.files.change(store.byId(id),null,ghost.folder(),true);
            folders.files.purge(store.byId(id));
            catalog.restore(ghost, queue.job(id));
            assertEquals(hash, LibraryFiles.digest(new FileInputStream(store.byId(id).file())));
            assertEquals(1, catalog.library().stream().filter(e -> e.id().equals(id)).count());
            ftp.deleteIfMatches(moved, hash);
            CloudReconcile.run(store, t);
            assertEquals(1, queue.enqueueSelected(store, List.of(id), t));
            driver.drive(queue, id, "Hochgeladen");
            assertEquals(moved, queue.job(id).path());
            assertEquals(hash, ftp.hash(moved));
            CloudConfig second = new CloudConfig(UUID.randomUUID().toString(), "FTP", t.host(),
                    t.port(), "qa", t.base() + "/Zweitablage", "");
            ftp.ensureFolder(second.base());
            second.save(app, secret);
            try {
                queue.enqueueSelected(store, List.of(id), second);
                driver.drive(queue, id, "Hochgeladen");
                UploadQueue.Job other = queue.job(id);
                assertEquals(second.id(), other.target());
                assertEquals(hash, ftp.hash(other.path()));
                SessionStore.Entry local = store.byId(id);
                assertThrows(IllegalArgumentException.class, () -> folders.files.rename(local, "a/b", true, List.of()));
                folders.files.rename(local, "Nur auf dem Gerät", true, List.of());
                assertEquals("Nur auf dem Gerät.ogg", store.byId(id).name());
                assertEquals(hash, ftp.hash(moved));
                UploadQueue.Job firstCopy = queue.jobs().stream().filter(j -> j.rec().equals(id)
                        && j.target().equals(t.id())).findFirst().orElseThrow();
                folders.files.rename(store.byId(id), "Gemeinsam", true, List.of(firstCopy));
                driver.driveJob(queue, firstCopy.id(), "Hochgeladen");
                assertEquals("Gemeinsam.ogg", store.byId(id).name());
                assertEquals(t.base() + "/Unbekannt/Gemeinsam.ogg", queue.byId(firstCopy.id()).path());
                assertNull(ftp.hash(moved));
                assertEquals(other, queue.byId(other.id()));
                assertEquals(hash, ftp.hash(other.path()));
                ftp.deleteIfMatches(queue.byId(firstCopy.id()).path(), hash);
                ftp.deleteIfMatches(other.path(), hash);
            } finally {
                app.getSharedPreferences("credentials", 0).edit().remove(second.id()).commit();
            }
        } finally {
            queue.getWritableDatabase().delete("jobs", "rec=?", new String[] {id});
            queue.getWritableDatabase().delete("recordings", "rec=?", new String[] {id});
            app.getSharedPreferences("credentials", 0).edit().remove(t.id()).commit();
            prefs.edit()
                    .putString("targets", oldTargets)
                    .putString("defaultTarget", defaultId)
                    .putStringSet("folders", oldFolders)
                    .putString("folder", oldFolder)
                    .putString("recordingBase", oldBase)
                    .remove("pendingFolderChange")
                    .commit();
            if (dir.isDirectory())
                try (var tree = Files.walk(dir.toPath())) {
                    for (Path p :
                            tree.sorted(Comparator.reverseOrder())
                                    .collect(java.util.stream.Collectors.toList())) Files.delete(p);
                }
            Files.deleteIfExists(original.toPath());
        }
    }
}
