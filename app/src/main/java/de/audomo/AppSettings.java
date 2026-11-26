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

    final class Page {
        final LinearLayout rows = screen.column();
        private LinearLayout card;
        final Dialog dialog;
        final ScrollView scroll = new ScrollView(screen);

        Page(String title) {
            dialog = new Dialog(screen, R.style.AudomoTheme);
            LinearLayout page = screen.column();
            page.setBackgroundColor(MainActivity.BG);
            page.setPadding(screen.dp(16), 0, screen.dp(16), 0);
            LinearLayout header = screen.row();
            page.addView(header);
            ImageButton back = screen.toolbarIcon("Zurück", R.drawable.ic_arrow_back);
            back.setOnClickListener(v -> dialog.dismiss());
            header.addView(back, screen.lp(48, 48));
            header.addView(screen.text(title, 22, MainActivity.INK), screen.weight());
            scroll.addView(rows);
            page.addView(scroll);
            dialog.setContentView(page);
            dialog.getWindow().setStatusBarColor(MainActivity.BG);
            dialog.getWindow().setNavigationBarColor(MainActivity.BG);
        }

        void group(String title) {
            TextView t = screen.text(title, 14, MainActivity.DIM);
            t.setPadding(screen.dp(4), screen.dp(16), 0, screen.dp(6));
            rows.addView(t);
            card = null;
        }

        void row(String title, String status, Runnable click) {
            row(R.drawable.ic_folder, title, status, click);
        }

        void row(int icon, String title, String status, Runnable click) {
            liveRow(icon, title, () -> status, click);
        }

        void liveRow(int icon, String title, java.util.function.Supplier<String> getStatus, Runnable click) {
            if (card == null || card.getParent() != rows) {
                card = screen.column();
                card.setBackground(screen.surface(MainActivity.PANEL, 14));
                card.setClipToOutline(true);
                rows.addView(card);
            } else {
                View line = new View(screen);
                line.setBackgroundColor(MainActivity.LINE);
                LinearLayout.LayoutParams lp = screen.lp(-1, 1);
                lp.leftMargin = screen.dp(52);
                card.addView(line, lp);
            }
            LinearLayout row = screen.row();
            row.setPadding(screen.dp(14), screen.dp(8), screen.dp(12), screen.dp(8));
            row.setMinimumHeight(screen.dp(54));
            android.util.TypedValue ripple = new android.util.TypedValue();
            if (screen.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true))
                row.setBackgroundResource(ripple.resourceId);
            ImageView symbol = new ImageView(screen);
            symbol.setImageResource(icon);
            symbol.setColorFilter(MainActivity.INK);
            symbol.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(symbol, screen.lp(23, 23));
            LinearLayout labels = screen.column();
            labels.setPadding(screen.dp(14), 0, screen.dp(8), 0);
            labels.addView(screen.text(title, 16, MainActivity.INK));
            TextView value = screen.text(getStatus.get(), 13, MainActivity.DIM);
            if (value.length() > 0) {
                labels.addView(value);
                row.getViewTreeObserver().addOnWindowFocusChangeListener(focus -> {
                    if (focus && row.isAttachedToWindow()) value.setText(getStatus.get());
                });
            }
            row.addView(labels, screen.weight());
            ImageView arrow = new ImageView(screen);
            arrow.setImageResource(R.drawable.ic_chevron);
            arrow.setColorFilter(MainActivity.DIM);
            arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(arrow, screen.lp(18, 18));
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                click.run();
                if (value.isAttachedToWindow()) value.setText(getStatus.get());
            });
            card.addView(row);
        }
    }

    void show() {
        settingsHome().dialog.show();
    }

    private Page settingsHome() {
        Page p = new Page("Einstellungen");
        p.group("Besprechungen");
        p.row(R.drawable.ic_mic, "Aufnahme und Klang", "Aufnahmeprofile, Mikrofonwahl und Audioqualität", this::recording);
        p.row(R.drawable.ic_play, "Wiedergabe", "Audioausgang, Tempo und Sprungintervall", this::playback);
        p.row(R.drawable.ic_calendar_day, "Kalender und Dateinamen", "Kalender auswählen, Dateinamen und Ordner festlegen", this::calendar);
        p.row(R.drawable.ic_folder, "Dateien und Ordner", "Speicherorte, Import und Papierkorb", () -> files(p, 0));
        p.row(R.drawable.ic_upload, "Cloud und Uploads", "Ziele, Automatik und Warteschlange", () -> cloud(p));
        p.group("App und System");
        p.row(R.drawable.ic_shield, "Berechtigungen", "Mikrofon, Kalender, Benachrichtigungen und Nicht stören", this::onboarding);
        p.row(R.drawable.ic_vibration, "Schnellzugriff und Vibration", "Kachel und Vibrationsmuster", this::shortcuts);
        p.row(R.drawable.ic_backup, "Sicherung und Diagnose", "Einstellungen übertragen und Ereignisse ansehen", this::maintenance);
        return p;
    }

    private void recording() {
        Page p = new Page("Aufnahme und Klang");
        p.liveRow(R.drawable.ic_mic, "Aufnahmeprofile", () -> Profile.selected(screen).name() + " aktiv",
                () -> new ProfileDialog(screen).choose());
        p.dialog.show();
    }

    private void playback() {
        Page p = new Page("Wiedergabe");
        String[] outputs = {"Lautsprecher nur nach Rückfrage", "Nur Bluetooth"};
        p.liveRow(R.drawable.ic_shield, "Audioausgang",
                () -> outputs[prefs.getBoolean(PlaybackOutput.BLUETOOTH_ONLY, false) ? 1 : 0],
                () -> new AlertDialog.Builder(screen).setTitle("Audioausgang")
                        .setSingleChoiceItems(outputs, prefs.getBoolean(PlaybackOutput.BLUETOOTH_ONLY, false) ? 1 : 0,
                                (dialog, index) -> {
                                    prefs.edit().putBoolean(PlaybackOutput.BLUETOOTH_ONLY, index == 1).apply();
                                    dialog.dismiss();
                                }).setNegativeButton("Abbrechen", null).show());
        p.liveRow(R.drawable.ic_play, "Wiedergabetempo", () -> PlaybackOptions.speedLabel(prefs),
                () -> PlaybackOptions.speed(screen, speed -> {}));
        p.liveRow(R.drawable.ic_forward, "Sprungintervall", () -> prefs.getInt("skipSeconds", 5) + " Sekunden",
                () -> PlaybackOptions.step(screen, step -> {}));
        Switch silence = new Switch(screen);
        silence.setText("Stille bei Wiedergabe überspringen");
        silence.setTextColor(MainActivity.INK);
        silence.setMinHeight(screen.dp(54));
        silence.setPadding(screen.dp(14), 0, screen.dp(14), 0);
        silence.setChecked(prefs.getBoolean("skipSilence", false));
        silence.setOnCheckedChangeListener((v, on) -> prefs.edit().putBoolean("skipSilence", on).apply());
        p.rows.addView(silence);
        p.dialog.show();
    }

    private void calendar() {
        Page p = new Page("Kalender und Dateinamen");
        p.liveRow(R.drawable.ic_calendar_day, "Kalender", () -> CalendarNames.ids(screen).size() + " Kalender ausgewählt",
                () -> screen.startActivity(new Intent(screen, CalendarSettings.class)));
        p.liveRow(R.drawable.ic_edit, "Dateiname und Ordner",
                () -> CalendarNames.template(screen) + " · " + CalendarNames.folderMode(screen).label,
                () -> screen.startActivity(new Intent(screen, NamingSettings.class)));
        p.dialog.show();
    }

    void restoreFileSettings(int scrollY) {
        Page parent = settingsHome();
        parent.dialog.show();
        files(parent, scrollY);
    }

    private void files(Page parent, int scrollY) {
        Page p = new Page("Dateien und Ordner");
        p.group("Ablage und Import");
        p.row(R.drawable.ic_folder, "Ordner verwalten", "Ordner bearbeiten und Speicherorte abgleichen", () -> new FolderSettings(screen).show());
        p.liveRow(R.drawable.ic_folder, "Speicher und Import",
                () -> prefs.contains("documentTree") ? "Android-Ordner gewählt" : "App-Speicher", this::storage);
        p.row(R.drawable.ic_forward, "Aufnahmen importieren", "Audiodateien auswählen", screen::pickImport);
        p.group("Papierkorb");
        p.row(R.drawable.ic_delete, screen.trashView ? "Zur Bibliothek" : "Papierkorb öffnen", "", () -> {
            p.dialog.dismiss();
            parent.dialog.dismiss();
            if (screen.trashView) screen.returnToLibrary();
            else screen.openTrash(p.scroll.getScrollY());
        });
        p.liveRow(R.drawable.ic_wait, "Nach 30 Tagen bereinigen",
                () -> prefs.getBoolean("cleanTrash", false) ? "Aktiv" : "Aus", this::toggleCleanup);
        if (screen.trashView) p.row(R.drawable.ic_delete, "Papierkorb leeren", "", screen::emptyTrash);
        p.dialog.show();
        if (scrollY > 0) p.scroll.post(() -> p.scroll.scrollTo(0, scrollY));
    }

    private void toggleCleanup() {
        if (prefs.getBoolean("cleanTrash", false)) {
            prefs.edit().putBoolean("cleanTrash", false).apply();
            return;
        }
        new AlertDialog.Builder(screen).setTitle("Dateien nach 30 Tagen endgültig löschen?")
                .setPositiveButton("Aktivieren", (d, w) -> {
                    prefs.edit().putBoolean("cleanTrash", true).apply();
                    screen.runFiles(() -> {
                        new LibraryFiles(app.sessions).cleanOld();
                        return "Bereinigung aktiv";
                    });
                }).setNegativeButton("Abbrechen", null).show();
    }

    private void cloud(Page parent) {
        Page p = new Page("Cloud und Uploads");
        p.row(R.drawable.ic_upload, "Uploadziele", "Dienste verbinden, Uploadregeln und Verzögerung einstellen", this::uploadTargets);
        p.row(R.drawable.ic_folder, "Bestehende Aufnahmen hochladen", "", () -> ManualUploadDialog.chooseRecordings(screen));
        p.row(R.drawable.ic_wait, "Warteschlange öffnen", "", () -> {
            if (screen.dashboardIndex() == 2) {
                p.dialog.dismiss();
                parent.dialog.dismiss();
            } else screen.openQueue();
        });
        p.dialog.show();
    }

    private void shortcuts() {
        Page p = new Page("Schnellzugriff und Vibration");
        p.liveRow(R.drawable.ic_vibration, "Vibration", () -> prefs.getBoolean("haptic", true) ? "Aktiv" : "Aus", screen::haptics);
        p.row(R.drawable.ic_record, "Kachel hinzufügen", "Android-Schnelleinstellungen", screen::addTile);
        p.dialog.show();
    }

    private void maintenance() {
        Page p = new Page("Sicherung und Diagnose");
        p.row(R.drawable.ic_backup, "Einstellungen exportieren", "JSON-Datei ohne Zugangsdaten",
                () -> create(20, "Audomo-Einstellungen.json", "application/json"));
        p.row(R.drawable.ic_folder, "Einstellungen importieren", "Profile und Regeln übernehmen",
                () -> screen.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json"), 21));
        p.row(R.drawable.ic_problem, "Diagnose", "Ereignisse, Audioeingang und Export", this::diagnostics);
        p.dialog.show();
    }

    void onboarding() {
        Page p = new Page("Berechtigungen");
        p.liveRow(R.drawable.ic_mic, "Mikrofon", () -> status(Manifest.permission.RECORD_AUDIO),
                () -> permission(Manifest.permission.RECORD_AUDIO, 15));
        p.liveRow(R.drawable.ic_calendar_day, "Kalender", () -> status(Manifest.permission.READ_CALENDAR),
                () -> permission(Manifest.permission.READ_CALENDAR, 4));
        p.liveRow(R.drawable.ic_bell, "Benachrichtigungen", () -> status(Manifest.permission.POST_NOTIFICATIONS),
                () -> {
                    if (screen.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                        permission(Manifest.permission.POST_NOTIFICATIONS, 2);
                    else screen.startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, screen.getPackageName()));
                });
        p.liveRow(R.drawable.ic_bell, "Nicht-stören-Zugriff",
                () -> screen.getSystemService(NotificationManager.class).isNotificationPolicyAccessGranted()
                        ? "Erlaubt" : "Zugriff einrichten",
                () -> screen.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)));
        p.row(R.drawable.ic_settings, "Android-App-Einstellungen", "Berechtigungen des Geräts verwalten",
                () -> screen.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + screen.getPackageName()))));
        p.dialog.show();
    }

    private void permission(String permission, int request) {
        if (screen.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
            screen.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + screen.getPackageName())));
        else screen.requestPermissions(new String[]{permission}, request);
    }

    String status(String p) {
        return screen.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
                ? "Erlaubt"
                : "Zugriff einrichten";
    }

    void uploadTargets() {
        new CloudScreen(screen).manage(java.util.List.of());
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
        p.liveRow(R.drawable.ic_search,
                "Vor Mediensuche verbergen",
                () -> prefs.getBoolean("hidden", false) ? "Aktiv" : "Aus",
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
        p.row(R.drawable.ic_share,
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
