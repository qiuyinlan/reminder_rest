package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 游戏化机制开关与配置。
 * 
 * 核心设计：
 * 1. 每日额度制 - 把大目标切成「今天只做一点点」
 * 2. 即时反馈 - 打卡瞬间给动画、音效、夸奖
 * 3. 批次激励 - 每 7/21/66 天宝箱奖励
 * 4. 弹性补签 - 漏了一天不清零，可补签
 * 5. 进度可视化 - 习惯之环/渐变日历
 */
final class GameSettings {
    private static final String PREFS = "game_settings";
    private static final String KEY_ENABLED = "game_enabled";
    
    /** 连胜奖励里程碑（天数） */
    static final int[] MILESTONES = {7, 21, 66};
    /** 宝箱图标 */
    static final String[] CHEST_ICONS = {"🎁", "💎", "👑"};
    
    private GameSettings() { }
    
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    
    /** 游戏化机制是否启用 */
    static boolean isEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }
    
    /** 设置游戏化机制开关 */
    static void setEnabled(Context context, boolean enabled) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply();
    }
    
    /** 获取下一个里程碑天数 */
    static int nextMilestone(int currentStreak) {
        for (int ms : MILESTONES) {
            if (ms > currentStreak) return ms;
        }
        return currentStreak + 7; // 超过最大里程碑后，每 7 天一个
    }

    /** 等级称号：按连续天数成长，给用户「在长大」的感觉。 */
    static String levelTitle(int streak) {
        if (streak >= 66) return "王者森林";
        if (streak >= 21) return "大树";
        if (streak >= 14) return "小树";
        if (streak >= 7) return "青苗";
        if (streak >= 3) return "嫩芽";
        return "种子";
    }

    /** 等级序号 0-5，用于未来做进度条。 */
    static int levelIndex(int streak) {
        if (streak >= 66) return 5;
        if (streak >= 21) return 4;
        if (streak >= 14) return 3;
        if (streak >= 7) return 2;
        if (streak >= 3) return 1;
        return 0;
    }
    
    /** 当前里程碑对应的宝箱图标索引 */
    static int chestIndex(int milestone) {
        for (int i = 0; i < MILESTONES.length; i++) {
            if (MILESTONES[i] == milestone) return i;
        }
        return MILESTONES.length - 1;
    }
}
