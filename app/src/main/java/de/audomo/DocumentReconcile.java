package de.audomo;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import org.json.JSONObject;

import java.io.*;
import java.nio.file.*;
import java.util.*;

final class DocumentReconcile {
    private record Place(Uri uri, String folder, String name, long size) {}

    private record Known(File dir, File audio, JSONObject meta, String hash, long bytes) {}

    static String run(SessionStore store) throws Exception {
        String tree = Profile.prefs(store.context).getString("documentTree", "");
        if (tree.isEmpty()) throw new IOException("Zuerst einen Android-Ablageort wählen");
        ContentResolver resolver = store.context.getContentResolver();
        List<Known> known = new ArrayList<>();
        Set<String> reserved = new HashSet<>();
        Map<String, SessionStore.Entry> catalog = new CloudLibrary(store).known();
        synchronized (store) {
            for (File dir : store.directories()) {
                if (!new File(dir, "session.json").exists()) continue;
                JSONObject m = SessionStore.metadata(dir);
                if (!tree.equals(m.optString("documentTree"))
                        || !m.has("deliveredUri")
                        || !m.has("frames")
                        || m.optLong("trashed") != 0
                        || m.has("pendingUri")
                        || m.has("obsoleteUri")) continue;
                File audio = new File(dir, m.getString("name"));
                String hash = m.optString("deliveredHash");
                long bytes = audio.isFile() ? audio.length() : -1;
                if (hash.isEmpty() && audio.isFile())
                    hash = LibraryFiles.digest(new FileInputStream(audio));
                if (hash.isEmpty() && catalog.containsKey(dir.getName())) {
                    for (UploadQueue.Job j : UploadQueue.get(store.context).jobs())
                        if (j.rec().equals(dir.getName()) && j.knownCopy()) {
                            hash = j.verified();
                            break;
                        }
                }
                reserved.add(m.getString("deliveredUri"));
                known.add(new Known(dir, audio, m, hash, bytes));
            }
        }
        if (known.isEmpty()) return "Für diesen Ablageort sind keine Ordnerkopien gespeichert.";
        Map<String, Place> index = inventory(resolver, Uri.parse(tree));
        Map<String, String> hashes = new HashMap<>();
        int checked = 0, changed = 0, open = 0;
        for (Known k : known) {
            String previous = k.meta.getString("deliveredUri");
            Place found = index.get(previous);
            if (k.hash.isEmpty()) {
                open++;
                continue;
            }
            if (found == null || !k.hash.equals(hash(resolver, found, hashes))) {
                List<Place> matches = new ArrayList<>();
                for (Place p : index.values()) {
                    if (reserved.contains(p.uri.toString())
                            || k.bytes >= 0 && p.size >= 0 && k.bytes != p.size) continue;
                    if (k.hash.equals(hash(resolver, p, hashes))) matches.add(p);
                }
                // Two lost originals with the same audio cannot be assigned by content alone.
                boolean ambiguous =
                        known.stream()
                                .anyMatch(other ->
                                        other != k
                                                && other.hash.equals(k.hash)
                                                && !index.containsKey(
                                                        other.meta.optString("deliveredUri")));
                if (matches.size() != 1 || ambiguous) {
                    open++;
                    continue;
                }
                found = matches.get(0);
            }
            if (!LibraryFiles.validFolderPath(found.folder)
                    || !found.name.equals(SessionStore.cleanComponent(found.name))) {
                open++;
                continue;
            }
            synchronized (store) {
                JSONObject m = SessionStore.metadata(k.dir);
                if (!m.toString().equals(k.meta.toString())) {
                    open++;
                    continue;
                }
                String available = store.availableName(found.name, found.folder, k.dir);
                if (!available.equals(found.name)) {
                    open++;
                    continue;
                }
                new LibraryFiles(store).ensureFolder(found.folder);
                boolean missing = !k.audio.isFile();
                if (missing) {
                    File temp = File.createTempFile(".abgleich-", ".part", k.dir);
                    try {
                        try (InputStream in = resolver.openInputStream(found.uri);
                                FileOutputStream out = new FileOutputStream(temp)) {
                            if (in == null) throw new IOException("Ordnerkopie nicht lesbar");
                            in.transferTo(out);
                            out.getFD().sync();
                        }
                        if (!LibraryFiles.digest(new FileInputStream(temp)).equals(k.hash))
                            throw new IOException("Ordnerkopie während der Prüfung geändert");
                        Files.move(temp.toPath(), k.audio.toPath(), StandardCopyOption.ATOMIC_MOVE);
                    } finally {
                        Files.deleteIfExists(temp.toPath());
                    }
                }
                if (missing
                        || !found.uri.toString().equals(previous)
                        || !found.folder.equals(m.optString("folder"))
                        || !found.name.equals(m.optString("displayName", k.audio.getName())))
                    changed++;
                m.put("deliveredUri", found.uri.toString())
                        .put("deliveredHash", k.hash)
                        .put("folder", found.folder)
                        .put("displayName", found.name);
                SessionStore.storeMeta(k.dir, m);
                UploadQueue queue = UploadQueue.get(store.context);
                if (queue.hasCloud(k.dir.getName()))
                    CloudLibrary.remember(queue.getWritableDatabase(), SessionStore.entry(k.dir));
                checked++;
            }
        }
        return checked
                + " Ordnerkopien bestätigt · "
                + changed
                + " aktualisiert · "
                + open
                + " ungeklärt";
    }

    private static String hash(ContentResolver resolver, Place p, Map<String, String> cache)
            throws Exception {
        String key = p.uri.toString();
        if (!cache.containsKey(key))
            cache.put(key, LibraryFiles.digest(resolver.openInputStream(p.uri)));
        return cache.get(key);
    }

    private static Map<String, Place> inventory(ContentResolver resolver, Uri tree)
            throws Exception {
        String root = DocumentsContract.getTreeDocumentId(tree);
        Map<String, Place> result = new HashMap<>();
        ArrayDeque<Place> work = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        work.add(new Place(DocumentsContract.buildDocumentUriUsingTree(tree, root), "", "", -1));
        while (!work.isEmpty()) {
            Place current = work.removeFirst();
            String id = DocumentsContract.getDocumentId(current.uri);
            if (!visited.add(id)) continue;
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id);
            try (Cursor c =
                    resolver.query(
                            children,
                            new String[] {
                                Document.COLUMN_DOCUMENT_ID,
                                Document.COLUMN_DISPLAY_NAME,
                                Document.COLUMN_MIME_TYPE,
                                Document.COLUMN_SIZE
                            },
                            null,
                            null,
                            null)) {
                if (c == null) throw new IOException("Android-Ablageort nicht vollständig lesbar");
                while (c.moveToNext()) {
                    Uri uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(0));
                    String name = c.getString(1);
                    if (name == null || name.contains("/")) continue;
                    if (Document.MIME_TYPE_DIR.equals(c.getString(2)))
                        work.add(new Place(uri, FolderManager.join(current.folder, name), "", -1));
                    else
                        result.put(
                                uri.toString(),
                                new Place(
                                        uri,
                                        current.folder,
                                        name,
                                        c.isNull(3) ? -1 : c.getLong(3)));
                }
            }
        }
        return result;
    }
}
