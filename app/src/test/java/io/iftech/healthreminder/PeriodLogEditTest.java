package io.iftech.healthreminder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Calendar;

/** 经期结束日点错了，必须能改成别的日子。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class PeriodLogEditTest {

    @After
    public void cleanup() {
        PeriodLog.clear(RuntimeEnvironment.getApplication());
    }

    @Test
    public void wrongEndCanBeMovedToAnotherDay() {
        Application app = RuntimeEnvironment.getApplication();
        Calendar start = Calendar.getInstance();
        start.add(Calendar.DAY_OF_MONTH, -5);
        assertTrue(PeriodLog.startOn(app, start));
        assertTrue(PeriodLog.endOn(app, Calendar.getInstance()));

        PeriodLog.Range latest = PeriodLog.latest(app);
        assertEquals(6, PeriodLog.days(app, latest));

        Calendar corrected = (Calendar) start.clone();
        corrected.add(Calendar.DAY_OF_MONTH, 3);
        assertTrue(PeriodLog.setEnd(app, latest, corrected));

        latest = PeriodLog.latest(app);
        assertEquals(PeriodLog.dayKey(corrected), latest.endKey);
        assertEquals(4, PeriodLog.days(app, latest));
    }

    @Test
    public void endIsClampedBetweenStartAndToday() {
        Application app = RuntimeEnvironment.getApplication();
        Calendar start = Calendar.getInstance();
        start.add(Calendar.DAY_OF_MONTH, -5);
        assertTrue(PeriodLog.startOn(app, start));
        PeriodLog.Range latest = PeriodLog.latest(app);

        Calendar future = Calendar.getInstance();
        future.add(Calendar.DAY_OF_MONTH, 5);
        assertTrue(PeriodLog.setEnd(app, latest, future));
        assertEquals(PeriodLog.todayKey(), PeriodLog.latest(app).endKey);

        Calendar earlierThanStart = (Calendar) start.clone();
        earlierThanStart.add(Calendar.DAY_OF_MONTH, -3);
        assertTrue(PeriodLog.setEnd(app, PeriodLog.latest(app), earlierThanStart));
        assertEquals(PeriodLog.dayKey(start), PeriodLog.latest(app).endKey);
    }

    @Test
    public void endNeverCrossesTheNextPeriod() {
        Application app = RuntimeEnvironment.getApplication();
        Calendar first = Calendar.getInstance();
        first.add(Calendar.DAY_OF_MONTH, -9);
        assertTrue(PeriodLog.startOn(app, first));
        assertTrue(PeriodLog.endOn(app, dayOffset(first, 4)));

        Calendar second = Calendar.getInstance();
        second.add(Calendar.DAY_OF_MONTH, -2);
        assertTrue(PeriodLog.startOn(app, second));

        PeriodLog.Range firstRange = PeriodLog.all(app).get(0);
        Calendar tooLate = Calendar.getInstance();
        assertTrue(PeriodLog.setEnd(app, firstRange, tooLate));

        PeriodLog.Range after = PeriodLog.all(app).get(0);
        assertEquals(PeriodLog.dayKey(dayOffset(second, -1)), after.endKey);
    }

    private static Calendar dayOffset(Calendar base, int days) {
        Calendar value = (Calendar) base.clone();
        value.add(Calendar.DAY_OF_MONTH, days);
        return value;
    }
}
