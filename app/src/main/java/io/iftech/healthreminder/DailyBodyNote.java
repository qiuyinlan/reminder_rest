package io.iftech.healthreminder;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Local-only daily notes, separate from reminder completion statistics. */
final class DailyBodyNote {
    private static final String PREFS = "daily_body_notes";

    private DailyBodyNote() { }

    static String dayKey(Calendar day) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(day.getTime());
    }

    static String read(Context context, Calendar day) {
        return prefs(context).getString(dayKey(day), "");
    }

    static void save(Context context, Calendar day, String note) {
        android.content.SharedPreferences.Editor editor = prefs(context).edit();
        if (note.trim().isEmpty()) editor.remove(dayKey(day));
        else editor.putString(dayKey(day), note);
        editor.apply();
    }

    /** 所有非空记录，按日期升序，供导出使用。 */
    static List<Map.Entry<String, String>> all(Context context) {
        List<Map.Entry<String, String>> entries = new ArrayList<>();
        for (Map.Entry<String, ?> entry : prefs(context).getAll().entrySet()) {
            String key = entry.getKey();
            if (key == null || key.length() != 10) continue;
            Object raw = entry.getValue();
            if (!(raw instanceof String)) continue;
            String note = (String) raw;
            if (note.trim().isEmpty()) continue;
            entries.add(new AbstractMap.SimpleEntry<>(key, note));
        }
        Collections.sort(entries, (left, right) -> left.getKey().compareTo(right.getKey()));
        return entries;
    }

    private static android.content.SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
