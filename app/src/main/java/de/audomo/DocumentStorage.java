package de.audomo;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;

import org.json.JSONObject;

import java.io.*;
import java.util.*;

final class DocumentStorage {
    static Uri root(Uri tree) {
        return DocumentsContract.buildDocumentUriUsingTree(
                tree, DocumentsContract.getTreeDocumentId(tree));
    }

    private static Uri child(ContentResolver r, Uri parent, String name) throws IOException {
        Uri children =
                DocumentsContract.buildChildDocumentsUriUsingTree(
                        parent, DocumentsContract.getDocumentId(parent));
        try (Cursor c =
                r.query(
                        children,
                        new String[] {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME},
                        null,
                        null,
                        null)) {
            if (c == null) throw new IOException("Ordner nicht lesbar");
            while (c.moveToNext())
                if (name.equals(c.getString(1)))
                    return DocumentsContract.buildDocumentUriUsingTree(parent, c.getString(0));
            return null;
        }
    }

    static void select(Context c, Uri tree) throws Exception {
        ContentResolver r = c.getContentResolver();
        child(r, root(tree), ".nomedia");
        SharedPreferences prefs = Profile.prefs(c);
        String old = prefs.getString("documentTree", "");
        if (!old.equals(tree.toString())) leaveCurrent(c);
        prefs.edit().putString("documentTree", tree.toString()).commit();
    }

    static void leaveCurrent(Context c) throws Exception {
        SharedPreferences settings = Profile.prefs(c);
        if (settings.contains("documentTree") && settings.contains("ownMarker")) hide(c, false);
        settings.edit().remove("documentTree").remove("ownMarker").remove("hidden").commit();
    }

    static void hide(Context c, boolean active) throws Exception {
        SharedPreferences p = Profile.prefs(c);
        String tree = p.getString("documentTree", "");
        if (tree.isEmpty())
            throw new IOException(
                    "Der private App-Speicher ist bereits vor der Mediensuche verborgen");
        ContentResolver r = c.getContentResolver();
        Uri parent = root(Uri.parse(tree));
        Uri marker = child(r, parent, ".nomedia");
        if (active && marker == null) {
            Uri made =
                    DocumentsContract.createDocument(
                            r, parent, "application/octet-stream", ".nomedia");
            if (made == null || !made.equals(child(r, parent, ".nomedia")))
                throw new IOException("Provider unterstützt .nomedia nicht");
            p.edit().putString("ownMarker", made.toString()).commit();
        } else if (!active && marker != null) {
            if (!marker.toString().equals(p.getString("ownMarker", "")))
                throw new IOException("Der bestehende .nomedia-Eintrag stammt nicht von Audomo");
            if (!DocumentsContract.deleteDocument(r, marker))
                throw new IOException("Marker konnte nicht entfernt werden");
            p.edit().remove("ownMarker").commit();
        }
        p.edit().putBoolean("hidden", active).commit();
    }

    static List<Uri> audioDocuments(Context c, Uri tree) throws Exception {
        String selected = Profile.prefs(c).getString("documentTree", "");
        if (!selected.isEmpty() && tree.getAuthority().equals(Uri.parse(selected).getAuthority())) {
            String src = DocumentsContract.getTreeDocumentId(tree),
                    dest = DocumentsContract.getTreeDocumentId(Uri.parse(selected));
            if (src.equals(dest) || dest.startsWith(src + "/") || src.startsWith(dest + "/"))
                throw new IOException("Getrennten Quell- und Zielordner wählen");
        }
        List<Uri> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        ArrayDeque<Uri> dirs = new ArrayDeque<>();
        dirs.add(root(tree));
        while (!dirs.isEmpty()) {
            String dir = DocumentsContract.getDocumentId(dirs.removeFirst());
            if (!seen.add(dir)) continue;
            Uri query = DocumentsContract.buildChildDocumentsUriUsingTree(tree, dir);
            try (Cursor rows =
                    c.getContentResolver()
                            .query(
                                    query,
                                    new String[] {
                                        Document.COLUMN_DOCUMENT_ID,
                                        Document.COLUMN_MIME_TYPE,
                                        Document.COLUMN_DISPLAY_NAME
                                    },
                                    null,
                                    null,
                                    null)) {
                if (rows == null) throw new IOException("Quellordner nicht lesbar");
                while (rows.moveToNext()) {
                    Uri child =
                            DocumentsContract.buildDocumentUriUsingTree(tree, rows.getString(0));
                    String type = rows.getString(1);
                    if (Document.MIME_TYPE_DIR.equals(type)) dirs.add(child);
                    else if ((type != null && type.startsWith("audio/"))
                            || LibraryFiles.mime(rows.getString(2)).startsWith("audio/")
                                    && !LibraryFiles.mime(rows.getString(2)).equals("audio/*"))
                        result.add(child);
                }
            }
        }
        return result;
    }

