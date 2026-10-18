package io.iftech.healthreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.util.Calendar;
import java.util.List;

final class ReminderScheduler {
    private static final int REQUEST_BASE = 71000;
    private ReminderScheduler() { }

    static void update(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        for (ReminderSettings.Rule rule : ReminderSettings.rules(context)) {
            alarms.cancel(pendingIntent(context, rule.id, null));
            alarms.cancel(pendingIntent(context, rule.id, rule.title));
        }
        if (!ReminderSettings.enabled(context)) return;
        for (ReminderSettings.Rule rule : ReminderSettings.rules(context)) {
            if (rule.enabled) scheduleNext(context, rule);
        }
    }

    static void cancelRule(Context context, int id) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(pendingIntent(context, id, null));
    }

    static void scheduleNext(Context context, int ruleId) {
        for (ReminderSettings.Rule rule : ReminderSettings.rules(context)) {
            if (rule.id == ruleId && rule.enabled && ReminderSettings.enabled(context)) {
                scheduleNext(context, rule);
                return;
            }
        }
    }

    private static void scheduleNext(Context context, ReminderSettings.Rule rule) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        long now = System.currentTimeMillis();
        long next;
        if (!rule.remindAfterTarget && ReminderSettings.completedToday(context, rule.id) >= rule.dailyTarget) {
            Calendar tomorrowStart = Calendar.getInstance();
            tomorrowStart.setTimeInMillis(now);
            tomorrowStart.add(Calendar.DAY_OF_YEAR, 1);
            tomorrowStart.set(Calendar.HOUR_OF_DAY, rule.start / 60);
            tomorrowStart.set(Calendar.MINUTE, rule.start % 60);
            tomorrowStart.set(Calendar.SECOND, 0);
            tomorrowStart.set(Calendar.MILLISECOND, 0);
            next = nextTime(rule, tomorrowStart.getTimeInMillis());
        } else {
            next = nextTime(rule, now);
        }
        setAlarm(context, alarms, next, pendingIntent(context, rule.id, rule.title));
    }

    static void scheduleSnooze(Context context, int minutes, String action) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class).putExtra("snooze", true).putExtra("action", action);
        PendingIntent pending = PendingIntent.getBroadcast(context, 704, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(context, alarms, System.currentTimeMillis() + minutes * 60_000L, pending);
    }

    private static void setAlarm(Context context, AlarmManager alarms, long triggerAt, PendingIntent pending) {
        if (Build.VERSION.SDK_INT >= 31 && ReminderSettings.exactAlarmsAllowed(context)) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
                return;
            } catch (SecurityException ignored) { }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending);
    }

    private static PendingIntent pendingIntent(Context context, int id, String title) {
        Intent intent = new Intent(context, ReminderReceiver.class).putExtra("rule_id", id);
        if (title != null) intent.putExtra("action", title);
        return PendingIntent.getBroadcast(context, REQUEST_BASE + id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static long nextTime(ReminderSettings.Rule rule, long nowMillis) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(nowMillis);
        Calendar start = (Calendar) now.clone();
        start.set(Calendar.HOUR_OF_DAY, rule.start / 60);
        start.set(Calendar.MINUTE, rule.start % 60);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) now.clone();
        end.set(Calendar.HOUR_OF_DAY, rule.end / 60);
        end.set(Calendar.MINUTE, rule.end % 60);
        end.set(Calendar.SECOND, 0);
        end.set(Calendar.MILLISECOND, 0);

        if (rule.start == rule.end) {
            Calendar next = (Calendar) now.clone();
            next.add(Calendar.MINUTE, rule.interval);
            return next.getTimeInMillis();
        }

        boolean overnight = rule.end < rule.start;
        if (overnight) end.add(Calendar.DAY_OF_YEAR, 1);

        if (overnight && now.before(start) && now.before(end)) {
            Calendar previousEnd = (Calendar) end.clone();
            previousEnd.add(Calendar.DAY_OF_YEAR, -1);
            if (now.before(previousEnd)) {
                Calendar next = (Calendar) now.clone();
                next.add(Calendar.MINUTE, rule.interval);
                if (next.before(end)) return next.getTimeInMillis();
            }
        } else if (!now.before(start) && now.before(end)) {
            Calendar next = (Calendar) now.clone();
            next.add(Calendar.MINUTE, rule.interval);
            if (next.before(end)) return next.getTimeInMillis();
        }

        if (now.before(start)) {
            start.add(Calendar.MINUTE, rule.interval);
            return start.getTimeInMillis();
        }
        start.add(Calendar.DAY_OF_YEAR, 1);
        start.add(Calendar.MINUTE, rule.interval);
        return start.getTimeInMillis();
    }
}
