package de.audomo;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.net.Uri;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Test;
import java.nio.file.Files;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@androidx.annotation.OptIn(markerClass = androidx.media3.common.util.UnstableApi.class)
public class BluetoothPlaybackDeviceTest {
    @Test public void classifyMediaOutputs() {
        assertTrue(PlaybackOutput.allows(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, true));
        assertTrue(PlaybackOutput.allows(AudioDeviceInfo.TYPE_BLE_HEADSET, true));
        assertTrue(PlaybackOutput.allows(AudioDeviceInfo.TYPE_HEARING_AID, true));
        assertTrue(PlaybackOutput.allows(AudioDeviceInfo.TYPE_USB_HEADSET, false));
        assertFalse(PlaybackOutput.allows(AudioDeviceInfo.TYPE_USB_HEADSET, true));
        assertTrue(PlaybackOutput.allows(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, false));
        assertFalse(PlaybackOutput.allows(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, false));
        assertFalse(PlaybackOutput.allows(AudioDeviceInfo.TYPE_USB_DEVICE, false));
        assertFalse(PlaybackOutput.allows(AudioDeviceInfo.TYPE_UNKNOWN, false));
        assertFalse(PlaybackOutput.allowsAll(java.util.List.of(), false));
    }

    @Test public void playTwoSecondsOnRealBluetoothOutput() throws Exception {
        Assume.assumeTrue("Hörprobe nur bei ausdrücklicher Freigabe",
                "true".equals(InstrumentationRegistry.getArguments().getString("bluetoothTone")));
        var inst = InstrumentationRegistry.getInstrumentation();
        Context ctx = inst.getTargetContext();
        assertTrue("Bluetooth als Medienausgang auswählen", PlaybackOutput.allowsAll(PlaybackOutput.routes(ctx), true));
        var prefs = Profile.prefs(ctx);
        var previous = prefs.getAll().get(PlaybackOutput.BLUETOOTH_ONLY);
        var file = Files.createTempFile(ctx.getCacheDir().toPath(), "kopfhoererprobe", ".mp3");
        AtomicReference<PlaybackOutput> output = new AtomicReference<>();
        AtomicBoolean audible = new AtomicBoolean();
        CountDownLatch finished = new CountDownLatch(1);
        MainActivity screen = (MainActivity) inst.startActivitySync(new Intent(ctx, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try {
            try (var in = inst.getContext().getAssets().open("testsignal.mp3")) {
                Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            inst.runOnMainSync(() -> {
                prefs.edit().putBoolean(PlaybackOutput.BLUETOOTH_ONLY, true).apply();
                var guarded = new PlaybackOutput(screen, wasPlaying -> finished.countDown());
                output.set(guarded);
                var player = guarded.player;
                player.addListener(new Player.Listener() {
                    @Override public void onVolumeChanged(float volume) {
                        if (volume > 0) audible.set(true);
                    }
                    @Override public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_ENDED) finished.countDown();
                    }
                });
                player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file.toFile())));
                player.prepare();
                guarded.start(PlaybackOutput.routes(ctx), false);
            });
            assertTrue("Testton nicht beendet", finished.await(10, TimeUnit.SECONDS));
            assertTrue("Bluetooth-Ausgabe wurde nicht freigegeben", audible.get());
            inst.runOnMainSync(() -> assertEquals(Player.STATE_ENDED, output.get().player.getPlaybackState()));
        } finally {
            inst.runOnMainSync(() -> {
                if (output.get() != null) output.get().release();
                var edit = prefs.edit();
                if (previous instanceof Boolean value) edit.putBoolean(PlaybackOutput.BLUETOOTH_ONLY, value);
                else edit.remove(PlaybackOutput.BLUETOOTH_ONLY);
                edit.commit();
                screen.finish();
            });
            Files.deleteIfExists(file);
        }
    }
}
