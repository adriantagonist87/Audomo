package de.audomo;

import static org.junit.Assert.*;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.*;

import javax.net.ssl.*;

@RunWith(AndroidJUnit4.class)
public class ProtocolDeviceTest {
    private void check(String type) throws Exception {
        var inst = InstrumentationRegistry.getInstrumentation();
        Context ctx = inst.getTargetContext();
        Assume.assumeTrue(
                "Lokale Tests mit synthetischem Zugang",
                android.os.Build.MODEL.contains("sdk_gphone")
                        && "true"
                                .equals(
                                        InstrumentationRegistry.getArguments()
                                                .getString("localServers")));
        File cfg = new File(ctx.getCacheDir(), "qa-protocols.json");
        JSONObject j =
                new JSONObject(
                        new String(Files.readAllBytes(cfg.toPath()), StandardCharsets.UTF_8));
        CloudConfig t =
                new CloudConfig(
                        UUID.randomUUID().toString(),
                        type,
                        j.optString("host", "10.0.2.2"),
                        j.getJSONObject("ports").getInt(type),
                        "qa",
                        "/",
                        type.equals("SFTP") ? j.getString("hostKey") : "");
        File sample = File.createTempFile("qa-protocol-", ".ogg", ctx.getCacheDir());
        try (InputStream in = inst.getContext().getAssets().open("testsignal.ogg")) {
            Files.copy(in, sample.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        String password = j.getString("password");
        RemoteFiles.Progress check = b -> {};
        KeyStore ca = KeyStore.getInstance(KeyStore.getDefaultType());
        ca.load(null);
        ca.setCertificateEntry(
                "fixture",
                CertificateFactory.getInstance("X.509")
                        .generateCertificate(
                                new ByteArrayInputStream(
                                        j.getString("ca").getBytes(StandardCharsets.US_ASCII))));
        TrustManagerFactory factory =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(ca);
        X509TrustManager tm = (X509TrustManager) factory.getTrustManagers()[0];
        SSLContext tls = SSLContext.getInstance("TLS");
        tls.init(null, new TrustManager[] {tm}, null);
        if (type.equals("FTPS") || type.equals("WebDAV"))
            assertThrows(
                    Exception.class,
                    () -> {
                        try (RemoteFiles remote = RemoteFiles.open(t, password, null, check)) {
                            remote.test();
                        }
                    });
        try (RemoteFiles r =
                switch (type) {
                    case "FTPS" -> new RemoteFiles.Ftp(t, password, null, check, tm);
                    case "WebDAV" ->
                            new RemoteFiles.WebDav(
                                    t,
                                    password,
                                    check,
                                    RemoteFiles.http(null)
                                            .newBuilder()
                                            .sslSocketFactory(tls.getSocketFactory(), tm)
                                            .build());
                    default -> RemoteFiles.open(t, password, null, check);
                }) {
            r.test();
            String dir = "/Prüfung-" + UUID.randomUUID();
            r.ensureFolder(dir);
            String path = dir + "/Audio 01.ogg";
            assertNull(r.hash(path));
            r.put(sample, path, false, null);
            String hash = LibraryFiles.digest(new FileInputStream(sample));
            assertEquals(hash, r.hash(path));
            assertThrows(RemoteFiles.Conflict.class, () -> r.put(sample, path, false, null));
            assertThrows(Exception.class, () -> r.deleteIfMatches(path, "falscher Hash"));
            assertEquals(hash, r.hash(path));
            r.put(sample, path, true, hash);
            assertEquals(hash, r.hash(path));
            r.deleteIfMatches(path, hash);
            assertNull(r.hash(path));
        } finally {
            Files.deleteIfExists(sample.toPath());
        }
    }

    @Test
    public void webdav() throws Exception {
        check("WebDAV");
    }

    @Test
    public void ftp() throws Exception {
        check("FTP");
    }

    @Test
    public void ftps() throws Exception {
        check("FTPS");
    }

    @Test
    public void sftp() throws Exception {
        check("SFTP");
    }
}
