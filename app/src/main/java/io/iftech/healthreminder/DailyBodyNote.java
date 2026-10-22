package io.iftech.healthreminder;

import android.content.Context;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Local-only daily notes, separate from reminder completion statistics. */
final class DailyBodyNote {
    private DailyBodyNote() { }

    static String dayKey(Calendar day) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(day.getTime());
    }

    static String read(Context context, Calendar day) {
        return context.getSharedPreferences("daily_body_notes", Context.MODE_PRIVATE)
                .getString(dayKey(day), "");
    }

    static void save(Context context, Calendar day, String note) {
        android.content.SharedPreferences.Editor editor = context
                .getSharedPreferences("daily_body_notes", Context.MODE_PRIVATE).edit();
        if (note.trim().isEmpty()) editor.remove(dayKey(day));
        else editor.putString(dayKey(day), note);
        editor.apply();
    }
}