    static void updateLocation(Context c, File audio, JSONObject meta) throws Exception {
        storeLocation(audio, meta);
        deliver(c, audio);
    }

    static void storeLocation(File audio, JSONObject meta) throws Exception {
        if (meta.has("deliveredUri")) {
            meta.put("obsoleteUri", meta.getString("deliveredUri"));
            meta.remove("deliveredUri");
        }
        SessionStore.storeMeta(audio.getParentFile(), meta);
    }

    static void deliver(Context c, File audio) throws Exception {
        File dir = audio.getParentFile();
        JSONObject meta = SessionStore.metadata(dir);
        String tree = meta.optString("documentTree");
        if (tree.isEmpty()) return;
        if (meta.optLong("trashed") != 0 || meta.has("deliveredUri")) {
            retirePrevious(c, audio, meta);
            return;
        }
        ContentResolver r = c.getContentResolver();
        Uri parent = root(Uri.parse(tree));
        for (String segment : meta.optString("folder").split("/")) {
            if (segment.isEmpty()) continue;
            Uri sub = child(r, parent, segment);
            if (sub == null)
                sub = DocumentsContract.createDocument(r, parent, Document.MIME_TYPE_DIR, segment);
            if (sub == null) throw new IOException("Zielordner nicht beschreibbar");
            parent = sub;
        }
        Uri target;
        if (meta.has("pendingUri")) {
            target = Uri.parse(meta.getString("pendingUri"));
            // After a restart the provider document may already be complete or have been edited.
            // A new provider document protects existing bytes, including an interrupted partial
            // copy.
            if (LibraryFiles.digest(new FileInputStream(audio))
                    .equals(LibraryFiles.digest(r.openInputStream(target)))) {
                meta.put("deliveredUri", target.toString());
                meta.remove("pendingUri");
                SessionStore.storeMeta(dir, meta);
                retirePrevious(c, audio, meta);
                return;
            }
        }
        target =
                DocumentsContract.createDocument(
                        r,
                        parent,
                        LibraryFiles.mime(audio.getName()),
                        meta.optString("displayName", audio.getName()));
        if (target == null) throw new IOException("Zieldatei konnte nicht angelegt werden");
        meta.put("pendingUri", target.toString());
        SessionStore.storeMeta(dir, meta);
        try (OutputStream out = r.openOutputStream(target, "wt");
                InputStream in = new FileInputStream(audio)) {
            if (out == null) throw new IOException("Ziel ist nicht beschreibbar");
            in.transferTo(out);
        }
        String verified = LibraryFiles.digest(new FileInputStream(audio));
        if (!verified.equals(LibraryFiles.digest(r.openInputStream(target))))
            throw new IOException("Kopie im Aufnahmeordner nicht verifiziert");
        meta.put("deliveredUri", target.toString()).put("deliveredHash", verified);
        meta.remove("pendingUri");
        SessionStore.storeMeta(dir, meta);
        retirePrevious(c, audio, meta);
    }

    private static void retirePrevious(Context c, File audio, JSONObject meta) throws Exception {
        if (!meta.has("obsoleteUri")) return;
        Uri previous = Uri.parse(meta.getString("obsoleteUri"));
        ContentResolver r = c.getContentResolver();
        try (InputStream in = r.openInputStream(previous)) {
            if (in == null
                    || !LibraryFiles.digest(in)
                            .equals(LibraryFiles.digest(new FileInputStream(audio))))
                throw new IOException(
                        "Frühere Ordnerkopie wurde extern verändert und bleibt erhalten");
        } catch (FileNotFoundException e) {
            // A revoked grant must remain retryable; absence is confirmed by a successful provider
            // query.
            try (Cursor cur =
                    r.query(
                            previous,
                            new String[] {Document.COLUMN_DOCUMENT_ID},
                            null,
                            null,
                            null)) {
                if (cur == null || cur.moveToFirst()) throw e;
            }
            meta.remove("obsoleteUri");
            SessionStore.storeMeta(audio.getParentFile(), meta);
            return;
        }
        if (!DocumentsContract.deleteDocument(r, previous))
            throw new IOException("Frühere Ordnerkopie konnte nicht entfernt werden");
        meta.remove("obsoleteUri");
        SessionStore.storeMeta(audio.getParentFile(), meta);
    }
}
