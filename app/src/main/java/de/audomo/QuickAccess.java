package de.audomo;

import android.app.AlertDialog;
import android.app.StatusBarManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.UUID;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

final class QuickAccess {
    private static final String SHORTCUT_ID = "capture-toggle";
    private static final String TILE_PRESENT = "tilePresent";
    private final MainActivity screen;
    private final ShortcutManager launcher;

    QuickAccess(MainActivity screen) {
        this.screen = screen;
        launcher = screen.getSystemService(ShortcutManager.class);
    }

    static void tilePresent(Context ctx, boolean present) {
        ctx.getSharedPreferences("quickAccess", Context.MODE_PRIVATE).edit()
                .putBoolean(TILE_PRESENT, present).apply();
    }

    String tileStatus() {
        var prefs = screen.getSharedPreferences("quickAccess", Context.MODE_PRIVATE);
        if (!prefs.contains(TILE_PRESENT)) return "Antippen, um den Status zu prüfen";
        return prefs.getBoolean(TILE_PRESENT, false)
                ? "In den Schnelleinstellungen vorhanden" : "Noch nicht hinzugefügt";
    }

    void addTile() {
        AlertDialog dialog = feedback("Schnelleinstellungskachel", "Android prüft die Audomo-Kachel …");
        Runnable slow = () -> dialog.setMessage("Android hat noch kein Ergebnis gemeldet. Du kannst die Schnelleinstellungen öffnen und unter „Bearbeiten“ nach Audomo suchen.");
        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(slow, 8000);
        dialog.setOnDismissListener(d -> handler.removeCallbacks(slow));
        try {
            screen.getSystemService(StatusBarManager.class).requestAddTileService(
                    new ComponentName(screen, CaptureTile.class), "Audomo",
                    Icon.createWithResource(screen, R.drawable.ic_mic), screen.getMainExecutor(), code -> {
                        handler.removeCallbacks(slow);
                        String message;
                        switch (code) {
                            case StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> {
                                tilePresent(screen, true);
                                message = "Die Audomo-Kachel ist bereits in deinen Schnelleinstellungen.";
                            }
                            case StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> {
                                tilePresent(screen, true);
                                message = "Die Audomo-Kachel wurde zu deinen Schnelleinstellungen hinzugefügt.";
                            }
                            case StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED ->
                                    message = "Die Kachel wurde nicht hinzugefügt. Du kannst sie auch in den Schnelleinstellungen unter „Bearbeiten“ hinzufügen.";
                            case StatusBarManager.TILE_ADD_REQUEST_ERROR_REQUEST_IN_PROGRESS ->
                                    message = "Es läuft bereits eine Anfrage. Schließe zuerst den offenen Android-Dialog.";
                            default -> message = "Android konnte das Hinzufügen nicht bestätigen. Öffne die Schnelleinstellungen und wähle „Bearbeiten“.";
                        }
                        if (dialog.isShowing()) dialog.setMessage(message);
                    });
        } catch (IllegalStateException | SecurityException error) {
            handler.removeCallbacks(slow);
            dialog.setMessage("Android kann die Kachelauswahl gerade nicht öffnen. Du kannst Audomo in den Schnelleinstellungen unter „Bearbeiten“ hinzufügen.");
        }
    }

    private enum PinState { AVAILABLE, PINNED, UNSUPPORTED, UNAVAILABLE }

    private static final ExecutorService launcherCalls = Executors.newSingleThreadExecutor();

    private void readPinState(Consumer<PinState> receive) {
        launcherCalls.execute(() -> {
            PinState result;
            try {
                boolean pinned = launcher.getPinnedShortcuts().stream()
                        .anyMatch(s -> SHORTCUT_ID.equals(s.getId()) && s.isEnabled());
                result = pinned ? PinState.PINNED : launcher.isRequestPinShortcutSupported()
                        ? PinState.AVAILABLE : PinState.UNSUPPORTED;
            } catch (IllegalStateException | SecurityException e) {
                result = PinState.UNAVAILABLE;
            }
            PinState state = result;
            screen.runOnUiThread(() -> { if (!screen.isDestroyed()) receive.accept(state); });
        });
    }

