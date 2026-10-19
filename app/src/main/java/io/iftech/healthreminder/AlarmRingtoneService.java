package io.iftech.healthreminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public final class AlarmRingtoneService extends Service {
    private static final String ACTION_START = "io.iftech.healthreminder.alarm.START_RINGTONE";
    private static final String EXTRA_SOUND_ENABLED = "sound_enabled";
    private static final String EXTRA_VIBRATE = "vibrate";
    private static final String CHANNEL_ID = "alarm_playback";
    private static final int NOTIFICATION_ID = 4201;
    private Ringtone ringtone;
    private Vibrator vibrator;

    static boolean start(Context context, Uri sound, boolean soundEnabled, boolean vibrate) {
        Intent intent = new Intent(context, AlarmRingtoneService.class)
                .setAction(ACTION_START)
                .setData(sound)
                .putExtra(EXTRA_SOUND_ENABLED, soundEnabled)
                .putExtra(EXTRA_VIBRATE, vibrate);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, AlarmRingtoneService.class));
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !ACTION_START.equals(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        startForeground(NOTIFICATION_ID, buildNotification());
        stopRingtone();
        stopVibration();
        boolean soundEnabled = intent.getBooleanExtra(EXTRA_SOUND_ENABLED, true);
        boolean vibrate = intent.getBooleanExtra(EXTRA_VIBRATE, false);
        if (vibrate) startVibration();
        if (!soundEnabled) return START_NOT_STICKY;

        Uri sound = intent.getData();
        Uri defaultAlarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        ringtone = loadRingtone(sound);
        if (ringtone == null && !java.util.Objects.equals(sound, defaultAlarm)) {
            ringtone = loadRingtone(defaultAlarm);
        }
        if (ringtone == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        ringtone.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
        if (Build.VERSION.SDK_INT >= 28) ringtone.setLooping(true);
        try {
            ringtone.play();
        } catch (RuntimeException error) {
            Log.e("AlarmRingtone", "Unable to play the selected alarm tone", error);
            stopRingtone();
            if (sound == null || java.util.Objects.equals(sound, defaultAlarm)) {
                stopSelf(startId);
                return START_NOT_STICKY;
            }
            ringtone = loadRingtone(defaultAlarm);
            if (ringtone == null) {
                stopSelf(startId);
                return START_NOT_STICKY;
            }
            ringtone.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
            if (Build.VERSION.SDK_INT >= 28) ringtone.setLooping(true);
            ringtone.play();
        }
        return START_NOT_STICKY;
    }

    private Ringtone loadRingtone(Uri sound) {
        if (sound == null) return null;
        try {
            return RingtoneManager.getRingtone(this, sound);
        } catch (RuntimeException error) {
            Log.w("AlarmRingtone", "Unable to load alarm tone " + sound, error);
            return null;
        }
    }

    private void startVibration() {
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }
        if (vibrator == null || !vibrator.hasVibrator()) return;
        if (Build.VERSION.SDK_INT >= 26) {
            VibrationEffect effect = VibrationEffect.createWaveform(new long[]{0, 500, 250, 500}, 0);
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            vibrator.vibrate(effect, attributes);
        } else {
            vibrator.vibrate(new long[]{0, 500, 250, 500}, 0);
        }
    }

    private void stopVibration() {
        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, ReminderActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 4201, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("健康提醒正在响铃")
                .setContentText("打开提醒页面可停止铃声")
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "闹钟声音播放",
                NotificationManager.IMPORTANCE_LOW);
        channel.setSound(null, null);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    private void stopRingtone() {
        if (ringtone != null) {
            if (ringtone.isPlaying()) ringtone.stop();
            ringtone = null;
        }
    }

    @Override public void onDestroy() {
        stopRingtone();
        stopVibration();
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
