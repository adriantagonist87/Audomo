package de.audomo;

import static org.junit.Assert.*;

import android.content.*;
import android.os.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.*;
import java.nio.file.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;

@RunWith(AndroidJUnit4.class)
public class ShareDeviceTest {
    @Test
    public void otherPackageReadsOnlyGrantedSyntheticAudio() throws Exception {
        var inst = InstrumentationRegistry.getInstrumentation();
        Context c = inst.getTargetContext();
        File root = new File(c.getFilesDir(), "sessions");
        Files.createDirectories(root.toPath());
        File dir = Files.createTempDirectory(root.toPath(), ".qa-share-").toFile();
        try {
            List<SessionStore.Entry> entries = new ArrayList<>();
            List<String> hashes = new ArrayList<>();
            for (String name : List.of("testsignal.mp3", "testsignal.ogg")) {
                File data = new File(dir, name);
                try (InputStream in = inst.getContext().getAssets().open(name)) {
                    Files.copy(in, data.toPath());
                }
                entries.add(new SessionStore.Entry(data, 48000, OffsetDateTime.now(), name, "", 0));
                hashes.add(LibraryFiles.digest(new FileInputStream(data)));
            }
            for (String action :
                    List.of(Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) {
                int count = action.equals(Intent.ACTION_SEND_MULTIPLE) ? 2 : 1;
                Intent i =
                        LibraryActions.contentIntent(
                                c, entries.subList(0, count), !action.equals(Intent.ACTION_VIEW));
                assertEquals(action, i.getAction());
                ArrayBlockingQueue<Bundle> result = new ArrayBlockingQueue<>(1);
                i.putExtra(
                        "qa",
                        new ResultReceiver(new Handler(Looper.getMainLooper())) {
                            @Override
                            protected void onReceiveResult(int code, Bundle data) {
                                result.offer(data);
                            }
                        });
                i.setClassName(inst.getContext().getPackageName(), "de.audomo.ShareTestReceiver")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                c.startActivity(i);
                Bundle reply = result.poll(15, TimeUnit.SECONDS);
                assertNotNull(reply);
                assertEquals(hashes.subList(0, count), reply.getStringArrayList("hashes"));
                assertTrue(reply.getString("type").startsWith("audio/"));
            }
        } finally {
            try (var paths = Files.walk(dir.toPath())) {
                for (Path f :
                        paths.sorted(Comparator.reverseOrder())
                                .collect(java.util.stream.Collectors.toList()))
                    Files.deleteIfExists(f);
            }
        }
    }
}
