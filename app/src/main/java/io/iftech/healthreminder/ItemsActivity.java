package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;

public final class ItemsActivity extends Activity {
    private static final int GREEN = UiPalette.ACCENT;
    private static final int MINT = UiPalette.ACCENT;
    private static final int MINT_LIGHT = UiPalette.SOFT;
    private static final int MINT_PALE = UiPalette.PALE;
    private static final int LINE = UiPalette.BORDER;
    private static final int INK = UiPalette.INK;
    private static final int MUTED = UiPalette.MUTED;
    private static final int PAPER = UiPalette.PAGE;
    private LinearLayout list;
    private boolean returnToHome;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        returnToHome = getIntent().getBooleanExtra("return_to_home", false);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(0);
        FrameLayout frame = new FrameLayout(this);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackground(UiPalette.page());
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(20), dp(14), dp(20), dp(96));
        scroll.addView(list);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        addNavigation(screen);
        frame.addView(screen);
        TextView add = text("＋", 28, Color.WHITE, false);
        add.setGravity(Gravity.CENTER);
        add.setBackground(circle(MINT));
        add.setElevation(dp(5));
        add.setContentDescription("添加提醒事项");
        FrameLayout.LayoutParams fab = new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.RIGHT | Gravity.BOTTOM);
        fab.setMargins(0, 0, dp(20), dp(78));
        frame.addView(add, fab);
        add.setOnClickListener(v -> editRule(null));
        setContentView(frame);
        renderItems();
        if (state == null) {
            if (getIntent().getBooleanExtra("new_item", false)) {
                editRule(null);
            } else {
                int editId = getIntent().getIntExtra("edit_id", -1);
                for (ReminderSettings.Rule rule : ReminderSettings.rules(this)) {
                    if (rule.id == editId) {
                        editRule(rule);
                        break;
                    }
                }
            }
        }
    }

    @Override protected void onResume() { super.onResume(); if (list != null) renderItems(); }

    private void renderItems() {
        list.removeAllViews();
        List<ReminderSettings.Rule> rules = ReminderSettings.rules(this);
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("今日行动", 26, INK, true);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView addLabel = text("添加", 13, GREEN, true);
        addLabel.setGravity(Gravity.CENTER);
        addLabel.setPadding(dp(12), dp(8), dp(12), dp(8));
        addLabel.setBackground(round(MINT_LIGHT, 18));
        addLabel.setOnClickListener(v -> editRule(null));
        heading.addView(addLabel);
        list.addView(heading);

        int doneTotal = 0;
        int targetTotal = 0;
        for (ReminderSettings.Rule rule : rules) {
            doneTotal += ReminderSettings.completedToday(this, rule.id);
            targetTotal += rule.dailyTarget;
        }
        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setPadding(dp(16), dp(14), dp(16), dp(14));
        summary.setBackground(surface(new int[]{0xff10412e, 0xff173c35}, 16, UiPalette.BORDER));
        LinearLayout summaryTop = new LinearLayout(this);
        summaryTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView summaryTitle = text("今天的完成进度", 14, INK, true);
        summaryTop.addView(summaryTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView date = text(new SimpleDateFormat("M月d日  EEEE", Locale.CHINA)
                .format(Calendar.getInstance().getTime()), 12, MUTED, false);
        summaryTop.addView(date);
        summary.addView(summaryTop);
        LinearLayout totalLine = new LinearLayout(this);
        totalLine.setGravity(Gravity.CENTER_VERTICAL);
        TextView completedCount = text(String.valueOf(doneTotal), 25, GREEN, true);
        totalLine.addView(completedCount);
        TextView targetCount = text(" / " + targetTotal + " 次", 13, MUTED, false);
        targetCount.setPadding(dp(5), dp(6), 0, 0);
        totalLine.addView(targetCount);
        summary.addView(totalLine);
        ProgressBar totalProgress = progressBar(targetTotal, doneTotal);
        LinearLayout.LayoutParams totalProgressParams = new LinearLayout.LayoutParams(-1, dp(5));
        totalProgressParams.topMargin = dp(8);
        summary.addView(totalProgress, totalProgressParams);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
        summaryParams.topMargin = dp(14);
        list.addView(summary, summaryParams);

        LinearLayout sectionHeader = new LinearLayout(this);
        sectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        sectionHeader.setPadding(0, dp(22), 0, dp(9));
        sectionHeader.addView(text("提醒事项", 17, INK, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        sectionHeader.addView(text(rules.size() + " 项", 12, MUTED, false));
        list.addView(sectionHeader);

        for (ReminderSettings.Rule rule : rules) {
            int done = ReminderSettings.completedToday(this, rule.id);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(13), dp(14), dp(12));
            card.setBackground(categorySurface(rule.title));
            card.setElevation(dp(1));
            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            LinearLayout titleLine = new LinearLayout(this);
            titleLine.setGravity(Gravity.CENTER_VERTICAL);
            TextView marker = text(rule.title.isEmpty() ? "·" : rule.title.substring(0, 1),
                    13, categoryInk(rule.title), true);
            marker.setGravity(Gravity.CENTER);
            marker.setBackground(round(categoryTint(rule.title), 10));
            titleLine.addView(marker, new LinearLayout.LayoutParams(dp(32), dp(32)));
            TextView itemTitle = text(rule.title, 16, INK, true);
            LinearLayout.LayoutParams itemTitleParams = new LinearLayout.LayoutParams(0, -2, 1);
            itemTitleParams.leftMargin = dp(9);
            titleLine.addView(itemTitle, itemTitleParams);
            TextView count = text(done >= rule.dailyTarget ? "已达标" : done + " / " + rule.dailyTarget,
                    12, categoryInk(rule.title), true);
            count.setGravity(Gravity.CENTER);
            count.setPadding(dp(9), dp(5), dp(9), dp(5));
            count.setBackground(round(categoryTint(rule.title), 16));
            titleLine.addView(count);
            details.addView(titleLine);
            ProgressBar itemProgress = progressBar(rule.dailyTarget, done);
            LinearLayout.LayoutParams itemProgressParams = new LinearLayout.LayoutParams(-1, dp(4));
            itemProgressParams.topMargin = dp(11);
            details.addView(itemProgress, itemProgressParams);
            LinearLayout meta = new LinearLayout(this);
            meta.setGravity(Gravity.CENTER_VERTICAL);
            meta.setPadding(0, dp(10), 0, 0);
            TextView schedule = text(scheduleSummary(rule), 12, categoryInk(rule.title), false);
            schedule.setPadding(dp(9), dp(5), dp(9), dp(5));
            schedule.setBackground(round(categoryTint(rule.title), 9));
            meta.addView(schedule, new LinearLayout.LayoutParams(0, -2, 1));
            TextView targetBehavior = text(rule.remindAfterTarget ? "继续提醒" : "达标停止", 11, MUTED, false);
            targetBehavior.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            targetBehavior.setPadding(dp(8), 0, 0, 0);
            meta.addView(targetBehavior);
            details.addView(meta);
            card.addView(details);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(10);
            list.addView(card, params);
            card.setOnClickListener(v -> editRule(rule));
            card.setOnLongClickListener(v -> { showItemHistory(rule); return true; });
        }
        if (rules.isEmpty()) {
            TextView empty = text("还没有提醒事项", 15, MUTED, true);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(16), dp(30), dp(16), dp(30));
            empty.setBackground(surface(new int[]{0xff12382c, 0xff172e32}, 14, UiPalette.BORDER));
            list.addView(empty);
        }
    }

    private ProgressBar progressBar(int target, int done) {
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false);
        progress.setMax(Math.max(1, target));
        progress.setProgress(Math.min(Math.max(0, done), Math.max(1, target)));
        progress.setProgressTintList(ColorStateList.valueOf(MINT));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        return progress;
    }

    private int categoryInk(String title) {
        if (title.contains("水")) return 0xff70d6dd;
        if (title.contains("眼")) return 0xffa8b9ff;
        if (title.contains("走") || title.contains("活动")) return 0xff67d49b;
        if (title.contains("颈") || title.contains("肩")) return 0xffffce78;
        if (title.contains("放松") || title.contains("呼吸")) return 0xffd7a6eb;
        return 0xffffa497;
    }

    private int categoryTint(String title) {
        if (title.contains("水")) return 0xff173b40;
        if (title.contains("眼")) return 0xff292f4c;
        if (title.contains("走") || title.contains("活动")) return 0xff173d2d;
        if (title.contains("颈") || title.contains("肩")) return 0xff49371f;
        if (title.contains("放松") || title.contains("呼吸")) return 0xff3b2947;
        return 0xff482b28;
    }

    private GradientDrawable categorySurface(String title) {
        int tint = categoryTint(title);
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{tint, 0xff192f29});
        background.setCornerRadius(dp(15));
        background.setStroke(dp(1), categoryBorder(title));
        return background;
    }

    private int categoryBorder(String title) {
        if (title.contains("水")) return 0xff39717a;
        if (title.contains("眼")) return 0xff59648d;
        if (title.contains("走") || title.contains("活动")) return 0xff34724f;
        if (title.contains("颈") || title.contains("肩")) return 0xff8a6935;
        if (title.contains("放松") || title.contains("呼吸")) return 0xff765187;
        return 0xff89514a;
    }

    private GradientDrawable surface(int[] colors, int radius, int stroke) {
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TL_BR, colors);
        background.setCornerRadius(dp(radius));
        background.setStroke(dp(1), stroke);
        return background;
    }

    private String scheduleSummary(ReminderSettings.Rule rule) {
        if (!rule.specificTimeMode) {
            return MainActivity.clock(rule.start) + " - " + MainActivity.clock(rule.end)
                    + "  ·  " + intervalText(rule.intervalSeconds);
        }
        StringBuilder summary = new StringBuilder("指定时间  ");
        for (int i = 0; i < rule.timePointsSeconds.size(); i++) {
            if (i > 0) summary.append("、");
            summary.append(timePointText(rule.timePointsSeconds.get(i)));
        }
        return summary.toString();
    }

    private void editRule(ReminderSettings.Rule existing) {
        int id = existing == null ? ReminderSettings.nextRuleId(this) : existing.id;
        int[] start = {existing == null ? 9 * 60 : existing.start};
        int[] end = {existing == null ? 18 * 60 : existing.end};
        int[] interval = {existing == null ? 30 * 60 : existing.intervalSeconds};
        int[] target = {existing == null ? 3 : existing.dailyTarget};
        boolean[] enabled = {existing == null || existing.enabled};
        boolean[] remindAfterTarget = {existing != null && existing.remindAfterTarget};
        boolean[] specificTimeMode = {existing != null && existing.specificTimeMode};
        ArrayList<Integer> timePoints = new ArrayList<>();
        if (existing != null) timePoints.addAll(existing.timePointsSeconds);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(4), dp(20), dp(14));
        TextView nameLabel = text("事项名称", 13, MUTED, true);
        nameLabel.setPadding(0, 0, 0, dp(6));
        form.addView(nameLabel);
        EditText name = styledInput(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setSingleLine(true);
        name.setHint("例如：喝水、站起来活动");
        name.setText(existing == null ? "" : existing.title);
        form.addView(name);

        form.addView(editorLabel("提醒方式", true));
        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setPadding(dp(5), dp(4), dp(5), dp(4));
        modes.setBackground(round(MINT_PALE, 12));
        TextView intervalMode = segmentOption("按间隔", !specificTimeMode[0]);
        TextView specificMode = segmentOption("指定时间点", specificTimeMode[0]);
        modes.addView(intervalMode, new LinearLayout.LayoutParams(0, dp(44), 1));
        modes.addView(specificMode, new LinearLayout.LayoutParams(0, dp(44), 1));
        form.addView(modes);

        LinearLayout intervalSection = new LinearLayout(this);
        intervalSection.setOrientation(LinearLayout.VERTICAL);
        intervalSection.addView(editorLabel("提醒时段", true));
        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout from = timeField("开始时间", start[0]);
        LinearLayout to = timeField("结束时间", end[0]);
        TextView fromValue = (TextView) from.getChildAt(1);
        TextView toValue = (TextView) to.getChildAt(1);
        from.setOnClickListener(v -> showStyledDialog(new TimePickerDialog(dialogContext(), (picker, hour, minute) -> {
            start[0] = hour * 60 + minute;
            fromValue.setText(MainActivity.clock(start[0]));
        }, start[0] / 60, start[0] % 60, true)));
        to.setOnClickListener(v -> showStyledDialog(new TimePickerDialog(dialogContext(), (picker, hour, minute) -> {
            end[0] = hour * 60 + minute;
            toValue.setText(MainActivity.clock(end[0]));
        }, end[0] / 60, end[0] % 60, true)));
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(0, -2, 1);
        timeParams.rightMargin = dp(6);
        timeRow.addView(from, timeParams);
        LinearLayout.LayoutParams toParams = new LinearLayout.LayoutParams(0, -2, 1);
        toParams.leftMargin = dp(6);
        timeRow.addView(to, toParams);
        intervalSection.addView(timeRow);
        Button intervalButton = new Button(this); styleControlButton(intervalButton);
        intervalButton.setText("提醒间隔  " + intervalText(interval[0]));
        intervalButton.setOnClickListener(v -> chooseInterval(interval, intervalButton));
        LinearLayout.LayoutParams intervalParams = new LinearLayout.LayoutParams(-1, -2);
        intervalParams.topMargin = dp(8);
        intervalSection.addView(intervalButton, intervalParams);
        form.addView(intervalSection);

        LinearLayout timePointSection = new LinearLayout(this);
        timePointSection.setOrientation(LinearLayout.VERTICAL);
        timePointSection.addView(editorLabel("每天的提醒时间", true));
        TextView timePointHint = text("每个时间点每天提醒一次", 12, MUTED, false);
        timePointHint.setPadding(0, 0, 0, dp(3));
        timePointSection.addView(timePointHint);
        LinearLayout timePointRows = new LinearLayout(this);
        timePointRows.setOrientation(LinearLayout.VERTICAL);
        timePointSection.addView(timePointRows);
        Button addTimePoint = new Button(this);
        styleControlButton(addTimePoint);
        addTimePoint.setText("＋ 添加时间点");
        addTimePoint.setOnClickListener(v -> showTimePointPicker(timePoints, timePointRows));
        timePointSection.addView(addTimePoint);
        form.addView(timePointSection);
        renderTimePoints(timePoints, timePointRows);
        Runnable updateModeViews = () -> {
            intervalSection.setVisibility(specificTimeMode[0] ? View.GONE : View.VISIBLE);
            timePointSection.setVisibility(specificTimeMode[0] ? View.VISIBLE : View.GONE);
            setSegmentOptionSelected(intervalMode, !specificTimeMode[0]);
            setSegmentOptionSelected(specificMode, specificTimeMode[0]);
        };
        intervalMode.setOnClickListener(v -> {
            specificTimeMode[0] = false;
            updateModeViews.run();
        });
        specificMode.setOnClickListener(v -> {
            specificTimeMode[0] = true;
            updateModeViews.run();
        });
        updateModeViews.run();

        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.VERTICAL);
        settings.setPadding(dp(14), dp(8), dp(14), dp(8));
        settings.setBackground(round(MINT_PALE, 12));
        LinearLayout targetRow = new LinearLayout(this);
        targetRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView targetLabel = text("每日目标", 14, INK, true);
        targetRow.addView(targetLabel, new LinearLayout.LayoutParams(0, -2, 1));
        Button targetButton = new Button(this);
        styleControlButton(targetButton);
        targetButton.setMinHeight(dp(40));
        targetButton.setText("" + target[0] + " 次  ›");
        targetButton.setOnClickListener(v -> {
            EditText input = styledInput(android.text.InputType.TYPE_CLASS_NUMBER);
            input.setText(String.valueOf(target[0]));
            AlertDialog goalDialog = new AlertDialog.Builder(dialogContext()).setTitle("每天想完成几次").setView(input).setNegativeButton("取消", null)
                    .setPositiveButton("确定", (dialog, which) -> { try { target[0] = Math.max(1, Integer.parseInt(input.getText().toString())); targetButton.setText(target[0] + " 次  ›"); } catch (NumberFormatException ignored) { } }).create();
            showStyledDialog(goalDialog);
        });
        targetRow.addView(targetButton);
        settings.addView(targetRow);
        TextView goalLabel = text("达标后是否继续提醒", 13, MUTED, true);
        goalLabel.setPadding(0, dp(6), 0, dp(2));
        settings.addView(goalLabel);
        LinearLayout goalOptions = new LinearLayout(this);
        goalOptions.setOrientation(LinearLayout.HORIZONTAL);
        goalOptions.setPadding(dp(4), dp(4), dp(4), dp(4));
        goalOptions.setBackground(round(Color.WHITE, 12));
        TextView stop = segmentOption("达标后停止", !remindAfterTarget[0]);
        TextView continueReminding = segmentOption("继续提醒", remindAfterTarget[0]);
        goalOptions.addView(stop, new LinearLayout.LayoutParams(0, dp(42), 1));
        goalOptions.addView(continueReminding, new LinearLayout.LayoutParams(0, dp(42), 1));
        stop.setOnClickListener(v -> {
            remindAfterTarget[0] = false;
            setSegmentOptionSelected(stop, true);
            setSegmentOptionSelected(continueReminding, false);
        });
        continueReminding.setOnClickListener(v -> {
            remindAfterTarget[0] = true;
            setSegmentOptionSelected(stop, false);
            setSegmentOptionSelected(continueReminding, true);
        });
        LinearLayout.LayoutParams goalOptionsParams = new LinearLayout.LayoutParams(-1, -2);
        goalOptionsParams.topMargin = dp(3);
        settings.addView(goalOptions, goalOptionsParams);
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.topMargin = dp(5);
        settings.addView(divider, dividerParams);
        Switch enabledSwitch = new Switch(this);
        enabledSwitch.setText("启用此事项");
        enabledSwitch.setTextColor(INK);
        enabledSwitch.setTextSize(14);
        enabledSwitch.setMinHeight(dp(48));
        enabledSwitch.setThumbTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{Color.WHITE, Color.WHITE}));
        enabledSwitch.setTrackTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}}, new int[]{MINT, UiPalette.DISABLED}));
        enabledSwitch.setChecked(enabled[0]);
        enabledSwitch.setOnCheckedChangeListener((button, checked) -> enabled[0] = checked);
        settings.addView(enabledSwitch);
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(-1, -2);
        settingsParams.topMargin = dp(16);
        form.addView(settings, settingsParams);
        final int maxContentHeight = Math.min(dp(620),
                getResources().getDisplayMetrics().heightPixels - dp(220));
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
        AlertDialog.Builder builder = new AlertDialog.Builder(dialogContext()).setTitle(existing == null ? "添加提醒事项" : "编辑提醒事项")
                .setView(formScroll).setNegativeButton("取消", null).setPositiveButton("保存", null);
        if (existing != null) builder.setNeutralButton("删除", (dialog, which) -> { ArrayList<ReminderSettings.Rule> updated = new ArrayList<>(ReminderSettings.rules(this)); updated.removeIf(rule -> rule.id == id); ReminderSettings.saveRules(this, updated); renderItems(); });
        AlertDialog editor = builder.create();
        editor.setOnShowListener(ignored -> editor.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = name.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "请输入事项名称", Toast.LENGTH_SHORT).show();
                return;
            }
            if (specificTimeMode[0] && timePoints.isEmpty()) {
                Toast.makeText(this, "请至少添加一个提醒时间", Toast.LENGTH_SHORT).show();
                return;
            }
            ArrayList<ReminderSettings.Rule> updated = new ArrayList<>(ReminderSettings.rules(this));
            updated.removeIf(rule -> rule.id == id);
            ReminderSettings.Rule rule = specificTimeMode[0]
                    ? ReminderSettings.Rule.withSpecificTimes(id, title, start[0], end[0], interval[0],
                    timePoints, target[0], remindAfterTarget[0], enabled[0])
                    : ReminderSettings.Rule.withIntervalSeconds(id, title, start[0], end[0], interval[0],
                    target[0], remindAfterTarget[0], enabled[0]);
            updated.add(rule);
            ReminderSettings.saveRules(this, updated);
            renderItems();
            editor.dismiss();
            if (returnToHome) {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
                overridePendingTransition(0, 0);
            }
        }));
        showStyledDialog(editor);
        if (editor.getWindow() != null) {
            editor.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void showItemHistory(ReminderSettings.Rule rule) {
        Calendar selectedDay = Calendar.getInstance();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(12));
        panel.setBackground(round(Color.WHITE, 20));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        titleGroup.addView(text(rule.title, 19, INK, true));
        TextView subtitle = text("今天每一次照顾自己，都值得记下来", 12, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, 0);
        titleGroup.addView(subtitle);
        header.addView(titleGroup, new LinearLayout.LayoutParams(0, -2, 1));
        View calendarButton = new CalendarButton();
        calendarButton.setContentDescription("选择日期");
        calendarButton.setBackground(round(MINT_LIGHT, 12));
        header.addView(calendarButton, new LinearLayout.LayoutParams(dp(42), dp(42)));
        panel.addView(header);

        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        dateRow.setPadding(0, dp(14), 0, dp(10));
        TextView previous = text("‹", 28, GREEN, true);
        previous.setGravity(Gravity.CENTER);
        dateRow.addView(previous, new LinearLayout.LayoutParams(dp(42), dp(38)));
        TextView dateLabel = text("", 14, INK, true);
        dateLabel.setGravity(Gravity.CENTER);
        dateRow.addView(dateLabel, new LinearLayout.LayoutParams(0, dp(38), 1));
        TextView next = text("›", 28, GREEN, true);
        next.setGravity(Gravity.CENTER);
        dateRow.addView(next, new LinearLayout.LayoutParams(dp(42), dp(38)));
        panel.addView(dateRow);

        TextView totals = text("", 13, MUTED, true);
        totals.setPadding(0, 0, 0, dp(10));
        panel.addView(totals);

        LinearLayout history = new LinearLayout(this);
        history.setOrientation(LinearLayout.VERTICAL);
        ScrollView historyScroll = new ScrollView(this);
        historyScroll.setFillViewport(true);
        historyScroll.addView(history);
        panel.addView(historyScroll, new LinearLayout.LayoutParams(-1, dp(300)));

        Runnable refresh = () -> renderItemHistory(rule, selectedDay, dateLabel, totals, history);
        View.OnClickListener chooseDate = v -> showStyledDialog(new DatePickerDialog(dialogContext(), (picker, year, month, day) -> {
            selectedDay.set(year, month, day);
            refresh.run();
        }, selectedDay.get(Calendar.YEAR), selectedDay.get(Calendar.MONTH), selectedDay.get(Calendar.DAY_OF_MONTH)));
        calendarButton.setOnClickListener(chooseDate);
        dateLabel.setOnClickListener(chooseDate);
        previous.setOnClickListener(v -> { selectedDay.add(Calendar.DAY_OF_MONTH, -1); refresh.run(); });
        next.setOnClickListener(v -> { selectedDay.add(Calendar.DAY_OF_MONTH, 1); refresh.run(); });
        refresh.run();

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        dialog.show();
        styleDialog(dialog, getResources().getDisplayMetrics().widthPixels - dp(32));
    }

    private void renderItemHistory(ReminderSettings.Rule rule, Calendar day, TextView dateLabel,
                                   TextView totals, LinearLayout history) {
        dateLabel.setText(new SimpleDateFormat("yyyy年M月d日  EEEE", Locale.CHINA).format(day.getTime()));
        ArrayList<ReminderRecord> records = new ArrayList<>();
        int done = 0;
        int missed = 0;
        for (ReminderRecord record : ReminderRecord.all(this)) {
            if (!rule.title.equals(record.action) || !sameDay(record.startedAt, day)) continue;
            if (ReminderRecord.KIND_DONE.equals(record.kind)) done++;
            else if (ReminderRecord.KIND_MISSED.equals(record.kind)) missed++;
            else continue;
            records.add(record);
        }
        Collections.sort(records, Comparator.comparingLong(record -> record.startedAt));
        totals.setText("完成 " + done + " 次    没做 " + missed + " 次");
        history.removeAllViews();
        for (ReminderRecord record : records) {
            boolean completed = ReminderRecord.KIND_DONE.equals(record.kind);
            int accent = completed ? GREEN : 0xffbf665a;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.TOP);
            row.setPadding(dp(12), dp(11), dp(12), dp(11));
            row.setBackground(round(completed ? MINT_PALE : 0xfffff3f0, 10));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(8);
            TextView marker = text(completed ? "✓" : "·", 18, accent, true);
            marker.setGravity(Gravity.CENTER);
            row.addView(marker, new LinearLayout.LayoutParams(dp(24), dp(24)));
            LinearLayout detail = new LinearLayout(this);
            detail.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, -2, 1);
            detailParams.leftMargin = dp(8);
            row.addView(detail, detailParams);
            detail.addView(text(completed ? "做到了" : "这次没做", 14, INK, true));
            String note = record.reason == null ? "" : record.reason.trim();
            if (completed && "提前完成".equals(note)) note = "提前完成一次";
            if (!note.isEmpty()) {
                TextView noteView = text(note, 13, MUTED, false);
                noteView.setPadding(0, dp(3), 0, 0);
                detail.addView(noteView);
            }
            TextView time = text(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(record.startedAt), 11, accent, true);
            time.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            row.addView(time, new LinearLayout.LayoutParams(-2, dp(24)));
            history.addView(row, rowParams);
        }
        if (records.isEmpty()) {
            TextView empty = text("这一天还没有“" + rule.title + "”的记录", 13, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(32), 0, dp(32));
            history.addView(empty);
        }
    }

    private boolean sameDay(long timestamp, Calendar day) {
        Calendar value = Calendar.getInstance();
        value.setTimeInMillis(timestamp);
        return value.get(Calendar.YEAR) == day.get(Calendar.YEAR)
                && value.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR);
    }

    private final class CalendarButton extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CalendarButton() {
            super(ItemsActivity.this);
            paint.setColor(GREEN);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.8f));
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = getResources().getDisplayMetrics().density;
            float x = (getWidth() - dp(20)) / 2f;
            float y = (getHeight() - dp(20)) / 2f;
            canvas.save();
            canvas.translate(x, y);
            canvas.scale(scale, scale);
            canvas.drawRoundRect(new RectF(2, 4, 18, 19), 2, 2, paint);
            canvas.drawLine(2, 8, 18, 8, paint);
            canvas.drawLine(6, 2, 6, 6, paint);
            canvas.drawLine(14, 2, 14, 6, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 3; col++) canvas.drawCircle(6 + col * 4, 12 + row * 3, 0.8f, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            canvas.restore();
        }
    }

    private LinearLayout timeField(String title, int minute) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(12), dp(8), dp(12), dp(8));
        field.setBackground(round(MINT_PALE, 10));
        TextView label = text(title, 11, MUTED, false);
        TextView value = text(MainActivity.clock(minute), 17, INK, true);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(-1, -2);
        valueParams.topMargin = dp(2);
        field.addView(label);
        field.addView(value, valueParams);
        field.setContentDescription(title + " " + MainActivity.clock(minute));
        field.setClickable(true);
        return field;
    }

    private void chooseInterval(int[] interval, Button button) {
        List<Integer> saved = readSavedIntervals();
        List<Integer> favorites = readFavoriteIntervals();
        saved.sort((a, b) -> {
            int favoriteOrder = Boolean.compare(favorites.contains(b), favorites.contains(a));
            return favoriteOrder != 0 ? favoriteOrder : Integer.compare(a, b);
        });
        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setPadding(dp(18), dp(6), dp(18), dp(8));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(360)));
        scroll.addView(options);
        AlertDialog intervalDialog = new AlertDialog.Builder(dialogContext())
                .setTitle("提醒间隔")
                .setView(scroll)
                .setNegativeButton("管理自定义", (dialog, which) -> manageSavedIntervals(interval, button))
                .setNeutralButton("自定义", (dialog, which) -> showCustomIntervalPicker(interval, button))
                .setPositiveButton("完成", null)
                .create();
        if (!favorites.isEmpty()) {
            addIntervalSection(options, "常用时间");
            for (int value : saved) {
                if (favorites.contains(value)) addIntervalOption(options, intervalDialog, interval, button,
                        value, interval[0] == value, true);
            }
        }
        boolean hasCustom = false;
        for (int value : saved) hasCustom |= !favorites.contains(value);
        if (hasCustom) {
            addIntervalSection(options, "自定义时间");
            for (int value : saved) {
                if (!favorites.contains(value)) addIntervalOption(options, intervalDialog, interval, button,
                        value, interval[0] == value, false);
            }
        }
        addIntervalSection(options, "快捷间隔");
        for (int value : new int[]{5, 10, 15, 20, 30, 45, 60, 90}) {
            int seconds = value * 60;
            addIntervalOption(options, intervalDialog, interval, button, seconds, interval[0] == seconds, false);
        }
        showStyledDialog(intervalDialog);
    }

    private void addIntervalSection(LinearLayout parent, String title) {
        TextView label = text(title, 12, MUTED, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(8);
        params.bottomMargin = dp(4);
        parent.addView(label, params);
    }

    private void addIntervalOption(LinearLayout parent, AlertDialog dialog, int[] interval,
                                   Button button, int value, boolean selected, boolean favorite) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setBackground(round(selected ? MINT_LIGHT : Color.WHITE, 11));
        TextView title = text(intervalText(value), 15, selected ? GREEN : INK, selected);
        row.addView(title, new LinearLayout.LayoutParams(0, dp(46), 1));
        if (favorite) row.addView(text("常用", 11, 0xffb28742, true));
        if (selected) row.addView(text("✓", 16, GREEN, true));
        row.setOnClickListener(v -> {
            setInterval(interval, button, value);
            dialog.dismiss();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(46));
        params.bottomMargin = dp(4);
        parent.addView(row, params);
    }

    private void showCustomIntervalPicker(int[] interval, Button button) {
        LinearLayout fields = new LinearLayout(this);
        fields.setGravity(Gravity.CENTER);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        fields.setPadding(dp(12), dp(8), dp(12), dp(8));
        fields.setBackground(round(MINT_PALE, 12));
        NumberPicker hours = new NumberPicker(this);
        hours.setMinValue(0);
        hours.setMaxValue(23);
        hours.setWrapSelectorWheel(false);
        hours.setValue(interval[0] / 3600);
        NumberPicker minutes = new NumberPicker(this);
        minutes.setMinValue(0);
        minutes.setMaxValue(59);
        minutes.setWrapSelectorWheel(false);
        minutes.setValue(interval[0] / 60 % 60);
        NumberPicker seconds = new NumberPicker(this);
        seconds.setMinValue(0);
        seconds.setMaxValue(59);
        seconds.setWrapSelectorWheel(false);
        seconds.setValue(interval[0] % 60);
        addIntervalWheel(fields, hours, "小时");
        addIntervalWheel(fields, minutes, "分钟");
        addIntervalWheel(fields, seconds, "秒");
        AlertDialog pickerDialog = new AlertDialog.Builder(dialogContext()).setTitle("自定义提醒间隔")
                .setMessage("选择提醒间隔")
                .setView(fields).setNegativeButton("取消", null)
                .setNeutralButton("收藏置顶", null).setPositiveButton("确定", null).create();
        pickerDialog.setOnShowListener(ignored -> {
            pickerDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int total = hours.getValue() * 3600 + minutes.getValue() * 60 + seconds.getValue();
                if (total <= 0) {
                    Toast.makeText(this, "提醒间隔至少为 1 秒", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!addSavedInterval(total)) {
                    Toast.makeText(this, "保存自定义时间失败，请重试", Toast.LENGTH_SHORT).show();
                    return;
                }
                setInterval(interval, button, total);
                Toast.makeText(this, "已保存自定义时间，下次可在间隔选项顶部选择", Toast.LENGTH_SHORT).show();
                pickerDialog.dismiss();
            });
            pickerDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                int total = hours.getValue() * 3600 + minutes.getValue() * 60 + seconds.getValue();
                if (total <= 0) {
                    Toast.makeText(this, "提醒间隔至少为 1 秒", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!addSavedInterval(total)) {
                    Toast.makeText(this, "保存自定义时间失败，请重试", Toast.LENGTH_SHORT).show();
                    return;
                }
                List<Integer> favorites = readFavoriteIntervals();
                if (!favorites.contains(total)) favorites.add(total);
                writeIntervalList("favorite_item_intervals_seconds", favorites);
                setInterval(interval, button, total);
                pickerDialog.dismiss();
            });
        });
        showStyledDialog(pickerDialog);
    }

    private void manageSavedIntervals(int[] interval, Button button) {
        List<Integer> saved = readSavedIntervals();
        saved.sort(Integer::compareTo);
        if (saved.isEmpty()) {
            Toast.makeText(this, "还没有自定义时间", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(16), dp(8), dp(16), dp(8));
        ScrollView managerScroll = new ScrollView(this);
        managerScroll.setFillViewport(true);
        managerScroll.addView(rows);
        AlertDialog manager = new AlertDialog.Builder(dialogContext()).setTitle("管理自定义时间")
                .setView(managerScroll).setPositiveButton("完成", null).create();
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            rows.removeAllViews();
            List<Integer> current = readSavedIntervals();
            current.sort(Integer::compareTo);
            for (int value : current) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView label = text(intervalText(value), 15, INK, true);
                row.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1));
                TextView favorite = text(readFavoriteIntervals().contains(value) ? "★" : "☆", 22,
                        readFavoriteIntervals().contains(value) ? 0xffd59b28 : MUTED, true);
                favorite.setGravity(Gravity.CENTER);
                favorite.setContentDescription("收藏到顶部");
                favorite.setOnClickListener(v -> {
                    List<Integer> updated = readFavoriteIntervals();
                    if (updated.contains(value)) updated.remove(Integer.valueOf(value));
                    else updated.add(value);
                    writeIntervalList("favorite_item_intervals_seconds", updated);
                    refresh[0].run();
                });
                row.addView(favorite, new LinearLayout.LayoutParams(dp(44), dp(48)));
                TextView delete = text("×", 22, 0xffbd5147, false);
                delete.setGravity(Gravity.CENTER);
                delete.setContentDescription("删除自定义时间");
                delete.setOnClickListener(v -> showStyledDialog(new AlertDialog.Builder(dialogContext())
                        .setMessage("删除 " + intervalText(value) + "？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (d, w) -> {
                            List<Integer> updated = readSavedIntervals();
                            updated.remove(Integer.valueOf(value));
                            writeIntervalList("custom_item_intervals_seconds", updated);
                            List<Integer> starred = readFavoriteIntervals();
                            starred.remove(Integer.valueOf(value));
                            writeIntervalList("favorite_item_intervals_seconds", starred);
                            refresh[0].run();
                            if (interval[0] == value) setInterval(interval, button, 30 * 60);
                        }).create()));
                row.addView(delete, new LinearLayout.LayoutParams(dp(40), dp(48)));
                rows.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
            }
        };
        manager.setOnShowListener(ignored -> refresh[0].run());
        showStyledDialog(manager);
    }

    private List<Integer> readSavedIntervals() {
        return readIntervalList("custom_item_intervals_seconds", "custom_item_intervals", 60);
    }

    private List<Integer> readFavoriteIntervals() {
        return readIntervalList("favorite_item_intervals_seconds", "favorite_item_intervals", 60);
    }

    private List<Integer> readIntervalList(String key, String legacyKey, int legacyMultiplier) {
        java.util.Set<String> stored = ReminderSettings.prefs(this).getStringSet(key, null);
        if (stored == null) {
            java.util.Set<String> legacy = ReminderSettings.prefs(this).getStringSet(legacyKey, java.util.Collections.emptySet());
            ArrayList<Integer> migrated = new ArrayList<>();
            for (String value : legacy) {
                try { migrated.add(Integer.parseInt(value) * legacyMultiplier); }
                catch (NumberFormatException ignored) { }
            }
            if (!migrated.isEmpty()) writeIntervalList(key, migrated);
            stored = ReminderSettings.prefs(this).getStringSet(key, java.util.Collections.emptySet());
        }
        ArrayList<Integer> values = new ArrayList<>();
        for (String value : new java.util.HashSet<>(stored)) {
            try {
                int seconds = Integer.parseInt(value);
                if (seconds > 0 && seconds <= 86399 && !values.contains(seconds)) values.add(seconds);
            } catch (NumberFormatException ignored) { }
        }
        return values;
    }

    private boolean writeIntervalList(String key, List<Integer> values) {
        java.util.Set<String> stored = new java.util.HashSet<>();
        for (int value : values) stored.add(Integer.toString(value));
        return ReminderSettings.prefs(this).edit().putStringSet(key, stored).commit();
    }

    private boolean addSavedInterval(int value) {
        List<Integer> saved = readSavedIntervals();
        if (!saved.contains(value)) saved.add(value);
        return writeIntervalList("custom_item_intervals_seconds", saved)
                && readSavedIntervals().contains(value);
    }

    private void setInterval(int[] interval, Button button, int value) {
        interval[0] = value;
        button.setText("提醒间隔  " + intervalText(value));
    }

    private void addIntervalWheel(LinearLayout parent, NumberPicker picker, String unit) {
        styleWheel(picker);
        LinearLayout column = new LinearLayout(this);
        column.setGravity(Gravity.CENTER);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(picker, new LinearLayout.LayoutParams(dp(76), dp(112)));
        TextView label = text(unit, 13, MUTED, true);
        label.setGravity(Gravity.CENTER);
        column.addView(label, new LinearLayout.LayoutParams(-1, dp(24)));
        parent.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void showTimePointPicker(ArrayList<Integer> timePoints, LinearLayout rows) {
        Calendar initial = Calendar.getInstance();
        initial.add(Calendar.MINUTE, 1);
        initial.set(Calendar.SECOND, 0);
        initial.set(Calendar.MILLISECOND, 0);

        LinearLayout fields = new LinearLayout(this);
        fields.setGravity(Gravity.CENTER);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        fields.setPadding(dp(12), dp(8), dp(12), dp(8));
        fields.setBackground(round(MINT_PALE, 12));
        NumberPicker hours = timePointWheel(0, 23, initial.get(Calendar.HOUR_OF_DAY));
        NumberPicker minutes = timePointWheel(0, 59, initial.get(Calendar.MINUTE));
        NumberPicker seconds = timePointWheel(0, 59, 0);
        addIntervalWheel(fields, hours, "时");
        addIntervalWheel(fields, minutes, "分");
        addIntervalWheel(fields, seconds, "秒");

        AlertDialog picker = new AlertDialog.Builder(dialogContext()).setTitle("选择提醒时间")
                .setView(fields).setNegativeButton("取消", null).setPositiveButton("添加", null).create();
        picker.setOnShowListener(ignored -> picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int point = hours.getValue() * 3600 + minutes.getValue() * 60 + seconds.getValue();
            if (timePoints.contains(point)) {
                Toast.makeText(this, "这个时间点已添加", Toast.LENGTH_SHORT).show();
                return;
            }
            timePoints.add(point);
            Collections.sort(timePoints);
            renderTimePoints(timePoints, rows);
            picker.dismiss();
        }));
        showStyledDialog(picker);
    }

    private NumberPicker timePointWheel(int min, int max, int value) {
        NumberPicker picker = new NumberPicker(this);
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setValue(value);
        picker.setWrapSelectorWheel(true);
        picker.setFormatter(number -> String.format(Locale.ROOT, "%02d", number));
        return picker;
    }

    private void renderTimePoints(List<Integer> timePoints, LinearLayout rows) {
        rows.removeAllViews();
        if (timePoints.isEmpty()) {
            TextView empty = text("尚未添加时间点", 13, MUTED, false);
            empty.setPadding(dp(4), dp(8), 0, dp(8));
            rows.addView(empty);
            return;
        }
        for (int point : timePoints) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), 0, dp(4), 0);
            row.setBackground(round(MINT_PALE, 8));
            TextView value = text(timePointText(point), 16, INK, true);
            row.addView(value, new LinearLayout.LayoutParams(0, dp(46), 1));
            Button remove = new Button(this);
            styleControlButton(remove);
            remove.setMinHeight(dp(40));
            remove.setText("×");
            remove.setTextSize(20);
            remove.setTextColor(MUTED);
            remove.setBackground(round(MINT_LIGHT, 10));
            remove.setContentDescription("删除时间点 " + timePointText(point));
            remove.setOnClickListener(v -> {
                timePoints.remove(Integer.valueOf(point));
                renderTimePoints(timePoints, rows);
            });
            row.addView(remove, new LinearLayout.LayoutParams(-2, dp(46)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(5);
            rows.addView(row, params);
        }
    }

    private String timePointText(int seconds) {
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }

    private String intervalText(int seconds) {
        int h = seconds / 3600, m = seconds / 60 % 60, s = seconds % 60;
        StringBuilder label = new StringBuilder("每 ");
        if (h > 0) label.append(h).append(" 小时 ");
        if (m > 0) label.append(m).append(" 分钟 ");
        if (s > 0 || (h == 0 && m == 0)) label.append(s).append(" 秒");
        return label.toString().trim();
    }

    private void addNavigation(LinearLayout screen) {
        LinearLayout nav = BottomNavigationBar.create(this, BottomNavigationBar.ITEMS,
                () -> { finish(); overridePendingTransition(0, 0); },
                null,
                () -> { startActivity(new Intent(this, CompanionLandingActivity.class)); overridePendingTransition(0, 0); finish(); },
                () -> { startActivity(new Intent(this, StatsActivity.class)); overridePendingTransition(0, 0); finish(); });
        screen.addView(nav, new LinearLayout.LayoutParams(-1, dp(60)));
    }

    private Context dialogContext() {
        return new ContextThemeWrapper(this, R.style.ItemsDialogTheme);
    }

    private void showStyledDialog(AlertDialog dialog) {
        showStyledDialog(dialog, Math.min(getResources().getDisplayMetrics().widthPixels - dp(36), dp(480)));
    }

    private void showStyledDialog(AlertDialog dialog, int width) {
        dialog.show();
        styleDialog(dialog, width);
    }

    private void styleDialog(AlertDialog dialog, int width) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(round(Color.WHITE, 20));
            window.setDimAmount(0.34f);
            window.setLayout(width, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(INK);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(MUTED);
            message.setLineSpacing(dp(2), 1f);
        }
        for (int buttonId : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
                AlertDialog.BUTTON_NEUTRAL}) {
            android.widget.Button button = dialog.getButton(buttonId);
            if (button == null) continue;
            button.setAllCaps(false);
            button.setTextSize(14);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setTextColor(buttonId == AlertDialog.BUTTON_POSITIVE ? GREEN : MUTED);
        }
        if (dialog.getListView() != null) {
            dialog.getListView().setBackgroundColor(UiPalette.SURFACE);
            dialog.getListView().setDivider(new ColorDrawable(LINE));
            dialog.getListView().setDividerHeight(dp(1));
            dialog.getListView().setSelector(new ColorDrawable(MINT_LIGHT));
        }
    }

    private EditText styledInput(int inputType) {
        EditText input = new EditText(dialogContext());
        input.setInputType(inputType);
        input.setTextColor(INK);
        input.setHintTextColor(MUTED);
        input.setPadding(dp(14), dp(10), dp(14), dp(10));
        input.setMinHeight(dp(48));
        input.setBackground(round(MINT_PALE, 10));
        return input;
    }

    private TextView editorLabel(String value, boolean separated) {
        TextView label = text(value, 13, MUTED, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = separated ? dp(14) : 0;
        params.bottomMargin = dp(6);
        label.setLayoutParams(params);
        return label;
    }

    private TextView segmentOption(String value, boolean selected) {
        TextView option = text(value, 13, selected ? INK : MUTED, selected);
        option.setGravity(Gravity.CENTER);
        option.setPadding(dp(8), dp(6), dp(8), dp(6));
        option.setBackground(round(selected ? Color.WHITE : Color.TRANSPARENT, 10));
        return option;
    }

    private void setSegmentOptionSelected(TextView option, boolean selected) {
        option.setTextColor(selected ? INK : MUTED);
        option.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        option.setBackground(round(selected ? Color.WHITE : Color.TRANSPARENT, 10));
    }

    private void styleControlButton(Button button) {
        button.setAllCaps(false);
        button.setTextColor(GREEN);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(46));
        button.setPadding(dp(14), dp(8), dp(14), dp(8));
        button.setBackground(round(MINT_LIGHT, 10));
    }

    private void styleWheel(NumberPicker picker) {
        picker.setBackground(round(MINT_PALE, 10));
        View child = picker.getChildAt(0);
        if (child instanceof EditText) {
            EditText value = (EditText) child;
            value.setTextColor(INK);
            value.setTextSize(20);
            value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            value.setBackgroundColor(Color.TRANSPARENT);
        }
    }

    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(UiPalette.surface(color)); d.setCornerRadius(dp(radius)); if (color == Color.WHITE) d.setStroke(dp(1), UiPalette.BORDER); return d; }
    private GradientDrawable circle(int color) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setShape(GradientDrawable.OVAL); return d; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
