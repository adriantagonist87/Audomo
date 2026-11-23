package de.audomo;

import static org.junit.Assert.*;

import org.junit.Test;

import java.time.OffsetDateTime;
import java.util.List;

public class CalendarNamesTest {
    private final List<Long> order = List.of(7L, 3L);
    private final CalendarNames.Event early =
            new CalendarNames.Event(2, 7, "Früher", 100, 200, "Privat");
    private final CalendarNames.Event current =
            new CalendarNames.Event(1, 3, "Jetzt", 190, 250, "Arbeit");

    @Test
    public void runningOutranksToleranceAndEndIsExclusive() {
        var candidates = List.of(early, current);
        assertEquals(early, CalendarNames.choose(candidates, order, 199, 0, 5));
        assertEquals(current, CalendarNames.choose(candidates, order, 200, 0, 5));
        assertNull(CalendarNames.choose(candidates, order, 255, 0, 5));
        assertEquals(early, CalendarNames.choose(candidates, order, 95, 5, 0));
    }

    @Test
    public void tieBreakDeterministicAcrossProviderOrder() {
        var first = new CalendarNames.Event(1, 7, "A", 100, 200, "Privat");
        assertEquals(first, CalendarNames.choose(List.of(early, first), order, 120, 0, 0));
        assertEquals(first, CalendarNames.choose(List.of(first, early), order, 120, 0, 0));
    }

    @Test
    public void templatesKeepTitleLiteralAndRespectOffset() {
        var t = OffsetDateTime.parse("2026-10-25T02:30:00+01:00");
        assertEquals(
                "2026-10-25_02-30-00_Aufnahme.ogg",
                CalendarNames.name(CalendarNames.DEFAULT, t, null));
        assertEquals(
                "{datum}_A_B.ogg",
                CalendarNames.name(
                        "{termin}", t, new CalendarNames.Event(1, 1, "{datum}/A:B", 0, 1, "P")));
    }

    @Test
    public void explicitAppointmentUsesTitleOnceAndKeepsRecordingTime() {
        var start = OffsetDateTime.parse("2026-11-23T08:25:16+01:00");
        assertEquals("2026-11-23_08-25-16_Jetzt.ogg",CalendarNames.chosenName(CalendarNames.DEFAULT,start,current));
        assertEquals("Arbeit_Jetzt.ogg",CalendarNames.chosenName("{kalender}",start,current));
    }

    @Test
    public void calendarNameAndFolderUseTheSameEventAndStayInOneSegment() {
        var time = OffsetDateTime.parse("2026-11-22T18:30:00+01:00");
        var event = new CalendarNames.Event(3, 7, "Essen", 0, 1, "../Privat:{datum}/A");
        assertEquals(
                "_Privat_{datum}_A_Essen.ogg",
                CalendarNames.name("{kalender}_{termin}", time, event));
        assertEquals(
                "Archiv/_Privat_{datum}_A",
                CalendarNames.folder("Archiv", CalendarNames.FolderMode.CALENDAR, event));
        assertEquals(
                "Archiv", CalendarNames.folder("Archiv", CalendarNames.FolderMode.FIXED, event));
        assertEquals("Privat", CalendarNames.folder("", CalendarNames.FolderMode.CALENDAR, early));
        for (CalendarNames.Event missing :
                new CalendarNames.Event[] {
                    null, new CalendarNames.Event(2, 7, "Termintitel", 0, 1, "...")
                }) {
            assertEquals("Aufnahme.ogg", CalendarNames.name("{kalender}", time, missing));
            assertEquals(
                    "Archiv",
                    CalendarNames.folder("Archiv", CalendarNames.FolderMode.CALENDAR, missing));
        }
    }
}
