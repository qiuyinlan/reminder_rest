package io.iftech.healthreminder;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.util.Calendar;
import java.util.List;

final class ReminderScheduler {
    private static final int REQUEST_BASE = 71000;
    private static final int SNOOZE_REQUEST_BASE = 72000;
    private static final int LEGACY_SNOOZE_REQUEST = 704;
    private static final String SCHEDULE_PREFS = "reminder_schedule";
    private ReminderScheduler() { }

    static void update(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        List<ReminderSettings.Rule> rules = ReminderSettings.rules(context);
        boolean enabled = ReminderSettings.enabled(context);
        for (ReminderSettings.Rule rule : rules) {
            alarms.cancel(pendingIntent(context, rule.id, null));
            alarms.cancel(pendingIntent(context, rule.id, rule.title));
            if (!enabled || !rule.enabled) cancelSnooze(context, rule.id);
        }
        if (!enabled) {
            cancelSnooze(context, -1);
            alarms.cancel(snoozePendingIntent(context, LEGACY_SNOOZE_REQUEST, null, null));
            return;
        }
        for (ReminderSettings.Rule rule : rules) {
            if (rule.enabled) {
                long next = scheduledAt(context, rule.id);
                if (next <= System.currentTimeMillis()) next = calculateNext(context, rule);
                setScheduledAt(context, rule.id, next);
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent(context, rule.id, rule.title));
            }
        }
    }

    static void cancelRule(Context context, int id) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(pendingIntent(context, id, null));
        cancelSnooze(context, id);
        resetNext(context, id);
    }

    static long scheduledAt(Context context, int ruleId) {
        return context.getSharedPreferences(SCHEDULE_PREFS, Context.MODE_PRIVATE).getLong(scheduleKey(ruleId), 0L);
    }

    static void resetNext(Context context, int ruleId) {
        context.getSharedPreferences(SCHEDULE_PREFS, Context.MODE_PRIVATE).edit().remove(scheduleKey(ruleId)).apply();
    }

    private static String scheduleKey(int ruleId) { return "next_" + ruleId; }

    private static void setScheduledAt(Context context, int ruleId, long timeMillis) {
        context.getSharedPreferences(SCHEDULE_PREFS, Context.MODE_PRIVATE).edit()
                .putLong(scheduleKey(ruleId), timeMillis).apply();
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
        long next = calculateNext(context, rule);
        setScheduledAt(context, rule.id, next);
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pendingIntent(context, rule.id, rule.title));
    }

    private static long calculateNext(Context context, ReminderSettings.Rule rule) {
        long now = System.currentTimeMillis();
        if (!rule.remindAfterTarget && ReminderSettings.completedToday(context, rule.id) >= rule.dailyTarget) {
            Calendar tomorrowStart = Calendar.getInstance();
            tomorrowStart.setTimeInMillis(now);
            tomorrowStart.add(Calendar.DAY_OF_YEAR, 1);
            tomorrowStart.set(Calendar.HOUR_OF_DAY, rule.start / 60);
            tomorrowStart.set(Calendar.MINUTE, rule.start % 60);
            tomorrowStart.set(Calendar.SECOND, 0);
            tomorrowStart.set(Calendar.MILLISECOND, 0);
            return nextTime(rule, tomorrowStart.getTimeInMillis());
        }
        return nextTime(rule, now);
    }

    static void scheduleSnooze(Context context, int minutes, String action, int ruleId) {
        scheduleSnooze(context, minutes, action, ruleId, false);
    }

    static void scheduleCustomSnooze(Context context, int minutes, String action, int ruleId) {
        if (ruleId >= 0) {
            AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarms != null) alarms.cancel(pendingIntent(context, ruleId, null));
            resetNext(context, ruleId);
        }
        scheduleSnooze(context, minutes, action, ruleId, true);
    }

    private static void scheduleSnooze(Context context, int minutes, String action, int ruleId,
                                       boolean resumeSchedule) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class).putExtra("snooze", true).putExtra("action", action)
                .putExtra("rule_id", ruleId).putExtra("resume_schedule", resumeSchedule);
        int requestCode = SNOOZE_REQUEST_BASE + Math.max(0, ruleId);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + minutes * 60_000L, pending);
    }

    private static void cancelSnooze(Context context, int ruleId) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(snoozePendingIntent(context, SNOOZE_REQUEST_BASE + Math.max(0, ruleId), null, null));
    }

    private static PendingIntent snoozePendingIntent(Context context, int requestCode, String action, Integer ruleId) {
        Intent intent = new Intent(context, ReminderReceiver.class).putExtra("snooze", true);
        if (action != null) intent.putExtra("action", action);
        if (ruleId != null) intent.putExtra("rule_id", ruleId);
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
