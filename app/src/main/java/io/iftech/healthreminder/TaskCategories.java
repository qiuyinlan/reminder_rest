package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务分类管理。
 * 分类存在 SharedPreferences 里，key = "task_categories"。
 * 每个分类：{ "id": 1, "name": "学习", "expanded": true }
 */
final class TaskCategories {
    private static final String KEY = "task_categories";
    private static final String DEFAULT = "默认";

    private static final String KEY_DEFAULT_EXPANDED = "default_expanded";

    private TaskCategories() { }

    static List<Category> all(Context context) {
        List<Category> result = new ArrayList<>();
        // 默认分类不可删除，但可以收起；展开状态单独存，不写进分类数组
        result.add(new Category(0, DEFAULT, defaultExpanded(context)));
        try {
            String json = prefs(context).getString(KEY, "[]");
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new Category(
                        item.getInt("id"),
                        item.getString("name"),
                        item.optBoolean("expanded", true)
                ));
            }
        } catch (JSONException ignored) { }
        return result;
    }

    static Category add(Context context, String name) {
        List<Category> existing = all(context);
        int nextId = 1;
        for (Category c : existing) {
            if (c.id >= nextId) nextId = c.id + 1;
        }
        Category cat = new Category(nextId, name, true);
        List<Category> toSave = new ArrayList<>();
        for (Category c : existing) {
            if (c.id > 0) toSave.add(c); // 跳过默认分类
        }
        toSave.add(cat);
        save(context, toSave);
        return cat;
    }

    static void update(Context context, List<Category> cats) {
        List<Category> toSave = new ArrayList<>();
        for (Category c : cats) {
            if (c.id > 0) toSave.add(c); // 跳过默认分类
        }
        save(context, toSave);
    }

    static void toggleExpanded(Context context, int categoryId) {
        if (categoryId <= 0) {
            // 默认分类不在分类数组里，单独存一个开关
            prefs(context).edit().putBoolean(KEY_DEFAULT_EXPANDED, !defaultExpanded(context)).apply();
            return;
        }
        List<Category> cats = all(context);
        for (Category c : cats) {
            if (c.id == categoryId) {
                c.expanded = !c.expanded;
                break;
            }
        }
        update(context, cats);
    }

    private static boolean defaultExpanded(Context context) {
        return prefs(context).getBoolean(KEY_DEFAULT_EXPANDED, true);
    }

    private static void save(Context context, List<Category> cats) {
        JSONArray array = new JSONArray();
        for (Category c : cats) {
            if (c.id <= 0) continue; // 不保存默认分类
            try {
                JSONObject item = new JSONObject();
                item.put("id", c.id);
                item.put("name", c.name);
                item.put("expanded", c.expanded);
                array.put(item);
            } catch (JSONException ignored) { }
        }
        prefs(context).edit().putString(KEY, array.toString()).apply();
    }

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("task_categories", Context.MODE_PRIVATE);
    }

    static final class Category {
        final int id;
        final String name;
        boolean expanded;

        Category(int id, String name, boolean expanded) {
            this.id = id;
            this.name = name;
            this.expanded = expanded;
        }
    }
}
