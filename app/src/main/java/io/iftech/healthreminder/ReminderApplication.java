package io.iftech.healthreminder;

import android.app.Application;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

public final class ReminderApplication extends Application {
    private static int startedActivities;

    static boolean isInForeground() {
        return startedActivities > 0;
    }

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, android.os.Bundle state) { }
            @Override public void onActivityStarted(Activity activity) { startedActivities++; }
            @Override public void onActivityResumed(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { startedActivities = Math.max(0, startedActivities - 1); }
            @Override public void onActivitySaveInstanceState(Activity activity, android.os.Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        });
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    ReminderScheduler.pauseForScreenOff(context);
                } else if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                    Log.i("HealthReminderAlarm", "screen on; reconciling scheduled reminders");
                    ReminderScheduler.update(context);
                }
            }
        };
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }

        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        if (power != null && power.isInteractive()) ReminderScheduler.resumeAfterScreenOn(this);
        else ReminderScheduler.pauseForScreenOff(this);
    }
}
