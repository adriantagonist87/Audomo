package de.audomo;

import android.media.AudioRecord;
import android.os.Process;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

final class CaptureEngine {
    interface Listener {
        void state(Audomo.State state);
    }

    private final Audomo app;
    private final BlockingQueue<String> commands;
    private final Listener listener;

    CaptureEngine(Audomo app, BlockingQueue<String> commands, Listener listener) {
        this.app = app;
        this.commands = commands;
        this.listener = listener;
    }

    File run(String requested) throws Exception {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        Profile profile = Profile.selected(app);
        OffsetDateTime start = OffsetDateTime.now();
        String template = CalendarNames.template(app);
        boolean automatic = requested == null || requested.isBlank();
        var match = CalendarNames.at(app, start);
        File dir =
                app.sessions.create(
                        automatic ? CalendarNames.name(template, start, null) : requested,
                        profile,
                        start);
        java.util.concurrent.CompletableFuture<Void> naming =
                java.util.concurrent.CompletableFuture.completedFuture(null);
        JSONObject meta = SessionStore.metadata(dir);
        app.activeName = meta.getString("name");
        File part = new File(dir, "capture.part");
        try (AudioInput input = new AudioInput(app, profile);
                OggOpus ogg = new OggOpus(part, UUID.randomUUID().hashCode());
                OpusEncoder encoder = new OpusEncoder(ogg, profile)) {
            SessionStore.syncDirectory(dir);
            input.start();
            app.route = input.route();
            meta.put("route", app.route)
                    .put("effects", input.effects())
                    .put("activeMicrophones", input.activeMicrophones());
            android.media.AudioRecordingConfiguration config =
                    input.recorder.getActiveRecordingConfiguration();
            if (config != null)
                meta.put("deviceRate", config.getFormat().getSampleRate())
                        .put("clientRate", config.getClientFormat().getSampleRate());
            app.audioDetails = input.effects();
            SessionStore.storeMeta(dir, meta);
            naming =
                    match.thenAcceptAsync(result -> {
                        try {
                            CalendarNames.Event event = result.event();
                            app.eventText = CalendarNames.describe(result);
                            if (automatic && event != null) {
                                String name =
                                        app.sessions.uniqueName(
                                                CalendarNames.name(template, start, event.title()),
                                                dir);
                                meta.put("name", name);
                                SessionStore.storeMeta(dir, meta);
                                app.activeName = name;
                            }
                        } catch (Exception error) {
                            app.eventText = "Kalendername nicht gesichert";
                            app.diagnostic("Kalendername konnte nicht übernommen werden");
                        }
                    });
            capture(input, encoder, ogg, dir, profile);
            listener.state(Audomo.State.FINALIZING);
            java.nio.file.Files.deleteIfExists(new File(dir, "reserve").toPath());
            encoder.finish();
        } finally {
            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT);
            naming.join();
        }
        return SessionStore.publish(dir, part, app.frames, false);
    }

    static int applyGain(short[] block, int n, double gain) {
        int peak = 0;
        for (int i = 0; i < n; i++) {
            int value = (int) Math.round(block[i] * gain);
            peak = Math.max(peak, Math.abs(value));
            block[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
        }
        return peak;
    }

    private void capture(
            AudioInput input, OpusEncoder encoder, OggOpus ogg, File dir, Profile profile)
            throws Exception {
        boolean paused = false, confirmed = false, clipping = false;
        long lastFrame = SystemClock.elapsedRealtime(), lastCheck = 0;
        long previousCheckpoint = 0;
        short[] block = new short[profile.rate() / 50];
        double gain = Math.pow(10, profile.gain() / 20.0);
        while (true) {
            String action = paused ? commands.take() : commands.poll();
            if ("STOP".equals(action)) {
                if (!paused) input.recorder.stop();
                break;
            }
            if ("PAUSE".equals(action) && !paused) {
                input.recorder.stop();
                paused = true;
                listener.state(Audomo.State.PAUSED);
                continue;
            }
            if ("RESUME".equals(action) && paused) {
                input.start();
                paused = false;
                lastFrame = SystemClock.elapsedRealtime();
                listener.state(Audomo.State.STARTING);
                confirmed = false;
                previousCheckpoint = ogg.durableFrames();
            }
            if (paused) continue;
            int n = input.recorder.read(block, 0, block.length, AudioRecord.READ_NON_BLOCKING);
            long now = SystemClock.elapsedRealtime();
            if (n < 0) throw new IOException("Mikrofon-Lesefehler (" + n + ")");
            if (now - lastFrame > 2000)
                throw new IOException("Mikrofon liefert keine kontinuierlichen Frames");
            if (now - lastCheck >= 500) {
                input.checkSignal();
                String route = input.route();
                String effects = input.effects();
                if (!route.equals(app.route)) app.diagnostic("Mikrofonroute gewechselt");
                if (!effects.equals(app.audioDetails))
                    app.diagnostic("Effektzustand geändert: " + effects);
                app.route = route;
                app.audioDetails = effects;
                app.spaceLow = dir.getUsableSpace() < 32L * 1024 * 1024;
                lastCheck = now;
            }
            if (n == 0) {
                Thread.sleep(5);
                continue;
            }
            lastFrame = now;
            int peak = applyGain(block, n, gain);
            if (!clipping && peak > Short.MAX_VALUE) {
                clipping = true;
                app.diagnostic("Übersteuerung bei " + profile.gain() + " dB erkannt");
            }
            input.accounted(n);
            encoder.write(block, n);
            app.frames += (long) n * (48000 / profile.rate());
            if (app.frames - ogg.durableFrames() > 4 * 48000)
                throw new IOException("Encoder liefert keine Schreibpunkte");
            app.peak = peak / 32768f;
            if (!confirmed && ogg.durableFrames() > previousCheckpoint) {
                confirmed = true;
                listener.state(Audomo.State.RECORDING);
            }
        }
    }
}
