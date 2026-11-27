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
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import java.io.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends ProtectedActivity {
    static final int RECORD = 0, LIBRARY = 1, QUEUE = 2;
    static final int BG = 0xff0d0b2b, PANEL = 0xff242039, LINE = 0xff39354f;
    static final int INK = 0xfffaf8ff, DIM = 0xffc2bed1, ACCENT = 0xffbd9eff;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Audomo app;
    private LinearLayout controls;
    private ScrollView recorderScroll;
    private DashboardPager pager;
    private int activeTab;
    private boolean resetLibrary, resetQueue, libraryTrash;
    private List<SessionStore.Entry> cachedEntries;
    private int libraryRequest;
    private TextView name, timer, status, note, mic, format, recordTab, libraryTab, queueTab;
    private Wave wave;
    private Button uploadAttention;
    private long nextUploadCheck;
    private boolean checkingUploads;
    private QueueDashboard dashboard;
    private TextView calendar, calendarHeading;
    private LinearLayout calendarButton;
    private EditText inlineName;
    private CalendarNames.Match previewEvent = new CalendarNames.Match(null, "");
    private long nextPreview, previewVersion;
    private boolean editing;
    private boolean pendingStart, resumed;
    private Profile currentProfile;
    private final SharedPreferences.OnSharedPreferenceChangeListener optionsChanged =
            (options, key) -> currentProfile = Profile.selected(this);
    private Audomo.State shown;
    private LibraryView recordings;
    private File export;
    boolean trashView;
    private final ArrayList<Bundle> history = new ArrayList<>();
    private Bundle loadingLibrary;
    private Bundle pausedLibrary;
    private boolean openingTab;
    private final OnBackInvokedCallback back = this::navigateBack;
    private boolean handlingBack;
    String importFolder;
    private AppSettings preferences;
    private final Runnable refresh =
            new Runnable() {
                public void run() {
                    if (isQueuePage()) {
                        dashboard.refresh();
                        handler.postDelayed(this, 1000);
                        return;
                    }
                    if (activeTab == RECORD && pendingStart
                            && resumed
                            && hasWindowFocus()
                            && app.ready()
                            && permitted(Manifest.permission.RECORD_AUDIO)) {
                        pendingStart = false;
                        app.command("START", app.draft.get().name());
                    }
                    if (app.state != shown) {
                        if (!app.ready()) stopPlayer();
                        shown = app.state;
                        if (activeTab == LIBRARY) readLibrary();
                        if (controls != null) updateControls();
                    }
                    if (activeTab == RECORD) {
                        updateRecorder();
                    } else if (recordings != null) {
                        recordings.refreshStatus();
                    }
                    handler.postDelayed(this, 150);
                }
            };

    private void updateRecorder() {
        boolean idle = app.ready();
        timer.setText(
                Audomo.duration(
                        idle && app.state != Audomo.State.FAILED ? 0 : app.frames));
        long now = SystemClock.elapsedRealtime();
        if (now >= nextUploadCheck && !checkingUploads) {
            nextUploadCheck = now + 5000;
            refreshUploadAttention();
        }
        CalendarNames.Event chosen = app.draft.get().event();
        if (idle && chosen == null && now >= nextPreview) {
            nextPreview = now + 15000;
            long version = ++previewVersion;
            CalendarNames.at(MainActivity.this, OffsetDateTime.now())
                    .thenAccept(result ->
                            runOnUiThread(() -> {
                                if (version == previewVersion)
                                    previewEvent = result;
                            }));
        }
        String heading = chosen == null ? "Aktueller Kalendertermin" : "Termin für die nächste Aufnahme";
        String appointment = chosen != null ? CalendarNames.describe(new CalendarNames.Match(chosen, ""))
                : idle || app.eventText.isEmpty() ? CalendarNames.describe(previewEvent) : app.eventText;
        String caption = heading.toUpperCase(Locale.GERMAN);
        if (!caption.contentEquals(calendarHeading.getText())) calendarHeading.setText(caption);
        if (!appointment.contentEquals(calendar.getText())) calendar.setText(appointment);
        String description = heading + " · " + appointment + " · Tagesansicht öffnen";
        if (!android.text.TextUtils.equals(description,calendarButton.getContentDescription()))
            calendarButton.setContentDescription(description);
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
                    case FAILED -> "Aufnahme unterbrochen";
                    default -> "Bereit zur Aufnahme";
                });
        String info = idle ? app.note : app.systemNote;
        if (app.state == Audomo.State.SAVED
                && app.note.startsWith("Gespeichert ·")
                && now - app.stateSince > 4500) info = "";
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

    @Override
    protected void openContent(Bundle state) {
        app = (Audomo) getApplication();
        currentProfile = Profile.selected(this);
        preferences = new AppSettings(this);
        prefs().registerOnSharedPreferenceChangeListener(optionsChanged);
        activeTab = this instanceof LibraryActivity ? LIBRARY : this instanceof QueueActivity ? QUEUE : RECORD;
        String legacyDraft = state == null ? getIntent().getStringExtra("draftName") : state.getString("name");
        if (legacyDraft != null && !legacyDraft.isBlank() && app.draft.get().event() == null && app.draft.get().name().isEmpty())
            app.draft.name(legacyDraft);
        if (state != null) {
            activeTab = state.getInt("tab", state.getBoolean("library") ? LIBRARY : activeTab);
            trashView = state.getBoolean("trashView");
            ArrayList<Bundle> savedHistory = state.getParcelableArrayList("history", Bundle.class);
            if (savedHistory != null) history.addAll(savedHistory);
            if (state.getString("export") != null) export = new File(state.getString("export"));
            importFolder = state.getString("importFolder");
            pendingStart = state.getBoolean("pending");
        }
        LinearLayout root = new DashboardGestures(this);
        root.setBackgroundColor(BG);
        root.setPadding(0, 0, 0, dp(12));
        setContentView(root);
        LinearLayout chrome = column();
        chrome.setPadding(dp(20), 0, dp(20), 0);
        root.addView(chrome);
        LinearLayout header = row();
        chrome.addView(header, lp(-1, 60));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.brand);
        header.addView(logo, lp(34, 34));
        TextView brand = text("Audomo", 23, INK);
        brand.setPadding(dp(9), 0, 0, 0);
        brand.setTypeface(getResources().getFont(R.font.inter_display_light));
        header.addView(brand, weight());
        ImageButton settings = toolbarIcon("Einstellungen", R.drawable.ic_settings);
        settings.setOnClickListener(v -> preferences.show());
        header.addView(settings, lp(48, 48));
        LinearLayout tabs = row();
        chrome.addView(tabs);
        recordTab = tab("Aufnehmen", () -> swipeTo(RECORD));
        libraryTab = tab("Wiedergeben", this::openLibrary);
        tabs.addView(recordTab, weight());
        tabs.addView(libraryTab, weight());
        queueTab = tab("Warteschlange", this::openQueue);
        tabs.addView(queueTab, weight());
        DashboardPager.Strip indicator = new DashboardPager.Strip(this, recordTab, libraryTab, queueTab);
        chrome.addView(indicator, lp(-1, 3));
        pager = new DashboardPager(this, indicator, activeTab, this::warmPages);
        root.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1));
        if (state == null && "de.audomo.UPLOADS".equals(getIntent().getAction()) && activeTab == RECORD) {
            rememberPage(-1);
            activeTab = QUEUE;
            pager.select(QUEUE);
            pager.move(QUEUE);
        }
        preparePage(activeTab, state);
        if (activeTab == RECORD) restoreRecorderScroll(state);
        updateBackHandler();
        if (isQueuePage() && state == null && getIntent().getLongExtra("uploadJob", -1) >= 0) {
            UploadDetailsDialog.open(this, getIntent().getLongExtra("uploadJob", -1));
        }
        else if (state == null && !prefs().getBoolean("onboardingSeen", false)) {
            prefs().edit().putBoolean("onboardingSeen", true).apply();
            if (!permitted(Manifest.permission.RECORD_AUDIO)) preferences.onboarding();
        }
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("options", MODE_PRIVATE);
    }

    private TextView tab(String label, Runnable go) {
        TextView t = text(label, 15, DIM);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        t.setHorizontallyScrolling(false);
        t.setPadding(dp(2), 0, dp(2), 0);
        // Keep the tab reachable at large system font sizes and size it to the actual column.
        int preferred = Math.round(t.getTextSize());
        int minimum = Math.min(dp(10), preferred - 1);
        t.setAutoSizeTextTypeUniformWithConfiguration(minimum, preferred, 1, android.util.TypedValue.COMPLEX_UNIT_PX);
        t.setMinHeight(dp(48));
        t.setOnClickListener(v -> go.run());
        return t;
    }

    private void refreshUploadAttention() {
        checkingUploads = true;
        app.uploadControls.execute(() -> {
            long count;
            try {
                count = UploadQueue.get(this).attentionCount();
            } catch (android.database.sqlite.SQLiteException unavailable) {
                count = -1;
            }
            long total = count;
            runOnUiThread(() -> {
                checkingUploads = false;
                if (isDestroyed() || uploadAttention == null || total < 0) return;
                uploadAttention.setVisibility(total > 0 ? View.VISIBLE : View.GONE);
                if (total > 0) uploadAttention.setText(total == 1 ? "1 Upload prüfen · Warteschlange öffnen"
                        : total + " Uploads prüfen · Warteschlange öffnen");
            });
        });
    }

    private void showRecorder() {
        LinearLayout body = pager.pages[RECORD];
        recorderScroll = new ScrollView(this);
        recorderScroll.setFillViewport(true);
        body.addView(recorderScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout content = column();
        recorderScroll.addView(content);
        calendarButton = row();
        calendarButton.setMinimumHeight(dp(58));
        calendarButton.setFocusable(true);
        calendarButton.setOnClickListener(v -> {
            if (openingTab) return;
            openingTab = true;
            finishName();
            startActivity(new Intent(this, CalendarDayActivity.class));
        });
        android.util.TypedValue selectable = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.selectableItemBackground, selectable, true))
            calendarButton.setBackgroundResource(selectable.resourceId);
        LinearLayout calendarLabels = column();
        calendarHeading = label("Aktueller Kalendertermin");
        calendarLabels.addView(calendarHeading);
        calendar = text("Kein Termin zugeordnet", 18, INK);
        calendarLabels.addView(calendar);
        calendarButton.addView(calendarLabels, weight());
        ImageView openDay = new ImageView(this);
        openDay.setImageResource(R.drawable.ic_calendar_day);
        openDay.setColorFilter(ACCENT);
        openDay.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        calendarButton.addView(openDay, lp(24, 24));
        content.addView(calendarButton);
        gap(content, 6);
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
            if (app.ready()) openLibrary();
        });
        content.addView(note);
        uploadAttention = button("", v -> {
            Bundle review = new Bundle();
            review.putBoolean("queueReviewingIssues", true);
            visit(QUEUE, review, -1);
        });
        uploadAttention.setVisibility(View.GONE);
        content.addView(uploadAttention);
        nextUploadCheck = 0;
        controls = row();
        controls.setGravity(Gravity.CENTER);
        body.addView(controls, lp(-1, 85));
        updateControls();
    }

    private void updateControls() {
        controls.removeAllViews();
        if (app.state == Audomo.State.FAILED) {
            controls.addView(button("Erneut versuchen", v -> startCapture()), weight());
            controls.addView(
                    button(
                            "Beenden",
                            v -> {
                                app.note = "";
                                app.change(Audomo.State.READY);
                            }),
                    weight());
            return;
        }
        boolean recording = app.state == Audomo.State.RECORDING,
                paused = app.state == Audomo.State.PAUSED;
        int surface = getColor(android.R.color.system_neutral1_800);
        Button folder = button("Dateien", v -> openLibrary());
        controls.addView(folder, weight());
        FrameLayout middle = new FrameLayout(this);
        controls.addView(middle, lp(104, 70));
        ImageButton main =
                icon(
                        recording
                                ? "Aufnahme stoppen"
                                : paused ? "Aufnahme fortsetzen" : "Aufnahme starten",
                        recording ? R.drawable.ic_stop
                                : paused ? R.drawable.ic_play : R.drawable.ic_record,
                        recording ? INK : 0xff85ad94,
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
            Button profile = button("Profil", v -> startActivity(new Intent(this, ProfileSettings.class)));
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
    protected void permissionResult(int code, String[] permissions, int[] results) {
        if (code == 1) {
            pendingStart = permitted(Manifest.permission.RECORD_AUDIO);
            if (!pendingStart) {
                app.note = "Mikrofonzugriff fehlt. In den App-Einstellungen freigeben.";
                app.vibrate("error");
            }
        }
    }

    private String previewName() {
        RecordingDraft.Value draft = app.draft.get();
        if (!draft.name().isBlank()) return SessionStore.fileName(draft.name(), OffsetDateTime.now());
        if (draft.event() != null) return CalendarNames.chosenName(CalendarNames.template(this),OffsetDateTime.now(),draft.event());
        return CalendarNames.name(
                CalendarNames.template(this),
                OffsetDateTime.now(),
                previewEvent.event());
    }

    private void editName() {
        if (!app.ready()) return;
        editing = true;
        updateBackHandler();
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
        updateBackHandler();
        app.draft.name(inlineName.getText().toString());
        getSystemService(android.view.inputmethod.InputMethodManager.class)
                .hideSoftInputFromWindow(inlineName.getWindowToken(), 0);
        inlineName.setVisibility(View.GONE);
        name.setVisibility(View.VISIBLE);
    }

    int dashboardIndex() {
        return activeTab;
    }

    DashboardPager pager() { return pager; }

    void swipeTo(int index) {
        if (index == LIBRARY && trashView) openLibrary();
        else if (index != activeTab) visit(index, null, -1);
        else pager.settle();
    }

    boolean outsidePlayer(float x, float y) {
        return activeTab == LIBRARY && recordings != null && recordings.outsidePlayer(x, y);
    }

    void closePlayer() {
        if (recordings != null) recordings.closePlayer();
    }

    void haptics() {
        LinearLayout settings = column();
        settings.setPadding(dp(20), 0, dp(20), 0);
        Switch enabled = new Switch(this);
        enabled.setText("Vibration aktiv");
        enabled.setMinHeight(dp(48));
        enabled.setChecked(prefs().getBoolean("haptic", true));
        settings.addView(enabled);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Vibration");
        if (!getSystemService(NotificationManager.class).areNotificationsEnabled()) {
            settings.addView(
                    text(
                            "Benachrichtigungen für Audomo sind ausgeschaltet. Auf dem Galaxy S24"
                                    + " blockiert das die Vibrationsmuster.",
                            14,
                            INK));
            dialog.setNeutralButton(
                    "Freigabe öffnen",
                    (ignored, which) ->
                            startActivity(
                                    new Intent(
                                                    android.provider.Settings
                                                            .ACTION_APP_NOTIFICATION_SETTINGS)
                                            .putExtra(
                                                    android.provider.Settings.EXTRA_APP_PACKAGE,
                                                    getPackageName())));
        }
        List<Button> previews = new ArrayList<>();
        String[] events = {"start", "stop", "error"}, labels = {"Start", "Stopp", "Fehler"};
        String[] options = {"Zwei kurze Impulse", "Ein langer Impuls", "Drei kurze Impulse"};
        for (int i = 0; i < events.length; i++) {
            String event = events[i];
            String title = labels[i];
            int defaultValue = i;
            LinearLayout actions = row();
            settings.addView(actions);
            actions.addView(
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
                                                    (choice, k) -> {
                                                        prefs().edit()
                                                                .putInt("pattern." + event, k)
                                                                .apply();
                                                        app.vibrate(event);
                                                        choice.dismiss();
                                                    })
                                            .show()),
                    weight());
            Button preview = button("Testen", v -> app.vibrate(event));
            preview.setContentDescription(title + " testen");
            preview.setEnabled(enabled.isChecked());
            actions.addView(preview);
            previews.add(preview);
        }
        enabled.setOnCheckedChangeListener((b, on) -> {
            prefs().edit().putBoolean("haptic", on).apply();
            for (Button preview : previews) preview.setEnabled(on);
        });
        ScrollView scroll = new ScrollView(this);
        scroll.addView(settings);
        dialog.setView(scroll).setPositiveButton("Schließen", null).show();
    }

    private boolean isQueuePage() {
        return activeTab == QUEUE;
    }

    void openQueue() { swipeTo(QUEUE); }

    void openLibrary() {
        if (activeTab != LIBRARY || trashView) visit(LIBRARY, null, -1);
    }

    private void warmPages() {
        if (pager == null || pager.position() != activeTab || isDestroyed() || isFinishing()) return;
        for (int i = 0; i < pager.pages.length; i++) {
            if (i != activeTab && (pager.pages[i].getChildCount() == 0
                    || i == LIBRARY && resetLibrary || i == QUEUE && resetQueue)) preparePage(i, null);
        }
    }

    void preparePage(int index, Bundle state) {
        if (index == RECORD) {
            if (controls == null) showRecorder();
            else updateControls();
            updateRecorder();
        } else if (index == QUEUE) {
            if (dashboard == null || resetQueue || state != null) {
                LinearLayout page = pager.pages[QUEUE];
                page.removeAllViews();
                dashboard = new QueueDashboard(this, page, state);
                resetQueue = false;
            } else dashboard.refresh();
        } else if (pager.pages[LIBRARY].getChildCount() == 0 || resetLibrary || state != null) {
            renderLibrary(state == null ? null : state.getBundle("libraryState"));
            resetLibrary = false;
        }
    }

    private void restoreRecorderScroll(Bundle state) {
        int y = state == null ? 0 : state.getInt("recordScroll");
        recorderScroll.post(() -> { if (activeTab == RECORD) recorderScroll.scrollTo(0, y); });
    }

    private void visit(int index, Bundle state, int settingsScroll) {
        rememberPage(settingsScroll);
        displayPage(index, state);
    }

    private void displayPage(int index, Bundle state) {
        finishName();
        if (activeTab == LIBRARY) {
            stopPlayer();
            resetLibrary = true;
        }
        if (isQueuePage()) resetQueue = true;
        int previous = activeTab;
        activeTab = index;
        trashView = state != null && state.getBoolean("trashView");
        if (Math.abs(index - previous) == 2) preparePage(LIBRARY, null);
        if (index == LIBRARY && !resetLibrary && state == null && pager.pages[LIBRARY].getChildCount() > 0) {
            if (recordings != null) recordings.refreshPreferences();
            readLibrary();
        }
        preparePage(index, state);
        if (index == RECORD) restoreRecorderScroll(state);
        pager.select(index);
        pager.settle();
        updateBackHandler();
        if (resumed) {
            handler.removeCallbacks(refresh);
            refresh.run();
        }
    }

    void openTrash(int settingsScroll) {
        if (trashView) return;
        Bundle trash = new Bundle();
        trash.putBoolean("trashView", true);
        visit(LIBRARY, trash, settingsScroll);
    }

    void returnToLibrary() {
        if (!history.isEmpty()) {
            Bundle previous = history.get(history.size() - 1);
            if (previous.getInt("tab", -1) == LIBRARY && !previous.getBoolean("trashView")
                    && !previous.containsKey("settingsScroll")) {
                restorePreviousPage();
                return;
            }
        }
        openLibrary();
    }

    private void rememberPage(int settingsScroll) {
        Bundle page = new Bundle();
        page.putInt("tab", activeTab);
        if (activeTab == RECORD && recorderScroll != null) page.putInt("recordScroll", recorderScroll.getScrollY());
        if (isQueuePage()) dashboard.save(page);
        page.putBoolean("trashView", trashView);
        if (activeTab == LIBRARY) page.putBundle("libraryState", libraryState());
        if (settingsScroll >= 0) page.putInt("settingsScroll", settingsScroll);
        history.add(page);
    }

    private void restorePreviousPage() {
        Bundle page = history.remove(history.size() - 1);
        displayPage(page.getInt("tab", page.getBoolean("library") ? LIBRARY
                : this instanceof QueueActivity ? QUEUE : RECORD), page);
        if (page.containsKey("settingsScroll")) preferences.restoreTrashSettings(page.getInt("settingsScroll"));
    }

    private void updateBackHandler() {
        boolean needed = activeTab != RECORD || editing || !history.isEmpty();
        if (needed == handlingBack) return;
        handlingBack = needed;
        if (needed) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, back);
        else getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(back);
    }

    private void navigateBack() {
        if (editing) {
            finishName();
            return;
        }
        if (isQueuePage() && dashboard.exitSelection()) return;
        if (activeTab == LIBRARY && recordings != null && recordings.navigateBack()) return;
        if (!history.isEmpty()) restorePreviousPage();
        else if (this instanceof LibraryActivity || this instanceof QueueActivity) finish();
        else displayPage(RECORD, null);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // Some navigation modes deliver Back through Activity rather than the window callback.
        if (mayUseContent() && (activeTab != RECORD || editing || !history.isEmpty())) navigateBack();
        else super.onBackPressed();
    }

    private Bundle libraryState() {
        return recordings == null ? loadingLibrary : recordings.saveState();
    }

    private void renderLibrary(Bundle restore) {
        stopPlayer();
        recordings = null;
        loadingLibrary = restore;
        libraryTrash = activeTab == LIBRARY && trashView;
        LinearLayout page = pager.pages[LIBRARY];
        page.removeAllViews();
        if (cachedEntries != null) buildLibrary(cachedEntries, restore);
        else page.addView(text("Aufnahmen werden geladen …", 16, DIM));
        readLibrary();
    }

    private void buildLibrary(List<SessionStore.Entry> entries, Bundle state) {
        stopPlayer();
        LinearLayout page = pager.pages[LIBRARY];
        page.removeAllViews();
        recordings = new LibraryView(this, page, entries, libraryTrash);
        if (state != null) recordings.restoreState(state);
        loadingLibrary = null;
    }

    private void readLibrary() {
        int request = ++libraryRequest;
        app.libraryFiles.execute(() -> {
            try {
                List<SessionStore.Entry> entries = new CloudLibrary(app.sessions).library();
                runOnUiThread(() -> {
                    if (request != libraryRequest || isDestroyed()) return;
                    if (recordings == null || !entries.equals(cachedEntries)) {
                        Bundle state = libraryState();
                        cachedEntries = entries;
                        buildLibrary(entries, state);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (request != libraryRequest || isDestroyed()) return;
                    if (recordings == null) {
                        pager.pages[LIBRARY].removeAllViews();
                        pager.pages[LIBRARY].addView(text(Audomo.cause(e), 16, DIM));
                    } else if (activeTab == LIBRARY) toast(Audomo.cause(e));
                });
            }
        });
    }

    void export(SessionStore.Entry entry) {
        export = entry.file();
        startActivityForResult(
                new Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .setType(LibraryFiles.mime(entry.name()))
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(Intent.EXTRA_TITLE, entry.name()),
                3);
    }

    @Override
    protected void contentResult(int req, int result, Intent data) {
        if (req == AppSettings.REQUEST_PROFILES) {
            preferences.show();
            return;
        }
        if (result == RESULT_OK && data != null && preferences.result(req, data)) return;
        if (req == 8 && result == RESULT_OK && data != null) {
            List<android.net.Uri> uris = new ArrayList<>();
            if (data.getClipData() != null)
                for (int i = 0; i < data.getClipData().getItemCount(); i++)
                    uris.add(data.getClipData().getItemAt(i).getUri());
            else if (data.getData() != null) uris.add(data.getData());
            String target = importFolder == null ? "" : importFolder;
            runFiles(() -> {
                LibraryFiles lib = new LibraryFiles(app.sessions);
                List<String> problems = new ArrayList<>();
                int count = 0;
                for (android.net.Uri uri : uris)
                    try {
                        lib.importUri(uri, target);
                        count++;
                    } catch (Exception e) {
                        problems.add(Audomo.cause(e));
                    }
                return count
                        + " importiert"
                        + (problems.isEmpty() ? "" : "\n" + String.join("\n", problems));
            });
            return;
        }
        if (req != 3
                || result != RESULT_OK
                || data == null
                || data.getData() == null
                || export == null) return;
        File source = export;
        android.net.Uri target = data.getData();
        app.libraryFiles.execute(() -> {
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

    void pickImport() {
        importFolder = prefs().getString("folder", "");
        startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("audio/*")
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true),
                8);
    }

    void runFiles(Callable<String> work) {
        app.libraryFiles.execute(() -> {
            String message;
            try {
                message = work.call();
            } catch (Exception e) {
                message = Audomo.cause(e);
            }
            String result = message;
            runOnUiThread(() -> {
                if (isDestroyed()) return;
                if (activeTab == LIBRARY) renderLibrary(libraryState());
                if (!result.isEmpty()) toast(result);
            });
        });
    }

    void upload(List<SessionStore.Entry> entries) {
        ArrayList<String> ids = new ArrayList<>(entries.size());
        for (SessionStore.Entry e : entries) ids.add(e.id());
        ManualUploadDialog.chooseTarget(this, ids);
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

    ImageButton toolbarIcon(String label, int resource) {
        ImageButton button = icon(label, resource, INK, BG);
        android.util.TypedValue ripple = new android.util.TypedValue();
        if (getTheme()
                .resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true))
            button.setBackgroundResource(ripple.resourceId);
        return button;
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
    protected void resumeContent() {
        openingTab = false;
        if (prefs().contains("migrationCalendarSelection")) app.libraryFiles.execute(() -> BackupPreferences.resolveCalendars(this));
        app.draft.validate(this);
        if (activeTab == LIBRARY && pausedLibrary != null) {
            renderLibrary(pausedLibrary);
            pausedLibrary = null;
        }
        nextPreview = 0;
        resumed = true;
        handler.post(refresh);
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (hasContent() && activeTab == LIBRARY) pausedLibrary = libraryState();
        handler.removeCallbacks(refresh);
        super.onPause();
    }

    @Override
    protected void onStop() {
        stopPlayer();
        super.onStop();
    }

    @Override
    protected void saveContent(Bundle state) {
        if (editing) app.draft.name(inlineName.getText().toString());
        if (activeTab == RECORD) state.putInt("recordScroll", recorderScroll.getScrollY());
        if (isQueuePage()) dashboard.save(state);
        state.putBoolean("trashView", trashView);
        state.putParcelableArrayList("history", new ArrayList<>(history));
        if (activeTab == LIBRARY) state.putBundle("libraryState", pausedLibrary != null ? pausedLibrary : libraryState());
        if (export != null) state.putString("export", export.getAbsolutePath());
        state.putString("importFolder", importFolder);
        state.putInt("tab", activeTab);
        state.putBoolean("pending", pendingStart);
    }

    @Override
    protected void onDestroy() {
        prefs().unregisterOnSharedPreferenceChangeListener(optionsChanged);
        handler.removeCallbacksAndMessages(null);
        stopPlayer();
        super.onDestroy();
    }
}
