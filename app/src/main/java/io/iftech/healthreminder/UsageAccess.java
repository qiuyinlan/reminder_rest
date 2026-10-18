package io.iftech.healthreminder;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.os.Process;

import java.util.Calendar;
import java.util.List;
import java.util.Set;

final class UsageAccess {
    private UsageAccess() { }

    static boolean hasAccess(Context context) {
        AppOpsManager ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        if (ops == null) return false;
        int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static long todayMillis(Context context) {
        if (!hasAccess(context)) return -1;
        Calendar start = Calendar.getInstance();
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) return 0;
        List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start.getTimeInMillis(), System.currentTimeMillis());
        if (stats == null) return 0;
        Set<String> excluded = ReminderSettings.whitelist(context);
        excluded.add(context.getPackageName());
        long total = 0;
        for (UsageStats stat : stats) {
            if (!excluded.contains(stat.getPackageName())) total += stat.getTotalTimeInForeground();
        }
        return total;
    }

    static long recentMillis(Context context, long from, long to) {
        if (!hasAccess(context)) return -1;
        UsageStatsManager manager = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        if (manager == null) return 0;
        List<UsageStats> stats = manager.queryUsageStats(UsageStatsManager.INTERVAL_BEST, from, to);
        if (stats == null) return 0;
        Set<String> excluded = ReminderSettings.whitelist(context);
        excluded.add(context.getPackageName());
        long total = 0;
        for (UsageStats stat : stats) {
            if (!excluded.contains(stat.getPackageName())) total += stat.getTotalTimeInForeground();
        }
        return total;
    }

    static String format(long millis) {
        if (millis < 0) return "未授权";
        long mins = millis / 60_000;
        if (mins < 60) return mins + " 分钟";
        return (mins / 60) + " 小时 " + (mins % 60) + " 分钟";
    }
}
