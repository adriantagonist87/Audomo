package de.audomo;

import android.app.AlertDialog;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

public final class UpdateSettings extends ProtectedActivity {
    private static final int SOURCES = 1, INSTALL = 2;
    private Audomo app;
    private AppUpdates updates;
    private TextView status, last;
    private Switch automatic;
    private Button interval, check, install;
    private ProgressBar progress;
    private boolean resumed;
    private String wantedVersion, permissionDigest, notice = "";
    private final Runnable observer = this::refresh;

    @Override protected void openContent(Bundle state) {
        app = (Audomo) getApplication();
        updates = app.updates;
        if (state != null) {
            wantedVersion = state.getString("wanted");
            permissionDigest = state.getString("permissionDigest");
            notice = state.getString("notice", "");
        }
        LinearLayout page = column();
        page.setPadding(dp(16), 0, dp(16), 0);
        page.setBackgroundColor(MainActivity.BG);
        setContentView(page);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(this, null, android.R.attr.borderlessButtonStyle);
        back.setImageResource(R.drawable.ic_arrow_back);
        back.setContentDescription("Zurück");
        back.setOnClickListener(v -> finish());
        header.addView(back, dp(48), dp(48));
        header.addView(label("Updates", 22, MainActivity.INK));
        page.addView(header);
        ScrollView scroll = new ScrollView(this);
        page.addView(scroll);
        LinearLayout body = column();
        body.setPadding(0, dp(8), 0, dp(16));
        scroll.addView(body);
        LinearLayout release = card(body, "Stabile GitHub-Version");
        release.addView(label("Installiert · Audomo " + BuildConfig.VERSION_NAME, 16, MainActivity.INK));
        status = label("", 16, MainActivity.INK);
        status.setPadding(0, dp(8), 0, 0);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        release.addView(status);
        last = label("", 14, MainActivity.DIM);
        release.addView(last);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        release.addView(progress);
        check = action(release, "Jetzt prüfen", () -> { notice = ""; updates.check(); });
        install = action(release, "Update herunterladen und installieren", () -> {
            if (!installWhenIdle()) { refresh(); return; }
            notice = "";
            wantedVersion = updates.latest.version();
            updates.download();
        });
        LinearLayout schedule = card(body, "Automatische Prüfung");
        automatic = new Switch(this);
        automatic.setText("Beim Öffnen der App prüfen");
        automatic.setTextSize(16);
        automatic.setTextColor(MainActivity.INK);
        automatic.setMinHeight(dp(48));
        automatic.setChecked(updates.automatic());
        automatic.setOnCheckedChangeListener((button, on) -> {
            Profile.prefs(this).edit().putBoolean(AppUpdates.AUTO, on).apply();
            updates.changed();
        });
        schedule.addView(automatic);
        interval = action(schedule, "", this::chooseInterval);
    }

    private void chooseInterval() {
        new AlertDialog.Builder(this).setTitle("Prüfintervall")
                .setSingleChoiceItems(AppUpdates.INTERVALS, updates.interval(), (d, i) -> {
                    Profile.prefs(this).edit().putInt(AppUpdates.INTERVAL, i).apply();
                    d.dismiss();
                    refresh();
                }).setNegativeButton("Abbrechen", null).show();
    }

    private boolean installWhenIdle() {
        if (app.ready() && !UploadQueue.get(app).transferring()) return true;
        notice = "Bitte warte, bis Aufnahme, Datenübertragung oder Datensicherung abgeschlossen sind.";
        return false;
    }

    @Override protected void resumeContent() {
        resumed = true;
        updates.subscribe(observer);
        refresh();
    }

    @Override protected void onPause() {
        resumed = false;
        if (updates != null) updates.unsubscribe(observer);
        super.onPause();
    }

    @Override protected void saveContent(Bundle state) {
        state.putString("wanted", wantedVersion);
        state.putString("permissionDigest", permissionDigest);
        state.putString("notice", notice);
    }

