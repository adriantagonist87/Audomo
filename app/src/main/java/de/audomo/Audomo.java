package de.audomo;

import android.app.Application;
import android.content.*;
import android.os.*;

import java.io.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.*;

public final class Audomo extends Application {
    enum State {
        RECOVERING,
        READY,
        STARTING,
        RECORDING,
        PAUSED,
        FINALIZING,
        SAVED,
        FAILED
    }

    volatile State state = State.RECOVERING;
    volatile String token = UUID.randomUUID().toString();
    volatile String note = "", route = "", activeName = "", audioDetails = "", eventText = "";
    volatile long frames, stateSince;
    volatile float peak;
    volatile boolean spaceLow, quietRequested;
    volatile String systemNote = "";
    final ExecutorService worker = Executors.newSingleThreadExecutor();
    final Handler main = new Handler(Looper.getMainLooper());
    SessionStore sessions;

    @Override
    public void onCreate() {
        super.onCreate();
        sessions = new SessionStore(this);
        worker.execute(() -> {
            RecordingQuietMode.update(this, false);
            String notice;
            try {
                notice = sessions.recoverAll();
            } catch (Exception e) {
                notice = cause(e);
            }
            try {
                new LibraryFiles(sessions).cleanOld();
                UploadQueue.get(this).reconcile();
            } catch (Exception e) {
                diagnostic("Warnung · Dateiverwaltung beim Start prüfen");
            }
            String result = notice;
            if (!result.isEmpty()) diagnostic("Wiederherstellungsprüfung mit Hinweis");
            main.post(() -> {
                note = result;
                change(State.READY);
            });
            try {
                for (SessionStore.Entry e : sessions.list()) {
                    try {
                        if (e.trashed() == 0) UploadQueue.afterCapture(this, e.file());
                    } catch (Exception issue) {
                        diagnostic("Warnung · Uploadauftrag nach Neustart ausstehend");
                    }
                }
            } catch (Exception issue) {
                diagnostic("Warnung · Aufnahmeindex beim Neustart nicht lesbar");
            }
        });
    }

    boolean ready() {
        return state == State.READY || state == State.SAVED || state == State.FAILED;
    }

    boolean active() {
        return state == State.RECORDING || state == State.PAUSED;
    }

    // TileService registers only while SystemUI is listening. Both callbacks run on the main
    // looper.
    Runnable tileChanged;

    void change(State next) {
        stateSince = SystemClock.elapsedRealtime();
        state = next;
        if (tileChanged != null) tileChanged.run();
    }

    void diagnostic(String event) {
        try (FileWriter log = new FileWriter(new File(getFilesDir(), "diagnose.txt"), true)) {
            log.write(Instant.now() + "  " + event + "\n");
        } catch (IOException ignored) {
            /* Recording errors must still be shown if storage is full. */
        }
    }

    void vibrate(String event) {
        SharedPreferences prefs = getSharedPreferences("options", MODE_PRIVATE);
        if (!prefs.getBoolean("haptic", true)) return;
        int preset =
                prefs.getInt(
                        "pattern." + event,
                        event.equals("start") ? 0 : event.equals("stop") ? 1 : 2);
        long[][] patterns = {{0, 300, 200, 300}, {0, 500}, {0, 300, 200, 300, 200, 300}};
        getSystemService(VibratorManager.class)
                .getDefaultVibrator()
                .vibrate(
                        VibrationEffect.createWaveform(patterns[preset], -1),
                        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION));
    }

    static String cause(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    static String duration(long frames) {
        long seconds = frames / 48000;
        return String.format(
                Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    void command(String action, String name) {
        Intent request =
                new Intent(this, CaptureService.class)
                        .setAction(action)
                        .putExtra("token", token)
                        .putExtra("name", name);
        if (action.equals("START")) {
            try {
                startForegroundService(request);
            } catch (RuntimeException e) {
                note = cause(e);
                vibrate("error");
                change(State.FAILED);
            }
        } else startService(request);
    }
}
