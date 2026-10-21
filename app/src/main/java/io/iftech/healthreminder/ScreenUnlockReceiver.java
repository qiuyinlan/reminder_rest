package io.iftech.healthreminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class ScreenUnlockReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        ReminderScheduler.markScreenOn(context);
        ReminderScheduler.reconcileAfterScreenOn(context);
    }
}
