package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 白板：只属于今天的清单。
 *
 * 换一天就自动清空；不进任务表、不提醒、不统计 —— 纯给自己看的草稿纸。
 */
final class BoardStore {
    private static final String PREFS = "board_notes";
    private static final String KEY_DAY = "day";
    private static final String KEY_ITEMS = "items";

    static final class Sub {
        String title = "";
        boolean done;
    }

    static final class Item {
        long id;
        String title = "";
        boolean done;
        final List<Sub> subs = new ArrayList<>();
    }

    private BoardStore() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
    }

    static String todayLabel() {
        return new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date());
    }

    /** 读今天的内容；日期不是今天就直接当空的（顺手清掉旧数据）。 */
    static List<Item> load(Context context) {
        List<Item> items = new ArrayList<>();
        SharedPreferences prefs = prefs(context);
        if (!todayKey().equals(prefs.getString(KEY_DAY, ""))) {
            if (prefs.contains(KEY_ITEMS)) prefs.edit().clear().apply();
            return items;
        }
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_ITEMS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                Item item = new Item();
                item.id = obj.optLong("id", 0L);
                item.title = obj.optString("title", "");
                item.done = obj.optBoolean("done", false);
                JSONArray subs = obj.optJSONArray("subs");
                if (subs != null) {
                    for (int j = 0; j < subs.length(); j++) {
                        JSONObject subObj = subs.optJSONObject(j);
                        if (subObj == null) continue;
                        Sub sub = new Sub();
                        sub.title = subObj.optString("title", "");
                        sub.done = subObj.optBoolean("done", false);
                        item.subs.add(sub);
                    }
                }
                items.add(item);
            }
        } catch (Exception ignored) { }
        return items;
    }

    static void save(Context context, List<Item> items) {
        JSONArray array = new JSONArray();
        try {
            for (Item item : items) {
                JSONObject obj = new JSONObject();
                obj.put("id", item.id);
                obj.put("title", item.title);
                obj.put("done", item.done);
                JSONArray subs = new JSONArray();
                for (Sub sub : item.subs) {
                    JSONObject subObj = new JSONObject();
                    subObj.put("title", sub.title);
                    subObj.put("done", sub.done);
                    subs.put(subObj);
                }
                obj.put("subs", subs);
                array.put(obj);
            }
        } catch (Exception ignored) { }
        prefs(context).edit()
                .putString(KEY_DAY, todayKey())
                .putString(KEY_ITEMS, array.toString())
                .apply();
    }

    static long nextId() {
        return System.currentTimeMillis();
    }

    static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }
}
