package io.iftech.healthreminder;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/** 卡片 / 答题的霸屏页：一次一张，答完退出。 */
public final class CardReminderActivity extends Activity {
    private String ruleTitle = "卡片";
    private int ruleId = -1;
    private CardDeck deck;
    private CardDeck.Card card;
    private boolean quiz;
    private long startedAt;
    private String sessionId;
    private EditText answerInput;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        ruleId = getIntent().getIntExtra("rule_id", -1);
        String action = getIntent().getStringExtra("action");
        if (action != null && !action.isEmpty()) ruleTitle = action;
        startedAt = System.currentTimeMillis();
        sessionId = java.util.UUID.randomUUID().toString();

        ReminderSettings.Rule rule = ReminderSettings.ruleById(this, ruleId);
        if (rule != null) {
            ruleTitle = rule.title.isEmpty() ? ruleTitle : rule.title;
            deck = CardDeck.byId(this, rule.deckId);
            quiz = rule.quiz;
        }
        if (deck == null || deck.cards.isEmpty()) {
            Toast.makeText(this, "卡片集不存在或为空", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        card = deck.cards.get(nextIndex(this, ruleId, deck.cards.size()));
        buildPage();
    }

    /** 每次弹下一张，循环播放。 */
    static int nextIndex(Context context, int ruleId, int size) {
        if (size <= 0) return 0;
        String key = "card_index_" + ruleId;
        android.content.SharedPreferences prefs = context.getSharedPreferences("card_progress", Context.MODE_PRIVATE);
        int index = prefs.getInt(key, 0) % size;
        prefs.edit().putInt(key, (index + 1) % size).apply();
        return index;
    }

    private void buildPage() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(24));
        root.setBackgroundColor(UiPalette.PAGE);
        setContentView(root);

        TextView overline = text(quiz ? "来答一题" : deck.title, 13, UiPalette.MUTED, true);
        overline.setGravity(Gravity.CENTER);
        overline.setPadding(dp(14), dp(8), dp(14), dp(8));
        overline.setBackground(round(UiPalette.SOFT, 12));
        root.addView(overline);

