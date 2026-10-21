package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

final class ReminderSettings {
    static final String[] TYPES = {"喝水", "护眼", "走动", "放松颈椎"};
    static final String KEY_ENABLED = "enabled";
    private static final String PREFS = "reminder_settings";
    private static final String RULES_KEY = "reminder_rules_v2";

    static final class Rule {
        final int id;
        final String title;
        /** 自定义图标（单个字符/符号）；为空时显示标题首字。 */
        final String icon;
        final String reminderMessage;
        final int start;
        final int end;
        final int interval;
        final int intervalSeconds;
        final int dailyTarget;
        final boolean remindAfterTarget;
        final boolean enabled;
        final boolean specificTimeMode;
        final List<Integer> timePointsSeconds;

        Rule(int id, String title, int start, int end, int interval, boolean enabled) {
            this(id, title, start, end, interval, 3, false, enabled);
        }
        Rule(int id, String title, int start, int end, int interval, int dailyTarget, boolean enabled) {
            this(id, title, start, end, interval, dailyTarget, false, enabled);
        }
        Rule(int id, String title, int start, int end, int interval, int dailyTarget, boolean remindAfterTarget, boolean enabled) {
            this(id, title, start, end, interval, interval * 60, dailyTarget, remindAfterTarget, enabled);
        }
        private Rule(int id, String title, int start, int end, int interval, int intervalSeconds,
                     int dailyTarget, boolean remindAfterTarget, boolean enabled) {
            this(id, title, start, end, interval, intervalSeconds, dailyTarget, remindAfterTarget,
                    enabled, false, new ArrayList<>());
        }

        private Rule(int id, String title, int start, int end, int interval, int intervalSeconds,
                     int dailyTarget, boolean remindAfterTarget, boolean enabled,
                     boolean specificTimeMode, List<Integer> timePointsSeconds) {
            this(id, title, start, end, interval, intervalSeconds, dailyTarget, remindAfterTarget,
                    enabled, specificTimeMode, timePointsSeconds, "");
        }

        private Rule(int id, String title, int start, int end, int interval, int intervalSeconds,
                     int dailyTarget, boolean remindAfterTarget, boolean enabled,
                     boolean specificTimeMode, List<Integer> timePointsSeconds, String reminderMessage) {
            this(id, title, start, end, interval, intervalSeconds, dailyTarget, remindAfterTarget,
                    enabled, specificTimeMode, timePointsSeconds, reminderMessage, "");
        }

        private Rule(int id, String title, int start, int end, int interval, int intervalSeconds,
                     int dailyTarget, boolean remindAfterTarget, boolean enabled,
                     boolean specificTimeMode, List<Integer> timePointsSeconds,
                     String reminderMessage, String icon) {
            this.id = id;
            this.title = title;
            this.icon = icon == null ? "" : icon.trim();
            this.reminderMessage = reminderMessage == null ? "" : reminderMessage.trim();
            this.start = start;
            this.end = end;
            this.interval = interval;
            this.intervalSeconds = Math.max(1, intervalSeconds);
            this.dailyTarget = Math.max(1, dailyTarget);
            this.remindAfterTarget = remindAfterTarget;
            this.enabled = enabled;
            this.specificTimeMode = specificTimeMode;
            TreeSet<Integer> sortedTimes = new TreeSet<>();
            if (timePointsSeconds != null) {
                for (int seconds : timePointsSeconds) {
                    if (seconds >= 0 && seconds < 24 * 60 * 60) sortedTimes.add(seconds);
                }
            }
            this.timePointsSeconds = java.util.Collections.unmodifiableList(new ArrayList<>(sortedTimes));
        }

        static Rule withIntervalSeconds(int id, String title, int start, int end, int intervalSeconds,
                                        int dailyTarget, boolean remindAfterTarget, boolean enabled) {
            return new Rule(id, title, start, end, Math.max(1, (intervalSeconds + 59) / 60), intervalSeconds,
                    dailyTarget, remindAfterTarget, enabled);
        }

        static Rule withSpecificTimes(int id, String title, int start, int end, int intervalSeconds,
                                      List<Integer> timePointsSeconds, int dailyTarget,
                                      boolean remindAfterTarget, boolean enabled) {
            return new Rule(id, title, start, end, Math.max(1, (intervalSeconds + 59) / 60), intervalSeconds,
                    dailyTarget, remindAfterTarget, enabled, true, timePointsSeconds);
        }

