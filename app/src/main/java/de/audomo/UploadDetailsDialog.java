package de.audomo;

import android.app.*;
import android.os.*;
import android.widget.*;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;

public final class UploadDetailsDialog extends DialogFragment {
    private static final String TAG = "uploadDetails";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MainActivity screen;
    private Audomo app;
    private UploadQueue queue;
    private LinearLayout page;
    private TextView status;
    private UploadQueue.Job shown;
    private String feedback = "";
    private boolean busy, shownCommitting;

    static void open(MainActivity screen, long id) {
        if (screen.getFragmentManager().findFragmentByTag(TAG) != null) return;
        Bundle args = new Bundle();
        args.putLong("job", id);
        UploadDetailsDialog dialog = new UploadDetailsDialog();
        dialog.setArguments(args);
        dialog.show(screen.getFragmentManager(), TAG);
    }

    @Override
    public Dialog onCreateDialog(Bundle state) {
        screen = (MainActivity) getActivity();
        app = (Audomo) screen.getApplication();
        queue = UploadQueue.get(screen);
        page = screen.column();
        page.setPadding(screen.dp(20), screen.dp(6), screen.dp(20), screen.dp(8));
        ScrollView scroll = new ScrollView(screen);
        scroll.addView(page);
        return new AlertDialog.Builder(screen)
                .setTitle("Auftragsdetails")
                .setView(scroll)
                .setNegativeButton("Schließen", null)
                .create();
    }

    private final Runnable refresh =
            new Runnable() {
                public void run() {
                    render();
                    handler.postDelayed(this, 1000);
                }
            };

    @Override
    public void onStart() {
        super.onStart();
        refresh.run();
    }

    @Override
    public void onStop() {
        handler.removeCallbacks(refresh);
        super.onStop();
    }

    private void render() {
        UploadQueue.Job job = queue.byId(getArguments().getLong("job"));
        if (job == null) {
            page.removeAllViews();
            page.addView(screen.text("Auftrag nicht mehr vorhanden", 16, MainActivity.DIM));
            return;
        }
        String phase =
                switch (job.action()) {
                    case "move" -> "Verschieben";
                    case "delete" -> "Cloudkopie löschen";
                    default -> "Upload";
                };
        boolean committing = job.state().equals("Upload läuft") && queue.committing(job.id());
        String state =
                phase
                        + " · "
                        + (committing
                                ? "Ziel bestätigt den Abschluss"
                                : CloudScreen.queueStatus(job))
                        + (job.state().equals("Upload läuft") ? " · " + job.progress() + " %" : "");
        if (shown != null
                && shownCommitting == committing
                && shown.state().equals(job.state())
                && shown.path().equals(job.path())
                && shown.lastChecked() == job.lastChecked()
                && shown.lastAttempt() == job.lastAttempt()
                && shown.availability().equals(job.availability())
                && shown.error().equals(job.error())) {
            status.setText(state);
            return;
        }
        shown = job;
        shownCommitting = committing;
        page.removeAllViews();
        page.addView(screen.text(RemoteFiles.leaf(job.path()), 19, MainActivity.INK));
        String target = job.provider();
        try {
            target = CloudConfig.byId(screen, job.target()).uploadLabel();
        } catch (Exception ignored) {
        }
        page.addView(screen.text(target + "\n" + job.path(), 14, MainActivity.DIM));
        status = screen.text(state, 16, MainActivity.ACCENT);
        status.setPadding(0, screen.dp(8), 0, screen.dp(8));
        page.addView(status);
        if (job.lastAttempt() > 0)
            page.addView(
                    screen.text(
                            "Versuche: "
                                    + job.attempts()
                                    + " · Zuletzt "
                                    + timestamp(job.lastAttempt()),
                            14,
                            MainActivity.DIM));
        if (job.lastChecked() > 0)
            page.addView(
                    screen.text(
                            "Cloudkopie geprüft: " + timestamp(job.lastChecked()),
                            14,
                            MainActivity.DIM));
        if (!job.error().isEmpty()) page.addView(screen.text(job.error(), 14, MainActivity.INK));
        if (!job.availability().isEmpty())
            page.addView(screen.text(job.availability(), 14, MainActivity.INK));
        if (!feedback.isEmpty()) page.addView(screen.text(feedback, 14, MainActivity.DIM));
        if (busy) return;
        switch (job.state()) {
            case "Wartet" -> {
                if (job.notBefore() > System.currentTimeMillis())
                    action("Jetzt freigeben", () -> control(() -> queue.resume(job.id())));
            }
            case "Ausgelassen", "Abgebrochen", "Fehler" ->
                    action("Erneut versuchen", () -> control(() -> queue.resume(job.id())));
            case "Konflikt" -> {
                action("Neuen Namen verwenden", () -> resolve(job, 0));
                action(
                        "Zieldatei überschreiben",
                        () ->
                                new AlertDialog.Builder(screen)
                                        .setTitle("Zieldatei überschreiben?")
                                        .setMessage(job.path())
                                        .setNegativeButton("Abbrechen", null)
                                        .setPositiveButton(
                                                "Überschreiben", (d, w) -> resolve(job, 1))
                                        .show());
            }
            case "Hochgeladen" -> {
                action(
                        "Cloudkopie prüfen",
                        () -> execute(() -> CloudReconcile.verify(app.sessions, job)));
                if (!job.availability().isEmpty()) {
                    action(
                            "Cloudpfade abgleichen",
                            () ->
                                    execute(() ->
                                            CloudReconcile.run(
                                                    app.sessions,
                                                    CloudConfig.byId(screen, job.target()))));
                    if (job.availability().equals("Cloudkopie fehlt"))
                        action(
                                "Erneut hochladen",
                                () ->
                                        execute(() -> {
                                            int added =
                                                    queue.enqueueSelected(
                                                            app.sessions,
                                                            List.of(job.rec()),
                                                            CloudConfig.byId(screen, job.target()));
                                            return added > 0
                                                    ? "Auftrag eingereiht"
                                                    : "Auftrag bereits vorhanden";
                                        }));
                }
            }
        }
        if (job.open() && !committing) {
            action(
                    "Überspringen",
                    () ->
                            control(() ->
                                    job.state().equals("Konflikt")
                                            ? skipConflict(job)
                                            : queue.stop(job.id(), true)));
            action(
                    "Auftrag abbrechen",
                    () ->
                            new AlertDialog.Builder(screen)
                                    .setTitle("Diesen Auftrag abbrechen?")
                                    .setMessage(RemoteFiles.leaf(job.path()) + "\n" + job.path())
                                    .setNegativeButton("Zurück", null)
                                    .setPositiveButton(
                                            "Auftrag abbrechen",
                                            (d, w) -> control(() -> queue.stop(job.id(), false)))
                                    .show());
        }
        action("Weitere Uploadziele wählen", () -> moreTargets(job));
        if (!(screen instanceof QueueActivity))
            action(
                    "Zur Warteschlange",
                    () -> {
                        dismiss();
                        screen.openQueue();
                    });
    }

