package io.iftech.healthreminder;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;

public final class ReminderScheduleMathTest {
    private TimeZone originalTimeZone;

    @Before public void useUtc() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @After public void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test public void choosesNextSelectedTimeToday() {
        long now = at(2026, Calendar.OCTOBER, 20, 9, 0, 0, 0);
        long expected = at(2026, Calendar.OCTOBER, 20, 12, 30, 5, 0);

        assertEquals(expected, ReminderScheduleMath.nextTime(
                Arrays.asList(8 * 3600, 12 * 3600 + 30 * 60 + 5, 18 * 3600), now, false));
    }

    @Test public void movesToTomorrowAfterTheLastPoint() {
        long now = at(2026, Calendar.OCTOBER, 20, 19, 0, 0, 0);
        long expected = at(2026, Calendar.OCTOBER, 21, 8, 0, 0, 0);

        assertEquals(expected, ReminderScheduleMath.nextTime(
                Arrays.asList(8 * 3600, 12 * 3600), now, false));
    }

    @Test public void tomorrowOnlySkipsLaterPointsToday() {
        long now = at(2026, Calendar.OCTOBER, 20, 9, 0, 0, 0);
        long expected = at(2026, Calendar.OCTOBER, 21, 8, 0, 0, 0);

        assertEquals(expected, ReminderScheduleMath.nextTime(
                Arrays.asList(8 * 3600, 12 * 3600), now, true));
    }

    @Test public void treatsMidnightAsAValidDailyTimePoint() {
        long now = at(2026, Calendar.OCTOBER, 20, 23, 59, 59, 0);
        long expected = at(2026, Calendar.OCTOBER, 21, 0, 0, 0, 0);

        assertEquals(expected, ReminderScheduleMath.nextTime(Arrays.asList(0), now, false));
    }

    @Test public void skipsThePointThatIsExactlyNow() {
        long now = at(2026, Calendar.OCTOBER, 20, 8, 0, 0, 1);
        long expected = at(2026, Calendar.OCTOBER, 20, 12, 0, 0, 0);

        assertEquals(expected, ReminderScheduleMath.nextTime(
                Arrays.asList(8 * 3600, 12 * 3600), now, false));
    }

    private long at(int year, int month, int day, int hour, int minute, int second, int millis) {
        Calendar value = Calendar.getInstance();
        value.clear();
        value.set(year, month, day, hour, minute, second);
        value.set(Calendar.MILLISECOND, millis);
        return value.getTimeInMillis();
    }
}
