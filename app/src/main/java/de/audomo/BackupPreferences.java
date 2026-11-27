package de.audomo;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.provider.CalendarContract.Calendars;

import org.json.*;

import java.io.IOException;
import java.util.*;

final class BackupPreferences {
    private static final String PENDING_CALENDARS = "migrationCalendarSelection";
    static final String[] GROUPS = {"options", "credentials", "app_lock", "next-recording"};
    private static final List<String> CALENDAR_FIELDS =
            List.of(
                    Calendars.ACCOUNT_NAME,
                    Calendars.ACCOUNT_TYPE,
                    Calendars.NAME,
                    Calendars.CALENDAR_DISPLAY_NAME);

    static JSONObject read(Context c, String group) throws JSONException {
        JSONObject json = new JSONObject();
        for (var e : c.getSharedPreferences(group, 0).getAll().entrySet()) {
            Object v = e.getValue();
            if (v instanceof Set<?> set) v = new JSONArray(set);
            json.put(e.getKey(), v);
        }
        return json;
    }

    static void write(Context c, String group, JSONObject values) throws Exception {
        SharedPreferences.Editor e = c.getSharedPreferences(group, 0).edit().clear();
        for (Iterator<String> it = values.keys(); it.hasNext(); ) {
            String k = it.next();
            Object v = values.get(k);
            if (v instanceof Boolean b) e.putBoolean(k, b);
            else if (v instanceof String s) e.putString(k, s);
            else if (k.equals("speed") && v instanceof Number n) e.putFloat(k, n.floatValue());
            else if (v instanceof Integer n) e.putInt(k, n);
            else if (v instanceof Long n) e.putLong(k, n);
            else if (v instanceof JSONArray a) {
                Set<String> set = new HashSet<>();
                for (int i = 0; i < a.length(); i++) set.add(a.getString(i));
                e.putStringSet(k, set);
            } else throw new IOException("Ungültiger Einstellungswert");
        }
        if (!e.commit()) throw new IOException("Einstellungen konnten nicht gespeichert werden");
    }

    static boolean allowedTree(Context c, String tree) {
        return !tree.isEmpty()
                && c.getContentResolver().getPersistedUriPermissions().stream()
                        .anyMatch(
                                p ->
                                        p.isReadPermission()
                                                && p.isWritePermission()
                                                && p.getUri().toString().equals(tree));
    }

    static Map<String, JSONObject> calendars(Context c) throws Exception {
        Map<String, JSONObject> result = new LinkedHashMap<>();
        if (c.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) return result;
        List<String> cols = new ArrayList<>(CALENDAR_FIELDS);
        cols.add(0, Calendars._ID);
        try (Cursor cursor =
                c.getContentResolver()
                        .query(
                                Calendars.CONTENT_URI,
                                cols.toArray(new String[0]),
                                null,
                                null,
                                null)) {
            if (cursor != null)
                while (cursor.moveToNext()) {
                    JSONObject item = new JSONObject();
                    for (int i = 0; i < CALENDAR_FIELDS.size(); i++)
                        item.put(
                                CALENDAR_FIELDS.get(i),
                                cursor.isNull(i + 1) ? "" : cursor.getString(i + 1));
                    result.put(cursor.getString(0), item);
                }
        }
        return result;
    }

