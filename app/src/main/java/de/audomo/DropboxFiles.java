package de.audomo;

import android.net.Network;
import android.util.Base64;

import okhttp3.*;

import org.json.JSONObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

final class DropboxFiles extends RemoteFiles {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8"),
            BYTES = MediaType.get("application/octet-stream");
    private final OkHttpClient client;
    private final String bearer;

    DropboxFiles(CloudConfig c, String refresh, Network network, Progress check) throws Exception {
        this(c, refresh, check, http(network));
    }

    DropboxFiles(CloudConfig c, String refresh, Progress check, OkHttpClient transport)
            throws Exception {
        super(c, check);
        client = transport;
        bearer =
                token(
                                client,
                                new FormBody.Builder()
                                        .add("grant_type", "refresh_token")
                                        .add("refresh_token", refresh)
                                        .add("client_id", c.host())
                                        .build())
                        .getString("access_token");
    }

    static String verifier() {
        byte[] bytes = new byte[48];
        new SecureRandom().nextBytes(bytes);
        return b64(bytes);
    }

    static String b64(byte[] data) {
        return Base64.encodeToString(data, Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);
    }

    static android.net.Uri authorize(String key, String verifier) throws Exception {
        return android.net.Uri.parse("https://www.dropbox.com/oauth2/authorize")
                .buildUpon()
                .appendQueryParameter("client_id", key)
                .appendQueryParameter("response_type", "code")
                .appendQueryParameter("token_access_type", "offline")
                .appendQueryParameter("code_challenge_method", "S256")
                .appendQueryParameter(
                        "code_challenge",
                        b64(
                                MessageDigest.getInstance("SHA-256")
                                        .digest(verifier.getBytes(StandardCharsets.US_ASCII))))
                .build();
    }

    static String exchange(String key, String verifier, String code) throws Exception {
        OkHttpClient c = http(null);
        try {
            return token(
                            c,
                            new FormBody.Builder()
                                    .add("grant_type", "authorization_code")
                                    .add("client_id", key)
                                    .add("code_verifier", verifier)
                                    .add("code", code)
                                    .build())
                    .getString("refresh_token");
        } finally {
            c.connectionPool().evictAll();
        }
    }

    static JSONObject token(OkHttpClient client, FormBody form) throws Exception {
        try (Response r =
                client.newCall(
                                new Request.Builder()
                                        .url("https://api.dropboxapi.com/oauth2/token")
                                        .post(form)
                                        .build())
                        .execute()) {
            require(r);
            return new JSONObject(r.body().string());
        }
    }

    private static String header(JSONObject json) {
        String value = json.toString();
        StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 32 || ch > 126)
                escaped.append(String.format(Locale.ROOT, "\\u%04x", (int) ch));
            else escaped.append(ch);
        }
        return escaped.toString();
    }

    private Response request(String method, JSONObject args, RequestBody data) throws Exception {
        boolean content = data != null;
        Request.Builder b =
                new Request.Builder()
                        .url(
                                "https://"
                                        + (content ? "content" : "api")
                                        + ".dropboxapi.com/2/files/"
                                        + method)
                        .header("Authorization", "Bearer " + bearer);
        if (content) b.header("Dropbox-API-Arg", header(args)).post(data);
        else b.post(RequestBody.create(JSON, args.toString()));
        return client.newCall(b.build()).execute();
    }

    void test() throws Exception {
        try (Response r =
                request(
                        "list_folder",
                        new JSONObject()
                                .put("path", config.base().equals("/") ? "" : config.base())
                                .put("limit", 1),
                        null)) {
            require(r);
        }
    }

    void ensureFolder(String path) throws Exception {
        if (path.equals("/")) return;
        try (Response r = request("get_metadata", new JSONObject().put("path", path), null)) {
            if (r.isSuccessful()) {
                if (!"folder".equals(new JSONObject(r.body().string()).getString(".tag")))
                    throw new IOException("Ziel ist kein Ordner");
                return;
            }
            if (r.code() != 409 || !r.body().string().contains("not_found")) require(r);
        }
        try (Response r = request("create_folder_v2", new JSONObject().put("path", path), null)) {
            require(r);
        }
    }

    String hash(String path) throws Exception {
        try (Response r =
                client.newCall(
                                new Request.Builder()
                                        .url("https://content.dropboxapi.com/2/files/download")
                                        .header("Authorization", "Bearer " + bearer)
                                        .header(
                                                "Dropbox-API-Arg",
                                                header(new JSONObject().put("path", path)))
                                        .post(RequestBody.create(null, new byte[0]))
                                        .build())
                        .execute()) {
            if (r.code() == 409 && r.body().string().contains("not_found")) return null;
            require(r);
            return LibraryFiles.digest(guarded(r.body().byteStream()));
        }
    }

    void put(File f, String path, boolean replace, String expected) throws Exception {
        JSONObject args =
                new JSONObject()
                        .put("path", path)
                        .put("mode", "add")
                        .put("autorename", false)
                        .put("strict_conflict", true);
        if (replace) {
            try (Response r = request("get_metadata", new JSONObject().put("path", path), null)) {
                require(r);
                args.put(
                        "mode",
                        new JSONObject()
                                .put(".tag", "update")
                                .put("update", new JSONObject(r.body().string()).getString("rev")));
            }
            if (!Objects.equals(expected, hash(path))) throw new Conflict();
        }
        if (f.length() <= 8 * 1024 * 1024) {
            try (Response r = request("upload", args, body(f))) {
                if (r.code() == 409) throw new Conflict();
                require(r);
            }
            return;
        }
        try (InputStream in = guarded(new FileInputStream(f))) {
            int chunk = 8 * 1024 * 1024;
            byte[] data = in.readNBytes(chunk);
            String id;
            try (Response r =
                    request(
                            "upload_session/start",
                            new JSONObject(),
                            RequestBody.create(BYTES, data))) {
                require(r);
                id = new JSONObject(r.body().string()).getString("session_id");
            }
            long pos = data.length;
            while (pos < f.length()) {
                data = in.readNBytes(chunk);
                if (data.length == 0) throw new EOFException();
                JSONObject req =
                        new JSONObject()
                                .put(
                                        "cursor",
                                        new JSONObject().put("session_id", id).put("offset", pos));
                pos += data.length;
                boolean last = pos == f.length();
                if (last) req.put("commit", args);
                try (Response r =
                        request(
                                last ? "upload_session/finish" : "upload_session/append_v2",
                                req,
                                RequestBody.create(BYTES, data))) {
                    if (last && r.code() == 409) throw new Conflict();
                    require(r);
                }
            }
        }
    }

    void deleteIfMatches(String path, String expected) throws Exception {
        JSONObject meta;
        try (Response r = request("get_metadata", new JSONObject().put("path", path), null)) {
            if (r.code() == 409 && r.body().string().contains("not_found")) return;
            require(r);
            meta = new JSONObject(r.body().string());
        }
        if (!expected.equals(hash(path))) throw new IOException("Cloudkopie wurde extern geändert");
        try (Response r =
                request(
                        "delete_v2",
                        new JSONObject()
                                .put("path", meta.getString("id"))
                                .put("parent_rev", meta.getString("rev")),
                        null)) {
            require(r);
        }
    }

    public void close() {
        client.connectionPool().evictAll();
    }
}
