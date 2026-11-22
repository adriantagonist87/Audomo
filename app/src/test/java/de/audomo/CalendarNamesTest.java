package de.audomo;

import static org.junit.Assert.*;

import org.junit.Test;

import java.time.OffsetDateTime;
import java.util.List;

public class CalendarNamesTest {
    private final List<Long> order = List.of(7L, 3L);
    private final CalendarNames.Event early = new CalendarNames.Event(2, 7, "Früher", 100, 200);
    private final CalendarNames.Event current = new CalendarNames.Event(1, 3, "Jetzt", 190, 250);

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
        var first = new CalendarNames.Event(1, 7, "A", 100, 200);
        assertEquals(first, CalendarNames.choose(List.of(early, first), order, 120, 0, 0));
        assertEquals(first, CalendarNames.choose(List.of(first, early), order, 120, 0, 0));
    }

    @Test
    public void templatesKeepTitleLiteralAndRespectOffset() {
        var t = OffsetDateTime.parse("2026-10-25T02:30:00+01:00");
        assertEquals(
                "2026-10-25_02-30-00_Aufnahme.ogg",
                CalendarNames.name(CalendarNames.DEFAULT, t, " "));
        assertEquals("{datum}_A_B.ogg", CalendarNames.name("{termin}", t, "{datum}/A:B"));
    }
}
