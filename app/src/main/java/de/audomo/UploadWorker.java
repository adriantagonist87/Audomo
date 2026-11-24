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
        if (p.getJobId() == UploadQueue.DELAY_TIMER) {
            UploadQueue.get(this).plan(false, true);
            return false;
        }
        AtomicBoolean current = new AtomicBoolean(true);
        active = current;
        worker.execute(() -> {
            UploadQueue queue = UploadQueue.get(this);
            Set<Long> attempted = new HashSet<>();
            while (current.get()) {
                UploadQueue.Job j;
                UploadQueue.Attempt attempt;
                synchronized (queue) {
                    j = queue.nextReady(attempted);
                    if (j == null) break;
                    attempted.add(j.id());
                    attempt = queue.claim(j);
                }
                if (attempt == null) continue;
                try {
                    transfer(this, queue, j, p.getNetwork(), current, attempt);
                    synchronized (queue) {
                        UploadQueue.Job result = queue.byId(j.id());
                        if (result != null && result.inQueue() && result.state().equals("Konflikt"))
                            notifyError(j.id(), j.provider(), "Dateiname am Ziel bereits vergeben", true);
                    }
                } catch (Exception e) {
                    String message = safeError(e);
                    synchronized (queue) {
                        if (attempt.cancelled) continue;
                        queue.state(
                                j.id(),
                                current.get() ? "Fehler" : "Wartet",
                                current.get() ? message : "");
                        if (current.get()
                                && !attempt.finishOnly
                                && j.inQueue()) notifyError(j.id(), j.provider(), message, false);
                    }
                } finally {
                    queue.release(attempt);
                    if (attempt.cancelled) attempted.remove(j.id());
                }
            }
            new Handler(Looper.getMainLooper())
                    .post(() -> {
                        synchronized (queue) {
                            if (current.get()) {
                                jobFinished(p, queue.pendingNow());
                                queue.applyAccessChangeAfterBatch();
                            }
                        }
                    });
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters p) {
        if (p.getJobId() == UploadQueue.JOB && active != null) active.set(false);
        return true;
    }

    @Override
    public void onDestroy() {
        if (active != null) active.set(false);
        worker.shutdownNow();
        super.onDestroy();
    }

    static String safeError(Exception e) {
        if (e instanceof DropboxFiles.Failure) return e.getMessage();
        if (e instanceof java.net.UnknownHostException) return "Servername konnte nicht aufgelöst werden";
        if (e instanceof java.net.SocketTimeoutException) return "Zeitüberschreitung bei der Serververbindung";
        if (e instanceof java.net.ConnectException) return "Server nicht erreichbar";
        if (e instanceof javax.net.ssl.SSLException) return "TLS-Verbindung nicht bestätigt";
        if (e instanceof com.jcraft.jsch.SftpException)
            return "SFTP-Server konnte den Vorgang nicht abschließen";
        if (e instanceof java.net.SocketException)
            return "Netzverbindung unterbrochen";
        String s = e.getMessage();
        if (s != null
                && (s.startsWith("Serverantwort ")
                        || s.startsWith("FTP-Serverantwort ")
                        || s.startsWith("SFTP-Verbindung")
                        || s.startsWith("Ziel erneut")
                        || s.startsWith("Cloudziel ")
                        || s.startsWith("Quelle ")
                        || s.startsWith("Verifikation")
                        || s.startsWith("Netzregel"))) return s;
        return "Übertragung fehlgeschlagen (" + e.getClass().getSimpleName() + ")";
    }

    static void transfer(
            Context c,
            UploadQueue q,
            UploadQueue.Job j,
            Network net,
            AtomicBoolean running,
            UploadQueue.Attempt attempt)
            throws Exception {
        Audomo app = (Audomo) c.getApplicationContext();
        SessionStore.Entry e = app.sessions.byId(j.rec());
        if (j.action().equals("upload") && (e == null || e.trashed() != 0))
            throw new IOException("Quelle fehlt oder liegt im Papierkorb");
        CloudConfig t = CloudConfig.byId(c, j.target());
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        long size = e == null ? 0 : e.bytes();
        long[] last = {-1};
        boolean[] uploading = {false};
        RemoteFiles.Progress check =
                new RemoteFiles.Progress() {
                    public void beforeCommit() throws IOException {
                        check(0);
                        q.beforeCommit(attempt);
                    }

                    public void check(long bytes) throws IOException {
                        attempt.check();
                        NetworkCapabilities caps =
                                net == null ? null : cm.getNetworkCapabilities(net);
                        if (!running.get()
                                || caps == null
                                || (Profile.prefs(c).getBoolean("wifiOnly", true)
                                        && !caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)))
                            throw new IOException("Netzregel erlaubt keine Übertragung");
                        if (uploading[0]) {
                            long percent = Math.min(99, bytes * 100 / Math.max(1, size));
                            if (percent > last[0]) {
                                synchronized (q) {
                                    attempt.check();
                                    q.progress(j.id(), (int) percent);
                                }
                                last[0] = percent;
                            }
                        }
                    }
                };
        check.check(0);
        File staging = null;
        try (RemoteFiles remote = RemoteFiles.open(t, CloudConfig.secret(c, t.id()), net, check)) {
            if (j.action().equals("rename")) {
                attempt.expectedHash = j.verified();
                boolean caseOnly = j.origin().equalsIgnoreCase(j.path());
                String source = remote.renameHash(j.origin(), caseOnly);
                String destination = remote.renameHash(j.path(), caseOnly);
                // A reply can be lost after the server moved the file but before the local commit.
                boolean alreadyMoved = j.attempted() && source == null && j.verified().equals(destination);
                if (!alreadyMoved) {
                    if (!j.verified().equals(source))
                        throw new IOException("Quelle in der Cloud fehlt oder wurde geändert");
                    if (destination != null) {
                        synchronized (q) { attempt.check(); q.conflict(j.id(), destination); }
                        return;
                    }
                    synchronized (q) { attempt.check(); q.sent(j.id()); }
                    try {
                        remote.renameFile(j.origin(), j.path(), j.verified());
                    } catch (RemoteFiles.Conflict conflict) {
                        String existing = remote.renameHash(j.path(), caseOnly);
                        if (existing == null) throw new IOException("Cloudziel hat den Namen abgelehnt");
                        synchronized (q) { attempt.check(); q.conflict(j.id(), existing); }
                        return;
                    }
                    if (!j.verified().equals(remote.renameHash(j.path(), caseOnly))
                            || remote.renameHash(j.origin(), caseOnly) != null)
                        throw new IOException("Verifikation der Cloud-Umbenennung fehlgeschlagen");
                }
                q.finishRename(app.sessions, j, attempt);
                return;
            }
            if (j.action().equals("verifyRemoval")) {
                boolean caseOnly = !j.origin().isEmpty() && !j.origin().equals(j.path())
                        && j.origin().equalsIgnoreCase(j.path());
                String destination = remote.renameHash(j.path(), caseOnly);
                String previous =
                        j.confirmedPath().equals(j.path())
                                ? destination
                                : remote.renameHash(j.confirmedPath(), caseOnly);
                q.finishRemoval(j, destination, previous);
                return;
            }
            if (j.action().equals("delete")) {
                if (j.verified().isEmpty()) throw new IOException("Cloudkopie ist nicht bestätigt");
                check.beforeCommit();
                remote.deleteIfMatches(j.origin(), j.verified());
                if (remote.hash(j.origin()) != null)
                    throw new IOException("Verifikation der Cloudlöschung fehlgeschlagen");
                q.state(j.id(), "Cloudkopie gelöscht", "");
                return;
            }
            String local =
                    e == null
                            ? j.verified()
                            : LibraryFiles.digest(remote.guarded(new FileInputStream(e.file())));
            attempt.expectedHash = local;
            if (j.action().equals("move") && (local.isEmpty() || !local.equals(j.verified())))
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
                    synchronized (q) {
                        attempt.check();
                        q.conflict(j.id(), previous);
                    }
                    return;
                }
                synchronized (q) {
                    attempt.check();
                    q.sent(j.id());
                }
                uploading[0] = true;
                try {
                    File source;
                    if (e == null) {
                        staging = File.createTempFile("move-", ".part", c.getCacheDir());
                        remote.downloadVerified(j.origin(), j.verified(), staging);
                        source = staging;
                    } else source = e.file();
                    remote.put(source, j.path(), previous != null, previous);
                } catch (RemoteFiles.Conflict conflict) {
                    String hash = remote.hash(j.path());
                    synchronized (q) {
                        attempt.check();
                        q.conflict(j.id(), hash);
                    }
                    return;
                }
                uploading[0] = false;
                check.check(0);
                if (!local.equals(remote.hash(j.path())))
                    throw new IOException("Verifikation des Zielinhalts fehlgeschlagen");
            }
            check.check(0);
            if (j.action().equals("move")) {
                check.beforeCommit();
                remote.deleteIfMatches(j.origin(), j.verified());
                if (remote.hash(j.origin()) != null)
                    throw new IOException("Verifikation des früheren Cloudpfads fehlgeschlagen");
            }
            synchronized (q) {
                attempt.check();
                q.verified(j.id(), local);
            }
        } finally {
            if (staging != null) java.nio.file.Files.deleteIfExists(staging.toPath());
        }
        if (j.action().equals("upload") && Profile.prefs(c).getBoolean("cleanupUploaded", false)) {
            synchronized (app.sessions) {
                UploadQueue.Job completed = q.byId(j.id());
                if (completed != null
                        && completed.inQueue()
                        && q.jobs().stream()
                                .noneMatch(k ->
                                        k.rec().equals(e.id()) && !"Hochgeladen".equals(k.state())))
                    try {
                        new LibraryFiles(app.sessions).change(e, null, e.folder(), true);
                    } catch (Exception x) {
                        app.diagnostic("Warnung · Lokales Aufräumen ausstehend");
                    }
            }
        }
    }

    private void notifyError(long job, String provider, String reason, boolean conflict) {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(
                new NotificationChannel(
                        "upload-errors", "Uploadfehler", NotificationManager.IMPORTANCE_DEFAULT));
        Intent details =
                new Intent(this, MainActivity.class)
                        .setAction("de.audomo.UPLOADS")
                        .putExtra("uploadJob", job);
        PendingIntent open =
                PendingIntent.getActivity(
                        this,
                        (int) job,
                        details,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Intent retry = new Intent(this, UploadRetry.class).putExtra("job", job);
        PendingIntent again =
                PendingIntent.getBroadcast(
                        this,
                        (int) job,
                        retry,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = reason + (reason.endsWith(".") ? " " : ". ")
                + (conflict ? "Bitte in der Warteschlange entscheiden."
                        : "Audomo versucht es automatisch erneut.");
        Notification.Builder notice = new Notification.Builder(this, "upload-errors")
                .setContentTitle((provider.isEmpty() ? "Cloud" : provider) + "-Upload benötigt Aufmerksamkeit")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOnlyAlertOnce(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setSmallIcon(R.drawable.ic_audomo_notification)
                .setContentIntent(open)
                .setAutoCancel(true);
        if (!conflict)
            notice.addAction(new Notification.Action.Builder(null, "Erneut versuchen", again).build());
        notice.addAction(new Notification.Action.Builder(null, "Details", open).build());
        manager.notify("upload", (int) job, notice.build());
    }
}
