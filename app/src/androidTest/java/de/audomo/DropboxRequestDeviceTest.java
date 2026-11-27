package de.audomo;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import okhttp3.*;

import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.*;
import java.util.*;

@RunWith(AndroidJUnit4.class)
public class DropboxRequestDeviceTest {
    @Test
    public void restoresUncataloguedDropboxTreeAndRetainsIdentityOnRepeat() throws Exception {
        var inst = InstrumentationRegistry.getInstrumentation();
        var ctx = inst.getTargetContext();
        var app = (Audomo) ctx.getApplicationContext();
        var store = app.sessions;
        var prefs = Profile.prefs(ctx);
        var queue = UploadQueue.get(ctx);
        String prefix = "Abruf-" + UUID.randomUUID();
        String originalTargets = prefs.getString("targets", "[]");
        String originalDefault = prefs.getString("defaultTarget", "");
        Set<String> originalFolders = new HashSet<>(prefs.getStringSet("folders", Set.of()));
        CloudConfig target = new CloudConfig(UUID.randomUUID().toString(), "Dropbox", "qa", 443, "", "/Projekt", "");
        assertEquals("Dropbox/Projekt", target.label());
        assertEquals("Dropbox/", new CloudConfig(target.id(), "Dropbox", "qa", 443, "", "/", "").uploadLabel());
        String relative = prefix + "/Tag";
        String path = "/Projekt/" + relative + "/Gespräch.ogg";
        byte[] signal;
        try (InputStream in = inst.getContext().getAssets().open("testsignal.ogg")) { signal = in.readAllBytes(); }
        JSONObject metadata = new JSONObject().put(".tag", "file").put("path_display", path).put("rev", "1234567890ab")
                .put("size", signal.length).put("client_modified", "2026-10-03T13:00:00Z")
                .put("content_hash", "d5e080942c1416635c4bfc9ce16f12d1e473f245c72172e69c55165be3fa198b");
        int[] downloads = {0};
        OkHttpClient transport = new OkHttpClient.Builder().addInterceptor(chain -> {
            var r = chain.request();
            ResponseBody result;
            try {
                switch (r.url().encodedPath()) {
                    case "/oauth2/token" -> result = ResponseBody.create(null, "{\"access_token\":\"fixture\"}");
                    case "/2/files/list_folder" -> {
                        okio.Buffer request = new okio.Buffer();
                        r.body().writeTo(request);
                        JSONObject args = new JSONObject(request.readUtf8());
                        assertEquals("/Projekt", args.getString("path"));
                        assertTrue(args.getBoolean("recursive"));
                        result = ResponseBody.create(null, new JSONObject().put("has_more", true).put("cursor", "next")
                                .put("entries", new JSONArray().put(new JSONObject().put(".tag","folder")
                                        .put("path_display","/Projekt/" + prefix + "/Leer"))).toString());
                    }
                    case "/2/files/list_folder/continue" -> result = ResponseBody.create(null, new JSONObject().put("has_more", false)
                            .put("entries", new JSONArray().put(metadata)).toString());
                    case "/2/files/download" -> {
                        assertEquals("rev:1234567890ab", new JSONObject(r.header("Dropbox-API-Arg")).getString("path"));
                        downloads[0]++;
                        result = ResponseBody.create(null, signal);
                    }
                    default -> throw new AssertionError(r.url());
                }
            } catch (JSONException e) { throw new IOException(e); }
            return new Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(result).build();
        }).build();
        String rec = null;
        try (DropboxFiles remote = new DropboxFiles(target, "synthetic", n -> {}, transport)) {
            target.save(ctx, "");
            DropboxRestore restore = new DropboxRestore(store, remote);
            var first = restore.run(n -> {});
            assertTrue(first.summary(), first.skipped().isEmpty());
            assertEquals(1, first.added());
            assertTrue(new LibraryFiles(store).folders().contains(prefix + "/Leer"));
            var job = queue.jobs().stream().filter(j -> j.target().equals(target.id())).findFirst().orElseThrow();
            rec = job.rec();
            assertFalse(job.inQueue());
            assertEquals(path, job.confirmedPath());
            var local = store.byId(rec);
            assertEquals(relative, local.folder());
            assertArrayEquals(signal, java.nio.file.Files.readAllBytes(local.file().toPath()));
            assertTrue(SessionStore.metadata(local.file().getParentFile()).getBoolean("autoQueued"));
            // A pre-existing local import without Dropbox history must also keep its UUID.
            queue.getWritableDatabase().delete("jobs", "id=?", new String[]{Long.toString(job.id())});
            queue.getWritableDatabase().delete("recordings", "rec=?", new String[]{rec});
            assertEquals(1, restore.run(n -> {}).present());
            assertEquals(rec, queue.jobs().stream().filter(j -> j.target().equals(target.id())).findFirst().orElseThrow().rec());
            assertEquals(1, restore.run(n -> {}).present());
            assertEquals(1, downloads[0]);
            java.nio.file.Files.delete(local.file().toPath());
            assertEquals(1, restore.run(n -> {}).added());
            assertArrayEquals(signal, java.nio.file.Files.readAllBytes(store.byId(rec).file().toPath()));
            assertEquals(2, downloads[0]);
            // A same-name local recording with different bytes must survive an import.
            byte[] different = signal.clone(); different[10] ^= 1;
            java.nio.file.Files.write(local.file().toPath(), different);
            assertEquals(1, restore.run(n -> {}).skipped().size());
            assertArrayEquals(different, java.nio.file.Files.readAllBytes(local.file().toPath()));
            java.nio.file.Files.delete(local.file().toPath());
            metadata.put("content_hash", "f".repeat(64));
            assertEquals(1, restore.run(n -> {}).skipped().size());
            assertNull(store.byId(rec));
            assertEquals(3, downloads[0]);
        } finally {
            if (rec != null) {
                var dir = new File(store.root, rec);
                for (File f : Objects.requireNonNull(dir.listFiles())) java.nio.file.Files.delete(f.toPath());
                java.nio.file.Files.delete(dir.toPath());
                queue.getWritableDatabase().delete("recordings","rec=?",new String[]{rec});
            }
            queue.getWritableDatabase().delete("jobs","target=?",new String[]{target.id()});
            prefs.edit().putString("targets", originalTargets).putString("defaultTarget", originalDefault)
                    .putStringSet("folders", originalFolders).commit();
        }
    }

