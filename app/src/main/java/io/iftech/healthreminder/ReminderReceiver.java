package io.iftech.healthreminder;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public final class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "health_breaks_v2";
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getBooleanExtra("snooze", false)) {
            post(context, intent.getStringExtra("action"));
            return;
        }
        if (!ReminderSettings.enabled(context)) return;
        int ruleId = intent == null ? -1 : intent.getIntExtra("rule_id", -1);
        ReminderSettings.Rule rule = null;
        for (ReminderSettings.Rule candidate : ReminderSettings.rules(context)) {
            if (candidate.id == ruleId && candidate.enabled) { rule = candidate; break; }
        }
        if (rule == null) return;
        if (ReminderSettings.usageOnly(context)) {
            long recent = UsageAccess.recentMillis(context, System.currentTimeMillis() - rule.interval * 60_000L, System.currentTimeMillis());
            if (recent == 0) {
                ReminderScheduler.scheduleNext(context, rule.id);
                return;
            }
        }
        post(context, rule.title);
        ReminderScheduler.scheduleNext(context, rule.id);
    }

    static void post(Context context, String action) {
        createChannel(context);
        if (action == null) action = ReminderSettings.nextType(context);
        Intent open = new Intent(context, ReminderActivity.class).putExtra("action", action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 702, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean covered = ReminderOverlayWindow.show(context, action);
        if (covered) {
            NotificationManagerCompat.from(context).cancel(703);
            return;
        }
        String title = ReminderActivity.titleFor(action);
        String message = ReminderActivity.pleaFor(action);
        androidx.core.app.NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("健康提醒 · " + title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(content);
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(703, builder.build());
        }
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "健康提醒", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("定时健康休息提醒");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 350, 150, 350});
            channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            manager.createNotificationChannel(channel);
        }
    }
}
