package de.audomo;

import android.media.*;
import android.media.audiofx.*;

import java.io.IOException;

final class AudioInput implements AutoCloseable {
    final AudioRecord recorder;
    private AcousticEchoCanceler echo;
    private NoiseSuppressor noise;
    private boolean directed;
    private long segmentFrames;
    private long initialLag = Long.MIN_VALUE;
    private final AudioTimestamp timestamp = new AudioTimestamp();
    private final Profile profile;

    AudioInput(android.content.Context context, Profile profile) throws IOException {
        this.profile = profile;
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            throw new IOException("Mikrofonberechtigung fehlt");
        }
        int buffer =
                AudioRecord.getMinBufferSize(
                        profile.rate(),
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
        if (buffer <= 0)
            throw new IOException("Mono-Eingang bei " + profile.rate() + " Hz nicht verfügbar");
        recorder =
                new AudioRecord.Builder()
                        .setAudioSource(MediaRecorder.AudioSource.MIC)
                        .setAudioFormat(
                                new AudioFormat.Builder()
                                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                        .setSampleRate(profile.rate())
                                        .build())
                        .setBufferSizeInBytes(Math.max(19200, buffer * 2))
                        .build();
        try {
            if (!profile.mic().equals(Profile.SYSTEM)) {
                AudioManager manager = context.getSystemService(AudioManager.class);
                for (AudioDeviceInfo dev : manager.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
                    if (dev.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) {
                        recorder.setPreferredDevice(dev);
                        break;
                    }
                }
                directed =
                        recorder.setPreferredMicrophoneDirection(
                                profile.mic().equals("Hinten")
                                        ? MicrophoneDirection.MIC_DIRECTION_AWAY_FROM_USER
                                        : MicrophoneDirection.MIC_DIRECTION_TOWARDS_USER);
            }
            if (!profile.echo().equals(Profile.SYSTEM) && AcousticEchoCanceler.isAvailable())
                echo = AcousticEchoCanceler.create(recorder.getAudioSessionId());
            if (!profile.noise().equals(Profile.SYSTEM) && NoiseSuppressor.isAvailable())
                noise = NoiseSuppressor.create(recorder.getAudioSessionId());
            if (echo != null) echo.setEnabled(profile.echo().equals("Ein"));
            if (noise != null) noise.setEnabled(profile.noise().equals("Ein"));
        } catch (RuntimeException ignored) {
            /* Report actual effect state and continue with the available input. */
        }
    }

    void start() throws IOException {
        initialLag = Long.MIN_VALUE;
        segmentFrames = 0;
        recorder.startRecording();
        if (recorder.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING)
            throw new IOException("Mikrofon startet nicht");
    }

    String route() {
        AudioDeviceInfo dev = recorder.getRoutedDevice();
        String type =
                dev == null
                        ? "Unbekannt"
                        : dev.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC
                                ? "Internes Mikrofon"
                                : dev.getProductName().toString();
        if (profile.mic().equals(Profile.SYSTEM)) return type;
        return type + " · " + profile.mic() + (directed ? " angefragt" : " unbestätigt");
    }

    org.json.JSONArray activeMicrophones() {
        org.json.JSONArray active = new org.json.JSONArray();
        try {
            for (MicrophoneInfo m : recorder.getActiveMicrophones()) {
                var direction = m.getOrientation();
                active.put(
                        new org.json.JSONObject()
                                .put("type", m.getType())
                                .put("location", m.getLocation())
                                .put("group", m.getGroup())
                                .put("index", m.getIndexInTheGroup())
                                .put("directionX", (double) direction.x)
                                .put("directionY", (double) direction.y)
                                .put("directionZ", (double) direction.z));
            }
        } catch (Exception unavailable) {
            /* Some HALs do not expose individual physical capsules. */
        }
        return active;
    }

    String effects() {
        return "Echo "
                + effect(echo, profile.echo())
                + " · Rauschen "
                + effect(noise, profile.noise());
    }

    private static String effect(AudioEffect effect, String requested) {
        if (effect == null)
            return requested.equals(Profile.SYSTEM) ? Profile.SYSTEM : "nicht steuerbar";
        return (effect.getEnabled() ? "ein" : "aus")
                + (effect.hasControl() ? "" : " (Systemkontrolle)");
    }

    void accounted(int frames) {
        segmentFrames += frames;
    }

    void checkSignal() throws IOException {
        if (recorder.getTimestamp(timestamp, AudioTimestamp.TIMEBASE_MONOTONIC)
                        == AudioRecord.SUCCESS
                && timestamp.framePosition > 0) {
            long lag = timestamp.framePosition - segmentFrames;
            if (initialLag == Long.MIN_VALUE) initialLag = lag;
            long tolerance = recorder.getBufferSizeInFrames() + profile.rate() / 4;
            if (lag - initialLag > tolerance)
                throw new IOException("Lücke im Mikrofon-Datenstrom erkannt");
        }
        AudioRecordingConfiguration config = recorder.getActiveRecordingConfiguration();
        if (config != null && config.isClientSilenced())
            throw new IOException("Android hat das Mikrofon stummgeschaltet");
    }

    @Override
    public void close() {
        if (noise != null) noise.release();
        if (echo != null) echo.release();
        recorder.release();
    }
}
