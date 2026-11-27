package de.audomo;

import java.time.ZonedDateTime;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdatePolicyTest {
    @Test public void stableVersionsCompareNumerically() {
        assertTrue(GitHubUpdate.newer("1.10.0", "1.9.99"));
        assertTrue(GitHubUpdate.newer("2.0.0", "1.99.99"));
        assertFalse(GitHubUpdate.newer("1.3.1", "1.3.1"));
        assertFalse(GitHubUpdate.newer("0.9.0", "1.0.0"));
        for (String invalid : new String[]{"", "1.3", "1.02.0", "1.4.0-beta", "v1.4.0", "1.4.0/extra"})
            assertFalse(GitHubUpdate.stable(invalid));
    }

    @Test public void checkOnlyWhenIntervalElapsed() {
        ZonedDateTime day = ZonedDateTime.parse("2026-01-31T10:00:00+01:00[Europe/Berlin]");
        long last = day.toInstant().toEpochMilli();
        assertTrue(AppUpdates.due(0, last, day));
        assertTrue(AppUpdates.due(3, 0, day));
        assertTrue(AppUpdates.due(3, last + 1000, day));
        for (int i = 1; i <= 3; i++) {
            ZonedDateTime next = i == 1 ? day.plusDays(1) : i == 2 ? day.plusWeeks(1) : day.plusMonths(1);
            assertFalse(AppUpdates.due(i, last, next.minusSeconds(1)));
            assertTrue(AppUpdates.due(i, last, next));
        }
    }
}
