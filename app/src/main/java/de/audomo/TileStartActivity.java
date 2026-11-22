package de.audomo;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.*;

public final class TileStartActivity extends Activity {
    private Audomo app;
    private TextView message;
    private boolean sent, resumed;
    private final Runnable observe =
            new Runnable() {
                public void run() {
                    if (!resumed || isFinishing()) return;
                    if (!sent && app.ready() && hasWindowFocus()) {
                        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                                != PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(
                                            TileStartActivity.this,
                                            "Mikrofon zuerst in Audomo freigeben",
                                            Toast.LENGTH_LONG)
                                    .show();
                            app.vibrate("error");
                            finishAndRemoveTask();
                            return;
                        }
                        sent = true;
                        app.command("START", null);
                    } else if (app.active() || sent && app.state == Audomo.State.FAILED) {
                        if (app.state == Audomo.State.FAILED)
                            Toast.makeText(TileStartActivity.this, app.note, Toast.LENGTH_LONG)
                                    .show();
                        finishAndRemoveTask();
                        return;
                    }
                    app.main.postDelayed(this, 60);
                }
            };

    @Override
    public void onCreate(Bundle b) {
        super.onCreate(b);
        app = (Audomo) getApplication();
        message = new TextView(this);
        message.setText("Audomo · Mikrofon wird geöffnet");
        message.setGravity(Gravity.CENTER);
        setContentView(message);
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        app.main.post(observe);
    }

    @Override
    protected void onPause() {
        resumed = false;
        app.main.removeCallbacks(observe);
        super.onPause();
    }
}
