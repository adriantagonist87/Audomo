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
