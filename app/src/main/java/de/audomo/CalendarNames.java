package de.audomo;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Instances;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

final class CalendarNames {
    static final String DEFAULT = "{datum}_{uhrzeit}_{termin}";
    private static final ExecutorService reader = Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService timeout =
            Executors.newSingleThreadScheduledExecutor();

    record Event(long id, long calendar, String title, long begin, long end) {}

    record Match(Event event, String warning) {}

    static String describe(Match match) {
        if (!match.warning().isEmpty()) return match.warning();
        Event event = match.event();
        if (event == null) return "Kein Termin zugeordnet";
        var clock = DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault());
        String title =
                event.title() == null || event.title().isBlank()
                        ? "Termin ohne Titel"
                        : event.title();
        return title
                + " · "
                + clock.format(java.time.Instant.ofEpochMilli(event.begin()))
                + " bis "
                + clock.format(java.time.Instant.ofEpochMilli(event.end()));
    }

    static List<Long> ids(Context context) {
        List<Long> result = new ArrayList<>();
        String order = Profile.prefs(context).getString("calendars", "");
        for (String raw : order.split(",")) if (!raw.isEmpty()) result.add(Long.parseLong(raw));
        return result;
    }

    static String name(String template, OffsetDateTime time, String title) {
        String event = title == null || title.isBlank() ? "Aufnahme" : title.strip();
        java.util.regex.Matcher tokens =
                java.util.regex.Pattern.compile("\\{(datum|uhrzeit|termin)\\}").matcher(template);
        StringBuffer out = new StringBuffer();
        while (tokens.find()) {
            String value =
                    switch (tokens.group(1)) {
                        case "datum" -> time.toLocalDate().toString();
                        case "uhrzeit" -> time.format(DateTimeFormatter.ofPattern("HH-mm-ss"));
                        default -> event;
                    };
            tokens.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(value));
        }
        tokens.appendTail(out);
        return SessionStore.fileName(out.toString(), time);
    }

    static String template(Context c) {
        return Profile.prefs(c).getString("template", DEFAULT);
    }

    static Event choose(List<Event> events, List<Long> order, long now, long before, long after) {
        return events.stream()
                .filter(e ->
                        order.contains(e.calendar)
                                && now >= e.begin - before
                                && now < e.end + after)
                .min(
                        Comparator.<Event>comparingInt(e -> now >= e.begin && now < e.end ? 0 : 1)
                                .thenComparingInt(e -> order.indexOf(e.calendar))
                                .thenComparingLong(
                                        e -> Math.max(0, Math.max(e.begin - now, now - e.end)))
                                .thenComparingLong(Event::begin)
                                .thenComparingLong(Event::id))
                .orElse(null);
    }

    static CompletableFuture<Match> at(Context context, OffsetDateTime time) {
        List<Long> order = ids(context);
        if (order.isEmpty()) return CompletableFuture.completedFuture(new Match(null, ""));
        if (context.checkSelfPermission(Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED)
            return CompletableFuture.completedFuture(new Match(null, "Kalenderzugriff fehlt"));
        long now = time.toInstant().toEpochMilli();
        long before = Profile.prefs(context).getInt("before", 0) * 60000L;
        long after = Profile.prefs(context).getInt("after", 0) * 60000L;
        CancellationSignal cancel = new CancellationSignal();
        CompletableFuture<Match> future = new CompletableFuture<>();
        ScheduledFuture<?> watchdog =
                timeout.schedule(
                        () -> {
                            future.complete(new Match(null, "Kalender antwortet nicht"));
                            cancel.cancel();
                        },
                        1500,
                        TimeUnit.MILLISECONDS);
        reader.execute(() -> {
            try {
                Uri.Builder uri = Instances.CONTENT_URI.buildUpon();
                ContentUris.appendId(uri, now - after - 1);
                ContentUris.appendId(uri, now + before + 1);
                String placeholders = String.join(",", Collections.nCopies(order.size(), "?"));
                String where =
                        Instances.ALL_DAY
                                + "=0 AND "
                                + CalendarContract.Events.DELETED
                                + "=0 AND ("
                                + Instances.STATUS
                                + " IS NULL OR "
                                + Instances.STATUS
                                + "!="
                                + CalendarContract.Events.STATUS_CANCELED
                                + ") AND "
                                + Instances.CALENDAR_ID
                                + " IN ("
                                + placeholders
                                + ")";
                String[] arguments = order.stream().map(String::valueOf).toArray(String[]::new);
                List<Event> events = new ArrayList<>();
                try (Cursor rows =
                        context.getContentResolver()
                                .query(
                                        uri.build(),
                                        new String[] {
                                            Instances.EVENT_ID,
                                            Instances.CALENDAR_ID,
                                            Instances.TITLE,
                                            Instances.BEGIN,
                                            Instances.END
                                        },
                                        where,
                                        arguments,
                                        null,
                                        cancel)) {
                    if (rows == null) throw new IllegalStateException();
                    while (rows.moveToNext())
                        events.add(
                                new Event(
                                        rows.getLong(0),
                                        rows.getLong(1),
                                        rows.getString(2),
                                        rows.getLong(3),
                                        rows.getLong(4)));
                }
                future.complete(new Match(choose(events, order, now, before, after), ""));
            } catch (RuntimeException failure) {
                future.complete(new Match(null, "Kalender nicht lesbar"));
            } finally {
                watchdog.cancel(false);
            }
        });
        return future;
    }
}
