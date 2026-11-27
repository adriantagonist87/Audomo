package de.audomo;

import android.app.*;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.net.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Network operations are single-flight; state and observers are owned by the main thread. */
final class AppUpdates implements Application.ActivityLifecycleCallbacks {
    static final String AUTO = "autoCheckUpdates", INTERVAL = "updateInterval";
    static final String[] INTERVALS = {"Bei jedem Start", "Täglich", "Wöchentlich", "Monatlich"};
    private final Audomo app;
    private final SharedPreferences checks;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Set<Runnable> listeners = new HashSet<>();
    GitHubUpdate.Release latest;
    enum Work { IDLE, CHECK, DOWNLOAD }
    Work work = Work.IDLE;
    int percent;
    String error = "";
    GitHubUpdate.Release prepared;
    private boolean newVisit = true;
    private int started;

    AppUpdates(Audomo app) {
        this.app = app;
        checks = app.getSharedPreferences("github-update-status", 0);
        if (checks.contains("version")) try {
            latest = new GitHubUpdate.Release(checks.getString("version", ""), checks.getLong("bytes", 0), checks.getString("sha", ""));
        } catch (IllegalArgumentException ignored) { checks.edit().clear().apply(); }
        app.registerActivityLifecycleCallbacks(this);
    }

    boolean automatic() { return Profile.prefs(app).getBoolean(AUTO, false); }
    int interval() { return Math.max(0, Math.min(3, Profile.prefs(app).getInt(INTERVAL, 1))); }
    long lastCheck() { return checks.getLong("lastCheck", 0); }
    boolean available() { return latest != null && GitHubUpdate.newer(latest.version(), BuildConfig.VERSION_NAME); }
    void subscribe(Runnable listener) { listeners.add(listener); }
    void unsubscribe(Runnable listener) { listeners.remove(listener); }
    void changed() { for (Runnable listener : Set.copyOf(listeners)) listener.run(); }

    static boolean due(int interval, long last, ZonedDateTime now) {
        if (interval == 0 || last <= 0 || last > now.toInstant().toEpochMilli()) return true;
        ZonedDateTime previous = Instant.ofEpochMilli(last).atZone(now.getZone());
        ZonedDateTime next = switch (interval) {
            case 2 -> previous.plusWeeks(1);
            case 3 -> previous.plusMonths(1);
            default -> previous.plusDays(1);
        };
        return !now.isBefore(next);
    }

    void check() {
        if (work != Work.IDLE) return;
        error = "";
        work = Work.CHECK;
        changed();
        worker.execute(() -> {
            try {
                GitHubUpdate.Release r = GitHubUpdate.latest();
                app.main.post(() -> {
                    checks.edit().putString("version", r.version()).putLong("bytes", r.size())
                            .putString("sha", r.sha256()).putLong("lastCheck", System.currentTimeMillis()).apply();
                    if (!r.equals(latest)) prepared = null;
                    latest = r;
                    finish("");
                });
            } catch (Exception e) { app.main.post(() -> finish("Updateprüfung fehlgeschlagen. " + explanation(e))); }
        });
    }

    void download() {
        if (!available() || work != Work.IDLE) return;
        GitHubUpdate.Release release = latest;
        prepared = null;
        error = "";
        percent = 0;
        work = Work.DOWNLOAD;
        changed();
        worker.execute(() -> {
            try {
                GitHubUpdate.prepare(app, release, p -> app.main.post(() -> {
                    percent = p;
                    changed();
                }));
                app.main.post(() -> { prepared = release; finish(""); });
            } catch (Exception e) { app.main.post(() -> finish("Update konnte nicht vorbereitet werden. " + explanation(e))); }
        });
    }

    private static String explanation(Exception e) {
        if (e instanceof UnknownHostException || e instanceof ConnectException)
            return "GitHub ist nicht erreichbar. Bitte die Internetverbindung prüfen.";
        if (e instanceof SocketTimeoutException)
            return "GitHub antwortet zu langsam. Bitte erneut versuchen.";
        return Audomo.cause(e);
    }

    private void finish(String message) { work = Work.IDLE; error = message; changed(); }

    public void onActivityStarted(Activity a) { started++; }
    public void onActivityStopped(Activity a) { if (--started == 0 && !a.isChangingConfigurations()) newVisit = true; }
    public void onActivityResumed(Activity a) {
        if (newVisit && a instanceof ProtectedActivity screen && screen.mayUseContent()) {
            newVisit = false;
            if (automatic() && due(interval(), lastCheck(), ZonedDateTime.now())) check();
        }
    }
    public void onActivityCreated(Activity a, Bundle b) {}
    public void onActivityPaused(Activity a) {}
    public void onActivitySaveInstanceState(Activity a, Bundle b) {}
    public void onActivityDestroyed(Activity a) {}
}
