package de.audomo;

import android.net.Network;

import com.jcraft.jsch.*;

import okhttp3.*;

import okio.BufferedSink;

import org.apache.commons.net.ftp.*;
import org.json.*;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.*;

abstract class RemoteFiles implements AutoCloseable {
    interface Progress {
        void check(long bytes) throws IOException;
    }

    final CloudConfig config;
    final Progress progress;

    RemoteFiles(CloudConfig config, Progress progress) {
        this.config = config;
        this.progress = progress;
    }

    abstract void test() throws Exception;

    abstract void ensureFolder(String path) throws Exception;

    abstract String hash(String path) throws Exception; // null only for an absent object

    abstract void deleteIfMatches(String path, String expected) throws Exception;

    abstract void put(File source, String path, boolean replace, String expectedHash)
            throws Exception;

    public abstract void close();

    static RemoteFiles open(CloudConfig c, String password, Network network, Progress check)
            throws Exception {
        return switch (c.protocol()) {
            case "SFTP" -> new Sftp(c, password, network, check);
            case "FTP", "FTPS" -> new Ftp(c, password, network, check);
            case "WebDAV" -> new WebDav(c, password, network, check);
            case "Dropbox" -> new DropboxFiles(c, password, network, check);
            default -> throw new IOException("Unbekanntes Protokoll");
        };
    }

    InputStream guarded(InputStream stream) {
        return new FilterInputStream(stream) {
            long bytes;

            public int read(byte[] b, int off, int len) throws IOException {
                int n = super.read(b, off, len);
                if (n > 0) {
                    bytes += n;
                    progress.check(bytes);
                }
                return n;
            }

            public int read() throws IOException {
                int n = super.read();
                if (n >= 0) progress.check(++bytes);
                return n;
            }
        };
    }

    static String parent(String path) {
        int n = path.lastIndexOf('/');
        return n <= 0 ? "/" : path.substring(0, n);
    }

