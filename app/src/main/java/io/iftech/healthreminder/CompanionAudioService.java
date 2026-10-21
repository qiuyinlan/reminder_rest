package io.iftech.healthreminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.res.AssetFileDescriptor;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;

import java.io.IOException;

import androidx.core.app.NotificationCompat;

public final class CompanionAudioService extends Service {
    static final String ACTION_START = "io.iftech.healthreminder.companion.START";
    static final String ACTION_TOGGLE_MUSIC = "io.iftech.healthreminder.companion.TOGGLE_MUSIC";
    static final String ACTION_SET_MUSIC = "io.iftech.healthreminder.companion.SET_MUSIC";
    static final String ACTION_SET_SOUND = "io.iftech.healthreminder.companion.SET_SOUND";
    static final String ACTION_STOP = "io.iftech.healthreminder.companion.STOP";
    static final String EXTRA_SOUND_ID = "sound_id";
    static final String EXTRA_MUSIC_PLAYING = "music_playing";
    static final String EXTRA_FOCUS_MODE = "focus_mode";
    static final String EXTRA_SESSION_NOTE = "session_note";
    private static final String PREFS = "companion_session";
    private static final String KEY_STARTED_AT = "started_at";
    private static final String KEY_MUSIC = "music_playing";
    private static final String KEY_SOUND = "sound_id";
    private static final String KEY_FOCUS_MODE = "focus_mode";
    private static final String CHANNEL_ID = "companion_audio";
    private static final int NOTIFICATION_ID = 4102;
    private MediaPlayer player;

    static long startedAt(android.content.Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getLong(KEY_STARTED_AT, 0L);
    }

    static boolean isFocusSession(android.content.Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_FOCUS_MODE, false);
    }

    static boolean isMusicPlaying(android.content.Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_MUSIC, false);
    }

    static void setMusicPlaying(android.content.Context context, boolean playing) {
        context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_MUSIC, playing).apply();
    }

    static String selectedSound(android.content.Context context) {
        return context.getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_SOUND, "river");
    }

    static void selectSound(android.content.Context context, String sound) {
        if (isSupportedSound(sound)) {
            context.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_SOUND, sound).apply();
        }
    }

    private static boolean isSupportedSound(String sound) {
        return "river".equals(sound) || "rain".equals(sound) || "birds".equals(sound)
                || "wind".equals(sound) || "waves".equals(sound) || "trees".equals(sound);
    }

    static String soundName(String id) {
        if ("rain".equals(id)) return "细雨";
        if ("birds".equals(id)) return "鸟鸣";
        if ("wind".equals(id)) return "风声";
        if ("waves".equals(id)) return "海浪";
        if ("trees".equals(id)) return "风吹树叶声";
        return "溪水";
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSession(intent == null ? "" : intent.getStringExtra(EXTRA_SESSION_NOTE));
            return START_NOT_STICKY;
        }

        long startedAt = startedAt(this);
        if (startedAt == 0L) {
            startedAt = System.currentTimeMillis();
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putLong(KEY_STARTED_AT, startedAt).putBoolean(KEY_MUSIC, false)
                    .putBoolean(KEY_FOCUS_MODE, intent != null
                            && intent.getBooleanExtra(EXTRA_FOCUS_MODE, false)).apply();
        }
        startForeground(NOTIFICATION_ID, notification());

        if (ACTION_TOGGLE_MUSIC.equals(action)) {
            boolean next = !isMusicPlaying(this);
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_MUSIC, next).apply();
        }
        if (ACTION_SET_MUSIC.equals(action) && intent != null) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_MUSIC, intent.getBooleanExtra(EXTRA_MUSIC_PLAYING, false)).apply();
        }
        if (ACTION_SET_SOUND.equals(action) && intent != null) {
            String sound = intent.getStringExtra(EXTRA_SOUND_ID);
            if (isSupportedSound(sound)) {
                selectSound(this, sound);
                releasePlayer();
            }
        }
        if (isMusicPlaying(this)) startMusic(); else pauseMusic();
        return START_STICKY;
    }

    private void startMusic() {
        if (player == null) {
            try {
                player = new MediaPlayer();
                player.setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build());
                String sound = selectedSound(this);
                int soundResource = "rain".equals(sound) ? R.raw.ambient_rain
                        : "birds".equals(sound) ? R.raw.ambient_birds
                        : "wind".equals(sound) ? R.raw.ambient_wind
                        : "waves".equals(sound) ? R.raw.ambient_waves
                        : "trees".equals(sound) ? R.raw.ambient_trees : R.raw.stream_river;
                try (AssetFileDescriptor audio = getResources().openRawResourceFd(soundResource)) {
                    player.setDataSource(audio.getFileDescriptor(), audio.getStartOffset(), audio.getLength());
                }
                player.setLooping(true);
                player.prepare();
            } catch (IOException | RuntimeException error) {
                if (player != null) player.release();
                player = null;
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_MUSIC, false).apply();
                return;
            }
        }
        if (!player.isPlaying()) player.start();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, notification());
    }

    private void pauseMusic() {
        if (player != null && player.isPlaying()) player.pause();
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.notify(NOTIFICATION_ID, notification());
    }

    private void releasePlayer() {
        if (player == null) return;
        if (player.isPlaying()) player.stop();
        player.release();
        player = null;
    }

    private void stopSession(String note) {
        long start = startedAt(this);
        boolean focus = isFocusSession(this);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .remove(KEY_STARTED_AT).putBoolean(KEY_MUSIC, false).putBoolean(KEY_FOCUS_MODE, false).apply();
        if (start > 0L) {
            long end = Math.max(start, System.currentTimeMillis());
            ReminderRecord.finish(this, (focus ? "focus-" : "companion-") + start,
                    focus ? "专注陪伴" : "轻松陪伴", focus ? ReminderRecord.KIND_FOCUS : ReminderRecord.KIND_COMPANION,
                    start, end, note == null ? "" : note.trim());
        }
        stopForeground(true);
        stopSelf();
    }

    private Notification notification() {
        Intent open = new Intent(this, CompanionActivity.class);
        open.putExtra(EXTRA_FOCUS_MODE, isFocusSession(this));
        PendingIntent openPending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent stop = new Intent(this, CompanionAudioService.class).setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String title = isFocusSession(this) ? "专注陪伴正在进行"
                : isMusicPlaying(this) ? "轻松陪伴中 · " + soundName(selectedSound(this)) + "播放中" : "轻松陪伴计时正在进行";
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setColor(UiPalette.ACCENT)
                .setContentTitle(title)
                .setContentText("点击返回陪伴页面，或停止本次陪伴")
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        if (!isFocusSession(this)) {
            builder.addAction(android.R.drawable.ic_media_pause, "结束陪伴", stopPending);
        }
        return builder.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "陪伴与环境音",
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("显示正在进行的陪伴计时和停止操作");
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(channel);
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
