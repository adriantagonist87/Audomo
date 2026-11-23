package de.audomo;

import android.app.AlertDialog;
import android.widget.*;

import java.util.*;

final class FolderSettings {
    final MainActivity screen;
    final FolderManager manager;
    final AppSettings settings;
    AppSettings.Page page;

    FolderSettings(MainActivity screen) {
        this.screen = screen;
        manager = new FolderManager(((Audomo) screen.getApplication()).sessions);
        settings = new AppSettings(screen);
    }

    void show() {
        page = settings.new Page("Ordner verwalten");
        draw();
        page.dialog.show();
    }

    private static String label(String folder) {
        return folder.isEmpty() ? "Aufnahmen" : folder.replace("/", " › ");
    }

    private void draw() {
        page.rows.removeAllViews();
        if (manager.files.prefs.contains("pendingFolderChange")) {
            page.row(
                    "Ordneränderung fortsetzen",
                    "Unterbrochene Änderung erneut versuchen",
                    () -> run(() -> manager.resume()));
            return;
        }
        page.row("Neuer Ordner", "Im Hauptordner anlegen", () -> name("", "", true));
        if (manager.files.prefs.contains("documentTree"))
            page.row(
                    "Android-Ablageort abgleichen",
                    "Verschobene Ordnerkopien wiederfinden",
                    () -> {
                        screen.toast("Ablageort wird geprüft");
                        screen.runFiles(() -> {
                            String result = DocumentReconcile.run(manager.files.store);
                            screen.runOnUiThread(() -> {
                                if (page.dialog.isShowing()) draw();
                            });
                            return result;
                        });
                    });
        try {
            List<CloudConfig> targets = CloudConfig.all(screen);
            targets.removeIf(t -> !t.hasCredentials(screen));
            if (!targets.isEmpty())
                page.row(
                        "Cloudpfade abgleichen",
                        "Bestätigte Kopien beim Dienst prüfen",
                        () ->
                                new AlertDialog.Builder(screen)
                                        .setTitle("Dienst zum Abgleichen wählen")
                                        .setItems(
                                                targets.stream()
                                                        .map(CloudConfig::uploadLabel)
                                                        .toArray(String[]::new),
                                                (dialog, index) -> {
                                                    screen.toast("Cloudpfade werden geprüft");
                                                    screen.runFiles(() -> {
                                                        String result =
                                                                CloudReconcile.run(
                                                                        manager.files.store,
                                                                        targets.get(index));
                                                        screen.runOnUiThread(() -> {
                                                            if (page.dialog.isShowing()) draw();
                                                        });
                                                        return result;
                                                    });
                                                })
                                        .setNegativeButton("Abbrechen", null)
                                        .show());
        } catch (Exception error) {
            screen.toast(Audomo.cause(error));
        }
        for (String path : manager.files.folders()) {
            if (path.isEmpty()) continue;
            page.row(label(path), "", () -> actions(path));
        }
    }

    private void actions(String path) {
        new AlertDialog.Builder(screen)
                .setTitle(label(path))
                .setItems(
                        new String[] {
                            "Unterordner anlegen", "Umbenennen", "Verschieben", "Ordner löschen"
                        },
                        (d, i) -> {
                            if (i == 0) name(path, "", true);
                            if (i == 1) name(path, RemoteFiles.leaf(path), false);
                            if (i == 2) destination(path);
                            if (i == 3) confirm(path, FolderManager.parent(path), "", true);
                        })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void name(String path, String initial, boolean create) {
        EditText input = new EditText(screen);
        input.setSingleLine();
        input.setHint("Ordnername");
        input.setText(initial);
        input.selectAll();
        new AlertDialog.Builder(screen)
                .setTitle(create ? "Neuer Ordner in " + label(path) : "Ordner umbenennen")
                .setView(input)
                .setPositiveButton(
                        create ? "Anlegen" : "Weiter",
                        (d, w) -> {
                            String name = input.getText().toString();
                            if (create) run(() -> manager.files.addFolder(path, name));
                            else confirm(path, FolderManager.parent(path), name, false);
                        })
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void destination(String path) {
        List<String> options = new ArrayList<>(manager.files.folders());
        options.removeIf(p -> FolderManager.below(p, path) || p.equals(FolderManager.parent(path)));
        if (options.isEmpty()) {
            screen.toast("Kein anderer Zielordner vorhanden");
            return;
        }
        new AlertDialog.Builder(screen)
                .setTitle("Zielordner wählen")
                .setItems(
                        options.stream().map(FolderSettings::label).toArray(String[]::new),
                        (d, w) -> confirm(path, options.get(w), RemoteFiles.leaf(path), false))
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    private void confirm(String path, String parent, String name, boolean remove) {
        LinearLayout content = screen.column();
        content.setPadding(screen.dp(20), screen.dp(8), screen.dp(20), 0);
        content.addView(
                screen.text(
                        remove
                                ? "Aufnahmen und Unterordner werden nach „"
                                        + label(parent)
                                        + "“ verschoben."
                                : "Neuer Pfad: " + label(FolderManager.join(parent, name)),
                        16,
                        MainActivity.INK));
        Switch sync = new Switch(screen);
        sync.setText("Bestätigte Cloudkopien ebenfalls verschieben");
        content.addView(sync);
        new AlertDialog.Builder(screen)
                .setTitle(remove ? "Ordner „" + label(path) + "“ löschen?" : "Ordner ändern")
                .setView(content)
                .setPositiveButton(
                        remove ? "Ordner löschen" : "Speichern",
                        (d, w) ->
                                run(() ->
                                        manager.change(
                                                path, parent, name, remove, sync.isChecked())))
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    interface Change {
        void apply() throws Exception;
    }

    private void run(Change change) {
        screen.runFiles(() -> {
            try {
                change.apply();
                return "Ordnerverwaltung aktualisiert";
            } finally {
                screen.runOnUiThread(() -> {
                    if (page.dialog.isShowing()) draw();
                });
            }
        });
    }
}