    private String skipConflict(UploadQueue.Job job) throws Exception {
        queue.resolve(job, 2);
        return "Auftrag ausgelassen";
    }

    private void resolve(UploadQueue.Job job, int choice) {
        control(() -> {
            queue.resolve(job, choice);
            return "Auftrag freigegeben";
        });
    }

    private void moreTargets(UploadQueue.Job job) {
        screen.runFiles(() -> {
            SessionStore.Entry local = app.sessions.byId(job.rec());
            if (local != null && local.trashed() == 0) {
                screen.runOnUiThread(() -> chooseTargets(job));
                return "";
            }
            if (local != null) return "Aufnahme zuerst aus dem Papierkorb wiederherstellen.";
            CloudLibrary library = new CloudLibrary(app.sessions);
            SessionStore.Entry entry = library.known().get(job.rec());
            UploadQueue.Job copy =
                    queue.jobs().stream()
                            .filter(j ->
                                    j.rec().equals(job.rec())
                                            && j.knownCopy()
                                            && j.availability().isEmpty())
                            .findFirst()
                            .orElse(null);
            if (entry == null || copy == null)
                throw new IOException("Keine verfügbare Kopie der Aufnahme");
            screen.runOnUiThread(() -> {
                if (!isAdded()) return;
                new AlertDialog.Builder(screen)
                        .setTitle("Aufnahme aus der Cloud laden?")
                        .setMessage(
                                "Für weitere Uploadziele wird die geprüfte Aufnahme lokal"
                                        + " wiederhergestellt.")
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton(
                                "Wiederherstellen",
                                (d, w) ->
                                        execute(() -> {
                                            library.restore(entry, copy);
                                            screen.runOnUiThread(() -> chooseTargets(job));
                                            return "Aufnahme wiederhergestellt";
                                        }))
                        .show();
            });
            return "";
        });
    }

    private void chooseTargets(UploadQueue.Job job) {
        if (!isAdded()) return;
        dismissAllowingStateLoss();
        ManualUploadDialog.chooseTarget(screen, List.of(job.rec()), job.target());
    }

    private String timestamp(long millis) {
        return java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss", Locale.GERMAN)
                .withZone(java.time.ZoneId.systemDefault())
                .format(java.time.Instant.ofEpochMilli(millis));
    }

    private void action(String label, Runnable click) {
        page.addView(screen.button(label, v -> click.run()));
    }

    private void control(Callable<String> task) {
        execute(app.uploadControls, task);
    }

    private void execute(Callable<String> task) {
        execute(app.libraryFiles, task);
    }

    private void execute(java.util.concurrent.Executor executor, Callable<String> task) {
        if (busy) return;
        busy = true;
        feedback = "Wird ausgeführt …";
        shown = null;
        render();
        executor.execute(() -> {
            String result;
            try {
                result = task.call();
            } catch (Exception e) {
                result = Audomo.cause(e);
            }
            String message = result;
            screen.runOnUiThread(() -> {
                if (!isAdded()) return;
                busy = false;
                feedback = message;
                shown = null;
                render();
            });
        });
    }
}