    private void refresh() {
        boolean idle = updates.work == AppUpdates.Work.IDLE;
        if (resumed && mayUseContent() && idle && wantedVersion != null) {
            String requested = wantedVersion;
            wantedVersion = null;
            if (updates.prepared != null && updates.prepared.version().equals(requested) && installWhenIdle())
                handOff();
        }
        boolean download = updates.work == AppUpdates.Work.DOWNLOAD;
        String message;
        if (download) message = updates.percent == 100 ? "APK wird geprüft …" : "APK wird heruntergeladen · " + updates.percent + " %";
        else if (!idle) message = "GitHub wird geprüft …";
        else if (!updates.error.isEmpty()) message = updates.error;
        else if (!notice.isEmpty()) message = notice;
        else if (updates.latest == null) message = "Noch keine Prüfung durchgeführt.";
        else if (updates.available()) message = "Audomo " + updates.latest.version() + " ist verfügbar.";
        else if (updates.latest.version().equals(BuildConfig.VERSION_NAME)) message = "Audomo ist aktuell.";
        else message = "Deine installierte Version ist neuer als das GitHub-Release " + updates.latest.version() + ".";
        if (!message.contentEquals(status.getText())) status.setText(message);
        long time = updates.lastCheck();
        last.setVisibility(time == 0 ? View.GONE : View.VISIBLE);
        if (time > 0) last.setText("Zuletzt erfolgreich geprüft · " + DateTimeFormatter
                .ofLocalizedDateTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time)));
        progress.setVisibility(idle ? View.GONE : View.VISIBLE);
        progress.setIndeterminate(!download);
        if (download) progress.setProgress(updates.percent);
        check.setEnabled(idle);
        install.setVisibility(updates.available() ? View.VISIBLE : View.GONE);
        install.setEnabled(idle);
        install.setText(updates.prepared == null ? "Update herunterladen und installieren" : "Installation öffnen");
        interval.setVisibility(updates.automatic() ? View.VISIBLE : View.GONE);
        interval.setText("Prüfintervall · " + AppUpdates.INTERVALS[updates.interval()]);
    }

    private void handOff() {
        try {
            if (!getPackageManager().canRequestPackageInstalls()) {
                permissionDigest = updates.prepared.sha256();
                startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.fromParts("package", getPackageName(), null)), SOURCES);
                return;
            }
            Uri apk = FileProvider.getUriForFile(this, "de.audomo.audio", GitHubUpdate.apk(this, updates.prepared));
            Intent installer = new Intent(Intent.ACTION_INSTALL_PACKAGE).setData(apk)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    .putExtra(Intent.EXTRA_RETURN_RESULT, true);
            startActivityForResult(installer, INSTALL);
        } catch (ActivityNotFoundException | SecurityException e) {
            notice = "Android konnte die Installation nicht öffnen. " + Audomo.cause(e);
        }
    }

    @Override protected void contentResult(int code, int result, Intent data) {
        if (code == SOURCES) {
            if (!getPackageManager().canRequestPackageInstalls()) {
                notice = "Android benötigt die Erlaubnis, Updates aus Audomo zu installieren.";
            } else if (updates.available() && updates.latest.sha256().equals(permissionDigest) && updates.work == AppUpdates.Work.IDLE) {
                wantedVersion = updates.latest.version();
                updates.download();
            } else notice = "Bitte wähle das verfügbare Update erneut aus.";
            permissionDigest = null;
        } else if (code == INSTALL) {
            notice = result == RESULT_CANCELED ? "Installation abgebrochen." :
                    result == RESULT_OK ? "Installation abgeschlossen." : "Android konnte das Update nicht installieren.";
        }
        if (resumed) refresh();
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView label(String value, int size, int color) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private LinearLayout card(LinearLayout parent, String name) {
        TextView title = label(name, 14, MainActivity.DIM);
        title.setAccessibilityHeading(true);
        title.setPadding(dp(4), dp(14), 0, dp(6));
        parent.addView(title);
        LinearLayout card = column();
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        card.setBackground(surface(MainActivity.PANEL));
        parent.addView(card);
        return card;
    }

    private Button action(LinearLayout parent, String title, Runnable click) {
        Button button = new Button(this);
        button.setText(title);
        button.setTextColor(MainActivity.INK);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x448e83bb), surface(MainActivity.BG), null));
        button.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(6);
        parent.addView(button, params);
        return button;
    }

    private GradientDrawable surface(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(14));
        drawable.setStroke(dp(1), MainActivity.LINE);
        return drawable;
    }

    private int dp(int n) { return Math.round(n * getResources().getDisplayMetrics().density); }
}
