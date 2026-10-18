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
    final String id;
    final String action;
    final String kind;
    final long startedAt;
    final long endedAt;
    final String reason;

    ReminderRecord(String id, String action, String kind, long startedAt, long endedAt, String reason) {
        this.id = id;
        this.action = action;
        this.kind = kind;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.reason = reason;
    }

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
        JSONArray records = new JSONArray();
        for (ReminderRecord record : all(context)) {
            if (!record.id.equals(id)) records.put(record.toJson());
        }
        records.put(new ReminderRecord(id, action, kind, startedAt, endedAt, reason).toJson());
        prefs(context).edit().putString("records", records.toString()).remove("pending").apply();
    }

    static List<ReminderRecord> all(Context context) {
        ArrayList<ReminderRecord> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString("records", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new ReminderRecord(item.getString("id"), item.getString("action"), item.getString("kind"),
                        item.getLong("startedAt"), item.getLong("endedAt"), item.optString("reason", "")));
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
        } catch (JSONException ignored) { }
        return item;
    }
}
