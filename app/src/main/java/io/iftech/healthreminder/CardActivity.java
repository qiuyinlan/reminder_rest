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
import android.provider.Settings;
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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());
        android.widget.FrameLayout shell = new android.widget.FrameLayout(this);
        shell.setBackgroundColor(UiPalette.PAGE);
        setContentView(shell);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiPalette.PAGE);
        shell.addView(root, new android.widget.FrameLayout.LayoutParams(-1, -1));

        root.addView(buildHeader(), new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), 0, dp(16), dp(96));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        deckList = new LinearLayout(this);
        deckList.setOrientation(LinearLayout.VERTICAL);
        body.addView(deckList);

        ruleList = new LinearLayout(this);
        ruleList.setOrientation(LinearLayout.VERTICAL);
        body.addView(ruleList);

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

        TextView fab = FormKit.fab(this, () -> CardEditor.create(this, null, this::reload));
        android.widget.FrameLayout.LayoutParams fabParams =
                new android.widget.FrameLayout.LayoutParams(dp(56), dp(56));
        fabParams.gravity = Gravity.BOTTOM | Gravity.END;
        fabParams.rightMargin = dp(20);
        fabParams.bottomMargin = dp(86);
        shell.addView(fab, fabParams);
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
        for (CardDeck deck : decks) deckList.addView(deckRow(deck));

        List<ReminderSettings.Rule> rules = ReminderSettings.moduleRules(this, ReminderSettings.MODULE_CARD);
        ruleList.removeAllViews();
        if (rules.isEmpty()) {
            TextView empty = text("还没有安排提醒", 12, UiPalette.MUTED, false);
            empty.setPadding(dp(4), dp(4), dp(4), dp(10));
            ruleList.addView(empty);
        }
        for (ReminderSettings.Rule rule : rules) ruleList.addView(ruleRow(rule, decks));

        answerList.removeAllViews();
        List<QuizLog> answers = QuizLog.all(this);
        if (answers.isEmpty()) {
            TextView empty = text("还没有答题记录", 12, UiPalette.MUTED, false);
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
        texts.addView(text(deck.size() + " 张", 12, UiPalette.MUTED, false));

        TextView plan = small(decksHasRule(deck) ? "编辑提醒" : "设提醒", UiPalette.ACCENT);
        plan.setOnClickListener(v -> scheduleDeckForDeck(deck));
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
        CardDeck target = null;
        for (CardDeck candidate : decks) {
            if (candidate.id.equals(rule.deckId)) {
                target = candidate;
                deckTitle = candidate.title;
            }
        }
        final CardDeck deck = target;
        texts.addView(text(deckTitle, 15, UiPalette.INK, true));

        String scheduleText;
        if (rule.specificTimeMode) {
            StringBuilder times = new StringBuilder();
            for (int seconds : rule.timePointsSeconds) {
                if (times.length() > 0) times.append("、");
                times.append(clockText(seconds));
            }
            scheduleText = "每天 · " + times;
        } else {
            scheduleText = "每天 " + minuteText(rule.start) + "-" + minuteText(rule.end)
                    + " · 每 " + intervalText(rule.intervalSeconds);
        }
        texts.addView(text(scheduleText + (rule.quiz ? " · 答题" : " · 阅读"),
                12, UiPalette.MUTED, false));

        Switch toggle = new Switch(this);
        toggle.setChecked(rule.enabled);
        toggle.setOnCheckedChangeListener((v, checked) ->
                ReminderSettings.upsertRule(this, rule.withEnabled(checked)));
        card.addView(toggle);
        card.setOnClickListener(v -> {
            if (deck != null) scheduleDeck(deck, rule);
        });
        card.setContentDescription("编辑提醒 " + deckTitle);
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

    private boolean decksHasRule(CardDeck deck) {
        for (ReminderSettings.Rule rule : ReminderSettings.moduleRules(this, ReminderSettings.MODULE_CARD)) {
            if (deck.id.equals(rule.deckId)) return true;
        }
        return false;
    }

    private void scheduleDeckForDeck(CardDeck deck) {
        ReminderSettings.Rule existing = null;
        for (ReminderSettings.Rule rule : ReminderSettings.moduleRules(this, ReminderSettings.MODULE_CARD)) {
            if (deck.id.equals(rule.deckId)) {
                existing = rule;
                break;
            }
        }
        scheduleDeck(deck, existing);
    }

    private void scheduleDeck(CardDeck deck, ReminderSettings.Rule existing) {
        final List<Integer> points = new ArrayList<>();
        if (existing != null && !existing.timePointsSeconds.isEmpty()) {
            points.addAll(existing.timePointsSeconds);
        }
        if (points.isEmpty()) points.add(9 * 3600);
        final boolean[] intervalMode = {existing != null && !existing.specificTimeMode};
        final int[] startMinute = {existing != null && existing.start > 0 ? existing.start : 9 * 60};
        final int[] endMinute = {existing != null && existing.end > startMinute[0] ? existing.end : 21 * 60};
        final int[] intervalSeconds = {existing != null && existing.intervalSeconds >= 60
                ? existing.intervalSeconds : 45 * 60};

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(8), dp(20), dp(8));
        TextView intro = text(deck.isQuiz() ? "到点每次弹一道题，你写一句回答。" : "到点展示一张卡片。",
                13, UiPalette.MUTED, false);
        intro.setLineSpacing(dp(3), 1f);
        form.addView(intro);

        LinearLayout modeRow = FormKit.segmentGroup(this);
        final TextView fixedChip = FormKit.segmentOption(this, "固定时间点", !intervalMode[0]);
        final TextView intervalChip = FormKit.segmentOption(this, "每 N 分钟", intervalMode[0]);
        modeRow.addView(fixedChip, new LinearLayout.LayoutParams(0, dp(38), 1));
        modeRow.addView(intervalChip, new LinearLayout.LayoutParams(0, dp(38), 1));
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(-1, -2);
        modeParams.topMargin = dp(12);
        form.addView(modeRow, modeParams);

        // ---------------- 固定时间点
        final LinearLayout fixedSection = new LinearLayout(this);
        fixedSection.setOrientation(LinearLayout.VERTICAL);
        final LinearLayout timeList = new LinearLayout(this);
        timeList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(-1, -2);
        listParams.topMargin = dp(8);
        fixedSection.addView(timeList, listParams);
        form.addView(fixedSection, new LinearLayout.LayoutParams(-1, -2));

        final Runnable renderPoints = new Runnable() {
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
                    TextView value = text(clockText(seconds), 17, UiPalette.INK, true);
                    row.addView(value, new LinearLayout.LayoutParams(0, -2, 1f));
                    row.setOnClickListener(v -> WheelPicker.time(CardActivity.this, "编辑提醒时间",
                            "保存", seconds, true, picked -> {
                                points.set(index, picked);
                                java.util.Collections.sort(points);
                                run();
                            }).show());
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
                add.setOnClickListener(v -> WheelPicker.time(CardActivity.this, "添加提醒时间",
                        "添加", 9 * 3600, true, picked -> {
                            if (!points.contains(picked)) points.add(picked);
                            java.util.Collections.sort(points);
                            run();
                        }).show());
                timeList.addView(add);
            }
        };
        renderPoints.run();

        // ---------------- 每 N 分钟
        final LinearLayout intervalSection = new LinearLayout(this);
        intervalSection.setOrientation(LinearLayout.VERTICAL);
        TextView intervalHint = text("在时间段内每隔 N 分钟弹一张，顺序抽卡会接着昨天继续。",
                12, UiPalette.MUTED, false);
        intervalHint.setLineSpacing(dp(3), 1f);
        intervalHint.setPadding(0, dp(8), 0, 0);
        intervalSection.addView(intervalHint);

        final TextView startValue = text(minuteText(startMinute[0]) + "  ›", 16, UiPalette.INK, true);
        final TextView endValue = text(minuteText(endMinute[0]) + "  ›", 16, UiPalette.INK, true);
        final TextView intervalValue = text("每 " + intervalText(intervalSeconds[0]) + "  ›", 16,
                UiPalette.INK, true);
        startValue.setOnClickListener(v -> WheelPicker.time(this, "开始时间", "确定",
                startMinute[0] * 60, false, picked -> {
                    startMinute[0] = picked / 60;
                    startValue.setText(minuteText(startMinute[0]) + "  ›");
                }).show());
        endValue.setOnClickListener(v -> WheelPicker.time(this, "结束时间", "确定",
                endMinute[0] * 60, false, picked -> {
                    endMinute[0] = picked / 60;
                    endValue.setText(minuteText(endMinute[0]) + "  ›");
                }).show());
        intervalValue.setOnClickListener(v -> chooseInterval(intervalSeconds[0], picked -> {
            intervalSeconds[0] = picked;
            intervalValue.setText("每 " + intervalText(intervalSeconds[0]) + "  ›");
        }));
        intervalSection.addView(fieldRow("开始时间", startValue), topMargin(10));
        intervalSection.addView(fieldRow("结束时间", endValue), topMargin(6));
        intervalSection.addView(fieldRow("提醒间隔", intervalValue), topMargin(6));
        form.addView(intervalSection, new LinearLayout.LayoutParams(-1, -2));

        final Runnable updateModes = () -> {
            fixedSection.setVisibility(intervalMode[0] ? android.view.View.GONE : android.view.View.VISIBLE);
            intervalSection.setVisibility(intervalMode[0] ? android.view.View.VISIBLE : android.view.View.GONE);
            FormKit.setSegmentSelected(fixedChip, !intervalMode[0]);
            FormKit.setSegmentSelected(intervalChip, intervalMode[0]);
        };
        fixedChip.setOnClickListener(v -> { intervalMode[0] = false; updateModes.run(); });
        intervalChip.setOnClickListener(v -> { intervalMode[0] = true; updateModes.run(); });
        updateModes.run();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle((existing == null ? "安排「" : "编辑「") + deck.title + "」")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            ReminderSettings.Rule rule;
            if (intervalMode[0]) {
                int start = startMinute[0];
                int end = endMinute[0];
                if (end <= start) end = Math.min(23 * 60 + 59, start + 60);
                rule = ReminderSettings.Rule.withIntervalSeconds(
                                existing == null ? ReminderSettings.nextRuleId(this) : existing.id,
                                deck.title, start, end, Math.max(60, intervalSeconds[0]), 9999, true, true)
                        .withModule(ReminderSettings.MODULE_CARD)
                        .withDeck(deck.id, deck.isQuiz());
            } else {
                int start = points.get(0) / 60;
                int end = points.get(points.size() - 1) / 60;
                rule = existing == null
                        ? ReminderSettings.Rule.withIntervalSeconds(ReminderSettings.nextRuleId(this),
                        deck.title, start, end, 24 * 3600, 1, true, true)
                        : existing.withEnabled(true);
                rule = rule.withSchedule(true, new ArrayList<>(points), 0, 0L)
                        .withModule(ReminderSettings.MODULE_CARD)
                        .withDeck(deck.id, deck.isQuiz());
            }
            ReminderSettings.upsertRule(this, rule);
            dialog.dismiss();
            reload();
            warnIfCannotPop();
        }));
        FormKit.showStyled(this, dialog);
    }

    /** 没有悬浮窗权限时，到点系统不会让卡片直接弹到屏幕上，提前说清楚。 */
    private void warnIfCannotPop() {
        if (android.os.Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) return;
        new AlertDialog.Builder(this)
                .setTitle("还差一个权限")
                .setMessage("卡片到点要直接弹到屏幕上，需要允许「显示在其他应用上层」。"
                        + "不开的话，时间到了只会发一条通知，很容易看不到。")
                .setNegativeButton("稍后", null)
                .setPositiveButton("去开启", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName())));
                    } catch (Exception error) {
                        Toast.makeText(this, "无法打开悬浮窗设置", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private interface OnInterval { void onPicked(int seconds); }

    private void chooseInterval(int currentSeconds, final OnInterval onPicked) {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(6), dp(18), dp(4));
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setPadding(0, dp(8), 0, dp(4));
        final int[] minutes = {Math.max(1, currentSeconds / 60)};
        final java.util.List<TextView> chips = new ArrayList<>();
        final Runnable refreshChips = () -> {
            for (TextView chip : chips) {
                boolean active = Integer.parseInt(chip.getTag().toString()) == minutes[0];
                chip.setTextColor(active ? Color.WHITE : UiPalette.ACCENT);
                chip.setBackground(FormKit.round(this, active ? UiPalette.ACCENT : UiPalette.SOFT, 10));
            }
        };
        final android.widget.EditText custom = FormKit.input(this,
                android.text.InputType.TYPE_CLASS_NUMBER);
        for (int option : new int[]{5, 10, 15, 30, 45, 60}) {
            TextView chip = text(option + " 分", 13, UiPalette.ACCENT, true);
            chip.setTag(String.valueOf(option));
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, dp(9), 0, dp(9));
            chip.setOnClickListener(v -> {
                minutes[0] = option;
                custom.setText(String.valueOf(option));
                refreshChips.run();
            });
            chips.add(chip);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.leftMargin = option == 5 ? 0 : dp(5);
            quick.addView(chip, params);
        }
        form.addView(quick);
        form.addView(FormKit.editorLabel(this, "自定义分钟数", true));
        custom.setText(String.valueOf(minutes[0]));
        form.addView(custom, new LinearLayout.LayoutParams(-1, -2));
        refreshChips.run();

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("提醒间隔")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int value = minutes[0];
            try {
                value = Integer.parseInt(custom.getText().toString().trim());
            } catch (NumberFormatException ignored) { }
            value = Math.max(1, Math.min(720, value));
            onPicked.onPicked(value * 60);
            dialog.dismiss();
        }));
        FormKit.showStyled(this, dialog);
    }

    private LinearLayout fieldRow(String label, TextView value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(10), 0);
        row.setBackground(FormKit.round(this, Color.WHITE, 10));
        row.addView(text(label, 14, UiPalette.INK, true), new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(value);
        return row;
    }

    private LinearLayout.LayoutParams topMargin(int value) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.topMargin = this.dp(value);
        return params;
    }

    private static String clockText(int seconds) {
        return String.format(Locale.getDefault(), "%02d:%02d:%02d",
                (seconds / 3600) % 24, (seconds / 60) % 60, seconds % 60);
    }

    private static String minuteText(int minuteOfDay) {
        return String.format(Locale.getDefault(), "%02d:%02d",
                (minuteOfDay / 60) % 24, minuteOfDay % 60);
    }

    private static String intervalText(int seconds) {
        int minutes = Math.max(1, seconds / 60);
        return minutes < 60 ? minutes + " 分钟"
                : (minutes % 60 == 0 ? minutes / 60 + " 小时"
                : minutes / 60 + " 小时 " + minutes % 60 + " 分钟");
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
