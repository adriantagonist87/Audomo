package de.audomo;

import android.app.Activity;
import android.content.Intent;
import android.hardware.biometrics.*;
import android.os.*;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;

public final class UnlockActivity extends Activity {
    static final String ENABLE = "de.audomo.lock.ENABLE", DISABLE = "de.audomo.lock.DISABLE";
    private AppLock lock;
    private CancellationSignal request;
    private TextView message;
    private Button unlock, setup;
    private boolean promptOnFocus;
    private boolean quickCapture;
    private String title;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        lock = ((Audomo)getApplication()).appLock;
        setRecentsScreenshotEnabled(false);
        String action = getIntent().getAction();
        title = ENABLE.equals(action) ? "App-Sperre aktivieren"
                : DISABLE.equals(action) ? "App-Sperre ausschalten" : "Audomo entsperren";
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER);
        int pad = Math.round(24 * getResources().getDisplayMetrics().density);
        page.setPadding(pad, pad, pad, pad);
        page.setBackgroundColor(MainActivity.BG);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.brand);
        page.addView(logo, pad * 3, pad * 3);
        TextView heading = new TextView(this);
        heading.setText(title);
        heading.setTextColor(MainActivity.INK);
        heading.setTextSize(22);
        heading.setPadding(0, pad, 0, 0);
        page.addView(heading);
        message = new TextView(this);
        message.setText("Mit Fingerabdruck oder deiner Android-Displaysperre bestätigen.");
        message.setGravity(Gravity.CENTER);
        message.setPadding(0, pad / 2, 0, pad);
        page.addView(message);
        unlock = button(page, "Jetzt bestätigen", this::authenticate);
        setup = button(page, "Gerätesicherheit öffnen", () ->
                startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS)));
        setup.setVisibility(android.view.View.GONE);
        if (action == null) button(page, "Aufnahme starten / stoppen", () -> {
            quickCapture = true;
            startActivity(new Intent(this, TileStartActivity.class).setAction(TileStartActivity.TOGGLE)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                            | Intent.FLAG_ACTIVITY_NO_USER_ACTION | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        });
        button(page, "Abbrechen", this::cancel);
        setContentView(page);
        promptOnFocus = true;
    }

    private Button button(LinearLayout page, String text, Runnable click) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setOnClickListener(v -> click.run());
        page.addView(button);
        return button;
    }

    @Override public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus && promptOnFocus && !isFinishing()) {
            promptOnFocus = false;
            authenticate();
        }
    }

    private void authenticate() {
        if (request != null || isFinishing()) return;
        int status = getSystemService(BiometricManager.class).canAuthenticate(AppLock.METHODS);
        if (status != BiometricManager.BIOMETRIC_SUCCESS) {
            message.setText(status == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED
                    ? "Richte in Android zuerst eine sichere Displaysperre ein. Ein Fingerabdruck ist optional."
                    : "Android kann deine Identität gerade nicht bestätigen. Prüfe die Gerätesicherheit oder versuche es erneut.");
            setup.setVisibility(android.view.View.VISIBLE);
            return;
        }
        setup.setVisibility(android.view.View.GONE);
        CancellationSignal signal = new CancellationSignal();
        long generation = lock.screenGeneration();
        request = signal;
        unlock.setEnabled(false);
        new BiometricPrompt.Builder(this).setTitle(title).setAllowedAuthenticators(AppLock.METHODS)
                .build().authenticate(signal, getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                    @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        if (signal != request || isFinishing() || isDestroyed()) return;
                        request = null;
                        if (generation == lock.screenGeneration() && getSystemService(PowerManager.class).isInteractive()
                                && lock.confirm(getIntent().getAction())) {
                            setResult(RESULT_OK);
                            finish();
                        } else {
                            unlock.setEnabled(true);
                            message.setText("Bestätigung erneut versuchen.");
                        }
                    }
                    @Override public void onAuthenticationError(int code, CharSequence reason) {
                        if (signal != request || isFinishing() || isDestroyed()) return;
                        request = null;
                        unlock.setEnabled(true);
                        message.setText(reason);
                    }
                });
    }

    void screenOff() {
        if (request != null) request.cancel();
        request = null;
        unlock.setEnabled(true);
    }

    private void cancel() {
        if (getIntent().getAction() != null) finish();
        else moveTaskToBack(true);
    }

    @SuppressWarnings("deprecation")
    @Override public void onBackPressed() { cancel(); }

    @Override protected void onRestart() {
        super.onRestart();
        promptOnFocus = request == null && !quickCapture;
        quickCapture = false;
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        lock.leave();
    }

    @Override protected void onStart() { super.onStart(); lock.started(this); }
    @Override protected void onStop() { lock.stopped(this); super.onStop(); }
    @Override protected void onDestroy() { if (request != null) request.cancel(); super.onDestroy(); }
}
