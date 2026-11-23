package de.audomo;

import android.app.*;
import android.content.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;

import java.util.*;

final class CloudScreen {
    final MainActivity s;
    final SharedPreferences p;
    final UploadQueue jobs;

    CloudScreen(MainActivity s) {
        this.s = s;
        p = Profile.prefs(s);
        jobs = UploadQueue.get(s);
    }

    private void fail(Exception e) {
        s.toast(Audomo.cause(e));
    }

    private LinearLayout column() {
        LinearLayout l = s.column();
        l.setPadding(s.dp(16), s.dp(4), s.dp(16), s.dp(8));
        return l;
    }

    private AlertDialog dialog(String title, LinearLayout page) {
        ScrollView scroll = new ScrollView(s);
        scroll.addView(page);
        return new AlertDialog.Builder(s)
                .setTitle(title)
                .setView(scroll)
                .setNegativeButton("Schließen", null)
                .create();
    }

    private void button(LinearLayout page, String label, Runnable run) {
        page.addView(s.button(label, v -> run.run()));
    }

    private void option(LinearLayout page, String title, String key, boolean def) {
        Switch on = new Switch(s);
        on.setText(title);
        on.setMinHeight(s.dp(48));
        on.setChecked(p.getBoolean(key, def));
        on.setOnCheckedChangeListener((v, value) -> {
            p.edit().putBoolean(key, value).apply();
            if (key.equals("wifiOnly") && jobs.pending()) jobs.schedule(true);
        });
        page.addView(on);
    }

    private void showDelayValue(Button button) {
        String value = UploadDelay.display(p);
        button.setText("Upload-Verzögerung · " + (value.equals("0") ? "Sofort" : value + " min"));
    }

