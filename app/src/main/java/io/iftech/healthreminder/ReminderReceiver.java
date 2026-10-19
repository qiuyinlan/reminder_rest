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
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public final class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "health_breaks_v2";
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.getBooleanExtra("snooze", false)) {
            if (!ReminderSettings.enabled(context)) return;
            String action = intent.getStringExtra("action");
            int ruleId = intent.getIntExtra("rule_id", -1);
            if (!hasEnabledRule(context, ruleId, action)) return;
            post(context, action, ruleId);
            if (intent.getBooleanExtra("resume_schedule", false) && ruleId >= 0) {
                ReminderScheduler.scheduleNext(context, ruleId);
            }
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
        post(context, rule.title, rule.id);
        ReminderScheduler.scheduleNext(context, rule.id);
    }

    private static boolean hasEnabledRule(Context context, int ruleId, String action) {
        for (ReminderSettings.Rule rule : ReminderSettings.rules(context)) {
            if (!rule.enabled) continue;
            if (ruleId >= 0 ? rule.id == ruleId : rule.title.equals(action)) return true;
        }
        return false;
    }

    static void post(Context context, String action, int ruleId) {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean alarmMode = ReminderSettings.screenOffAlarm(context) && power != null && !power.isInteractive();
        boolean soundEnabled = alarmMode && ReminderSettings.screenOffSound(context);
        boolean vibrate = alarmMode && ReminderSettings.screenOffVibrate(context);
        Uri alarmSound = alarmSound(context);
        String channelId = createChannel(context, alarmMode, soundEnabled, vibrate, alarmSound);
        if (action == null) action = ReminderSettings.nextType(context);
        Intent open = new Intent(context, ReminderActivity.class).putExtra("action", action).putExtra("rule_id", ruleId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 702, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean covered = ReminderOverlayWindow.show(context, action, ruleId);
        if (covered) {
            NotificationManagerCompat.from(context).cancel(703);
            return;
        }
        String title = ReminderActivity.titleFor(action);
        String message = ReminderActivity.pleaFor(action);
        androidx.core.app.NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("健康提醒 · " + title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(alarmMode ? NotificationCompat.CATEGORY_ALARM : NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(content)
                .setFullScreenIntent(content, alarmMode);
        if (Build.VERSION.SDK_INT < 26 && alarmMode) {
            if (soundEnabled) builder.setSound(alarmSound);
            else builder.setSound(null);
            if (vibrate) builder.setVibrate(new long[]{0, 500, 250, 500});
        }
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(703, builder.build());
        }
    }

    private static String createChannel(Context context, boolean alarmMode, boolean soundEnabled, boolean vibrate, Uri alarmSound) {
        if (!alarmMode) {
            createNotificationChannel(context, CHANNEL_ID, "健康提醒", false, false, false, null);
            return CHANNEL_ID;
        }
        String channelId = alarmChannelId(alarmSound, soundEnabled, vibrate);
        String name = "息屏闹钟" + (soundEnabled ? "（铃声" : "（静音") + (vibrate ? "、振动）" : "）");
        createNotificationChannel(context, channelId, name, true, soundEnabled, vibrate, alarmSound);
        return channelId;
    }

    private static Uri alarmSound(Context context) {
        String stored = ReminderSettings.screenOffRingtone(context);
        Uri sound = stored == null || stored.isEmpty() ? null : Uri.parse(stored);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (sound == null) sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        return sound;
    }

    private static String alarmChannelId(Uri sound, boolean soundEnabled, boolean vibrate) {
        String value = (soundEnabled && sound != null ? sound.toString() : "silent") + ":" + vibrate;
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder id = new StringBuilder("health_alarm_v2_");
            for (int i = 0; i < 8; i++) id.append(String.format(java.util.Locale.ROOT, "%02x", digest[i] & 0xff));
            return id.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            return "health_alarm_v2_" + Integer.toHexString(value.hashCode());
        }
    }

    private static void createNotificationChannel(Context context, String channelId, String name,
                                                  boolean alarmMode, boolean soundEnabled, boolean vibrate, Uri alarmSound) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            NotificationChannel channel = new NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(alarmMode ? "息屏时使用闹钟声音提醒" : "定时健康休息提醒");
            channel.enableVibration(alarmMode ? vibrate : true);
            if (alarmMode && vibrate) channel.setVibrationPattern(new long[]{0, 500, 250, 500});
            if (alarmMode && soundEnabled) {
                channel.setSound(alarmSound, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            } else if (alarmMode) {
                channel.setSound(null, null);
            } else {
                channel.setVibrationPattern(new long[]{0, 350, 150, 350});
                channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            }
            manager.createNotificationChannel(channel);
        }
    }
}
