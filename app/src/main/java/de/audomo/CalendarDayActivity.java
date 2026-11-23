package de.audomo;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.*;

public final class CalendarDayActivity extends Activity {
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private LinearLayout body;
    private TextView dateLabel;
    private Button automatic;
    private CancellationSignal query;
    private CalendarTimeline timeline;
    private int scroll = -1;
    private Audomo app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable midnight =
            () -> {
                scroll = -1;
                load();
            };

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private Button button(String label, Runnable action) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setOnClickListener(v -> action.run());
        return b;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        app = (Audomo) getApplication();
        scroll = state == null ? -1 : state.getInt("scroll", -1);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(16), dp(12), dp(16), dp(8));
        page.setBackgroundColor(MainActivity.BG);
        page.addView(text("Termin auswählen", 23, MainActivity.INK));
        dateLabel = text("", 15, MainActivity.ACCENT);
        dateLabel.setPadding(0, dp(3), 0, dp(12));
        page.addView(dateLabel);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        page.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.END);
        automatic = button("Automatisch zuordnen", () -> choose(null));
        footer.addView(automatic);
        footer.addView(button("Abbrechen", this::finish));
        page.addView(footer);
        setContentView(page);
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        if (query != null) query.cancel();
        main.removeCallbacks(midnight);
        ZoneId zone = ZoneId.systemDefault();
        LocalDate date = LocalDate.now(zone);
        long remaining =
                Duration.between(Instant.now(), date.plusDays(1).atStartOfDay(zone).toInstant())
                        .toMillis();
        main.postDelayed(midnight, Math.max(100, remaining));
        app.draft.validate(this);
        automatic.setVisibility(app.draft.get().event() == null ? View.GONE : View.VISIBLE);
        dateLabel.setText(
                DateTimeFormatter.ofPattern("EEEE, d. MMMM yyyy", Locale.GERMAN).format(date));
        body.removeAllViews();
        timeline = null;
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            boolean denied =
                    getPreferences(MODE_PRIVATE).getBoolean("requested", false)
                            && !shouldShowRequestPermissionRationale(
                                    Manifest.permission.READ_CALENDAR);
            body.addView(
                    text(
                            denied
                                    ? "Kalenderzugriff in den App-Einstellungen erlauben."
                                    : "Für die Tagesansicht braucht Audomo Kalenderzugriff.",
                            16,
                            MainActivity.INK));
            if (denied)
                body.addView(
                        button(
                                "App-Einstellungen",
                                () ->
                                        startActivity(
                                                new Intent(
                                                        android.provider.Settings
                                                                .ACTION_APPLICATION_DETAILS_SETTINGS,
                                                        android.net.Uri.fromParts(
                                                                "package",
                                                                getPackageName(),
                                                                null)))));
            else
                body.addView(
                        button(
                                "Kalenderzugriff erlauben",
                                () -> {
                                    getPreferences(MODE_PRIVATE)
                                            .edit()
                                            .putBoolean("requested", true)
                                            .apply();
                                    requestPermissions(
                                            new String[] {Manifest.permission.READ_CALENDAR}, 1);
                                }));
            return;
        }
        CancellationSignal task = new CancellationSignal();
        query = task;
        body.addView(text("Termine werden geladen …", 16, MainActivity.DIM));
        reader.execute(() -> {
            try {
                CalendarNames.Day day = CalendarNames.day(this, date, zone, task);
                runOnUiThread(() -> {
                    if (task.isCanceled() || isFinishing() || isDestroyed()) return;
                    body.removeAllViews();
                    if (day.appointments().isEmpty()) {
                        body.addView(
                                text(
                                        day.warning().isEmpty()
                                                ? "Heute keine Termine in den ausgewählten"
                                                        + " Kalendern."
                                                : day.warning(),
                                        16,
                                        MainActivity.INK));
                        body.addView(
                                button(
                                        "Kalender auswählen",
                                        () ->
                                                startActivity(
                                                        new Intent(this, CalendarSettings.class))));
                        return;
                    }
                    timeline =
                            new CalendarTimeline(
                                    this,
                                    date,
                                    zone,
                                    day.appointments(),
                                    app.draft.get().event(),
                                    scroll,
                                    this::choose);
                    body.addView(timeline, new LinearLayout.LayoutParams(-1, -1));
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (task.isCanceled() || isFinishing() || isDestroyed()) return;
                    body.removeAllViews();
                    body.addView(text("Kalender nicht lesbar", 16, MainActivity.INK));
                    body.addView(button("Erneut versuchen", this::load));
                });
            }
        });
    }

    private void choose(CalendarNames.Event event) {
        app.draft.select(event);
        if (!app.ready() && event != null)
            Toast.makeText(this, "Termin für die nächste Aufnahme gewählt", Toast.LENGTH_SHORT)
                    .show();
        finish();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (code == 1) load();
    }

    @Override
    public void onPause() {
        if (timeline != null) scroll = timeline.scrollPosition();
        main.removeCallbacks(midnight);
        if (query != null) query.cancel();
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("scroll", timeline == null ? scroll : timeline.scrollPosition());
    }

    @Override
    public void onDestroy() {
        if (query != null) query.cancel();
        reader.shutdownNow();
        super.onDestroy();
    }
}
