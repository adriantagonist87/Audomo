package de.audomo;

import android.net.Network;

import okhttp3.*;

import org.json.JSONObject;

import java.io.*;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

final class DropboxFiles extends RemoteFiles {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8"),
            BYTES = MediaType.get("application/octet-stream");
    private final OkHttpClient client;
    private final String bearer;

    DropboxFiles(CloudConfig c, String refresh, Network network, Progress check) throws Exception {
        this(c, refresh, check, dropboxHttp(network));
    }

    static OkHttpClient dropboxHttp(Network network) {
        // Some networks publish an IPv6 address that never connects. Let OkHttp try the
        // other DNS results on the same Android network, including a working IPv4 route.
        return http(network).newBuilder()
                .retryOnConnectionFailure(true)
                .connectTimeout(6, TimeUnit.SECONDS)
                .build();
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

    static JSONObject token(OkHttpClient client, FormBody form) throws Exception {
        try (Response r =
                client.newCall(
                                new Request.Builder()
                                        .url("https://api.dropboxapi.com/oauth2/token")
                                        .post(form)
                                        .build())
                        .execute()) {
            requireDropbox(r);
            return new JSONObject(r.body().string());
        }
    }

    static final class Failure extends IOException {
        Failure(String message) { super(message); }
    }

    private static String errorSummary(Response response) throws IOException {
        try {
            JSONObject json = new JSONObject(response.peekBody(8192).string());
            return json.optString("error_summary", json.optString("error", ""));
        } catch (org.json.JSONException ignored) {
            return "";
        }
    }

    private static void requireDropbox(Response response) throws IOException {
        if (response.isSuccessful()) return;
        String reason = errorSummary(response);
        String message;
        if (reason.startsWith("missing_scope") || reason.startsWith("insufficient_permissions"))
            message = "Dropbox-Berechtigungen fehlen. Zugang erneut freigeben.";
        else if (reason.startsWith("invalid_grant") || response.code() == 401)
            message = "Dropbox-Anmeldung abgelaufen. Zugang erneut verbinden.";
        else if (reason.contains("insufficient_space"))
            message = "Der Speicherplatz in Dropbox reicht nicht aus.";
        else if (reason.startsWith("path/not_found"))
            message = "Dropbox findet den eingestellten Zielpfad nicht.";
        else if (response.code() == 429)
            message = "Dropbox begrenzt momentan die Zahl der Anfragen.";
        else
            message = "Dropbox hat die Anfrage abgelehnt (HTTP " + response.code() + ").";
        throw new Failure(message);
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
        RequestBody body = content ? data : RequestBody.create(JSON, args.toString());
        if (content) b.header("Dropbox-API-Arg", header(args));
        boolean mutates = content || method.equals("create_folder_v2") || method.equals("delete_v2");
        b.post(mutates ? oneShot(body) : body);
        return client.newCall(b.build()).execute();
    }

    // A connect failure before sending the request may try another address. After a write
    // starts, only the queue may retry it, after verifying the existing remote content.
    private static RequestBody oneShot(RequestBody body) {
        return new RequestBody() {
            @Override public boolean isOneShot() { return true; }
            @Override public MediaType contentType() { return body.contentType(); }
            @Override public long contentLength() throws IOException { return body.contentLength(); }
            @Override public void writeTo(okio.BufferedSink sink) throws IOException { body.writeTo(sink); }
        };
    }

    void test() throws Exception {
        try (Response r =
                request(
                        "list_folder",
                        new JSONObject()
                                .put("path", config.base().equals("/") ? "" : config.base())
                                .put("limit", 1),
                        null)) {
            requireDropbox(r);
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
            if (r.code() != 409 || !errorSummary(r).startsWith("path/not_found")) requireDropbox(r);
        }
        try (Response r = request("create_folder_v2", new JSONObject().put("path", path), null)) {
            requireDropbox(r);
        }
    }

    java.util.List<Child> children(String path) throws Exception {
        java.util.List<Child> list = new java.util.ArrayList<>();
        String method = "list_folder";
        JSONObject args = new JSONObject().put("path", path.equals("/") ? "" : path);
        while (true) {
            try (Response r = request(method, args, null)) {
                requireDropbox(r);
                JSONObject page = new JSONObject(r.body().string());
                org.json.JSONArray entries = page.getJSONArray("entries");
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject item = entries.getJSONObject(i);
                    String kind = item.getString(".tag");
                    if (kind.equals("file") || kind.equals("folder"))
                        list.add(new Child(item.getString("name"), kind.equals("folder"), item.optLong("size", -1)));
                }
                if (!page.getBoolean("has_more")) return list;
                method = "list_folder/continue";
                args = new JSONObject().put("cursor", page.getString("cursor"));
            }
        }
    }

    boolean read(String path, OutputStream out) throws Exception {
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
            if (r.code() == 409 && errorSummary(r).startsWith("path/not_found")) return false;
            requireDropbox(r);
            copyTo(r.body().byteStream(), out);
            return true;
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
                requireDropbox(r);
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
                if (r.code() == 409 && errorSummary(r).startsWith("path/conflict")) throw new Conflict();
                requireDropbox(r);
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
                requireDropbox(r);
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
                if (last) {
                    progress.beforeCommit();
                    req.put("commit", args);
                }
                try (Response r =
                        request(
                                last ? "upload_session/finish" : "upload_session/append_v2",
                                req,
                                RequestBody.create(BYTES, data))) {
                    if (last && r.code() == 409 && errorSummary(r).startsWith("path/conflict")) throw new Conflict();
                    requireDropbox(r);
                }
            }
        }
    }

    void deleteIfMatches(String path, String expected) throws Exception {
        JSONObject meta;
        try (Response r = request("get_metadata", new JSONObject().put("path", path), null)) {
            if (r.code() == 409 && errorSummary(r).startsWith("path/not_found")) return;
            requireDropbox(r);
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
            requireDropbox(r);
        }
    }

    public void close() {
        client.connectionPool().evictAll();
    }
}
