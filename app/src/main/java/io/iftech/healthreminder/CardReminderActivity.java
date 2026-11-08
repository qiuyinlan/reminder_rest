package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
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
import android.widget.NumberPicker;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/**
 * 卡片 / 答题的霸屏页：一次一张，可以前后翻、换卡片集，看完或稍后再提醒。
 */
public final class CardReminderActivity extends Activity {
    private String ruleTitle = "卡片";
    private int ruleId = -1;
    private CardDeck deck;
    private CardDeck.Card card;
    private int index;
    private boolean quiz;
    private long startedAt;
    private String sessionId;
    private EditText answerInput;
    private LinearLayout contentRoot;

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
        index = nextIndex(this, deck.id, deck.cards.size());
        card = deck.cards.get(index);
        buildPage();
    }

    /** 每次弹下一张，按卡片集共享进度，跨天接着昨天的位置继续。 */
    static int nextIndex(Context context, String deckId, int size) {
        if (size <= 0) return 0;
        String key = "card_index_deck_" + deckId;
        android.content.SharedPreferences prefs = context.getSharedPreferences("card_progress", Context.MODE_PRIVATE);
        int index = prefs.getInt(key, 0) % size;
        prefs.edit().putInt(key, (index + 1) % size).apply();
        return index;
    }

    /** 记下「下次从第几张开始」。 */
    static void setNextIndex(Context context, String deckId, int next) {
        context.getSharedPreferences("card_progress", Context.MODE_PRIVATE)
                .edit().putInt("card_index_deck_" + deckId, Math.max(0, next)).apply();
    }

    private void buildPage() {
        if (deck == null || deck.cards.isEmpty()) {
            finish();
            return;
        }
        if (index < 0) index = 0;
        if (index >= deck.cards.size()) index = deck.cards.size() - 1;
        card = deck.cards.get(index);
        answerInput = null;

        contentRoot = new LinearLayout(this);
        contentRoot.setOrientation(LinearLayout.VERTICAL);
        contentRoot.setGravity(Gravity.CENTER_HORIZONTAL);
        contentRoot.setPadding(dp(20), dp(22), dp(20), dp(18));
        contentRoot.setBackgroundColor(UiPalette.PAGE);
        setContentView(contentRoot);

        contentRoot.addView(buildDeckBar(), new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        LinearLayout cardView = new LinearLayout(this);
        cardView.setOrientation(LinearLayout.VERTICAL);
        cardView.setPadding(dp(20), dp(20), dp(20), dp(20));
        cardView.setBackground(round(Color.WHITE, 20));
        cardView.setElevation(dp(2));
        scroll.addView(cardView, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollParams.topMargin = dp(12);
        contentRoot.addView(scroll, scrollParams);

        TextView counter = text(String.format(Locale.getDefault(), "第 %d/%d 张", index + 1, deck.cards.size()),
                11, UiPalette.MUTED, false);
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
            answerInput.setHintTextColor(UiPalette.MUTED);
            answerInput.setTextColor(UiPalette.INK);
            answerInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            answerInput.setMinLines(2);
            answerInput.setPadding(dp(12), dp(10), dp(12), dp(10));
            answerInput.setBackground(UiPalette.inputBackground(this, UiPalette.PALE, 10));
            LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
            ap.topMargin = dp(16);
            cardView.addView(answerInput, ap);
        } else if (!card.answer.isEmpty()) {
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

        contentRoot.addView(buildPager(), new LinearLayout.LayoutParams(-1, -2));
        contentRoot.addView(buildActions(), new LinearLayout.LayoutParams(-1, -2));
    }

    /** 顶部：当前卡片集 + 切换。 */
    private View buildDeckBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = text(deck.title, 13, UiPalette.MUTED, true);
        name.setMaxLines(1);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        bar.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        List<CardDeck> decks = CardDeck.all(this);
        TextView swap = text("切换卡片集 ›", 12, UiPalette.ACCENT, true);
        swap.setPadding(dp(8), dp(6), dp(2), dp(6));
        bar.addView(swap);
        swap.setOnClickListener(v -> {
            if (decks.size() <= 1) {
                Toast.makeText(this, "只有一个卡片集", Toast.LENGTH_SHORT).show();
                return;
            }
            String[] labels = new String[decks.size()];
            for (int i = 0; i < decks.size(); i++) labels[i] = decks.get(i).title;
            FormKit.options(this, "切换卡片集", labels, -1, which -> {
                CardDeck picked = decks.get(which);
                if (picked.cards.isEmpty()) {
                    Toast.makeText(this, "这个卡片集是空的", Toast.LENGTH_SHORT).show();
                    return;
                }
                deck = picked;
                index = nextIndex(this, deck.id, deck.cards.size());
                buildPage();
            });
        });
        return bar;
    }

    /** 上一张 / 下一张。 */
    private View buildPager() {
        LinearLayout pager = new LinearLayout(this);
        pager.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams pagerParams = new LinearLayout.LayoutParams(-1, -2);
        pagerParams.topMargin = dp(12);
        pager.setLayoutParams(pagerParams);

        TextView prev = actionButton("‹ 上一张", Color.WHITE, UiPalette.INK);
        LinearLayout.LayoutParams prevParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        prevParams.rightMargin = dp(6);
        pager.addView(prev, prevParams);
        prev.setOnClickListener(v -> {
            index = (index - 1 + deck.cards.size()) % deck.cards.size();
            buildPage();
        });

        TextView next = actionButton("下一张 ›", Color.WHITE, UiPalette.INK);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        nextParams.leftMargin = dp(6);
        pager.addView(next, nextParams);
        next.setOnClickListener(v -> {
            index = (index + 1) % deck.cards.size();
            buildPage();
        });
        return pager;
    }

    /** 底部：学完了 / 关闭 / 关闭并稍后提醒。 */
    private View buildActions() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.topMargin = dp(12);
        actions.setLayoutParams(actionsParams);

        TextView primary = actionButton(quiz ? "提交" : "学完了", UiPalette.ACCENT, Color.WHITE);
        LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(-1, dp(50));
        primaryParams.topMargin = dp(6);
        actions.addView(primary, primaryParams);
        primary.setOnClickListener(v -> {
            if (quiz) submitQuiz();
            else finishCard(ReminderRecord.KIND_DONE, "");
        });

        LinearLayout secondary = new LinearLayout(this);
        secondary.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams secondaryParams = new LinearLayout.LayoutParams(-1, -2);
        secondaryParams.topMargin = dp(8);
        actions.addView(secondary, secondaryParams);

        TextView close = text("关闭", 13, UiPalette.MUTED, true);
        close.setGravity(Gravity.CENTER);
        close.setPadding(0, dp(12), 0, dp(12));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(0, -2, 1f);
        closeParams.rightMargin = dp(6);
        secondary.addView(close, closeParams);
        close.setOnClickListener(v -> finishOnly());

        TextView later = text("关闭并稍后提醒", 13, UiPalette.ACCENT, true);
        later.setGravity(Gravity.CENTER);
        later.setPadding(0, dp(12), 0, dp(12));
        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(0, -2, 1f);
        laterParams.leftMargin = dp(6);
        secondary.addView(later, laterParams);
        later.setOnClickListener(v -> chooseSnooze());
        return actions;
    }

    /** 竖着滚轮选「多久之后再提醒」。 */
    private void chooseSnooze() {
        final NumberPicker hours = WheelPicker.wheel(this, 0, 5, 0, false);
        final NumberPicker minutes = WheelPicker.wheel(this, 0, 59, 30, true);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(6), dp(10), dp(6), dp(10));
        card.setBackground(round(UiPalette.PALE, 16));
        card.addView(column(hours, "小时"));
        card.addView(column(minutes, "分钟"));

        final TextView preview = text("", 20, UiPalette.INK, true);
        preview.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2);
        previewParams.topMargin = dp(4);
        previewParams.bottomMargin = dp(10);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(8), dp(16), dp(4));
        content.addView(preview, previewParams);
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));

        final Runnable refresh = () -> {
            int total = hours.getValue() * 60 + minutes.getValue();
            preview.setText(total <= 0 ? "选个时间" : total + " 分钟后再提醒");
        };
        NumberPicker.OnValueChangeListener changed = (picker, oldValue, newValue) -> refresh.run();
        hours.setOnValueChangedListener(changed);
        minutes.setOnValueChangedListener(changed);
        refresh.run();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("多久之后再提醒")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            WheelPicker.styleDialog(this, dialog);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int total = hours.getValue() * 60 + minutes.getValue();
                if (total <= 0) {
                    Toast.makeText(this, "至少 1 分钟", Toast.LENGTH_SHORT).show();
                    return;
                }
                ReminderScheduler.scheduleCustomSnooze(this, total, deck.title, ruleId);
                Toast.makeText(this, total + " 分钟后再提醒", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                finish();
            });
        });
        dialog.show();
    }

    private LinearLayout column(NumberPicker picker, String unit) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.addView(picker, new LinearLayout.LayoutParams(-2, dp(140)));
        TextView label = text(unit, 11, UiPalette.MUTED, false);
        label.setGravity(Gravity.CENTER);
        column.addView(label);
        return column;
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
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("参考答案")
                .setMessage(card.answer)
                .setPositiveButton("知道了", (d, w) -> finishOnly())
                .setCancelable(false)
                .create();
        FormKit.showStyled(this, dialog);
    }

    /** 记录一次结果（阅读模式 / 跳过）并关闭。 */
    private void finishCard(String kind, String reason) {
        ReminderRecord.finish(this, sessionId, card.question, kind,
                startedAt, System.currentTimeMillis(), reason, ReminderSettings.MODULE_CARD);
        finishOnly();
    }

    /** 只关页并排下一次。 */
    private void finishOnly() {
        if (deck != null && !deck.cards.isEmpty()) {
            setNextIndex(this, deck.id, index + 1);
        }
        ReminderScheduler.scheduleNext(this, ruleId);
        finish();
    }

    private TextView actionButton(String label, int background, int textColor) {
        TextView view = text(label, 16, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(round(background, 16));
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

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
