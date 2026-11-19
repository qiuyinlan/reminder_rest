package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.UUID;

/**
 * 心情记录：一天可以记很多次，每条写下心情和可选的「我做什么能缓解」。
 * 本地存储，进统计页汇总。
 */
final class MoodStore {
    private static final String PREFS = "mood_log";
    private static final String KEY_ENTRIES = "entries";

    static final class Entry {
        final String id;
        final long at;
        final String mood;
        final String relief;

        Entry(String id, long at, String mood, String relief) {
            this.id = id;
            this.at = at;
            this.mood = mood == null ? "" : mood;
            this.relief = relief == null ? "" : relief;
        }

        Entry withMood(String value, String reliefValue) {
            return new Entry(id, at, value, reliefValue);
        }
    }

    private MoodStore() { }

    static List<Entry> all(Context context) {
        List<Entry> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY_ENTRIES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.optJSONObject(i);
                if (item == null) continue;
                result.add(new Entry(item.optString("id", UUID.randomUUID().toString()),
                        item.optLong("at", 0L),
                        item.optString("mood", ""),
                        item.optString("relief", "")));
            }
        } catch (JSONException ignored) { }
        result.sort((a, b) -> Long.compare(b.at, a.at));
        return result;
    }

    static List<Entry> onDay(Context context, long dayStart) {
        long dayEnd = dayStart + 24 * 60 * 60 * 1000L;
        List<Entry> result = new ArrayList<>();
        for (Entry entry : all(context)) {
            if (entry.at >= dayStart && entry.at < dayEnd) result.add(entry);
        }
        return result;
    }

    static List<Entry> inMonth(Context context, Calendar month) {
        Calendar start = (Calendar) month.clone();
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);
        Calendar end = (Calendar) start.clone();
        end.add(Calendar.MONTH, 1);
        List<Entry> result = new ArrayList<>();
        for (Entry entry : all(context)) {
            if (entry.at >= start.getTimeInMillis() && entry.at < end.getTimeInMillis()) result.add(entry);
        }
        return result;
    }

    static void add(Context context, String mood, String relief) {
        String value = mood == null ? "" : mood.trim();
        if (value.isEmpty()) return;
        Entry entry = new Entry(UUID.randomUUID().toString(), System.currentTimeMillis(),
                value, relief == null ? "" : relief.trim());
        List<Entry> list = all(context);
        list.add(entry);
        save(context, list);
    }

    static void update(Context context, Entry changed) {
        List<Entry> list = new ArrayList<>();
        for (Entry entry : all(context)) {
            list.add(entry.id.equals(changed.id) ? changed : entry);
        }
        save(context, list);
    }

    static void delete(Context context, String id) {
        List<Entry> list = new ArrayList<>();
        for (Entry entry : all(context)) {
            if (!entry.id.equals(id)) list.add(entry);
        }
        save(context, list);
    }

    private static void save(Context context, List<Entry> entries) {
        JSONArray array = new JSONArray();
        try {
            for (Entry entry : entries) {
                JSONObject item = new JSONObject();
                item.put("id", entry.id);
                item.put("at", entry.at);
                item.put("mood", entry.mood);
                item.put("relief", entry.relief);
                array.put(item);
            }
        } catch (JSONException ignored) { }
        prefs(context).edit().putString(KEY_ENTRIES, array.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
