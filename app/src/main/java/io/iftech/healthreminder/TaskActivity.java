package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * 任务清单：增删改、一键清空、重置今日，到点由 ReminderReceiver 弹霸屏页。
 * 编辑弹窗的视觉语言和「添加事项」保持一致（FormKit）。
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
        UiPalette.applyWindow(getWindow());
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(UiPalette.page());
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
        header.setPadding(dp(20), dp(16), dp(20), dp(8));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("任务清单", 25, UiPalette.INK, true);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView cards = text("卡片", 13, UiPalette.ACCENT, true);
        cards.setGravity(Gravity.CENTER);
        cards.setPadding(dp(14), dp(8), dp(14), dp(8));
        cards.setBackground(FormKit.round(this, UiPalette.SOFT, 12));
        cards.setOnClickListener(v -> startActivity(new Intent(this, CardActivity.class)));
        row.addView(cards);

        TextView add = text("＋ 新任务", 13, Color.WHITE, true);
        add.setGravity(Gravity.CENTER);
        add.setPadding(dp(14), dp(8), dp(14), dp(8));
        add.setBackground(FormKit.round(this, UiPalette.ACCENT, 12));
        add.setOnClickListener(v -> showEditor(null));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(-2, -2);
        addParams.leftMargin = dp(8);
        row.addView(add, addParams);
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
        footer.setPadding(dp(16), dp(6), dp(16), dp(4));

        TextView reset = text("重置今日", 13, UiPalette.MUTED, true);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(0, dp(10), 0, dp(10));
        reset.setBackground(FormKit.round(this, Color.WHITE, 12));
        reset.setOnClickListener(v -> confirmResetToday());
        LinearLayout.LayoutParams a = new LinearLayout.LayoutParams(0, -2, 1f);
        a.rightMargin = dp(6);
        footer.addView(reset, a);

        TextView clear = text("清空全部任务", 13, UiPalette.WARN, true);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(0, dp(10), 0, dp(10));
        clear.setBackground(FormKit.round(this, UiPalette.WARN_SOFT, 12));
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
        card.setPadding(dp(14), dp(14), dp(10), dp(14));
        card.setBackground(FormKit.round(this, Color.WHITE, 18));
        card.setElevation(dp(1));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);

        TextView badge = text(doneToday ? "✓" : task.badgeText(), 16,
                doneToday ? Color.WHITE : UiPalette.ACCENT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(FormKit.round(this, doneToday ? UiPalette.ACCENT : UiPalette.PALE, 12));
        card.addView(badge, new LinearLayout.LayoutParams(dp(40), dp(40)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.leftMargin = dp(12);
        card.addView(texts, textParams);

        texts.addView(text(task.title.isEmpty() ? "未命名任务" : task.title, 16,
                doneToday ? UiPalette.MUTED : UiPalette.INK, true));
        TextView schedule = text(describe(task), 12, UiPalette.MUTED, false);
        LinearLayout.LayoutParams scheduleParams = new LinearLayout.LayoutParams(-1, -2);
        scheduleParams.topMargin = dp(3);
        texts.addView(schedule, scheduleParams);
        if (!task.reminderMessage.isEmpty()) {
            TextView note = text(task.reminderMessage, 12, UiPalette.MUTED, false);
            note.setMaxLines(1);
            note.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(note);
        }

        Switch toggle = new Switch(this);
        toggle.setChecked(task.enabled);
        toggle.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Color.WHITE, Color.WHITE}));
        toggle.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{UiPalette.ACCENT, UiPalette.DISABLED}));
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
                if ((rule.daysMask & (1 << i)) != 0) days.append(names[i]);
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
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("删除任务")
                .setMessage("确定删除「" + rule.title + "」？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    ReminderSettings.deleteRule(this, rule.id);
                    reload();
                })
                .create();
        FormKit.showStyled(this, dialog);
    }

    private void confirmClearAll() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("清空全部任务")
                .setMessage("会删除所有任务和它们的提醒，历史记录保留。确定吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清空", (d, w) -> {
                    ReminderSettings.clearModule(this, ReminderSettings.MODULE_TASK);
                    reload();
                })
                .create();
        FormKit.showStyled(this, dialog);
    }

    private void confirmResetToday() {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("重置今日")
                .setMessage("把今天所有任务的完成状态清掉，重新开始打卡。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重置", (d, w) -> {
                    ReminderRecord.deleteForDay(this, ReminderSettings.MODULE_TASK,
                            ReminderRecord.dayStart(System.currentTimeMillis()));
                    reload();
                })
                .create();
        FormKit.showStyled(this, dialog);
    }

    // ---------------------------------------------------------------- 编辑

    /** 当前编辑中的调度方式（可被分段选择器改写）。 */
    private String draftMode;

    private void showEditor(ReminderSettings.Rule existing) {
        final boolean creating = existing == null;
        final int id = creating ? ReminderSettings.nextRuleId(this) : existing.id;
        draftMode = creating ? MODE_DAILY : modeOf(existing);

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(6), dp(20), dp(6));

        EditText name = FormKit.textInput(this, "例如：背 20 个单词", existing == null ? "" : existing.title);
        name.setSingleLine(true);
        form.addView(name);

        EditText note = FormKit.input(this, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        note.setHint("备注（选填，会显示在霸屏页上）");
        note.setMinLines(2);
        note.setMaxLines(3);
        note.setText(existing == null ? "" : existing.reminderMessage);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(10);
        form.addView(note, noteParams);

        form.addView(FormKit.editorLabel(this, "提醒方式", true));

        final List<Integer> points = new ArrayList<>();
        if (existing != null && !existing.timePointsSeconds.isEmpty()) points.addAll(existing.timePointsSeconds);
        if (points.isEmpty()) points.add(9 * 3600);

        final int[] daysMask = {existing == null ? 0x7f : existing.daysMask};
        final long[] onceAt = {existing == null || existing.onceAt <= 0L
                ? tomorrowMorning() : existing.onceAt};
        final int[] window = {existing == null ? 9 * 60 : existing.start,
                existing == null ? 18 * 60 : existing.end};
        final int[] intervalMinutes = {existing == null ? 60 : Math.max(1, existing.intervalSeconds / 60)};

        // 分段选择器
        LinearLayout modes = FormKit.segmentGroup(this);
        String[] labels = {"每天", "每周", "单次", "间隔"};
        String[] values = {MODE_DAILY, MODE_WEEKLY, MODE_ONCE, MODE_INTERVAL};
        final TextView[] modeOptions = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final String value = values[i];
            final TextView option = FormKit.segmentOption(this, labels[i], value.equals(draftMode));
            option.setOnClickListener(v -> {
                draftMode = value;
                for (int j = 0; j < modeOptions.length; j++) {
                    FormKit.setSegmentSelected(modeOptions[j], values[j].equals(draftMode));
                }
                updateModeSections(this, form);
            });
            modeOptions[i] = option;
            modes.addView(option, new LinearLayout.LayoutParams(0, dp(42), 1));
        }
        form.addView(modes);

        // —— 每天 / 每周：时间点
        LinearLayout pointsSection = new LinearLayout(this);
        pointsSection.setOrientation(LinearLayout.VERTICAL);
        pointsSection.addView(FormKit.editorLabel(this, "提醒时间", false));
        LinearLayout pointRows = new LinearLayout(this);
        pointRows.setOrientation(LinearLayout.VERTICAL);
        pointsSection.addView(pointRows);
        Button addPoint = FormKit.controlButton(this);
        addPoint.setText("＋ 添加时间点");
        addPoint.setOnClickListener(v -> WheelPicker.time(this, "提醒时间", "确定", 9 * 3600, false, picked -> {
            points.add(picked);
            java.util.Collections.sort(points);
            renderPoints(pointRows, points);
        }).show());
        LinearLayout.LayoutParams addPointParams = new LinearLayout.LayoutParams(-1, -2);
        addPointParams.topMargin = dp(6);
        pointsSection.addView(addPoint, addPointParams);
        form.addView(pointsSection);
        renderPoints(pointRows, points);

        // —— 每周：星期
        LinearLayout weekSection = new LinearLayout(this);
        weekSection.setOrientation(LinearLayout.VERTICAL);
        weekSection.addView(FormKit.editorLabel(this, "哪几天", true));
        LinearLayout weekRow = FormKit.segmentGroup(this);
        String[] dayNames = {"一", "二", "三", "四", "五", "六", "日"};
        for (int i = 0; i < 7; i++) {
            final int bit = 1 << i;
            final TextView chip = FormKit.segmentOption(this, dayNames[i], (daysMask[0] & bit) != 0);
            chip.setOnClickListener(v -> {
                daysMask[0] ^= bit;
                FormKit.setSegmentSelected(chip, (daysMask[0] & bit) != 0);
            });
            weekRow.addView(chip, new LinearLayout.LayoutParams(0, dp(40), 1));
        }
        weekSection.addView(weekRow);
        form.addView(weekSection);

        // —— 单次：日期 + 时间
        LinearLayout onceSection = new LinearLayout(this);
        onceSection.setOrientation(LinearLayout.VERTICAL);
        onceSection.addView(FormKit.editorLabel(this, "什么时候", true));
        LinearLayout onceRow = new LinearLayout(this);
        Calendar onceCal = Calendar.getInstance();
        onceCal.setTimeInMillis(onceAt[0]);
        LinearLayout dateField = FormKit.timeField(this, "日期",
                String.format(Locale.getDefault(), "%d月%d日", onceCal.get(Calendar.MONTH) + 1,
                        onceCal.get(Calendar.DAY_OF_MONTH)));
        dateField.setOnClickListener(v -> {
            Calendar base = Calendar.getInstance();
            base.setTimeInMillis(onceAt[0]);
            WheelPicker.date(this, "选择日期", base, picked -> {
                Calendar merged = Calendar.getInstance();
                merged.setTimeInMillis(onceAt[0]);
                merged.set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH));
                onceAt[0] = merged.getTimeInMillis();
                FormKit.updateTimeField(dateField, String.format(Locale.getDefault(), "%d月%d日",
                        picked.get(Calendar.MONTH) + 1, picked.get(Calendar.DAY_OF_MONTH)));
            }).show();
        });
        LinearLayout timeField = FormKit.timeField(this, "时间", timeText(onceAt[0] / 1000));
        timeField.setOnClickListener(v -> WheelPicker.time(this, "时间", "确定",
                (int) ((onceAt[0] / 1000) % 86400), false, picked -> {
                    Calendar merged = Calendar.getInstance();
                    merged.setTimeInMillis(onceAt[0]);
                    merged.set(Calendar.HOUR_OF_DAY, picked / 3600);
                    merged.set(Calendar.MINUTE, (picked % 3600) / 60);
                    merged.set(Calendar.SECOND, 0);
                    onceAt[0] = merged.getTimeInMillis();
                    FormKit.updateTimeField(timeField, timeText(picked));
                }).show());
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(0, -2, 1f);
        dateParams.rightMargin = dp(6);
        onceRow.addView(dateField, dateParams);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(0, -2, 1f);
        timeParams.leftMargin = dp(6);
        onceRow.addView(timeField, timeParams);
        onceSection.addView(onceRow);
        form.addView(onceSection);

        // —— 间隔：开始 / 结束 / 间隔
        LinearLayout intervalSection = new LinearLayout(this);
        intervalSection.setOrientation(LinearLayout.VERTICAL);
        intervalSection.addView(FormKit.editorLabel(this, "提醒时段", true));
        LinearLayout windowRow = new LinearLayout(this);
        LinearLayout fromField = FormKit.timeField(this, "开始时间", minuteText(window[0]));
        fromField.setOnClickListener(v -> WheelPicker.time(this, "开始时间", "确定",
                window[0] * 60, false, picked -> {
                    window[0] = picked / 60;
                    FormKit.updateTimeField(fromField, minuteText(window[0]));
                }).show());
        LinearLayout toField = FormKit.timeField(this, "结束时间", minuteText(window[1]));
        toField.setOnClickListener(v -> WheelPicker.time(this, "结束时间", "确定",
                window[1] * 60, false, picked -> {
                    window[1] = picked / 60;
                    FormKit.updateTimeField(toField, minuteText(window[1]));
                }).show());
        LinearLayout.LayoutParams fromParams = new LinearLayout.LayoutParams(0, -2, 1f);
        fromParams.rightMargin = dp(6);
        windowRow.addView(fromField, fromParams);
        LinearLayout.LayoutParams toParams = new LinearLayout.LayoutParams(0, -2, 1f);
        toParams.leftMargin = dp(6);
        windowRow.addView(toField, toParams);
        intervalSection.addView(windowRow);

        Button intervalButton = FormKit.controlButton(this);
        intervalButton.setText("提醒间隔  " + intervalText(intervalMinutes[0]));
        intervalButton.setOnClickListener(v -> chooseInterval(intervalMinutes, intervalButton));
        LinearLayout.LayoutParams intervalParams = new LinearLayout.LayoutParams(-1, -2);
        intervalParams.topMargin = dp(8);
        intervalSection.addView(intervalButton, intervalParams);
        form.addView(intervalSection);

        // 设置块
        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.VERTICAL);
        settings.setPadding(dp(14), dp(10), dp(14), dp(10));
        settings.setBackground(FormKit.round(this, UiPalette.PALE, 12));
        LinearLayout resetRow = new LinearLayout(this);
        resetRow.setGravity(Gravity.CENTER_VERTICAL);
        resetRow.addView(text("每天自动重置完成状态", 14, UiPalette.INK, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        Switch resetSwitch = new Switch(this);
        resetSwitch.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{Color.WHITE, Color.WHITE}));
        resetSwitch.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{UiPalette.ACCENT, UiPalette.DISABLED}));
        resetSwitch.setChecked(existing != null && ReminderSettings.autoReset(this, existing.id));
        resetRow.addView(resetSwitch);
        settings.addView(resetRow);
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(-1, -2);
        settingsParams.topMargin = dp(16);
        form.addView(settings, settingsParams);

        final int maxContentHeight = Math.min(dp(600), getResources().getDisplayMetrics().heightPixels - dp(240));
        ScrollView formScroll = new ScrollView(this) {
            @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                int available = Math.max(dp(320), maxContentHeight);
                int size = MeasureSpec.getSize(heightMeasureSpec);
                int constrained = size == 0 ? available : Math.min(size, available);
                super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(constrained, MeasureSpec.AT_MOST));
            }
        };
        formScroll.setClipToPadding(false);
        formScroll.setVerticalScrollBarEnabled(false);
        formScroll.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(creating ? "添加任务" : "编辑任务")
                .setView(formScroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = name.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "请输入任务名", Toast.LENGTH_SHORT).show();
                return;
            }
            ReminderSettings.Rule rule = buildRule(id, existing, title, note.getText().toString().trim(),
                    draftMode, points, daysMask[0], onceAt[0], window, intervalMinutes[0]);
            ReminderSettings.upsertRule(this, rule);
            ReminderSettings.setAutoReset(this, id, resetSwitch.isChecked());
            dialog.dismiss();
            reload();
        }));
        FormKit.showStyled(this, dialog);
        updateModeSections(this, form);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    /** 按当前调度方式显隐各段。 */
    private void updateModeSections(Context context, LinearLayout form) {
        for (int i = 1; i < form.getChildCount(); i++) {
            View child = form.getChildAt(i);
            if (!(child instanceof LinearLayout)) continue;
            LinearLayout section = (LinearLayout) child;
            if (section.getChildCount() == 0) continue;
            View first = section.getChildAt(0);
            if (!(first instanceof TextView)) continue;
            String label = ((TextView) first).getText().toString();
            boolean visible;
            if ("提醒时间".equals(label)) {
                visible = MODE_DAILY.equals(draftMode) || MODE_WEEKLY.equals(draftMode);
            } else if ("哪几天".equals(label)) {
                visible = MODE_WEEKLY.equals(draftMode);
            } else if ("什么时候".equals(label)) {
                visible = MODE_ONCE.equals(draftMode);
            } else if ("提醒时段".equals(label)) {
                visible = MODE_INTERVAL.equals(draftMode);
            } else {
                continue;
            }
            section.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void renderPoints(LinearLayout container, final List<Integer> points) {
        container.removeAllViews();
        for (int i = 0; i < points.size(); i++) {
            final int index = i;
            final int seconds = points.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(8), 0);
            row.setBackground(FormKit.round(this, Color.WHITE, 10));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, dp(48));
            rowParams.bottomMargin = dp(6);
            row.setLayoutParams(rowParams);
            row.addView(text(timeText(seconds), 17, UiPalette.INK, true),
                    new LinearLayout.LayoutParams(0, -2, 1f));
            TextView remove = text("移除", 12, UiPalette.MUTED, false);
            remove.setPadding(dp(10), dp(8), dp(6), dp(8));
            remove.setOnClickListener(v -> {
                if (points.size() <= 1) return;
                points.remove(index);
                renderPoints(container, points);
            });
            row.addView(remove);
            row.setOnClickListener(v -> WheelPicker.time(this, "提醒时间", "确定", seconds, false, picked -> {
                points.set(index, picked);
                java.util.Collections.sort(points);
                renderPoints(container, points);
            }).show());
            container.addView(row);
        }
    }

    private void chooseInterval(final int[] minutes, final Button button) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(4));

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setPadding(0, dp(4), 0, dp(8));
        final AlertDialog[] holder = new AlertDialog[1];
        for (final int option : new int[]{5, 10, 15, 30, 60}) {
            TextView chip = text(option + " 分", 13, UiPalette.ACCENT, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, dp(9), 0, dp(9));
            chip.setBackground(FormKit.round(this, UiPalette.SOFT, 10));
            chip.setOnClickListener(v -> {
                minutes[0] = option;
                button.setText("提醒间隔  " + intervalText(minutes[0]));
                if (holder[0] != null) holder[0].dismiss();
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, -2, 1f);
            if (option != 5) chipParams.leftMargin = dp(6);
            quickRow.addView(chip, chipParams);
        }
        content.addView(quickRow);

        EditText input = FormKit.input(this, InputType.TYPE_CLASS_NUMBER);
        input.setHint("自定义分钟数");
        input.setText(String.valueOf(minutes[0]));
        content.addView(input);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("提醒间隔")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, w) -> {
                    try {
                        minutes[0] = Math.max(1, Integer.parseInt(input.getText().toString().trim()));
                        button.setText("提醒间隔  " + intervalText(minutes[0]));
                    } catch (NumberFormatException ignored) { }
                })
                .create();
        holder[0] = dialog;
        FormKit.showStyled(this, dialog);
    }

    private static String intervalText(int minutes) {
        if (minutes % 60 == 0 && minutes >= 60) return (minutes / 60) + " 小时";
        return minutes + " 分钟";
    }

    private long tomorrowMorning() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 9);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
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
        if (!MODE_WEEKLY.equals(mode)) daysMask = 0;
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

    // ---------------------------------------------------------------- 小工具

    private TextView text(String value, int size, int color, boolean bold) {
        return FormKit.text(this, value, size, color, bold);
    }

    private int dp(float value) {
        return FormKit.dp(this, value);
    }
}
