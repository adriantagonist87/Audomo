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

    void show(SessionStore.Entry recording) {
        try {
            List<CloudConfig> configs = CloudConfig.all(s);
            if (recording != null && !configs.isEmpty()) {
                String[] labels =
                        configs.stream()
                                .map(t -> t.protocol() + " · " + t.host())
                                .toArray(String[]::new);
                new AlertDialog.Builder(s)
                        .setTitle("Ziel für " + recording.name())
                        .setItems(
                                labels,
                                (d, i) -> {
                                    jobs.enqueue(recording, configs.get(i));
                                    s.toast("Upload eingereiht");
                                    s.showLibrary();
                                })
                        .setNegativeButton("Abbrechen", null)
                        .show();
                return;
            }
            LinearLayout page = column();
            AlertDialog d = dialog("Uploadziele", page);
            String selected = p.getString("defaultTarget", "");
            for (CloudConfig t : configs)
                button(
                        page,
                        t.protocol()
                                + " · "
                                + t.host()
                                + (t.id().equals(selected) ? " · Standard" : ""),
                        () -> {
                            new AlertDialog.Builder(s)
                                    .setTitle(t.protocol())
                                    .setItems(
                                            new String[] {
                                                "Verbindung testen",
                                                "Als Standard verwenden",
                                                "Zugang bearbeiten"
                                            },
                                            (dlg, i) -> {
                                                if (i == 0) test(t);
                                                else if (i == 1) {
                                                    p.edit()
                                                            .putString("defaultTarget", t.id())
                                                            .apply();
                                                    d.dismiss();
                                                    show(recording);
                                                } else {
                                                    d.dismiss();
                                                    edit(t.protocol(), t, recording);
                                                }
                                            })
                                    .show();
                        });
            button(
                    page,
                    "Ziel hinzufügen",
                    () ->
                            new AlertDialog.Builder(s)
                                    .setTitle("Protokoll")
                                    .setItems(
                                            CloudConfig.TYPES.toArray(new String[0]),
                                            (dlg, i) -> {
                                                d.dismiss();
                                                edit(CloudConfig.TYPES.get(i), null, recording);
                                            })
                                    .show());
            option(page, "Automatisch nach Aufnahme hochladen", "autoUpload", false);
            option(page, "Nur über WLAN", "wifiOnly", true);
            option(page, "Lokale Unterordner nachbilden", "mirrorFolders", true);
            option(page, "Nach verifiziertem Upload in den Papierkorb", "cleanupUploaded", false);
            button(
                    page,
                    "Warteschlange",
                    () -> {
                        d.dismiss();
                        queue();
                    });
            d.show();
        } catch (Exception e) {
            fail(e);
        }
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

    private void edit(String type, CloudConfig old, SessionStore.Entry rec) {
        boolean drop = type.equals("Dropbox");
        LinearLayout page = column();
        EditText host =
                edit(
                        page,
                        drop ? "Öffentlicher Dropbox-App-Key" : "Server",
                        old == null ? "" : old.host(),
                        false);
        int def =
                switch (type) {
                    case "Dropbox", "WebDAV" -> 443;
                    case "SFTP" -> 22;
                    default -> 21;
                };
        EditText port =
                drop
                        ? null
                        : edit(
                                page,
                                "Port",
                                Integer.toString(old == null ? def : old.port()),
                                false);
        EditText user =
                drop ? null : edit(page, "Benutzername", old == null ? "" : old.user(), false);
        String[] verifier = {null};
        String[] authorized = {null, null, null};
        if (drop)
            button(
                    page,
                    "Bei Dropbox anmelden",
                    () -> {
                        try {
                            Arrays.fill(authorized, null);
                            verifier[0] = DropboxFiles.verifier();
                            s.startActivity(
                                    new Intent(
                                            Intent.ACTION_VIEW,
                                            DropboxFiles.authorize(
                                                    host.getText().toString().trim(),
                                                    verifier[0])));
                        } catch (Exception e) {
                            fail(e);
                        }
                    });
        EditText pass = edit(page, drop ? "Autorisierungscode" : "Passwort", "", true);
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
                                    credential(target, old, input, verifier[0], authorized);
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
                            String credential = credential(t, old, input, verifier[0], authorized);
                            t.save(s, credential);
                            s.runOnUiThread(() -> {
                                dlg.dismiss();
                                show(rec);
                            });
                            return "Ziel gespeichert";
                        });
                    } catch (Exception e) {
                        fail(e);
                    }
                });
        dlg.show();
    }

    private String credential(
            CloudConfig t, CloudConfig old, String value, String verifier, String[] authorized)
            throws Exception {
        if (value.isEmpty()) {
            if (old == null || !t.equals(old))
                throw new IllegalArgumentException("Zugang für dieses Ziel erforderlich");
            return CloudConfig.secret(s, t.id());
        }
        if (t.protocol().equals("Dropbox")) {
            if (verifier == null) throw new IllegalArgumentException("Dropbox-Anmeldung starten");
            if (!t.host().equals(authorized[0]) || !value.equals(authorized[1])) {
                String refresh = DropboxFiles.exchange(t.host(), verifier, value);
                authorized[0] = t.host();
                authorized[1] = value;
                authorized[2] = refresh;
            }
            return authorized[2];
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
                        port == null ? 443 : Integer.parseInt(port.getText().toString()),
                        user == null ? "" : user.getText().toString(),
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
        LinearLayout page = column();
        AlertDialog d = dialog("Upload-Warteschlange", page);
        Runnable render =
                () -> {
                    page.removeAllViews();
                    List<UploadQueue.Job> entries = jobs.jobs();
                    if (entries.isEmpty())
                        page.addView(s.text("Keine Uploadaufträge", 16, MainActivity.DIM));
                    for (UploadQueue.Job j : entries)
                        button(
                                page,
                                RemoteFiles.leaf(j.path())
                                        + " · "
                                        + j.state()
                                        + ("Upload läuft".equals(j.state())
                                                ? " " + j.progress() + " %"
                                                : ""),
                                () -> details(j));
                };
        render.run();
        android.os.Handler h = new android.os.Handler(s.getMainLooper());
        Runnable refresh =
                new Runnable() {
                    public void run() {
                        if (!d.isShowing()) return;
                        render.run();
                        h.postDelayed(this, 3000);
                    }
                };
        d.setOnDismissListener(x -> h.removeCallbacks(refresh));
        d.show();
        h.postDelayed(refresh, 3000);
    }

    void details(UploadQueue.Job j) {
        LinearLayout page = column();
        AlertDialog dlg = dialog("Uploadstatus", page);
        String label = "";
        try {
            CloudConfig t = CloudConfig.byId(s, j.target());
            label = t.protocol() + " · " + t.host();
        } catch (Exception ignored) {
        }
        page.addView(
                s.text(
                        label + "\n" + j.path() + "\n" + j.state() + " · " + j.progress() + " %",
                        16,
                        MainActivity.INK));
        if (!j.error().isEmpty()) page.addView(s.text(j.error(), 14, MainActivity.DIM));
        if (j.state().equals("Fehler")) {
            page.addView(
                    s.text(
                            "Automatischer Neuversuch bei geeigneter Verbindung",
                            14,
                            MainActivity.DIM));
            button(
                    page,
                    "Erneut versuchen",
                    () -> {
                        jobs.retry(j.id());
                        dlg.dismiss();
                    });
        }
        if (j.state().equals("Konflikt")) {
            LinearLayout actions = s.row();
            page.addView(actions);
            String[] labels = {"Neuer Name", "Direkt überschreiben", "Upload auslassen"};
            for (int i = 0; i < labels.length; i++) {
                final int choice = i;
                actions.addView(
                        s.button(
                                labels[i],
                                v -> {
                                    Runnable resolve =
                                            () -> {
                                                try {
                                                    jobs.resolve(j, choice);
                                                    dlg.dismiss();
                                                } catch (Exception e) {
                                                    fail(e);
                                                }
                                            };
                                    if (choice == 1)
                                        new AlertDialog.Builder(s)
                                                .setTitle("Zieldatei überschreiben?")
                                                .setMessage(j.path())
                                                .setPositiveButton(
                                                        "Überschreiben", (d, w) -> resolve.run())
                                                .setNegativeButton("Abbrechen", null)
                                                .show();
                                    else resolve.run();
                                }),
                        s.weight());
            }
        }
        button(
                page,
                "Zur Warteschlange",
                () -> {
                    dlg.dismiss();
                    queue();
                });
        dlg.show();
        dlg.getWindow().setGravity(Gravity.BOTTOM);
    }
}
