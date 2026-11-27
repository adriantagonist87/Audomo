package de.audomo;

import android.app.AlertDialog;
import android.net.Network;
import android.os.SystemClock;

import java.io.InterruptedIOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class DropboxRestoreDialog {
    private final MainActivity screen;

    DropboxRestoreDialog(MainActivity screen) {
        this.screen = screen;
    }

    void show() {
        try {
            List<CloudConfig> targets =
                    CloudConfig.all(screen).stream()
                            .filter(t -> t.protocol().equals("Dropbox") && t.hasCredentials(screen))
                            .toList();
            if (targets.isEmpty()) {
                new AlertDialog.Builder(screen)
                        .setTitle("Aus Dropbox wiederherstellen")
                        .setMessage(
                                "Verbinde Dropbox, um deine Aufnahmen aus dem Audomo-App-Ordner"
                                    + " herunterzuladen.")
                        .setPositiveButton(
                                "Uploadziele öffnen",
                                (d, w) -> new CloudScreen(screen).manage(List.of()))
                        .setNegativeButton("Abbrechen", null)
                        .show();
            } else if (targets.size() == 1) confirm(targets.get(0));
            else {
                String[] names = targets.stream().map(CloudConfig::label).toArray(String[]::new);
                for (int i = 0; i < names.length; i++) {
                    String label = targets.get(i).label();
                    if (targets.stream().filter(t -> t.label().equals(label)).count() > 1)
                        names[i] += " · Ziel " + (i + 1);
                }
                new AlertDialog.Builder(screen)
                        .setTitle("Dropbox-Ordner wählen")
                        .setItems(names, (d, i) -> confirm(targets.get(i)))
                        .setNegativeButton("Abbrechen", null)
                        .show();
            }
        } catch (Exception e) {
            screen.toast(Audomo.cause(e));
        }
    }

    private void confirm(CloudConfig target) {
        new AlertDialog.Builder(screen)
                .setTitle("Aufnahmen aus Dropbox wiederherstellen?")
                .setMessage(
                        "Quelle: "
                                + target.label()
                                + "\n\n"
                                + "Audomo übernimmt die Aufnahmen und Unterordner in deine"
                                + " Bibliothek. Identische lokale Dateien werden zugeordnet. Bei"
                                + " abweichendem Inhalt bleiben beide Fassungen erhalten.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Herunterladen", (d, w) -> start(target))
                .show();
    }

    private void start(CloudConfig target) {
        AtomicBoolean canceled = new AtomicBoolean();
        AlertDialog dialog =
                new AlertDialog.Builder(screen)
                        .setTitle("Aus Dropbox wiederherstellen")
                        .setMessage("Ordner wird eingelesen …")
                        .setNegativeButton("Abbrechen", null)
                        .show();
        dialog.setOnDismissListener(d -> canceled.set(true));
        // The library executor also serializes imports, renames and migration on this device.
        screen.runFiles(
                () -> {
                    Audomo app = (Audomo) screen.getApplication();
                    String message;
                    try {
                        Network network = CloudLibrary.network(screen);
                        RemoteFiles.Progress check =
                                new RemoteFiles.Progress() {
                                    long last;

                                    public void check(long bytes) throws java.io.IOException {
                                        if (canceled.get()
                                                || screen.isDestroyed()
                                                || screen.isFinishing())
                                            throw new InterruptedIOException(
                                                    "Wiederherstellung abgebrochen");
                                        long now = SystemClock.elapsedRealtime();
                                        if (now - last >= 1000) {
                                            CloudLibrary.checkNetwork(screen, network);
                                            last = now;
                                        }
                                    }
                                };
                        check.check(0);
                        try (DropboxFiles remote =
                                new DropboxFiles(
                                        target,
                                        CloudConfig.secret(screen, target.id()),
                                        network,
                                        check)) {
                            message =
                                    new DropboxRestore(app.sessions, remote)
                                            .run(
                                                    progress ->
                                                            screen.runOnUiThread(
                                                                    () -> {
                                                                        if (dialog.isShowing())
                                                                            dialog.setMessage(
                                                                                    progress);
                                                                    }))
                                            .summary();
                        }
                    } catch (Exception error) {
                        message = Audomo.cause(error);
                    }
                    String result = message;
                    screen.runOnUiThread(
                            () -> {
                                if (!dialog.isShowing()
                                        || screen.isFinishing()
                                        || screen.isDestroyed()) return;
                                dialog.dismiss();
                                new AlertDialog.Builder(screen)
                                        .setTitle("Dropbox-Wiederherstellung")
                                        .setMessage(result)
                                        .setPositiveButton("Schließen", null)
                                        .show();
                            });
                    return "";
                });
    }
}
