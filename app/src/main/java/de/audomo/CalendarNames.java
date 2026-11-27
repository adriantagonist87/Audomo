package de.audomo;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Instances;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

final class CalendarNames {
    static final String DEFAULT = "{datum}_{uhrzeit}_{termin}";
    private static final ExecutorService reader = Executors.newSingleThreadExecutor();
    private static final ScheduledExecutorService timeout =
            Executors.newSingleThreadScheduledExecutor();

    record Event(
            long id,
            long calendar,
            String title,
            long begin,
            long end,
            String calendarName,
            boolean allDay) {
        Event(long id, long calendar, String title, long begin, long end, String calendarName) {
            this(id, calendar, title, begin, end, calendarName, false);
        }
    }

    record Appointment(Event event, String location, int color) {}

    record Day(List<Appointment> appointments, String warning) {}

    static String chosenName(String template, OffsetDateTime time, Event event) {
        return name(template.contains("{termin}") ? template : template + "_{termin}", time, event);
    }

    enum FolderMode {
        CURRENT("Aktueller Bibliotheksordner"),
        FIXED("Fester Ordner"),
        CALENDAR("Unterordner je Kalender");
        final String label;

        FolderMode(String label) {
            this.label = label;
        }
    }

    static FolderMode folderMode(Context context) {
        return FolderMode.valueOf(
                Profile.prefs(context).getString("recordingFolderMode", "CURRENT"));
    }

    static String baseFolder(Context context, FolderMode mode) {
        return Profile.prefs(context)
                .getString(mode == FolderMode.CURRENT ? "folder" : "recordingBase", "");
    }

    static String folder(String base, FolderMode mode, Event event) {
        if (mode != FolderMode.CALENDAR || event == null) return base;
        String segment = SessionStore.cleanComponent(event.calendarName());
        return segment.isEmpty() ? base : base.isEmpty() ? segment : base + "/" + segment;
    }

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
        if (event.allDay()) return title + " · Ganztägig";
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

    static String name(String template, OffsetDateTime time, Event event) {
        String calendar = event == null ? "" : SessionStore.cleanComponent(event.calendarName());
        String title = event == null || event.title() == null ? "" : event.title().strip();
        java.util.regex.Matcher tokens =
                java.util.regex.Pattern.compile("\\{(datum|uhrzeit|termin|kalender)\\}")
                        .matcher(template);
        StringBuffer out = new StringBuffer();
        while (tokens.find()) {
            String value =
                    switch (tokens.group(1)) {
                        case "datum" -> time.toLocalDate().toString();
                        case "uhrzeit" -> time.format(DateTimeFormatter.ofPattern("HH-mm-ss"));
                        case "kalender" -> calendar.isEmpty() ? "-" : calendar;
                        default -> title.isEmpty() ? "Aufnahme" : title;
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
                List<Event> events =
                        query(context, order, now - after - 1, now + before + 1, false, cancel)
                                .stream()
                                .map(Appointment::event)
                                .collect(java.util.stream.Collectors.toList());
                future.complete(new Match(choose(events, order, now, before, after), ""));
            } catch (RuntimeException failure) {
                future.complete(new Match(null, "Kalender nicht lesbar"));
            } finally {
                watchdog.cancel(false);
            }
        });
        return future;
    }

    static Day day(Context context, LocalDate date, ZoneId zone, CancellationSignal cancel) {
        List<Long> order = ids(context);
        if (order.isEmpty()) return new Day(List.of(), "Keine Kalender ausgewählt");
        long from = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long until = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        // Android stores all-day dates at UTC midnight, independently of the device's zone.
        long utc = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long utcEnd = utc + Duration.ofDays(1).toMillis();
        List<Appointment> result =
                query(context, order, Math.min(from, utc), Math.max(until, utcEnd), true, cancel);
        result.removeIf(a -> {
            Event e = a.event();
            long start = e.allDay() ? utc : from, end = e.allDay() ? utcEnd : until;
            return e.begin() >= end || (e.end() > e.begin() ? e.end() <= start : e.begin() < start);
        });
        result.sort(
                Comparator.comparingLong((Appointment a) -> a.event().begin())
                        .thenComparingInt(a -> order.indexOf(a.event().calendar()))
                        .thenComparingLong(a -> a.event().id()));
        return new Day(result, "");
    }

    private static List<Appointment> query(
            Context context,
            List<Long> order,
            long start,
            long end,
            boolean allDay,
            CancellationSignal cancel) {
        Uri.Builder uri = Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri, start);
        ContentUris.appendId(uri, end);
        String placeholders = String.join(",", Collections.nCopies(order.size(), "?"));
        String where =
                (allDay ? "" : Instances.ALL_DAY + "=0 AND ")
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
        String[] args = order.stream().map(String::valueOf).toArray(String[]::new);
        List<Appointment> events = new ArrayList<>();
        try (Cursor rows =
                context.getContentResolver()
                        .query(
                                uri.build(),
                                new String[] {
                                    Instances.EVENT_ID,
                                    Instances.CALENDAR_ID,
                                    Instances.TITLE,
                                    Instances.BEGIN,
                                    Instances.END,
                                    Instances.CALENDAR_DISPLAY_NAME,
                                    Instances.ALL_DAY,
                                    Instances.EVENT_LOCATION,
                                    Instances.DISPLAY_COLOR
                                },
                                where,
                                args,
                                null,
                                cancel)) {
            if (rows == null) throw new IllegalStateException("Kalender nicht lesbar");
            while (rows.moveToNext()) {
                Event e =
                        new Event(
                                rows.getLong(0),
                                rows.getLong(1),
                                rows.getString(2),
                                rows.getLong(3),
                                rows.getLong(4),
                                rows.getString(5),
                                rows.getInt(6) != 0);
                events.add(
                        new Appointment(
                                e,
                                rows.getString(7),
                                rows.isNull(8) ? MainActivity.ACCENT : rows.getInt(8)));
            }
        }
        return events;
    }
}
