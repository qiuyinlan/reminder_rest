package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 全部数据备份：把 App 的 SharedPreferences 整体打包成一个 JSON。
 * 只排除和本机强绑定的东西（更新下载状态、会员凭证、崩溃日志、滚动位置）。
 */
final class AppBackup {
    static final String KIND = "rest-bean-backup";
    static final int VERSION = 1;
    private static final Set<String> SKIP = new HashSet<>();

    static {
        SKIP.add("app_updates");
        SKIP.add("membership_credentials");
        SKIP.add("crash_log");
        SKIP.add("task_scroll_memory");
        SKIP.add("companion_session");
        SKIP.add("reminder_debug");
    }

    static final class Summary {
        final int prefs;
        final String createdAt;

        Summary(int prefs, String createdAt) {
            this.prefs = prefs;
            this.createdAt = createdAt == null ? "" : createdAt;
        }
    }

    private AppBackup() { }

    /** 本机要备份的数据组数。 */
    static int prefCount(Context context) {
        return prefNames(context).size();
    }

    static String fileName() {
        return "rest-bean-backup-" + new SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA)
                .format(new Date()) + ".json";
    }

    static String exportJson(Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("kind", KIND);
        root.put("version", VERSION);
        root.put("createdAt", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                .format(new Date()));
        JSONObject prefsJson = new JSONObject();
        for (String name : prefNames(context)) {
            JSONObject fileJson = new JSONObject();
            for (Map.Entry<String, ?> entry : context.getSharedPreferences(name, Context.MODE_PRIVATE)
                    .getAll().entrySet()) {
                if (entry.getKey() == null) continue;
                fileJson.put(entry.getKey(), encode(entry.getValue()));
            }
            prefsJson.put(name, fileJson);
        }
        root.put("prefs", prefsJson);
        return root.toString();
    }

    /** 只看不写：读出备份文件里有什么，给恢复页预览用。 */
    static Summary inspect(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!KIND.equals(root.optString("kind"))) {
            throw new IllegalArgumentException("这不是休息豆芽的备份文件");
        }
        JSONObject prefsJson = root.optJSONObject("prefs");
        if (prefsJson == null) throw new IllegalArgumentException("备份文件里没有数据");
        int usable = 0;
        Iterator<String> names = prefsJson.keys();
        while (names.hasNext()) {
            String name = names.next();
            if (SKIP.contains(name) || !safeName(name)) continue;
            if (prefsJson.optJSONObject(name) == null) continue;
            usable++;
        }
        if (usable == 0) throw new IllegalArgumentException("备份文件里没有可恢复的数据");
        return new Summary(usable, root.optString("createdAt", ""));
    }

    /** 真正写回本机；返回写了几组。 */
    static int restore(Context context, String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!KIND.equals(root.optString("kind"))) {
            throw new IllegalArgumentException("这不是休息豆芽的备份文件");
        }
        JSONObject prefsJson = root.optJSONObject("prefs");
        if (prefsJson == null) throw new IllegalArgumentException("备份文件里没有数据");

        int count = 0;
        Iterator<String> names = prefsJson.keys();
        while (names.hasNext()) {
            String name = names.next();
            if (SKIP.contains(name) || !safeName(name)) continue;
            JSONObject fileJson = prefsJson.optJSONObject(name);
            if (fileJson == null) continue;
            SharedPreferences.Editor editor =
                    context.getSharedPreferences(name, Context.MODE_PRIVATE).edit();
            editor.clear();
            Iterator<String> keys = fileJson.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object raw = fileJson.opt(key);
                if (raw instanceof JSONObject) put(editor, key, (JSONObject) raw);
            }
            editor.commit();
            count++;
        }
        if (count == 0) throw new IllegalArgumentException("备份文件里没有可恢复的数据");
        return count;
    }

    private static boolean safeName(String name) {
        return name != null && !name.isEmpty() && !name.contains("/") && !name.contains("..");
    }

    private static List<String> prefNames(Context context) {
        List<String> names = new ArrayList<>();
        File dir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (!name.endsWith(".xml")) continue;
                String base = name.substring(0, name.length() - 4);
                if (SKIP.contains(base)) continue;
                names.add(base);
            }
        }
        Collections.sort(names);
        return names;
    }

    private static JSONObject encode(Object value) {
        JSONObject item = new JSONObject();
        try {
            if (value instanceof String) {
                item.put("t", "s");
                item.put("v", value);
            } else if (value instanceof Integer) {
                item.put("t", "i");
                item.put("v", ((Integer) value).intValue());
            } else if (value instanceof Long) {
                item.put("t", "l");
                item.put("v", ((Long) value).longValue());
            } else if (value instanceof Float) {
                item.put("t", "f");
                item.put("v", ((Float) value).doubleValue());
            } else if (value instanceof Boolean) {
                item.put("t", "b");
                item.put("v", ((Boolean) value).booleanValue());
            } else if (value instanceof Set) {
                item.put("t", "set");
                JSONArray array = new JSONArray();
                for (Object member : (Set<?>) value) array.put(String.valueOf(member));
                item.put("v", array);
            } else {
                item.put("t", "s");
                item.put("v", String.valueOf(value));
            }
        } catch (Exception ignored) { }
        return item;
    }

    private static void put(SharedPreferences.Editor editor, String key, JSONObject item) {
        String type = item.optString("t", "s");
        if ("i".equals(type)) {
            editor.putInt(key, item.optInt("v", 0));
        } else if ("l".equals(type)) {
            editor.putLong(key, item.optLong("v", 0L));
        } else if ("f".equals(type)) {
            editor.putFloat(key, (float) item.optDouble("v", 0d));
        } else if ("b".equals(type)) {
            editor.putBoolean(key, item.optBoolean("v", false));
        } else if ("set".equals(type)) {
            Set<String> set = new HashSet<>();
            JSONArray array = item.optJSONArray("v");
            if (array != null) {
                for (int i = 0; i < array.length(); i++) set.add(array.optString(i));
            }
            editor.putStringSet(key, set);
        } else {
            editor.putString(key, item.optString("v", ""));
        }
    }
}
