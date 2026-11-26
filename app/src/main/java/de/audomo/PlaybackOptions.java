package de.audomo;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

final class PlaybackOptions {
    private static final float[] SPEEDS = {.75f, 1f, 1.25f, 1.5f, 2f};
    private static final String[] SPEED_LABELS = {"0,75×", "1×", "1,25×", "1,5×", "2×"};
    private static final int[] STEPS = {5, 10, 15, 30};

    static String speedLabel(SharedPreferences prefs) {
        float value = prefs.getFloat("speed", 1f);
        for (int i = 0; i < SPEEDS.length; i++) if (SPEEDS[i] == value) return SPEED_LABELS[i];
        return "1×";
    }

    static void speed(MainActivity screen, Consumer<Float> changed) {
        new AlertDialog.Builder(screen).setTitle("Wiedergabetempo").setItems(SPEED_LABELS, (d, i) -> {
            Profile.prefs(screen).edit().putFloat("speed", SPEEDS[i]).apply();
            changed.accept(SPEEDS[i]);
        }).show();
    }

    static void step(MainActivity screen, IntConsumer changed) {
        new AlertDialog.Builder(screen).setTitle("Sprungintervall")
                .setItems(new String[]{"5 Sekunden", "10 Sekunden", "15 Sekunden", "30 Sekunden"}, (d, i) -> {
                    Profile.prefs(screen).edit().putInt("skipSeconds", STEPS[i]).apply();
                    changed.accept(STEPS[i]);
                }).show();
    }
}
