package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 答题记录：什么时候弹的、答了什么、花了多久。不判对错。 */
final class QuizLog {
    private static final String PREFS = "quiz_answers";
    private static final String KEY = "answers";
    private static final int MAX = 2000;

    final String deckId;
    final String cardId;
    final String question;
    final String answer;
    final long answeredAt;
    final long seconds;

    QuizLog(String deckId, String cardId, String question, String answer, long answeredAt, long seconds) {
        this.deckId = deckId == null ? "" : deckId;
        this.cardId = cardId == null ? "" : cardId;
        this.question = question == null ? "" : question;
        this.answer = answer == null ? "" : answer;
        this.answeredAt = answeredAt;
        this.seconds = seconds;
    }

    JSONObject toJson() {
        JSONObject item = new JSONObject();
        try {
            item.put("deckId", deckId);
            item.put("cardId", cardId);
            item.put("question", question);
            item.put("answer", answer);
            item.put("answeredAt", answeredAt);
            item.put("seconds", seconds);
        } catch (JSONException ignored) { }
        return item;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void add(Context context, QuizLog entry) {
        JSONArray array = raw(context);
        array.put(entry.toJson());
        while (array.length() > MAX) array.remove(0);
        prefs(context).edit().putString(KEY, array.toString()).apply();
    }

    private static JSONArray raw(Context context) {
        try {
            return new JSONArray(prefs(context).getString(KEY, "[]"));
        } catch (JSONException ignored) {
            return new JSONArray();
        }
    }

    /** 按时间倒序返回。 */
    static List<QuizLog> all(Context context) {
        ArrayList<QuizLog> result = new ArrayList<>();
        JSONArray array = raw(context);
        for (int i = array.length() - 1; i >= 0; i--) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            result.add(new QuizLog(item.optString("deckId", ""), item.optString("cardId", ""),
                    item.optString("question", ""), item.optString("answer", ""),
                    item.optLong("answeredAt", 0L), item.optLong("seconds", 0L)));
        }
        return result;
    }

    static int countOn(Context context, long dayStart) {
        long dayEnd = dayStart + 24 * 60 * 60 * 1000L;
        int count = 0;
        for (QuizLog entry : all(context)) {
            if (entry.answeredAt >= dayStart && entry.answeredAt < dayEnd) count++;
        }
        return count;
    }

    static void clearAll(Context context) {
        prefs(context).edit().remove(KEY).apply();
    }
}
