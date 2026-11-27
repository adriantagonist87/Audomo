package de.audomo;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.util.Arrays;

final class BackupDialogs {
    static final int EXPORT = 31, IMPORT = 34;
    private static final String[] NAMES = {
        "Alle Einstellungen", "Einstellungen und Cloudverbindungen", "Vollständige Migration"
    };
    private final MainActivity screen;
    private final Audomo app;

    BackupDialogs(MainActivity screen) {
        this.screen = screen;
        app = (Audomo) screen.getApplication();
    }

    void export() {
        new AlertDialog.Builder(screen)
                .setTitle("Was möchtest du sichern?")
                .setItems(NAMES, (dialog, index) -> createFile(index))
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void createFile(int index) {
        String[] names = {"Audomo-Einstellungen", "Audomo-Verbindungen", "Audomo-Migration"};
        Intent picker =
                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/octet-stream")
                        .putExtra(Intent.EXTRA_TITLE, names[index] + ".audomo");
        screen.startActivityForResult(picker, EXPORT + index);
    }

    void selectImport() {
        screen.startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("*/*"),
                IMPORT);
    }

    boolean result(int request, Uri uri) {
        if (request >= EXPORT && request < IMPORT) {
            password(uri, BackupArchive.Scope.values()[request - EXPORT], false);
            return true;
        }
        if (request != IMPORT) return false;
        app.libraryFiles.execute(() -> inspectImport(uri));
        return true;
    }

    private void inspectImport(Uri uri) {
        try (BufferedInputStream in = new BufferedInputStream(input(uri))) {
            in.mark(64);
            int first;
            do {
                first = in.read();
            } while (first >= 0 && Character.isWhitespace(first));
            if (first == '{') {
                show(() -> confirmLegacy(uri));
                return;
            }
            in.reset();
            String[] choices = Arrays.copyOf(NAMES, BackupArchive.read(in).scope.ordinal() + 1);
            show(
                    () ->
                            new AlertDialog.Builder(screen)
                                    .setTitle("Was möchtest du wiederherstellen?")
                                    .setItems(
                                            choices,
                                            (d, index) ->
                                                    password(
                                                            uri,
                                                            BackupArchive.Scope.values()[index],
                                                            true))
                                    .setNegativeButton("Abbrechen", null)
                                    .show());
        } catch (Exception e) {
            show(() -> message("Sicherung kann nicht gelesen werden", Audomo.cause(e)));
        }
    }

