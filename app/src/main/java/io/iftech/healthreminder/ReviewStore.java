package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 每日复盘：一条固定规则（module=review）+ 每天一条一条的小记录。
 * 每条带自己的时间，互不合并；长按某条可以单独改或删。
 * 旧版本存的是「一天一段纯文本」，读到时自动迁移成一条。
 */
final class ReviewStore {
    static final int RULE_ID = 1000010;
    private static final String PREFS = "review_notes";
    /** 默认 21:30。 */
    private static final int DEFAULT_SECONDS = 21 * 3600 + 30 * 60;

    /** 复盘里的一条小记录。at=0 表示从旧版本迁移过来的，没有具体时间。 */
    static final class Entry {
        final long at;
        final String text;

        Entry(long at, String text) {
            this.at = at;
            this.text = text;
        }
    }

    private ReviewStore() { }

    static String dayKey() {
        return dayKey(Calendar.getInstance());
    }

    static String dayKey(Calendar day) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(day.getTime());
    }

    /** 某条记录的时间，显示成 21:30。 */
    static String clock(long at) {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date(at));
    }

    /** 某天的全部记录，按时间正序（先写的在上）。 */
    static List<Entry> entries(Context context, String key) {
        List<Entry> result = new ArrayList<>();
        SharedPreferences p = prefs(context);
        String raw = p.getString(key, null);
        if (raw == null || raw.trim().isEmpty()) return result;
        String trimmed = raw.trim();
        if (!trimmed.startsWith("[")) {
            // 旧格式（一天一段纯文本）：迁移成一条，原 key 改存 JSON
            result.add(new Entry(0L, trimmed));
            write(context, key, result);
            return result;
        }
        try {
            JSONArray array = new JSONArray(trimmed);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                String text = item.optString("text", "").trim();
                if (text.isEmpty()) continue;
                result.add(new Entry(item.optLong("at", 0L), text));
            }
        } catch (Exception ignored) {
            return result;
        }
        result.sort((a, b) -> Long.compare(a.at, b.at));
        return result;
    }

    static List<Entry> today(Context context) {
        return entries(context, dayKey());
    }

    static int count(Context context, String key) {
        return entries(context, key).size();
    }

    /** 今天再记一条。 */
    static void add(Context context, String text) {
        add(context, dayKey(), text);
    }

    static void add(Context context, String key, String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return;
        List<Entry> list = new ArrayList<>(entries(context, key));
        list.add(new Entry(System.currentTimeMillis(), value));
        write(context, key, list);
    }

    /** 改某一条；改成空就等于删掉这条。 */
    static void update(Context context, String key, int index, String text) {
        List<Entry> list = new ArrayList<>(entries(context, key));
        if (index < 0 || index >= list.size()) return;
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) list.remove(index);
        else list.set(index, new Entry(list.get(index).at, value));
        write(context, key, list);
    }

    static void remove(Context context, String key, int index) {
        List<Entry> list = new ArrayList<>(entries(context, key));
        if (index < 0 || index >= list.size()) return;
        list.remove(index);
        write(context, key, list);
    }

    /** 某天全部记录拼成纯文本，每条前面带 HH:mm。 */
    static String read(Context context, String key) {
        return join(entries(context, key));
    }

    static String read(Context context) {
        return read(context, dayKey());
    }

    static String join(List<Entry> list) {
        StringBuilder sb = new StringBuilder();
        for (Entry entry : list) {
            if (sb.length() > 0) sb.append('\n');
            if (entry.at > 0) sb.append(clock(entry.at)).append(' ');
            sb.append(entry.text);
        }
        return sb.toString();
    }

    /** 所有复盘记录，按日期倒序（dayKey -> 全部内容）。 */
    static List<Map.Entry<String, String>> all(Context context) {
        List<Map.Entry<String, String>> result = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            String key = entry.getKey();
            if (key == null || key.length() != 10) continue;
            if (!(entry.getValue() instanceof String)) continue;
            String note = read(context, key).trim();
            if (note.isEmpty()) continue;
            result.add(new AbstractMap.SimpleEntry<>(key, note));
        }
        result.sort((a, b) -> b.getKey().compareTo(a.getKey()));
        return result;
    }

    /** 全部复盘拼成一段纯文本：日期 + 换行 + 内容，段与段空一行。 */
    static String allAsText(Context context) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : all(context)) {
            if (sb.length() > 0) sb.append("\n\n");
            sb.append(entry.getKey()).append('\n').append(entry.getValue());
        }
        return sb.toString();
    }

    private static void write(Context context, String key, List<Entry> list) {
        if (list.isEmpty()) {
            prefs(context).edit().remove(key).apply();
            return;
        }
        JSONArray array = new JSONArray();
        for (Entry entry : list) {
            JSONObject item = new JSONObject();
            try {
                item.put("at", entry.at);
                item.put("text", entry.text);
                array.put(item);
            } catch (Exception ignored) { }
        }
        prefs(context).edit().putString(key, array.toString()).apply();
    }

    /** 记完今天这一句：算今天已完成，并把下次提醒推到明天。 */
    static void markDone(Context context, int ruleId) {
        ReminderSettings.markCompleted(context, ruleId, 1);
        ReminderScheduler.update(context);
    }

    /** 复盘提醒是否显示（设置里可关，关了就不出现在提醒事项里）。 */
    static boolean visible(Context context) {
        return prefs(context).getBoolean("review_visible", true);
    }

    static void setVisible(Context context, boolean on) {
        prefs(context).edit().putBoolean("review_visible", on).apply();
        ReminderSettings.Rule rule = rule(context);
        if (rule != null) ReminderSettings.upsertRule(context, rule.withEnabled(on));
    }

    static ReminderSettings.Rule rule(Context context) {
        for (ReminderSettings.Rule rule : ReminderSettings.scheduledRules(context)) {
            if (rule.id != RULE_ID) continue;
            if (!"复盘".equals(rule.title)) {
                ReminderSettings.Rule renamed = rule.withTitle("复盘");
                ReminderSettings.upsertRule(context, renamed);
                return renamed;
            }
            return rule;
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
        return ReminderSettings.Rule.withSpecificTimes(RULE_ID, "复盘",
                        safe / 60, safe / 60, 24 * 3600, points, 1, false, enabled)
                .withModule(ReminderSettings.MODULE_REVIEW)
                .withReminderMessage("花一分钟，写下今天的一句");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