    private static List<String> matchCalendars(Context c, JSONArray selected) throws Exception {
        Map<String, JSONObject> known = calendars(c);
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < selected.length(); i++) {
            JSONObject entry = selected.getJSONObject(i);
            List<String> matches = new ArrayList<>();
            for (var item : known.entrySet()) {
                boolean match =
                        CALENDAR_FIELDS.stream()
                                .allMatch(
                                        k ->
                                                entry.has(k)
                                                        && entry.optString(k)
                                                                .equals(
                                                                        item.getValue()
                                                                                .optString(k)));
                if (match) matches.add(item.getKey());
            }
            if (matches.size() == 1) ids.add(matches.get(0));
        }
        return ids;
    }

    static void resolveCalendars(Context c) {
        SharedPreferences prefs = Profile.prefs(c);
        String raw = prefs.getString(PENDING_CALENDARS, "");
        if (raw.isEmpty()
                || c.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                        != PackageManager.PERMISSION_GRANTED) return;
        try {
            JSONArray selected = new JSONArray(raw);
            List<String> ids = matchCalendars(c, selected);
            if (ids.size() == selected.length()
                    && raw.equals(prefs.getString(PENDING_CALENDARS, "")))
                prefs.edit()
                        .putString("calendars", String.join(",", ids))
                        .remove(PENDING_CALENDARS)
                        .apply();
        } catch (Exception error) {
            ((Audomo) c.getApplicationContext())
                    .diagnostic("Kalenderzuordnung nach Import noch offen");
        }
    }

    static JSONObject export(Context c, BackupArchive.Scope scope) throws Exception {
        JSONObject options = read(c, "options");
        if (options.has("pendingFolderChange"))
            throw new IOException("Ordneränderung zuerst abschließen");
        options.remove("targets");
        String standard = options.optString("defaultTarget");
        options.remove("defaultTarget");
        String ids = options.optString("calendars");
        Map<String, JSONObject> known = calendars(c);
        JSONArray selected = new JSONArray();
        for (String id : ids.split(","))
            if (!id.isEmpty())
                selected.put(known.getOrDefault(id, new JSONObject()).put("oldId", id));
        if (options.has(PENDING_CALENDARS))
            selected = new JSONArray(options.getString(PENDING_CALENDARS));
        boolean deferred = options.has(PENDING_CALENDARS);
        options.remove(PENDING_CALENDARS);
        JSONObject data =
                new JSONObject()
                        .put("options", options)
                        .put("calendars", selected)
                        .put("calendarSelectionPending", deferred)
                        .put("installation", SettingsBackup.installation(c))
                        .put("locked", ((Audomo) c.getApplicationContext()).appLock.enabled());
        if (scope != BackupArchive.Scope.SETTINGS) {
            data.put("defaultTarget", standard);
            JSONArray targets = new JSONArray();
            for (CloudConfig t : CloudConfig.all(c)) {
                JSONObject x = t.json();
                if (t.hasCredentials(c)) x.put("credential", CloudConfig.secret(c, t.id()));
                targets.put(x);
            }
            data.put("connections", targets);
        }
        if (scope == BackupArchive.Scope.FULL) data.put("draft", read(c, "next-recording"));
        return data;
    }

    static JSONObject prepare(Context c, JSONObject data, BackupArchive.Scope scope)
            throws Exception {
        JSONObject prior = read(c, "options");
        JSONObject o = data.getJSONObject("options");
        boolean same = SettingsBackup.installation(c).equals(data.getString("installation"));
        for (String k : List.of("targets", "defaultTarget", "pendingFolderChange", "ownMarker"))
            o.remove(k);
        if (!allowedTree(c, o.optString("documentTree"))) {
            if (o.has("documentTree")) o.put("migrationDocumentTree", o.remove("documentTree"));
        } else if (same
                && o.optString("documentTree").equals(prior.optString("documentTree"))
                && prior.has("ownMarker")) o.put("ownMarker", prior.get("ownMarker"));
        if (!same || data.optBoolean("calendarSelectionPending")) {
            JSONArray selected = data.getJSONArray("calendars");
            List<String> ids = matchCalendars(c, selected);
            o.put("calendars", String.join(",", ids));
            if (ids.size() != selected.length()) o.put(PENDING_CALENDARS, selected.toString());
        }
        for (String key : SettingsBackup.LIMITS.keySet()) {
            if (o.has(key)
                    && (!(o.get(key) instanceof Integer n)
                            || n < 0
                            || n > SettingsBackup.LIMITS.get(key)))
                throw new IOException("Ungültige Einstellung: " + key);
        }
        for (String key : SettingsBackup.BOOLEAN)
            if (o.has(key) && !(o.get(key) instanceof Boolean))
                throw new IOException("Ungültige Einstellung: " + key);
        if (o.has("speed")
                && (!(o.get("speed") instanceof Number n)
                        || !List.of(.75, 1., 1.25, 1.5, 2.).contains(n.doubleValue())))
            throw new IOException("Ungültiges Wiedergabetempo");
        if (o.has("skipSeconds") && !List.of(5, 10, 15, 30).contains(o.get("skipSeconds")))
            throw new IOException("Ungültiges Sprungintervall");
        if (o.has(UploadDelay.KEY)) UploadDelay.minutes(o.getString(UploadDelay.KEY));
        if (o.has("folders")) {
            JSONArray folders = o.getJSONArray("folders");
            for (int i = 0; i < folders.length(); i++)
                if (!LibraryFiles.validFolderPath(folders.getString(i)))
                    throw new IOException("Ungültiger Bibliotheksordner");
        }
        if (!LibraryFiles.validFolderPath(o.optString("folder"))
                || !LibraryFiles.validFolderPath(o.optString("recordingBase")))
            throw new IOException("Ungültiger Aufnahmeordner");
        if (o.has("profiles")) {
            JSONArray profiles = new JSONArray(o.getString("profiles"));
            for (int i = 0; i < profiles.length(); i++) Profile.parse(profiles.getJSONObject(i));
        }
        JSONObject result = new JSONObject();
        JSONObject sealed =
                scope == BackupArchive.Scope.FULL ? new JSONObject() : read(c, "credentials");
        Map<String, CloudConfig> targets = new LinkedHashMap<>();
        if (scope != BackupArchive.Scope.FULL)
            for (CloudConfig t : CloudConfig.all(c)) targets.put(t.id(), t);
        String standard = prior.optString("defaultTarget");
        if (scope != BackupArchive.Scope.SETTINGS) {
            standard = data.getString("defaultTarget");
            JSONArray imported = data.getJSONArray("connections");
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < imported.length(); i++) {
                JSONObject row = imported.getJSONObject(i);
                CloudConfig t = CloudConfig.parse(row);
                if (!seen.add(t.id())
                        || targets.containsKey(t.id()) && !targets.get(t.id()).equals(t))
                    throw new IOException("Widersprüchliches Uploadziel");
                targets.put(t.id(), t);
                if (row.has("credential"))
                    sealed.put(t.id(), CloudConfig.box(t.id(), row.getString("credential")));
            }
            result.put("credentials", sealed);
        }
        JSONArray list = new JSONArray();
        for (CloudConfig t : targets.values()) list.put(t.json());
        o.put("targets", list.toString())
                .put("defaultTarget", standard)
                .put(
                        "autoUpload",
                        o.optBoolean("autoUpload")
                                && targets.containsKey(standard)
                                && sealed.has(standard));
        result.put("options", o);
        boolean lock =
                data.getBoolean("locked") || ((Audomo) c.getApplicationContext()).appLock.enabled();
        if (lock
                && c.getSystemService(android.hardware.biometrics.BiometricManager.class)
                                .canAuthenticate(AppLock.METHODS)
                        != android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS)
            throw new IOException(
                    "Für die gesicherte App-Sperre zuerst eine Android-Gerätesperre einrichten");
        result.put("app_lock", new JSONObject().put("enabled", lock));
        if (scope == BackupArchive.Scope.FULL) {
            JSONObject draft = data.getJSONObject("draft");
            if (!same) draft.remove("event");
            result.put("next-recording", draft);
        }
        return result;
    }
}
