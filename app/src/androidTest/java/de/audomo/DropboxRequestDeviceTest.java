package de.audomo;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import okhttp3.*;

import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

@RunWith(AndroidJUnit4.class)
public class DropboxRequestDeviceTest {
    @Test
    public void pkceAndChunkOffsetsWithLocalTransport() throws Exception {
        String verifier = DropboxFiles.verifier();
        var auth = DropboxFiles.authorize("local-qa", verifier);
        assertEquals("S256", auth.getQueryParameter("code_challenge_method"));
        assertEquals(
                DropboxFiles.b64(
                        MessageDigest.getInstance("SHA-256")
                                .digest(verifier.getBytes(StandardCharsets.US_ASCII))),
                auth.getQueryParameter("code_challenge"));
        assertNotEquals(verifier, DropboxFiles.verifier());
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
                                        response = "{\"access_token\":\"synthetic\"}";
                                    } else {
                                        assertEquals(
                                                "content.dropboxapi.com", request.url().host());
                                        assertEquals(
                                                "Bearer synthetic",
                                                request.header("Authorization"));
                                        JSONObject arguments =
                                                new JSONObject(request.header("Dropbox-API-Arg"));
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