    @Test
    public void contentHashAcrossDropboxBlockBoundary() throws Exception {
        File tmp = File.createTempFile("blocks-", ".part", InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
        try {
            try (RandomAccessFile data = new RandomAccessFile(tmp, "rw")) {
                data.seek(4L * 1024 * 1024);
                data.write('A');
            }
            assertEquals("11d29899ccb4a260814f07931519a87c793ec37a2d50e145ae9e1269a3b4bcd8",
                    DropboxRestore.hashes(tmp, n -> {}).dropbox());
        } finally { java.nio.file.Files.deleteIfExists(tmp.toPath()); }
    }

    @Test
    public void cloudInventoryFollowsCursorAndDownloadsOriginalBytes() throws Exception {
        List<String> calls = new ArrayList<>();
        String audio = "Audomo-Testinhalt";
        OkHttpClient local = new OkHttpClient.Builder().addInterceptor(chain -> {
            String path = chain.request().url().encodedPath();
            calls.add(path);
            String body = switch (path) {
                case "/oauth2/token" -> "{\"access_token\":\"synthetic\"}";
                case "/2/files/list_folder" -> "{\"entries\":[{\".tag\":\"folder\",\"name\":\"Archiv\"}],\"has_more\":true,\"cursor\":\"seite2\"}";
                case "/2/files/list_folder/continue" -> "{\"entries\":[{\".tag\":\"file\",\"name\":\"Probe.ogg\",\"size\":17}],\"has_more\":false}";
                case "/2/files/download" -> audio;
                default -> throw new AssertionError(path);
            };
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(200).message("OK").body(ResponseBody.create(null, body)).build();
        }).build();
        CloudConfig t = new CloudConfig(UUID.randomUUID().toString(), "Dropbox","test",443,"","/","");
        try (DropboxFiles remote = new DropboxFiles(t,"synthetic", n -> {},local)) {
            assertEquals(List.of(new RemoteFiles.Child("Archiv",true,-1), new RemoteFiles.Child("Probe.ogg",false,17)),remote.children("/"));
            File file = File.createTempFile("dropbox-restore-", ".ogg", InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
            try {
                String hash = LibraryFiles.digest(new ByteArrayInputStream(audio.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                remote.downloadVerified("/Archiv/Probe.ogg", hash, file);
                assertEquals(audio,new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8));
            } finally { java.nio.file.Files.deleteIfExists(file.toPath()); }
        }
        assertEquals(List.of("/oauth2/token","/2/files/list_folder","/2/files/list_folder/continue","/2/files/download"),calls);
    }

    @Test
    public void renameUsesOriginalFolderAndReportsConflictWithoutOverwriting() throws Exception {
        int[] calls = {0};
        OkHttpClient local = new OkHttpClient.Builder().addInterceptor(chain -> {
            boolean login = chain.request().url().encodedPath().equals("/oauth2/token");
            if (!login) {
                calls[0]++;
                assertEquals("/2/files/move_v2", chain.request().url().encodedPath());
                assertTrue(chain.request().body().isOneShot());
                okio.Buffer buffer = new okio.Buffer();
                chain.request().body().writeTo(buffer);
                try {
                    JSONObject body = new JSONObject(buffer.readUtf8());
                    assertEquals("/Archiv/Alt.ogg", body.getString("from_path"));
                    assertEquals("/Archiv/Neu.ogg", body.getString("to_path"));
                    assertFalse(body.getBoolean("autorename"));
                } catch (JSONException error) { throw new IOException(error); }
            }
            boolean conflict = calls[0] == 2;
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(conflict ? 409 : 200).message("fixture").body(ResponseBody.create(null,
                            login ? "{\"access_token\":\"synthetic\"}" : conflict
                            ? "{\"error_summary\":\"to/conflict/file/..\"}" : "{}")).build();
        }).build();
        CloudConfig t = new CloudConfig(UUID.randomUUID().toString(), "Dropbox", "qa", 443, "", "/", "");
        try (DropboxFiles remote = new DropboxFiles(t, "synthetic", n -> {}, local)) {
            remote.renameFile("/Archiv/Alt.ogg", "/Archiv/Neu.ogg", "hash");
            assertThrows(RemoteFiles.Conflict.class, () -> remote.renameFile("/Archiv/Alt.ogg", "/Archiv/Neu.ogg", "hash"));
            assertEquals(2, calls[0]);
        }
    }

    @Test
    public void quotaErrorDoesNotBecomeAnOverwritePrompt() throws Exception {
        var client = DropboxFiles.dropboxHttp(null);
        assertTrue(client.retryOnConnectionFailure());
        OkHttpClient local = client.newBuilder().addInterceptor(chain -> {
            boolean token = chain.request().url().encodedPath().equals("/oauth2/token");
            if (!token) assertTrue(chain.request().body().isOneShot());
            String body = token ? "{\"access_token\":\"synthetic\"}"
                    : "{\"error_summary\":\"path/insufficient_space/..\"}";
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                    .code(token ? 200 : 409).message("fixture")
                    .body(ResponseBody.create(null, body)).build();
        }).build();
        CloudConfig t = new CloudConfig(UUID.randomUUID().toString(), "Dropbox", "qa", 443, "", "/", "");
        File sample = File.createTempFile("qa-quota-", ".ogg",
                InstrumentationRegistry.getInstrumentation().getTargetContext().getCacheDir());
        try (DropboxFiles remote = new DropboxFiles(t, "synthetic", n -> {}, local)) {
            DropboxFiles.Failure error = assertThrows(DropboxFiles.Failure.class,
                    () -> remote.put(sample, "/Probe.ogg", false, null));
            assertTrue(UploadWorker.safeError(error).contains("Speicherplatz"));
        } finally {
            java.nio.file.Files.deleteIfExists(sample.toPath());
        }
    }

    @Test
    public void refreshAndChunkOffsetsWithLocalTransport() throws Exception {
        final int chunk = 8 * 1024 * 1024;
        File f =
                File.createTempFile(
                        "qa-dropbox-",
                        ".bin",
                        InstrumentationRegistry.getInstrumentation()
                                .getTargetContext()
                                .getCacheDir());
        try (RandomAccessFile file = new RandomAccessFile(f, "rw")) {
            file.setLength(2L * chunk + 17);
            List<String> calls = new ArrayList<>();
            OkHttpClient local =
                    new OkHttpClient.Builder()
                            .addInterceptor(chain -> {
                                Request request = chain.request();
                                String path = request.url().encodedPath();
                                calls.add(path);
                                String response = "{}";
                                try {
                                    if (path.equals("/oauth2/token")) {
                                        assertEquals("api.dropboxapi.com", request.url().host());
                                        FormBody form = (FormBody) request.body();
                                        assertEquals("refresh_token", form.value(0));
                                        assertEquals("client_id", form.name(2));
                                        assertEquals("local-qa", form.value(2));
                                        response = "{\"access_token\":\"synthetic\"}";
                                    } else {
                                        assertEquals(
                                                "content.dropboxapi.com", request.url().host());
                                        assertEquals(
                                                "Bearer synthetic",
                                                request.header("Authorization"));
                                        JSONObject arguments =
                                                new JSONObject(request.header("Dropbox-API-Arg"));
                                        assertTrue(request.body().isOneShot());
                                        long length = request.body().contentLength();
                                        switch (path) {
                                            case "/2/files/upload_session/start":
                                                assertEquals(chunk, length);
                                                response = "{\"session_id\":\"fixture\"}";
                                                break;
                                            case "/2/files/upload_session/append_v2":
                                                assertEquals(
                                                        chunk,
                                                        arguments
                                                                .getJSONObject("cursor")
                                                                .getLong("offset"));
                                                assertEquals(chunk, length);
                                                break;
                                            case "/2/files/upload_session/finish":
                                                assertEquals(
                                                        2L * chunk,
                                                        arguments
                                                                .getJSONObject("cursor")
                                                                .getLong("offset"));
                                                assertEquals(
                                                        "/Prüfung.ogg",
                                                        arguments
                                                                .getJSONObject("commit")
                                                                .getString("path"));
                                                assertEquals(17, length);
                                                break;
                                            default:
                                                throw new AssertionError(path);
                                        }
                                    }
                                } catch (JSONException e) {
                                    throw new IOException(e);
                                }
                                return new Response.Builder()
                                        .request(request)
                                        .protocol(Protocol.HTTP_1_1)
                                        .code(200)
                                        .message("OK")
                                        .body(
                                                ResponseBody.create(
                                                        MediaType.get("application/json"),
                                                        response))
                                        .build();
                            })
                            .build();
            CloudConfig target =
                    new CloudConfig(
                            UUID.randomUUID().toString(), "Dropbox", "local-qa", 443, "", "/", "");
            try (DropboxFiles remote = new DropboxFiles(target, "synthetic", n -> {}, local)) {
                remote.put(f, "/Prüfung.ogg", false, null);
            }
            assertEquals(
                    List.of(
                            "/oauth2/token",
                            "/2/files/upload_session/start",
                            "/2/files/upload_session/append_v2",
                            "/2/files/upload_session/finish"),
                    calls);
        } finally {
            java.nio.file.Files.deleteIfExists(f.toPath());
        }
    }
}
