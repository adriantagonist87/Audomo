package de.audomo;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;

import androidx.core.content.FileProvider;
import androidx.media3.common.*;
import androidx.media3.exoplayer.ExoPlayer;

import java.io.File;
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
    private SessionStore.Entry selected;
    private ExoPlayer player;
    private String query = "";
    private ImageButton play, back, forward;
    private SeekBar seek;
    private TextView elapsed;
    private int step;
    private static final String[] SORT = {"Name", "Datum", "Dauer", "Größe", "Endung"};

    LibraryView(MainActivity screen, LinearLayout page, List<SessionStore.Entry> entries) {
        this.screen = screen;
        app = (Audomo) screen.getApplication();
        all = entries;
        prefs = Profile.prefs(screen);
        step = prefs.getInt("skipSeconds", 5);
        LinearLayout heading = screen.row();
        page.addView(heading);
        heading.addView(
                screen.text(entries.size() + " Aufnahmen", 14, MainActivity.DIM), screen.weight());
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
        EditText field = new EditText(screen);
        field.setSingleLine();
        field.setHint("Aufnahmen suchen");
        field.setVisibility(View.GONE);
        page.addView(field);
        search.setOnClickListener(v -> {
            if (field.getVisibility() == View.VISIBLE) {
                field.setText("");
                field.setVisibility(View.GONE);
            } else {
                field.setVisibility(View.VISIBLE);
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
                        cell.setMinimumHeight(screen.dp(53));
                        LinearLayout top = screen.row();
                        TextView title = screen.text(e.file().getName(), 15, MainActivity.INK);
                        title.setSingleLine();
                        title.setEllipsize(TextUtils.TruncateAt.END);
                        top.addView(title, screen.weight());
                        top.addView(screen.text(Audomo.duration(e.frames()), 14, MainActivity.INK));
                        cell.addView(top);
                        cell.addView(
                                screen.text(
                                        e.start()
                                                        .format(
                                                                DateTimeFormatter.ofPattern(
                                                                        "dd.MM.yyyy  HH:mm"))
                                                + "   ·   "
                                                + android.text.format.Formatter.formatShortFileSize(
                                                        screen, e.file().length())
                                                + "   ·   Lokal",
                                        14,
                                        MainActivity.DIM));
                        return cell;
                    }
                };
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> {
            stop();
            selected = visible.get(pos);
            showPlayer();
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
        filter();
    }

    private void filter() {
        visible.clear();
        for (SessionStore.Entry entry : all)
            if (entry.file().getName().toLowerCase(Locale.ROOT).contains(query)) visible.add(entry);
        Comparator<SessionStore.Entry> by =
                switch (prefs.getInt("sort", 1)) {
                    case 0 ->
                            Comparator.comparing(
                                    e -> e.file().getName(), String.CASE_INSENSITIVE_ORDER);
                    case 2 -> Comparator.comparingLong(SessionStore.Entry::frames);
                    case 3 -> Comparator.comparingLong(e -> e.file().length());
                    case 4 ->
                            Comparator.comparing(e ->
                                    e.file()
                                            .getName()
                                            .substring(e.file().getName().lastIndexOf('.') + 1));
                    default -> Comparator.comparing(SessionStore.Entry::start);
                };
        if (prefs.getBoolean("descending", true)) by = by.reversed();
        visible.sort(by.thenComparing(e -> e.file().getName()));
        if (selected != null && !visible.contains(selected)) {
            stop();
            selected = null;
            panel.removeAllViews();
        }
        adapter.notifyDataSetChanged();
    }

    private void showPlayer() {
        panel.removeAllViews();
        panel.setBackground(screen.surface(MainActivity.PANEL, 14));
        int pad = screen.dp(10);
        panel.setPadding(pad, pad, pad, pad);
        LinearLayout title = screen.row();
        TextView full = screen.text(selected.file().getName(), 16, MainActivity.INK);
        full.setMaxLines(2);
        full.setEllipsize(TextUtils.TruncateAt.END);
        title.addView(full, screen.weight());
        ImageButton more =
                screen.icon(
                        "Dateiaktionen", R.drawable.ic_more, MainActivity.INK, MainActivity.PANEL);
        more.setOnClickListener(v -> actions(more));
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
        controls.addView(back, screen.lp(48, 48));
        controls.addView(play, screen.lp(48, 48));
        controls.addView(forward, screen.lp(48, 48));
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

    private void actions(View anchor) {
        File file = selected.file();
        PopupMenu menu = new PopupMenu(screen, anchor);
        menu.getMenu()
                .add("Teilen")
                .setOnMenuItemClickListener(item -> {
                    open(file, true);
                    return true;
                });
        menu.getMenu()
                .add("Extern öffnen")
                .setOnMenuItemClickListener(item -> {
                    open(file, false);
                    return true;
                });
        menu.getMenu()
                .add("Datei exportieren")
                .setOnMenuItemClickListener(item -> {
                    screen.export(file);
                    return true;
                });
        menu.show();
    }

    private void open(File file, boolean share) {
        Uri uri = FileProvider.getUriForFile(screen, "de.audomo.audio", file);
        Intent intent =
                share
                        ? new Intent(Intent.ACTION_SEND)
                                .setType("audio/ogg")
                                .putExtra(Intent.EXTRA_STREAM, uri)
                        : new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "audio/ogg");
        intent.setClipData(ClipData.newRawUri("Aufnahme", uri));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            screen.startActivity(share ? Intent.createChooser(intent, "Aufnahme teilen") : intent);
        } catch (ActivityNotFoundException e) {
            screen.toast("Keine passende App installiert");
        }
    }
}
