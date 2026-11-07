package io.iftech.healthreminder;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.view.View;

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
    private void openAndLayout() {
        ActivityController<StatsActivity> controller = Robolectric.buildActivity(StatsActivity.class);
        controller.setup();
        assertNotNull(controller.get().getWindow().getDecorView());
        View root = controller.get().findViewById(android.R.id.content);
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(2340, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, 1080, 2340);
        controller.pause().stop().destroy();
    }

    @Test public void opensWithoutProfileOrRecords() {
        Context context = RuntimeEnvironment.getApplication();
        UiPalette.load(context);
        openAndLayout();
    }

    @Test public void opensWithFemaleAndOngoingPeriod() {
        Context context = RuntimeEnvironment.getApplication();
        UiPalette.load(context);
        UserProfile.setGender(context, UserProfile.FEMALE);
        PeriodLog.startOn(context, Calendar.getInstance());
        openAndLayout();
    }

    @Test public void opensWithFemaleAndClosedPeriod() {
        Context context = RuntimeEnvironment.getApplication();
        UiPalette.load(context);
        UserProfile.setGender(context, UserProfile.FEMALE);
        Calendar start = Calendar.getInstance();
        start.add(Calendar.DAY_OF_MONTH, -8);
        PeriodLog.startOn(context, start);
        Calendar end = Calendar.getInstance();
        end.add(Calendar.DAY_OF_MONTH, -3);
        PeriodLog.endOn(context, end);
        openAndLayout();
    }
}
