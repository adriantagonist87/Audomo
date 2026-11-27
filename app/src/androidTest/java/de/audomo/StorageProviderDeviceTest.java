package de.audomo;

import static org.junit.Assert.*;

import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;

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
public class StorageProviderDeviceTest {
    @Test
    public void verifiedCopyMoveRestoreAndIndexMarker() throws Exception {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        String selected =
                ctx.getContentResolver().getPersistedUriPermissions().stream()
                        .filter(grant -> grant.isReadPermission() && grant.isWritePermission())
                        .map(grant -> grant.getUri().toString())
                        .filter(uri -> uri.endsWith("Audomo-QA-020"))
                        .findFirst()
                        .orElse("");
        Assume.assumeTrue(
                "Nur der im Emulator freigegebene QA-Ordner",
                android.os.Build.MODEL.contains("sdk_gphone")
                        && selected.endsWith("Audomo-QA-020"));
        Uri tree = Uri.parse(selected);
        File temp = Files.createTempDirectory(ctx.getCacheDir().toPath(), "qa-saf-").toFile();
        String prefix = "qa-saf-" + UUID.randomUUID();
        Context individual =
                new ContextWrapper(ctx) {
                    public File getFilesDir() {
                        return temp;
                    }

                    public Context getApplicationContext() {
                        return this;
                    }

                    public SharedPreferences getSharedPreferences(String n, int mode) {
                        return ctx.getSharedPreferences(prefix + n, mode);
                    }
                };
        SessionStore store = new SessionStore(individual);
        LibraryFiles lib = new LibraryFiles(store);
        File dir = new File(store.root, UUID.randomUUID().toString());
        Files.createDirectories(dir.toPath());
        File source = new File(dir, "Prüfung.ogg");
        try (InputStream in =
                InstrumentationRegistry.getInstrumentation()
                        .getContext()
                        .getAssets()
                        .open("testsignal.ogg")) {
            Files.copy(in, source.toPath());
        }
        String hash = LibraryFiles.digest(new FileInputStream(source));
        String folder = lib.addFolder("", prefix);
        try {
            DocumentStorage.select(individual, tree);
            DocumentStorage.hide(individual, true);
            assertTrue(lib.prefs.getBoolean("hidden", false));
            DocumentStorage.hide(individual, false);
            JSONObject meta =
                    new JSONObject()
                            .put("name", source.getName())
                            .put("documentTree", selected)
                            .put("start", OffsetDateTime.now().toString())
                            .put("frames", 48317)
                            .put("folder", folder);
            SessionStore.storeMeta(dir, meta);
            DocumentStorage.deliver(individual, source);
            Uri first = Uri.parse(SessionStore.metadata(dir).getString("deliveredUri"));
            assertEquals(
                    hash, LibraryFiles.digest(ctx.getContentResolver().openInputStream(first)));
            JSONObject detached = SessionStore.metadata(dir);
            detached.remove("deliveredUri");
            detached.remove("documentTree");
            detached.put("previousDocumentStorage",new JSONObject().put("documentTree",selected));
            SessionStore.storeMeta(dir,detached);
            DocumentStorage.relink(store,tree);
            assertFalse(SessionStore.metadata(dir).has("previousDocumentStorage"));
            DocumentStorage.deliver(individual, source);
            assertEquals(first.toString(), SessionStore.metadata(dir).getString("deliveredUri"));
            lib.rename(store.list().get(0), "Sicherung", true, List.of());
            Uri next = Uri.parse(SessionStore.metadata(dir).getString("deliveredUri"));
            assertNotEquals(first, next);
            assertEquals(hash, LibraryFiles.digest(ctx.getContentResolver().openInputStream(next)));
            lib.change(store.list().get(0), null, folder, true);
            assertFalse(SessionStore.metadata(dir).has("deliveredUri"));
            lib.change(store.list().get(0), null, folder, false);
            assertEquals(
                    hash,
                    LibraryFiles.digest(
                            ctx.getContentResolver()
                                    .openInputStream(
                                            Uri.parse(
                                                    SessionStore.metadata(dir)
                                                            .getString("deliveredUri")))));
            lib.change(store.list().get(0), null, folder, true);
            lib.purge(store.list().get(0));
        } finally {
            Uri dirUri =
                    DocumentsContract.buildDocumentUriUsingTree(
                            tree, DocumentsContract.getTreeDocumentId(tree) + "/" + prefix);
            try {
                DocumentsContract.deleteDocument(ctx.getContentResolver(), dirUri);
            } catch (FileNotFoundException ignored) {
            }
            ctx.deleteSharedPreferences(prefix + "options");
            try (var paths = Files.walk(temp.toPath())) {
                for (Path f :
                        paths.sorted(Comparator.reverseOrder())
                                .collect(java.util.stream.Collectors.toList()))
                    Files.deleteIfExists(f);
            }
        }
    }
}
