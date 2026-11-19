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
 * 「现在」页的轻量状态：1 个主线 + 2 个支线，共 3 个并行任务。
 *
 * 想法虽然是临时捕捉，但背后是一条真任务规则（enabled=false，不会弹提醒），
 * 这样它才能改名、加步骤，跟任务页的小任务完全同构。
 * 任务页会把这些 id 过滤掉，免得清单被灵感灌满；
 * 一旦它被设为「现在」，就从灵感表里移出，变成普通任务。
 */
final class FocusStore {
    private static final String PREFS = "focus_state";
    static final String KEY_MAIN = "main_task";
    static final String KEY_SUB1 = "sub_task_1";
    static final String KEY_SUB2 = "sub_task_2";
    private static final String KEY_IDEAS = "ideas";

    private FocusStore() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------ 任务槽位（1 主线 +2 支线）

    static final class TaskSlot {
        final String slotKey;
        final String title;
        final int ruleId;
        final long startedAt;
        final int targetSeconds;

        TaskSlot(String slotKey, String title, int ruleId, long startedAt, int targetSeconds) {
            this.slotKey = slotKey;
            this.title = title;
            this.ruleId = ruleId;
            this.startedAt = startedAt;
            this.targetSeconds = targetSeconds;
        }

        boolean isEmpty() { return title == null || title.isEmpty(); }
        boolean isRunning() { return startedAt > 0; }
    }

    /** 获取主线任务 */
    static TaskSlot getMain(Context context) { return getSlot(context, KEY_MAIN); }
    /** 获取支线 1 */
    static TaskSlot getSub1(Context context) { return getSlot(context, KEY_SUB1); }
    /** 获取支线 2 */
    static TaskSlot getSub2(Context context) { return getSlot(context, KEY_SUB2); }

    /** 设置主线任务 */
    static void setMain(Context context, String title, int ruleId, int targetSeconds) {
        setSlot(context, KEY_MAIN, title, ruleId, targetSeconds);
    }
    /** 设置支线 1 */
    static void setSub1(Context context, String title, int ruleId, int targetSeconds) {
        setSlot(context, KEY_SUB1, title, ruleId, targetSeconds);
    }
    /** 设置支线 2 */
    static void setSub2(Context context, String title, int ruleId, int targetSeconds) {
        setSlot(context, KEY_SUB2, title, ruleId, targetSeconds);
    }

    /** 开始任务（记录开始时间） */
    static void startSlot(Context context, String key) {
        try {
            String json = prefs(context).getString(key, "");
            if (json == null || json.isEmpty()) return;
            JSONObject obj = new JSONObject(json);
            obj.put("startedAt", System.currentTimeMillis());
            SharedPreferences.Editor editor = prefs(context).edit();
            editor.putString(key, obj.toString());
            editor.apply();
        } catch (JSONException ignored) { }
    }

    /** 清空主线 */
    static void clearMain(Context context) { clearSlot(context, KEY_MAIN); }
    /** 清空支线 1 */
    static void clearSub1(Context context) { clearSlot(context, KEY_SUB1); }
    /** 清空支线 2 */
    static void clearSub2(Context context) { clearSlot(context, KEY_SUB2); }
    /** 清空指定槽位（公开方法，供 FocusActivity 使用） */
    static void clearSlot(Context context, String key) { prefs(context).edit().remove(key).apply(); }

    private static TaskSlot getSlot(Context context, String key) {
        try {
            String json = prefs(context).getString(key, "");
            if (json == null || json.isEmpty()) return new TaskSlot(key, "", -1, 0, 0);
            JSONObject obj = new JSONObject(json);
            return new TaskSlot(key,
                    obj.optString("title", ""),
                    obj.optInt("ruleId", -1),
                    obj.optLong("startedAt", 0L),
                    obj.optInt("targetSeconds", 0));
        } catch (JSONException e) {
            return new TaskSlot(key, "", -1, 0, 0);
        }
    }

    private static void setSlot(Context context, String key, String title, int ruleId, int targetSeconds) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("title", title == null ? "" : title.trim());
            obj.put("ruleId", ruleId);
            obj.put("targetSeconds", targetSeconds);
            SharedPreferences.Editor editor = prefs(context).edit();
            editor.putString(key, obj.toString());
            editor.apply();
        } catch (JSONException ignored) { }
    }

    /** 只改时长，不动标题和开始时间。 */
    static void setTarget(Context context, String key, int targetSeconds) {
        try {
            String json = prefs(context).getString(key, "");
            if (json == null || json.isEmpty()) return;
            JSONObject obj = new JSONObject(json);
            obj.put("targetSeconds", targetSeconds);
            prefs(context).edit().putString(key, obj.toString()).apply();
        } catch (JSONException ignored) { }
    }

    private static void removeSlot(Context context, String key) {
        prefs(context).edit().remove(key).apply();
    }

    // ------------------------------------------ 向后兼容旧接口（全部映射到主线槽位）

    /** 任意槽位有任务就算「有当前任务」。 */
    static boolean hasCurrent(Context context) {
        return !getMain(context).isEmpty() || !getSub1(context).isEmpty() || !getSub2(context).isEmpty();
    }

    static String currentTitle(Context context) { return getMain(context).title; }

    static int currentRuleId(Context context) { return getMain(context).ruleId; }

    static long currentStartedAt(Context context) { return getMain(context).startedAt; }

    static int currentTargetSeconds(Context context) { return getMain(context).targetSeconds; }

    /** 旧接口：写主线并立即开始（悬浮条取用想法时用，行为与旧版一致）。 */
    static void setCurrent(Context context, String title, int ruleId) {
        setCurrent(context, title, ruleId, 0);
    }

    static void setCurrent(Context context, String title, int ruleId, int targetSeconds) {
        setSlot(context, KEY_MAIN, title, ruleId, targetSeconds);
        startSlot(context, KEY_MAIN);
    }

    static void clearCurrent(Context context) { clearSlot(context, KEY_MAIN); }

    // -------------------------------------------------------------- 想法清单

    static final class Idea {
        final String id;
        final String title;
        final long createdAt;
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

    static List<Integer> ideaRuleIds(Context context) {
        List<Integer> ids = new ArrayList<>();
        for (Idea idea : ideas(context)) {
            if (idea.ruleId >= 0) ids.add(idea.ruleId);
        }
        return ids;
    }

    /** 这条规则是不是「现在」页记下的想法（只存在想法列表，不算提醒事项）。 */
    static boolean isIdeaRule(Context context, int ruleId) {
        return ruleId >= 0 && ideaRuleIds(context).contains(ruleId);
    }

    /** 过滤掉想法规则的提醒列表。 */
    static List<ReminderSettings.Rule> withoutIdeas(Context context, List<ReminderSettings.Rule> rules) {
        List<Integer> ideaIds = ideaRuleIds(context);
        if (ideaIds.isEmpty()) return rules;
        List<ReminderSettings.Rule> kept = new ArrayList<>();
        for (ReminderSettings.Rule rule : rules) {
            if (!ideaIds.contains(rule.id)) kept.add(rule);
        }
        return kept;
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

    /** 真的丢掉这条想法：连带删除它背后那条规则，免得变成孤儿跑到提醒事项里。 */
    static void deleteIdea(Context context, String id) {
        List<Idea> kept = new ArrayList<>();
        for (Idea idea : ideas(context)) {
            if (!idea.id.equals(id)) kept.add(idea);
            else if (idea.ruleId >= 0) ReminderSettings.deleteRule(context, idea.ruleId);
        }
        save(context, kept);
    }

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
