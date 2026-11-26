package de.audomo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

/** User-initiated foreground entry for the microphone service, outside the dashboard task. */
public final class TileStartActivity extends Activity {
    static final String TOGGLE = "de.audomo.action.TOGGLE_RECORDING";
    private Audomo app;
    private boolean startSent;
    private long previousStateSince;
    private String previousToken;

    private final Runnable observe = new Runnable() {
        @Override public void run() {
            if (isFinishing()) return;
            if (startSent) {
                boolean processed = !app.token.equals(previousToken) || app.stateSince != previousStateSince;
                if (processed && app.state == Audomo.State.FAILED) {
                    toast(app.note);
                    finishAndRemoveTask();
                    return;
                }
                if (app.active() || processed && (app.state == Audomo.State.SAVED || app.state == Audomo.State.FINALIZING)) {
                    finishAndRemoveTask();
                    return;
                }
            } else if (hasWindowFocus() && app.state != Audomo.State.RECOVERING) {
                if (!app.ready()) {
                    if (app.active() && TOGGLE.equals(getIntent().getAction())) app.command("STOP", null);
                    finishAndRemoveTask();
                    return;
                }
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    toast("Mikrofon zuerst in Audomo unter Berechtigungen freigeben");
                    app.vibrate("error");
                    finishAndRemoveTask();
                    return;
                }
                startSent = true;
                previousStateSince = app.stateSince;
                previousToken = app.token;
                app.command("START", null);
            }
            app.main.postDelayed(this, 60);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        app = (Audomo) getApplication();
        if (state != null) {
            // Restoring Android's task is not another tap on the launcher shortcut.
            finishAndRemoveTask();
            return;
        }
        setContentView(new View(this));
    }

    @Override protected void onResume() {
        super.onResume();
        app.main.post(observe);
    }

    @Override protected void onPause() {
        app.main.removeCallbacks(observe);
        super.onPause();
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
