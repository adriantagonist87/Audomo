package de.audomo;

import android.app.Activity;
import android.content.*;
import android.hardware.biometrics.BiometricManager;
import java.util.HashSet;
import java.util.Set;

final class AppLock {
    static final int METHODS = BiometricManager.Authenticators.BIOMETRIC_STRONG
            | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
    private final SharedPreferences settings;
    private final Set<Activity> visible = new HashSet<>();
    private boolean authenticated;
    private long screenGeneration;

    AppLock(Audomo app) {
        settings = app.getSharedPreferences("app_lock", Context.MODE_PRIVATE);
        app.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                authenticated = false;
                screenGeneration++;
                for (Activity activity : Set.copyOf(visible)) {
                    if (activity instanceof ProtectedActivity page) page.coverIfLocked();
                    if (activity instanceof UnlockActivity gate) gate.screenOff();
                }
            }
        }, new IntentFilter(Intent.ACTION_SCREEN_OFF), Context.RECEIVER_NOT_EXPORTED);
    }

    boolean enabled() { return settings.getBoolean("enabled", false); }
    boolean locked() { return enabled() && !authenticated; }
    void leave() { authenticated = false; }
    long screenGeneration() { return screenGeneration; }

    void started(Activity page) { visible.add(page); }
    void stopped(Activity page) {
        visible.remove(page);
        if (visible.isEmpty() && !page.isChangingConfigurations()) authenticated = false;
    }

    boolean confirm(String action) {
        if (UnlockActivity.ENABLE.equals(action) || UnlockActivity.DISABLE.equals(action)) {
            boolean enable = UnlockActivity.ENABLE.equals(action);
            if (!settings.edit().putBoolean("enabled", enable).commit()) return false;
            for (Activity page : visible) page.setRecentsScreenshotEnabled(!enable);
        }
        authenticated = true;
        return true;
    }
}
