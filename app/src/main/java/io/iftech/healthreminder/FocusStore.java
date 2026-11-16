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
 * 想法虽然是临时捕捉，但背后是一条真任务规则（enabled=false，不会弹提醒），
 * 这样它才能改名、加步骤，跟任务页的小任务完全同构。
 * 任务页会把这些 id 过滤掉，免得清单被灵感灌满；
 * 一旦它被设为「现在」，就从灵感表里移出，变成普通任务。
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

    /** 来自任务清单时是任务 id；旧数据缺 id 时为 -1。 */
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
        /** 背后的任务规则 id；-1 表示旧数据还没补。 */
        final int ruleId;

        Idea(String id, String title, long createdAt, int ruleId) {
            this.id = id;
            this.title = title;
            this.createdAt = createdAt;
            this.ruleId = ruleId;
        }
    }

    static List<Idea> ideas(Context context) {
        List<Idea> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY_IDEAS, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                result.add(new Idea(item.getString("id"), item.getString("title"),
                        item.optLong("createdAt", 0L), item.optInt("ruleId", -1)));
            }
        } catch (JSONException ignored) { }
        return result;
    }

    static int ideaCount(Context context) {
        return ideas(context).size();
    }

    /** 任务页用它把灵感条目从清单里排除。 */
    static List<Integer> ideaRuleIds(Context context) {
        List<Integer> ids = new ArrayList<>();
        for (Idea idea : ideas(context)) {
            if (idea.ruleId >= 0) ids.add(idea.ruleId);
        }
        return ids;
    }

    static void addIdea(Context context, String title, int ruleId) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) return;
        List<Idea> list = ideas(context);
        list.add(new Idea(UUID.randomUUID().toString(), value, System.currentTimeMillis(), ruleId));
        save(context, list);
    }

    static void renameIdea(Context context, String id, String title) {
        String value = title == null ? "" : title.trim();
        if (value.isEmpty()) return;
        List<Idea> updated = new ArrayList<>();
        for (Idea item : ideas(context)) {
            updated.add(item.id.equals(id)
                    ? new Idea(item.id, value, item.createdAt, item.ruleId)
                    : item);
        }
        save(context, updated);
    }

    static void removeIdea(Context context, String id) {
        List<Idea> kept = new ArrayList<>();
        for (Idea idea : ideas(context)) {
            if (!idea.id.equals(id)) kept.add(idea);
        }
        save(context, kept);
    }

    /** 旧数据（没有 ruleId）在第一次打开时补建任务，返回可用 id。 */
    static int ensureIdeaRule(Context context, Idea idea) {
        if (idea.ruleId >= 0) return idea.ruleId;
        ReminderSettings.Rule rule = createTaskRule(context, idea.title);
        int ruleId = rule != null ? rule.id : -1;
        if (ruleId < 0) return -1;
        List<Idea> updated = new ArrayList<>();
        for (Idea item : ideas(context)) {
            if (item.id.equals(idea.id)) {
                updated.add(new Idea(item.id, item.title, item.createdAt, ruleId));
            } else {
                updated.add(item);
            }
        }
        save(context, updated);
        return ruleId;
    }

    /** 建一条「不提醒」的任务：只占清单一行，靠手动勾。 */
    static ReminderSettings.Rule createTaskRule(Context context, String title) {
        ArrayList<Integer> points = new ArrayList<>();
        points.add(9 * 3600);
        ReminderSettings.Rule rule = ReminderSettings.Rule.withSpecificTimes(
                        ReminderSettings.nextRuleId(context), title, 9 * 60, 21 * 60, 24 * 3600,
                        points, 1, true, false)
                .withModule(ReminderSettings.MODULE_TASK)
                .withCategory(0);
        ReminderSettings.upsertRule(context, rule);
        return ReminderSettings.ruleById(context, rule.id);
    }

    private static void save(Context context, List<Idea> list) {
        JSONArray array = new JSONArray();
        for (Idea idea : list) {
            JSONObject item = new JSONObject();
            try {
                item.put("id", idea.id);
                item.put("title", idea.title);
                item.put("createdAt", idea.createdAt);
                item.put("ruleId", idea.ruleId);
            } catch (JSONException ignored) { }
            array.put(item);
        }
        prefs(context).edit().putString(KEY_IDEAS, array.toString()).apply();
    }
}
