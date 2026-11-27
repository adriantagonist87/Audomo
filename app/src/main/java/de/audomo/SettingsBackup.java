package de.audomo;

import android.content.*;

import org.json.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class SettingsBackup {
    static final List<String> BOOLEAN =
            List.of("haptic", "descending", "skipSilence", "wifiOnly", "mirrorFolders", AppUpdates.AUTO, PlaybackOutput.BLUETOOTH_ONLY);
    static final Map<String, Integer> LIMITS =
            Map.of(
                    AppUpdates.INTERVAL, 3,
                    "before",
                    Integer.MAX_VALUE,
                    "after",
                    Integer.MAX_VALUE,
                    "sort",
                    4,
                    "queueFilter",
                    3,
                    "pattern.start",
                    2,
                    "pattern.stop",
                    2,
                    "pattern.error",
                    2);

    static String installation(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("installation", 0);
        String id = p.getString("id", null);
        if (id == null) {
            id = UUID.randomUUID().toString();
            p.edit().putString("id", id).commit();
        }
        return id;
    }

    static byte[] export(Context c) throws Exception {
        SharedPreferences p = Profile.prefs(c);
        JSONObject data = new JSONObject();
        for (String k : BOOLEAN) if (p.contains(k)) data.put(k, p.getBoolean(k, false));
        for (String k : LIMITS.keySet()) if (p.contains(k)) data.put(k, p.getInt(k, 0));
        JSONArray profiles = new JSONArray();
        for (Profile pr : Profile.profiles(c)) profiles.put(pr.json());
        data.put("profiles", profiles)
                .put("selected", Profile.selected(c).name())
                .put("template", CalendarNames.template(c))
                .put("recordingFolderMode", CalendarNames.folderMode(c).name())
                .put("recordingBase", p.getString("recordingBase", ""))
                .put("speed", (double) p.getFloat("speed", 1))
                .put("skipSeconds", p.getInt("skipSeconds", 5))
                .put("calendars", p.getString("calendars", ""))
                .put("installation", installation(c))
                .put("defaultTarget", p.getString("defaultTarget", ""))
                .put(UploadDelay.KEY, p.getString(UploadDelay.KEY, "0"))
                .put("autoUpload", p.getBoolean("autoUpload", false))
                .put("cleanupUploaded", p.getBoolean("cleanupUploaded", false));
        JSONObject root = new JSONObject().put("schema", 1).put("audomo", data);
        root.put("targets", CloudConfig.publicTargets(c));
        return root.toString(2).getBytes(StandardCharsets.UTF_8);
    }

    static String restore(Context c, InputStream in) throws Exception {
        byte[] raw = in.readNBytes(1024 * 1024 + 1);
        if (raw.length > 1024 * 1024) throw new IOException("Einstellungsdatei zu groß");
        JSONObject root = new JSONObject(new String(raw, StandardCharsets.UTF_8));
        if (root.getInt("schema") != 1)
            throw new IOException("Unbekannte Version der Einstellungsdatei");
        JSONObject d = root.getJSONObject("audomo");
        SharedPreferences.Editor ed = Profile.prefs(c).edit();
        JSONArray own = new JSONArray(), profiles = d.getJSONArray("profiles");
        Set<String> names = new HashSet<>();
        for (int i = 0; i < profiles.length(); i++) {
            Profile p = Profile.parse(profiles.getJSONObject(i));
            if (!names.add(p.name())) throw new IOException("Doppeltes Profil");
            if (!Profile.BUILTINS.contains(p)) own.put(p.json());
        }
        String chosen = d.getString("selected"), template = d.getString("template");
        if (!names.contains(chosen) || template.isBlank())
            throw new IOException("Profil oder Vorlage ungültig");
        CalendarNames.FolderMode mode =
                CalendarNames.FolderMode.valueOf(d.optString("recordingFolderMode", "CURRENT"));
        String base = d.optString("recordingBase", "");
        if (!LibraryFiles.validFolderPath(base)) throw new IOException("Ungültiger Aufnahmeordner");
        ed.putString("recordingFolderMode", mode.name()).putString("recordingBase", base);
        ed.putString("profiles", own.toString())
                .putString("selected", chosen)
                .putString("template", template);
        for (String k : BOOLEAN) {
            if (d.has(k)) ed.putBoolean(k, d.getBoolean(k));
            else ed.remove(k);
        }
        for (String k : LIMITS.keySet()) {
            if (!d.has(k)) {
                ed.remove(k);
                continue;
            }
            int n = d.getInt(k);
            if (n < 0 || n > LIMITS.get(k)) throw new IOException("Ungültiger Wert für " + k);
            ed.putInt(k, n);
        }
        int step = d.getInt("skipSeconds");
        double speed = d.getDouble("speed");
        if (!List.of(5, 10, 15, 30).contains(step)
                || !List.of(.75, 1., 1.25, 1.5, 2.).contains(speed))
            throw new IOException("Playerwerte ungültig");
        String ids = d.getString("calendars");
        if (!ids.matches("([0-9]+(,[0-9]+)*)?")) throw new IOException("Kalenderliste ungültig");
        boolean same = installation(c).equals(d.getString("installation"));
        ed.putString("calendars", same ? ids : "")
                .putFloat("speed", (float) speed)
                .putInt("skipSeconds", step);
        CloudConfig.importTargets(
                c,
                ed,
                root.getJSONArray("targets"),
                d.optString("defaultTarget"),
                d.optBoolean("autoUpload"));
        ed.putString(UploadDelay.KEY, UploadDelay.minutes(d.optString(UploadDelay.KEY, "0")).toPlainString());
        ed.putBoolean("cleanupUploaded", d.optBoolean("cleanupUploaded"));
        if (!ed.commit()) throw new IOException("Einstellungen nicht gesichert");
        return same
                ? "Einstellungen importiert"
                : "Einstellungen importiert · Kalender und Speicherort neu zuordnen";
    }
}