    static String leaf(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    static class Conflict extends IOException {
        Conflict() {
            super("Zielname bereits belegt");
        }
    }

    static OkHttpClient http(Network network) {
        OkHttpClient.Builder builder =
                new OkHttpClient.Builder()
                        .followRedirects(false)
                        .followSslRedirects(false)
                        .retryOnConnectionFailure(false)
                        .connectTimeout(15, TimeUnit.SECONDS)
                        .readTimeout(45, TimeUnit.SECONDS)
                        .writeTimeout(45, TimeUnit.SECONDS);
        if (network != null)
            builder.socketFactory(network.getSocketFactory())
                    .dns(host -> Arrays.asList(network.getAllByName(host)));
        return builder.build();
    }

    RequestBody body(File file) {
        return new RequestBody() {
            public MediaType contentType() {
                return MediaType.get("application/octet-stream");
            }

            public long contentLength() {
                return file.length();
            }

            public void writeTo(BufferedSink sink) throws IOException {
                try (InputStream in = guarded(new FileInputStream(file))) {
                    byte[] b = new byte[65536];
                    for (int n; (n = in.read(b)) != -1; ) sink.write(b, 0, n);
                }
            }
        };
    }

    static void require(Response r) throws IOException {
        if (!r.isSuccessful()) throw new IOException("Serverantwort " + r.code());
    }

    static final class WebDav extends RemoteFiles {
        final OkHttpClient client;
        final String auth;

        WebDav(CloudConfig c, String pass, Network net, Progress check) {
            this(c, pass, check, http(net));
        }

        WebDav(CloudConfig c, String pass, Progress check, OkHttpClient secureClient) {
            super(c, check);
            client = secureClient;
            auth = Credentials.basic(c.user(), pass, StandardCharsets.UTF_8);
        }

        Request.Builder request(String path) {
            HttpUrl.Builder url =
                    new HttpUrl.Builder().scheme("https").host(config.host()).port(config.port());
            for (String p : CloudConfig.path(path).split("/"))
                if (!p.isEmpty()) url.addPathSegment(p);
            return new Request.Builder().url(url.build()).header("Authorization", auth);
        }

        void test() throws Exception {
            try (Response r =
                    client.newCall(
                                    request(config.base())
                                            .header("Depth", "0")
                                            .method(
                                                    "PROPFIND",
                                                    RequestBody.create(null, new byte[0]))
                                            .build())
                            .execute()) {
                require(r);
            }
        }

        void ensureFolder(String path) throws Exception {
            if (path.equals("/")) return;
            try (Response r =
                    client.newCall(request(path).method("MKCOL", null).build()).execute()) {
                if (r.code() != 405) require(r);
            }
        }

        String hash(String path) throws Exception {
            try (Response r = client.newCall(request(path).get().build()).execute()) {
                if (r.code() == 404) return null;
                require(r);
                return LibraryFiles.digest(guarded(r.body().byteStream()));
            }
        }

        void put(File f, String path, boolean replace, String expected) throws Exception {
            Request.Builder req = request(path);
            if (!replace) req.header("If-None-Match", "*");
            else {
                try (Response r = client.newCall(request(path).get().build()).execute()) {
                    require(r);
                    String etag = r.header("ETag");
                    if (etag == null
                            || etag.startsWith("W/")
                            || !Objects.equals(
                                    expected, LibraryFiles.digest(guarded(r.body().byteStream()))))
                        throw new Conflict();
                    req.header("If-Match", etag);
                }
            }
            try (Response r = client.newCall(req.put(body(f)).build()).execute()) {
                if (r.code() == 412 || r.code() == 409) throw new Conflict();
                require(r);
            }
        }

        void deleteIfMatches(String path, String expected) throws Exception {
            String etag;
            try (Response r = client.newCall(request(path).get().build()).execute()) {
                if (r.code() == 404) return;
                require(r);
                etag = r.header("ETag");
                if (etag == null
                        || etag.startsWith("W/")
                        || !expected.equals(LibraryFiles.digest(guarded(r.body().byteStream()))))
                    throw new IOException("Cloudkopie wurde extern geändert");
            }
            try (Response r =
                    client.newCall(request(path).header("If-Match", etag).delete().build())
                            .execute()) {
                if (r.code() != 404) require(r);
            }
        }

        public void close() {
            client.connectionPool().evictAll();
        }
    }

    static final class Ftp extends RemoteFiles {
        final FTPClient ftp;

        Ftp(CloudConfig c, String pass, Network network, Progress check) throws Exception {
            this(c, pass, network, check, null);
        }

        Ftp(
                CloudConfig c,
                String pass,
                Network network,
                Progress check,
                X509TrustManager trustedAuthority)
                throws Exception {
            super(c, check);
            if (c.protocol().equals("FTPS")) {
                TrustManagerFactory factory =
                        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                factory.init((KeyStore) null);
                FTPSClient tls = new FTPSClient(false);
                tls.setTrustManager(
                        trustedAuthority == null
                                ? factory.getTrustManagers()[0]
                                : trustedAuthority);
                tls.setEndpointCheckingEnabled(true);
                ftp = tls;
            } else ftp = new FTPClient();
            try {
                ftp.setControlEncoding("UTF-8");
                if (network != null) ftp.setSocketFactory(network.getSocketFactory());
                ftp.setConnectTimeout(15000);
                ftp.setDefaultTimeout(45000);
                ftp.setDataTimeout(java.time.Duration.ofSeconds(45));
                ftp.connect(c.host(), c.port());
                if (!ftp.login(c.user(), pass)) throw new IOException("FTP-Anmeldung abgelehnt");
                if (ftp instanceof FTPSClient tls) {
                    tls.execPBSZ(0);
                    tls.execPROT("P");
                }
                ftp.enterLocalPassiveMode();
                if (!ftp.setFileType(FTP.BINARY_FILE_TYPE))
                    throw new IOException("Binärmodus abgelehnt");
            } catch (Exception e) {
                close();
                throw e;
            }
        }

        void test() throws Exception {
            if (!ftp.changeWorkingDirectory(config.base())) throw error();
        }

        IOException error() {
            return new IOException("FTP-Serverantwort " + ftp.getReplyCode());
        }

        void ensureFolder(String p) throws Exception {
            if (!ftp.changeWorkingDirectory(p) && !ftp.makeDirectory(p)) throw error();
        }

        boolean exists(String path) throws Exception {
            if (ftp.mlistFile(path) != null) return true;
            FTPFile[] files = ftp.listFiles(parent(path));
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) throw error();
            return Arrays.stream(files).anyMatch(f -> f.getName().equals(leaf(path)));
        }

        String hash(String path) throws Exception {
            if (!exists(path)) return null;
            InputStream in = ftp.retrieveFileStream(path);
            if (in == null) throw error();
            String hash = LibraryFiles.digest(guarded(in));
            if (!ftp.completePendingCommand()) throw error();
            return hash;
        }

        void put(File local, String path, boolean replace, String expected) throws Exception {
            String tmp = parent(path) + "/.audomo-" + UUID.randomUUID() + ".part";
            try {
                try (InputStream in = guarded(new FileInputStream(local))) {
                    if (!ftp.storeFile(tmp, in)) throw error();
                }
                if (!Objects.equals(hash(tmp), LibraryFiles.digest(new FileInputStream(local))))
                    throw new IOException("Übertragene Arbeitsdatei fehlerhaft");
                String current = hash(path);
                if (current != null && (!replace || !current.equals(expected)))
                    throw new Conflict();
                if (!ftp.rename(tmp, path)) throw error();
            } finally {
                if (ftp.isConnected()) ftp.deleteFile(tmp);
            }
        }

        void deleteIfMatches(String path, String expected) throws Exception {
            String actual = hash(path);
            if (actual == null) return;
            if (!actual.equals(expected)) throw new IOException("Cloudkopie wurde extern geändert");
            if (!ftp.deleteFile(path)) throw error();
        }

        public void close() {
            try {
                if (ftp.isConnected()) ftp.disconnect();
            } catch (IOException ignored) {
            }
        }
    }

