package io.iftech.healthreminder;

import android.content.Context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 子任务：挂在某个任务下面的小条目（流程式拆解）。
 * 复用 ReminderSettings.Rule 存，module = subtask，deckId 存父任务 id，
 * step 字段存顺序（从 0 开始），不排独立闹钟；
 * 完成情况记在 ReminderRecord，module 也用 subtask。
 */
final class Subtasks {
    private Subtasks() { }

    static List<ReminderSettings.Rule> of(Context context, int parentId) {
        List<ReminderSettings.Rule> result = new ArrayList<>();
        for (ReminderSettings.Rule rule : ReminderSettings.scheduledRules(context)) {
            if (rule.isSubtaskModule() && parentKey(parentId).equals(rule.deckId)) result.add(rule);
        }
        Collections.sort(result, (a, b) -> Integer.compare(a.step, b.step));
        return result;
    }

    static int count(Context context, int parentId, long dayStart) {
        int done = 0;
        for (ReminderSettings.Rule rule : of(context, parentId)) {
            if (doneToday(context, parentId, rule.title, dayStart)) done++;
        }
        return done;
    }

    static ReminderSettings.Rule add(Context context, int parentId, String title, int step) {
        ReminderSettings.Rule rule = ReminderSettings.Rule
                .withIntervalSeconds(ReminderSettings.nextRuleId(context), title, 0, 0, 24 * 3600, 1, true, false)
                .withModule(ReminderSettings.MODULE_SUBTASK)
                .withDeck(parentKey(parentId), false)
                .withStep(step);
        ReminderSettings.upsertRule(context, rule);
        return rule;
    }

    static boolean allCompleted(Context context, int parentId, long dayStart) {
        List<ReminderSettings.Rule> list = of(context, parentId);
        if (list.isEmpty()) return false;
        for (ReminderSettings.Rule rule : list) {
            if (!doneToday(context, parentId, rule.title, dayStart)) return false;
        }
        return true;
    }

    static void delete(Context context, ReminderSettings.Rule subtask) {
        ReminderSettings.deleteRule(context, subtask.id);
    }

    static void deleteForParent(Context context, int parentId) {
        for (ReminderSettings.Rule rule : of(context, parentId)) {
            ReminderSettings.deleteRule(context, rule.id);
        }
    }

    static boolean doneToday(Context context, int parentId, String title, long dayStart) {
        return ReminderRecord.completedOn(context, ReminderSettings.MODULE_SUBTASK,
                recordKey(parentId, title), dayStart);
    }

    static void toggle(Context context, int parentId, String title, boolean done, long dayStart) {
        String key = recordKey(parentId, title);
        if (done) {
            ReminderRecord.deleteCompletion(context, ReminderSettings.MODULE_SUBTASK, key, dayStart);
        } else {
            long now = System.currentTimeMillis();
            ReminderRecord.finish(context, java.util.UUID.randomUUID().toString(), key,
                    ReminderRecord.KIND_DONE, now, now, "子任务勾选", ReminderSettings.MODULE_SUBTASK);
        }
    }

    private static String recordKey(int parentId, String title) {
        return parentKey(parentId) + ":" + title;
    }

    private static String parentKey(int parentId) {
        return "p" + parentId;
    }
}
