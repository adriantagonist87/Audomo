package de.audomo;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioRouting;
import android.media.AudioTrack;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.audio.AudioSink;
import androidx.media3.exoplayer.audio.DefaultAudioSink;

import java.util.List;
import java.util.function.Consumer;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
final class PlaybackOutput {
    static final String BLUETOOTH_ONLY = "bluetoothOnly";
    static final AudioAttributes ATTRIBUTES = AudioAttributes.DEFAULT;
    final ExoPlayer player;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable check = this::verify;
    private final AudioRouting.OnRoutingChangedListener routeChanged = route -> verify();
    private final Consumer<Boolean> interrupted;
    private AudioTrack track;
    private boolean confirmed, audible, closed;
    private long waitingSince;

    PlaybackOutput(Context context, Consumer<Boolean> interrupted) {
        this.context = context;
        this.interrupted = interrupted;
        // Follow the player's own track, including device-specific per-app routing.
        DefaultRenderersFactory renderers = new DefaultRenderersFactory(context) {
            @Override
            protected AudioSink buildAudioSink(Context ctx, boolean floats, boolean speed) {
                return new DefaultAudioSink.Builder(ctx)
                        .setEnableFloatOutput(floats)
                        .setEnableAudioOutputPlaybackParameters(speed)
                        .setAudioTrackProvider((config, attributes, sessionId, trackContext) -> {
                            AudioTrack result = DefaultAudioSink.AudioTrackProvider.DEFAULT
                                    .getAudioTrack(config, attributes, sessionId, trackContext);
                            main.post(() -> observe(result));
                            return result;
                        }).build();
            }
        };
        player = new ExoPlayer.Builder(context, renderers)
                .setAudioAttributes(ATTRIBUTES, true).setHandleAudioBecomingNoisy(true).build();
    }

    static List<AudioDeviceInfo> routes(Context ctx) {
        AudioManager manager = ctx.getSystemService(AudioManager.class);
        return manager.getAudioDevicesForAttributes(ATTRIBUTES.getPlatformAudioAttributes());
    }

    static boolean allows(int type, boolean bluetoothOnly) {
        return switch (type) {
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
                    AudioDeviceInfo.TYPE_HEARING_AID -> true;
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_USB_HEADSET -> !bluetoothOnly;
            default -> false;
        };
    }

    static boolean allowsAll(List<AudioDeviceInfo> routes, boolean bluetoothOnly) {
        return !routes.isEmpty() && routes.stream().allMatch(device -> allows(device.getType(), bluetoothOnly));
    }

    void start(List<AudioDeviceInfo> routes, boolean once) {
        confirmed = once;
        audible = once;
        waitingSince = 0;
        player.setVolume(once ? 1f : 0f);
        player.setPreferredAudioDevice(!once && routes.size() == 1 ? routes.get(0) : null);
        player.play();
        verify();
    }

    private void observe(AudioTrack next) {
        if (closed) return;
        if (track != null) track.removeOnRoutingChangedListener(routeChanged);
        track = next;
        track.addOnRoutingChangedListener(routeChanged, main);
        verify();
    }

    private List<AudioDeviceInfo> actualRoutes() {
        if (track == null) return List.of();
        if (Build.VERSION.SDK_INT >= 36) return track.getRoutedDevices();
        AudioDeviceInfo device = track.getRoutedDevice();
        return device == null ? List.of() : List.of(device);
    }

    private void verify() {
        main.removeCallbacks(check);
        if (closed || !player.getPlayWhenReady() || player.getPlaybackState() == Player.STATE_ENDED) return;
        boolean strict = Profile.prefs(context).getBoolean(BLUETOOTH_ONLY, false);
        boolean planned = allowsAll(routes(context), strict);
        var actual = actualRoutes();
        if (planned && allowsAll(actual, strict)) {
            confirmed = false;
            if (!audible) player.setVolume(1f);
            audible = true;
            waitingSince = 0;
        } else if (!confirmed || strict) {
            // AudioTrack reports a route only while running. Keep startup silent until it does.
            boolean wasAudible = audible;
            if (audible) player.setVolume(0f);
            audible = false;
            if (player.isPlaying() && waitingSince == 0) waitingSince = SystemClock.elapsedRealtime();
            if (!planned || !actual.isEmpty() || (waitingSince != 0 && SystemClock.elapsedRealtime() - waitingSince > 1500)) {
                player.pause();
                interrupted.accept(wasAudible);
                return;
            }
        }
        main.postDelayed(check, audible ? 250 : 30);
    }

    void release() {
        closed = true;
        main.removeCallbacks(check);
        if (track != null) track.removeOnRoutingChangedListener(routeChanged);
        player.release();
    }
}
