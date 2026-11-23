package de.audomo;

import static org.junit.Assert.*;

import android.Manifest;
import android.content.*;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.*;
import java.util.*;

@RunWith(AndroidJUnit4.class)
public class CalendarProviderDeviceTest {
    @Test
    public void activeRecurrenceAndProviderFilters() throws Exception {
        var inst = InstrumentationRegistry.getInstrumentation();
        Context ctx = inst.getTargetContext();
        org.junit.Assume.assumeTrue(android.os.Build.MODEL.contains("sdk_gphone"));
        String owner = "audomo.qa." + UUID.randomUUID();
        String opts = "qa-calendar-" + UUID.randomUUID();
        Context isolated =
                new ContextWrapper(ctx) {
                    public SharedPreferences getSharedPreferences(String name, int mode) {
                        return ctx.getSharedPreferences(opts, mode);
                    }
                };
        inst.getUiAutomation()
                .adoptShellPermissionIdentity(
                        Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR);
        Uri tables =
                Calendars.CONTENT_URI
                        .buildUpon()
                        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                        .appendQueryParameter(Calendars.ACCOUNT_NAME, owner)
                        .appendQueryParameter(
                                Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
                        .build();
        List<Uri> calendars = new ArrayList<>();
        try {
            for (String name : List.of("Priorität A", "Priorität B")) {
                ContentValues v = new ContentValues();
                v.put(Calendars.ACCOUNT_NAME, owner);
                v.put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL);
                v.put(Calendars.NAME, name);
                v.put(Calendars.CALENDAR_DISPLAY_NAME, name);
                v.put(Calendars.OWNER_ACCOUNT, owner);
                v.put(Calendars.CALENDAR_COLOR, 0xff9977aa);
                v.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER);
                v.put(Calendars.VISIBLE, 1);
                v.put(Calendars.SYNC_EVENTS, 1);
                calendars.add(ctx.getContentResolver().insert(tables, v));
            }
            long a = ContentUris.parseId(calendars.get(0)),
                    b = ContentUris.parseId(calendars.get(1));
            OffsetDateTime point = OffsetDateTime.parse("2026-11-22T12:15:00Z");
            long from = point.minusMinutes(15).toInstant().toEpochMilli();
            long series =
                    event(
                            ctx,
                            a,
                            "Serie",
                            from - 86400000,
                            3600000,
                            "FREQ=DAILY;COUNT=3",
                            false,
                            false);
            event(ctx, b, "Einzeltermin", from, 3600000, null, false, false);
            event(ctx, a, "Abgesagt", from - 3600000, 7200000, null, false, true);
            event(ctx, a, "Ganztägig", from - 12 * 3600000, 86400000, null, true, false);
            SharedPreferences prefs = Profile.prefs(isolated);
            prefs.edit().putString("calendars", a + "," + b).commit();
            var winner = CalendarNames.at(isolated, point).get().event();
            assertEquals("Serie", winner.title());
            assertEquals("Priorität A", winner.calendarName());
            prefs.edit().putString("calendars", b + "," + a).commit();
            winner = CalendarNames.at(isolated, point).get().event();
            assertEquals("Einzeltermin", winner.title());
            assertEquals("Priorität B", winner.calendarName());
            assertNull(CalendarNames.at(isolated, point.plusHours(2)).get().event());
            var selectedDay =
                    CalendarNames.day(
                            isolated,
                            point.toLocalDate(),
                            ZoneId.of("Europe/Berlin"),
                            new android.os.CancellationSignal());
            var titles =
                    selectedDay.appointments().stream()
                            .map(item -> item.event().title())
                            .collect(java.util.stream.Collectors.toList());
            assertEquals(3, titles.size());
            assertTrue(titles.containsAll(List.of("Serie", "Einzeltermin", "Ganztägig")));
            assertEquals(
                    1L,
                    selectedDay.appointments().stream()
                            .filter(item -> item.event().allDay())
                            .count());
            prefs.edit().putString("calendars", Long.toString(a)).commit();
            assertFalse(
                    CalendarNames.day(
                                    isolated,
                                    point.toLocalDate(),
                                    ZoneId.of("America/Los_Angeles"),
                                    new android.os.CancellationSignal())
                            .appointments()
                            .stream()
                            .anyMatch(item -> item.event().calendar() == b));
            long utcMidnight =
                    point.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            event(
                    ctx,
                    a,
                    "Früh am Folgetag",
                    utcMidnight + 86400000L + 1800000L,
                    600000,
                    null,
                    false,
                    false);
            event(ctx, a, "Ganztägig morgen", utcMidnight + 86400000L, 86400000, null, true, false);
            var west =
                    CalendarNames.day(
                            isolated,
                            point.toLocalDate(),
                            ZoneId.of("America/Los_Angeles"),
                            new android.os.CancellationSignal());
            assertTrue(
                    west.appointments().stream()
                            .anyMatch(item -> item.event().title().equals("Früh am Folgetag")));
            assertFalse(
                    west.appointments().stream()
                            .anyMatch(item -> item.event().title().equals("Ganztägig morgen")));
            LocalDate autumn = LocalDate.of(2026, 10, 25);
            ZoneId berlin = ZoneId.of("Europe/Berlin");
            var repeated = autumn.atTime(2, 30).atZone(berlin);
            event(
                    ctx,
                    a,
                    "Frühe Winterzeitstunde",
                    repeated.withEarlierOffsetAtOverlap().toInstant().toEpochMilli(),
                    600000,
                    null,
                    false,
                    false);
            event(
                    ctx,
                    a,
                    "Späte Winterzeitstunde",
                    repeated.withLaterOffsetAtOverlap().toInstant().toEpochMilli(),
                    600000,
                    null,
                    false,
                    false);
            var overlap =
                    CalendarNames.day(isolated, autumn, berlin, new android.os.CancellationSignal())
                            .appointments();
            assertEquals(2, overlap.size());
            assertEquals(3600000L, overlap.get(1).event().begin() - overlap.get(0).event().begin());
            prefs.edit().putString("calendars", b + "," + a).commit();
            prefs.edit().putInt("after", 90).commit();
            assertEquals(
                    "Einzeltermin",
                    CalendarNames.at(isolated, point.plusHours(2)).get().event().title());
            ContentValues moved = new ContentValues();
            moved.put(Events.CALENDAR_ID, a);
            moved.put(Events.TITLE, "Verschobene Folge");
            moved.put(Events.ORIGINAL_ID, series);
            moved.put(Events.ORIGINAL_INSTANCE_TIME, from);
            moved.put(Events.ORIGINAL_ALL_DAY, 0);
            moved.put(Events.EVENT_TIMEZONE, "UTC");
            moved.put(Events.DTSTART, from + 3 * 3600000L);
            moved.put(Events.DTEND, from + 4 * 3600000L);
            assertNotNull(ctx.getContentResolver().insert(Events.CONTENT_URI, moved));
            prefs.edit().putString("calendars", Long.toString(a)).putInt("after", 0).commit();
            assertNull(CalendarNames.at(isolated, point).get().event());
            assertEquals(
                    "Verschobene Folge",
                    CalendarNames.at(isolated, point.plusHours(3)).get().event().title());
        } finally {
            for (Uri cal : calendars)
                if (cal != null)
                    ctx.getContentResolver()
                            .delete(
                                    ContentUris.withAppendedId(tables, ContentUris.parseId(cal)),
                                    null,
                                    null);
            ctx.deleteSharedPreferences(opts);
            inst.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    long event(
            Context ctx,
            long cal,
            String title,
            long start,
            long duration,
            String rule,
            boolean allDay,
            boolean cancelled) {
        ContentValues v = new ContentValues();
        v.put(Events.CALENDAR_ID, cal);
        v.put(Events.TITLE, title);
        v.put(Events.DTSTART, start);
        v.put(Events.EVENT_TIMEZONE, "UTC");
        if (rule == null) v.put(Events.DTEND, start + duration);
        else {
            v.put(Events.RRULE, rule);
            v.put(Events.DURATION, "PT1H");
        }
        v.put(Events.ALL_DAY, allDay ? 1 : 0);
        v.put(Events.STATUS, cancelled ? Events.STATUS_CANCELED : Events.STATUS_CONFIRMED);
        Uri row = ctx.getContentResolver().insert(Events.CONTENT_URI, v);
        assertNotNull(row);
        return ContentUris.parseId(row);
    }
}
