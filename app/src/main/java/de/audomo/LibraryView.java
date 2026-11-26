package de.audomo;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.*;
import android.widget.*;

import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;

import java.time.format.DateTimeFormatter;
import java.util.*;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class LibraryView {
    private final MainActivity screen;
    private final Audomo app;
    private final LinearLayout panel;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<SessionStore.Entry> all, visible = new ArrayList<>();
    private final SharedPreferences prefs;
    private final BaseAdapter adapter;
    private final LibraryActions actions;
    private Map<String, List<UploadQueue.Job>> uploads = new HashMap<>();
    private long nextStatusCheck;
    private final Set<String> marked = new LinkedHashSet<>();
    private boolean selectionMode;
    private final Button folderButton;
    private final ImageButton sort;
    private final TextView count, empty;
    private final boolean trash;
    private final EditText field;
    private final CheckBox allScope;
    private final ListView list;
    private final ArrayList<String> folderTrail = new ArrayList<>();
    private boolean searchOnTop;
    private boolean allFolders;
    private SessionStore.Entry selected;
    private View playerRowActions;
    private ExoPlayer player;
    private PlaybackOutput output;
    private AlertDialog outputWarning;
    private String query = "";
    private ImageButton play, back, forward;
    private SeekBar seek;
    private TextView elapsed, leftStep, rightStep;
    private int step;

    private record SortField(
            String label,
            String ascending,
            String descending,
            Comparator<SessionStore.Entry> order) {}

    // The indices are also used by existing preferences and settings backups.
    private static final List<SortField> SORT =
            List.of(
                    new SortField(
                            "Name",
                            "A bis Z",
                            "Z bis A",
                            Comparator.comparing(
                                    SessionStore.Entry::name, String.CASE_INSENSITIVE_ORDER)),
                    new SortField(
                            "Datum",
                            "Älteste zuerst",
                            "Neueste zuerst",
                            Comparator.comparing(SessionStore.Entry::start)),
                    new SortField(
                            "Dauer",
                            "Kürzeste zuerst",
                            "Längste zuerst",
                            Comparator.comparingLong(SessionStore.Entry::frames)),
                    new SortField(
                            "Größe",
                            "Kleinste zuerst",
                            "Größte zuerst",
                            Comparator.comparingLong(SessionStore.Entry::bytes)),
                    new SortField(
                            "Endung",
                            "A bis Z",
                            "Z bis A",
                            Comparator.comparing(
                                    e -> LibraryFiles.extension(e.name()),
                                    String.CASE_INSENSITIVE_ORDER)));

    LibraryView(MainActivity screen, LinearLayout page, List<SessionStore.Entry> entries) {
        this.screen = screen;
        app = (Audomo) screen.getApplication();
        all = entries;
        prefs = Profile.prefs(screen);
        actions = new LibraryActions(screen, new LibraryFiles(app.sessions));
        trash = screen.trashView;
        step = prefs.getInt("skipSeconds", 5);
        LinearLayout heading = screen.row();
        page.addView(heading);
        count = screen.text("", 14, MainActivity.DIM);
        LinearLayout titles = screen.column();
        titles.addView(screen.text(trash ? "Papierkorb" : "Aufnahmen", 21, MainActivity.INK));
        titles.addView(count);
        heading.addView(titles, screen.weight());
        ImageButton search = screen.toolbarIcon("Aufnahmen durchsuchen", R.drawable.ic_search);
        sort = screen.toolbarIcon("Aufnahmen sortieren", R.drawable.ic_sort);
        heading.addView(search, screen.lp(48, 48));
        heading.addView(sort, screen.lp(48, 48));

        folderButton =
                screen.button(
                        "",
                        v ->
                                actions.folder(
                                        folder(),
                                        path -> {
                                            if (path.equals(folder())) return;
                                            folderTrail.add(folder());
                                            prefs.edit().putString("folder", path).apply();
                                            filter();
                                        }));
        folderButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        folderButton.setPadding(screen.dp(12), 0, screen.dp(12), 0);
        folderButton.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_expand, 0);
        folderButton.setContentDescription("Ordner wählen");
        if (!trash) page.addView(folderButton);
        allScope = new CheckBox(screen);
        allScope.setText("Alle Ordner");
        allScope.setVisibility(View.GONE);
        allScope.setOnCheckedChangeListener((v, on) -> {
            allFolders = on;
            filter();
        });
        field = new EditText(screen);
        field.setSingleLine();
        field.setHint("Aufnahmen suchen");
        field.setVisibility(View.GONE);
        page.addView(field);
        page.addView(allScope);
        search.setOnClickListener(v -> {
            if (field.getVisibility() == View.VISIBLE) {
                closeSearch();
            } else {
                searchOnTop = true;
                field.setVisibility(View.VISIBLE);
                if (!trash) allScope.setVisibility(View.VISIBLE);
                field.requestFocus();
                screen.getSystemService(android.view.inputmethod.InputMethodManager.class)
                        .showSoftInput(field, 0);
            }
        });
        sort.setOnClickListener(v -> showSortMenu());
        list = new ListView(screen);
        list.setDividerHeight(screen.dp(1));
        page.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        empty = screen.text("", 16, MainActivity.DIM);
        empty.setGravity(Gravity.CENTER);
        page.addView(empty, new LinearLayout.LayoutParams(-1, 0, 1));
        list.setEmptyView(empty);
        panel = screen.column();
        page.addView(panel);
        adapter =
                new BaseAdapter() {
                    public int getCount() {
                        return visible.size();
                    }

                    public Object getItem(int i) {
                        return visible.get(i);
                    }

                    public long getItemId(int i) {
                        return i;
                    }

                    public View getView(int i, View previous, ViewGroup parent) {
                        SessionStore.Entry e = visible.get(i);
                        LinearLayout cell = screen.column();
                        cell.setGravity(Gravity.CENTER_VERTICAL);
                        cell.setMinimumHeight(screen.dp(48));
                        if (selected == e || marked.contains(e.id()))
                            cell.setBackground(screen.surface(MainActivity.PANEL, 8));
                        LinearLayout top = screen.row();
                        TextView title = screen.text(e.name(), 15, MainActivity.INK);
                        title.setSingleLine();
                        title.setEllipsize(TextUtils.TruncateAt.END);
                        if (selectionMode) {
                            RadioButton circle = new RadioButton(screen);
                            circle.setChecked(marked.contains(e.id()));
                            circle.setClickable(false);
                            circle.setFocusable(false);
                            top.addView(circle);
                        }
                        top.addView(title, screen.weight());
                        top.addView(screen.text(e.frames() < 0 ? "" : Audomo.duration(e.frames()), 14, MainActivity.INK));
                        cell.addView(top);
                        LinearLayout info = screen.row();
                        cell.addView(info);
                        info.addView(
                                screen.text(
                                        e.start().equals(CloudLibrary.UNKNOWN_DATE) ? "Datum unbekannt" : e.start()
                                                .format(
                                                        DateTimeFormatter.ofPattern(
                                                                "dd.MM.yyyy HH:mm")),
                                        13,
                                        MainActivity.INK));
                        info.addView(
                                screen.text(
                                        e.bytes() < 0 ? "" : "  " + android.text.format.Formatter.formatShortFileSize(screen, e.bytes()),
                                        12,
                                        MainActivity.DIM),
                                screen.weight());
                        List<UploadQueue.Job> copies = uploads.getOrDefault(e.id(), List.of());
                        List<String> locations = new ArrayList<>();
                        if (!e.cloudOnly()) locations.add("Lokal");
                        String state = "Lokal";
                        for (UploadQueue.Job job : copies) {
                            if (job.knownCopy() && job.availability().isEmpty()) {
                                String provider = job.provider().isEmpty() ? "Cloud" : job.provider();
                                if (!locations.contains(provider)) locations.add(provider);
                                state = "Hochgeladen";
                            }
                        }
                        for (UploadQueue.Job job : copies) {
                            if (!job.availability().isEmpty()) {
                                locations.add((job.provider().isEmpty() ? "Cloud" : job.provider()) + " · " + job.availability());
                                state = "Fehler";
                            } else if (!List.of("Cloudkopie gelöscht", "Hochgeladen", "Ausgelassen").contains(job.state())
                                    || !job.knownCopy() && job.state().equals("Hochgeladen")) {
                                if (!locations.contains(job.state())) locations.add(job.state());
                                state = job.state();
                            }
                        }
                        String where = String.join(" · ", locations);
                        int color =
                                switch (state) {
                                    case "Hochgeladen" -> 0xff82dcc3;
                                    case "Wartet" -> 0xffffd38a;
                                    case "Fehler", "Konflikt" -> 0xffff9a9d;
                                    case "Upload läuft" -> MainActivity.ACCENT;
                                    default -> MainActivity.DIM;
                                };
                        int icon =
                                switch (state) {
                                    case "Hochgeladen" -> R.drawable.ic_check;
                                    case "Wartet" -> R.drawable.ic_wait;
                                    case "Fehler", "Konflikt" -> R.drawable.ic_problem;
                                    case "Upload läuft" -> R.drawable.ic_progress;
                                    default -> R.drawable.ic_folder;
                                };
                        TextView status = screen.text(where, 12, color);
                        android.graphics.drawable.Drawable symbol =
                                screen.getDrawable(icon).mutate();
                        symbol.setBounds(0, 0, screen.dp(14), screen.dp(14));
                        symbol.setTint(color);
                        status.setCompoundDrawables(symbol, null, null, null);
                        status.setCompoundDrawablePadding(screen.dp(3));
                        if (!copies.isEmpty()) status.setOnClickListener(v -> new CloudScreen(screen).locations(e, copies));
                        if (where.length() > 23) {
                            status.setGravity(Gravity.END);
                            cell.addView(status);
                        } else info.addView(status);
                        if (selected == e && !selectionMode) {
                            LinearLayout bar = screen.row();
                            cell.addView(bar);
                            playerRowActions = bar;
                            if (trash) {
                                action(
                                        bar,
                                        "Wiederherstellen",
                                        R.drawable.ic_back,
                                        v -> actions.restore(List.of(e)));
                                action(
                                        bar,
                                        "Endgültig löschen",
                                        R.drawable.ic_delete,
                                        v -> actions.trash(List.of(e), true));
                            } else {
                                action(
                                        bar,
                                        "Teilen",
                                        R.drawable.ic_share,
                                        v -> actions.share(List.of(e), true));
                                action(
                                        bar,
                                        "Umbenennen",
                                        R.drawable.ic_edit,
                                        v -> actions.rename(e));
                                action(bar, "Upload", R.drawable.ic_upload, v -> screen.upload(List.of(e)));
                                action(
                                        bar,
                                        "Mehr",
                                        R.drawable.ic_more,
                                        v -> actions.more(v, List.of(e)));
                            }
                        }
                        return cell;
                    }
                };
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            searchOnTop = false;
            SessionStore.Entry e = visible.get(pos);
            if (selectionMode) {
                mark(e);
                return;
            }
            if (e.cloudOnly()) {
                new CloudScreen(screen).locations(e, uploads.getOrDefault(e.id(), List.of()));
                return;
            }
            stop();
            selected = selected == e ? null : e;
            panel.removeAllViews();
            if (selected != null && !trash) showPlayer();
            adapter.notifyDataSetChanged();
        });
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            mark(visible.get(pos));
            return true;
        });
        field.addTextChangedListener(
                new TextWatcher() {
                    public void beforeTextChanged(CharSequence t, int s, int c, int a) {}

                    public void onTextChanged(CharSequence t, int s, int b, int c) {
                        query = t.toString().toLowerCase(Locale.ROOT);
                        filter();
                    }

                    public void afterTextChanged(Editable e) {}
                });
        refreshStatus();
        filter();
    }

    void emptyTrash() {
        List<SessionStore.Entry> entries = all.stream().filter(e -> e.trashed() > 0).collect(java.util.stream.Collectors.toList());
        if (entries.isEmpty()) screen.toast("Der Papierkorb ist leer");
        else actions.trash(entries, true);
    }

    boolean outsidePlayer(float x, float y) {
        return selected != null && !trash && !DashboardGestures.contains(panel, x, y)
                && !DashboardGestures.contains(playerRowActions, x, y);
    }

    void closePlayer() {
        stop();
        selected = null;
        playerRowActions = null;
        panel.removeAllViews();
        panel.setPadding(0, 0, 0, 0);
        adapter.notifyDataSetChanged();
    }

    void refreshStatus() {
        long now = SystemClock.elapsedRealtime();
        if (now < nextStatusCheck) return;
        nextStatusCheck = now + 2500;
        Map<String, List<UploadQueue.Job>> latest = new HashMap<>();
        for (UploadQueue.Job j : UploadQueue.get(screen).jobs()) latest.computeIfAbsent(j.rec(), key -> new ArrayList<>()).add(j);
        if (!uploads.equals(latest)) {
            uploads = latest;
            adapter.notifyDataSetChanged();
        }
    }

    private String folder() {
        return prefs.getString("folder", "");
    }

    private void closeSearch() {
        screen.getSystemService(android.view.inputmethod.InputMethodManager.class)
                .hideSoftInputFromWindow(field.getWindowToken(), 0);
        allScope.setChecked(false);
        allScope.setVisibility(View.GONE);
        field.setText("");
        field.setVisibility(View.GONE);
        searchOnTop = false;
    }

    boolean navigateBack() {
        if (searchOnTop && field.isShown()) {
            closeSearch();
        } else if (selectionMode) {
            endSelection();
        } else if (selected != null) {
            closePlayer();
        } else if (field.isShown()) {
            closeSearch();
        } else if (!folderTrail.isEmpty()) {
            prefs.edit().putString("folder", folderTrail.remove(folderTrail.size() - 1)).apply();
            filter();
        } else return false;
        return true;
    }

    Bundle saveState() {
        Bundle state = new Bundle();
        if (field.getVisibility() == View.VISIBLE) state.putString("search", field.getText().toString());
        if (!trash) state.putString("folder", folder());
        state.putBoolean("allFolders", allFolders);
        state.putBoolean("searchOnTop", searchOnTop);
        state.putStringArrayList("folders", new ArrayList<>(folderTrail));
        state.putStringArrayList("marked", new ArrayList<>(marked));
        state.putBoolean("selectionMode", selectionMode);
        if (selected != null) {
            state.putString("selected", selected.id());
            if (!trash) state.putInt("seek", seek.getProgress());
        }
        state.putInt("first", list.getFirstVisiblePosition());
        if (list.getChildCount() > 0) state.putInt("offset", list.getChildAt(0).getTop());
        return state;
    }

    void restoreState(Bundle state) {
        if (!trash && state.containsKey("folder") && actions.files.folders().contains(state.getString("folder")) && !folder().equals(state.getString("folder")))
            prefs.edit().putString("folder", state.getString("folder")).apply();
        folderTrail.addAll(state.getStringArrayList("folders"));
        folderTrail.retainAll(actions.files.folders());
        if (state.containsKey("search")) {
            field.setVisibility(View.VISIBLE);
            if (!trash) allScope.setVisibility(View.VISIBLE);
            allScope.setChecked(state.getBoolean("allFolders"));
            field.setText(state.getString("search"));
            searchOnTop = state.getBoolean("searchOnTop");
        }
        marked.addAll(state.getStringArrayList("marked"));
        selectionMode = state.getBoolean("selectionMode", !marked.isEmpty());
        filter();
        for (SessionStore.Entry e : visible) {
            if (!e.cloudOnly() && e.id().equals(state.getString("selected"))) {
                selected = e;
                if (!trash) {
                    showPlayer();
                    seek.setProgress(state.getInt("seek"));
                    time(durationMs() * seek.getProgress() / 100000);
                }
                adapter.notifyDataSetChanged();
                break;
            }
        }
        list.setSelectionFromTop(state.getInt("first"), state.getInt("offset"));
    }

    private void action(LinearLayout bar, String label, int icon, View.OnClickListener listener) {
        Button b = screen.button(label, listener);
        b.setTextSize(12);
        b.setPadding(screen.dp(2), screen.dp(3), screen.dp(2), screen.dp(2));
        android.graphics.drawable.Drawable image = screen.getDrawable(icon).mutate();
        image.setTint(MainActivity.ACCENT);
        image.setBounds(0, 0, screen.dp(19), screen.dp(19));
        b.setCompoundDrawables(null, image, null, null);
        b.setFocusable(false);
        bar.addView(b, screen.weight());
    }

    private void mark(SessionStore.Entry e) {
        searchOnTop = false;
        stop();
        selected = null;
        selectionMode = true;
        if (!marked.add(e.id())) marked.remove(e.id());
        selectionBar();
        adapter.notifyDataSetChanged();
    }

    private void endSelection() {
        selectionMode = false;
        marked.clear();
        panel.removeAllViews();
        panel.setPadding(0, 0, 0, 0);
        adapter.notifyDataSetChanged();
    }

    private void selectionBar() {
        panel.removeAllViews();
        panel.setPadding(0, 0, 0, 0);
        List<SessionStore.Entry> picked =
                visible.stream()
                        .filter(e -> marked.contains(e.id()))
                        .collect(java.util.stream.Collectors.toList());
        LinearLayout header = screen.row();
        panel.addView(header);
        header.addView(
                screen.text(picked.size() + " ausgewählt", 16, MainActivity.INK), screen.weight());
        boolean allSelected = !visible.isEmpty() && marked.size() == visible.size();
        String label = allSelected ? "Alle abwählen" : "Alle auswählen";
        ImageButton toggleAll = screen.toolbarIcon(
                label, allSelected ? R.drawable.ic_deselect_all : R.drawable.ic_select_all);
        toggleAll.setTooltipText(label);
        toggleAll.setEnabled(!visible.isEmpty());
        if (visible.isEmpty()) toggleAll.setAlpha(0.45f);
        toggleAll.setOnClickListener(v -> {
            searchOnTop = false;
            if (allSelected) {
                marked.clear();
            } else {
                for (SessionStore.Entry entry : visible) marked.add(entry.id());
            }
            selectionBar();
            adapter.notifyDataSetChanged();
        });
        header.addView(toggleAll, screen.lp(48, 48));
        header.addView(screen.button("Abbrechen", v -> endSelection()));
        SessionStore.Entry single = picked.size() == 1 ? picked.get(0) : null;
        if (picked.stream().anyMatch(SessionStore.Entry::cloudOnly)) {
            if (single != null) {
                List<UploadQueue.Job> copies = uploads.getOrDefault(single.id(), List.of());
                LinearLayout cloudActions = screen.row();
                panel.addView(cloudActions);
                if (copies.stream().anyMatch(UploadQueue.Job::canRename))
                    action(cloudActions, "Umbenennen", R.drawable.ic_edit, v -> actions.rename(single));
                cloudActions.addView(screen.button("Speicherorte", v -> new CloudScreen(screen)
                        .locations(single, copies)), screen.weight());
            } else {
                panel.addView(screen.text("Für gemeinsame Dateiaktionen zuerst die Cloudaufnahmen lokal wiederherstellen.", 14, MainActivity.DIM));
            }
            return;
        }
        if (!trash) {
            LinearLayout uploads = screen.row();
            panel.addView(uploads);
            action(uploads, "Cloud Upload", R.drawable.ic_upload, v -> screen.upload(picked));
            uploads.getChildAt(0).setEnabled(!picked.isEmpty());
            if (picked.isEmpty()) uploads.setAlpha(0.45f);
            if (single != null)
                action(uploads, "Umbenennen", R.drawable.ic_edit, v -> actions.rename(single));
        }
        LinearLayout bar = screen.row();
        panel.addView(bar);
        if (trash) {
            action(bar, "Wiederherstellen", R.drawable.ic_back, v -> actions.restore(picked));
            action(bar, "Löschen", R.drawable.ic_delete, v -> actions.trash(picked, true));
        } else {
            action(bar, "Teilen", R.drawable.ic_share, v -> actions.share(picked, true));
            action(bar, "Verschieben", R.drawable.ic_folder, v -> actions.relocate(picked, false));
            action(bar, "Löschen", R.drawable.ic_delete, v -> actions.trash(picked, false));
            action(bar, "Mehr", R.drawable.ic_more, v -> actions.more(v, picked));
        }
        if (picked.isEmpty()) {
            bar.setAlpha(0.45f);
            for (int i = 0; i < bar.getChildCount(); i++) bar.getChildAt(i).setEnabled(false);
        }
    }

    private SortField currentSort() {
        int key = prefs.getInt("sort", 1);
        return SORT.get(key >= 0 && key < SORT.size() ? key : 1);
    }

    private void showSortMenu() {
        PopupMenu popup = new PopupMenu(screen, sort, Gravity.END);
        Menu menu = popup.getMenu();
        menu.setGroupDividerEnabled(true);
        SortField current = currentSort();
        boolean descending = prefs.getBoolean("descending", true);
        for (int i = 0; i < SORT.size(); i++) {
            int key = i;
            SortField field = SORT.get(i);
            addSortOption(
                    menu,
                    0,
                    field.label(),
                    field == current,
                    () -> prefs.edit().putInt("sort", key).apply());
        }
        addSortOption(
                menu,
                1,
                current.ascending(),
                !descending,
                () -> prefs.edit().putBoolean("descending", false).apply());
        addSortOption(
                menu,
                1,
                current.descending(),
                descending,
                () -> prefs.edit().putBoolean("descending", true).apply());
        popup.show();
    }

    private void addSortOption(
            Menu menu, int group, String label, boolean active, Runnable select) {
        SpannableString title = new SpannableString(label);
        if (active) {
            title.setSpan(
                    new StyleSpan(Typeface.BOLD),
                    0,
                    title.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            title.setSpan(
                    new ForegroundColorSpan(MainActivity.ACCENT),
                    0,
                    title.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        MenuItem item = menu.add(group, Menu.NONE, Menu.NONE, title);
        if (active) item.setContentDescription(label + ", ausgewählt");
        item.setOnMenuItemClickListener(clicked -> {
            select.run();
            filter();
            return true;
        });
    }

    private void filter() {
        visible.clear();
        for (SessionStore.Entry entry : all)
            if ((entry.trashed() > 0) == trash
                    && (trash || allFolders || entry.folder().equals(folder()))
                    && entry.name().toLowerCase(Locale.ROOT).contains(query)) visible.add(entry);
        SortField field = currentSort();
        boolean descending = prefs.getBoolean("descending", true);
        sort.setStateDescription(
                field.label() + ", " + (descending ? field.descending() : field.ascending()));
        Comparator<SessionStore.Entry> by = descending ? field.order().reversed() : field.order();
        visible.sort(by.thenComparing(e -> e.name()));
        if (selected != null && !visible.contains(selected)) {
            stop();
            selected = null;
            panel.removeAllViews();
        }
        if (selectionMode) {
            marked.retainAll(
                    visible.stream()
                            .map(SessionStore.Entry::id)
                            .collect(java.util.stream.Collectors.toSet()));
            selectionBar();
        }
        empty.setText(
                !query.isEmpty()
                        ? "Keine passenden Aufnahmen"
                        : trash ? "Der Papierkorb ist leer" : "Keine Aufnahmen in diesem Ordner");
        count.setText(visible.size() + " Aufnahmen");
        folderButton.setText(folder().isEmpty() ? "Aufnahmen" : folder().replace("/", " › "));
        adapter.notifyDataSetChanged();
    }

    private void showPlayer() {
        panel.removeAllViews();
        panel.setBackground(screen.surface(MainActivity.PANEL, 14));
        int pad = screen.dp(10);
        panel.setPadding(pad, pad, pad, pad);
        LinearLayout title = screen.row();
        TextView full = screen.text(selected.name(), 16, MainActivity.INK);
        full.setMaxLines(2);
        full.setEllipsize(TextUtils.TruncateAt.END);
        title.addView(full, screen.weight());
        ImageButton more =
                screen.icon(
                        "Dateiaktionen", R.drawable.ic_more, MainActivity.INK, MainActivity.PANEL);
        more.setOnClickListener(v -> actions.more(more, List.of(selected)));
        title.addView(more, screen.lp(48, 48));
        panel.addView(title);
        elapsed = screen.text("", 14, MainActivity.DIM);
        panel.addView(elapsed);
        seek = new SeekBar(screen);
        seek.setMax(100000);
        panel.addView(seek);
        seek.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar s, int progress, boolean user) {
                        if (user) {
                            long position = durationMs() * progress / 100000;
                            if (player != null) player.seekTo(position);
                            time(position);
                        }
                    }

                    public void onStartTrackingTouch(SeekBar s) {}

                    public void onStopTrackingTouch(SeekBar s) {}
                });
        LinearLayout controls = screen.row();
        controls.setGravity(Gravity.CENTER);
        panel.addView(controls);
        back =
                screen.icon(
                        "Zurückspringen", R.drawable.ic_back, MainActivity.INK, MainActivity.PANEL);
        play = screen.icon("Abspielen", R.drawable.ic_play, 0xffffffff, 0xff9565e5);
        forward =
                screen.icon(
                        "Vorspringen", R.drawable.ic_forward, MainActivity.INK, MainActivity.PANEL);
        LinearLayout backGroup = screen.column(), forwardGroup = screen.column();
        controls.addView(backGroup);
        backGroup.addView(back, screen.lp(48, 48));
        controls.addView(play, screen.lp(48, 48));
        controls.addView(forwardGroup);
        forwardGroup.addView(forward, screen.lp(48, 48));
        leftStep = screen.text("", 12, MainActivity.DIM);
        rightStep = screen.text("", 12, MainActivity.DIM);
        leftStep.setGravity(Gravity.CENTER);
        rightStep.setGravity(Gravity.CENTER);
        backGroup.addView(leftStep);
        forwardGroup.addView(rightStep);
        back.setOnClickListener(v -> jump(-step));
        forward.setOnClickListener(v -> jump(step));
        play.setOnClickListener(v -> toggle());
        LinearLayout options = screen.row();
        panel.addView(options);
        Button speed = screen.button("Tempo " + prefs.getFloat("speed", 1f) + "×", null);
        speed.setOnClickListener(v -> PlaybackOptions.speed(screen, value -> {
            speed.setText("Tempo " + PlaybackOptions.speedLabel(prefs));
            if (player != null) player.setPlaybackSpeed(value);
        }));
        Switch silence = new Switch(screen);
        silence.setText("Stille");
        silence.setContentDescription("Stille bei Wiedergabe überspringen");
        silence.setChecked(prefs.getBoolean("skipSilence", false));
        silence.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean("skipSilence", on).apply();
            if (player != null) player.setSkipSilenceEnabled(on);
        });
        Button interval = screen.button("Sprung " + step + " s", null);
        interval.setOnClickListener(v -> PlaybackOptions.step(screen, value -> {
            step = value;
            interval.setText("Sprung " + step + " s");
            directions();
        }));
        options.addView(speed, screen.weight());
        options.addView(silence, screen.weight());
        options.addView(interval, screen.weight());
        directions();
        time(0);
    }

    private long durationMs() {
        return selected.frames() / 48;
    }

    private void directions() {
        leftStep.setText(step + " Sek.");
        rightStep.setText(step + " Sek.");
        back.setContentDescription(step + " Sekunden zurück");
        forward.setContentDescription(step + " Sekunden vor");
    }

    private void time(long ms) {
        elapsed.setText(Audomo.duration(ms * 48) + " / " + Audomo.duration(selected.frames()));
    }

    private void jump(int seconds) {
        long at =
                player == null
                        ? durationMs() * seek.getProgress() / 100000
                        : player.getCurrentPosition();
        long next = Math.max(0, Math.min(durationMs(), at + seconds * 1000L));
        if (player != null) player.seekTo(next);
        seek.setProgress((int) (next * 100000 / Math.max(1, durationMs())));
        time(next);
    }

    private void toggle() {
        if (!app.ready()) {
            screen.toast("Zuerst Aufnahme beenden");
            return;
        }
        if (player != null && player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED) {
            player.pause();
            return;
        }
        requestPlay(false);
    }

    private void requestPlay(boolean confirmed) {
        if (selected == null || !app.ready() || !play.isShown()) return;
        var routes = PlaybackOutput.routes(screen);
        boolean strict = prefs.getBoolean(PlaybackOutput.BLUETOOTH_ONLY, false);
        if (!(confirmed && !strict) && !PlaybackOutput.allowsAll(routes, strict)) {
            warnAboutOutput();
            return;
        }
        if (player == null) {
            output = new PlaybackOutput(screen, wasPlaying -> {
                if (wasPlaying) screen.toast("Wiedergabe pausiert · Audioausgang gewechselt");
                else warnAboutOutput();
            });
            player = output.player;
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(selected.file())));
            player.setSkipSilenceEnabled(prefs.getBoolean("skipSilence", false));
            player.setPlaybackSpeed(prefs.getFloat("speed", 1));
            player.addListener(
                    new Player.Listener() {
                        @Override
                        public void onIsPlayingChanged(boolean playing) {
                            play.setImageResource(
                                    playing ? R.drawable.ic_pause : R.drawable.ic_play);
                            play.setContentDescription(playing ? "Pause" : "Abspielen");
                            handler.removeCallbacks(update);
                            if (playing) handler.post(update);
                        }

                        @Override
                        public void onPlaybackStateChanged(int state) {
                            if (state == Player.STATE_ENDED) {
                                seek.setProgress(0);
                                time(0);
                            }
                        }

                        @Override
                        public void onPlayerError(PlaybackException error) {
                            screen.toast("Wiedergabe unterbrochen · " + error.getErrorCodeName());
                            stop();
                        }
                    });
            player.seekTo(durationMs() * seek.getProgress() / 100000);
            player.prepare();
        }
        if (player.getPlaybackState() == Player.STATE_ENDED) player.seekTo(0);
        output.start(routes, confirmed && !strict);
    }

    private void warnAboutOutput() {
        if (outputWarning != null || !play.isShown() || screen.isFinishing()) return;
        boolean strict = prefs.getBoolean(PlaybackOutput.BLUETOOTH_ONLY, false);
        AlertDialog.Builder dialog = new AlertDialog.Builder(screen);
        if (strict) {
            dialog.setTitle("Bluetooth-Ausgabe erforderlich")
                    .setMessage("Wähle in der Android-Medienausgabe dein Bluetooth-Audiogerät und drücke erneut auf Play.")
                    .setNegativeButton("Schließen", null);
        } else {
            dialog.setTitle("Ohne Kopfhörer abspielen?")
                    .setMessage("Die Aufnahme könnte über einen Lautsprecher hörbar sein. Möchtest du sie trotzdem abspielen?")
                    .setNegativeButton("Abbrechen", null)
                    .setPositiveButton("Trotzdem abspielen", (d, which) -> {
                        d.dismiss();
                        requestPlay(true);
                    });
        }
        outputWarning = dialog.create();
        outputWarning.setOnDismissListener(d -> { if (outputWarning == d) outputWarning = null; });
        outputWarning.show();
    }

    private final Runnable update =
            new Runnable() {
                public void run() {
                    if (player == null || !player.isPlaying()) return;
                    long at = player.getCurrentPosition();
                    if (!seek.isPressed()) {
                        seek.setProgress((int) (at * 100000 / Math.max(1, durationMs())));
                        time(at);
                    }
                    handler.postDelayed(this, 200);
                }
            };

    void stop() {
        handler.removeCallbacks(update);
        if (outputWarning != null) {
            outputWarning.dismiss();
            outputWarning = null;
        }
        if (output != null) {
            output.release();
            output = null;
            player = null;
        }
        if (play != null) {
            play.setContentDescription("Abspielen");
            play.setImageResource(R.drawable.ic_play);
        }
    }
}
