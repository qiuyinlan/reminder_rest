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
    private static final String ROUTINE_RULES_KEY = "routine_rules_v1";
    static final int ROUTINE_WAKE_ID = 1000001;
    static final int ROUTINE_SLEEP_ID = 1000002;

    static final class Rule {
        final int id;
        final String title;
        /** 自定义图标（单个字符/符号）；为空时显示标题首字。 */
        final String icon;
        final String reminderMessage;
        final String overlayArtwork;
        /** 用户自选颜色（UiPalette 类目色编号 0..7）；-1 表示跟随标题自动配色。 */
        final int color;
        static final int COLOR_AUTO = -1;
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
            this(id, title, start, end, interval, intervalSeconds, dailyTarget, remindAfterTarget,
                    enabled, specificTimeMode, timePointsSeconds, reminderMessage, icon,
                    ReminderArtworkCatalog.DEFAULT, COLOR_AUTO);
        }

        private Rule(int id, String title, int start, int end, int interval, int intervalSeconds,
                     int dailyTarget, boolean remindAfterTarget, boolean enabled,
                     boolean specificTimeMode, List<Integer> timePointsSeconds,
                     String reminderMessage, String icon, String overlayArtwork, int color) {
            this.id = id;
            this.title = title;
            this.icon = icon == null ? "" : icon.trim();
            this.reminderMessage = reminderMessage == null ? "" : reminderMessage.trim();
            this.overlayArtwork = overlayArtwork == null || ReminderArtworkCatalog.drawable(overlayArtwork) == 0
                    ? ReminderArtworkCatalog.DEFAULT : overlayArtwork;
            this.color = color >= 0 && color < UiPalette.COLOR_COUNT ? color : COLOR_AUTO;
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
                    remindAfterTarget, enabled, specificTimeMode, timePointsSeconds, message, icon, overlayArtwork, color);
        }

        Rule withIcon(String value) {
            return new Rule(id, title, start, end, interval, intervalSeconds, dailyTarget,
                    remindAfterTarget, enabled, specificTimeMode, timePointsSeconds, reminderMessage, value, overlayArtwork, color);
        }

        Rule withOverlayArtwork(String value) {
            return new Rule(id, title, start, end, interval, intervalSeconds, dailyTarget,
                    remindAfterTarget, enabled, specificTimeMode, timePointsSeconds,
                    reminderMessage, icon, value, color);
        }

        Rule withColor(int value) {
            return new Rule(id, title, start, end, interval, intervalSeconds, dailyTarget,
                    remindAfterTarget, enabled, specificTimeMode, timePointsSeconds,
                    reminderMessage, icon, overlayArtwork, value);
        }

        Rule withEnabled(boolean value) {
            return new Rule(id, title, start, end, interval, intervalSeconds, dailyTarget,
                    remindAfterTarget, value, specificTimeMode, timePointsSeconds,
                    reminderMessage, icon, overlayArtwork, color);
        }

        /** 内置图片图标的存储值；没有设置图片图标时返回空串。 */
        String iconImage() {
            return ItemIconCatalog.isImageIcon(icon) ? icon : "";
        }

        /** 卡片徽标文字：图片图标取标题首字，自定义符号优先，否则取标题首字。 */
        String badgeText() {
            if (!icon.isEmpty() && !ItemIconCatalog.isImageIcon(icon)) return icon;
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

    /** 保活壁纸来源：内置壁纸或相册图片。 */
    static String wallpaperSource(Context context) {
        return prefs(context).getString("wallpaper_source", WallpaperCatalog.SOURCE_BUILTIN);
    }

    static void setWallpaperSource(Context context, String source) {
        prefs(context).edit().putString("wallpaper_source", source).apply();
    }

    static String wallpaperBuiltinId(Context context) {
        return prefs(context).getString("wallpaper_builtin", WallpaperCatalog.DEFAULT);
    }

    static void setWallpaperBuiltinId(Context context, String id) {
        prefs(context).edit().putString("wallpaper_builtin", id).apply();
    }

    static String wallpaperUserUri(Context context) {
        return prefs(context).getString("wallpaper_user_uri", null);
    }

    static void setWallpaperUserUri(Context context, String uri) {
        prefs(context).edit().putString("wallpaper_user_uri", uri).apply();
    }

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
        return decodeRules(encoded);
    }

    static List<Rule> routineRules(Context context) {
        String encoded = prefs(context).getString(ROUTINE_RULES_KEY, null);
        if (encoded != null) return decodeRules(encoded);
        ArrayList<Rule> defaults = new ArrayList<>();
        defaults.add(routineRule(ROUTINE_WAKE_ID, "起床", 7 * 3600,
                "morning_scene", "早上好，该起床啦！"));
        defaults.add(routineRule(ROUTINE_SLEEP_ID, "睡眠", 22 * 3600,
                "sleep_scene", "主人，该睡觉啦，再不睡种子就发不了芽了！"));
        writeRules(context, ROUTINE_RULES_KEY, defaults);
        return defaults;
    }

    private static Rule routineRule(int id, String title, int secondOfDay, String artwork, String message) {
        int minute = secondOfDay / 60;
        ArrayList<Integer> times = new ArrayList<>();
        times.add(secondOfDay);
        return Rule.withSpecificTimes(id, title, minute, minute, 24 * 60 * 60,
                        times, 1, false, true)
                .withReminderMessage(message)
                .withIcon(title.equals("起床") ? "☀" : "月")
                .withOverlayArtwork(artwork);
    }

    static List<Rule> scheduledRules(Context context) {
        ArrayList<Rule> all = new ArrayList<>(rules(context));
        all.addAll(routineRules(context));
        return all;
    }

    static void saveRoutineRules(Context context, List<Rule> routines) {
        List<Rule> previous = routineRules(context);
        for (Rule rule : previous) {
            Rule updated = findRule(routines, rule.id);
            if (updated == null || !sameRule(rule, updated)) {
                ReminderScheduler.cancelRule(context, rule.id);
            }
        }
        for (Rule rule : routines) {
            if (findRule(previous, rule.id) == null) ReminderScheduler.resetNext(context, rule.id);
        }
        writeRules(context, ROUTINE_RULES_KEY, routines);
        ReminderScheduler.update(context);
    }

    private static ArrayList<Rule> decodeRules(String encoded) {
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
                        .withIcon(item.optString("icon", ""))
                        .withColor(item.optInt("color", Rule.COLOR_AUTO))
                        .withOverlayArtwork(item.optString("overlayArtwork", ReminderArtworkCatalog.DEFAULT)));
            }
        } catch (JSONException ignored) { }
        return result;
    }

    private static List<Rule> migrateRules(Context context) {
        SharedPreferences p = prefs(context);
        // Fresh installs start empty; retain explicitly saved choices from the legacy app.
        Set<String> selected = p.getStringSet("types", new HashSet<>());
        ArrayList<Rule> result = new ArrayList<>();
        int id = 1;
        for (String title : TYPES) {
            if (!selected.contains(title)) continue;
            result.add(new Rule(id++, title, p.getInt("start_minute", 9 * 60),
                    p.getInt("end_minute", 18 * 60), p.getInt("interval", 30), true));
        }
        writeRules(context, RULES_KEY, result);
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
        writeRules(context, RULES_KEY, rules);
        ReminderScheduler.update(context);
    }

    private static Rule findRule(List<Rule> rules, int id) {
        for (Rule rule : rules) if (rule.id == id) return rule;
        return null;
    }

    /** 只关心会影响排期的字段；颜色只是视觉设置，改动它不该重置下一次提醒。 */
    private static boolean sameRule(Rule first, Rule second) {
        return first.id == second.id && first.title.equals(second.title)
                && first.icon.equals(second.icon)
                && first.overlayArtwork.equals(second.overlayArtwork)
                && first.start == second.start && first.end == second.end
                && first.intervalSeconds == second.intervalSeconds && first.dailyTarget == second.dailyTarget
                && first.remindAfterTarget == second.remindAfterTarget && first.enabled == second.enabled
                && first.specificTimeMode == second.specificTimeMode
                && first.timePointsSeconds.equals(second.timePointsSeconds);
    }

    private static void writeRules(Context context, String key, List<Rule> rules) {
        JSONArray array = new JSONArray();
        for (Rule rule : rules) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", rule.id);
                item.put("title", rule.title);
                item.put("icon", rule.icon);
                item.put("color", rule.color);
                item.put("reminderMessage", rule.reminderMessage);
                item.put("overlayArtwork", rule.overlayArtwork);
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
        prefs(context).edit().putString(key, array.toString()).apply();
    }

    static String reminderMessage(Context context, int ruleId, String action) {
        Rule rule = findRule(scheduledRules(context), ruleId);
        return rule != null && !rule.reminderMessage.isEmpty()
                ? rule.reminderMessage : ReminderActivity.pleaFor(action);
    }

    static String overlayArtwork(Context context, int ruleId) {
        Rule rule = findRule(scheduledRules(context), ruleId);
        return rule == null ? ReminderArtworkCatalog.DEFAULT : rule.overlayArtwork;
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
        List<Rule> rules = scheduledRules(context);
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