    private void confirmLegacy(Uri uri) {
        new AlertDialog.Builder(screen)
                .setTitle("Früheren JSON-Export importieren?")
                .setMessage(
                        "Profile und Regeln übernehmen. Dropbox und andere Cloudzugänge können"
                            + " danach neu verbunden werden.")
                .setPositiveButton(
                        "Importieren",
                        (d, w) ->
                                run(
                                        () -> {
                                            try (InputStream in = input(uri)) {
                                                return SettingsBackup.restore(screen, in);
                                            }
                                        }))
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private InputStream input(Uri uri) throws IOException {
        InputStream in = screen.getContentResolver().openInputStream(uri);
        if (in == null) throw new IOException("Datei ist nicht lesbar");
        return in;
    }

    private EditText field(LinearLayout layout, String hint) {
        EditText field = new EditText(screen);
        field.setSingleLine();
        field.setHint(hint);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        field.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        field.setSaveEnabled(false);
        layout.addView(field);
        return field;
    }

    private void password(Uri uri, BackupArchive.Scope scope, boolean restoring) {
        LinearLayout form = screen.column();
        form.setPadding(screen.dp(20), 0, screen.dp(20), 0);
        String detail =
                switch (scope) {
                    case SETTINGS -> "Profile, Kalenderauswahl, Ordner und Bedienoptionen.";
                    case CONNECTIONS ->
                            "Einstellungen und gespeicherte Cloudzugänge einschließlich"
                                    + " Anmeldedaten.";
                    case FULL ->
                            restoring
                                    ? "Ersetzt die lokale Bibliothek, den Papierkorb,"
                                            + " Uploadverlauf, Cloudkatalog, Diagnose und"
                                            + " Einstellungen dieses Geräts durch den gesicherten"
                                            + " Stand."
                                    : "Alle Einstellungen, Zugänge, Aufnahmen, Papierkorb,"
                                          + " Uploadverlauf und Diagnose. Aufnahmen, die nur in der"
                                          + " Cloud liegen, werden geprüft heruntergeladen.";
                };
        form.addView(screen.text(detail, 15, MainActivity.INK));
        form.addView(
                screen.text(
                        restoring
                                ? "Gib das Passwort dieser Sicherung ein."
                                : "Dieses Passwort wird zum Wiederherstellen benötigt. Es lässt"
                                        + " sich nicht zurücksetzen.",
                        14,
                        MainActivity.DIM));
        EditText first = field(form, "Sicherungspasswort");
        EditText repeat = restoring ? null : field(form, "Passwort wiederholen");
        ScrollView scroll = new ScrollView(screen);
        scroll.addView(form);
        AlertDialog dialog =
                new AlertDialog.Builder(screen)
                        .setTitle(NAMES[scope.ordinal()])
                        .setView(scroll)
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton(
                                restoring
                                        ? (scope == BackupArchive.Scope.FULL
                                                ? "Bestand ersetzen"
                                                : "Importieren")
                                        : "Sichern",
                                null)
                        .create();
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        dialog.setOnShowListener(
                ignored ->
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                                .setOnClickListener(
                                        v -> {
                                            char[] key = checkedPassword(first, repeat);
                                            if (key == null) return;
                                            dialog.dismiss();
                                            run(() -> transfer(uri, scope, key, restoring));
                                        }));
        dialog.show();
    }

    private char[] checkedPassword(EditText first, EditText repeat) {
        boolean exporting = repeat != null;
        if (first.length() < (exporting ? 8 : 1)) {
            first.setError(exporting ? "Mindestens acht Zeichen verwenden" : "Passwort eingeben");
            return null;
        }
        char[] key = new char[first.length()];
        first.getText().getChars(0, key.length, key, 0);
        if (exporting) {
            char[] check = new char[repeat.length()];
            repeat.getText().getChars(0, check.length, check, 0);
            boolean same = Arrays.equals(key, check);
            Arrays.fill(check, (char) 0);
            if (!same) {
                Arrays.fill(key, (char) 0);
                repeat.setError("Die Passwörter unterscheiden sich");
                return null;
            }
            repeat.setText("");
        }
        first.setText("");
        return key;
    }

    private String transfer(Uri uri, BackupArchive.Scope scope, char[] key, boolean restoring)
            throws Exception {
        try {
            if (restoring) {
                try (InputStream in = input(uri)) {
                    MigrationBackup.restore(app, in, scope, key);
                }
                return "Wiederherstellung abgeschlossen. Android-Freigaben und Schnellzugriffe auf"
                           + " diesem Gerät prüfen.";
            }
            File staged = File.createTempFile("audomo-encrypted-", ".tmp", app.getCacheDir());
            try {
                try (FileOutputStream file = new FileOutputStream(staged)) {
                    MigrationBackup.export(app, file, scope, key);
                }
                try (OutputStream out = screen.getContentResolver().openOutputStream(uri, "wt");
                        InputStream file = new FileInputStream(staged)) {
                    if (out == null) throw new IOException("Zieldatei ist nicht beschreibbar");
                    file.transferTo(out);
                }
            } finally {
                java.nio.file.Files.deleteIfExists(staged.toPath());
            }
            return "Verschlüsselte Sicherung gespeichert.";
        } finally {
            Arrays.fill(key, (char) 0);
        }
    }

    private void run(java.util.concurrent.Callable<String> work) {
        AlertDialog progress =
                new AlertDialog.Builder(screen)
                        .setTitle("Datensicherung")
                        .setMessage("Vorgang läuft …")
                        .create();
        progress.show();
        app.libraryFiles.execute(
                () -> {
                    String result;
                    boolean success = false;
                    try {
                        result = work.call();
                        success = true;
                    } catch (Exception e) {
                        result =
                                e instanceof IOException && e.getCause() != null
                                        ? "Passwort oder Datei prüfen. " + Audomo.cause(e)
                                        : Audomo.cause(e);
                    }
                    String text = result;
                    boolean ok = success;
                    show(
                            () -> {
                                if (progress.isShowing()) progress.dismiss();
                                if (app.appLock.locked()) {
                                    screen.recreate();
                                    return;
                                }
                                new AlertDialog.Builder(screen)
                                        .setTitle(
                                                ok
                                                        ? "Datensicherung abgeschlossen"
                                                        : "Datensicherung fehlgeschlagen")
                                        .setMessage(text)
                                        .setPositiveButton(
                                                "Schließen",
                                                (d, w) -> {
                                                    if (ok) screen.recreate();
                                                })
                                        .show();
                            });
                });
    }

    private void message(String title, String text) {
        new AlertDialog.Builder(screen)
                .setTitle(title)
                .setMessage(text)
                .setPositiveButton("Schließen", null)
                .show();
    }

    private void show(Runnable r) {
        screen.runOnUiThread(
                () -> {
                    if (!screen.isFinishing() && !screen.isDestroyed()) r.run();
                });
    }
}
