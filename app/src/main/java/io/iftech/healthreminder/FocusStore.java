package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 「现在」页的轻量状态：当前主任务 + 突然冒出来的想法。
 *
 * 刻意存在独立 SharedPreferences 里，不写成任务规则：
 * 想法只是捕捉，还没承诺去做，塞进任务表会把统计页灌得很乱。
 * 只有真正被设成主任务、并且做完的那一刻，才写一条 ReminderRecord 进时间线。
 */
final class FocusStore {
    private static final String PREFS = "focus_state";
    private static final String KEY_TITLE = "current_title";
    private static final String KEY_RULE = "current_rule_id";
    private static final String KEY_START = "current_started_at";
    private static final String KEY_IDEAS = "ideas";

    private FocusStore() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ 当前主任务

    static String currentTitle(Context context) {
        String title = prefs(context).getString(KEY_TITLE, "");
        return title == null ? "" : title;
    }

    static boolean hasCurrent(Context context) {
        return !currentTitle(context).isEmpty();
    }

    /** 来自任务清单时是任务 id；自己手写/从想法升上来的主任务为 -1。 */
    static int currentRuleId(Context context) {
        return prefs(context).getInt(KEY_RULE, -1);
    }

    static long currentStartedAt(Context context) {
        return prefs(context).getLong(KEY_START, 0L);
    }

    static void setCurrent(Context context, String title, int ruleId) {
        String value = title == null ? "" : title.trim();
        SharedPreferences.Editor editor = prefs(context).edit();
        if (value.isEmpty()) {
            editor.putString(KEY_TITLE, "").putInt(KEY_RULE, -1).putLong(KEY_START, 0L);
        } else {
            editor.putString(KEY_TITLE, value)
                    .putInt(KEY_RULE, ruleId)
                    .putLong(KEY_START, System.currentTimeMillis());
        }
        editor.apply();
    }

    static void clearCurrent(Context context) {
        setCurrent(context, "", -1);
    }

    // -------------------------------------------------------------- 想法清单

    static final class Idea {
        final String id;
        final String title;
        final long createdAt;

        Idea(String id, String title, long createdAt) {
            this.id = id;
            this.title = title;
            this.createdAt = createdAt;
        }
    }

    static List<Idea> ideas(Context context) {
        List<Idea> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY_IDEAS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new Idea(item.getString("id"), item.getString("title"),
                        item.optLong("createdAt", 0L)));
            }
        } catch (JSONException ignored) { }
        return result;
    }

    static int ideaCount(Context context) {
        return ideas(context).size();
    }

    static void addIdea(Context context, String title) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) return;
        List<Idea> list = ideas(context);
        list.add(new Idea(UUID.randomUUID().toString(), value, System.currentTimeMillis()));
        save(context, list);
    }

    static void removeIdea(Context context, String id) {
        List<Idea> list = ideas(context);
        List<Idea> kept = new ArrayList<>();
        for (Idea idea : list) {
            if (!idea.id.equals(id)) kept.add(idea);
        }
        save(context, kept);
    }

    private static void save(Context context, List<Idea> list) {
        JSONArray array = new JSONArray();
        for (Idea idea : list) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", idea.id);
                item.put("title", idea.title);
                item.put("createdAt", idea.createdAt);
            } catch (JSONException ignored) { }
            array.put(item);
        }
        prefs(context).edit().putString(KEY_IDEAS, array.toString()).apply();
    }
}
