package io.iftech.healthreminder;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** 任务（module=task）的日期过滤：设成「之后」的任务今天不显示也不提醒。 */
final class Tasks {
    private Tasks() { }

    static List<ReminderSettings.Rule> all(Context context) {
        return ReminderSettings.moduleRules(context, ReminderSettings.MODULE_TASK);
    }

    /** 今天该出现在清单里的任务：单次任务只在当天及以后到期前显示，每周任务只看星期。 */
    static List<ReminderSettings.Rule> today(Context context) {
        List<ReminderSettings.Rule> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ReminderSettings.Rule task : all(context)) {
            if (isForToday(task, now)) result.add(task);
        }
        return result;
    }

    static boolean isForToday(ReminderSettings.Rule task, long now) {
        if (task.isOnceSchedule()) return task.onceAt <= endOfDay(now);
        if (task.isWeeklySchedule()) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(now);
            return task.matchesWeekday(cal.get(Calendar.DAY_OF_WEEK));
        }
        return true;
    }

    static long endOfDay(long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        return cal.getTimeInMillis();
    }
}
