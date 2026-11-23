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
        default void beforeCommit() throws IOException { check(0); }
    }

    final CloudConfig config;
    final Progress progress;

    RemoteFiles(CloudConfig config, Progress progress) {
        this.config = config;
        this.progress = progress;
    }

    abstract void test() throws Exception;

    abstract void ensureFolder(String path) throws Exception;

    record Child(String name, boolean directory, long bytes) {}
    abstract List<Child> children(String directory) throws Exception;
    abstract boolean read(String path, OutputStream out) throws Exception;

    String hash(String path) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (OutputStream sink = new java.security.DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
            if (!read(path, sink)) return null;
        }
        return android.util.Base64.encodeToString(digest.digest(), android.util.Base64.NO_WRAP);
    }

    void downloadVerified(String path, String expected, File temp) throws Exception {
        try (FileOutputStream out = new FileOutputStream(temp)) {
            if (!read(path, out)) throw new IOException("Cloudkopie am gespeicherten Pfad nicht gefunden");
            out.getFD().sync();
        }
        if (!LibraryFiles.digest(new FileInputStream(temp)).equals(expected))
            throw new IOException("Cloudkopie stimmt nicht mit der bestätigten Aufnahme überein");
    }

    void copyTo(InputStream raw, OutputStream out) throws IOException {
        try (InputStream in = guarded(raw)) { in.transferTo(out); }
    }

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
                    long remaining = contentLength();
                    if (remaining == 0) progress.beforeCommit();
                    for (int n; (n = in.read(b)) != -1; ) {
                        remaining -= n;
                        if (remaining == 0) progress.beforeCommit();
                        sink.write(b, 0, n);
                    }
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

        List<Child> children(String directory) throws Exception {
            Request.Builder req = request(directory).header("Depth", "1").method("PROPFIND",
                    RequestBody.create(MediaType.get("application/xml"),
                            "<propfind xmlns='DAV:'><prop><resourcetype/><getcontentlength/></prop></propfind>"));
            List<Child> result = new ArrayList<>();
            try (Response response = client.newCall(req.build()).execute()) {
                require(response);
                org.xmlpull.v1.XmlPullParser xml = android.util.Xml.newPullParser();
                xml.setFeature(org.xmlpull.v1.XmlPullParser.FEATURE_PROCESS_DOCDECL, false);
                xml.setInput(guarded(response.body().byteStream()), null);
                String href = null, status = "";
                boolean dir = false, confirmed = false;
                long size = -1;
                for (int event = xml.getEventType(); event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT; event = xml.next()) {
                    String tag = xml.getName();
                    if (!"DAV:".equals(xml.getNamespace())) continue;
                    if (event == org.xmlpull.v1.XmlPullParser.START_TAG) {
                        if ("response".equals(tag)) { href = null; dir = false; size = -1; confirmed = false; }
                        else if ("href".equals(tag)) href = xml.nextText();
                        else if ("collection".equals(tag)) dir = true;
                        else if ("getcontentlength".equals(tag)) {
                            try { size = Long.parseLong(xml.nextText()); } catch (NumberFormatException ignored) { size = -1; }
                        } else if ("status".equals(tag)) {
                            status = xml.nextText();
                            if (status.matches("HTTP/[^ ]+ 2[0-9][0-9].*")) confirmed = true;
                        }
                    } else if (event == org.xmlpull.v1.XmlPullParser.END_TAG && "response".equals(tag) && href != null) {
                        if (!confirmed) throw new IOException("WebDAV-Ordner nicht vollständig lesbar");
                        HttpUrl address = request(directory.equals("/") ? "/" : directory + "/").build().url();
                        HttpUrl resource = address.newBuilder().addPathSegment("").build().resolve(href);
                        if (resource == null || !address.host().equals(resource.host()) || address.port() != resource.port()) continue;
                        List<String> parts = new ArrayList<>(resource.pathSegments());
                        if (!parts.isEmpty() && parts.get(parts.size()-1).isEmpty()) parts.remove(parts.size()-1);
                        if (parts.stream().anyMatch(part -> part.contains("/"))) continue;
                        String path = "/" + String.join("/", parts);
                        if (!path.equals(directory) && parent(path).equals(directory)) result.add(new Child(leaf(path), dir, size));
                    }
                }
            }
            return result;
        }

        boolean read(String path, OutputStream out) throws Exception {
            try (Response r = client.newCall(request(path).get().build()).execute()) {
                if (r.code() == 404) return false;
                require(r);
                copyTo(r.body().byteStream(), out);
                return true;
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
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) {
                if (ftp.getReplyCode() == 550 && !parent(path).equals("/") && !exists(parent(path))) return false;
                throw error();
            }
            return Arrays.stream(files).anyMatch(f -> f.getName().equals(leaf(path)));
        }

        List<Child> children(String directory) throws Exception {
            FTPFile[] files = ftp.listFiles(directory);
            if (!FTPReply.isPositiveCompletion(ftp.getReplyCode())) throw error();
            List<Child> result = new ArrayList<>();
            for (FTPFile file : files)
                if (file.isDirectory() || file.isFile())
                    result.add(new Child(file.getName(), file.isDirectory(), file.getSize()));
            return result;
        }

        boolean read(String path, OutputStream out) throws Exception {
            if (!exists(path)) return false;
            InputStream in = ftp.retrieveFileStream(path);
            if (in == null) throw error();
            copyTo(in, out);
            if (!ftp.completePendingCommand()) throw error();
            return true;
        }

        void put(File local, String path, boolean replace, String expected) throws Exception {
            String tmp = parent(path) + "/.audomo-" + UUID.randomUUID() + ".part";
            try {
                try (InputStream in = guarded(new FileInputStream(local))) {
                    if (!ftp.storeFile(tmp, in)) throw error();
                }
                if (!Objects.equals(hash(tmp), LibraryFiles.digest(guarded(new FileInputStream(local)))))
                    throw new IOException("Übertragene Arbeitsdatei fehlerhaft");
                String current = hash(path);
                if (current != null && (!replace || !current.equals(expected)))
                    throw new Conflict();
                progress.beforeCommit();
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

        List<Child> children(String directory) throws Exception {
            List<Child> result = new ArrayList<>();
            for (ChannelSftp.LsEntry entry : channel.ls(literal(directory))) {
                SftpATTRS attrs = entry.getAttrs();
                if (attrs.isDir() || attrs.isReg())
                    result.add(new Child(entry.getFilename(), attrs.isDir(), attrs.getSize()));
            }
            return result;
        }

        boolean read(String path, OutputStream out) throws Exception {
            try {
                copyTo(channel.get(literal(path)), out);
                return true;
            } catch (SftpException e) {
                if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) return false;
                throw e;
            }
        }

        void put(File local, String path, boolean replace, String expected) throws Exception {
            String tmp = parent(path) + "/.audomo-" + UUID.randomUUID() + ".part";
            try {
                try (InputStream in = guarded(new FileInputStream(local))) {
                    channel.put(in, literal(tmp));
                }
                if (!Objects.equals(hash(tmp), LibraryFiles.digest(guarded(new FileInputStream(local)))))
                    throw new IOException("SFTP-Arbeitsdatei fehlerhaft");
                String current = hash(path);
                if (current != null && (!replace || !current.equals(expected)))
                    throw new Conflict();
                progress.beforeCommit();
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
