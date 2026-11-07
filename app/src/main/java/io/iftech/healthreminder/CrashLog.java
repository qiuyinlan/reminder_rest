package io.iftech.healthreminder;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 记住最后一次闪退的堆栈，方便在设置里复制出来排查。 */
final class CrashLog {
    private static final String PREFS = "crash_log";
    private static final String KEY_LAST = "last";
    private static final int MAX_CHARS = 20000;

    private CrashLog() { }

    static void install(final Context context) {
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            try {
                save(context.getApplicationContext(), error);
            } catch (Throwable ignored) {
                // 记录失败也不能挡住系统原本的崩溃处理。
            }
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }

    static void save(Context context, Throwable error) {
        if (error == null) return;
        StringWriter buffer = new StringWriter();
        PrintWriter writer = new PrintWriter(buffer);
        writer.println("时间：" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date()));
        writer.println("设备：" + Build.MANUFACTURER + " " + Build.MODEL
                + " / Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        writer.println("版本：" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        writer.println();
        error.printStackTrace(writer);
        writer.flush();
        String text = buffer.toString();
        if (text.length() > MAX_CHARS) text = text.substring(0, MAX_CHARS) + "\n…";
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY_LAST, text).apply();
    }

    static String last(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST, "");
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LAST).apply();
    }

    static void copy(Context context, String text) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("崩溃日志", text));
    }
}
