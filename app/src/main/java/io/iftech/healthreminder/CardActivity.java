package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** 卡片集管理：导入 JSON、为卡片集安排提醒、查看答题记录。 */
public final class CardActivity extends Activity {
    private static final int REQUEST_IMPORT = 4101;
    private LinearLayout deckList;
    private LinearLayout ruleList;
    private LinearLayout answerList;
    private TextView deckHint;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiPalette.PAGE);
        setContentView(root);

        root.addView(buildHeader(), new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), 0, dp(16), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        body.addView(sectionTitle("卡片集"));
        deckHint = text("", 12, UiPalette.MUTED, false);
        deckHint.setPadding(dp(4), 0, dp(4), dp(8));
        body.addView(deckHint);
        deckList = new LinearLayout(this);
        deckList.setOrientation(LinearLayout.VERTICAL);
        body.addView(deckList);

        body.addView(sectionTitle("已安排的提醒"));
        ruleList = new LinearLayout(this);
        ruleList.setOrientation(LinearLayout.VERTICAL);
        body.addView(ruleList);

        body.addView(sectionTitle("最近的答题"));
        answerList = new LinearLayout(this);
        answerList.setOrientation(LinearLayout.VERTICAL);
        body.addView(answerList);

        LinearLayout nav = BottomNavigationBar.create(this, BottomNavigationBar.CARDS,
                () -> { startActivity(new Intent(this, MainActivity.class)); overridePendingTransition(0, 0); finish(); },
                () -> { startActivity(new Intent(this, TaskActivity.class)); overridePendingTransition(0, 0); finish(); },
                null,
                () -> { startActivity(new Intent(this, ItemsActivity.class)); overridePendingTransition(0, 0); finish(); },
                () -> { startActivity(new Intent(this, CompanionLandingActivity.class)); overridePendingTransition(0, 0); finish(); },
                () -> { startActivity(new Intent(this, StatsActivity.class)); overridePendingTransition(0, 0); finish(); });
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, dp(64));
        navParams.leftMargin = dp(14);
        navParams.rightMargin = dp(14);
        navParams.bottomMargin = dp(10);
        root.addView(nav, navParams);
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
    }

    private LinearLayout buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(10));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("卡片", 24, UiPalette.INK, true);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView addButton = text("＋ 新建", 14, UiPalette.ACCENT, true);
        addButton.setGravity(Gravity.CENTER);
        addButton.setPadding(dp(13), dp(9), dp(13), dp(9));
        addButton.setBackground(FormKit.round(this, UiPalette.SOFT, 20));
        addButton.setOnClickListener(v -> CardEditor.create(this, null, this::reload));
        row.addView(addButton);

        TextView importButton = text("导入文件", 13, Color.WHITE, true);
        importButton.setGravity(Gravity.CENTER);
        importButton.setPadding(dp(12), dp(9), dp(12), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiPalette.ACCENT);
        bg.setCornerRadius(dp(20));
        importButton.setBackground(bg);
        importButton.setOnClickListener(v -> pickJson());
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(-2, -2);
        importParams.leftMargin = dp(7);
        row.addView(importButton, importParams);
        header.addView(row);
        return header;
    }

    // ------------------------------------------------------------ 导入

    private void pickJson() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_IMPORT);
        } catch (RuntimeException error) {
            Toast.makeText(this, "打不开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        String json;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("无法读取文件");
            byte[] buffer = new byte[8192];
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            int read;
            while ((read = input.read(buffer)) > 0) out.write(buffer, 0, read);
            json = new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception error) {
            Toast.makeText(this, "读取失败：" + error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        String error = CardDeck.importJson(this, json);
        if (error != null) {
            new AlertDialog.Builder(this).setTitle("导入失败").setMessage(error)
                    .setPositiveButton("知道了", null).show();
            return;
        }
        Toast.makeText(this, "导入成功", Toast.LENGTH_SHORT).show();
        reload();
    }

    // ------------------------------------------------------------ 列表

    private void reload() {
        List<CardDeck> decks = CardDeck.all(this);
        deckList.removeAllViews();
        deckHint.setText(decks.isEmpty()
                ? "还没有卡片集。点右上角导入一个 JSON 文件。"
                : "共 " + decks.size() + " 份卡片集");
        for (CardDeck deck : decks) deckList.addView(deckRow(deck));

        List<ReminderSettings.Rule> rules = ReminderSettings.moduleRules(this, ReminderSettings.MODULE_CARD);
        ruleList.removeAllViews();
        if (rules.isEmpty()) {
            TextView empty = text("还没有安排提醒。给某个卡片集点「设提醒」。", 12, UiPalette.MUTED, false);
            empty.setPadding(dp(4), dp(4), dp(4), dp(10));
            ruleList.addView(empty);
        }
        for (ReminderSettings.Rule rule : rules) ruleList.addView(ruleRow(rule, decks));

        answerList.removeAllViews();
        List<QuizLog> answers = QuizLog.all(this);
        if (answers.isEmpty()) {
            TextView empty = text("还没有答题记录。", 12, UiPalette.MUTED, false);
            empty.setPadding(dp(4), dp(4), dp(4), dp(10));
            answerList.addView(empty);
        }
        int shown = 0;
        for (QuizLog entry : answers) {
            if (shown >= 10) break;
            answerList.addView(answerRow(entry));
            shown++;
        }
    }

    private LinearLayout deckRow(CardDeck deck) {
        LinearLayout card = card();
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));
        texts.addView(text(deck.title, 16, UiPalette.INK, true));
        int taskCount = 0;
        for (CardDeck.Card item : deck.cards) if (item.isTask()) taskCount++;
        texts.addView(text(deck.size() + " 张 · " + (deck.isQuiz() ? "答题模式" : "阅读模式")
                + (taskCount > 0 ? " · " + taskCount + " 个任务" : ""),
                12, UiPalette.MUTED, false));

        TextView plan = small("设提醒", UiPalette.ACCENT);
        plan.setOnClickListener(v -> scheduleDeck(deck));
        card.addView(plan);

        TextView delete = small("删除", 0xffc85a4c);
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("删除卡片集")
                .setMessage("删除「" + deck.title + "」？相关提醒会一起删掉。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    CardDeck.delete(this, deck.id);
                    for (ReminderSettings.Rule rule : ReminderSettings.moduleRules(this, ReminderSettings.MODULE_CARD)) {
                        if (deck.id.equals(rule.deckId)) ReminderSettings.deleteRule(this, rule.id);
                    }
                    reload();
                })
                .show());
        card.addView(delete);
        card.setOnClickListener(v -> startActivity(new Intent(this, CardDeckActivity.class)
                .putExtra(CardDeckActivity.EXTRA_DECK_ID, deck.id)));
        card.setContentDescription("查看卡片集 " + deck.title);
        return card;
    }

    private LinearLayout ruleRow(ReminderSettings.Rule rule, List<CardDeck> decks) {
        LinearLayout card = card();
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        card.addView(texts, new LinearLayout.LayoutParams(0, -2, 1f));

        String deckTitle = rule.title;
        for (CardDeck deck : decks) if (deck.id.equals(rule.deckId)) deckTitle = deck.title;
        texts.addView(text(deckTitle, 15, UiPalette.INK, true));

        StringBuilder times = new StringBuilder();
        for (int seconds : rule.timePointsSeconds) {
            if (times.length() > 0) times.append("、");
            times.append(String.format(Locale.getDefault(), "%02d:%02d", (seconds / 3600) % 24, (seconds / 60) % 60));
        }
        texts.addView(text("每天 · " + times + (rule.quiz ? " · 答题" : " · 阅读"), 12, UiPalette.MUTED, false));

        Switch toggle = new Switch(this);
        toggle.setChecked(rule.enabled);
        toggle.setOnCheckedChangeListener((v, checked) ->
                ReminderSettings.upsertRule(this, rule.withEnabled(checked)));
        card.addView(toggle);
        card.setOnLongClickListener(v -> {
            new AlertDialog.Builder(this).setTitle("删除提醒")
                    .setMessage("删掉这条提醒？卡片集本身保留。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除", (d, w) -> {
                        ReminderSettings.deleteRule(this, rule.id);
                        reload();
                    }).show();
            return true;
        });
        return card;
    }

    private LinearLayout answerRow(QuizLog entry) {
        LinearLayout card = card();
        card.setOrientation(LinearLayout.VERTICAL);
        TextView question = text(entry.question, 14, UiPalette.INK, false);
        question.setMaxLines(2);
        card.addView(question);
        if (!entry.answer.isEmpty()) {
            TextView answer = text("我写：" + entry.answer, 13, UiPalette.MUTED, false);
            answer.setMaxLines(2);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
            p.topMargin = dp(4);
            card.addView(answer, p);
        }
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(entry.answeredAt);
        String meta = String.format(Locale.getDefault(), "%d月%d日 %02d:%02d · 用了 %d 秒",
                cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH),
                cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE), entry.seconds);
        TextView metaView = text(meta, 11, UiPalette.MUTED, false);
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(-1, -2);
        mp.topMargin = dp(4);
        card.addView(metaView, mp);
        return card;
    }

    // ------------------------------------------------------------ 设提醒

    private void scheduleDeck(CardDeck deck) {
        final List<Integer> points = new ArrayList<>();
        points.add(9 * 3600);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(8));
        form.addView(text(deck.isQuiz() ? "到点每次弹一道题，你写一句回答。" : "到点展示一张卡片。", 13, UiPalette.MUTED, false));

        LinearLayout timeList = new LinearLayout(this);
        timeList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, -2);
        listParams.topMargin = dp(8);
        form.addView(timeList, listParams);

        final Runnable render = new Runnable() {
            @Override public void run() {
                timeList.removeAllViews();
                for (int i = 0; i < points.size(); i++) {
                    final int index = i;
                    int seconds = points.get(i);
                    LinearLayout row = new LinearLayout(CardActivity.this);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    row.setPadding(dp(12), 0, dp(8), 0);
                    row.setBackground(FormKit.round(CardActivity.this, Color.WHITE, 10));
                    LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(48));
                    rowParams.bottomMargin = dp(6);
                    row.setLayoutParams(rowParams);
                    TextView value = text(String.format(Locale.getDefault(), "%02d:%02d",
                            (seconds / 3600) % 24, (seconds / 60) % 60), 17, UiPalette.INK, true);
                    value.setOnClickListener(v -> new TimePickerDialog(CardActivity.this, (view, hour, minute) -> {
                        points.set(index, hour * 3600 + minute * 60);
                        java.util.Collections.sort(points);
                        run();
                    }, seconds / 3600, (seconds % 3600) / 60, true).show());
                    row.addView(value, new LinearLayout.LayoutParams(0, -2, 1f));
                    TextView remove = text("移除", 12, UiPalette.MUTED, false);
                    remove.setPadding(dp(8), dp(10), 0, dp(10));
                    remove.setOnClickListener(v -> {
                        if (points.size() <= 1) return;
                        points.remove(index);
                        run();
                    });
                    row.addView(remove);
                    timeList.addView(row);
                }
                Button add = FormKit.controlButton(CardActivity.this);
                add.setText("＋ 添加时间点");
                add.setOnClickListener(v -> new TimePickerDialog(CardActivity.this, (view, hour, minute) -> {
                    points.add(hour * 3600 + minute * 60);
                    java.util.Collections.sort(points);
                    run();
                }, 9, 0, true).show());
                timeList.addView(add);
            }
        };
        render.run();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("安排「" + deck.title + "」")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int id = ReminderSettings.nextRuleId(this);
            int startMinute = points.get(0) / 60;
            int endMinute = points.get(points.size() - 1) / 60;
            ReminderSettings.Rule rule = ReminderSettings.Rule
                    .withIntervalSeconds(id, deck.title, startMinute, endMinute, 24 * 3600, 1, true, true)
                    .withSchedule(true, new ArrayList<>(points), 0, 0L)
                    .withModule(ReminderSettings.MODULE_CARD)
                    .withDeck(deck.id, deck.isQuiz());
            ReminderSettings.upsertRule(this, rule);
            dialog.dismiss();
            reload();
        }));
        FormKit.showStyled(this, dialog);
    }

    // ------------------------------------------------------------ 小工具

    private TextView sectionTitle(String label) {
        TextView view = text(label, 13, UiPalette.MUTED, true);
        view.setPadding(dp(4), dp(18), dp(4), dp(8));
        return view;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(12), dp(12));
        card.setBackground(FormKit.round(this, Color.WHITE, 16));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);
        return card;
    }

    private TextView small(String label, int color) {
        TextView view = text(label, 13, color, true);
        view.setPadding(dp(10), dp(6), dp(10), dp(6));
        return view;
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
