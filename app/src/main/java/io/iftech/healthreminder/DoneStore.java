package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * done：今天做成的事，一行一条。
 *
 * 跟白板一样只活今天，换一天自动清空。不统计、不提醒，纯记录。
 */
final class DoneStore {
    private static final String PREFS = "done_lines";
    private static final String KEY_DAY = "day";
    private static final String KEY_LINES = "lines";

    private DoneStore() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
    }

    static String todayLabel() {
        return new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date());
    }

    static List<String> load(Context context) {
        List<String> lines = new ArrayList<>();
        SharedPreferences prefs = prefs(context);
        if (!todayKey().equals(prefs.getString(KEY_DAY, ""))) {
            if (prefs.contains(KEY_LINES)) prefs.edit().clear().apply();
            return lines;
        }
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_LINES, "[]"));
            for (int i = 0; i < array.length(); i++) {
                String line = array.optString(i, "");
                if (!line.isEmpty()) lines.add(line);
            }
        } catch (Exception ignored) { }
        return lines;
    }

    static void save(Context context, List<String> lines) {
        JSONArray array = new JSONArray();
        for (String line : lines) array.put(line);
        prefs(context).edit()
                .putString(KEY_DAY, todayKey())
                .putString(KEY_LINES, array.toString())
                .apply();
    }

    static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }
}
