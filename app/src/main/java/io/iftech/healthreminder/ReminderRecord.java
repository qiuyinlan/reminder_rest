package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class ReminderRecord {
    static final String KIND_DONE = "done";
    static final String KIND_MISSED = "missed";
    static final String KIND_COMPANION = "companion";
    static final String KIND_FOCUS = "focus";
    final String id;
    final String action;
    final String kind;
    final long startedAt;
    final long endedAt;
    final String reason;
    /** 所属模块：body / task / card；旧记录没有这个字段时按 body 处理。 */
    final String module;

    ReminderRecord(String id, String action, String kind, long startedAt, long endedAt, String reason) {
        this(id, action, kind, startedAt, endedAt, reason, ReminderSettings.MODULE_BODY);
    }

    ReminderRecord(String id, String action, String kind, long startedAt, long endedAt, String reason, String module) {
        this.id = id;
        this.action = action;
        this.kind = kind;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.reason = reason;
        this.module = module == null || module.trim().isEmpty() ? ReminderSettings.MODULE_BODY : module.trim();
    }

    boolean isModule(String value) { return module.equals(value); }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("health_records", Context.MODE_PRIVATE);
    }

    static void begin(Context context, String id, String action, long startedAt) {
        try {
            prefs(context).edit().putString("pending", new JSONObject()
                    .put("id", id).put("action", action).put("startedAt", startedAt).toString()).apply();
        } catch (JSONException ignored) { }
    }

    static JSONObject pending(Context context) {
        try { return new JSONObject(prefs(context).getString("pending", "{}")); }
        catch (JSONException ignored) { return new JSONObject(); }
    }

    static void finish(Context context, String id, String action, String kind, long startedAt, long endedAt, String reason) {
        finish(context, id, action, kind, startedAt, endedAt, reason, ReminderSettings.MODULE_BODY);
    }

    static void finish(Context context, String id, String action, String kind, long startedAt, long endedAt, String reason, String module) {
        JSONArray records = new JSONArray();
        for (ReminderRecord record : all(context)) {
            if (!record.id.equals(id)) records.put(record.toJson());
        }
        records.put(new ReminderRecord(id, action, kind, startedAt, endedAt, reason, module).toJson());
        android.content.SharedPreferences preferences = prefs(context);
        JSONObject active = pending(context);
        android.content.SharedPreferences.Editor editor = preferences.edit().putString("records", records.toString());
        if (id.equals(active.optString("id", ""))) editor.remove("pending");
        editor.apply();
    }

    static void update(Context context, ReminderRecord changed) {
        JSONArray records = new JSONArray();
        for (ReminderRecord record : all(context)) {
            records.put(record.id.equals(changed.id) ? changed.toJson() : record.toJson());
        }
        prefs(context).edit().putString("records", records.toString()).apply();
    }

    static void delete(Context context, String id) {
        JSONArray records = new JSONArray();
        for (ReminderRecord record : all(context)) {
            if (!record.id.equals(id)) records.put(record.toJson());
        }
        prefs(context).edit().putString("records", records.toString()).apply();
    }

    static void recordSnoozed(Context context, String action, long timestamp) {
        String id = "snoozed-" + java.util.UUID.randomUUID();
        finish(context, id, action, KIND_MISSED, timestamp, timestamp, "稍后提醒");
    }

    /** 当天开始时间戳（本地时区 00:00）。 */
    static long dayStart(long timeMillis) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(timeMillis);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /** 某模块某天是否有完成记录（按事项标题匹配）。 */
    static boolean completedOn(Context context, String module, String action, long dayStart) {
        long dayEnd = dayStart + 24 * 60 * 60 * 1000L;
        for (ReminderRecord record : all(context)) {
            if (!record.module.equals(module)) continue;
            if (!KIND_DONE.equals(record.kind)) continue;
            if (!record.action.equals(action)) continue;
            if (record.endedAt >= dayStart && record.endedAt < dayEnd) return true;
        }
        return false;
    }

    /** 取消某模块某天某个事项的完成状态（卡片勾选框取消时用）。 */
    static void deleteCompletion(Context context, String module, String action, long dayStart) {
        long dayEnd = dayStart + 24 * 60 * 60 * 1000L;
        JSONArray records = new JSONArray();
        for (ReminderRecord record : all(context)) {
            boolean match = record.module.equals(module)
                    && KIND_DONE.equals(record.kind)
                    && record.action.equals(action)
                    && record.endedAt >= dayStart && record.endedAt < dayEnd;
            if (!match) records.put(record.toJson());
        }
        prefs(context).edit().putString("records", records.toString()).apply();
    }

    /** 删除某模块某天的全部记录（用于「重置今日」）。 */
    static void deleteForDay(Context context, String module, long dayStart) {
        long dayEnd = dayStart + 24 * 60 * 60 * 1000L;
        JSONArray records = new JSONArray();
        for (ReminderRecord record : all(context)) {
            boolean match = record.module.equals(module)
                    && record.endedAt >= dayStart && record.endedAt < dayEnd;
            if (!match) records.put(record.toJson());
        }
        prefs(context).edit().putString("records", records.toString()).apply();
    }

    static List<ReminderRecord> all(Context context) {        ArrayList<ReminderRecord> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString("records", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new ReminderRecord(item.getString("id"), item.getString("action"), item.getString("kind"),
                        item.getLong("startedAt"), item.getLong("endedAt"), item.optString("reason", ""),
                        item.optString("module", ReminderSettings.MODULE_BODY)));
            }
        } catch (JSONException ignored) { }
        return result;
    }

    JSONObject toJson() {
        JSONObject item = new JSONObject();
        try {
            item.put("id", id);
            item.put("action", action);
            item.put("kind", kind);
            item.put("startedAt", startedAt);
            item.put("endedAt", endedAt);
            item.put("reason", reason);
            item.put("module", module);
        } catch (JSONException ignored) { }
        return item;
    }
}
