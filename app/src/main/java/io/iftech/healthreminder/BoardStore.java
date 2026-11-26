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
 * 宝箱：收件箱。不按天清空，一直攒。
 *
 * 两种条目：
 * - TYPE_TASK：随手记的任务，有勾选，可以以后转成正式任务
 * - TYPE_NOTE：纯文字备忘录，没有完成概念
 */
final class BoardStore {
    static final int TYPE_TASK = 0;
    static final int TYPE_NOTE = 1;

    private static final String PREFS = "box_store";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_LAST_TYPE = "last_type";

    static final class Sub {
        String title = "";
        boolean done;
    }

    static final class Item {
        long id;
        int type = TYPE_TASK;
        String title = "";
        boolean done;
        long createdAt = System.currentTimeMillis();
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

    /** 永远返回全部条目；不再按天清空。 */
    static List<Item> load(Context context) {
        List<Item> items = new ArrayList<>();
        SharedPreferences prefs = prefs(context);
        try {
            JSONArray array = new JSONArray(prefs.getString(KEY_ITEMS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj == null) continue;
                Item item = new Item();
                item.id = obj.optLong("id", 0L);
                item.type = obj.optInt("type", TYPE_TASK);
                item.title = obj.optString("title", "");
                item.done = obj.optBoolean("done", false);
                item.createdAt = obj.optLong("createdAt", System.currentTimeMillis());
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
                obj.put("type", item.type);
                obj.put("title", item.title);
                obj.put("done", item.done);
                obj.put("createdAt", item.createdAt);
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
        prefs(context).edit().putString(KEY_ITEMS, array.toString()).apply();
    }

    static int lastType(Context context) {
        return prefs(context).getInt(KEY_LAST_TYPE, TYPE_TASK);
    }

    static void setLastType(Context context, int type) {
        prefs(context).edit().putInt(KEY_LAST_TYPE, type).apply();
    }

    static long nextId() {
        return System.currentTimeMillis();
    }

    static Item addText(Context context, int type, String title) {
        List<Item> items = load(context);
        Item item = new Item();
        item.id = nextId();
        item.type = type;
        item.title = title;
        item.createdAt = System.currentTimeMillis();
        items.add(item);
        save(context, items);
        return item;
    }

    /** 从任务页移进来：保留标题和子任务，方便以后恢复成正式任务。 */
    static void addFromTask(Context context, String title, List<ReminderSettings.Rule> subtasks) {
        List<Item> items = load(context);
        Item item = new Item();
        item.id = nextId();
        item.type = TYPE_TASK;
        item.title = title;
        item.createdAt = System.currentTimeMillis();
        if (subtasks != null) {
            for (ReminderSettings.Rule rule : subtasks) {
                Sub sub = new Sub();
                sub.title = rule.title;
                item.subs.add(sub);
            }
        }
        items.add(item);
        save(context, items);
    }

    static void clear(Context context) {
        prefs(context).edit().clear().apply();
    }
}
