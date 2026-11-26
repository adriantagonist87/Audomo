package de.audomo;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AppLockDeviceTest {
    @Test public void privateEntryRemainsClosedWhenDeviceCredentialIsMissing() {
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        Context ctx = inst.getTargetContext();
        Assume.assumeTrue(Build.MODEL.contains("sdk_gphone"));
        Assume.assumeFalse(ctx.getSystemService(KeyguardManager.class).isDeviceSecure());
        var settings = ctx.getSharedPreferences("app_lock", Context.MODE_PRIVATE);
        Assume.assumeFalse(settings.getBoolean("enabled", false));
        boolean wasConfigured = settings.contains("enabled");
        var monitor = inst.addMonitor(UnlockActivity.class.getName(), null, false);
        UnlockActivity gate = null;
        try {
            settings.edit().putBoolean("enabled", true).commit();
            ctx.startActivity(new Intent(ctx, MainActivity.class).setAction("de.audomo.UPLOADS")
                    .putExtra("uploadJob", 123L).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            gate = (UnlockActivity)inst.waitForMonitorWithTimeout(monitor, 6000);
            assertNotNull(gate);
            long deadline = SystemClock.elapsedRealtime() + 6000;
            while (SystemClock.elapsedRealtime() < deadline) {
                var window = inst.getUiAutomation().getRootInActiveWindow();
                if (window != null && !window.findAccessibilityNodeInfosByText("Gerätesicherheit öffnen").isEmpty()) {
                    assertTrue(window.findAccessibilityNodeInfosByText("Auftragsdetails").isEmpty());
                    assertTrue(window.findAccessibilityNodeInfosByText("Wiedergeben").isEmpty());
                    assertTrue(settings.getBoolean("enabled", false));
                    return;
                }
                SystemClock.sleep(100);
            }
            fail("Die App-Sperre muss bei fehlender Android-Displaysperre geschlossen bleiben.");
        } finally {
            inst.removeMonitor(monitor);
            if (gate != null) inst.runOnMainSync(gate::finishAndRemoveTask);
            if (wasConfigured) settings.edit().putBoolean("enabled", false).commit();
            else settings.edit().remove("enabled").commit();
        }
    }
}
