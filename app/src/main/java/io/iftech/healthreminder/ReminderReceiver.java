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
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public final class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL_ID = "health_breaks_v2";
    private static final String ACTION_STOP_ALARM_VIBRATION = "io.iftech.healthreminder.STOP_ALARM_VIBRATION";
    private static final String TAG = "HealthReminderAlarm";
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && ACTION_STOP_ALARM_VIBRATION.equals(intent.getAction())) {
            Log.i(TAG, "stopAlarm action received");
            stopAlarmVibration(context, "notification dismissed");
            return;
        }
        boolean snooze = intent != null && intent.getBooleanExtra("snooze", false);
        int receivedRuleId = intent == null ? -1 : intent.getIntExtra("rule_id", -1);
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        Log.i(TAG, "received snooze=" + snooze + " rule=" + receivedRuleId
                + " enabled=" + ReminderSettings.enabled(context)
                + " screenOffAlarm=" + ReminderSettings.screenOffAlarm(context)
                + " interactive=" + (power == null || power.isInteractive()));
        if (snooze) {
            if (!ReminderSettings.enabled(context)) return;
            String action = intent.getStringExtra("action");
            int ruleId = receivedRuleId;
            if (!hasEnabledRule(context, ruleId, action)) return;
            if (shouldSkipWhileScreenOff(context)) {
                Log.i(TAG, "snooze skipped while screen is off; pausing schedule");
                ReminderScheduler.pauseForScreenOff(context);
                return;
            }
            ReminderScheduler.consumeSnooze(context, ruleId);
            if (intent.getBooleanExtra("resume_schedule", false) && ruleId >= 0) {
                scheduleNextSafely(context, ruleId);
            }
            postSafely(context, action, ruleId);
            return;
        }
        if (!ReminderSettings.enabled(context)) return;
        int ruleId = receivedRuleId;
        ReminderSettings.Rule rule = null;
        for (ReminderSettings.Rule candidate : ReminderSettings.rules(context)) {
            if (candidate.id == ruleId && candidate.enabled) { rule = candidate; break; }
        }
        if (rule == null) {
            Log.w(TAG, "ignored alarm with no enabled rule=" + ruleId);
            return;
        }
        if (shouldSkipWhileScreenOff(context)) {
            Log.i(TAG, "rule skipped while screen is off; pausing schedule rule=" + ruleId);
            ReminderScheduler.pauseForScreenOff(context);
            return;
        }
        if (ReminderSettings.usageOnly(context) && !rule.specificTimeMode) {
            long recent = UsageAccess.recentMillis(context, System.currentTimeMillis() - rule.intervalSeconds * 1_000L, System.currentTimeMillis());
            if (recent == 0) {
                scheduleNextSafely(context, rule.id);
                return;
            }
        }
        if (ReminderSettings.screenOffAlarm(context)
                && (power == null || !power.isInteractive())
                && (ReminderSettings.screenOffSound(context) || ReminderSettings.screenOffVibrate(context))) {
            ReminderScheduler.beginScreenOffAlarmSession(context, rule.id);
            Log.i(TAG, "screen-off alarm session started rule=" + rule.id);
        }
        scheduleNextSafely(context, rule.id);
        postSafely(context, rule.title, rule.id);
    }

    private static void scheduleNextSafely(Context context, int ruleId) {
        try {
            ReminderScheduler.scheduleNext(context, ruleId);
            Log.i(TAG, "next alarm scheduled rule=" + ruleId
                    + " at=" + ReminderScheduler.scheduledAt(context, ruleId));
        } catch (RuntimeException error) {
            Log.e(TAG, "unable to schedule next alarm rule=" + ruleId, error);
        }
    }

    private static void postSafely(Context context, String action, int ruleId) {
        try {
            post(context, action, ruleId);
        } catch (RuntimeException error) {
            Log.e(TAG, "unable to post reminder rule=" + ruleId, error);
        }
    }

    private static boolean hasEnabledRule(Context context, int ruleId, String action) {
        for (ReminderSettings.Rule rule : ReminderSettings.rules(context)) {
            if (!rule.enabled) continue;
            if (ruleId >= 0 ? rule.id == ruleId : rule.title.equals(action)) return true;
        }
        return false;
    }

    private static boolean shouldSkipWhileScreenOff(Context context) {
        if (ReminderSettings.screenOffAlarm(context)) return false;
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return power != null && !power.isInteractive();
    }

    static void post(Context context, String action, int ruleId) {
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        boolean screenOffAlarmEnabled = ReminderSettings.screenOffAlarm(context);
        boolean interactive = power == null || power.isInteractive();
        boolean alarmSession = ReminderScheduler.screenOffAlarmSessionActive(context, ruleId);
        boolean alarmMode = screenOffAlarmEnabled && (!interactive || alarmSession);
        boolean soundEnabled = alarmMode && ReminderSettings.screenOffSound(context);
        boolean vibrate = alarmMode && ReminderSettings.screenOffVibrate(context);
        boolean notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                && (Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        Log.i(TAG, "post rule=" + ruleId + " screenOffEnabled=" + screenOffAlarmEnabled
                + " alarmSession=" + alarmSession + " interactive=" + interactive
                + " notificationsEnabled=" + notificationsEnabled);
        Uri alarmSound = alarmSound(context);
        boolean serviceRequestAccepted = alarmMode && (soundEnabled || vibrate)
                && AlarmRingtoneService.start(context, alarmSound, soundEnabled, vibrate);
        Log.i(TAG, "alarmMode=" + alarmMode + " serviceRequestAccepted=" + serviceRequestAccepted
                + " sound=" + soundEnabled + " vibrate=" + vibrate);
        if (vibrate && !notificationsEnabled && !serviceRequestAccepted) vibrateForAlarm(context);
        String channelId = createChannel(context, alarmMode,
                soundEnabled && !serviceRequestAccepted, vibrate && !serviceRequestAccepted, alarmSound);
        if (action == null) action = ReminderSettings.nextType(context);
        Intent open = new Intent(context, ReminderActivity.class).putExtra("action", action).putExtra("rule_id", ruleId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 702, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent dismissed = new Intent(context, ReminderReceiver.class).setAction(ACTION_STOP_ALARM_VIBRATION);
        PendingIntent delete = PendingIntent.getBroadcast(context, 704, dismissed,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        boolean covered = ReminderOverlayWindow.show(context, action, ruleId);
        Log.i(TAG, "overlayCovered=" + covered);
        if (covered && !screenOffAlarmEnabled) {
            NotificationManagerCompat.from(context).cancel(703);
            return;
        }
        if (!screenOffAlarmEnabled && !covered && interactive && ReminderApplication.isInForeground()) {
            try {
                context.startActivity(open);
                NotificationManagerCompat.from(context).cancel(703);
                return;
            } catch (RuntimeException ignored) { }
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
                .setDeleteIntent(delete)
                .setFullScreenIntent(content, alarmMode);
        if (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(703, builder.build());
            Log.i(TAG, "notificationPosted channel=" + channelId + " fullScreen=" + alarmMode);
        } else {
            Log.w(TAG, "notificationSkipped permissionDenied");
        }
    }

    static void stopAlarmVibration(Context context, String reason) {
        Log.i(TAG, "stopping alarm reason=" + reason);
        ReminderScheduler.clearScreenOffAlarmSession(context, reason);
        try {
            Vibrator vibrator = defaultVibrator(context);
            if (vibrator != null) vibrator.cancel();
        } catch (RuntimeException error) {
            Log.w(TAG, "unable to cancel the default vibrator", error);
        }
        AlarmRingtoneService.stop(context);
    }

    private static void vibrateForAlarm(Context context) {
        Vibrator vibrator = defaultVibrator(context);
        if (vibrator == null || !vibrator.hasVibrator()) return;
        VibrationEffect effect = VibrationEffect.createWaveform(new long[]{0, 500, 250, 500}, 0);
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        vibrator.vibrate(effect, attributes);
    }

    private static Vibrator defaultVibrator(Context context) {
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            return manager == null ? null : manager.getDefaultVibrator();
        }
        return (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
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
            StringBuilder id = new StringBuilder("health_alarm_v7_");
            for (int i = 0; i < 8; i++) id.append(String.format(java.util.Locale.ROOT, "%02x", digest[i] & 0xff));
            return id.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            return "health_alarm_v7_" + Integer.toHexString(value.hashCode());
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
            if (!alarmMode) channel.setVibrationPattern(new long[]{0, 350, 150, 350});
            if (alarmMode) {
                AudioAttributes alarmAttributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                channel.setSound(soundEnabled ? alarmSound : null, alarmAttributes);
            } else {
                channel.setVibrationPattern(new long[]{0, 350, 150, 350});
                channel.setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                        new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).build());
            }
            manager.createNotificationChannel(channel);
        }
    }
}
