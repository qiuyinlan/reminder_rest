package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 任务清单：增删改、一键清空、重置今日，到点由 ReminderReceiver 弹霸屏页。
 * 全部复用 ReminderSettings.Rule 的调度能力，只是 module = task。
 */
public final class TaskActivity extends Activity {
    private static final String MODE_DAILY = "daily";
    private static final String MODE_WEEKLY = "weekly";
    private static final String MODE_ONCE = "once";
    private static final String MODE_INTERVAL = "interval";

    private LinearLayout listContainer;
    private TextView subtitle;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiPalette.PAGE);
        setContentView(root);

        root.addView(buildHeader(), new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(16), dp(4), dp(16), dp(16));
        scroll.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(buildFooter(), new LinearLayout.LayoutParams(-1, -2));

        LinearLayout nav = BottomNavigationBar.create(this, BottomNavigationBar.TASKS,
                () -> { startActivity(new Intent(this, MainActivity.class)); overridePendingTransition(0, 0); finish(); },
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

    // ---------------------------------------------------------------- 顶部

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(8));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("任务清单", 24, UiPalette.INK, true);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView cards = text("卡片", 14, UiPalette.ACCENT, true);
        cards.setGravity(Gravity.CENTER);
        cards.setPadding(dp(12), dp(9), dp(12), dp(9));
        cards.setOnClickListener(v -> startActivity(new Intent(this, CardActivity.class)));
        row.addView(cards);

        TextView add = text("＋ 新任务", 14, Color.WHITE, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(14), dp(9), dp(14), dp(9));
        GradientDrawable addBg = new GradientDrawable();
        addBg.setColor(UiPalette.ACCENT);
        addBg.setCornerRadius(dp(20));
        add.setBackground(addBg);
        add.setOnClickListener(v -> showEditor(null));
        row.addView(add);
        header.addView(row);

        subtitle = text("", 13, UiPalette.MUTED, false);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(6);
        header.addView(subtitle, subParams);
        return header;
    }

    private View buildFooter() {
        LinearLayout footer = new LinearLayout(this);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setPadding(dp(16), dp(4), dp(16), dp(4));

        TextView reset = text("重置今日", 13, UiPalette.MUTED, true);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(0, dp(10), 0, dp(10));
        reset.setBackground(outline(UiPalette.hairline()));
        reset.setOnClickListener(v -> confirmResetToday());
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -2, 1f);
        a.rightMargin = dp(6);
        footer.addView(reset, a);

        TextView clear = text("清空全部任务", 13, 0xffc85a4c, true);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(0, dp(10), 0, dp(10));
        clear.setBackground(outline(0x33c85a4c));
        clear.setOnClickListener(v -> confirmClearAll());
        LinearLayout.LayoutParams b = new LinearLayout.LayoutParams(0, -2, 1f);
        b.leftMargin = dp(6);
        footer.addView(clear, b);
        return footer;
    }

    // ---------------------------------------------------------------- 列表

    private void reload() {
        List<ReminderSettings.Rule> tasks = ReminderSettings.moduleRules(this, ReminderSettings.MODULE_TASK);
        listContainer.removeAllViews();
        long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
        int done = 0;
        for (ReminderSettings.Rule task : tasks) {
            if (ReminderRecord.completedOn(this, ReminderSettings.MODULE_TASK, task.title, dayStart)) done++;
        }
        subtitle.setText(tasks.isEmpty() ? "还没有任务，点右上角加一个"
                : String.format(Locale.getDefault(), "共 %d 个任务 · 今天完成 %d 个", tasks.size(), done));
        if (tasks.isEmpty()) {
            TextView empty = text("任务到点会全屏弹出来提醒你。\n先加一个试试，比如「背 20 个单词」。", 14, UiPalette.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(40), dp(20), dp(40));
            listContainer.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (ReminderSettings.Rule task : tasks) {
            listContainer.addView(buildRow(task, dayStart));
        }
    }

    private View buildRow(ReminderSettings.Rule task, long dayStart) {
        boolean doneToday = ReminderRecord.completedOn(this, ReminderSettings.MODULE_TASK, task.title, dayStart);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(14), dp(12), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(16));
        card.setBackground(bg);
        card.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);

        TextView badge = text(doneToday ? "✓" : task.badgeText(), 16,
                doneToday ? Color.WHITE : UiPalette.ACCENT, true);
        badge.setGravity(Gravity.CENTER);
        GradientDrawable badgeBg = new GradientDrawable();
        badgeBg.setColor(doneToday ? UiPalette.ACCENT : UiPalette.SOFT);
        badgeBg.setCornerRadius(dp(12));
        badge.setBackground(badgeBg);
        card.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.leftMargin = dp(12);
        card.addView(texts, textParams);

        TextView title = text(task.title.isEmpty() ? "未命名任务" : task.title, 16,
                doneToday ? UiPalette.MUTED : UiPalette.INK, true);
        texts.addView(title);
        TextView schedule = text(describe(task), 12, UiPalette.MUTED, false);
        LinearLayout.LayoutParams scheduleParams = new LinearLayout.LayoutParams(-1, -2);
        scheduleParams.topMargin = dp(3);
        texts.addView(schedule, scheduleParams);
        if (!task.reminderMessage.isEmpty()) {
            TextView note = text(task.reminderMessage, 12, UiPalette.MUTED, false);
            note.setMaxLines(1);
            note.setEllipsize(android.text.TextUtils.TruncateAt.END);
            texts.addView(note);
        }

        Switch toggle = new Switch(this);
        toggle.setChecked(task.enabled);
        toggle.setPadding(0, 0, 0, 0);
        toggle.setOnCheckedChangeListener((v, checked) ->
                ReminderSettings.upsertRule(this, task.withEnabled(checked)));
        card.addView(toggle, new LinearLayout.LayoutParams(-2, -2));

        card.setOnClickListener(v -> showEditor(task));
        card.setOnLongClickListener(v -> {
            confirmDelete(task);
            return true;
        });
        return card;
    }

    private String describe(ReminderSettings.Rule rule) {
        if (rule.isOnceSchedule()) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(rule.onceAt);
            return String.format(Locale.getDefault(), "单次 · %d月%d日 %s",
                    cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH), timeText(rule.onceAt / 1000));
        }
        StringBuilder times = new StringBuilder();
        for (int seconds : rule.timePointsSeconds) {
            if (times.length() > 0) times.append("、");
            times.append(timeText(seconds));
        }
        if (rule.isWeeklySchedule()) {
            StringBuilder days = new StringBuilder();
            String[] names = {"一", "二", "三", "四", "五", "六", "日"};
            for (int i = 0; i < 7; i++) {
                int bit = 1 << i;
                if ((rule.daysMask & bit) != 0) days.append(names[i]);
            }
            return "每周 " + days + " · " + times;
        }
        if (rule.specificTimeMode) return "每天 · " + times;
        return String.format(Locale.getDefault(), "每天 %s-%s · 每 %d 分钟",
                minuteText(rule.start), minuteText(rule.end), Math.max(1, rule.intervalSeconds / 60));
    }

    private static String timeText(long secondsOfDay) {
        return String.format(Locale.getDefault(), "%02d:%02d", (secondsOfDay / 3600) % 24, (secondsOfDay / 60) % 60);
    }

    private static String minuteText(int minuteOfDay) {
        return String.format(Locale.getDefault(), "%02d:%02d", (minuteOfDay / 60) % 24, minuteOfDay % 60);
    }

    // ---------------------------------------------------------------- 删除 / 清空

    private void confirmDelete(ReminderSettings.Rule rule) {
        new AlertDialog.Builder(this)
                .setTitle("删除任务")
                .setMessage("确定删除「" + rule.title + "」？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    ReminderSettings.deleteRule(this, rule.id);
                    reload();
                })
                .show();
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle("清空全部任务")
                .setMessage("会删除所有任务和它们的提醒，历史记录保留。确定吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> {
                    ReminderSettings.clearModule(this, ReminderSettings.MODULE_TASK);
                    reload();
                })
                .show();
    }

    private void confirmResetToday() {
        new AlertDialog.Builder(this)
                .setTitle("重置今日")
                .setMessage("把今天所有任务的完成状态清掉，重新开始打卡。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重置", (d, w) -> {
                    ReminderRecord.deleteForDay(this, ReminderSettings.MODULE_TASK,
                            ReminderRecord.dayStart(System.currentTimeMillis()));
                    reload();
                })
                .show();
    }

    // ---------------------------------------------------------------- 编辑

    private void showEditor(ReminderSettings.Rule existing) {
        final boolean creating = existing == null;
        final int id = creating ? ReminderSettings.nextRuleId(this) : existing.id;

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        form.setPadding(pad, dp(8), pad, dp(8));

        EditText titleInput = new EditText(this);
        titleInput.setHint("任务名，例如「背 20 个单词」");
        titleInput.setInputType(InputType.TYPE_CLASS_TEXT);
        if (!creating) titleInput.setText(existing.title);
        form.addView(titleInput);

        EditText noteInput = new EditText(this);
        noteInput.setHint("备注（可选，会显示在霸屏页上）");
        noteInput.setInputType(InputType.TYPE_CLASS_TEXT);
        if (!creating) noteInput.setText(existing.reminderMessage);
        form.addView(noteInput);

        form.addView(sectionLabel("什么时候提醒"));

        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        final String[] mode = {creating ? MODE_DAILY : modeOf(existing)};
        final TextView[] chips = new TextView[4];
        String[] labels = {"每天", "每周", "单次", "间隔"};
        String[] values = {MODE_DAILY, MODE_WEEKLY, MODE_ONCE, MODE_INTERVAL};
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = chip(labels[i], mode[0].equals(values[i]));
            chip.setOnClickListener(v -> {
                mode[0] = values[index];
                for (int j = 0; j < chips.length; j++) styleChip(chips[j], values[j].equals(mode[0]));
            });
            chips[i] = chip;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) p.leftMargin = dp(6);
            modeRow.addView(chip, p);
        }
        form.addView(modeRow);

        // 时间点（每天 / 每周共用）
        final List<Integer> points = new ArrayList<>();
        if (!creating && !existing.timePointsSeconds.isEmpty()) points.addAll(existing.timePointsSeconds);
        if (points.isEmpty()) points.add(9 * 3600);

        final LinearLayout pointList = new LinearLayout(this);
        pointList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams pointListParams = new LinearLayout.LayoutParams(-1, -2);
        pointListParams.topMargin = dp(8);
        form.addView(pointList, pointListParams);

        final Runnable renderPoints = new Runnable() {
            @Override public void run() {
                pointList.removeAllViews();
                for (int i = 0; i < points.size(); i++) {
                    final int index = i;
                    final int seconds = points.get(i);
                    LinearLayout row = new LinearLayout(TaskActivity.this);
                    row.setOrientation(LinearLayout.HORIZONTAL);
                    row.setGravity(Gravity.CENTER_VERTICAL);
                    TextView value = text("🕘 " + timeText(seconds), 15, UiPalette.INK, false);
                    value.setPadding(0, dp(10), 0, dp(10));
                    value.setOnClickListener(v -> pickTime(seconds, picked -> {
                        points.set(index, picked);
                        run();
                    }));
                    row.addView(value, new LinearLayout.LayoutParams(0, -2, 1f));
                    TextView remove = text("移除", 12, UiPalette.MUTED, false);
                    remove.setPadding(dp(8), dp(10), 0, dp(10));
                    remove.setOnClickListener(v -> {
                        if (points.size() <= 1) return;
                        points.remove(index);
                        run();
                    });
                    row.addView(remove);
                    pointList.addView(row);
                }
                TextView addPoint = text("＋ 添加时间", 13, UiPalette.ACCENT, true);
                addPoint.setPadding(0, dp(8), 0, dp(8));
                addPoint.setOnClickListener(v -> pickTime(9 * 3600, picked -> {
                    points.add(picked);
                    java.util.Collections.sort(points);
                    run();
                }));
                pointList.addView(addPoint);
            }
        };
        renderPoints.run();

        // 每周：星期选择
        final int[] daysMask = {creating ? 0x7f : existing.daysMask};
        final LinearLayout weekRow = new LinearLayout(this);
        weekRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] dayNames = {"一", "二", "三", "四", "五", "六", "日"};
        final TextView[] dayChips = new TextView[7];
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            TextView chip = chip(dayNames[i], (daysMask[0] & bit) != 0);
            chip.setOnClickListener(v -> {
                daysMask[0] ^= bit;
                styleChip(chip, (daysMask[0] & bit) != 0);
            });
            dayChips[i] = chip;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) p.leftMargin = dp(4);
            weekRow.addView(chip, p);
        }
        LinearLayout.LayoutParams weekParams = new LinearLayout.LayoutParams(-1, -2);
        weekParams.topMargin = dp(8);
        form.addView(weekRow, weekParams);

        // 单次：日期
        final long[] onceAt = {creating ? System.currentTimeMillis() + 3600_000L : existing.onceAt};
        final TextView onceDate = chip("选择日期", false);
        onceDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(onceAt[0]);
            new DatePickerDialog(this, (view, year, month, day) -> {
                Calendar picked = Calendar.getInstance();
                picked.setTimeInMillis(onceAt[0]);
                picked.set(year, month, day);
                onceAt[0] = picked.getTimeInMillis();
                onceDate.setText(String.format(Locale.getDefault(), "日期：%d月%d日", month + 1, day));
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
        });
        LinearLayout.LayoutParams onceParams = new LinearLayout.LayoutParams(-1, -2);
        onceParams.topMargin = dp(8);
        form.addView(onceDate, onceParams);

        // 间隔：开始 / 结束 / 分钟
        final int[] window = {
                creating ? 9 * 60 : existing.start,
                creating ? 18 * 60 : existing.end};
        final int[] intervalMinutes = {creating ? 60 : Math.max(1, existing.intervalSeconds / 60)};
        LinearLayout intervalRow = new LinearLayout(this);
        intervalRow.setOrientation(LinearLayout.VERTICAL);
        final TextView windowText = text("", 14, UiPalette.INK, false);
        windowText.setPadding(0, dp(8), 0, dp(4));
        windowText.setOnClickListener(v -> new TimePickerDialog(this, (view, hour, minute) -> {
            window[0] = hour * 60 + minute;
            windowText.setText(String.format(Locale.getDefault(), "从 %s 开始", minuteText(window[0])));
        }, window[0] / 60, window[0] % 60, true).show());
        intervalRow.addView(windowText);
        final TextView endText = text("", 14, UiPalette.INK, false);
        endText.setPadding(0, dp(8), 0, dp(4));
        endText.setOnClickListener(v -> new TimePickerDialog(this, (view, hour, minute) -> {
            window[1] = hour * 60 + minute;
            endText.setText(String.format(Locale.getDefault(), "到 %s 结束", minuteText(window[1])));
        }, window[1] / 60, window[1] % 60, true).show());
        intervalRow.addView(endText);
        LinearLayout.LayoutParams intervalParams = new LinearLayout.LayoutParams(-1, -2);
        intervalParams.topMargin = dp(4);
        form.addView(intervalRow, intervalParams);
        windowText.setText(String.format(Locale.getDefault(), "从 %s 开始", minuteText(window[0])));
        endText.setText(String.format(Locale.getDefault(), "到 %s 结束", minuteText(window[1])));

        // 每日重置
        LinearLayout resetRow = new LinearLayout(this);
        resetRow.setOrientation(LinearLayout.HORIZONTAL);
        resetRow.setGravity(Gravity.CENTER_VERTICAL);
        resetRow.setPadding(0, dp(10), 0, 0);
        TextView resetLabel = text("每天自动把完成状态重置", 14, UiPalette.INK, false);
        resetRow.addView(resetLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch resetSwitch = new Switch(this);
        resetSwitch.setChecked(!creating && ReminderSettings.autoReset(this, existing.id));
        resetRow.addView(resetSwitch);
        form.addView(resetRow);

        ScrollView formScroll = new ScrollView(this);
        formScroll.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(creating ? "新建任务" : "编辑任务")
                .setView(formScroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = titleInput.getText().toString().trim();
            if (title.isEmpty()) {
                titleInput.setError("任务名不能为空");
                return;
            }
            ReminderSettings.Rule rule = buildRule(id, existing, title,
                    noteInput.getText().toString().trim(), mode[0], points, daysMask[0], onceAt[0], window, intervalMinutes[0]);
            ReminderSettings.upsertRule(this, rule);
            ReminderSettings.setAutoReset(this, id, resetSwitch.isChecked());
            dialog.dismiss();
            reload();
        }));
        dialog.show();
    }

    private ReminderSettings.Rule buildRule(int id, ReminderSettings.Rule existing, String title, String note,
                                            String mode, List<Integer> points, int daysMask, long onceAt,
                                            int[] window, int intervalMinutes) {
        int startMinute = window[0];
        int endMinute = window[1];
        int intervalSeconds = Math.max(1, intervalMinutes) * 60;
        boolean specificTimes = !MODE_INTERVAL.equals(mode);
        List<Integer> effectivePoints = new ArrayList<>();
        if (specificTimes) {
            if (MODE_ONCE.equals(mode)) {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(onceAt);
                effectivePoints.add(cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE) * 60);
            } else {
                effectivePoints.addAll(points);
            }
            startMinute = effectivePoints.isEmpty() ? 9 * 60 : (effectivePoints.get(0) / 60);
            endMinute = effectivePoints.isEmpty() ? 9 * 60 : (effectivePoints.get(effectivePoints.size() - 1) / 60);
        } else if (startMinute == endMinute) {
            endMinute = (startMinute + 1) % (24 * 60);
        }
        if (!MODE_ONCE.equals(mode) && !MODE_WEEKLY.equals(mode)) daysMask = 0;
        if (!MODE_ONCE.equals(mode)) onceAt = 0L;

        ReminderSettings.Rule rule = ReminderSettings.Rule.withIntervalSeconds(id, title, startMinute, endMinute,
                        intervalSeconds, 1, true, true)
                .withSchedule(specificTimes, effectivePoints, daysMask, onceAt)
                .withModule(ReminderSettings.MODULE_TASK)
                .withReminderMessage(note);
        if (existing != null) {
            rule = rule.withIcon(existing.icon).withColor(existing.color).withOverlayArtwork(existing.overlayArtwork);
        }
        return rule;
    }

    private String modeOf(ReminderSettings.Rule rule) {
        if (rule.isOnceSchedule()) return MODE_ONCE;
        if (rule.isWeeklySchedule()) return MODE_WEEKLY;
        return rule.specificTimeMode ? MODE_DAILY : MODE_INTERVAL;
    }

    private interface TimeCallback { void onPicked(int secondsOfDay); }

    private void pickTime(int current, TimeCallback callback) {
        new TimePickerDialog(this, (view, hour, minute) -> callback.onPicked(hour * 3600 + minute * 60),
                current / 3600, (current % 3600) / 60, true).show();
    }

    // ---------------------------------------------------------------- 小工具

    private TextView sectionLabel(String value) {
        TextView label = text(value, 12, UiPalette.MUTED, true);
        label.setPadding(0, dp(16), 0, dp(6));
        return label;
    }

    private TextView chip(String value, boolean selected) {
        TextView chip = text(value, 13, UiPalette.INK, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(0, dp(8), 0, dp(8));
        styleChip(chip, selected);
        return chip;
    }

    private void styleChip(TextView chip, boolean selected) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(selected ? UiPalette.ACCENT : UiPalette.SOFT);
        bg.setCornerRadius(dp(10));
        chip.setBackground(bg);
        chip.setTextColor(selected ? Color.WHITE : UiPalette.INK);
    }

    private GradientDrawable outline(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.TRANSPARENT);
        shape.setCornerRadius(dp(14));
        shape.setStroke(dp(1), color);
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
