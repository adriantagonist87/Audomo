package de.audomo;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import org.json.JSONArray;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

final class BackupDatabase {
    static final List<String> TABLES = List.of("recordings", "jobs");

    static void exportTable(SQLiteDatabase db, String table, File file) throws Exception {
        try (Cursor c = db.query(table, null, null, null, null, null, null);
                BufferedWriter out =
                        new BufferedWriter(
                                new OutputStreamWriter(
                                        new FileOutputStream(file), StandardCharsets.UTF_8))) {
            out.write(new JSONArray(Arrays.asList(c.getColumnNames())).toString());
            out.newLine();
            while (c.moveToNext()) {
                JSONArray row = new JSONArray();
                for (int i = 0; i < c.getColumnCount(); i++) {
                    if (c.isNull(i)) row.put(org.json.JSONObject.NULL);
                    else if (c.getType(i) == Cursor.FIELD_TYPE_INTEGER) row.put(c.getLong(i));
                    else row.put(c.getString(i));
                }
                out.write(row.toString());
                out.newLine();
            }
        }
    }

    static void prepare(File stage) throws Exception {
        try (SQLiteDatabase db =
                SQLiteDatabase.openOrCreateDatabase(new File(stage, "restored.db"), null)) {
            UploadQueue.createRecordings(db);
            UploadQueue.createJobs(db);
            db.beginTransaction();
            try {
                for (String table : TABLES) loadTable(db, table, new File(stage, table + ".jsonl"));
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
    }

    private static void loadTable(SQLiteDatabase db, String table, File file) throws Exception {
        try (BufferedReader in =
                        new BufferedReader(
                                new InputStreamReader(
                                        new FileInputStream(file), StandardCharsets.UTF_8));
                Cursor schema = db.query(table, null, null, null, null, null, null, "0")) {
            JSONArray header = new JSONArray(line(in));
            Set<String> columns = new HashSet<>(Arrays.asList(schema.getColumnNames()));
            if (header.length() != columns.size())
                throw new IOException("Katalogschema unvollständig");
            for (int i = 0; i < header.length(); i++)
                if (!columns.remove(header.getString(i)))
                    throw new IOException("Unbekannte Katalogspalte");
            String line;
            while ((line = line(in)) != null) {
                JSONArray row = new JSONArray(line);
                if (row.length() != header.length())
                    throw new IOException("Katalogzeile unvollständig");
                ContentValues values = new ContentValues();
                for (int i = 0; i < header.length(); i++) {
                    String key = header.getString(i);
                    if (row.isNull(i)) values.putNull(key);
                    else if (row.get(i) instanceof Number n) values.put(key, n.longValue());
                    else values.put(key, row.getString(i));
                }
                db.insertOrThrow(table, null, values);
            }
        }
    }

    // Bound a single piece of metadata; audio is stored in separate streaming records.
    private static String line(BufferedReader in) throws IOException {
        StringBuilder result = new StringBuilder();
        for (int ch; (ch = in.read()) != -1; ) {
            if (ch == '\n') return result.toString();
            if (result.length() >= 1024 * 1024) throw new IOException("Katalogeintrag zu groß");
            result.append((char) ch);
        }
        return result.length() == 0 ? null : result.toString();
    }

    // Caller owns the live database transaction. The staged DB was constructed from trusted DDL.
    static void replace(SQLiteDatabase live, File stage) {
        try (SQLiteDatabase saved =
                SQLiteDatabase.openDatabase(
                        new File(stage, "restored.db").getPath(),
                        null,
                        SQLiteDatabase.OPEN_READONLY)) {
            for (String table : TABLES) {
                live.delete(table, null, null);
                try (Cursor rows = saved.query(table, null, null, null, null, null, null)) {
                    while (rows.moveToNext()) {
                        ContentValues values = new ContentValues();
                        DatabaseUtils.cursorRowToContentValues(rows, values);
                        live.insertOrThrow(table, null, values);
                    }
                }
            }
        }
    }
}
