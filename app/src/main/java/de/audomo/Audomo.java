package de.audomo;

import android.app.Application;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import android.service.quicksettings.TileService;

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
    volatile long frames;
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
            String result = notice;
            if (!result.isEmpty()) diagnostic("Wiederherstellungsprüfung mit Hinweis");
            main.post(() -> {
                note = result;
                change(State.READY);
            });
        });
    }

    boolean ready() {
        return state == State.READY || state == State.SAVED || state == State.FAILED;
    }

    boolean active() {
        return state == State.RECORDING || state == State.PAUSED;
    }

    void change(State next) {
        state = next;
        TileService.requestListeningState(this, new ComponentName(this, CaptureTile.class));
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
        if (!prefs.getBoolean("haptic", true)
                || Settings.System.getInt(
                                getContentResolver(), Settings.System.HAPTIC_FEEDBACK_ENABLED, 1)
                        == 0) return;
        int preset =
                prefs.getInt(
                        "pattern." + event,
                        event.equals("start") ? 0 : event.equals("stop") ? 1 : 2);
        long[][] patterns = {{0, 45, 65, 45}, {0, 190}, {0, 45, 65, 45, 65, 45}};
        getSystemService(VibratorManager.class)
                .getDefaultVibrator()
                .vibrate(
                        VibrationEffect.createWaveform(patterns[preset], -1),
                        VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH));
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
