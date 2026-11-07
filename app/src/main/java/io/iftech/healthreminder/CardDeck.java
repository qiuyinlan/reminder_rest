package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** 卡片集：从 JSON 导入，供「卡片 / 答题」模块的霸屏页使用。 */
final class CardDeck {
    static final String MODE_CARD = "card";
    static final String MODE_QUIZ = "quiz";
    private static final String PREFS = "card_decks";
    private static final String KEY = "decks";

    static final class Card {
        final String id;
        final String question;
        final String answer;
        final String note;

        Card(String id, String question, String answer, String note) {
            this.id = id;
            this.question = question;
            this.answer = answer == null ? "" : answer;
            this.note = note == null ? "" : note;
        }

        JSONObject toJson() {
            JSONObject item = new JSONObject();
            try {
                item.put("id", id);
                item.put("question", question);
                item.put("answer", answer);
                item.put("note", note);
            } catch (JSONException ignored) { }
            return item;
        }
    }

    final String id;
    final String title;
    final String mode;
    final List<Card> cards;

    CardDeck(String id, String title, String mode, List<Card> cards) {
        this.id = id;
        this.title = title;
        this.mode = MODE_QUIZ.equals(mode) ? MODE_QUIZ : MODE_CARD;
        this.cards = cards;
    }

    boolean isQuiz() { return MODE_QUIZ.equals(mode); }
    int size() { return cards.size(); }

    JSONObject toJson() {
        JSONObject item = new JSONObject();
        try {
            item.put("id", id);
            item.put("title", title);
            item.put("mode", mode);
            JSONArray array = new JSONArray();
            for (Card card : cards) array.put(card.toJson());
            item.put("cards", array);
        } catch (JSONException ignored) { }
        return item;
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static List<CardDeck> all(Context context) {
        ArrayList<CardDeck> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                ArrayList<Card> cards = new ArrayList<>();
                JSONArray list = item.optJSONArray("cards");
                if (list != null) {
                    for (int j = 0; j < list.length(); j++) {
                        JSONObject card = list.getJSONObject(j);
                        cards.add(new Card(card.optString("id", "c" + (j + 1)),
                                card.optString("question", ""),
                                card.optString("answer", ""),
                                card.optString("note", "")));
                    }
                }
                if (cards.isEmpty()) continue;
                result.add(new CardDeck(item.optString("id", "deck-" + i),
                        item.optString("title", "未命名卡片集"),
                        item.optString("mode", MODE_CARD), cards));
            }
        } catch (JSONException ignored) { }
        return result;
    }

    static CardDeck byId(Context context, String deckId) {
        for (CardDeck deck : all(context)) {
            if (deck.id.equals(deckId)) return deck;
        }
        return null;
    }

    static void save(Context context, List<CardDeck> decks) {
        JSONArray array = new JSONArray();
        for (CardDeck deck : decks) array.put(deck.toJson());
        prefs(context).edit().putString(KEY, array.toString()).apply();
    }

    static void delete(Context context, String deckId) {
        List<CardDeck> decks = all(context);
        for (int i = decks.size() - 1; i >= 0; i--) {
            if (decks.get(i).id.equals(deckId)) decks.remove(i);
        }
        save(context, decks);
    }

    /** 解析导入的 JSON 文本；成功返回 null，失败返回给用户看的原因。 */
    static String importJson(Context context, String json) {
        CardDeck deck;
        try {
            deck = parse(json);
        } catch (Exception error) {
            return "JSON 解析失败：" + error.getMessage();
        }
        if (deck == null) return "格式不对：需要 {\"title\": ..., \"cards\": [...]}";
        List<CardDeck> decks = all(context);
        decks.add(deck);
        save(context, decks);
        return null;
    }

    static CardDeck parse(String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        String title = root.optString("title", "").trim();
        if (title.isEmpty()) title = "未命名卡片集";
        String mode = root.optString("mode", MODE_CARD);
        JSONArray array = root.optJSONArray("cards");
        if (array == null || array.length() == 0) return null;
        ArrayList<Card> cards = new ArrayList<>();
        int limit = Math.min(array.length(), 2000);
        for (int i = 0; i < limit; i++) {
            JSONObject item = array.getJSONObject(i);
            String question = item.optString("question", "").trim();
            if (question.isEmpty()) continue;
            cards.add(new Card(item.optString("id", "c" + (i + 1)), question,
                    item.optString("answer", ""), item.optString("note", "")));
        }
        if (cards.isEmpty()) return null;
        String id = "deck-" + System.currentTimeMillis();
        return new CardDeck(id, title, mode, cards);
    }
}
