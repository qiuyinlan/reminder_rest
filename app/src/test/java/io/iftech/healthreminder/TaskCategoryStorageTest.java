package io.iftech.healthreminder;

import static org.junit.Assert.assertEquals;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** 分类 id 必须能跟着规则一起存进 SharedPreferences 再读回来。 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 33)
public final class TaskCategoryStorageTest {

    @Test
    public void categoryIdSurvivesSaveAndReload() {
        Application app = RuntimeEnvironment.getApplication();
        TaskCategories.Category cat = TaskCategories.add(app, "学习");
        System.out.println("[DIAG] added category id=" + cat.id + " all=" + dumpCategories(app));

        int ruleId = ReminderSettings.nextRuleId(app);
        ReminderSettings.Rule rule = ReminderSettings.Rule
                .withIntervalSeconds(ruleId, "背单词", 9 * 60, 12 * 60, 3600, 1, true, true)
                .withModule(ReminderSettings.MODULE_TASK);
        ReminderSettings.upsertRule(app, rule);
        System.out.println("[DIAG] stored categoryId after create="
                + ReminderSettings.ruleById(app, ruleId).categoryId);

        ReminderSettings.Rule moved = ReminderSettings.ruleById(app, ruleId).withCategory(cat.id);
        System.out.println("[DIAG] rule handed to upsert has categoryId=" + moved.categoryId);
        ReminderSettings.upsertRule(app, moved);

        ReminderSettings.Rule stored = ReminderSettings.ruleById(app, ruleId);
        System.out.println("[DIAG] stored after move=" + stored.categoryId
                + " raw=" + ReminderSettings.prefs(app).getString("reminder_rules_v2", "null"));

        assertEquals("categoryId 应该跟着规则存下来", cat.id, stored.categoryId);
        assertEquals("分类列表里应该有默认 + 新建的", 2, TaskCategories.all(app).size());
        assertEquals("今天的任务应该带着分类", cat.id, Tasks.today(app).get(0).categoryId);
    }

    @Test
    public void everyWithMethodKeepsCategoryId() {
        ReminderSettings.Rule base = ReminderSettings.Rule
                .withIntervalSeconds(1, "t", 9 * 60, 12 * 60, 3600, 1, true, true)
                .withModule(ReminderSettings.MODULE_TASK)
                .withCategory(7);

        assertEquals(7, base.withTitle("x").categoryId);
        assertEquals(7, base.withReminderMessage("m").categoryId);
        assertEquals(7, base.withIcon("★").categoryId);
        assertEquals(7, base.withOverlayArtwork(ReminderArtworkCatalog.DEFAULT).categoryId);
        assertEquals(7, base.withColor(2).categoryId);
        assertEquals(7, base.withEnabled(false).categoryId);
        assertEquals(7, base.withModule(ReminderSettings.MODULE_TASK).categoryId);
        assertEquals(7, base.withDeck("d", false).categoryId);
        assertEquals(7, base.withStep(3).categoryId);
        assertEquals(3, base.withStep(3).step);
        assertEquals(9, base.withCategory(9).categoryId);
    }

    private static String dumpCategories(Application app) {
        StringBuilder sb = new StringBuilder();
        for (TaskCategories.Category c : TaskCategories.all(app)) {
            sb.append('[').append(c.id).append(':').append(c.name).append(']');
        }
        return sb.toString();
    }
}
