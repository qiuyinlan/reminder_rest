package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 每日任务 + 冲刺模式 + 经验体系。
 *
 * 设计要点：
 * - 每天 3 个小任务，跨天自动归零（只重置当日状态，累计经验和双倍次数保留）
 * - 3 个全完成：+40 经验、+3 次双倍、解锁冲刺模式
 * - 冲刺模式 5 阶段（今日完成 4/7/10/14/19 次），逐级解锁，每阶段给经验和双倍
 * - 冲刺开启后每次学习消耗 1 次双倍，经验翻倍
 * - 连续学习每满 7 天发金宝箱；累计每满 10 次发铜宝箱
 */
final class DailyQuest {
    private static final String PREFS = "daily_quest";

    // 长期保留
    private static final String K_EXP = "exp_total";
    private static final String K_DOUBLES = "doubles";
    private static final String K_TOTAL_ACTIONS = "total_actions";
    private static final String K_NEXT_BRONZE = "next_bronze_at";
    private static final String K_NEXT_GOLD = "next_gold_at";

    // 每日重置
    private static final String K_DAY = "day";
    private static final String K_BONUS_CLAIMED = "bonus_claimed";
    private static final String K_SPRINT_STAGE = "sprint_stage";

    /** 每次学习的基础经验 */
    static final int EXP_PER_ACTION = 10;
    /** 每日三个任务全完成的奖励 */
    static final int ALL_BONUS_EXP = 40;
    static final int ALL_BONUS_DOUBLES = 3;
    /** 冲刺阶段门槛（今日完成次数） */
    static final int[] SPRINT_STEPS = {4, 7, 10, 14, 19};
    static final int[] SPRINT_EXP = {10, 20, 30, 40, 60};
    static final int[] SPRINT_DOUBLES = {1, 1, 2, 2, 3};
    /** 冲刺阶段附带的宝箱（null 表示没有） */
    static final String[] SPRINT_CHEST = {null, "铜", "银", "金", null};

    private DailyQuest() { }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String todayKey() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    /** 跨天时清空当日状态。 */
    static void ensureDay(Context context) {
        String today = todayKey();
        if (!today.equals(prefs(context).getString(K_DAY, ""))) {
            prefs(context).edit()
                    .putString(K_DAY, today)
                    .putBoolean(K_BONUS_CLAIMED, false)
                    .putInt(K_SPRINT_STAGE, 0)
                    .apply();
        }
    }

    // ---------------- 每日三个任务 ----------------

    static final class Quest {
        final String key;
        final String icon;
        final String title;
        final String source; // habit / task
        final int target;

        Quest(String key, String icon, String title, String source, int target) {
            this.key = key;
            this.icon = icon;
            this.title = title;
            this.source = source;
            this.target = target;
        }
    }

    static List<Quest> quests() {
        List<Quest> list = new ArrayList<>();
        list.add(new Quest("habit2", "📖", "完成两个微习惯", "habit", 2));
        list.add(new Quest("task1", "📚", "完成一项任务", "task", 1));
        list.add(new Quest("task2", "🃏", "完成两项任务", "task", 2));
        return list;
    }

    /** 今日已完成的微习惯个数。 */
    static int habitCount(Context context) {
        long dayStart = GameProgress.dayStart(System.currentTimeMillis());
        return Habits.countCompletedToday(context, dayStart);
    }

    /** 今日任务完成次数（同一任务可多次）。 */
    static int taskCount(Context context) {
        int sum = 0;
        for (ReminderSettings.Rule rule : ReminderSettings.listedRules(context)) {
            if (rule.isTaskModule()) {
                sum += ReminderSettings.completedToday(context, rule.id);
            }
        }
        return sum;
    }

    static int questProgress(Context context, Quest quest) {
        return "habit".equals(quest.source) ? habitCount(context) : taskCount(context);
    }

    static boolean questDone(Context context, Quest quest) {
        return questProgress(context, quest) >= quest.target;
    }

    static int doneQuestCount(Context context) {
        int done = 0;
        for (Quest q : quests()) {
            if (questDone(context, q)) done++;
        }
        return done;
    }

    static boolean allQuestsDone(Context context) {
        return doneQuestCount(context) >= quests().size();
    }

    /** 今日总完成次数（任务 + 习惯），冲刺模式用它算阶段。 */
    static int todayActions(Context context) {
        return taskCount(context) + habitCount(context);
    }

    // ---------------- 奖励发放 ----------------

    /** 单个奖励事件，供 UI 提示。 */
    static final class Reward {
        final String icon;
        final String text;
        Reward(String icon, String text) { this.icon = icon; this.text = text; }
    }

    /** 领取每日全完成奖励；已领或未完成返回 null。 */
    static Reward claimAllBonus(Context context) {
        ensureDay(context);
        if (!allQuestsDone(context)) return null;
        if (prefs(context).getBoolean(K_BONUS_CLAIMED, false)) return null;
        SharedPreferences p = prefs(context);
        p.edit()
                .putBoolean(K_BONUS_CLAIMED, true)
                .putInt(K_EXP, p.getInt(K_EXP, 0) + ALL_BONUS_EXP)
                .putInt(K_DOUBLES, p.getInt(K_DOUBLES, 0) + ALL_BONUS_DOUBLES)
                .apply();
        return new Reward("🏆", "今日任务全部完成！+" + ALL_BONUS_EXP + " 经验，"
                + ALL_BONUS_DOUBLES + " 次双倍，冲刺模式已开启");
    }

    static boolean allBonusClaimed(Context context) {
        ensureDay(context);
        return prefs(context).getBoolean(K_BONUS_CLAIMED, false);
    }

    // ---------------- 冲刺模式 ----------------

