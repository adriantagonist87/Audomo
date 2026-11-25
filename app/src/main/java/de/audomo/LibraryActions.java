package de.audomo;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;

import androidx.core.content.FileProvider;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

final class LibraryActions {
    final MainActivity screen;
    final LibraryFiles files;

    LibraryActions(MainActivity screen, LibraryFiles files) {
        this.screen = screen;
        this.files = files;
    }

    void folder(String current, Consumer<String> picked) {
        List<String> folders = new ArrayList<>(files.folders());
        String[] labels =
                folders.stream()
                        .map(x ->
                                (x.equals(current) ? "✓  " : "")
                                        + (x.isEmpty() ? "Aufnahmen" : x.replace("/", " › ")))
                        .toArray(String[]::new);
        AlertDialog d =
                new AlertDialog.Builder(screen)
                        .setTitle("Ordner wählen")
                        .setItems(labels, (a, i) -> picked.accept(folders.get(i)))
                        .setPositiveButton("Verwalten", (dialog, which) -> new FolderSettings(screen).show())
                        .setNeutralButton("Neuer Ordner", null)
                        .setNegativeButton("Abbrechen", null)
                        .show();
        d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v ->
                input("Unterordner in " + (current.isEmpty() ? "Aufnahmen" : current), "", name -> {
                    try {
                        String path = files.addFolder(current, name);
                        d.dismiss();
                        picked.accept(path);
                    } catch (Exception e) {
                        screen.toast(Audomo.cause(e));
                    }
                }));
        d.getWindow().setGravity(Gravity.BOTTOM);
    }

    private void input(String title, String value, Consumer<String> result) {
        EditText edit = new EditText(screen);
        edit.setSingleLine();
        edit.setText(value);
        edit.selectAll();
        new AlertDialog.Builder(screen)
                .setTitle(title)
                .setView(edit)
                .setPositiveButton("Speichern", (d, w) -> result.accept(edit.getText().toString()))
                .setNegativeButton("Abbrechen", null)
                .show();
    }

    interface Operation {
        void run(SessionStore.Entry entry) throws Exception;
    }

    void perform(List<SessionStore.Entry> list, Operation op) {
        screen.runFiles(() -> {
            List<String> failures = new ArrayList<>();
            for (SessionStore.Entry e : list)
                try {
                    op.run(e);
                } catch (Exception x) {
                    failures.add(e.name() + " · " + Audomo.cause(x));
                }
            return failures.isEmpty() ? list.size() + " bearbeitet" : String.join("\n", failures);
        });
    }

    void rename(SessionStore.Entry e) {
        LinearLayout page = screen.column();
        ScrollView scroll = new ScrollView(screen);
        scroll.addView(page);
        int inset = screen.dp(20);
        page.setPadding(inset, screen.dp(8), inset, screen.dp(8));
        String ext = LibraryFiles.extension(e.name());
        EditText edit = new EditText(screen);
        edit.setHint("Dateiname");
        edit.setSingleLine();
        edit.setText(e.name().substring(0, e.name().length() - ext.length()));
        edit.setSelectAllOnFocus(true);
        page.addView(edit);
        if (!ext.isEmpty()) page.addView(screen.text("Dateiendung " + ext + " bleibt erhalten", 14, MainActivity.DIM));
        page.addView(screen.text("Speicherorte", 16, MainActivity.INK));
        CheckBox local = new CheckBox(screen);
        if (!e.cloudOnly()) {
            local.setText("Auf diesem Gerät");
            local.setChecked(true);
            page.addView(local);
        }
        Map<CheckBox, UploadQueue.Job> choices = new LinkedHashMap<>();
        for (UploadQueue.Job job : UploadQueue.get(screen).jobs()) {
            if (!job.rec().equals(e.id()) || (!job.knownCopy() && !job.open())) continue;
            CheckBox choice = new CheckBox(screen);
            String provider = job.provider().isEmpty() ? "Cloud" : job.provider();
            try { provider = CloudConfig.byId(screen, job.target()).uploadLabel(); }
            catch (Exception missing) { /* Historical copies may outlive their connection. */ }
            String status = !job.availability().isEmpty() ? job.availability() : job.state();
            choice.setText(provider + "\n" + job.confirmedPath() + (job.canRename()
                    ? (job.knownCopy() ? "" : " · Geplanter Upload") : " · " + status));
            choice.setEnabled(job.canRename());
            choice.setChecked(job.canRename() && e.cloudOnly());
            page.addView(choice);
            choices.put(choice, job);
        }
        AlertDialog dialog = new AlertDialog.Builder(screen)
                .setTitle("Aufnahme umbenennen").setView(scroll)
                .setNegativeButton("Abbrechen", null).setPositiveButton("Umbenennen", null).show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String input = edit.getText().toString();
            try { LibraryFiles.renameStem(input, ext); }
            catch (IllegalArgumentException invalid) { edit.setError(invalid.getMessage()); return; }
            List<UploadQueue.Job> selected = new ArrayList<>();
            choices.forEach((choice, job) -> { if (choice.isChecked()) selected.add(job); });
            boolean onDevice = local.isChecked();
            if (!onDevice && selected.isEmpty()) { screen.toast("Bitte einen Speicherort auswählen"); return; }
            dialog.dismiss();
            screen.runFiles(() -> files.rename(e, input, onDevice, selected));
        });
    }

    private Switch cloudSwitch(LinearLayout page, List<SessionStore.Entry> list, String label) {
        Switch toggle = new Switch(screen);
        toggle.setText(label);
        toggle.setMinHeight(screen.dp(48));
        if (list.stream().anyMatch(e -> UploadQueue.get(screen).hasCloud(e.id())))
            page.addView(toggle);
        return toggle;
    }

    private void change(
            SessionStore.Entry e, String name, String folder, boolean trash, boolean remote)
            throws Exception {
        files.change(e, name, folder, trash);
        if (remote)
            UploadQueue.get(screen)
                    .followLocalChange(SessionStore.entry(e.file().getParentFile()), trash);
    }

    void relocate(List<SessionStore.Entry> list, boolean copy) {
        folder(
                list.get(0).folder(),
                folder -> {
                    if (copy) {
                        perform(list, e -> files.copy(e, folder));
                        return;
                    }
                    LinearLayout options = screen.column();
                    Switch cloud = cloudSwitch(options, list, "Cloudkopie ebenfalls verschieben");
                    Runnable move =
                            () ->
                                    perform(
                                            list,
                                            e -> change(e, null, folder, false, cloud.isChecked()));
                    if (options.getChildCount() == 0) move.run();
                    else
                        new AlertDialog.Builder(screen)
                                .setTitle("Verschieben")
                                .setView(options)
                                .setPositiveButton("Verschieben", (d, w) -> move.run())
                                .setNegativeButton("Abbrechen", null)
                                .show();
                });
    }

    void trash(List<SessionStore.Entry> list, boolean purge) {
        LinearLayout page = screen.column();
        page.addView(screen.text(list.size() + " ausgewählt", 16, MainActivity.INK));
        Switch cloud =
                purge ? new Switch(screen) : cloudSwitch(page, list, "Auch Cloudkopie löschen");
        Runnable remove =
                () ->
                        perform(
                                list,
                                e -> {
                                    if (purge) files.purge(e);
                                    else change(e, null, e.folder(), true, cloud.isChecked());
                                });
        new AlertDialog.Builder(screen)
                .setTitle(purge ? "Endgültig löschen?" : "In den Papierkorb?")
                .setView(page)
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton(
                        purge ? "Endgültig löschen" : "In den Papierkorb",
                        (d, w) -> {
                            if (cloud.isChecked())
                                new AlertDialog.Builder(screen)
                                        .setTitle("Auch die bestätigte Cloudkopie löschen?")
                                        .setPositiveButton(
                                                "Cloudlöschung bestätigen", (x, y) -> remove.run())
                                        .setNegativeButton("Abbrechen", null)
                                        .show();
                            else remove.run();
                        })
                .show();
    }

    void restore(List<SessionStore.Entry> list) {
        perform(
                list,
                e ->
                        files.change(
                                e,
                                null,
                                files.folders().contains(e.folder()) ? e.folder() : "",
                                false));
    }

    void more(View anchor, List<SessionStore.Entry> entries) {
        PopupMenu menu = new PopupMenu(screen, anchor);
        if (entries.size() == 1) item(menu, "Umbenennen", () -> rename(entries.get(0)));
        item(menu, "Kopieren", () -> relocate(entries, true));
        item(menu, "Verschieben", () -> relocate(entries, false));
        item(menu, "In den Papierkorb", () -> trash(entries, false));
        if (entries.size() == 1) {
            SessionStore.Entry e = entries.get(0);
            item(
                    menu,
                    "Details",
                    () ->
                            new AlertDialog.Builder(screen)
                                    .setTitle(e.name())
                                    .setMessage(
                                            (e.start().equals(CloudLibrary.UNKNOWN_DATE) ? "Aufnahmedatum unbekannt" : e.start().toString())
                                                    + "\n"
                                                    + Audomo.duration(e.frames())
                                                    + " · "
                                                    + e.file().length()
                                                    + " Bytes\n"
                                                    + (e.folder().isEmpty()
                                                            ? "Aufnahmen"
                                                            : e.folder()))
                                    .setPositiveButton("Schließen", null)
                                    .show());
            item(menu, "Extern öffnen", () -> share(entries, false));
            try {
                org.json.JSONObject meta = SessionStore.metadata(e.file().getParentFile());
                if (!meta.optString("documentTree").isEmpty()
                        && (!meta.has("deliveredUri") || meta.has("obsoleteUri")))
                    item(
                            menu,
                            "Ordnerkopie erneut sichern",
                            () ->
                                    perform(
                                            List.of(e),
                                            entry ->
                                                    DocumentStorage.deliver(screen, entry.file())));
            } catch (Exception error) {
                screen.toast(Audomo.cause(error));
            }
            item(menu, "Datei exportieren", () -> screen.export(e));
        }
        menu.show();
    }

    static void item(PopupMenu menu, String title, Runnable action) {
        menu.getMenu()
                .add(title)
                .setOnMenuItemClickListener(i -> {
                    action.run();
                    return true;
                });
    }

    static Intent contentIntent(Context context, List<SessionStore.Entry> entries, boolean share) {
        ArrayList<Uri> uris = new ArrayList<>();
        for (SessionStore.Entry e : entries)
            uris.add(FileProvider.getUriForFile(context, "de.audomo.audio", e.file(), e.name()));
        String mime = entries.size() == 1 ? LibraryFiles.mime(entries.get(0).name()) : "audio/*";
        Intent i;
        if (!share) i = new Intent(Intent.ACTION_VIEW).setDataAndType(uris.get(0), mime);
        else if (uris.size() == 1)
            i =
                    new Intent(Intent.ACTION_SEND)
                            .setType(mime)
                            .putExtra(Intent.EXTRA_STREAM, uris.get(0));
        else
            i =
                    new Intent(Intent.ACTION_SEND_MULTIPLE)
                            .setType(mime)
                            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        ClipData clips = ClipData.newRawUri("Aufnahme", uris.get(0));
        for (int k = 1; k < uris.size(); k++) clips.addItem(new ClipData.Item(uris.get(k)));
        i.setClipData(clips);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return i;
    }

    void share(List<SessionStore.Entry> entries, boolean share) {
        Intent i = contentIntent(screen, entries, share);
        try {
            screen.startActivity(share ? Intent.createChooser(i, "Aufnahmen teilen") : i);
        } catch (ActivityNotFoundException e) {
            screen.toast("Keine passende App installiert");
        }
    }
}
