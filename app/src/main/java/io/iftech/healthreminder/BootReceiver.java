package io.iftech.healthreminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("HealthReminderAlarm", "refreshing schedules after "
                + (intent == null ? "unknown event" : intent.getAction()));
        ReminderScheduler.refreshSchedulesForClockChange(context);
    }
}