    static final class Sftp extends RemoteFiles {
        final Session session;
        ChannelSftp channel;

        Sftp(CloudConfig c, String pass, Network network, Progress check) throws Exception {
            super(c, check);
            JSch jsch = new JSch();
            String host = c.port() == 22 ? c.host() : "[" + c.host() + "]:" + c.port();
            jsch.setKnownHosts(
                    new ByteArrayInputStream(
                            (host + " " + c.hostKey() + "\n").getBytes(StandardCharsets.UTF_8)));
            session = jsch.getSession(c.user(), c.host(), c.port());
            session.setPassword(pass);
            session.setConfig("StrictHostKeyChecking", "yes");
            session.setConfig("PreferredAuthentications", "password,keyboard-interactive");
            if (network != null)
                session.setSocketFactory(
                        new com.jcraft.jsch.SocketFactory() {
                            public Socket createSocket(String h, int p) throws IOException {
                                Socket s = network.getSocketFactory().createSocket();
                                s.connect(new InetSocketAddress(network.getByName(h), p), 15000);
                                return s;
                            }

                            public InputStream getInputStream(Socket s) throws IOException {
                                return s.getInputStream();
                            }

                            public OutputStream getOutputStream(Socket s) throws IOException {
                                return s.getOutputStream();
                            }
                        });
            try {
                session.setTimeout(45000);
                session.connect(15000);
                channel = (ChannelSftp) session.openChannel("sftp");
                channel.connect(15000);
            } catch (Exception e) {
                close();
                throw new IOException("SFTP-Verbindung oder Hostschlüssel nicht bestätigt", e);
            }
        }

        String literal(String path) {
            return path.replace("\\", "\\\\").replace("*", "\\*").replace("?", "\\?");
        }

        void test() throws Exception {
            channel.cd(literal(config.base()));
        }

        void ensureFolder(String path) throws Exception {
            try {
                if (!channel.stat(literal(path)).isDir())
                    throw new IOException("Ziel ist keine Mappe");
            } catch (SftpException e) {
                if (e.id != ChannelSftp.SSH_FX_NO_SUCH_FILE) throw e;
                channel.mkdir(path);
            }
        }

        String hash(String path) throws Exception {
            try {
                return LibraryFiles.digest(guarded(channel.get(literal(path))));
            } catch (SftpException e) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) return null;
                throw e;
            }
        }

        void put(File local, String path, boolean replace, String expected) throws Exception {
            String tmp = parent(path) + "/.audomo-" + UUID.randomUUID() + ".part";
            try {
                try (InputStream in = guarded(new FileInputStream(local))) {
                    channel.put(in, literal(tmp));
                }
                if (!Objects.equals(hash(tmp), LibraryFiles.digest(new FileInputStream(local))))
                    throw new IOException("SFTP-Arbeitsdatei fehlerhaft");
                String current = hash(path);
                if (current != null && (!replace || !current.equals(expected)))
                    throw new Conflict();
                channel.rename(literal(tmp), literal(path));
            } finally {
                try {
                    channel.rm(literal(tmp));
                } catch (SftpException ignored) {
                }
            }
        }

        void deleteIfMatches(String path, String expected) throws Exception {
            String actual = hash(path);
            if (actual == null) return;
            if (!actual.equals(expected)) throw new IOException("Cloudkopie wurde extern geändert");
            channel.rm(literal(path));
        }

        public void close() {
            if (channel != null) channel.disconnect();
            session.disconnect();
        }
    }
}
