package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 喝水记录：按天存每一次喝的量（10–500ml，10 的倍数）。
 * 默认开启，设置里可以整体关掉显示和记录。
 */
final class WaterLog {
    static final int MIN_ML = 10;
    static final int MAX_ML = 500;
    static final int STEP_ML = 10;
    /** 第一次用默认 200ml。 */
    private static final int DEFAULT_ML = 200;
    private static final int MAX_ENTRIES_PER_DAY = 60;

    private static final String PREFS = "water_log";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_LAST = "last_ml";

    /** 一次喝水记录。at<=0 表示是补记的，没有具体时间。 */
    static final class Entry {
        final long at;
        final int ml;

        Entry(long at, int ml) {
            this.at = at;
            this.ml = ml;
        }
    }

    private WaterLog() { }

    static boolean enabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, true);
    }

    static void setEnabled(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_ENABLED, on).apply();
    }

    /** 上次滚轮停在哪，下次点开默认还是这个位置。 */
    static int lastAmount(Context context) {
        return clamp(prefs(context).getInt(KEY_LAST, DEFAULT_ML));
    }

    static void add(Context context, int ml) {
        add(context, Calendar.getInstance(), ml);
    }

    /** 记一次；补记过去某天时 at 记 0（只留量，不留时间）。 */
    static void add(Context context, Calendar day, int ml) {
        int amount = clamp(ml);
        String key = dayKey(day);
        JSONArray list = readArray(prefs(context), key);
        boolean today = dayKey(Calendar.getInstance()).equals(key);
        JSONObject item = new JSONObject();
        try {
            item.put("at", today ? System.currentTimeMillis() : 0L);
            item.put("ml", amount);
            list.put(item);
        } catch (Exception ignored) {
            return;
        }
        JSONArray kept = new JSONArray();
        int from = Math.max(0, list.length() - MAX_ENTRIES_PER_DAY);
        for (int i = from; i < list.length(); i++) {
            JSONObject entry = list.optJSONObject(i);
            if (entry != null) kept.put(entry);
        }
        prefs(context).edit().putString(key, kept.toString()).putInt(KEY_LAST, amount).apply();
    }

    static int total(Context context, Calendar day) {
        int sum = 0;
        for (Entry entry : entries(context, day)) sum += entry.ml;
        return sum;
    }

    static int todayTotal(Context context) {
        return total(context, Calendar.getInstance());
    }

    /** 某天的记录，时间倒序（最新的在上面）。 */
    static List<Entry> entries(Context context, Calendar day) {
        List<Entry> result = new ArrayList<>();
        JSONArray list = readArray(prefs(context), dayKey(day));
        for (int i = 0; i < list.length(); i++) {
            JSONObject item = list.optJSONObject(i);
            if (item == null) continue;
            int ml = item.optInt("ml", 0);
            if (ml <= 0) continue;
            result.add(new Entry(item.optLong("at", 0L), ml));
        }
        result.sort((a, b) -> Long.compare(b.at, a.at));
        return result;
    }

    static List<Entry> todayEntries(Context context) {
        return entries(context, Calendar.getInstance());
    }

    private static int clamp(int ml) {
        int stepped = Math.round(ml / (float) STEP_ML) * STEP_ML;
        return Math.max(MIN_ML, Math.min(MAX_ML, stepped));
    }

    private static String dayKey(Calendar day) {
        return "d" + new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(day.getTime());
    }

    private static JSONArray readArray(SharedPreferences prefs, String key) {
        String raw = prefs.getString(key, null);
        if (raw == null || raw.trim().isEmpty()) return new JSONArray();
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
