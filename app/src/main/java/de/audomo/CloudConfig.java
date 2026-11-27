package de.audomo;

import android.content.*;
import android.security.keystore.*;
import android.util.Base64;

import org.json.*;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

record CloudConfig(
        String id,
        String protocol,
        String host,
        int port,
        String user,
        String base,
        String hostKey) {
    static final List<String> TYPES = List.of("Dropbox", "WebDAV", "FTP", "FTPS", "SFTP");

    CloudConfig {
        if (!TYPES.contains(protocol) || !id.matches("[a-f0-9-]{36}") || port < 1 || port > 65535)
            throw new IllegalArgumentException("Ungültiges Verbindungsprofil");
        if (!protocol.equals("Dropbox")) {
            try {
                URI uri = new URI("https", null, host, port, null, null, null);
                if (uri.getHost() == null || !uri.getHost().equalsIgnoreCase(host))
                    throw new Exception();
            } catch (Exception invalid) {
                throw new IllegalArgumentException(
                        "Gültigen Servernamen ohne Protokoll und Pfad angeben");
            }
        } else if (!host.matches("[A-Za-z0-9_-]+"))
            throw new IllegalArgumentException("Öffentlicher Dropbox-App-Key fehlt");
        if (user.contains("\r") || user.contains("\n"))
            throw new IllegalArgumentException("Ungültiger Benutzername");
        base = path(base);
        if (protocol.equals("SFTP")
                && !hostKey.matches("(ssh-|ecdsa-)[A-Za-z0-9@._-]+ [A-Za-z0-9+/=]+"))
            throw new IllegalArgumentException("Öffentlichen SSH-Hostschlüssel angeben");
    }

    String label() {
        if (protocol.equals("Dropbox"))
            return "Dropbox · " + (base.equals("/") ? "App-Ordner" : base);
        return protocol + " · " + host;
    }

    boolean hasCredentials(Context context) {
        return context.getSharedPreferences("credentials", 0).contains(id);
    }

    String uploadLabel() {
        if (protocol.equals("Dropbox")) return label();
        String account = user.isEmpty() ? "" : user + "@";
        return protocol + " · " + account + host + ":" + port + base;
    }

    static String path(String raw) {
        List<String> clean = new ArrayList<>();
        for (String s : raw.split("/")) {
            if (s.isEmpty()) continue;
            if (s.equals(".")
                    || s.equals("..")
                    || s.contains("\\")
                    || s.matches(".*[\\p{Cntrl}].*"))
                throw new IllegalArgumentException("Ungültiger Zielpfad");
            clean.add(s);
        }
        return "/" + String.join("/", clean);
    }

    JSONObject json() throws JSONException {
        return new JSONObject()
                .put("id", id)
                .put("protocol", protocol)
                .put("host", host)
                .put("port", port)
                .put("user", user)
                .put("base", base)
                .put("hostKey", hostKey);
    }

    static CloudConfig parse(JSONObject j) throws Exception {
        return new CloudConfig(
                j.getString("id"),
                j.getString("protocol"),
                j.getString("host"),
                j.getInt("port"),
                j.getString("user"),
                j.getString("base"),
                j.optString("hostKey"));
    }

    static List<CloudConfig> all(Context c) throws Exception {
        JSONArray a = new JSONArray(Profile.prefs(c).getString("targets", "[]"));
        List<CloudConfig> result = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) result.add(parse(a.getJSONObject(i)));
        return result;
    }

    static CloudConfig byId(Context c, String id) throws Exception {
        for (CloudConfig t : all(c)) if (t.id.equals(id)) return t;
        throw new IOException("Ziel nicht mehr eingerichtet");
    }

    static JSONArray publicTargets(Context c) throws Exception {
        JSONArray a = new JSONArray();
        for (CloudConfig t : all(c)) a.put(t.json());
        return a;
    }

    static void importTargets(
            Context c,
            SharedPreferences.Editor ed,
            JSONArray data,
            String selected,
            boolean automatic)
            throws Exception {
        List<CloudConfig> merged = new ArrayList<>(all(c));
        String standard = "";
        for (int i = 0; i < data.length(); i++) {
            CloudConfig imported = parse(data.getJSONObject(i));
            CloudConfig match = null;
            for (CloudConfig existing : merged) {
                if (existing.protocol.equals(imported.protocol)
                        && existing.host.equals(imported.host)
                        && existing.port == imported.port
                        && existing.user.equals(imported.user)
                        && existing.base.equals(imported.base)
                        && existing.hostKey.equals(imported.hostKey)) {
                    match = existing;
                    break;
                }
            }
            if (match == null) {
                match =
                        new CloudConfig(
                                UUID.randomUUID().toString(),
                                imported.protocol,
                                imported.host,
                                imported.port,
                                imported.user,
                                imported.base,
                                imported.hostKey);
                merged.add(match);
            }
            if (imported.id.equals(selected)) standard = match.id;
        }
        JSONArray a = new JSONArray();
        for (CloudConfig t : merged) a.put(t.json());
        ed.putString("targets", a.toString()).putString("defaultTarget", standard);
        ed.putBoolean(
                "autoUpload",
                automatic
                        && !standard.isEmpty()
                        && c.getSharedPreferences("credentials", 0).contains(standard));
    }

    void save(Context c, String secret) throws Exception {
        List<CloudConfig> current = all(c);
        JSONArray a = new JSONArray();
        boolean found = false;
        for (CloudConfig t : current) {
            if (t.id.equals(id)) {
                if (!t.equals(this))
                    throw new IOException("Geändertes Ziel benötigt neue Zuordnung");
                found = true;
            }
            a.put(t.json());
        }
        if (!found) a.put(json());
        if (!secret.isEmpty()) seal(c, id, secret);
        SharedPreferences.Editor edit = Profile.prefs(c).edit().putString("targets", a.toString());
        if (Profile.prefs(c).getString("defaultTarget", "").isEmpty())
            edit.putString("defaultTarget", id);
        if (!edit.commit()) throw new IOException("Ziel nicht gesichert");
        if (found && !secret.isEmpty()) UploadQueue.get(c).accessRenewed(id);
    }

    private static synchronized SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        String alias = "audomo.upload.v1";
        if (!ks.containsAlias(alias)) {
            KeyGenerator gen = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            gen.init(
                    new KeyGenParameterSpec.Builder(
                                    alias,
                                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                            .build());
            return gen.generateKey();
        }
        return (SecretKey) ks.getKey(alias, null);
    }

    static String box(String id, String value) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
        return
                Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                        + "."
                        + Base64.encodeToString(
                                cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)),
                                Base64.NO_WRAP);
    }

    static void seal(Context c, String id, String value) throws Exception {
        if (!c.getSharedPreferences("credentials", 0).edit().putString(id, box(id, value)).commit())
            throw new IOException("Zugang nicht gesichert");
    }

    static String secret(Context c, String id) throws Exception {
        String boxed = c.getSharedPreferences("credentials", 0).getString(id, "");
        if (boxed.isEmpty()) throw new IOException("Ziel erneut verbinden");
        String[] parts = boxed.split("\\.", 2);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.decode(parts[0], 0)));
        cipher.updateAAD(id.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(Base64.decode(parts[1], 0)), StandardCharsets.UTF_8);
    }
}
