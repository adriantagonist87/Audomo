package de.audomo;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

final class AppSettings {
    final MainActivity screen;
    final Audomo app;
    final SharedPreferences prefs;

    AppSettings(MainActivity screen) {
        this.screen = screen;
        app = (Audomo) screen.getApplication();
        prefs = Profile.prefs(screen);
    }

    private final class Page {
        final LinearLayout rows = screen.column();
        final Dialog dialog;

        Page(String title) {
            dialog = new Dialog(screen, android.R.style.Theme_Material_NoActionBar);
            LinearLayout page = screen.column();
            page.setBackgroundColor(MainActivity.BG);
            page.setPadding(screen.dp(16), 0, screen.dp(16), 0);
            LinearLayout header = screen.row();
            page.addView(header);
            ImageButton back = screen.toolbarIcon("Zurück", R.drawable.ic_arrow_back);
            back.setOnClickListener(v -> dialog.dismiss());
            header.addView(back, screen.lp(48, 48));
            header.addView(screen.text(title, 22, MainActivity.INK), screen.weight());
            ScrollView scroll = new ScrollView(screen);
            scroll.addView(rows);
            page.addView(scroll);
            dialog.setContentView(page);
            dialog.getWindow().setStatusBarColor(MainActivity.BG);
            dialog.getWindow().setNavigationBarColor(MainActivity.BG);
        }

        void group(String title) {
            TextView t = screen.text(title.toUpperCase(Locale.GERMAN), 11, MainActivity.ACCENT);
            t.setPadding(0, screen.dp(12), 0, screen.dp(3));
            rows.addView(t);
        }

        void row(String title, String status, Runnable click) {
            liveRow(title, () -> status, click);
        }

        void liveRow(String title, java.util.function.Supplier<String> getStatus, Runnable click) {
            String status = getStatus.get();
            LinearLayout row = screen.row();
            row.setMinimumHeight(screen.dp(49));
            LinearLayout labels = screen.column();
            labels.addView(screen.text(title, 16, MainActivity.INK));
            if (!status.isEmpty()) {
                TextView value = screen.text(status, 13, MainActivity.DIM);
                labels.addView(value);
                row.getViewTreeObserver()
                        .addOnWindowFocusChangeListener(focus -> {
                            if (focus) value.setText(getStatus.get());
                        });
            }
            row.addView(labels, screen.weight());
            row.addView(screen.text("›", 22, MainActivity.ACCENT));
            row.setOnClickListener(v -> click.run());
            rows.addView(row);
            View line = new View(screen);
            line.setBackgroundColor(MainActivity.LINE);
            rows.addView(line, screen.lp(-1, 1));
        }
    }

