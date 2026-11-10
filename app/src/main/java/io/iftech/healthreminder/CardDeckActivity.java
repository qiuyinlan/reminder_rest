package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 一个卡片集内部的卡片列表：查看、编辑、删除、勾选。 */
public final class CardDeckActivity extends Activity {
    static final String EXTRA_DECK_ID = "deck_id";

    private String deckId;
    private CardDeck deck;
    private LinearLayout list;
    private TextView subtitle;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        deckId = getIntent().getStringExtra(EXTRA_DECK_ID);
        UiPalette.applyWindow(getWindow());
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(UiPalette.page());
        setContentView(root);
        root.addView(buildHeader(), new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(16), dp(4), dp(16), dp(24));
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    @Override protected void onResume() {
        super.onResume();
        reload();
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(12), dp(20), dp(8));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 28, UiPalette.INK, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(Color.WHITE, 14));
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        row.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));

        subtitle = text("", 12, UiPalette.MUTED, false);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(12), 0, dp(8), 0);
        titles.addView(text("卡片集", 20, UiPalette.INK, true));
        titles.addView(subtitle);
        row.addView(titles, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView add = text("＋ 卡片", 13, Color.WHITE, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(13), dp(8), dp(13), dp(8));
        add.setBackground(round(UiPalette.ACCENT, 12));
        add.setOnClickListener(v -> CardEditor.create(this, deck == null ? "" : deck.title, this::reload));
        row.addView(add);
        header.addView(row);
        return header;
    }

    private void reload() {
        deck = CardDeck.byId(this, deckId);
        if (deck == null) {
            finish();
            return;
        }
        int tasks = 0;
        for (CardDeck.Card card : deck.cards) if (card.isTask()) tasks++;
        subtitle.setText(deck.size() + " 张 · " + (deck.isQuiz() ? "答题模式" : "阅读模式")
                + (tasks > 0 ? " · " + tasks + " 个任务" : ""));
        if (list == null) return;
        list.removeAllViews();
        int index = 0;
        for (CardDeck.Card card : deck.cards) {
            index++;
            list.addView(cardRow(deck, card, index), new LinearLayout.LayoutParams(-1, -2));
        }
        if (deck.cards.isEmpty()) {
            TextView empty = text("还没有卡片", 13, UiPalette.MUTED, false);
            empty.setPadding(dp(4), dp(24), dp(4), dp(24));
            list.addView(empty);
        }
    }

    private View cardRow(final CardDeck parent, final CardDeck.Card card, int index) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(10), dp(12));
        row.setBackground(round(Color.WHITE, 16));
        row.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(9);
        row.setLayoutParams(params);

        if (card.isTask()) {
            final long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
            CheckBox check = new CheckBox(this);
            check.setChecked(ReminderRecord.completedOn(this, ReminderSettings.MODULE_TASK,
                    card.question, dayStart));
            check.setButtonTintList(new ColorStateList(
                    new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                    new int[]{UiPalette.ACCENT, UiPalette.DISABLED}));
            check.setContentDescription("标记任务完成");
            check.setOnCheckedChangeListener((button, checked) -> {
                long now = System.currentTimeMillis();
                long start = ReminderRecord.dayStart(now);
                if (checked) {
                    ReminderRecord.finish(this, "card-task-" + card.id + "-" + start, card.question,
                            ReminderRecord.KIND_DONE, now, now, "卡片勾选", ReminderSettings.MODULE_TASK);
                    Toast.makeText(this, "已完成", Toast.LENGTH_SHORT).show();
                } else {
                    ReminderRecord.deleteCompletion(this, ReminderSettings.MODULE_TASK,
                            card.question, start);
                }
            });
            row.addView(check, new LinearLayout.LayoutParams(dp(40), dp(40)));
        }

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.leftMargin = card.isTask() ? dp(4) : dp(6);
        row.addView(texts, textParams);

        TextView question = text(card.question, 15, UiPalette.INK, true);
        question.setMaxLines(2);
        question.setEllipsize(android.text.TextUtils.TruncateAt.END);
        texts.addView(question);

        if (!card.answer.isEmpty()) {
            TextView answer = text("背面：" + card.answer.replace('\n', ' '), 12, UiPalette.MUTED, false);
            answer.setMaxLines(1);
            answer.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams answerParams = new LinearLayout.LayoutParams(-1, -2);
            answerParams.topMargin = dp(3);
            texts.addView(answer, answerParams);
        }
        TextView meta = text("第 " + index + " 张" + (card.isTask() ? " · 任务" : ""), 11,
                UiPalette.MUTED, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(3);
        texts.addView(meta, metaParams);

        TextView edit = text("编辑", 12, UiPalette.ACCENT, true);
        edit.setPadding(dp(10), dp(8), dp(8), dp(8));
        edit.setOnClickListener(v -> CardEditor.edit(this, parent, card, this::reload));
        row.addView(edit);

        row.setOnClickListener(v -> CardEditor.edit(this, parent, card, this::reload));
        row.setOnLongClickListener(v -> {
            confirmDelete(parent, card);
            return true;
        });
        return row;
    }

    private void confirmDelete(final CardDeck parent, final CardDeck.Card card) {
        new AlertDialog.Builder(new ContextThemeWrapper(this, R.style.ItemsDialogTheme))
                .setTitle("删除这张卡片？")
                .setMessage(card.question)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    CardDeck.deleteCard(this, parent.id, card.id);
                    reload();
                })
                .show();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(UiPalette.surface(color));
        shape.setCornerRadius(dp(radius));
        if (color == Color.WHITE) shape.setStroke(dp(1), UiPalette.BORDER);
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
