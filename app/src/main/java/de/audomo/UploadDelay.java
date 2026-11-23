package de.audomo;

import android.content.SharedPreferences;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class UploadDelay {
    static final String KEY = "autoUploadDelayMinutes";
    private static final BigDecimal MILLIS_PER_MINUTE = BigDecimal.valueOf(60_000);

    static BigDecimal minutes(String input) {
        String value = input.strip().replace(',', '.');
        if (!value.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)"))
            throw new IllegalArgumentException("Minuten als Zahl ab 0 eingeben.");
        BigDecimal minutes = new BigDecimal(value).stripTrailingZeros();
        try {
            millis(minutes);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Die angegebene Wartezeit ist zu lang.");
        }
        return minutes;
    }

    static long millis(BigDecimal minutes) {
        return minutes.multiply(MILLIS_PER_MINUTE)
                .setScale(0, RoundingMode.CEILING)
                .longValueExact();
    }

    static long notBefore(SharedPreferences prefs, long finishedAt) {
        long wait = millis(minutes(prefs.getString(KEY, "0")));
        return wait == 0 ? 0 : finishedAt + Math.min(wait, Long.MAX_VALUE - finishedAt);
    }

    static String display(SharedPreferences prefs) {
        return prefs.getString(KEY, "0").replace('.', ',');
    }
}
