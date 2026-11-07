package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 经期记录：每条记录 = 开始日期 + 结束日期（未结束时为空字符串）。
 * 只保存在本机，以开始日期为键，所以同一天开始只会有一条记录。
 */
final class PeriodLog {
    static final String PREFS = "period_log";
    private static final String KEY_PREFIX = "period_";
    private static final String PATTERN = "yyyy-MM-dd";
    /** 一次经期最长按 10 天计，避免忘了点「结束」时把粉色线拉满整月。 */
    private static final int MAX_DAYS = 10;

    static final class Range {
        final String startKey;
        final String endKey;

        Range(String startKey, String endKey) {
            this.startKey = startKey;
            this.endKey = endKey == null ? "" : endKey;
        }

        boolean ongoing() {
            return endKey.isEmpty();
        }

        String effectiveEndKey() {
            return ongoing() ? todayKey() : endKey;
        }

        String label() {
            return monthDay(startKey) + " - " + (ongoing() ? "至今" : monthDay(endKey));
        }
    }

    private PeriodLog() { }

    static String dayKey(Calendar day) {
        return new SimpleDateFormat(PATTERN, Locale.ROOT).format(day.getTime());
    }

    static String todayKey() {
        return dayKey(Calendar.getInstance());
    }

    private static Calendar parse(String key) {
        if (key == null || key.length() != PATTERN.length()) return null;
        SimpleDateFormat format = new SimpleDateFormat(PATTERN, Locale.ROOT);
        format.setLenient(false);
        try {
            Calendar value = Calendar.getInstance();
            value.setTime(format.parse(key));
            value.set(Calendar.HOUR_OF_DAY, 0);
            value.set(Calendar.MINUTE, 0);
            value.set(Calendar.SECOND, 0);
            value.set(Calendar.MILLISECOND, 0);
            return value;
        } catch (ParseException error) {
            return null;
        }
    }

    static String monthDay(String key) {
        Calendar day = parse(key);
        if (day == null) return key;
        return new SimpleDateFormat("M月d日", Locale.CHINA).format(day.getTime());
    }

    static List<Range> all(Context context) {
        List<Range> ranges = new ArrayList<>();
        Map<String, ?> values = prefs(context).getAll();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith(KEY_PREFIX)) continue;
            String startKey = key.substring(KEY_PREFIX.length());
            if (parse(startKey) == null) continue;
            Object raw = entry.getValue();
            String endKey = raw instanceof String ? (String) raw : "";
            if (!endKey.isEmpty() && parse(endKey) == null) endKey = "";
            if (!endKey.isEmpty() && endKey.compareTo(startKey) < 0) endKey = startKey;
            ranges.add(new Range(startKey, endKey));
        }
        Collections.sort(ranges, Comparator.comparing(range -> range.startKey));
        return ranges;
    }

    /** 最近一条还没结束的记录。 */
    static Range active(Context context) {
        List<Range> ranges = all(context);
        for (int index = ranges.size() - 1; index >= 0; index--) {
            if (ranges.get(index).ongoing()) return ranges.get(index);
        }
        return null;
    }

    static Range latest(Context context) {
        List<Range> ranges = all(context);
        return ranges.isEmpty() ? null : ranges.get(ranges.size() - 1);
    }

    /** 某天属于哪一段经期（返回开始日期的键），不属于则返回 null。 */
    static String coveringStart(Context context, String dayKey) {
        for (Range range : all(context)) {
            if (range.startKey.compareTo(dayKey) > 0) continue;
            String end = range.effectiveEndKey();
            if (dayKey.compareTo(end) <= 0) return range.startKey;
        }
        return null;
    }

    static boolean covers(Context context, String dayKey) {
        return coveringStart(context, dayKey) != null;
    }

    /**
     * 从某天开始记录。若上一段还没结束，会自动按「新开始的前一天」收尾
     * （最多算 10 天），这样忘记点结束时也不会留下跨月的粉色线。
     */
    static void startOn(Context context, Calendar startDay) {
        Calendar start = atMidnight(startDay);
        String startKey = dayKey(start);
        SharedPreferences.Editor editor = prefs(context).edit();

        Range previous = active(context);
        Calendar previousStart = previous == null ? null : parse(previous.startKey);
        if (previous != null && previousStart != null && previous.startKey.compareTo(startKey) != 0) {
            Calendar closeAt = (Calendar) start.clone();
            closeAt.add(Calendar.DAY_OF_MONTH, -1);
            Calendar cap = (Calendar) previousStart.clone();
            cap.add(Calendar.DAY_OF_MONTH, MAX_DAYS - 1);
            if (closeAt.after(cap)) closeAt = cap;
            if (closeAt.before(previousStart)) closeAt = (Calendar) previousStart.clone();
            editor.putString(KEY_PREFIX + previous.startKey, dayKey(closeAt));
        }

        editor.putString(KEY_PREFIX + startKey, "");
        editor.apply();
    }

    /** 在指定日期结束经期；没有进行中的记录时返回 false。 */
    static boolean endOn(Context context, Calendar endDay) {
        Range active = active(context);
        if (active == null) return false;
        Calendar end = atMidnight(endDay);
        Calendar start = parse(active.startKey);
        if (start != null && end.before(start)) end = (Calendar) start.clone();
        prefs(context).edit().putString(KEY_PREFIX + active.startKey, dayKey(end)).apply();
        return true;
    }

    static void remove(Context context, Range range) {
        if (range == null) return;
        prefs(context).edit().remove(KEY_PREFIX + range.startKey).apply();
    }

    static int days(Context context, Range range) {
        if (range == null) return 0;
        Calendar start = parse(range.startKey);
        Calendar end = parse(range.effectiveEndKey());
        if (start == null || end == null) return 0;
        long millis = end.getTimeInMillis() - start.getTimeInMillis();
        return Math.max(1, Math.round(millis / 86400000f) + 1);
    }

    private static Calendar atMidnight(Calendar value) {
        Calendar day = (Calendar) value.clone();
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
