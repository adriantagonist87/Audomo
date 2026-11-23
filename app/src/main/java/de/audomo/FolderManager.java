package de.audomo;

import android.content.SharedPreferences;

import org.json.*;

import java.io.*;
import java.util.*;

final class FolderManager {
    private static final String PENDING = "pendingFolderChange";
    final LibraryFiles files;
    final CloudLibrary cloud;

    FolderManager(SessionStore store) {
        files = new LibraryFiles(store);
        cloud = new CloudLibrary(store);
    }

    static boolean below(String path, String source) {
        return path.equals(source) || path.startsWith(source + "/");
    }

    static void checkWritable(SharedPreferences prefs, String folder) throws IOException {
        String pending = prefs.getString(PENDING, "");
        if (pending.isEmpty()) return;
        try {
            JSONObject plan = new JSONObject(pending);
            if (below(folder, plan.getString("from")) || below(folder, plan.getString("to")))
                throw new IOException(
                        "Zuerst die Ordneränderung unter „Ordner verwalten“ fortsetzen");
        } catch (JSONException e) {
            throw new IOException("Ordneränderung nicht lesbar", e);
        }
    }

    static String parent(String path) {
        int end = path.lastIndexOf('/');
        return end < 0 ? "" : path.substring(0, end);
    }

    static String join(String parent, String name) {
        return parent.isEmpty() ? name : parent + "/" + name;
    }

    private static String remap(String path, String source, String dest) {
        if (!below(path, source)) return path;
        if (path.equals(source)) return dest;
        return join(dest, path.substring(source.length() + 1));
    }

    void change(String source, String parent, String newName, boolean dissolve, boolean follow)
            throws Exception {
        synchronized (files.store) {
            resume();
            Set<String> folders = files.folders();
            if (source.isEmpty()
                    || !folders.contains(source)
                    || !folders.contains(parent)
                    || below(parent, source)) throw new IOException("Ungültiger Zielordner");
            String dest = dissolve ? parent(source) : join(parent, LibraryFiles.title(newName));
            if (source.equals(dest)) return;
            Set<String> next = new TreeSet<>(folders);
            next.removeIf(p -> below(p, source));
            for (String path : folders)
                if (below(path, source)) {
                    String mapped = remap(path, source, dest);
                    if (below(mapped, source))
                        throw new IOException(
                                "Unterordner vor dem Löschen umbenennen oder verschieben");
                    if (!next.add(mapped) && !(dissolve && path.equals(source)))
                        throw new IOException("Am Ziel gibt es bereits den Ordner " + mapped);
                }
            Map<String, SessionStore.Entry> entries = new LinkedHashMap<>(cloud.known());
            for (SessionStore.Entry e : files.store.list()) entries.put(e.id(), e);
            for (File dir : files.store.directories()) {
                if (!new File(dir, "session.json").isFile()) continue;
                JSONObject meta = SessionStore.metadata(dir);
                if (below(meta.optString("folder"), source)
                        && (new File(dir, "capture.part").exists() || !meta.has("frames")))
                    throw new IOException(
                            "Unabgeschlossene Aufnahme in diesem Ordner zuerst sichern");
            }
            Set<String> occupied = new HashSet<>();
            for (SessionStore.Entry e : entries.values())
                if (!below(e.folder(), source) && e.trashed() == 0)
                    occupied.add(e.folder() + "/" + e.name().toLowerCase(Locale.ROOT));
            JSONArray changes = new JSONArray();
            List<UploadQueue.Job> jobs = cloud.queue.jobs();
            for (SessionStore.Entry e : entries.values()) {
                if (!below(e.folder(), source)) continue;
                String folder = remap(e.folder(), source, dest);
                if (e.trashed() == 0
                        && !occupied.add(folder + "/" + e.name().toLowerCase(Locale.ROOT)))
                    throw new IOException("Dateiname am Ziel bereits belegt: " + e.name());
                if (follow
                        && jobs.stream()
                                .anyMatch(j ->
                                        j.rec().equals(e.id())
                                                && List.of(
                                                                "Wartet",
                                                                "Upload läuft",
                                                                "Konflikt",
                                                                "Fehler")
                                                        .contains(j.state())))
                    throw new IOException(
                            "Zuerst offene Cloudaufträge für diesen Ordner abschließen");
                changes.put(new JSONObject().put("id", e.id()).put("folder", folder));
            }
            JSONObject plan =
                    new JSONObject()
                            .put("from", source)
                            .put("to", dest)
                            .put("folders", new JSONArray(next))
                            .put("files", changes)
                            .put("follow", follow);
            if (!files.prefs.edit().putString(PENDING, plan.toString()).commit())
                throw new IOException("Ordneränderung konnte nicht gesichert werden");
            resume();
        }
    }

    void resume() throws Exception {
        synchronized (files.store) {
            String raw = files.prefs.getString(PENDING, "");
            if (raw.isEmpty()) return;
            JSONObject plan = new JSONObject(raw);
            String from = plan.getString("from"), to = plan.getString("to");
            Set<String> folders = files.folders();
            JSONArray paths = plan.getJSONArray("folders");
            for (int i = 0; i < paths.length(); i++) folders.add(paths.getString(i));
            if (!files.prefs.edit().putStringSet("folders", folders).commit())
                throw new IOException("Speichern fehlgeschlagen");
            Map<String, SessionStore.Entry> known = cloud.known();
            JSONArray items = plan.getJSONArray("files");
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                String id = item.getString("id"), folder = item.getString("folder");
                SessionStore.Entry local = files.store.byId(id);
                SessionStore.Entry current = local != null ? local : known.get(id);
                if (current == null) continue;
                File dir = current.file().getParentFile();
                if (new File(dir, "session.json").isFile()) {
                    JSONObject meta = SessionStore.metadata(dir);
                    if (!folder.equals(meta.optString("folder"))) {
                        if (local != null && (meta.has("pendingUri") || meta.has("obsoleteUri")))
                            DocumentStorage.deliver(files.store.context, local.file());
                        meta = SessionStore.metadata(dir);
                        meta.put("folder", folder);
                        if (local == null) SessionStore.storeMeta(dir, meta);
                        else
                            DocumentStorage.updateLocation(files.store.context, local.file(), meta);
                    } else if (local != null)
                        DocumentStorage.deliver(files.store.context, local.file());
                }
                cloud.queue
                        .getWritableDatabase()
                        .execSQL(
                                "UPDATE recordings SET folder=? WHERE rec=?",
                                new Object[] {folder, id});
                if (plan.getBoolean("follow")) {
                    SessionStore.Entry updated =
                            new SessionStore.Entry(
                                    current.file(),
                                    current.frames(),
                                    current.start(),
                                    current.name(),
                                    folder,
                                    current.trashed(),
                                    current.cloudOnly(),
                                    current.bytes());
                    cloud.queue.followLocalChange(updated, false);
                }
            }
            folders = files.folders();
            folders.removeIf(p -> below(p, from));
            SharedPreferences.Editor edit =
                    files.prefs.edit().putStringSet("folders", folders).remove(PENDING);
            for (String key : List.of("folder", "recordingBase"))
                edit.putString(key, remap(files.prefs.getString(key, ""), from, to));
            if (!edit.commit()) throw new IOException("Ordneränderung nicht abgeschlossen");
        }
    }
}
