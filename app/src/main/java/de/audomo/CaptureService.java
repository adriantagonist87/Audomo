package de.audomo;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.*;

public final class CaptureService extends Service {
    private static final String CHANNEL = "aufnahme";
    private static final int ID = 21;
    private Audomo app;
    private NotificationManager notifications;
    private BlockingQueue<String> commands;
    private PowerManager.WakeLock wake;
    private boolean running;
    private boolean sounded;
    private boolean warned;
    private final Runnable storageCheck =
            new Runnable() {
                public void run() {
                    if (!running) return;
                    if (warned != app.spaceLow) {
                        warned = app.spaceLow;
                        notifications.notify(ID, notification());
                    }
                    app.main.postDelayed(this, 2000);
                }
            };

    @Override
    public void onCreate() {
        super.onCreate();
        app = (Audomo) getApplication();
        notifications = getSystemService(NotificationManager.class);
        NotificationChannel channel =
                new NotificationChannel(
                        CHANNEL, "Aufnahmesteuerung", NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        notifications.createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        if (intent == null || !app.token.equals(intent.getStringExtra("token"))) {
            if (!running) stopSelf(id);
            return START_NOT_STICKY;
        }
        String action = intent.getAction();
        if ("START".equals(action) && !running) {
            synchronized (app) {
                if (app.ready()) begin(intent.getStringExtra("name"));
                else stopSelf(id);
            }
        }
        else if (running
                && app.active()
                && ("STOP".equals(action) || "PAUSE".equals(action) || "RESUME".equals(action))) {
            commands.offer(action);
        } else if (!running) stopSelf(id);
        return START_NOT_STICKY;
    }

    private void begin(String name) {
        app.draft.validate(this);
        RecordingDraft.Value draft = app.draft.get();
        running = true;
        sounded = false;
        warned = false;
        app.frames = 0;
        app.peak = 0;
        app.spaceLow = false;
        app.note = "";
        app.activeName = "";
        app.eventText = "";
        app.token = UUID.randomUUID().toString();
        app.change(Audomo.State.STARTING);
        commands = new LinkedBlockingQueue<>();
        try {
            startForeground(ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            wake =
                    getSystemService(PowerManager.class)
                            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Audomo:Aufnahme");
            wake.acquire();
        } catch (RuntimeException e) {
            complete(false, Audomo.cause(e));
            return;
        }
        app.quietRequested = true;
        app.systemNote = RecordingQuietMode.update(this, true);
        app.main.post(storageCheck);
        CaptureEngine engine =
                new CaptureEngine(
                        app,
                        commands,
                        next ->
                                app.main.post(() -> {
                                    app.change(next);
                                    if (next == Audomo.State.RECORDING && !sounded) {
                                        sounded = true;
                                        app.vibrate("start");
                                    }
                                    notifications.notify(ID, notification());
                                }));
        app.worker.execute(() -> {
            try {
                File file = engine.run(name, draft);
                String note = "Gespeichert · " + file.getName();
                try {
                    DocumentStorage.deliver(this, file);
                } catch (Exception e) {
                    note =
                            "Im App-Speicher gesichert · Zielordner nicht bestätigt · "
                                    + Audomo.cause(e);
                    app.diagnostic("Warnung · Kopie im Zielordner ausstehend");
                }
                try {
                    String upload = UploadQueue.afterCapture(app, file);
                    if (!upload.isEmpty()) note += "\n" + upload;
                } catch (Exception e) {
                    note += " · Uploadauftrag ausstehend";
                    app.diagnostic("Uploadauftrag konnte nicht geplant werden");
                }
                String message = note;
                app.main.post(() -> complete(true, message));
            } catch (Exception e) {
                String text = "Aufnahme unterbrochen · " + Audomo.cause(e);
                app.diagnostic("Aufnahmepfad unterbrochen (" + e.getClass().getSimpleName() + ")");
                try {
                    String recovered = app.sessions.recoverAll();
                    if (!recovered.isEmpty()) text += "\n" + recovered;
                } catch (Exception recovery) {
                    text += "\n" + Audomo.cause(recovery);
                }
                String message = text;
                app.main.post(() -> complete(false, message));
            }
        });
    }

    private void complete(boolean success, String message) {
        app.quietRequested = false;
        RecordingQuietMode.update(this, false);
        app.systemNote = "";
        running = false;
        app.main.removeCallbacks(storageCheck);
        if (wake != null && wake.isHeld()) wake.release();
        app.note = message;
        app.token = UUID.randomUUID().toString();
        app.change(success ? Audomo.State.SAVED : Audomo.State.FAILED);
        app.vibrate(success ? "stop" : "error");
        stopForeground(STOP_FOREGROUND_REMOVE);
        if (!success)
            notifications.notify(22, base("Aufnahme unterbrochen", "Details in Audomo").build());
        stopSelf();
    }

    private Notification.Builder base(String title, String body) {
        PendingIntent open =
                PendingIntent.getActivity(
                        this,
                        0,
                        new Intent(this, MainActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                        PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_audomo_notification)
                .setColor(getColor(R.color.audomo_purple))
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(open)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOnlyAlertOnce(true);
    }

    private Notification notification() {
        boolean paused = app.state == Audomo.State.PAUSED;
        String title =
                switch (app.state) {
                    case RECORDING -> "die App ist aktiv und verrichtet ihren Dienst";
                    case PAUSED -> "Aufnahme pausiert";
                    case FINALIZING -> "Aufnahme wird gesichert";
                    default -> "Aufnahme startet";
                };
        Notification.Builder builder =
                base(title, app.spaceLow ? "Wenig freier Speicherplatz" : "Audomo")
                        .setOngoing(true)
                        .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        if (app.active()) {
            if (paused) builder.setContentText(Audomo.duration(app.frames));
            else
                builder.setWhen(System.currentTimeMillis() - app.frames / 48)
                        .setUsesChronometer(true)
                        .setShowWhen(true);
            builder.addAction(
                    action(
                            paused ? "RESUME" : "PAUSE",
                            paused ? "Fortsetzen" : "Pause",
                            paused ? R.drawable.ic_play : R.drawable.ic_pause));
            builder.addAction(action("STOP", "Stoppen", R.drawable.ic_stop));
        }
        return builder.build();
    }

    private Notification.Action action(String cmd, String label, int icon) {
        Intent intent =
                new Intent(this, CaptureService.class).setAction(cmd).putExtra("token", app.token);
        return new Notification.Action.Builder(
                        android.graphics.drawable.Icon.createWithResource(this, icon),
                        label,
                        PendingIntent.getService(
                                this,
                                (app.token + cmd).hashCode(),
                                intent,
                                PendingIntent.FLAG_IMMUTABLE))
                .build();
    }

    @Override
    public void onDestroy() {
        if (running) commands.offer("STOP");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
