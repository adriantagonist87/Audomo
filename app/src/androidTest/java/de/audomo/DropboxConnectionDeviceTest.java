package de.audomo;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Test;

import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.OkHttpClient;
import okhttp3.Response;

/** Explicit, read-only check against the user's configured Dropbox. Reports no paths or tokens. */
public class DropboxConnectionDeviceTest {
    @Test
    public void checkConfiguredFolder() throws Exception {
        var inst = InstrumentationRegistry.getInstrumentation();
        Assume.assumeTrue("true".equals(InstrumentationRegistry.getArguments().getString("liveDropboxReadOnly")));
        Context c = inst.getTargetContext();
        CloudConfig t = CloudConfig.byId(c, Profile.prefs(c).getString("defaultTarget", ""));
        Assume.assumeTrue(t.protocol().equals("Dropbox"));
        ConnectivityManager cm = c.getSystemService(ConnectivityManager.class);
        Network active = cm.getActiveNetwork();
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        StringBuilder info = new StringBuilder();
        info.append("VPN=").append(caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN));
        boolean success = true;
        for (boolean bind : List.of(false, true)) {
            info.append(bind ? " Android-Netz gebunden" : " Standardroute");
            EventListener listener = new EventListener() {
                @Override public void dnsStart(Call call, String domain) { info.append(" DNS"); }
                @Override public void dnsEnd(Call call, String domain, List<InetAddress> addresses) {
                    long ipv4 = addresses.stream().filter(Inet4Address.class::isInstance).count();
                    long ipv6 = addresses.stream().filter(Inet6Address.class::isInstance).count();
                    info.append('(').append(ipv4).append(" IPv4/").append(ipv6).append(" IPv6)");
                }
                @Override public void connectStart(Call call, InetSocketAddress addr, Proxy proxy) {
                    info.append(addr.getAddress() instanceof Inet6Address ? " IPv6" : " IPv4");
                }
                @Override public void secureConnectStart(Call call) { info.append(" TLS"); }
                @Override public void responseHeadersEnd(Call call, Response response) {
                    info.append(" HTTP-").append(response.code());
                }
            };
            OkHttpClient client = DropboxFiles.dropboxHttp(bind ? active : null).newBuilder()
                    .eventListener(listener).callTimeout(40, TimeUnit.SECONDS).build();
            try (DropboxFiles files = new DropboxFiles(t, CloudConfig.secret(c, t.id()), n -> {}, client)) {
                files.test();
                info.append(" Ordner bestätigt.");
            } catch (Exception issue) {
                success = false;
                for (Throwable cause = issue; cause != null; cause = cause.getCause()) {
                    info.append(' ').append(cause.getClass().getSimpleName());
                    if (cause instanceof android.system.ErrnoException errno) info.append('(').append(errno.errno).append(')');
                }
            } finally { client.connectionPool().evictAll(); }
        }
        Bundle status = new Bundle();
        status.putString("stream", info.toString());
        inst.sendStatus(2, status);
        org.junit.Assert.assertTrue(info.toString(), success);
    }
}