    void updateShortcutStatus(TextView value) {
        if (value.length() == 0) value.setText("Startbildschirm wird geprüft …");
        readPinState(state -> value.setText(switch (state) {
            case PINNED -> "Als angeheftet bestätigt";
            case AVAILABLE -> "Aufnahme mit einem Tipp starten oder beenden";
            case UNSUPPORTED -> "Vom aktuellen Launcher nicht unterstützt";
            case UNAVAILABLE -> "Launcher momentan nicht verfügbar";
        }));
    }

    void addShortcut() {
        AlertDialog dialog = new AlertDialog.Builder(screen).setTitle("Startbildschirm-Verknüpfung")
                .setMessage("Startbildschirm wird geprüft …")
                .setPositiveButton("Schließen", null).setNeutralButton("Erneut anfordern", null).show();
        View retry = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(v -> {
            retry.setVisibility(View.GONE);
            requestShortcut(dialog);
        });
        readPinState(state -> {
            if (!dialog.isShowing()) return;
            switch (state) {
                case PINNED -> {
                    dialog.setMessage("Android meldet „Aufnahme“ als angeheftet. Ein Tipp startet die Aufnahme, der nächste beendet und speichert sie.");
                    retry.setVisibility(View.VISIBLE);
                }
                case AVAILABLE -> requestShortcut(dialog);
                case UNSUPPORTED -> dialog.setMessage("Dieser Launcher unterstützt das Anheften von Verknüpfungen nicht.");
                case UNAVAILABLE -> dialog.setMessage("Der Launcher ist gerade nicht verfügbar. Versuche es nach dem Entsperren erneut.");
            }
        });
    }

    private void requestShortcut(AlertDialog dialog) {
        dialog.setMessage("Bestätige das Hinzufügen im Launcher. Die Verknüpfung heißt „Aufnahme“.");
        String action = screen.getPackageName() + ".PINNED." + UUID.randomUUID();
        String success = "„Aufnahme“ wurde angeheftet. Ein Tipp startet die Aufnahme, der nächste beendet und speichert sie.";
        class PinResult extends BroadcastReceiver {
            boolean confirmed;
            @Override public void onReceive(Context c, Intent intent) {
                confirmed = true;
                if (dialog.isShowing()) dialog.setMessage(success);
            }
        }
        PinResult result = new PinResult();
        screen.registerReceiver(result, new IntentFilter(action), Context.RECEIVER_NOT_EXPORTED);
        PendingIntent callback = PendingIntent.getBroadcast(screen, 0, new Intent(action).setPackage(screen.getPackageName()),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);
        View decor = dialog.getWindow().getDecorView();
        decor.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) {}
            @Override public void onViewDetachedFromWindow(View v) {
                screen.unregisterReceiver(result);
                callback.cancel();
                v.removeOnAttachStateChangeListener(this);
            }
        });
        decor.getViewTreeObserver().addOnWindowFocusChangeListener(new android.view.ViewTreeObserver.OnWindowFocusChangeListener() {
            private boolean lostFocus;
            @Override public void onWindowFocusChanged(boolean focus) {
                if (!focus) lostFocus = true;
                else if (lostFocus) readPinState(state -> {
                    if (dialog.isShowing()) dialog.setMessage(result.confirmed || state == PinState.PINNED ? success
                            : "Android hat keine angeheftete Verknüpfung gemeldet. Nach einem Abbruch kannst du das Hinzufügen erneut anfordern.");
                });
            }
        });
        ShortcutInfo shortcut = new ShortcutInfo.Builder(screen, SHORTCUT_ID)
                .setShortLabel("Aufnahme").setLongLabel("Audomo-Aufnahme starten oder stoppen")
                .setIcon(Icon.createWithResource(screen, R.drawable.ic_capture_shortcut))
                .setIntent(new Intent(screen, TileStartActivity.class).setAction(TileStartActivity.TOGGLE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                                | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION))
                .build();
        launcherCalls.execute(() -> {
            boolean accepted;
            try {
                accepted = launcher.requestPinShortcut(shortcut, callback.getIntentSender());
            } catch (IllegalStateException | SecurityException error) {
                accepted = false;
            }
            if (!accepted) screen.runOnUiThread(() -> {
                if (dialog.isShowing()) dialog.setMessage("Der Launcher hat die Anfrage nicht angenommen.");
            });
        });
    }

    private AlertDialog feedback(String title, String message) {
        return new AlertDialog.Builder(screen).setTitle(title).setMessage(message)
                .setPositiveButton("Schließen", null).show();
    }
}
