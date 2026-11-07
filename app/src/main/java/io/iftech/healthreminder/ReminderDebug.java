package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 记录最近几次闹钟触发情况，给「提醒诊断」用。 */
final class ReminderDebug {
    private static final String PREFS = "reminder_debug";
    private static final String KEY = "last";
    private static final int MAX_CHARS = 6000;

    private ReminderDebug() { }

    static void note(Context context, String message) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String line = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(new Date())
                    + "  " + message;
            String old = prefs.getString(KEY, "");
            String all = old.isEmpty() ? line : line + "\n" + old;
            if (all.length() > MAX_CHARS) all = all.substring(0, MAX_CHARS);
            prefs.edit().putString(KEY, all).apply();
        } catch (Throwable ignored) {
            // 诊断日志失败不能影响真正的闹钟。
        }
    }

    static String all(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "");
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply();
    }
}
