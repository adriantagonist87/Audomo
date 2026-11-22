package de.audomo;

import static org.junit.Assert.*;

import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.*;
import org.junit.runner.RunWith;

import java.util.*;

@RunWith(AndroidJUnit4.class)
public class RadioDeviceTest {
    private static View startButton(View v) {
        if ("Aufnahme starten"
                .contentEquals(v.getContentDescription() == null ? "" : v.getContentDescription()))
            return v;
        if (v instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = startButton(group.getChildAt(i));
                if (found != null) return found;
            }
        return null;
    }

    @Test
    public void timedBackgroundRecording() throws Exception {
        Assume.assumeTrue(
                "Nur explizit gestarteter Mikrofontest",
                "30".equals(InstrumentationRegistry.getArguments().getString("radioMinutes")));
        Instrumentation inst = InstrumentationRegistry.getInstrumentation();
        Context ctx = inst.getTargetContext();
        Audomo app = (Audomo) ctx.getApplicationContext();
        MainActivity activity =
                (MainActivity)
                        inst.startActivitySync(
                                new Intent(ctx, MainActivity.class)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Set<String> existing = new HashSet<>();
        for (java.io.File dir : app.sessions.directories()) existing.add(dir.getName());
        long limit = SystemClock.elapsedRealtime() + 12000;
        while ((!activity.hasWindowFocus() || !app.ready())
                && SystemClock.elapsedRealtime() < limit) SystemClock.sleep(100);
        assertTrue(
                "Audomo ist nicht sichtbar oder nicht bereit",
                activity.hasWindowFocus() && app.ready());
        long start = SystemClock.elapsedRealtime();
        inst.runOnMainSync(() -> {
            View play = startButton(activity.getWindow().getDecorView());
            assertNotNull(play);
            assertTrue(play.performClick());
        });
        String token = null;
        try {
            while (app.state != Audomo.State.RECORDING
                    && SystemClock.elapsedRealtime() - start < 12000) SystemClock.sleep(100);
            assertEquals(Audomo.State.RECORDING, app.state);
            token = app.token;
            inst.runOnMainSync(() -> activity.moveTaskToBack(true));
            for (int minute = 1; minute <= 30; minute++) {
                long deadline = start + minute * 60000L;
                while (SystemClock.elapsedRealtime() < deadline
                        && token.equals(app.token)
                        && app.state == Audomo.State.RECORDING)
                    SystemClock.sleep(Math.min(1000, deadline - SystemClock.elapsedRealtime()));
                assertEquals("Aufnahme unterbrochen", Audomo.State.RECORDING, app.state);
                android.util.Log.i(
                        "AudomoRadioTest", "Minute " + minute + ", frames=" + app.frames);
            }
        } finally {
            if ((token == null || token.equals(app.token)) && app.active())
                inst.runOnMainSync(() -> app.command("STOP", null));
            long until = SystemClock.elapsedRealtime() + 20000;
            while (!app.ready() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(100);
        }
        assertEquals(Audomo.State.SAVED, app.state);
        List<SessionStore.Entry> newFiles = new ArrayList<>();
        for (SessionStore.Entry e : app.sessions.list())
            if (!existing.contains(e.file().getParentFile().getName())) newFiles.add(e);
        assertEquals(1, newFiles.size());
        SessionStore.Entry e = newFiles.get(0);
        SessionStore.verify(e.file());
        Bundle result = new Bundle();
        result.putString("radioId", e.file().getParentFile().getName());
        result.putLong("radioFrames", e.frames());
        result.putLong("radioBytes", e.file().length());
        inst.sendStatus(0, result);
        assertEquals(1800.0, e.frames() / 48000., 3.0);
    }
}
