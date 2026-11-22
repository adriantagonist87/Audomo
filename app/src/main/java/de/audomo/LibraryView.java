package de.audomo;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
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
    private Map<String, UploadQueue.Job> uploads = new HashMap<>();
    private final Set<String> marked = new LinkedHashSet<>();
    private final Button folderButton;
    private final TextView count, empty;
    private final boolean trash;
    private boolean allFolders;
    private SessionStore.Entry selected;
    private ExoPlayer player;
    private String query = "";
    private ImageButton play, back, forward;
    private SeekBar seek;
    private TextView elapsed, leftStep, rightStep;
    private int step;
    private static final String[] SORT = {"Name", "Datum", "Dauer", "Größe", "Endung"};

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
        ImageButton menuButton =
                screen.icon(
                        "Bibliotheksaktionen",
                        R.drawable.ic_more,
                        MainActivity.INK,
                        MainActivity.BG);
        menuButton.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(screen, menuButton);
            if (!trash) LibraryActions.item(menu, "Aufnahmen importieren", screen::pickImport);
            LibraryActions.item(
                    menu,
                    trash ? "Zur Bibliothek" : "Papierkorb",
                    () -> {
                        screen.trashView = !trash;
                        screen.showLibrary();
                    });
            if (trash) {
                menu.getMenu()
                        .add("Nach 30 Tagen bereinigen")
                        .setCheckable(true)
                        .setChecked(prefs.getBoolean("cleanTrash", false))
                        .setOnMenuItemClickListener(item -> {
                            boolean on = !item.isChecked();
                            if (on)
                                new AlertDialog.Builder(screen)
                                        .setTitle("Dateien nach 30 Tagen endgültig löschen?")
                                        .setPositiveButton(
                                                "Aktivieren",
                                                (d, w) -> {
                                                    prefs.edit()
                                                            .putBoolean("cleanTrash", true)
                                                            .apply();
                                                    screen.runFiles(() -> {
                                                        actions.files.cleanOld();
                                                        return "Bereinigung aktiv";
                                                    });
                                                })
                                        .setNegativeButton("Abbrechen", null)
                                        .show();
                            else prefs.edit().putBoolean("cleanTrash", false).apply();
                            return true;
                        });
                if (!visible.isEmpty())
                    LibraryActions.item(
                            menu,
                            "Papierkorb leeren",
                            () -> actions.trash(List.copyOf(visible), true));
            }
            menu.show();
        });
        ImageButton search =
                screen.icon(
                        "Aufnahmen durchsuchen",
                        R.drawable.ic_search,
                        MainActivity.INK,
                        MainActivity.BG);
        ImageButton sort =
                screen.icon(
                        "Aufnahmen sortieren",
                        R.drawable.ic_sort,
                        MainActivity.INK,
                        MainActivity.BG);
        heading.addView(search, screen.lp(48, 48));
        heading.addView(sort, screen.lp(48, 48));
        heading.addView(menuButton, screen.lp(48, 48));
        folderButton =
                screen.button(
                        "",
                        v ->
                                actions.folder(
                                        folder(),
                                        path -> {
                                            prefs.edit().putString("folder", path).apply();
                                            filter();
                                        }));
        folderButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        folderButton.setPadding(screen.dp(12), 0, screen.dp(12), 0);
        folderButton.setContentDescription("Ordner wählen");
        if (!trash) page.addView(folderButton);
        CheckBox allScope = new CheckBox(screen);
        allScope.setText("Alle Ordner");
        allScope.setVisibility(View.GONE);
        allScope.setOnCheckedChangeListener((v, on) -> {
            allFolders = on;
            filter();
        });
        EditText field = new EditText(screen);
        field.setSingleLine();
        field.setHint("Aufnahmen suchen");
        field.setVisibility(View.GONE);
        page.addView(field);
        page.addView(allScope);
        search.setOnClickListener(v -> {
            if (field.getVisibility() == View.VISIBLE) {
                allScope.setChecked(false);
                allScope.setVisibility(View.GONE);
                field.setText("");
                field.setVisibility(View.GONE);
            } else {
                field.setVisibility(View.VISIBLE);
                if (!trash) allScope.setVisibility(View.VISIBLE);
                field.requestFocus();
                screen.getSystemService(android.view.inputmethod.InputMethodManager.class)
                        .showSoftInput(field, 0);
            }
        });
        sort.setOnClickListener(v -> {
            PopupMenu menu = new PopupMenu(screen, sort);
            for (int i = 0; i < SORT.length; i++) {
                int key = i;
                menu.getMenu()
                        .add(SORT[i])
                        .setCheckable(true)
                        .setChecked(i == prefs.getInt("sort", 1))
                        .setOnMenuItemClickListener(item -> {
                            prefs.edit().putInt("sort", key).apply();
                            filter();
                            return true;
                        });
            }
            menu.getMenu()
                    .add("Absteigend")
                    .setCheckable(true)
                    .setChecked(prefs.getBoolean("descending", true))
                    .setOnMenuItemClickListener(item -> {
                        prefs.edit().putBoolean("descending", !item.isChecked()).apply();
                        filter();
                        return true;
                    });
            menu.show();
        });
        ListView list = new ListView(screen);
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
                        if (!marked.isEmpty()) {
                            RadioButton circle = new RadioButton(screen);
                            circle.setChecked(marked.contains(e.id()));
                            circle.setClickable(false);
                            circle.setFocusable(false);
                            top.addView(circle);
                        }
                        top.addView(title, screen.weight());
                        top.addView(screen.text(Audomo.duration(e.frames()), 14, MainActivity.INK));
                        cell.addView(top);
                        LinearLayout info = screen.row();
                        cell.addView(info);
                        info.addView(
                                screen.text(
                                        e.start()
                                                .format(
                                                        DateTimeFormatter.ofPattern(
                                                                "dd.MM.yyyy HH:mm")),
                                        13,
                                        MainActivity.INK));
                        info.addView(
                                screen.text(
                                        "  "
                                                + android.text.format.Formatter.formatShortFileSize(
                                                        screen, e.file().length()),
                                        12,
                                        MainActivity.DIM),
                                screen.weight());
                        UploadQueue.Job job = uploads.get(e.id());
                        TextView status =
                                screen.text(
                                        job == null ? "Lokal" : job.state(),
                                        12,
                                        MainActivity.ACCENT);
                        int statusIcon =
                                job == null
                                        ? R.drawable.ic_folder
                                        : "Hochgeladen".equals(job.state())
                                                ? R.drawable.ic_check
                                                : R.drawable.ic_upload;
                        android.graphics.drawable.Drawable symbol =
                                screen.getDrawable(statusIcon).mutate();
                        symbol.setBounds(0, 0, screen.dp(14), screen.dp(14));
                        symbol.setTint(MainActivity.ACCENT);
                        status.setCompoundDrawables(symbol, null, null, null);
                        status.setCompoundDrawablePadding(screen.dp(3));
                        if (job != null)
                            status.setOnClickListener(v -> new CloudScreen(screen).details(job));
                        info.addView(status);
                        if (selected == e && marked.isEmpty()) {
                            LinearLayout bar = screen.row();
                            cell.addView(bar);
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
                                action(bar, "Upload", R.drawable.ic_upload, v -> screen.upload(e));
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
            SessionStore.Entry e = visible.get(pos);
            if (!marked.isEmpty()) {
                mark(e);
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
        refreshJobs();
        filter();
        page.addOnAttachStateChangeListener(
                new View.OnAttachStateChangeListener() {
                    public void onViewAttachedToWindow(View v) {
                        handler.post(refreshStatus);
                    }

                    public void onViewDetachedFromWindow(View v) {
                        handler.removeCallbacks(refreshStatus);
                    }
                });
        if (page.isAttachedToWindow()) handler.post(refreshStatus);
    }

    private void refreshJobs() {
        Map<String, UploadQueue.Job> latest = new HashMap<>();
        for (UploadQueue.Job j : UploadQueue.get(screen).jobs()) latest.putIfAbsent(j.rec(), j);
        if (!uploads.equals(latest)) {
            uploads = latest;
            adapter.notifyDataSetChanged();
        }
    }

    private final Runnable refreshStatus =
            new Runnable() {
                public void run() {
                    refreshJobs();
                    handler.postDelayed(this, 2500);
                }
            };

    private String folder() {
        return prefs.getString("folder", "");
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
        stop();
        selected = null;
        if (!marked.add(e.id())) marked.remove(e.id());
        selectionBar();
        adapter.notifyDataSetChanged();
    }

    private void selectionBar() {
        panel.removeAllViews();
        if (marked.isEmpty()) return;
        List<SessionStore.Entry> picked =
                visible.stream()
                        .filter(e -> marked.contains(e.id()))
                        .collect(java.util.stream.Collectors.toList());
        LinearLayout header = screen.row();
        panel.addView(header);
        header.addView(
                screen.text(picked.size() + " ausgewählt", 16, MainActivity.INK), screen.weight());
        header.addView(
                screen.button(
                        "Abbrechen",
                        v -> {
                            marked.clear();
                            panel.removeAllViews();
                            adapter.notifyDataSetChanged();
                        }));
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
    }

    private void filter() {
        visible.clear();
        for (SessionStore.Entry entry : all)
            if ((entry.trashed() > 0) == trash
                    && (trash || allFolders || entry.folder().equals(folder()))
                    && entry.name().toLowerCase(Locale.ROOT).contains(query)) visible.add(entry);
        Comparator<SessionStore.Entry> by =
                switch (prefs.getInt("sort", 1)) {
                    case 0 -> Comparator.comparing(e -> e.name(), String.CASE_INSENSITIVE_ORDER);
                    case 2 -> Comparator.comparingLong(SessionStore.Entry::frames);
                    case 3 -> Comparator.comparingLong(e -> e.file().length());
                    case 4 ->
                            Comparator.comparing(
                                    e -> LibraryFiles.extension(e.name()),
                                    String.CASE_INSENSITIVE_ORDER);
                    default -> Comparator.comparing(SessionStore.Entry::start);
                };
        if (prefs.getBoolean("descending", true)) by = by.reversed();
        visible.sort(by.thenComparing(e -> e.name()));
        if (selected != null && !visible.contains(selected)) {
            stop();
            selected = null;
            panel.removeAllViews();
        }
        if (!marked.isEmpty()) {
            marked.retainAll(
                    visible.stream()
                            .map(SessionStore.Entry::id)
                            .collect(java.util.stream.Collectors.toList()));
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
        speed.setOnClickListener(v -> {
            String[] text = {"0,75×", "1×", "1,25×", "1,5×", "2×"};
            float[] speeds = {.75f, 1, 1.25f, 1.5f, 2};
            new AlertDialog.Builder(screen)
                    .setTitle("Wiedergabetempo")
                    .setItems(
                            text,
                            (d, i) -> {
                                prefs.edit().putFloat("speed", speeds[i]).apply();
                                speed.setText("Tempo " + text[i]);
                                if (player != null) player.setPlaybackSpeed(speeds[i]);
                            })
                    .show();
        });
        Switch silence = new Switch(screen);
        silence.setText("Stille");
        silence.setContentDescription("Stille bei Wiedergabe überspringen");
        silence.setChecked(prefs.getBoolean("skipSilence", false));
        silence.setOnCheckedChangeListener((b, on) -> {
            prefs.edit().putBoolean("skipSilence", on).apply();
            if (player != null) player.setSkipSilenceEnabled(on);
        });
        Button interval = screen.button("Sprung " + step + " s", null);
        interval.setOnClickListener(v -> {
            int[] values = {5, 10, 15, 30};
            new AlertDialog.Builder(screen)
                    .setTitle("Sprungintervall")
                    .setItems(
                            new String[] {
                                "5 Sekunden", "10 Sekunden", "15 Sekunden", "30 Sekunden"
                            },
                            (d, i) -> {
                                step = values[i];
                                prefs.edit().putInt("skipSeconds", step).apply();
                                interval.setText("Sprung " + step + " s");
                                directions();
                            })
                    .show();
        });
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
        if (player != null && player.isPlaying()) {
            player.pause();
            return;
        }
        if (player == null) {
            player = new ExoPlayer.Builder(screen).build();
            player.setAudioAttributes(AudioAttributes.DEFAULT, true);
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
        player.play();
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
        if (player != null) {
            player.release();
            player = null;
        }
        if (play != null) {
            play.setContentDescription("Abspielen");
            play.setImageResource(R.drawable.ic_play);
        }
    }
}
