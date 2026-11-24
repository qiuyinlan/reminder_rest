package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * 游戏化进度追踪：连胜、宝箱、补签、成就等。
 * 
 * 存储结构：
 * - 每个习惯/任务有独立的连胜计数
 * - 完成记录按天存储（dayStart 为 key）
 * - 补签记录单独存储（避免滥用）
 * - 宝箱领取状态
 */
final class GameProgress {
    private static final String PREFS = "game_progress";
    private static final String KEY_STREAK = "streak_";
    private static final String KEY_COMPLETIONS = "completions_";
    private static final String KEY_MAKEUPS = "makeups_";
    private static final String KEY_CHEST_CLAIMED = "chest_claimed_";
    
    /** 最大补签天数（超过这个天数就不让补了） */
    static final int MAX_MAKEUP_DAYS = 3;
    
    private GameProgress() { }
    
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
    
    // ==================== 连胜管理 ====================
    
    /** 获取当前连胜天数 */
    static int getStreak(Context context, String habitKey) {
        return prefs(context).getInt(KEY_STREAK + habitKey, 0);
    }
    
    /** 设置连胜天数 */
    static void setStreak(Context context, String habitKey, int streak) {
        prefs(context).edit().putInt(KEY_STREAK + habitKey, streak).apply();
    }
    
    /** 完成打卡，更新连胜 */
    static void markComplete(Context context, String habitKey, long dayStart) {
        // 检查是否连续
        long yesterday = dayStart - 24 * 60 * 60 * 1000L;
        boolean wasCompleteYesterday = isCompletedOn(context, habitKey, yesterday);
        
        int streak = getStreak(context, habitKey);
        if (wasCompleteYesterday || streak == 0) {
            // 连续或刚开始，连胜 +1
            setStreak(context, habitKey, streak + 1);
        } else {
            // 断了，从 1 开始
            setStreak(context, habitKey, 1);
        }
        
        // 记录今天已完成
        addCompletion(context, habitKey, dayStart);
    }
    
    /** 重置连胜（用户主动或强制） */
    static void resetStreak(Context context, String habitKey) {
        setStreak(context, habitKey, 0);
    }
    
    // ==================== 完成记录 ====================
    
    /** 添加完成记录 */
    private static void addCompletion(Context context, String habitKey, long dayStart) {
        try {
            SharedPreferences prefs = prefs(context);
            String key = KEY_COMPLETIONS + habitKey;
            String json = prefs.getString(key, "{}");
            JSONObject obj = new JSONObject(json);
            obj.put(String.valueOf(dayStart), System.currentTimeMillis());
            prefs.edit().putString(key, obj.toString()).apply();
        } catch (JSONException ignored) { }
    }
    
    /** 某天是否已完成 */
    static boolean isCompletedOn(Context context, String habitKey, long dayStart) {
        try {
            SharedPreferences prefs = prefs(context);
            String key = KEY_COMPLETIONS + habitKey;
            String json = prefs.getString(key, "{}");
            JSONObject obj = new JSONObject(json);
            return obj.has(String.valueOf(dayStart));
        } catch (JSONException e) {
            return false;
        }
    }
    
    /** 获取最近 N 天的完成记录（用于可视化） */
    static List<Long> getRecentCompletions(Context context, String habitKey, int days) {
        List<Long> result = new ArrayList<>();
        try {
            SharedPreferences prefs = prefs(context);
            String key = KEY_COMPLETIONS + habitKey;
            String json = prefs.getString(key, "{}");
            JSONObject obj = new JSONObject(json);
            
            long now = System.currentTimeMillis();
            long cutoff = now - days * 24 * 60 * 60 * 1000L;
            
            JSONArray names = obj.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    long dayStart = Long.parseLong(names.getString(i));
                    if (dayStart >= cutoff) {
                        result.add(dayStart);
                    }
                }
            }
        } catch (JSONException ignored) { }
        return result;
    }
    
    // ==================== 补签管理 ====================
    
    /** 获取可补签的日期列表（最近 MAX_MAKEUP_DAYS 天内未完成的） */
    static List<Long> getAvailableMakeupDays(Context context, String habitKey) {
        List<Long> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        long today = dayStart(now);
        
        for (int i = 1; i <= MAX_MAKEUP_DAYS; i++) {
            long day = today - i * 24 * 60 * 60 * 1000L;
            if (!isCompletedOn(context, habitKey, day) && !wasMakeupUsed(context, habitKey, day)) {
                result.add(day);
            }
        }
        return result;
    }
    
    /** 补签某天 */
    static void markMakeup(Context context, String habitKey, long dayStart) {
        // 记录补签（避免重复补同一天）
        try {
            SharedPreferences prefs = prefs(context);
            String key = KEY_MAKEUPS + habitKey;
            String json = prefs.getString(key, "{}");
            JSONObject obj = new JSONObject(json);
            obj.put(String.valueOf(dayStart), System.currentTimeMillis());
            prefs.edit().putString(key, obj.toString()).apply();
        } catch (JSONException ignored) { }
        
        // 同时标记为完成
        addCompletion(context, habitKey, dayStart);
    }
    
    /** 某天是否已用过补签 */
    private static boolean wasMakeupUsed(Context context, String habitKey, long dayStart) {
        try {
            SharedPreferences prefs = prefs(context);
            String key = KEY_MAKEUPS + habitKey;
            String json = prefs.getString(key, "{}");
            JSONObject obj = new JSONObject(json);
            return obj.has(String.valueOf(dayStart));
        } catch (JSONException e) {
            return false;
        }
    }
    
    // ==================== 宝箱管理 ====================
    
    /** 检查是否可以领取宝箱（达到里程碑且未领取） */
    static Integer checkChestAvailable(Context context, String habitKey) {
        int streak = getStreak(context, habitKey);
        for (int milestone : GameSettings.MILESTONES) {
            if (streak >= milestone && !isChestClaimed(context, habitKey, milestone)) {
                return milestone;
            }
        }
        return null;
    }
    
    /** 宝箱是否已领取 */
    static boolean isChestClaimed(Context context, String habitKey, int milestone) {
        try {
            SharedPreferences prefs = prefs(context);
            String key = KEY_CHEST_CLAIMED + habitKey;
            String json = prefs.getString(key, "[]");
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                if (array.getInt(i) == milestone) return true;
            }
            return false;
        } catch (JSONException e) {
            return false;
        }
    }
    
    /** 标记宝箱已领取 */
    static void markChestClaimed(Context context, String habitKey, int milestone) {
        try {
            SharedPreferences prefs = prefs(context);
            String key = KEY_CHEST_CLAIMED + habitKey;
            String json = prefs.getString(key, "[]");
            JSONArray array = new JSONArray(json);
            array.put(milestone);
            prefs.edit().putString(key, array.toString()).apply();
        } catch (JSONException ignored) { }
    }
    
    // ==================== 工具方法 ====================
    
    /** 获取当天开始时间戳（本地时区 00:00） */
    static long dayStart(long timeMillis) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeMillis);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
    
    /** 获取习惯的 key（用于存储） */
    static String makeHabitKey(String module, int id) {
        return module + "_" + id;
    }
}
