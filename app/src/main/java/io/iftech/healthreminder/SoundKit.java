package io.iftech.healthreminder;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;

/**
 * 打卡音效：一声短促的「啵」，音量压得很低，可全局静音。
 */
final class SoundKit {
    private static final String PREFS = "sound_settings";
    private static final String KEY_ON = "sound_on";

    private SoundKit() { }

    static boolean enabled(Context context) {
        return context.getSharedPreferences(PREFS, 0).getBoolean(KEY_ON, true);
    }

    static void setEnabled(Context context, boolean on) {
        context.getSharedPreferences(PREFS, 0).edit().putBoolean(KEY_ON, on).apply();
    }

    /** 打勾「啵」一声，约 0.15 秒，音量 35%。 */
    static void tick(Context context) {
        if (!enabled(context)) return;
        try {
            final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35);
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 150);
            new Handler(Looper.getMainLooper()).postDelayed(tone::release, 500);
        } catch (Exception ignored) {
            // 静音模式或音频设备占用时直接跳过，不影响打卡
        }
    }

    /** 升级/开箱时两个上扬音阶。 */
    static void levelUp(Context context) {
        if (!enabled(context)) return;
        try {
            final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 40);
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    tone.startTone(ToneGenerator.TONE_PROP_ACK, 160);
                } catch (Exception ignored) { }
            }, 140);
            new Handler(Looper.getMainLooper()).postDelayed(tone::release, 700);
        } catch (Exception ignored) { }
    }
}
