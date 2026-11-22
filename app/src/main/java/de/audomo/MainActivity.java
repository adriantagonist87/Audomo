package de.audomo;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    static final int BG = 0xff0d0b2b, PANEL = 0xff242039, LINE = 0xff39354f;
    static final int INK = 0xfffaf8ff, DIM = 0xffc2bed1, ACCENT = 0xffbd9eff;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService files = Executors.newSingleThreadExecutor();
    private Audomo app;
    private LinearLayout root, body, controls;
    private TextView name, timer, status, note, mic, format, recordTab, libraryTab;
    private Wave wave;
    private View recordLine, libraryLine;
    private TextView calendar;
    private EditText inlineName;
    private CalendarNames.Match previewEvent = new CalendarNames.Match(null, "");
    private long nextPreview, previewVersion;
    private boolean editing;
    private boolean library, pendingStart, resumed;
    private String manualName = "";
    private Profile currentProfile;
    private final SharedPreferences.OnSharedPreferenceChangeListener optionsChanged =
            (options, key) -> currentProfile = Profile.selected(this);
    private Audomo.State shown;
    private LibraryView recordings;
    private File export;
    private final Runnable refresh =
            new Runnable() {
                public void run() {
                    if (pendingStart
                            && resumed
                            && hasWindowFocus()
                            && app.ready()
                            && permitted(Manifest.permission.RECORD_AUDIO)) {
                        pendingStart = false;
                        app.command("START", manualName);
                    }
                    if (app.state != shown) {
                        if (app.state == Audomo.State.SAVED) manualName = "";
                        if (!app.ready()) stopPlayer();
                        shown = app.state;
                        if (library) showLibrary();
                        else updateControls();
                    }
                    if (!library) {
                        boolean idle = app.ready();
                        timer.setText(Audomo.duration(idle ? 0 : app.frames));
                        long now = SystemClock.elapsedRealtime();
                        if (idle && now >= nextPreview) {
                            nextPreview = now + 15000;
                            long version = ++previewVersion;
                            CalendarNames.at(MainActivity.this, OffsetDateTime.now())
                                    .thenAccept(result ->
                                            runOnUiThread(() -> {
                                                if (version == previewVersion)
                                                    previewEvent = result;
                                            }));
                        }
                        calendar.setText(
                                idle || app.eventText.isEmpty()
                                        ? CalendarNames.describe(previewEvent)
                                        : app.eventText);
                        String active =
                                !idle && !app.activeName.isEmpty() ? app.activeName : previewName();
                        name.setText(active);
                        name.setEnabled(idle);
                        status.setText(
                                switch (app.state) {
                                    case RECORDING -> "Aufnahme läuft";
                                    case PAUSED -> "Aufnahme pausiert";
                                    case STARTING -> "Mikrofon wird geöffnet";
                                    case FINALIZING -> "Aufnahme wird gesichert";
                                    case RECOVERING -> "Aufnahmen werden geprüft";
                                    default -> "Bereit zur Aufnahme";
                                });
                        String info = idle ? app.note : app.systemNote;
                        if (app.spaceLow && !idle) info = "Wenig freier Speicherplatz";
                        note.setText(info);
                        note.setVisibility(info.isEmpty() ? View.GONE : View.VISIBLE);
                        mic.setText(
                                !idle && !app.route.isEmpty()
                                        ? app.route
                                        : "Mikrofonwunsch: " + currentProfile.mic());
                        format.setText("Opus · " + currentProfile.kbps() + " kbit/s");
                        if (app.state == Audomo.State.RECORDING) wave.sample(app.peak);
                    }
                    handler.postDelayed(this, 150);
                }
            };

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        app = (Audomo) getApplication();
        currentProfile = Profile.selected(this);
        prefs().registerOnSharedPreferenceChangeListener(optionsChanged);
        if (state != null) {
            manualName = state.getString("name", "");
            library = state.getBoolean("library");
            pendingStart = state.getBoolean("pending");
        }
        root = column();
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), 0, dp(20), dp(12));
        setContentView(root);
        LinearLayout header = row();
        root.addView(header, lp(-1, 60));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.brand);
        header.addView(logo, lp(34, 34));
        TextView brand = text("Audomo", 23, INK);
        brand.setPadding(dp(9), 0, 0, 0);
        brand.setTypeface(getResources().getFont(R.font.inter_display_light));
        header.addView(brand, weight());
        ImageButton more = icon("Weitere Optionen", R.drawable.ic_more, INK, BG);
        more.setOnClickListener(v -> menu(more));
        header.addView(more, lp(48, 48));
        LinearLayout tabs = row();
        root.addView(tabs);
        recordTab = tab("Aufnehmen", false);
        libraryTab = tab("Wiedergeben", true);
        tabs.addView(recordTab, weight());
        tabs.addView(libraryTab, weight());
        LinearLayout indicator = row();
        root.addView(indicator, lp(-1, 2));
        recordLine = new View(this);
        libraryLine = new View(this);
        indicator.addView(recordLine, new LinearLayout.LayoutParams(0, -1, 1));
        indicator.addView(libraryLine, new LinearLayout.LayoutParams(0, -1, 1));
        body = column();
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        if (library) showLibrary();
        else showRecorder();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("options", MODE_PRIVATE);
    }

    private TextView tab(String label, boolean toLibrary) {
        TextView t = text(label, 16, DIM);
        t.setGravity(
                toLibrary
                        ? Gravity.END | Gravity.CENTER_VERTICAL
                        : Gravity.START | Gravity.CENTER_VERTICAL);
        t.setMinHeight(dp(48));
        t.setOnClickListener(v -> {
            if (toLibrary) showLibrary();
            else showRecorder();
        });
        return t;
    }

    private void switchTab(boolean toLibrary) {
        finishName();
        stopPlayer();
        library = toLibrary;
        recordLine.setBackgroundColor(toLibrary ? LINE : ACCENT);
        libraryLine.setBackgroundColor(toLibrary ? ACCENT : LINE);
        body.removeAllViews();
        recordTab.setTextColor(toLibrary ? DIM : ACCENT);
        libraryTab.setTextColor(toLibrary ? ACCENT : DIM);
        recordTab.setSelected(!toLibrary);
        libraryTab.setSelected(toLibrary);
    }

    private void showRecorder() {
        switchTab(false);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        body.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = column();
        scroll.addView(content);
        content.addView(label("Aktueller Kalendertermin"));
        calendar = text("Kein Termin zugeordnet", 18, INK);
        content.addView(calendar);
        gap(content, 16);
        divider(content);
        content.addView(label("Dateiname"));
        name = text("", 17, INK);
        name.setMinHeight(dp(48));
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setOnClickListener(v -> editName());
        content.addView(name);
        inlineName = new EditText(this);
        inlineName.setSingleLine();
        inlineName.setSelectAllOnFocus(true);
        inlineName.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        inlineName.setVisibility(View.GONE);
        content.addView(inlineName);
        editing = false;
        inlineName.setOnEditorActionListener((v, id, event) -> {
            finishName();
            return true;
        });
        inlineName.setOnFocusChangeListener((v, focused) -> {
            if (!focused && editing) finishName();
        });
        content.addView(text("Zum Bearbeiten antippen", 12, DIM));
        LinearLayout center = column();
        center.setGravity(Gravity.CENTER);
        center.setMinimumHeight(dp(160));
        content.addView(center, new LinearLayout.LayoutParams(-1, -2, 1));
        timer = text("00:00:00", 42, INK);
        timer.setGravity(Gravity.CENTER);
        timer.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        center.addView(timer);
        status = text("", 15, DIM);
        status.setGravity(Gravity.CENTER);
        center.addView(status);
        wave = new Wave();
        center.addView(wave, lp(-1, 42));
        gap(center, 16);
        divider(content);
        LinearLayout info = row();
        mic = text("", 12, DIM);
        format = text("", 12, DIM);
        info.addView(mic, weight());
        info.addView(format);
        info.setMinimumHeight(dp(48));
        content.addView(info);
        note = text(app.note, 14, ACCENT);
        note.setOnClickListener(v -> {
            if (app.ready()) showLibrary();
        });
        content.addView(note);
        controls = row();
        controls.setGravity(Gravity.CENTER);
        body.addView(controls, lp(-1, 85));
        updateControls();
    }

    private void updateControls() {
        controls.removeAllViews();
        boolean recording = app.state == Audomo.State.RECORDING,
                paused = app.state == Audomo.State.PAUSED;
        int surface = getColor(android.R.color.system_neutral1_800);
        Button folder = button("Dateien", v -> showLibrary());
        controls.addView(folder, weight());
        FrameLayout middle = new FrameLayout(this);
        controls.addView(middle, lp(104, 70));
        ImageButton main =
                icon(
                        recording
                                ? "Aufnahme stoppen"
                                : paused ? "Aufnahme fortsetzen" : "Aufnahme starten",
                        recording ? R.drawable.ic_stop : R.drawable.ic_play,
                        recording ? INK : 0xff71db9b,
                        recording ? 0xff575564 : surface);
        main.setEnabled(app.ready() || recording || paused);
        main.setAlpha(main.isEnabled() ? 1f : .5f);
        main.setHapticFeedbackEnabled(false);
        main.setOnClickListener(v -> {
            if (app.ready()) startCapture();
            else if (app.active()) app.command(paused ? "RESUME" : "STOP", null);
        });
        middle.addView(main, new FrameLayout.LayoutParams(dp(56), dp(56), Gravity.CENTER));
        if (recording || paused) {
            FrameLayout side = new FrameLayout(this);
            controls.addView(side, weight());
            ImageButton aux =
                    icon(
                            paused ? "Aufnahme stoppen" : "Aufnahme pausieren",
                            paused ? R.drawable.ic_stop : R.drawable.ic_pause,
                            INK,
                            0xff575564);
            aux.setOnClickListener(v -> app.command(paused ? "STOP" : "PAUSE", null));
            side.addView(aux, new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER));
        } else {
            Button profile = button("Profil", v -> profiles());
            profile.setEnabled(app.ready());
            controls.addView(profile, weight());
        }
    }

    private void startCapture() {
        finishName();
        stopPlayer();
        if (!permitted(Manifest.permission.RECORD_AUDIO)) {
            requestPermissions(new String[] {Manifest.permission.RECORD_AUDIO}, 1);
        } else {
            pendingStart = true;
        }
    }

    private boolean permitted(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == 1) {
            pendingStart = permitted(Manifest.permission.RECORD_AUDIO);
            if (!pendingStart) {
                app.note = "Mikrofonzugriff fehlt. In den App-Einstellungen freigeben.";
                app.vibrate("error");
            }
        }
    }

    private String previewName() {
        if (!manualName.isBlank()) return SessionStore.fileName(manualName, OffsetDateTime.now());
        return CalendarNames.name(
                CalendarNames.template(this),
                OffsetDateTime.now(),
                previewEvent.event() == null ? null : previewEvent.event().title());
    }

    private void editName() {
        if (!app.ready()) return;
        editing = true;
        inlineName.setText(previewName().replaceFirst("\\.ogg$", ""));
        name.setVisibility(View.GONE);
        inlineName.setVisibility(View.VISIBLE);
        inlineName.requestFocus();
        inlineName.selectAll();
        getSystemService(android.view.inputmethod.InputMethodManager.class)
                .showSoftInput(inlineName, 0);
    }

    private void finishName() {
        if (!editing) return;
        editing = false;
        manualName = inlineName.getText().toString();
        getSystemService(android.view.inputmethod.InputMethodManager.class)
                .hideSoftInputFromWindow(inlineName.getWindowToken(), 0);
        inlineName.setVisibility(View.GONE);
        name.setVisibility(View.VISIBLE);
    }

    private void profiles() {
        new ProfileDialog(this).choose();
    }

    private void menu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu()
                .add("Kalender und Dateinamen")
                .setOnMenuItemClickListener(item -> {
                    startActivity(new Intent(this, CalendarSettings.class));
                    return true;
                });
        menu.getMenu()
                .add("Kachel hinzufügen")
                .setOnMenuItemClickListener(item -> {
                    getSystemService(StatusBarManager.class)
                            .requestAddTileService(
                                    new ComponentName(this, CaptureTile.class),
                                    "Audomo",
                                    android.graphics.drawable.Icon.createWithResource(
                                            this, R.drawable.ic_mic),
                                    getMainExecutor(),
                                    code -> {
                                        if (code
                                                        != StatusBarManager
                                                                .TILE_ADD_REQUEST_RESULT_TILE_ADDED
                                                && code
                                                        != StatusBarManager
                                                                .TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED)
                                            toast(
                                                    "Schnelleinstellungen öffnen, Bearbeiten wählen"
                                                        + " und Audomo hinzufügen.");
                                    });
                    return true;
                });
        menu.getMenu()
                .add("Benachrichtigungen")
                .setOnMenuItemClickListener(item -> {
                    if (!permitted(Manifest.permission.POST_NOTIFICATIONS))
                        requestPermissions(
                                new String[] {Manifest.permission.POST_NOTIFICATIONS}, 2);
                    else toast("Benachrichtigungen sind erlaubt");
                    return true;
                });
        menu.getMenu()
                .add("Nicht-stören-Zugriff")
                .setOnMenuItemClickListener(item -> {
                    startActivity(
                            new Intent(
                                    android.provider.Settings
                                            .ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
                    return true;
                });
        menu.getMenu()
                .add("Vibration")
                .setOnMenuItemClickListener(item -> {
                    haptics();
                    return true;
                });
        menu.getMenu()
                .add("Diagnose")
                .setOnMenuItemClickListener(item -> {
                    File log = new File(getFilesDir(), "diagnose.txt");
                    String detail = app.route + "\n" + app.audioDetails;
                    try {
                        if (log.exists())
                            detail +=
                                    "\n"
                                            + new String(
                                                    java.nio.file.Files.readAllBytes(log.toPath()),
                                                    java.nio.charset.StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        detail += Audomo.cause(e);
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Diagnose")
                            .setMessage(detail.isBlank() ? "Keine Ereignisse" : detail)
                            .setPositiveButton("Schließen", null)
                            .show();
                    return true;
                });
        menu.show();
    }

    private void haptics() {
        LinearLayout settings = column();
        settings.setPadding(dp(20), 0, dp(20), 0);
        Switch enabled = new Switch(this);
        enabled.setText("Vibration aktiv");
        enabled.setMinHeight(dp(48));
        enabled.setChecked(prefs().getBoolean("haptic", true));
        settings.addView(enabled);
        enabled.setOnCheckedChangeListener(
                (b, on) -> prefs().edit().putBoolean("haptic", on).apply());
        String[] events = {"start", "stop", "error"}, labels = {"Start", "Stopp", "Fehler"};
        String[] options = {"Zwei kurze Impulse", "Ein langer Impuls", "Drei kurze Impulse"};
        for (int i = 0; i < events.length; i++) {
            String event = events[i];
            String title = labels[i];
            int defaultValue = i;
            settings.addView(
                    button(
                            title + " · Muster wählen",
                            v ->
                                    new AlertDialog.Builder(this)
                                            .setTitle(title)
                                            .setSingleChoiceItems(
                                                    options,
                                                    prefs().getInt(
                                                                    "pattern." + event,
                                                                    defaultValue),
                                                    (dialog, k) -> {
                                                        prefs().edit()
                                                                .putInt("pattern." + event, k)
                                                                .apply();
                                                        app.vibrate(event);
                                                        dialog.dismiss();
                                                    })
                                            .show()));
        }
        new AlertDialog.Builder(this)
                .setTitle("Vibration")
                .setView(settings)
                .setPositiveButton("Schließen", null)
                .show();
    }

    private void showLibrary() {
        switchTab(true);
        LinearLayout page = column();
        body.addView(page, new LinearLayout.LayoutParams(-1, -1));
        page.addView(label("Aufnahmen"));
        files.execute(() -> {
            try {
                List<SessionStore.Entry> entries = app.sessions.list();
                runOnUiThread(() -> {
                    if (!library || page.getParent() != body || isDestroyed()) return;
                    if (entries.isEmpty())
                        page.addView(text("Noch keine gespeicherten Aufnahmen", 16, DIM));
                    else libraryRows(page, entries);
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast(Audomo.cause(e)));
            }
        });
    }

    private void libraryRows(LinearLayout page, List<SessionStore.Entry> entries) {
        recordings = new LibraryView(this, page, entries);
    }

    void export(File source) {
        export = source;
        startActivityForResult(
                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .setType("audio/ogg")
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(Intent.EXTRA_TITLE, source.getName()),
                3);
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req != 3
                || result != RESULT_OK
                || data == null
                || data.getData() == null
                || export == null) return;
        File source = export;
        android.net.Uri target = data.getData();
        files.execute(() -> {
            try (InputStream in = new FileInputStream(source);
                    OutputStream out = getContentResolver().openOutputStream(target, "wt")) {
                if (out == null) throw new IOException("Ziel nicht beschreibbar");
                in.transferTo(out);
            } catch (Exception e) {
                runOnUiThread(() -> toast(Audomo.cause(e)));
                return;
            }
            runOnUiThread(() -> toast("Exportiert"));
        });
    }

    private void stopPlayer() {
        if (recordings != null) recordings.stop();
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    private final class Wave extends View {
        final float[] levels = new float[50];
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Wave() {
            super(MainActivity.this);
            paint.setColor(ACCENT);
            paint.setStrokeWidth(dp(2));
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        void sample(float x) {
            System.arraycopy(levels, 1, levels, 0, levels.length - 1);
            levels[levels.length - 1] = Math.min(1, x);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!app.active()) return;
            float mid = getHeight() / 2f, step = getWidth() / (float) levels.length;
            for (int i = 0; i < levels.length; i++) {
                float h = Math.max(dp(1), mid * levels[i]);
                c.drawLine(step * (i + .5f), mid - h, step * (i + .5f), mid + h, paint);
            }
        }
    }

    int dp(float n) {
        return (int) (getResources().getDisplayMetrics().density * n + .5f);
    }

    LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h));
    }

    LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, -2, 1);
    }

    TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private TextView label(String s) {
        TextView t = text(s.toUpperCase(Locale.GERMAN), 11, ACCENT);
        t.setPadding(0, dp(16), 0, dp(6));
        return t;
    }

    private void divider(LinearLayout parent) {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        parent.addView(v, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void gap(LinearLayout parent, int height) {
        parent.addView(new View(this), lp(-1, height));
    }

    RippleDrawable surface(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radius));
        g.setStroke(dp(1), LINE);
        return new RippleDrawable(ColorStateList.valueOf(0x448e83bb), g, null);
    }

    Button button(String label, View.OnClickListener action) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setTextColor(INK);
        b.setMinHeight(dp(48));
        b.setMinimumWidth(0);
        b.setMinWidth(0);
        b.setBackground(surface(PANEL, 12));
        b.setOnClickListener(action);
        return b;
    }

    ImageButton icon(String label, int res, int tint, int color) {
        ImageButton b = new ImageButton(this);
        b.setImageResource(res);
        b.setColorFilter(tint);
        b.setContentDescription(label);
        b.setBackground(surface(color, 50));
        return b;
    }

    @Override
    protected void onResume() {
        super.onResume();
        nextPreview = 0;
        resumed = true;
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        resumed = false;
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    @Override
    protected void onStop() {
        stopPlayer();
        super.onStop();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("name", manualName);
        state.putBoolean("library", library);
        state.putBoolean("pending", pendingStart);
    }

    @Override
    protected void onDestroy() {
        prefs().unregisterOnSharedPreferenceChangeListener(optionsChanged);
        handler.removeCallbacksAndMessages(null);
        stopPlayer();
        files.shutdown();
        super.onDestroy();
    }
}