    private void editDelay(Button button) {
        LinearLayout page = column();
        TextView label = s.text("Wartezeit nach Aufnahmeende in Minuten", 16, MainActivity.INK);
        page.addView(label);
        EditText input = new EditText(s);
        input.setSingleLine();
        input.setId(View.generateViewId());
        label.setLabelFor(input.getId());
        input.setKeyListener(android.text.method.DigitsKeyListener.getInstance("0123456789,.-"));
        input.setRawInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(UploadDelay.display(p));
        input.selectAll();
        page.addView(input);
        page.addView(s.text("0 = sofort · 0,5 = 30 Sekunden · 5 = 5 Minuten", 14, MainActivity.DIM));
        AlertDialog dialog = new AlertDialog.Builder(s).setTitle("Automatischen Upload verzögern")
                .setView(page).setNegativeButton("Abbrechen", null).setPositiveButton("Speichern", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            try {
                String value = UploadDelay.minutes(input.getText().toString()).toPlainString();
                p.edit().putString(UploadDelay.KEY, value).apply();
                showDelayValue(button);
                dialog.dismiss();
            } catch (IllegalArgumentException error) {
                input.setError(error.getMessage());
            }
        }));
        dialog.show();
    }

    static String queueStatus(UploadQueue.Job job) {
        if (!job.state().equals("Wartet") || job.notBefore() <= System.currentTimeMillis()) return job.state();
        java.time.ZonedDateTime time = java.time.Instant.ofEpochMilli(job.notBefore()).atZone(java.time.ZoneId.systemDefault());
        String pattern = time.toLocalDate().equals(java.time.LocalDate.now()) ? "HH:mm:ss" : "dd.MM.yyyy HH:mm:ss";
        return "Frühestens " + time.format(java.time.format.DateTimeFormatter.ofPattern(pattern, Locale.GERMAN));
    }

    void afterSetup(List<String> selection) {
        if (selection.isEmpty()) manage(selection);
        else ManualUploadDialog.chooseTarget(s, selection);
    }

    void manage(List<String> selection) {
        try {
            List<CloudConfig> configs = CloudConfig.all(s);
            LinearLayout page = column();
            AlertDialog d = dialog("Uploadziele", page);
            String selected = p.getString("defaultTarget", "");
            for (CloudConfig t : configs) {
                String label = t.label() + (t.id().equals(selected) ? " · Standard" : "");
                button(page, label, () -> {
                    AlertDialog actions = choices(t.protocol(), new String[] {
                        "Verbindung testen", "Als Standard verwenden", "Zugang bearbeiten", "Cloudpfade abgleichen"
                    });
                    actions.getListView().setOnItemClickListener((parent, view, index, id) -> {
                        if (index == 2) {
                            edit(t.protocol(), t, selection, () -> {
                                actions.dismiss();
                                d.dismiss();
                            });
                            return;
                        }
                        actions.dismiss();
                        if (index == 3) { reconcile(t); return; }
                        if (index == 0) test(t);
                        else {
                            p.edit().putString("defaultTarget", t.id()).apply();
                            d.dismiss();
                            manage(selection);
                        }
                    });
                });
            }
            button(page, "Ziel hinzufügen", () -> {
                AlertDialog protocols = choices("Protokoll", CloudConfig.TYPES.toArray(new String[0]));
                protocols.getListView().setOnItemClickListener((parent, view, index, id) ->
                        edit(CloudConfig.TYPES.get(index), null, selection, () -> {
                            protocols.dismiss();
                            d.dismiss();
                        }));
            });
            if (selection.isEmpty())
                button(page, "Bestehende Aufnahmen hochladen", () -> ManualUploadDialog.chooseRecordings(s));
            option(page, "Automatisch nach Aufnahme hochladen", "autoUpload", false);
            Button delay = s.button("", null);
            showDelayValue(delay);
            delay.setOnClickListener(v -> editDelay(delay));
            page.addView(delay);
            option(page, "Nur über WLAN", "wifiOnly", true);
            option(page, "Lokale Unterordner nachbilden", "mirrorFolders", true);
            option(page, "Nach verifiziertem Upload in den Papierkorb", "cleanupUploaded", false);
            button(page, "Warteschlange", this::queue);
            d.show();
        } catch (Exception e) {
            fail(e);
        }
    }

    private AlertDialog choices(String title, String[] labels) {
        return new AlertDialog.Builder(s).setTitle(title).setItems(labels, null).show();
    }

    private EditText edit(LinearLayout page, String label, String value, boolean secret) {
        page.addView(s.text(label, 13, MainActivity.ACCENT));
        EditText e = new EditText(s);
        e.setSingleLine();
        if (secret) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            e.setSaveEnabled(false);
            e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        e.setText(value);
        page.addView(e);
        return e;
    }

    private void edit(String type, CloudConfig old, List<String> selection, Runnable closeParents) {
        if (type.equals("Dropbox")) {
            DropboxLinkDialog.open(s, old, selection, closeParents);
            return;
        }
        LinearLayout page = column();
        EditText host = edit(page, "Server", old == null ? "" : old.host(), false);
        int def = switch (type) {
            case "WebDAV" -> 443;
            case "SFTP" -> 22;
            default -> 21;
        };
        EditText port = edit(page, "Port", Integer.toString(old == null ? def : old.port()), false);
        EditText user = edit(page, "Benutzername", old == null ? "" : old.user(), false);
        EditText pass = edit(page, "Passwort", "", true);
        EditText base = edit(page, "Zielordner", old == null ? "/" : old.base(), false);
        EditText pin =
                type.equals("SFTP")
                        ? edit(
                                page,
                                "Öffentlicher Hostschlüssel (Typ und Base64)",
                                old == null ? "" : old.hostKey(),
                                false)
                        : null;
        if (type.equals("FTP"))
            page.addView(
                    s.text(
                            "FTP überträgt Anmeldedaten und Audio unverschlüsselt.",
                            14,
                            MainActivity.DIM));
        AlertDialog dlg = dialog(type, page);
        button(
                page,
                "Verbindung testen",
                () -> {
                    try {
                        CloudConfig target = config(type, old, host, port, user, base, pin);
                        String input = pass.getText().toString();
                        s.runFiles(() -> {
                            String credential =
                                    credential(target, old, input);
                            try (RemoteFiles remote =
                                    RemoteFiles.open(target, credential, null, n -> {})) {
                                remote.test();
                            }
                            return "Verbindung und Zielordner bestätigt";
                        });
                    } catch (Exception e) {
                        fail(e);
                    }
                });
        button(
                page,
                "Speichern",
                () -> {
                    try {
                        CloudConfig t = config(type, old, host, port, user, base, pin);
                        String input = pass.getText().toString();
                        s.runFiles(() -> {
                            String credential = credential(t, old, input);
                            t.save(s, credential);
                            s.runOnUiThread(() -> {
                                dlg.dismiss();
                                closeParents.run();
                                afterSetup(selection);
                            });
                            return "Ziel gespeichert";
                        });
                    } catch (Exception e) {
                        fail(e);
                    }
                });
        dlg.show();
    }

    private String credential(CloudConfig t, CloudConfig old, String value) throws Exception {
        if (value.isEmpty()) {
            if (old == null || !t.equals(old))
                throw new IllegalArgumentException("Zugang für dieses Ziel erforderlich");
            return CloudConfig.secret(s, t.id());
        }
        return value;
    }

    private CloudConfig config(
            String type,
            CloudConfig old,
            EditText host,
            EditText port,
            EditText user,
            EditText base,
            EditText key) {
        String id = old == null ? UUID.randomUUID().toString() : old.id();
        CloudConfig t =
                new CloudConfig(
                        id,
                        type,
                        host.getText().toString().trim(),
                        Integer.parseInt(port.getText().toString()),
                        user.getText().toString(),
                        base.getText().toString(),
                        key == null ? "" : key.getText().toString().trim());
        if (old != null && !old.equals(t))
            return new CloudConfig(
                    UUID.randomUUID().toString(),
                    type,
                    t.host(),
                    t.port(),
                    t.user(),
                    t.base(),
                    t.hostKey());
        return t;
    }

    private void test(CloudConfig t) {
        s.runFiles(() -> {
            try (RemoteFiles r =
                    RemoteFiles.open(t, CloudConfig.secret(s, t.id()), null, n -> {})) {
                r.test();
            }
            return "Verbindung und Zielordner bestätigt";
        });
    }

    void queue() {
        s.openQueue();
    }

    void reconcile(CloudConfig target) {
        s.toast("Cloudpfade werden geprüft");
        s.runFiles(() -> CloudReconcile.run(((Audomo)s.getApplication()).sessions, target));
    }

    void locations(SessionStore.Entry entry, List<UploadQueue.Job> jobs) {
        LinearLayout page = column();
        AlertDialog dialog = dialog("Speicherorte", page);
        page.addView(s.text(entry.name(), 16, MainActivity.INK));
        if (!entry.cloudOnly()) page.addView(s.text("Lokal · " + (entry.folder().isEmpty() ? "Aufnahmen" : entry.folder()), 14, MainActivity.DIM));
        for (UploadQueue.Job job : jobs) {
            String provider = job.provider().isEmpty() ? "Cloud" : job.provider();
            page.addView(s.text(provider + " · " + job.confirmedPath(), 15, MainActivity.INK));
            String status = job.availability().isEmpty() ? job.state() : job.availability();
            button(page, status, () -> details(job));
            if (entry.cloudOnly() && job.knownCopy() && job.availability().isEmpty())
                button(page, "Lokal wiederherstellen", () -> {
                    dialog.dismiss();
                    s.runFiles(() -> {
                        new CloudLibrary(((Audomo)s.getApplication()).sessions).restore(entry, job);
                        return "Aufnahme wieder lokal verfügbar";
                    });
                });
            if (job.knownCopy()) button(page, "Cloudpfade abgleichen", () -> {
                try { CloudConfig target = CloudConfig.byId(s, job.target()); dialog.dismiss(); reconcile(target); }
                catch (Exception error) { fail(error); }
            });
        }
        dialog.show();
    }

    void details(UploadQueue.Job job) {
        UploadDetailsDialog.open(s, job.id());
    }
}