        Rule withReminderMessage(String message) {
            return new Rule(id, title, start, end, interval, intervalSeconds, dailyTarget,
                    remindAfterTarget, enabled, specificTimeMode, timePointsSeconds, message, icon);
        }

        Rule withIcon(String value) {
            return new Rule(id, title, start, end, interval, intervalSeconds, dailyTarget,
                    remindAfterTarget, enabled, specificTimeMode, timePointsSeconds, reminderMessage, value);
        }

        Rule withEnabled(boolean value) {
            return new Rule(id, title, start, end, interval, intervalSeconds, dailyTarget,
                    remindAfterTarget, value, specificTimeMode, timePointsSeconds, reminderMessage, icon);
        }

        /** 卡片徽标文字：自定义图标优先，否则取标题首字。 */
        String badgeText() {
            if (!icon.isEmpty()) return icon;
            return title.isEmpty() ? "·" : title.substring(0, 1);
        }

    }

    private ReminderSettings() { }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context context) { return prefs(context).getBoolean(KEY_ENABLED, false); }
    static boolean usageOnly(Context context) { return prefs(context).getBoolean("usage_only", false); }
    static boolean blockEnabled(Context context) { return prefs(context).getBoolean("block_enabled", false); }
    static boolean screenOffAlarm(Context context) { return prefs(context).getBoolean("screen_off_alarm", false); }
    static boolean screenOffSound(Context context) { return prefs(context).getBoolean("screen_off_sound", true); }
    static boolean screenOffVibrate(Context context) { return prefs(context).getBoolean("screen_off_vibrate", false); }
    static String screenOffRingtone(Context context) { return prefs(context).getString("screen_off_ringtone", null); }

    static Set<String> whitelist(Context context) {
        return new HashSet<>(prefs(context).getStringSet("whitelist", new HashSet<>()));
    }

    static Set<String> blockPackages(Context context) {
        return new HashSet<>(prefs(context).getStringSet("block_packages", new HashSet<>()));
    }

    static void saveWhitelist(Context context, Set<String> packages) {
        prefs(context).edit().putStringSet("whitelist", new HashSet<>(packages)).apply();
        ReminderScheduler.update(context);
    }

    static List<Rule> rules(Context context) {
        String encoded = prefs(context).getString(RULES_KEY, null);
        if (encoded == null) return migrateRules(context);
        ArrayList<Rule> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(encoded);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                int intervalSeconds = item.has("intervalSeconds")
                        ? item.getInt("intervalSeconds") : item.getInt("interval") * 60;
                Rule rule = Rule.withIntervalSeconds(item.getInt("id"), item.getString("title"), item.getInt("start"),
                        item.getInt("end"), intervalSeconds, item.optInt("dailyTarget", 3),
                        item.optBoolean("remindAfterTarget", false), item.optBoolean("enabled", true));
                if ("times".equals(item.optString("scheduleMode", "interval"))) {
                    ArrayList<Integer> times = new ArrayList<>();
                    JSONArray points = item.optJSONArray("timePointsSeconds");
                    if (points != null) {
                        for (int j = 0; j < points.length(); j++) {
                            int seconds = points.optInt(j, -1);
                            if (seconds >= 0 && seconds < 24 * 60 * 60) times.add(seconds);
                        }
                    }
                    rule = Rule.withSpecificTimes(rule.id, rule.title, rule.start, rule.end, rule.intervalSeconds,
                            times, rule.dailyTarget, rule.remindAfterTarget, rule.enabled);
                }
                result.add(rule.withReminderMessage(item.optString("reminderMessage", ""))
                        .withIcon(item.optString("icon", "")));
            }
        } catch (JSONException ignored) { }
        return result;
    }

    private static List<Rule> migrateRules(Context context) {
        SharedPreferences p = prefs(context);
        Set<String> selected = p.getStringSet("types", new HashSet<>(java.util.Arrays.asList(TYPES)));
        ArrayList<Rule> result = new ArrayList<>();
        int id = 1;
        for (String title : TYPES) {
            if (!selected.contains(title)) continue;
            result.add(new Rule(id++, title, p.getInt("start_minute", 9 * 60),
                    p.getInt("end_minute", 18 * 60), p.getInt("interval", 30), true));
        }
        writeRules(context, result);
        return result;
    }

    static void saveRules(Context context, List<Rule> rules) {
        List<Rule> previousRules = rules(context);
        for (Rule previous : previousRules) {
            Rule updated = findRule(rules, previous.id);
            if (updated == null || !sameRule(previous, updated)) {
                ReminderScheduler.cancelRule(context, previous.id);
            }
        }
        for (Rule updated : rules) {
            if (findRule(previousRules, updated.id) == null) {
                ReminderScheduler.resetNext(context, updated.id);
            }
        }
        writeRules(context, rules);
        ReminderScheduler.update(context);
    }

    private static Rule findRule(List<Rule> rules, int id) {
        for (Rule rule : rules) if (rule.id == id) return rule;
        return null;
    }

    private static boolean sameRule(Rule first, Rule second) {
        return first.id == second.id && first.title.equals(second.title)
                && first.icon.equals(second.icon)
                && first.start == second.start && first.end == second.end
                && first.intervalSeconds == second.intervalSeconds && first.dailyTarget == second.dailyTarget
                && first.remindAfterTarget == second.remindAfterTarget && first.enabled == second.enabled
                && first.specificTimeMode == second.specificTimeMode
                && first.timePointsSeconds.equals(second.timePointsSeconds);
    }

    private static void writeRules(Context context, List<Rule> rules) {
        JSONArray array = new JSONArray();
        for (Rule rule : rules) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", rule.id);
                item.put("title", rule.title);
                item.put("icon", rule.icon);
                item.put("reminderMessage", rule.reminderMessage);
                item.put("start", rule.start);
                item.put("end", rule.end);
                item.put("interval", rule.interval);
                item.put("intervalSeconds", rule.intervalSeconds);
                item.put("dailyTarget", rule.dailyTarget);
                item.put("remindAfterTarget", rule.remindAfterTarget);
                item.put("enabled", rule.enabled);
                item.put("scheduleMode", rule.specificTimeMode ? "times" : "interval");
                JSONArray timePoints = new JSONArray();
                for (int seconds : rule.timePointsSeconds) timePoints.put(seconds);
                item.put("timePointsSeconds", timePoints);
                array.put(item);
            } catch (JSONException ignored) { }
        }
        prefs(context).edit().putString(RULES_KEY, array.toString()).apply();
    }

    static String reminderMessage(Context context, int ruleId, String action) {
        Rule rule = findRule(rules(context), ruleId);
        return rule != null && !rule.reminderMessage.isEmpty()
                ? rule.reminderMessage : ReminderActivity.pleaFor(action);
    }

    static int nextRuleId(Context context) {
        int max = 0;
        for (Rule rule : rules(context)) max = Math.max(max, rule.id);
        return max + 1;
    }

    static boolean isWithinWindow(Context context, long timeMillis) {
        for (Rule rule : rules(context)) if (rule.enabled && isWithinWindow(rule, timeMillis)) return true;
        return false;
    }

    static boolean isWithinWindow(Rule rule, long timeMillis) {
        Calendar now = Calendar.getInstance();
        now.setTimeInMillis(timeMillis);
        int minute = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (rule.start == rule.end) return true;
        return rule.start < rule.end ? minute >= rule.start && minute < rule.end : minute >= rule.start || minute < rule.end;
    }

    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
        ReminderScheduler.update(context);
    }

    static String nextType(Context context) {
        List<Rule> rules = rules(context);
        for (Rule rule : rules) if (rule.enabled) return rule.title;
        return "歇一歇";
    }

    static int completedToday(Context context, int ruleId) {
        String key = "done_" + new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(new java.util.Date()) + "_" + ruleId;
        return prefs(context).getInt(key, 0);
    }

    static boolean markCompleted(Context context, int ruleId, int target) {
        String key = "done_" + new java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(new java.util.Date()) + "_" + ruleId;
        int count = prefs(context).getInt(key, 0) + 1;
        prefs(context).edit().putInt(key, count).apply();
        return count >= target;
    }
}