        ScrollView scroll = new ScrollView(this);
        LinearLayout cardView = new LinearLayout(this);
        cardView.setOrientation(LinearLayout.VERTICAL);
        cardView.setPadding(dp(22), dp(24), dp(22), dp(24));
        cardView.setBackground(round(Color.WHITE, 20));
        cardView.setElevation(dp(2));
        scroll.addView(cardView, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollParams.topMargin = dp(16);
        root.addView(scroll, scrollParams);

        TextView counter = text(String.format(Locale.getDefault(), "%s · 第 %d/%d 张",
                deck.title, indexOf(card) + 1, deck.cards.size()), 11, UiPalette.MUTED, false);
        cardView.addView(counter);

        TextView question = text(card.question, 20, UiPalette.INK, true);
        question.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(-1, -2);
        qp.topMargin = dp(10);
        cardView.addView(question, qp);

        if (!card.note.isEmpty()) {
            TextView note = text(card.note, 13, UiPalette.MUTED, false);
            note.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
            np.topMargin = dp(10);
            cardView.addView(note, np);
        }

        if (card.isTask()) {
            final long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
            CheckBox done = new CheckBox(this);
            done.setText("完成这项任务");
            done.setTextSize(14);
            done.setTextColor(UiPalette.INK);
            done.setChecked(ReminderRecord.completedOn(this, ReminderSettings.MODULE_TASK,
                    card.question, dayStart));
            done.setButtonTintList(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{UiPalette.ACCENT, UiPalette.DISABLED}));
            LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, -2);
            doneParams.topMargin = dp(14);
            cardView.addView(done, doneParams);
            done.setOnCheckedChangeListener((button, checked) -> {
                long now = System.currentTimeMillis();
                long start = ReminderRecord.dayStart(now);
                if (checked) {
                    ReminderRecord.finish(this, "card-task-" + card.id + "-" + start, card.question,
                            ReminderRecord.KIND_DONE, now, now, "卡片勾选", ReminderSettings.MODULE_TASK);
                } else {
                    ReminderRecord.deleteCompletion(this, ReminderSettings.MODULE_TASK,
                            card.question, start);
                }
            });
        }

        if (quiz) {
            answerInput = new EditText(this);
            answerInput.setHint("用一句话写下你的答案");
            answerInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            answerInput.setMinLines(2);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
            ap.topMargin = dp(16);
            cardView.addView(answerInput, ap);
        } else if (!card.answer.isEmpty()) {
            // 阅读模式：答案在背面，点一下才翻出来。
            final TextView answer = text(card.answer, 14, UiPalette.MUTED, false);
            answer.setLineSpacing(dp(3), 1f);
            answer.setVisibility(View.GONE);
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
            ap.topMargin = dp(14);
            cardView.addView(answer, ap);

            final TextView peek = actionButton("翻面看答案", UiPalette.SOFT, UiPalette.ACCENT);
            LinearLayout.LayoutParams peekParams = new LinearLayout.LayoutParams(-1, dp(44));
            peekParams.topMargin = dp(14);
            cardView.addView(peek, peekParams);
            peek.setOnClickListener(v -> {
                answer.setVisibility(View.VISIBLE);
                peek.setVisibility(View.GONE);
            });
        }

        TextView primary = actionButton(quiz ? "提交" : "看完了", UiPalette.ACCENT, Color.WHITE);
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(-1, dp(50));
        primaryParams.topMargin = dp(20);
        root.addView(primary, primaryParams);
        primary.setOnClickListener(v -> {
            if (quiz) submitQuiz();
            else finishCard(ReminderRecord.KIND_DONE, "");
        });

        TextView skip = actionButton(quiz ? "跳过这题" : "关闭", Color.TRANSPARENT, UiPalette.MUTED);
        LinearLayout.LayoutParams skipParams = new LinearLayout.LayoutParams(-1, dp(46));
        skipParams.topMargin = dp(6);
        root.addView(skip, skipParams);
        skip.setOnClickListener(v -> finishCard(ReminderRecord.KIND_MISSED, "跳过"));
    }

    private void submitQuiz() {
        String answer = answerInput == null ? "" : answerInput.getText().toString().trim();
        if (answer.isEmpty()) {
            answerInput.setError("写一句也行");
            return;
        }
        long seconds = Math.max(1, (System.currentTimeMillis() - startedAt) / 1000);
        QuizLog.add(this, new QuizLog(deck.id, card.id, card.question, answer, System.currentTimeMillis(), seconds));
        ReminderRecord.finish(this, sessionId, card.question, ReminderRecord.KIND_DONE,
                startedAt, System.currentTimeMillis(), answer, ReminderSettings.MODULE_CARD);

        if (card.answer.isEmpty()) {
            finishOnly();
            return;
        }
        // 有参考答案：先给看一眼，再关。
        new android.app.AlertDialog.Builder(this)
                .setTitle("参考答案")
                .setMessage(card.answer)
                .setPositiveButton("知道了", (d, w) -> finishOnly())
                .setCancelable(false)
                .show();
    }

    /** 记录一次结果（阅读模式 / 跳过）并关闭。 */
    private void finishCard(String kind, String reason) {
        ReminderRecord.finish(this, sessionId, card.question, kind,
                startedAt, System.currentTimeMillis(), reason, ReminderSettings.MODULE_CARD);
        finishOnly();
    }

    /** 已经记过记录，只关页并排下一次。 */
    private void finishOnly() {
        ReminderScheduler.scheduleNext(this, ruleId);
        finish();
    }

    private int indexOf(CardDeck.Card target) {
        List<CardDeck.Card> cards = deck.cards;
        for (int i = 0; i < cards.size(); i++) if (cards.get(i) == target) return i;
        return 0;
    }

    private TextView actionButton(String label, int background, int textColor) {
        TextView view = text(label, 16, textColor, true);
        view.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(background);
        bg.setCornerRadius(dp(16));
        view.setBackground(bg);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
