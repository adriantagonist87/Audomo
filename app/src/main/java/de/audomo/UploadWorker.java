package de.audomo;

import android.Manifest;
import android.app.*;
import android.app.job.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.*;
import android.os.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UploadWorker extends JobService {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private AtomicBoolean active;

    @Override
    public boolean onStartJob(JobParameters p) {
        AtomicBoolean current = new AtomicBoolean(true);
        active = current;
        worker.execute(() -> {
            UploadQueue queue = UploadQueue.get(this);
            Set<Long> attempted = new HashSet<>();
            while (current.get()) {
                UploadQueue.Job j =
                        queue.jobs().stream()
                                .filter(v ->
                                        !attempted.contains(v.id())
                                                && List.of("Wartet", "Fehler", "Upload läuft")
                                                        .contains(v.state()))
                                .findFirst()
                                .orElse(null);
                if (j == null) break;
                attempted.add(j.id());
                try {
                    transfer(this, queue, j, p.getNetwork(), current);
                } catch (Exception e) {
                    String message = safeError(e);
                    queue.state(
                            j.id(),
                            current.get() ? "Fehler" : "Wartet",
                            current.get() ? message : "");
                    if (current.get() && !message.equals(j.error())) notifyError(j.id());
                }
            }
            new Handler(Looper.getMainLooper())
                    .post(() -> {
                        synchronized (queue) {
                            if (current.get()) jobFinished(p, queue.pending());
                        }
                    });
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters p) {
        if (active != null) active.set(false);
        return true;
    }

    @Override
    public void onDestroy() {
        if (active != null) active.set(false);
        worker.shutdownNow();
        super.onDestroy();
    }

    static String safeError(Exception e) {
        if (e instanceof javax.net.ssl.SSLException) return "TLS-Verbindung nicht bestätigt";
        if (e instanceof com.jcraft.jsch.SftpException)
            return "SFTP-Server konnte den Vorgang nicht abschließen";
        if (e instanceof java.net.SocketException || e instanceof java.net.SocketTimeoutException)
            return "Netzverbindung unterbrochen";
        String s = e.getMessage();
        if (s != null
                && (s.startsWith("Serverantwort ")
                        || s.startsWith("FTP-Serverantwort ")
                        || s.startsWith("SFTP-Verbindung")
                        || s.startsWith("Ziel erneut")
                        || s.startsWith("Quelle ")
                        || s.startsWith("Verifikation")
                        || s.startsWith("Netzregel"))) return s;
        return "Übertragung fehlgeschlagen (" + e.getClass().getSimpleName() + ")";
    }

    static void transfer(
            Context c, UploadQueue q, UploadQueue.Job j, Network net, AtomicBoolean running)
            throws Exception {
        Audomo app = (Audomo) c.getApplicationContext();
        SessionStore.Entry e =
                app.sessions.list().stream()
                        .filter(entry ->
                                entry.id().equals(j.rec())
                                        && (entry.trashed() == 0 || j.action().equals("delete")))
                        .findFirst()
                        .orElseThrow(
                                () -> new IOException("Quelle fehlt oder liegt im Papierkorb"));
        CloudConfig t = CloudConfig.byId(c, j.target());
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        long size = e.file().length();
        long[] last = {-1};
        boolean[] uploading = {false};
        RemoteFiles.Progress check =
                bytes -> {
                    NetworkCapabilities caps = net == null ? null : cm.getNetworkCapabilities(net);
                    if (!running.get()
                            || caps == null
                            || (Profile.prefs(c).getBoolean("wifiOnly", true)
                                    && !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)))
                        throw new IOException("Netzregel erlaubt keine Übertragung");
                    if (uploading[0]) {
                        long percent = Math.min(99, bytes * 100 / Math.max(1, size));
                        if (percent > last[0]) {
                            q.progress(j.id(), (int) percent);
                            last[0] = percent;
                        }
                    }
                };
        check.check(0);
        q.state(j.id(), "Upload läuft", j.error());
        String local = LibraryFiles.digest(new FileInputStream(e.file()));
        try (RemoteFiles remote = RemoteFiles.open(t, CloudConfig.secret(c, t.id()), net, check)) {
            if (j.action().equals("delete")) {
                if (j.verified().isEmpty()) throw new IOException("Cloudkopie ist nicht bestätigt");
                remote.deleteIfMatches(j.origin(), j.verified());
                if (remote.hash(j.origin()) != null)
                    throw new IOException("Verifikation der Cloudlöschung fehlgeschlagen");
                q.state(j.id(), "Cloudkopie gelöscht", "");
                return;
            }
            if (j.action().equals("move") && !local.equals(j.verified()))
                throw new IOException("Quelle hat sich seit dem Upload geändert");
            String sub = RemoteFiles.parent(j.path());
            String base = t.base();
            remote.test();
            String rest = sub.substring(base.equals("/") ? 0 : base.length());
            String dir = base;
            for (String part : rest.split("/"))
                if (!part.isEmpty()) {
                    dir = CloudConfig.path(dir + "/" + part);
                    remote.ensureFolder(dir);
                }
            String previous = remote.hash(j.path());
            boolean recovered = j.attempted() && local.equals(previous);
            if (!recovered) {
                if (previous != null
                        && (j.replacement() == null || !j.replacement().equals(previous))) {
                    q.conflict(j.id(), previous);
                    return;
                }
                q.sent(j.id());
                uploading[0] = true;
                try {
                    remote.put(e.file(), j.path(), previous != null, previous);
                } catch (RemoteFiles.Conflict conflict) {
                    q.conflict(j.id(), remote.hash(j.path()));
                    return;
                }
                uploading[0] = false;
                check.check(0);
                if (!local.equals(remote.hash(j.path())))
                    throw new IOException("Verifikation des Zielinhalts fehlgeschlagen");
            }
            check.check(0);
            if (j.action().equals("move")) {
                remote.deleteIfMatches(j.origin(), j.verified());
                if (remote.hash(j.origin()) != null)
                    throw new IOException("Verifikation des früheren Cloudpfads fehlgeschlagen");
            }
            q.verified(j.id(), local);
        }
        if (j.action().equals("upload") && Profile.prefs(c).getBoolean("cleanupUploaded", false)) {
            synchronized (q) {
                if (q.jobs().stream()
                        .noneMatch(k -> k.rec().equals(e.id()) && !"Hochgeladen".equals(k.state())))
                    try {
                        new LibraryFiles(app.sessions).change(e, null, e.folder(), true);
                    } catch (Exception x) {
                        app.diagnostic("Warnung · Lokales Aufräumen ausstehend");
                    }
            }
        }
    }

    void notifyError(long job) {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(
                new NotificationChannel(
                        "upload-errors", "Uploadfehler", NotificationManager.IMPORTANCE_DEFAULT));
        Intent details = new Intent(this, MainActivity.class).setAction("de.audomo.UPLOADS");
        PendingIntent open =
                PendingIntent.getActivity(
                        this,
                        0,
                        details,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent retry = new Intent(this, UploadRetry.class).putExtra("job", job);
        PendingIntent again =
                PendingIntent.getBroadcast(
                        this,
                        (int) job,
                        retry,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        manager.notify(
                "upload",
                (int) job,
                new Notification.Builder(this, "upload-errors")
                        .setContentTitle("Upload unterbrochen")
                        .setContentText("Audomo versucht es erneut.")
                        .setVisibility(Notification.VISIBILITY_PUBLIC)
                        .setSmallIcon(R.drawable.ic_upload)
                        .setContentIntent(open)
                        .setAutoCancel(true)
                        .addAction(
                                new Notification.Action.Builder(null, "Erneut versuchen", again)
                                        .build())
                        .addAction(new Notification.Action.Builder(null, "Details", open).build())
                        .build());
    }
}
