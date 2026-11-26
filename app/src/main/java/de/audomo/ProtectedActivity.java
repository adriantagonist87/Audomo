package de.audomo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.view.WindowManager;
import android.view.inspector.WindowInspector;
import java.util.ArrayList;

/** Hosts private screens. The recording shortcut and its service do not use this lifecycle. */
public abstract class ProtectedActivity extends Activity {
    private static final int UNLOCK = 7901;
    private static final String DEFERRED = "de.audomo.lock.deferred", RESULTS = "de.audomo.lock.results";
    private AppLock lock;
    private boolean deferred, gateRequested, restoring;
    private Bundle original;
    private final ArrayList<Bundle> results = new ArrayList<>();
    private final ArrayList<View> coveredDialogs = new ArrayList<>();

    @Override public final void onCreate(Bundle state) {
        lock = ((Audomo)getApplication()).appLock;
        setRecentsScreenshotEnabled(!lock.enabled());
        original = state != null && state.containsKey(DEFERRED) ? state.getBundle(DEFERRED) : state;
        if (state != null) {
            ArrayList<Bundle> pending = state.getParcelableArrayList(RESULTS, Bundle.class);
            if (pending != null) results.addAll(pending);
        }
        deferred = lock.locked();
        super.onCreate(deferred ? null : original);
        if (deferred) {
            setContentView(new View(this));
        } else {
            openContent(original);
            original = null;
        }
    }

    protected abstract void openContent(Bundle state);
    protected void resumeContent() {}
    protected void saveContent(Bundle state) {}
    protected void contentResult(int code, int result, Intent data) {}
    protected void permissionResult(int code, String[] permissions, int[] grants) {}

    protected final boolean hasContent() { return !deferred; }
    protected final boolean mayUseContent() { return !deferred && !lock.locked(); }

    @Override public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (intent.getComponent() != null && getPackageName().equals(intent.getComponent().getPackageName())) {
            intent = new Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NO_USER_ACTION);
        }
        super.startActivityForResult(intent, requestCode, options);
    }

    @Override protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (lock.enabled()) {
            lock.leave();
            coverWindows();
        }
    }

    @Override protected void onStart() {
        super.onStart();
        lock.started(this);
        setRecentsScreenshotEnabled(!lock.enabled());
        coverIfLocked();
    }

    void coverIfLocked() {
        if (!lock.locked()) return;
        coverWindows();
        if (hasWindowFocus() && !isFinishing()) requestUnlock();
    }

    private void coverWindows() {
        // Full-screen settings dialogs and popups can have windows of their own.
        View decor = getWindow().getDecorView();
        if (decor.getLayoutParams() instanceof WindowManager.LayoutParams host && host.token != null) {
            IBinder token = host.token;
            for (View view : WindowInspector.getGlobalWindowViews()) {
                if (view == decor || view.getVisibility() != View.VISIBLE) continue;
                if (view.getLayoutParams() instanceof WindowManager.LayoutParams child &&
                        (token.equals(child.token) || decor.getWindowToken() == child.token)) {
                    view.setVisibility(View.INVISIBLE);
                    coveredDialogs.add(view);
                }
            }
        }
        findViewById(android.R.id.content).setVisibility(View.INVISIBLE);
    }

    private void requestUnlock() {
        if (gateRequested || isFinishing() || restoring) return;
        gateRequested = true;
        startActivityForResult(new Intent(this, UnlockActivity.class), UNLOCK);
    }

    @Override protected final void onResume() {
        super.onResume();
        if (lock.locked()) {
            coverWindows();
            requestUnlock();
        } else if (deferred) {
            if (!restoring) {
                restoring = true;
                recreate();
            }
        } else {
            findViewById(android.R.id.content).setVisibility(View.VISIBLE);
            for (View view : coveredDialogs)
                if (view.isAttachedToWindow()) view.setVisibility(View.VISIBLE);
            coveredDialogs.clear();
            for (Bundle result : results) {
                if (result.containsKey("permissions")) permissionResult(result.getInt("code"),
                        result.getStringArray("permissions"), result.getIntArray("grants"));
                else contentResult(result.getInt("code"), result.getInt("result"), result.getParcelable("data", Intent.class));
            }
            results.clear();
            resumeContent();
        }
    }

    @Override protected void onStop() {
        if (lock.enabled()) coverWindows();
        lock.stopped(this);
        super.onStop();
    }

    @Override protected final void onActivityResult(int code, int result, Intent data) {
        super.onActivityResult(code, result, data);
        if (code == UNLOCK) {
            gateRequested = false;
        } else if (mayUseContent()) {
            contentResult(code, result, data);
        } else {
            Bundle pending = new Bundle();
            pending.putInt("code", code);
            pending.putInt("result", result);
            pending.putParcelable("data", data);
            results.add(pending);
        }
    }

    @Override public final void onRequestPermissionsResult(int code, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(code, permissions, grants);
        if (mayUseContent()) {
            permissionResult(code, permissions, grants);
        } else {
            Bundle pending = new Bundle();
            pending.putInt("code", code);
            pending.putStringArray("permissions", permissions);
            pending.putIntArray("grants", grants);
            results.add(pending);
        }
    }

    @Override protected final void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (deferred) state.putBundle(DEFERRED, original);
        else saveContent(state);
        if (!results.isEmpty()) state.putParcelableArrayList(RESULTS, new ArrayList<>(results));
    }

    @Override protected final void onRestoreInstanceState(Bundle state) {
        Bundle saved = state.containsKey(DEFERRED) ? state.getBundle(DEFERRED) : state;
        if (!deferred && saved != null) super.onRestoreInstanceState(saved);
    }
}
