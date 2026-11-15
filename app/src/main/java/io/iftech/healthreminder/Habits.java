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
 * 微习惯：每日重复的小目标（如打八段锦、颈椎训练等）。
 * 每个习惯有独立的频率（周一到周日 7 个圆点），完成情况按天记录。
 */
final class Habits {
    private static final String PREFS = "habit_settings";
    private static final String KEY_HABITS = "habits_v1";
    private static final String KEY_COMPLETIONS = "habit_completions_v1";

    private Habits() { }

    static class Habit {
        final int id;
        String title;
        /** 频率掩码：周一=1, 周二=2, ..., 周日=64，全选=127 */
        int frequencyMask;
        final long createdAt;

        Habit(int id, String title, int frequencyMask, long createdAt) {
            this.id = id;
            this.title = title;
            this.frequencyMask = frequencyMask;
            this.createdAt = createdAt;
        }

        boolean isScheduledForToday() {
            Calendar cal = Calendar.getInstance();
            int dow = cal.get(Calendar.DAY_OF_WEEK);
            int bit = dowToBit(dow);
            return (frequencyMask & bit) != 0;
        }

        static int dowToBit(int dayOfWeek) {
            // Calendar.SUNDAY=1, MONDAY=2, ..., SATURDAY=7
            // 我们要：周一=1, 周二=2, ..., 周日=64
            if (dayOfWeek == Calendar.SUNDAY) return 64;
            return 1 << (dayOfWeek - 2);
        }

        static int bitToDow(int bit) {
            if (bit == 64) return Calendar.SUNDAY;
            for (int i = 0; i < 7; i++) {
                if ((bit & (1 << i)) != 0) return Calendar.MONDAY + i;
            }
            return Calendar.MONDAY;
        }
    }

    static List<Habit> all(Context context) {
        List<Habit> result = new ArrayList<>();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        String json = prefs.getString(KEY_HABITS, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                Habit habit = new Habit(
                        obj.getInt("id"),
                        obj.getString("title"),
                        obj.optInt("frequencyMask", 127),
                        obj.optLong("createdAt", System.currentTimeMillis())
                );
                result.add(habit);
            }
        } catch (JSONException ignored) { }
        return result;
    }

    /** 子任务挂在习惯下时用的父 id，避开任务 id（任务 id 从 1 连续增长）。 */
    static int parentId(int habitId) {
        return 100000 + habitId;
    }

    static Habit add(Context context, String title) {
        List<Habit> habits = all(context);
        int nextId = 1;
        for (Habit h : habits) {
            if (h.id >= nextId) nextId = h.id + 1;
        }
        Habit habit = new Habit(nextId, title, 127, System.currentTimeMillis());
        habits.add(habit);
        save(context, habits, null);
        return habit;
    }

    static void update(Context context, Habit habit) {
        List<Habit> habits = all(context);
        for (int i = 0; i < habits.size(); i++) {
            if (habits.get(i).id == habit.id) {
                habits.set(i, habit);
                break;
            }
        }
        save(context, habits, null);
    }

    static void delete(Context context, int habitId) {
        List<Habit> habits = all(context);
        List<Habit> filtered = new ArrayList<>();
        for (Habit h : habits) {
            if (h.id != habitId) filtered.add(h);
        }
        save(context, filtered, null);
        // 同时删除完成记录
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        SharedPreferences.Editor ed = prefs.edit();
        ed.remove(KEY_COMPLETIONS + "_" + habitId);
        ed.apply();
    }

    private static void save(Context context, List<Habit> habits, Habit newOrUpdated) {
        JSONArray arr = new JSONArray();
        for (Habit h : habits) {
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", h.id);
                obj.put("title", h.title);
                obj.put("frequencyMask", h.frequencyMask);
                obj.put("createdAt", h.createdAt);
                arr.put(obj);
            } catch (JSONException ignored) { }
        }
        if (newOrUpdated != null) {
            // 已经包含在 habits 列表里了
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        prefs.edit().putString(KEY_HABITS, arr.toString()).apply();
    }

    static void toggleCompletion(Context context, int habitId, long dayStart, boolean done) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        String key = KEY_COMPLETIONS + "_" + habitId;
        String json = prefs.getString(key, "{}");
        try {
            JSONObject obj = new JSONObject(json);
            String dayKey = String.valueOf(dayStart);
            if (done) {
                obj.put(dayKey, System.currentTimeMillis());
            } else {
                obj.remove(dayKey);
            }
            prefs.edit().putString(key, obj.toString()).apply();
        } catch (JSONException ignored) { }
    }

    static boolean isCompletedToday(Context context, int habitId, long dayStart) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, 0);
        String key = KEY_COMPLETIONS + "_" + habitId;
        String json = prefs.getString(key, "{}");
        try {
            JSONObject obj = new JSONObject(json);
            return obj.has(String.valueOf(dayStart));
        } catch (JSONException e) {
            return false;
        }
    }

    static int countCompletedToday(Context context, long dayStart) {
        int count = 0;
        for (Habit habit : all(context)) {
            if (habit.isScheduledForToday() && isCompletedToday(context, habit.id, dayStart)) {
                count++;
            }
        }
        return count;
    }

    static List<Habit> todayScheduled(Context context) {
        List<Habit> result = new ArrayList<>();
        for (Habit habit : all(context)) {
            if (habit.isScheduledForToday()) {
                result.add(habit);
            }
        }
        return result;
    }

    static String getDayLabel(int bit) {
        String[] labels = {"一", "二", "三", "四", "五", "六", "日"};
        if (bit == 64) return labels[6];
        for (int i = 0; i < 7; i++) {
            if ((bit & (1 << i)) != 0) return labels[i];
        }
        return "";
    }
}
