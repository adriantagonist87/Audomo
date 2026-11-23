package de.audomo;

import static org.junit.Assert.*;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.dropbox.core.android.AuthActivity;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class DropboxLinkDeviceTest {
    private final Instrumentation inst = InstrumentationRegistry.getInstrumentation();

    @Test
    public void registrationAndReturnToAudomo() throws Exception {
        Context context = inst.getTargetContext();
        MainActivity screen = (MainActivity) inst.startActivitySync(
                new Intent(context, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Instrumentation.ActivityMonitor sdk = inst.addMonitor(AuthActivity.class.getName(), null, false);
        ArrayBlockingQueue<Uri> launches = new ArrayBlockingQueue<>(2);
        Instrumentation.ActivityMonitor browser = new Instrumentation.ActivityMonitor() {
            @Override
            public Instrumentation.ActivityResult onStartActivity(Intent intent) {
                if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null
                        && "https".equals(intent.getData().getScheme())) {
                    launches.add(intent.getData());
                    return new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null);
                }
                return null;
            }
        };
        inst.addMonitor(browser);
        try {
            inst.runOnMainSync(() -> {
                DropboxLinkDialog.open(screen, null, java.util.List.of(), null);
                screen.getFragmentManager().executePendingTransactions();
            });
            DropboxLinkDialog fragment = (DropboxLinkDialog)
                    screen.getFragmentManager().findFragmentByTag("dropboxLink");
            AlertDialog dialog = (AlertDialog) fragment.getDialog();
            if (BuildConfig.DROPBOX_APP_KEY.isEmpty()) {
                assertTrue(dialog.findViewById(android.R.id.message) instanceof android.widget.TextView);
                assertTrue(((android.widget.TextView) dialog.findViewById(android.R.id.message))
                        .getText().toString().contains("noch nicht eingerichtet"));
                assertFalse(dialog.getButton(AlertDialog.BUTTON_POSITIVE).isShown());
                assertTrue(launches.isEmpty());
                return;
            }
            Uri callback = Uri.parse("db-" + BuildConfig.DROPBOX_APP_KEY + "://1/connect");
            assertEquals(AuthActivity.class.getName(), new Intent(Intent.ACTION_VIEW, callback)
                    .addCategory(Intent.CATEGORY_BROWSABLE).setPackage(context.getPackageName())
                    .resolveActivity(context.getPackageManager()).getClassName());
            // Only the throwaway build writes a synthetic credential, never a user's configuration.
            if (!BuildConfig.DROPBOX_APP_KEY.equals("audomotestkey")) return;
            String targetId = fragment.getArguments().getString("newId");
            try {
                for (boolean approve : new boolean[] {false, true}) {
                    inst.runOnMainSync(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
                    Activity auth = sdk.waitForActivityWithTimeout(8000);
                    assertNotNull("SDK-Anmeldeaktivität", auth);
                    Uri request = launches.poll(8, TimeUnit.SECONDS);
                    assertNotNull("Browser-Anmeldung", request);
                    assertEquals("www.dropbox.com", request.getHost());
                    assertEquals(BuildConfig.DROPBOX_APP_KEY, request.getQueryParameter("k"));
                    String params = request.getQueryParameter("extra_query_params");
                    assertTrue(params.contains("code_challenge_method=S256"));
                    assertTrue(params.contains("token_access_type=offline"));
                    assertTrue(params.contains("files.content.write"));
                    inst.runOnMainSync(() -> {
                        // Substitute the SDK result at the provider boundary; no Dropbox account involved.
                        AuthActivity.result = approve ? new Intent()
                                .putExtra("ACCESS_TOKEN", "qa-access")
                                .putExtra("ACCESS_SECRET", "qa-access")
                                .putExtra("UID", "qa-user")
                                .putExtra("CONSUMER_KEY", BuildConfig.DROPBOX_APP_KEY)
                                .putExtra("REFRESH_TOKEN", "qa-refresh")
                                .putExtra("EXPIRES_AT", System.currentTimeMillis() + 60000) : null;
                        auth.finish();
                    });
                    inst.waitForIdleSync();
                    if (!approve) {
                        assertTrue(dialog.isShowing());
                        assertFalse(CloudConfig.all(context).stream().anyMatch(t -> t.id().equals(targetId)));
                    }
                }
                long deadline = SystemClock.elapsedRealtime() + 5000;
                while (CloudConfig.all(context).stream().noneMatch(t -> t.id().equals(targetId))
                        && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50);
                assertEquals(BuildConfig.DROPBOX_APP_KEY, CloudConfig.byId(context, targetId).host());
                assertEquals("qa-refresh", CloudConfig.secret(context, targetId));
            } finally {
                org.json.JSONArray remaining = new org.json.JSONArray();
                for (CloudConfig t : CloudConfig.all(context))
                    if (!t.id().equals(targetId)) remaining.put(t.json());
                var prefs = Profile.prefs(context);
                var edit = prefs.edit().putString("targets", remaining.toString());
                if (targetId.equals(prefs.getString("defaultTarget", ""))) edit.remove("defaultTarget");
                edit.commit();
                context.getSharedPreferences("credentials", 0).edit().remove(targetId).commit();
            }
        } finally {
            inst.removeMonitor(browser);
            inst.removeMonitor(sdk);
            inst.runOnMainSync(screen::finish);
        }
    }
}