    /** 今日已解锁到的阶段数（0 = 未开启）。 */
    static int sprintStage(Context context) {
        ensureDay(context);
        return prefs(context).getInt(K_SPRINT_STAGE, 0);
    }

    static boolean sprintUnlocked(Context context) {
        return allBonusClaimed(context);
    }

    /** 按今日完成次数推进冲刺阶段，返回本次新解锁的奖励。 */
    static List<Reward> advanceSprint(Context context) {
        List<Reward> rewards = new ArrayList<>();
        ensureDay(context);
        if (!sprintUnlocked(context)) return rewards;

        int actions = todayActions(context);
        int stage = sprintStage(context);
        if (stage >= SPRINT_STEPS.length || actions < SPRINT_STEPS[stage]) return rewards;

        SharedPreferences p = prefs(context);
        int exp = p.getInt(K_EXP, 0);
        int doubles = p.getInt(K_DOUBLES, 0);
        int nextGold = p.getInt(K_NEXT_GOLD, 7);

        while (stage < SPRINT_STEPS.length && actions >= SPRINT_STEPS[stage]) {
            exp += SPRINT_EXP[stage];
            doubles += SPRINT_DOUBLES[stage];
            StringBuilder sb = new StringBuilder("冲刺第 ").append(stage + 1)
                    .append(" 阶段达成（今日 ").append(SPRINT_STEPS[stage]).append(" 次）+ ")
                    .append(SPRINT_EXP[stage]).append(" 经验");
            if (SPRINT_DOUBLES[stage] > 0) {
                sb.append("、+").append(SPRINT_DOUBLES[stage]).append(" 次双倍");
            }
            String chest = SPRINT_CHEST[stage];
            if (chest != null) {
                sb.append("、").append(chest).append("宝箱");
                if ("金".equals(chest)) nextGold += 7;
            }
            rewards.add(new Reward("⚡", sb.toString()));
            stage++;
        }

        p.edit()
                .putInt(K_EXP, exp)
                .putInt(K_DOUBLES, doubles)
                .putInt(K_SPRINT_STAGE, stage)
                .putInt(K_NEXT_GOLD, nextGold)
                .apply();
        return rewards;
    }

    /** 下一个冲刺目标（今日完成次数），全部达完返回 -1。 */
    static int nextSprintTarget(Context context) {
        int stage = sprintStage(context);
        if (stage >= SPRINT_STEPS.length) return -1;
        return SPRINT_STEPS[stage];
    }

    // ---------------- 经验 / 双倍 ----------------

    static int exp(Context context) {
        return prefs(context).getInt(K_EXP, 0);
    }

    static int doubles(Context context) {
        return prefs(context).getInt(K_DOUBLES, 0);
    }

    static int level(Context context) {
        return exp(context) / 100 + 1;
    }

    static int levelProgress(Context context) {
        return exp(context) % 100;
    }

    /**
     * 一次学习动作完成时调用：加经验（冲刺模式下消耗双倍翻倍）、累计次数，并检查宝箱。
     */
    static List<Reward> onActionDone(Context context) {
        List<Reward> rewards = new ArrayList<>();
        ensureDay(context);
        SharedPreferences p = prefs(context);

        int gain = EXP_PER_ACTION;
        boolean doubled = false;
        if (sprintUnlocked(context) && p.getInt(K_DOUBLES, 0) > 0) {
            p.edit().putInt(K_DOUBLES, p.getInt(K_DOUBLES, 0) - 1).apply();
            gain *= 2;
            doubled = true;
        }
        p.edit().putInt(K_EXP, p.getInt(K_EXP, 0) + gain).apply();
        int total = p.getInt(K_TOTAL_ACTIONS, 0) + 1;
        p.edit().putInt(K_TOTAL_ACTIONS, total).apply();
        rewards.add(new Reward(doubled ? "✨" : "＋",
                (doubled ? "双倍经验 +" : "经验 +") + gain));

        // 累计每满 10 次 → 铜宝箱
        int nextBronze = p.getInt(K_NEXT_BRONZE, 10);
        if (total >= nextBronze) {
            p.edit().putInt(K_NEXT_BRONZE, nextBronze + 10).apply();
            rewards.add(new Reward("🟫", "累计 " + nextBronze + " 次，获得铜宝箱"));
        }

        // 连续每满 7 天 → 金宝箱
        int streak = GameProgress.getGlobalStreak(context);
        int nextGold = p.getInt(K_NEXT_GOLD, 7);
        if (streak >= nextGold) {
            p.edit().putInt(K_NEXT_GOLD, nextGold + 7).apply();
            rewards.add(new Reward("🏅", "连续 " + nextGold + " 天，获得金宝箱"));
        }

        return rewards;
    }

    /** 今日是否已经领过全完成奖励（用于首页按钮态）。 */
    static boolean bonusAvailable(Context context) {
        ensureDay(context);
        return allQuestsDone(context) && !prefs(context).getBoolean(K_BONUS_CLAIMED, false);
    }

    /**
     * 一次学习动作完成后的统一入口：先记经验/次数，再结每日全完成，
     * 再推冲刺阶段，并把奖励提示直接弹出来（只在 Activity 上下文下弹）。
     */
    static List<Reward> afterAction(Context context) {
        List<Reward> rewards = new ArrayList<>();
        rewards.addAll(onActionDone(context));
        Reward bonus = claimAllBonus(context);
        if (bonus != null) rewards.add(bonus);
        rewards.addAll(advanceSprint(context));

        if (!rewards.isEmpty() && context instanceof android.app.Activity) {
            StringBuilder sb = new StringBuilder();
            for (Reward r : rewards) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(r.icon).append(' ').append(r.text);
            }
            android.widget.Toast.makeText(context, sb.toString(), android.widget.Toast.LENGTH_LONG).show();
        }
        return rewards;
    }
}
