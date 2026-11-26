package io.iftech.healthreminder;

import android.content.Context;

import java.util.UUID;

/**
 * 首页「抽一个」里直接勾任务时用的写回逻辑。
 * 与任务页 toggleDone 保持一致：勾主任务连带勾子任务，取消则一起退。
 */
final class TaskInlineToggle {
    private TaskInlineToggle() { }

    static void setMainDone(Context context, ReminderSettings.Rule task, long dayStart,
                            boolean willBeDone) {
        if (!willBeDone) {
            // 取消完成：子任务一起退回，并删掉整条任务的完成记录
            for (ReminderSettings.Rule sub : Subtasks.of(context, task.id)) {
                if (Subtasks.doneToday(context, task.id, sub.title, dayStart)) {
                    Subtasks.toggleQuiet(context, task.id, sub.title, true, dayStart);
                }
            }
            ReminderRecord.deleteCompletion(context, ReminderSettings.MODULE_TASK,
                    task.title, dayStart);
            ReminderSettings.unmarkCompleted(context, task.id);
            return;
        }

        // 勾主任务：还没勾的子任务一并勾上（静默，不重复触发父任务联动）
        for (ReminderSettings.Rule sub : Subtasks.of(context, task.id)) {
            if (!Subtasks.doneToday(context, task.id, sub.title, dayStart)) {
                Subtasks.toggleQuiet(context, task.id, sub.title, false, dayStart);
            }
        }
        long now = System.currentTimeMillis();
        ReminderRecord.finish(context, UUID.randomUUID().toString(), task.title,
                ReminderRecord.KIND_DONE, now, now, "任务清单勾选", ReminderSettings.MODULE_TASK);
        ReminderSettings.markCompleted(context, task.id, Math.max(1, task.dailyTarget));
    }
}