    void show() {
        Page p = new Page("Einstellungen");
        p.group("Aufnahme");
        p.liveRow(
                "Aufnahmeprofile",
                () -> Profile.selected(screen).name() + " aktiv",
                () -> new ProfileDialog(screen).choose());
        p.row(
                "Aufnahmeverhalten",
                "Hintergrund, Anrufe und Töne",
                () -> {
                    Page r = new Page("Aufnahmeverhalten");
                    r.group("Mikrofon");
                    r.rows.addView(
                            screen.text(
                                    "Die Aufnahme läuft im Hintergrund weiter. Unterbrechungen des"
                                            + " Mikrofoneingangs werden gemeldet.",
                                    14,
                                    MainActivity.DIM));
                    r.row(
                            "Nicht-stören-Zugriff",
                            screen.getSystemService(NotificationManager.class)
                                            .isNotificationPolicyAccessGranted()
                                    ? "Erlaubt"
                                    : "Nicht erteilt",
                            () ->
                                    screen.startActivity(
                                            new Intent(
                                                    Settings
                                                            .ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
                    r.dialog.show();
                });
        p.group("Kalender und Dateinamen");
        p.liveRow(
                "Kalender",
                () -> CalendarNames.ids(screen).size() + " Kalender ausgewählt",
                () -> screen.startActivity(new Intent(screen, CalendarSettings.class)));
        p.liveRow(
                "Dateinamen",
                () -> CalendarNames.template(screen),
                () ->
                        screen.startActivity(
                                new Intent(screen, CalendarSettings.class)
                                        .putExtra("focusTemplate", true)));
        p.group("Dateien");
        p.liveRow(
                "Speicher und Import",
                () -> prefs.contains("documentTree") ? "Android-Ordner gewählt" : "App-Speicher",
                this::storage);
        p.row(
                "Papierkorb",
                "Gelöschte Aufnahmen",
                () -> {
                    p.dialog.dismiss();
                    screen.trashView = true;
                    screen.showLibrary();
                });
        p.group("Cloud und Uploads");
        p.row("Uploadziele", "Verbindungen und Standardziel", () -> upload(null));
        p.row(
                "Upload-Warteschlange",
                "Fortschritt und Fehler",
                () -> new CloudScreen(screen).queue());
        p.group("App und Diagnose");
        p.row(
                "Quick Settings und Vibration",
                "Kachel und haptische Rückmeldungen",
                () -> {
                    Page q = new Page("Quick Settings und Vibration");
                    q.row(
                            "Vibration",
                            prefs.getBoolean("haptic", true) ? "Aktiv" : "Aus",
                            screen::haptics);
                    q.row("Kachel hinzufügen", "Android-Schnelleinstellungen", screen::addTile);
                    q.dialog.show();
                });
        p.row(
                "Einstellungen sichern",
                "Import und Export",
                () -> {
                    Page q = new Page("Einstellungen sichern");
                    q.row(
                            "Exportieren",
                            "JSON-Datei ohne Geheimnisse",
                            () -> create(20, "Audomo-Einstellungen.json", "application/json"));
                    q.row(
                            "Importieren",
                            "Profile und Regeln übernehmen",
                            () ->
                                    screen.startActivityForResult(
                                            new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                                    .setType("application/json"),
                                            21));
                    q.dialog.show();
                });
        p.row("Diagnose", "Ereignisse und Export", this::diagnostics);
        p.row("Einrichtung", "Berechtigungen und Schnellzugriff", this::onboarding);
        p.dialog.show();
    }

    void onboarding() {
        Page p = new Page("Einrichtung");
        p.liveRow(
                "Mikrofon",
                () -> status(Manifest.permission.RECORD_AUDIO),
                () ->
                        screen.requestPermissions(
                                new String[] {Manifest.permission.RECORD_AUDIO}, 15));
        p.liveRow(
                "Kalender",
                () -> status(Manifest.permission.READ_CALENDAR),
                () -> screen.startActivity(new Intent(screen, CalendarSettings.class)));
        p.liveRow(
                "Benachrichtigungen",
                () -> status(Manifest.permission.POST_NOTIFICATIONS),
                () ->
                        screen.requestPermissions(
                                new String[] {Manifest.permission.POST_NOTIFICATIONS}, 2));
        p.row("Kachel hinzufügen", "Android-Schnelleinstellungen", screen::addTile);
        p.row("Cloudziel", "Optional", () -> upload(null));
        p.dialog.show();
    }

    String status(String p) {
        return screen.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
                ? "Erlaubt"
                : "Zugriff einrichten";
    }

    void upload(SessionStore.Entry e) {
        new CloudScreen(screen).show(e);
    }

    void storage() {
        Page p = new Page("Aufnahmeordner");
        p.row(
                "Ordner wählen",
                "Nach der lokalen Sicherung dort speichern",
                () ->
                        screen.startActivityForResult(
                                new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                        .addFlags(
                                                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                                        | Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                23));
        p.row("Aufnahmen importieren", "Bestandsdateien unverändert kopieren", screen::pickImport);
        p.row(
                "Aus Ordner importieren",
                "MP3 und andere Audiodateien",
                () ->
                        screen.startActivityForResult(
                                new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                24));
        p.row(
                "App-Speicher verwenden",
                "Privater Aufnahmeordner",
                () ->
                        screen.runFiles(() -> {
                            DocumentStorage.leaveCurrent(screen);
                            return "App-Speicher gewählt";
                        }));
        p.row(
                "Vor Mediensuche verbergen",
                prefs.getBoolean("hidden", false) ? "Aktiv" : "Aus",
                () ->
                        screen.runFiles(() -> {
                            DocumentStorage.hide(screen, !prefs.getBoolean("hidden", false));
                            return "Mediensuche aktualisiert";
                        }));
        p.dialog.show();
    }

    private void create(int code, String name, String mime) {
        screen.startActivityForResult(
                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(mime)
                        .putExtra(Intent.EXTRA_TITLE, name),
                code);
    }

    void diagnostics() {
        Page p = new Page("Diagnose");
        Spinner choices = new Spinner(screen);
        String[] levels = {"Alle Ereignisse", "Fehler", "Warnungen", "Info"};
        choices.setAdapter(
                new ArrayAdapter<>(screen, android.R.layout.simple_spinner_dropdown_item, levels));
        p.rows.addView(choices);
        LinearLayout list = screen.column();
        p.rows.addView(list);
        if (!app.route.isEmpty())
            p.row(
                    "Audioeingang",
                    "Zuletzt gemeldete Route und Effekte",
                    () ->
                            new AlertDialog.Builder(screen)
                                    .setTitle("Audioeingang")
                                    .setMessage(app.route + "\n" + app.audioDetails)
                                    .setPositiveButton("Schließen", null)
                                    .show());
        choices.setOnItemSelectedListener(
                new AdapterView.OnItemSelectedListener() {
                    public void onNothingSelected(AdapterView<?> a) {}

                    public void onItemSelected(AdapterView<?> a, View v, int i, long id) {
                        try {
                            list.removeAllViews();
                            for (String line : events(i).split("\n")) {
                                if (line.equals("Keine Ereignisse")) {
                                    list.addView(screen.text(line, 14, MainActivity.DIM));
                                    continue;
                                }
                                String[] parts = line.split("  ", 2);
                                TextView item =
                                        screen.text(
                                                parts.length == 2
                                                        ? parts[1] + "\n" + parts[0]
                                                        : line,
                                                14,
                                                MainActivity.INK);
                                item.setMinHeight(screen.dp(48));
                                item.setOnClickListener(t ->
                                        new AlertDialog.Builder(screen)
                                                .setTitle("Ereignisdetails")
                                                .setMessage(line)
                                                .setPositiveButton("Schließen", null)
                                                .show());
                                list.addView(item);
                            }
                        } catch (Exception e) {
                            list.removeAllViews();
                            list.addView(screen.text(Audomo.cause(e), 14, MainActivity.DIM));
                        }
                    }
                });
        p.row(
                "Diagnose exportieren",
                "Textdatei ohne Audio",
                () -> create(22, "Audomo-Diagnose.txt", "text/plain"));
        p.dialog.show();
    }

    String events(int level) throws Exception {
        File f = new File(screen.getFilesDir(), "diagnose.txt");
        List<String> lines =
                f.exists() ? new ArrayList<>(Files.readAllLines(f.toPath())) : new ArrayList<>();
        Collections.reverse(lines);
        List<String> result = new ArrayList<>();
        for (String line : lines) {
            String l = line.toLowerCase(Locale.ROOT);
            int category =
                    l.contains("fehler") || l.contains("unterbrochen")
                            ? 1
                            : l.contains("warn")
                                            || l.contains("übersteuer")
                                            || l.contains("wiederherstell")
                                    ? 2
                                    : 3;
            if (level == 0 || category == level) result.add(line);
        }
        return result.isEmpty() ? "Keine Ereignisse" : String.join("\n", result);
    }

    boolean result(int req, Intent data) {
        Uri uri = data.getData();
        if (req < 20 || req > 24 || uri == null) return false;
        if (req == 24) {
            String target = prefs.getString("folder", "");
            screen.runFiles(() -> {
                List<Uri> sources = DocumentStorage.audioDocuments(screen, uri);
                List<String> errors = new ArrayList<>();
                int ok = 0;
                LibraryFiles lib = new LibraryFiles(app.sessions);
                for (Uri item : sources)
                    try {
                        lib.importUri(item, target);
                        ok++;
                    } catch (Exception e) {
                        errors.add(Audomo.cause(e));
                    }
                return ok
                        + " importiert"
                        + (errors.isEmpty() ? "" : "\n" + String.join("\n", errors));
            });
            return true;
        }
        if (req == 23) {
            try {
                int grants =
                        data.getFlags()
                                & (Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                        | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if ((grants & 3) != 3)
                    throw new IOException("Lese- und Schreibzugriff erforderlich");
                screen.getContentResolver()
                        .takePersistableUriPermission(
                                uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                DocumentStorage.select(screen, uri);
                screen.toast("Aufnahmeordner gespeichert");
            } catch (Exception e) {
                screen.toast(Audomo.cause(e));
            }
            return true;
        }
        if (req == 21) {
            new AlertDialog.Builder(screen)
                    .setTitle("Einstellungen übernehmen?")
                    .setMessage("Profile und Regeln aus der Datei werden übernommen.")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton(
                            "Importieren",
                            (d, w) ->
                                    screen.runFiles(() -> {
                                        try (InputStream in =
                                                screen.getContentResolver().openInputStream(uri)) {
                                            return SettingsBackup.restore(screen, in);
                                        }
                                    }))
                    .show();
            return true;
        }
        screen.runFiles(() -> {
            byte[] bytes =
                    req == 20
                            ? SettingsBackup.export(screen)
                            : events(0).getBytes(StandardCharsets.UTF_8);
            try (OutputStream out = screen.getContentResolver().openOutputStream(uri, "wt")) {
                if (out == null) throw new IOException("Ziel nicht beschreibbar");
                out.write(bytes);
            }
            return "Exportiert";
        });
        return true;
    }
}
