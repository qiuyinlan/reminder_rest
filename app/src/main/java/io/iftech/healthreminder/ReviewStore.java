package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 每日复盘：一条固定规则（module=review）+ 每天一句话。
 * 规则不放进事项列表，只从「事项」页顶部的复盘卡片控制。
 */
final class ReviewStore {
    static final int RULE_ID = 1000010;
    private static final String PREFS = "review_notes";
    /** 默认 21:30。 */
    private static final int DEFAULT_SECONDS = 21 * 3600 + 30 * 60;

    private ReviewStore() { }

    static String dayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
    }

    static String read(Context context) {
        return prefs(context).getString(dayKey(), "");
    }

    static String read(Context context, String key) {
        return prefs(context).getString(key, "");
    }

    /** 所有复盘记录，按日期倒序（dayKey -> 内容）。 */
    static List<java.util.Map.Entry<String, String>> all(Context context) {
        List<java.util.Map.Entry<String, String>> result = new ArrayList<>();
        for (java.util.Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            String key = entry.getKey();
            if (key == null || key.length() != 10) continue;
            Object raw = entry.getValue();
            if (!(raw instanceof String)) continue;
            String note = ((String) raw).trim();
            if (note.isEmpty()) continue;
            result.add(new java.util.AbstractMap.SimpleEntry<>(key, note));
        }
        result.sort((a, b) -> b.getKey().compareTo(a.getKey()));
        return result;
    }

    /** 按日期保存（统计页编辑某一天用）。 */
    static void save(Context context, String key, String note) {
        String value = note == null ? "" : note.trim();
        if (value.isEmpty()) prefs(context).edit().remove(key).apply();
        else prefs(context).edit().putString(key, value).apply();
    }

    static void save(Context context, String note) {
        String value = note == null ? "" : note.trim();
        if (value.isEmpty()) prefs(context).edit().remove(dayKey()).apply();
        else prefs(context).edit().putString(dayKey(), value).apply();
    }

    static ReminderSettings.Rule rule(Context context) {
        for (ReminderSettings.Rule rule : ReminderSettings.scheduledRules(context)) {
            if (rule.id == RULE_ID) return rule;
        }
        return null;
    }


    static boolean enabled(Context context) {
        ReminderSettings.Rule rule = rule(context);
        return rule != null && rule.enabled;
    }

    /** 拿到复盘规则；还没有就建一条默认的（21:30，先关着）。 */
    static ReminderSettings.Rule ensureRule(Context context) {
        ReminderSettings.Rule rule = rule(context);
        if (rule != null) return rule;
        ReminderSettings.upsertRule(context, build(context, DEFAULT_SECONDS, false));
        return rule(context);
    }

    /** 拿到规则，没有就建（开关用）。 */
    static void setEnabled(Context context, boolean on) {
        ReminderSettings.Rule rule = rule(context);
        rule = rule == null ? build(context, DEFAULT_SECONDS, on) : rule.withEnabled(on);
        ReminderSettings.upsertRule(context, rule);
    }



    private static ReminderSettings.Rule build(Context context, int seconds, boolean enabled) {
        int safe = ((seconds % 86400) + 86400) % 86400;
        ArrayList<Integer> points = new ArrayList<>();
        points.add(safe);
        return ReminderSettings.Rule.withSpecificTimes(RULE_ID, "每日复盘",
                        safe / 60, safe / 60, 24 * 3600, points, 1, false, enabled)
                .withModule(ReminderSettings.MODULE_REVIEW)
                .withReminderMessage("花一分钟，写下今天的一句");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
