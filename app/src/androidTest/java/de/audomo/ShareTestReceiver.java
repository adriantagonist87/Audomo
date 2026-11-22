package de.audomo;

import android.app.Activity;
import android.net.Uri;
import android.os.*;

import java.io.*;
import java.security.MessageDigest;
import java.util.*;

public class ShareTestReceiver extends Activity {
    @Override
    public void onCreate(Bundle saved) {
        super.onCreate(saved);
        ResultReceiver reply = getIntent().getParcelableExtra("qa", ResultReceiver.class);
        if (reply == null) {
            finish();
            return;
        }
        new Thread(() -> receive(reply)).start();
    }

    private void receive(ResultReceiver reply) {
        try {
            ArrayList<String> hashes = new ArrayList<>();
            var clips = getIntent().getClipData();
            for (int i = 0; i < clips.getItemCount(); i++) {
                Uri uri = clips.getItemAt(i).getUri();
                if (!"de.audomo.audio".equals(uri.getAuthority())) throw new IOException();
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    byte[] b = new byte[8192];
                    for (int n; (n = in.read(b)) != -1; ) sha.update(b, 0, n);
                }
                android.media.MediaPlayer player = new android.media.MediaPlayer();
                try {
                    java.util.concurrent.CountDownLatch end =
                            new java.util.concurrent.CountDownLatch(1);
                    java.util.concurrent.atomic.AtomicBoolean played =
                            new java.util.concurrent.atomic.AtomicBoolean();
                    player.setOnCompletionListener(mp -> {
                        played.set(true);
                        end.countDown();
                    });
                    player.setOnErrorListener((mp, code, extra) -> {
                        end.countDown();
                        return true;
                    });
                    player.setDataSource(this, uri);
                    player.setVolume(0, 0);
                    player.prepare();
                    player.start();
                    if (!end.await(6, java.util.concurrent.TimeUnit.SECONDS) || !played.get())
                        throw new IOException("Testaudio nicht vollständig abgespielt");
                } finally {
                    player.release();
                }
                hashes.add(
                        android.util.Base64.encodeToString(
                                sha.digest(), android.util.Base64.NO_WRAP));
            }
            Bundle b = new Bundle();
            b.putStringArrayList("hashes", hashes);
            b.putString("type", getIntent().getType());
            reply.send(1, b);
        } catch (Exception e) {
            reply.send(0, new Bundle());
        } finally {
            runOnUiThread(this::finish);
        }
    }
}
