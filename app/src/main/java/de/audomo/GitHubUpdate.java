package de.audomo;

import android.content.Context;
import android.content.pm.*;
import org.json.*;
import okhttp3.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/** The only update channel is the public stable release of the official Audomo repository. */
final class GitHubUpdate {
    static final String REPO = "adriantagonist87/Audomo";
    static final String LATEST = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static final long MAX_APK = 256L * 1024 * 1024;
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .followRedirects(false).connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS).build();

    record Release(String version, long size, String sha256) {
        Release {
            if (!stable(version) || size <= 0 || size > MAX_APK || !sha256.matches("[a-f0-9]{64}"))
                throw new IllegalArgumentException("Ungültige Release-Angaben");
        }
        String fileName() { return "Audomo-" + version + ".apk"; }
        String url() { return "https://github.com/" + REPO + "/releases/download/v" + version + "/" + fileName(); }
    }

    static boolean stable(String v) {
        return v != null && v.matches("(0|[1-9][0-9]{0,8})(\\.(0|[1-9][0-9]{0,8})){2}");
    }
    static boolean newer(String candidate, String installed) {
        if (!stable(candidate) || !stable(installed)) return false;
        String[] a = candidate.split("\\."), b = installed.split("\\.");
        for (int i = 0; i < 3; i++) {
            int diff = Integer.compare(Integer.parseInt(a[i]), Integer.parseInt(b[i]));
            if (diff != 0) return diff > 0;
        }
        return false;
    }

    static Release parse(JSONObject json) throws Exception {
        String tag = json.getString("tag_name");
        if (json.getBoolean("draft") || json.getBoolean("prerelease")
                || !tag.startsWith("v") || !stable(tag.substring(1)))
            throw new IOException("GitHub meldet kein stabiles Audomo-Release.");
        String version = tag.substring(1);
        JSONArray assets = json.getJSONArray("assets");
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.getJSONObject(i);
            if (!asset.getString("name").equals("Audomo-" + version + ".apk")) continue;
            String digest = asset.optString("digest");
            if (!digest.matches("sha256:[a-f0-9]{64}"))
                throw new IOException("Für die Release-APK fehlt eine gültige SHA-256-Prüfsumme.");
            Release r = new Release(version, asset.getLong("size"), digest.substring(7));
            if (!r.url().equals(asset.getString("browser_download_url")))
                throw new IOException("Unerwarteter Downloadpfad für die Release-APK.");
            return r;
        }
        throw new IOException("Dieses Release enthält noch keine Audomo-APK.");
    }

    private static Request.Builder request(String url) {
        return new Request.Builder().url(url).header("User-Agent", "Audomo/" + BuildConfig.VERSION_NAME);
    }

    static Release latest() throws Exception {
        Request req = request(LATEST).header("Accept", "application/vnd.github+json").build();
        try (Response response = CLIENT.newCall(req).execute()) {
            requireSuccess(response);
            byte[] data = response.body().byteStream().readNBytes(1024 * 1024 + 1);
            if (data.length > 1024 * 1024) throw new IOException("Release-Angaben sind zu groß.");
            return parse(new JSONObject(new String(data, StandardCharsets.UTF_8)));
        }
    }

    private static void requireSuccess(Response response) throws IOException {
        if (response.isSuccessful()) return;
        int code = response.code();
        if (code == 403 || code == 429)
            throw new IOException("GitHub begrenzt den Zugriff. Bitte später erneut prüfen.");
        throw new IOException("GitHub antwortet mit HTTP " + code + ".");
    }

    private static Response openApk(Release release) throws IOException {
        HttpUrl url = HttpUrl.get(release.url());
        for (int hop = 0; hop <= 4; hop++) {
            Response response = CLIENT.newCall(request(url.toString()).build()).execute();
            if (!response.isRedirect()) return response;
            try (response) {
                String location = response.header("Location");
                url = location == null ? null : url.resolve(location);
                if (url == null || !url.isHttps() || url.port() != 443 || !url.username().isEmpty()
                        || !url.password().isEmpty() || !Set.of("github.com", "release-assets.githubusercontent.com",
                                "objects.githubusercontent.com").contains(url.host()))
                    throw new IOException("Unbekanntes Downloadziel von GitHub.");
            }
        }
        throw new IOException("Zu viele Weiterleitungen beim APK-Download.");
    }

    static File apk(Context c, Release r) {
        return new File(new File(c.getCacheDir(), "updates"), r.fileName());
    }

    static void prepare(Context c, Release release, IntConsumer progress) throws Exception {
        File apk = apk(c, release);
        File dir = apk.getParentFile();
        Files.createDirectories(dir.toPath());
        if (apk.isFile()) {
            try { verify(c, apk, release); return; }
            catch (Exception invalid) { Files.delete(apk.toPath()); }
        }
        // Access is serialized by AppUpdates. Only this private cache is disposable.
        File[] older = dir.listFiles();
        if (older != null) for (File file : older) if (file.isFile()) Files.deleteIfExists(file.toPath());
        File partial = File.createTempFile("download-", ".apk", dir);
        try {
            try (Response response = openApk(release)) {
                requireSuccess(response);
                try (InputStream in = response.body().byteStream(); OutputStream out = new FileOutputStream(partial)) {
                    long total = 0;
                    int last = -1;
                    byte[] buffer = new byte[65536];
                    for (int n; (n = in.read(buffer)) != -1;) {
                        total += n;
                        if (total > release.size()) throw new IOException("APK ist größer als angekündigt.");
                        out.write(buffer, 0, n);
                        int percent = (int)(100 * total / release.size());
                        if (percent != last) { progress.accept(percent); last = percent; }
                    }
                }
            }
            verify(c, partial, release);
            Files.move(partial.toPath(), apk.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } finally { Files.deleteIfExists(partial.toPath()); }
    }

    static void verify(Context c, File file, Release release) throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");
        if (file.length() != release.size()) throw new IOException("APK-Größe stimmt nicht mit GitHub überein.");
        try (InputStream in = new FileInputStream(file)) {
            byte[] buffer = new byte[65536];
            for (int n; (n = in.read(buffer)) != -1;) sha.update(buffer, 0, n);
        }
        if (!HexFormat.of().formatHex(sha.digest()).equals(release.sha256()))
            throw new IOException("Die SHA-256-Prüfsumme der APK stimmt nicht mit GitHub überein.");
        PackageManager pm = c.getPackageManager();
        PackageManager.PackageInfoFlags flags = PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES);
        PackageInfo installed = pm.getPackageInfo(c.getPackageName(), flags);
        PackageInfo next = pm.getPackageArchiveInfo(file.getAbsolutePath(), flags);
        if (next == null || !installed.packageName.equals(next.packageName) || !release.version().equals(next.versionName)
                || !newer(next.versionName, installed.versionName) || next.getLongVersionCode() <= installed.getLongVersionCode())
            throw new IOException("Die APK ist kein neueres Update dieser Audomo-Installation.");
        if (!officialSigner(installed) || !officialSigner(next))
            throw new IOException("Die App-Signatur passt nicht zum offiziellen Audomo-Release.");
    }

    private static boolean officialSigner(PackageInfo info) throws Exception {
        if (info.signingInfo == null) return false;
        Signature[] certs = info.signingInfo.getApkContentsSigners();
        return certs.length == 1 && BuildConfig.RELEASE_CERT_SHA256.equals(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(certs[0].toByteArray())));
    }
}
