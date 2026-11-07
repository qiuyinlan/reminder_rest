package io.iftech.healthreminder;

import static org.junit.Assert.assertNotNull;

import android.content.Context;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.Calendar;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class StatsSmokeTest {
    @Test public void opensWithFemaleAndOngoingPeriod() {
        Context context = RuntimeEnvironment.getApplication();
        UiPalette.load(context);
        UserProfile.setGender(context, UserProfile.FEMALE);
        PeriodLog.startOn(context, Calendar.getInstance());
        ActivityController<StatsActivity> controller = Robolectric.buildActivity(StatsActivity.class);
        controller.setup();
        assertNotNull(controller.get().getWindow().getDecorView());
        controller.pause().stop().destroy();
    }
}
