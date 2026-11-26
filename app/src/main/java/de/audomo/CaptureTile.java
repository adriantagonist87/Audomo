package de.audomo;

import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.*;

public final class CaptureTile extends TileService {
    private final Runnable refresh = this::refreshTile;

    @Override public void onTileAdded() {
        super.onTileAdded();
        QuickAccess.tilePresent(this, true);
    }

    @Override public void onTileRemoved() {
        super.onTileRemoved();
        QuickAccess.tilePresent(this, false);
    }

    @Override
    public void onStartListening() {
        super.onStartListening();
        if (getQsTile() != null) QuickAccess.tilePresent(this, true);
        ((Audomo) getApplication()).tileChanged = refresh;
        refreshTile();
    }

    @Override
    public void onStopListening() {
        Audomo app = (Audomo) getApplication();
        if (app.tileChanged == refresh) app.tileChanged = null;
        super.onStopListening();
    }

    private void refreshTile() {
        Audomo app = (Audomo) getApplication();
        Tile tile = getQsTile();
        if (tile == null) return;
        tile.setLabel("Audomo");
        tile.setSubtitle(app.active() ? "Stoppen" : app.ready() ? "Aufnehmen" : "Bitte warten");
        tile.setState(
                app.active()
                        ? Tile.STATE_ACTIVE
                        : app.ready() ? Tile.STATE_INACTIVE : Tile.STATE_UNAVAILABLE);
        tile.updateTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        if (isLocked()) {
            unlockAndRun(this::activate);
            return;
        }
        activate();
    }

    private void activate() {
        Audomo app = (Audomo) getApplication();
        if (app.active()) app.command("STOP", null);
        else if (app.ready()) {
            Intent intent =
                    new Intent(this, TileStartActivity.class)
                            .setFlags(
                                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivityAndCollapse(
                    PendingIntent.getActivity(this, 7, intent, PendingIntent.FLAG_IMMUTABLE));
        }
    }
}
