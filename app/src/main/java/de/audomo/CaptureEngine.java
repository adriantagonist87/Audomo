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

    File run(String requested, RecordingDraft.Value draft) throws Exception {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        Profile profile = Profile.selected(app);
        OffsetDateTime start = OffsetDateTime.now();
        String template = CalendarNames.template(app);
        CalendarNames.FolderMode mode = CalendarNames.folderMode(app);
        String base = CalendarNames.baseFolder(app, mode);
        CalendarNames.Event chosen = draft.event();
        String custom =
                chosen != null && (requested == null || requested.isBlank())
                        ? draft.name()
                        : requested;
        boolean automatic = custom == null || custom.isBlank();
        var match = chosen == null ? CalendarNames.at(app, start) : null;
        String initial =
                automatic
                        ? (chosen == null
                                ? CalendarNames.name(template, start, null)
                                : CalendarNames.chosenName(template, start, chosen))
                        : custom;
        File dir =
                app.sessions.create(
                        initial, profile, start, CalendarNames.folder(base, mode, chosen));
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
            app.draft.consume(draft);
            if (chosen != null)
                app.eventText = CalendarNames.describe(new CalendarNames.Match(chosen, ""));
            if (match != null)
                naming =
                        match.thenAcceptAsync(result -> {
                            try {
                                CalendarNames.Event event = result.event();
                                app.eventText = CalendarNames.describe(result);
                                if (event != null
                                        && (automatic
                                                || mode == CalendarNames.FolderMode.CALENDAR)) {
                                    String folder = CalendarNames.folder(base, mode, event);
                                    String proposed =
                                            automatic
                                                    ? CalendarNames.name(template, start, event)
                                                    : SessionStore.fileName(custom, start);
                                    synchronized (app.sessions) {
                                        new LibraryFiles(app.sessions).ensureFolder(folder);
                                        String name =
                                                app.sessions.availableName(proposed, folder, dir);
                                        meta.put("name", name).put("folder", folder);
                                        SessionStore.storeMeta(dir, meta);
                                        app.activeName = name;
                                    }
                                }
                            } catch (Exception error) {
                                app.eventText = "Kalenderzuordnung fehlgeschlagen";
                                app.diagnostic(
                                        "Name oder Ordner aus dem Kalender konnte nicht übernommen"
                                                + " werden");
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
        return app.sessions.publish(dir, part, app.frames, false);
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
