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
    private static final String KEY_PROMPTED = "prompted";
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
                .edit().putString(KEY_LAST, text).remove(KEY_PROMPTED).apply();
    }

    private static boolean prompted;

    /** 主界面启动时提示上次崩溃，方便用户直接把堆栈发出来。 */
    static void promptIfAny(final android.app.Activity activity) {
        if (prompted) return;
        String text = last(activity);
        if (text.isEmpty()) return;
        // 同一份日志只弹一次：旧版本留下的崩溃翻来覆去地弹，
        // 会让人以为现在还在闪退。日志本体保留在设置里可查。
        android.content.SharedPreferences prefs =
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_PROMPTED, false)) return;
        prompted = true;
        prefs.edit().putBoolean(KEY_PROMPTED, true).apply();

        String rawVersion = BuildConfig.VERSION_NAME;
        int versionStart = text.indexOf("版本：");
        int versionEnd = versionStart < 0 ? -1 : text.indexOf('\n', versionStart);
        if (versionStart >= 0 && versionEnd > versionStart) {
            rawVersion = text.substring(versionStart + 3, versionEnd).trim();
        }
        boolean stale = !rawVersion.startsWith(BuildConfig.VERSION_NAME)
                && !BuildConfig.VERSION_NAME.startsWith(rawVersion.split(" ")[0]);
        String bodyText = (stale
                ? "※ 这条日志来自旧版本 " + rawVersion + "，当前是 " + BuildConfig.VERSION_NAME
                + "，可能已经修好了。先升级到最新版再看。\n\n" : "")
                + (text.length() > 6000 ? text.substring(0, 6000) + "\n…" : text);

        android.widget.TextView body = new android.widget.TextView(activity);
        body.setText(bodyText);
        body.setTextSize(11);
        body.setTextColor(UiPalette.INK);
        body.setTextIsSelectable(true);
        body.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.addView(body);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(activity);
        frame.addView(scroll, new android.widget.FrameLayout.LayoutParams(-1, dp(activity, 320)));

        new android.app.AlertDialog.Builder(activity)
                .setTitle("上次运行崩溃了（" + rawVersion + "）")
                .setView(frame)
                .setPositiveButton("复制日志", (dialog, which) -> {
                    copy(activity, text);
                    android.widget.Toast.makeText(activity, "已复制，发给开发者即可",
                            android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static String last(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_LAST, "");
    }

    static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_LAST).apply();
    }

    static void share(android.app.Activity activity, String text) {
        android.content.Intent send = new android.content.Intent(android.content.Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(android.content.Intent.EXTRA_SUBJECT, "休息豆芽崩溃日志")
                .putExtra(android.content.Intent.EXTRA_TEXT, text);
        activity.startActivity(android.content.Intent.createChooser(send, "发送崩溃日志"));
    }

    static void copy(Context context, String text) {
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("崩溃日志", text));
    }
}
