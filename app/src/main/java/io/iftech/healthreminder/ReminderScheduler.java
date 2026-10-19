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
    private static final String KEY_SCREEN_OFF_PAUSED = "screen_off_paused";
    private static final String KEY_SCREEN_OFF_ALARM_SESSION_RULE = "screen_off_alarm_session_rule";
    private ReminderScheduler() { }

    static void update(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        if (!ReminderSettings.enabled(context) || !ReminderSettings.screenOffAlarm(context)) {
            ReminderReceiver.stopAlarmVibration(context, "reminders or screen-off alarm disabled");
        }
        if (isInteractive(context) && isPaused(context)) resumeAfterScreenOn(context);
        List<ReminderSettings.Rule> rules = ReminderSettings.rules(context);
        boolean enabled = ReminderSettings.enabled(context);
        int sessionRuleId = schedulePrefs(context).getInt(KEY_SCREEN_OFF_ALARM_SESSION_RULE, -1);
        if (sessionRuleId >= 0) {
            boolean sessionRuleEnabled = false;
            for (ReminderSettings.Rule rule : rules) {
                if (rule.id == sessionRuleId && rule.enabled) {
                    sessionRuleEnabled = true;
                    break;
                }
            }
            if (!sessionRuleEnabled) {
                ReminderReceiver.stopAlarmVibration(context, "session rule disabled or removed");
            }
        }
        for (ReminderSettings.Rule rule : rules) {
            alarms.cancel(pendingIntent(context, rule.id, null));
            alarms.cancel(pendingIntent(context, rule.id, rule.title));
            if (!enabled || !rule.enabled) cancelSnooze(context, rule.id);
        }
        if (!enabled) {
            cancelSnooze(context, -1);
            alarms.cancel(snoozePendingIntent(context, LEGACY_SNOOZE_REQUEST, null, null));
            clearPauseState(context, rules);
            return;
        }
        if (!ReminderSettings.screenOffAlarm(context) && !isInteractive(context)) {
            pauseForScreenOff(context);
            return;
        }
        for (ReminderSettings.Rule rule : rules) {
            if (rule.enabled) {
                long next = scheduledAt(context, rule.id);
                long now = System.currentTimeMillis();
                if (next <= 0L) {
                    next = calculateNext(context, rule);
                } else if (next <= now && (!ReminderSettings.isWithinWindow(rule, now)
                        || (!rule.remindAfterTarget
                        && ReminderSettings.completedToday(context, rule.id) >= rule.dailyTarget))) {
                    next = calculateNext(context, rule);
                }
                setScheduledAt(context, rule.id, next);
                setAlarm(context, alarms, next, pendingIntent(context, rule.id, rule.title));
            }
        }
    }

    static void cancelRule(Context context, int id) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(pendingIntent(context, id, null));
        cancelSnooze(context, id);
        resetNext(context, id);
        schedulePrefs(context).edit().remove(remainingKey(id)).apply();
    }

    static long scheduledAt(Context context, int ruleId) {
        return context.getSharedPreferences(SCHEDULE_PREFS, Context.MODE_PRIVATE).getLong(scheduleKey(ruleId), 0L);
    }

    static boolean screenOffAlarmSessionActive(Context context, int ruleId) {
        return schedulePrefs(context).getInt(KEY_SCREEN_OFF_ALARM_SESSION_RULE, -1) == ruleId;
    }

    static void beginScreenOffAlarmSession(Context context, int ruleId) {
        schedulePrefs(context).edit().putInt(KEY_SCREEN_OFF_ALARM_SESSION_RULE, ruleId).apply();
    }

    static void clearScreenOffAlarmSession(Context context, String reason) {
        int ruleId = schedulePrefs(context).getInt(KEY_SCREEN_OFF_ALARM_SESSION_RULE, -1);
        schedulePrefs(context).edit().remove(KEY_SCREEN_OFF_ALARM_SESSION_RULE).apply();
        android.util.Log.i("HealthReminderAlarm", "screen-off alarm session cleared rule="
                + ruleId + " reason=" + reason);
    }

    static void resetNext(Context context, int ruleId) {
        schedulePrefs(context).edit().remove(scheduleKey(ruleId)).remove(remainingKey(ruleId)).apply();
    }

    private static String scheduleKey(int ruleId) { return "next_" + ruleId; }

    private static void setScheduledAt(Context context, int ruleId, long timeMillis) {
        schedulePrefs(context).edit()
                .putLong(scheduleKey(ruleId), timeMillis).apply();
    }

    private static android.content.SharedPreferences schedulePrefs(Context context) {
        return context.getSharedPreferences(SCHEDULE_PREFS, Context.MODE_PRIVATE);
    }

    private static String remainingKey(int ruleId) { return "remaining_" + ruleId; }

    private static boolean isPaused(Context context) {
        return schedulePrefs(context).getBoolean(KEY_SCREEN_OFF_PAUSED, false);
    }

    private static boolean isInteractive(Context context) {
        android.os.PowerManager power = (android.os.PowerManager) context.getSystemService(Context.POWER_SERVICE);
        return power == null || power.isInteractive();
    }

    static void pauseForScreenOff(Context context) {
        if (!ReminderSettings.enabled(context) || ReminderSettings.screenOffAlarm(context)
                || isPaused(context)) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        long now = System.currentTimeMillis();
        android.content.SharedPreferences.Editor editor = schedulePrefs(context).edit()
                .putBoolean(KEY_SCREEN_OFF_PAUSED, true);
        for (ReminderSettings.Rule rule : ReminderSettings.rules(context)) {
            if (!rule.enabled) {
                editor.remove(remainingKey(rule.id)).remove(snoozeRemainingKey(rule.id));
                continue;
            }
            long next = scheduledAt(context, rule.id);
            if (next > 0L) editor.putLong(remainingKey(rule.id), Math.max(0L, next - now));
            alarms.cancel(pendingIntent(context, rule.id, null));
            alarms.cancel(pendingIntent(context, rule.id, rule.title));
            long snoozeAt = schedulePrefs(context).getLong(snoozeAtKey(rule.id), 0L);
            if (snoozeAt > 0L) {
                editor.putLong(snoozeRemainingKey(rule.id), Math.max(0L, snoozeAt - now));
                alarms.cancel(snoozePendingIntent(context, SNOOZE_REQUEST_BASE + Math.max(0, rule.id), null, null));
            }
        }
        long legacySnoozeAt = schedulePrefs(context).getLong(snoozeAtKey(-1), 0L);
        if (legacySnoozeAt > 0L) {
            editor.putLong(snoozeRemainingKey(-1), Math.max(0L, legacySnoozeAt - now));
            alarms.cancel(snoozePendingIntent(context, SNOOZE_REQUEST_BASE, null, null));
        }
        editor.apply();
    }

    static void resumeAfterScreenOn(Context context) {
        if (!isPaused(context) || !ReminderSettings.enabled(context)) return;
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        long now = System.currentTimeMillis();
        android.content.SharedPreferences prefs = schedulePrefs(context);
        android.content.SharedPreferences.Editor editor = prefs.edit().putBoolean(KEY_SCREEN_OFF_PAUSED, false);
        for (ReminderSettings.Rule rule : ReminderSettings.rules(context)) {
            if (!rule.enabled) {
                editor.remove(remainingKey(rule.id)).remove(snoozeRemainingKey(rule.id));
                continue;
            }
            long remaining = prefs.getLong(remainingKey(rule.id), -1L);
            long scheduled = scheduledAt(context, rule.id);
            if (remaining >= 0L || scheduled > 0L) {
                long next = remaining >= 0L ? now + remaining
                        : scheduled > now ? scheduled : calculateNext(context, rule);
                setScheduledAt(context, rule.id, next);
                alarms.cancel(pendingIntent(context, rule.id, null));
                alarms.cancel(pendingIntent(context, rule.id, rule.title));
                setAlarm(context, alarms, next, pendingIntent(context, rule.id, rule.title));
                editor.remove(remainingKey(rule.id));
            }

            long snoozeRemaining = prefs.getLong(snoozeRemainingKey(rule.id), -1L);
            if (snoozeRemaining >= 0L) {
                String action = prefs.getString(snoozeActionKey(rule.id), rule.title);
                boolean resumeSchedule = prefs.getBoolean(snoozeResumeKey(rule.id), false);
                scheduleSnoozeAt(context, rule.id, action, resumeSchedule, now + snoozeRemaining);
                editor.remove(snoozeRemainingKey(rule.id));
            }
        }
        long legacyRemaining = prefs.getLong(snoozeRemainingKey(-1), -1L);
        if (legacyRemaining >= 0L) {
            String action = prefs.getString(snoozeActionKey(-1), "");
            boolean resumeSchedule = prefs.getBoolean(snoozeResumeKey(-1), false);
            scheduleSnoozeAt(context, -1, action, resumeSchedule, now + legacyRemaining);
            editor.remove(snoozeRemainingKey(-1));
        }
        editor.apply();
    }

    private static void clearPauseState(Context context, List<ReminderSettings.Rule> rules) {
        android.content.SharedPreferences.Editor editor = schedulePrefs(context).edit()
                .remove(KEY_SCREEN_OFF_PAUSED);
        for (ReminderSettings.Rule rule : rules) {
            editor.remove(remainingKey(rule.id)).remove(snoozeRemainingKey(rule.id));
        }
        editor.remove(snoozeRemainingKey(-1));
        editor.apply();
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
        setAlarm(context, alarms, next, pendingIntent(context, rule.id, rule.title));
    }

    private static void setAlarm(Context context, AlarmManager alarms, long time, PendingIntent pending) {
        if (ReminderSettings.screenOffAlarm(context)
                && android.os.Build.VERSION.SDK_INT >= 21
                && (android.os.Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms())) {
            Intent showApp = new Intent(context, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent show = PendingIntent.getActivity(context, 73000, showApp,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(time, show), pending);
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 31 && alarms.canScheduleExactAlarms()) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending);
                return;
            } catch (SecurityException ignored) {
                // Fall back if exact-alarm access was revoked after the permission check.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending);
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
        scheduleSnoozeAt(context, ruleId, action, resumeSchedule,
                System.currentTimeMillis() + minutes * 60_000L);
    }

    private static void scheduleSnoozeAt(Context context, int ruleId, String action,
                                         boolean resumeSchedule, long atMillis) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        Intent intent = new Intent(context, ReminderReceiver.class).putExtra("snooze", true).putExtra("action", action)
                .putExtra("rule_id", ruleId).putExtra("resume_schedule", resumeSchedule);
        int requestCode = SNOOZE_REQUEST_BASE + Math.max(0, ruleId);
        PendingIntent pending = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        schedulePrefs(context).edit().putLong(snoozeAtKey(ruleId), atMillis)
                .putString(snoozeActionKey(ruleId), action)
                .putBoolean(snoozeResumeKey(ruleId), resumeSchedule).apply();
        setAlarm(context, alarms, atMillis, pending);
    }

    private static void cancelSnooze(Context context, int ruleId) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(snoozePendingIntent(context, SNOOZE_REQUEST_BASE + Math.max(0, ruleId), null, null));
        schedulePrefs(context).edit().remove(snoozeAtKey(ruleId)).remove(snoozeActionKey(ruleId))
                .remove(snoozeResumeKey(ruleId)).remove(snoozeRemainingKey(ruleId)).apply();
    }

    static void consumeSnooze(Context context, int ruleId) {
        schedulePrefs(context).edit().remove(snoozeAtKey(ruleId)).remove(snoozeActionKey(ruleId))
                .remove(snoozeResumeKey(ruleId)).remove(snoozeRemainingKey(ruleId)).apply();
    }

    private static String snoozeAtKey(int id) { return "snooze_at_" + id; }
    private static String snoozeActionKey(int id) { return "snooze_action_" + id; }
    private static String snoozeResumeKey(int id) { return "snooze_resume_" + id; }
    private static String snoozeRemainingKey(int id) { return "snooze_remaining_" + id; }

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
            next.add(Calendar.SECOND, rule.intervalSeconds);
            return next.getTimeInMillis();
        }

        boolean overnight = rule.end < rule.start;
        if (overnight) end.add(Calendar.DAY_OF_YEAR, 1);

        if (overnight && now.before(start) && now.before(end)) {
            Calendar previousEnd = (Calendar) end.clone();
            previousEnd.add(Calendar.DAY_OF_YEAR, -1);
            if (now.before(previousEnd)) {
                Calendar next = (Calendar) now.clone();
                next.add(Calendar.SECOND, rule.intervalSeconds);
                if (next.before(end)) return next.getTimeInMillis();
            }
        } else if (!now.before(start) && now.before(end)) {
            Calendar next = (Calendar) now.clone();
            next.add(Calendar.SECOND, rule.intervalSeconds);
            if (next.before(end)) return next.getTimeInMillis();
        }

        if (now.before(start)) {
            start.add(Calendar.SECOND, rule.intervalSeconds);
            return start.getTimeInMillis();
        }
        start.add(Calendar.DAY_OF_YEAR, 1);
        start.add(Calendar.SECOND, rule.intervalSeconds);
        return start.getTimeInMillis();
    }
}
