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

final class ReminderSettings {
    static final String[] TYPES = {"喝水", "护眼", "走动", "放松颈椎"};
    static final String KEY_ENABLED = "enabled";
    private static final String PREFS = "reminder_settings";
    private static final String RULES_KEY = "reminder_rules_v2";

    static final class Rule {
        final int id;
        final String title;
        final int start;
        final int end;
        final int interval;
        final int dailyTarget;
        final boolean remindAfterTarget;
        final boolean enabled;

        Rule(int id, String title, int start, int end, int interval, boolean enabled) {
            this(id, title, start, end, interval, 3, false, enabled);
        }
        Rule(int id, String title, int start, int end, int interval, int dailyTarget, boolean enabled) {
            this(id, title, start, end, interval, dailyTarget, false, enabled);
        }
        Rule(int id, String title, int start, int end, int interval, int dailyTarget, boolean remindAfterTarget, boolean enabled) {
            this.id = id;
            this.title = title;
            this.start = start;
            this.end = end;
            this.interval = interval;
            this.dailyTarget = Math.max(1, dailyTarget);
            this.remindAfterTarget = remindAfterTarget;
            this.enabled = enabled;
        }
    }

    private ReminderSettings() { }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean enabled(Context context) { return prefs(context).getBoolean(KEY_ENABLED, false); }
    static boolean usageOnly(Context context) { return prefs(context).getBoolean("usage_only", false); }
    static boolean blockEnabled(Context context) { return prefs(context).getBoolean("block_enabled", false); }

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
                result.add(new Rule(item.getInt("id"), item.getString("title"), item.getInt("start"),
                        item.getInt("end"), item.getInt("interval"), item.optInt("dailyTarget", 3),
                        item.optBoolean("remindAfterTarget", false), item.optBoolean("enabled", true)));
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
        for (Rule previous : rules(context)) ReminderScheduler.cancelRule(context, previous.id);
        writeRules(context, rules);
        ReminderScheduler.update(context);
    }

    private static void writeRules(Context context, List<Rule> rules) {
        JSONArray array = new JSONArray();
        for (Rule rule : rules) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", rule.id);
                item.put("title", rule.title);
                item.put("start", rule.start);
                item.put("end", rule.end);
                item.put("interval", rule.interval);
                item.put("dailyTarget", rule.dailyTarget);
                item.put("remindAfterTarget", rule.remindAfterTarget);
                item.put("enabled", rule.enabled);
                array.put(item);
            } catch (JSONException ignored) { }
        }
        prefs(context).edit().putString(RULES_KEY, array.toString()).apply();
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
