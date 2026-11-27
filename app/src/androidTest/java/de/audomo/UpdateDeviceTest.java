package de.audomo;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class UpdateDeviceTest {
    private static final String HASH = "0".repeat(64);

    @Test public void onlyAcceptsTheOfficialStableApk() throws Exception {
        GitHubUpdate.Release r = new GitHubUpdate.Release("1.10.0", 123, HASH);
        JSONObject asset = new JSONObject().put("name", r.fileName()).put("size", r.size())
                .put("digest", "sha256:" + HASH).put("browser_download_url", r.url());
        JSONObject release = new JSONObject().put("tag_name", "v1.10.0").put("draft", false)
                .put("prerelease", false).put("assets", new JSONArray().put(asset));
        assertEquals(r, GitHubUpdate.parse(release));
        for (String key : new String[]{"draft", "prerelease"}) {
            release.put(key, true);
            assertThrows(IOException.class, () -> GitHubUpdate.parse(release));
            release.put(key, false);
        }
        asset.put("browser_download_url", "https://example.org/" + r.fileName());
        assertThrows(IOException.class, () -> GitHubUpdate.parse(release));
        asset.put("browser_download_url", r.url()).put("digest", JSONObject.NULL);
        assertThrows(IOException.class, () -> GitHubUpdate.parse(release));
        asset.put("name", "another.apk");
        assertThrows(IOException.class, () -> GitHubUpdate.parse(release));
    }

    @Test public void rejectsCorruptDataBeforeInstaller() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        File sample = File.createTempFile("update-test", ".apk", c.getCacheDir());
        try {
            byte[] content = {1, 2, 3};
            Files.write(sample.toPath(), content);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            for (GitHubUpdate.Release r : new GitHubUpdate.Release[]{
                    new GitHubUpdate.Release("9.0.0", 4, digest),
                    new GitHubUpdate.Release("9.0.0", 3, HASH),
                    new GitHubUpdate.Release("9.0.0", 3, digest)}) {
                assertThrows(IOException.class, () -> GitHubUpdate.verify(c, sample, r));
            }
        } finally { Files.deleteIfExists(sample.toPath()); }
    }
}
