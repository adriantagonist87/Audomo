package de.audomo;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

public final class TileOpenActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ComponentName main = new ComponentName(this, MainActivity.class);
        try {
            for (ActivityManager.AppTask task :
                    getSystemService(ActivityManager.class).getAppTasks()) {
                try {
                    if (main.equals(task.getTaskInfo().baseIntent.getComponent())) {
                        task.moveToFront();
                        return;
                    }
                } catch (IllegalArgumentException removedTask) {
                    // A task can leave Recents between enumeration and activation.
                }
            }
            startActivity(Intent.makeMainActivity(main).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } finally {
            finish();
        }
    }
}
