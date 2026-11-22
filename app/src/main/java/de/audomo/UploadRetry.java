package de.audomo;

import android.content.*;

public final class UploadRetry extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        UploadQueue.get(c).retry(i.getLongExtra("job", -1));
    }
}
