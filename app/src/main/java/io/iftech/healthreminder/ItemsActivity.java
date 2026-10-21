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
import android.widget.ImageView;
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
    private int GREEN = UiPalette.ACCENT;
    private int MINT = UiPalette.ACCENT;
    private int MINT_LIGHT = UiPalette.SOFT;
    private int MINT_PALE = UiPalette.PALE;
    private int LINE = UiPalette.BORDER;
    private int INK = UiPalette.INK;
    private int MUTED = UiPalette.MUTED;
    private int PAPER = UiPalette.PAGE;
    private LinearLayout list;
    private boolean returnToHome;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        returnToHome = getIntent().getBooleanExtra("return_to_home", false);
        UiPalette.applyWindow(getWindow());
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
        addLabel.setBackground(round(MINT_LIGHT, 13));
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
        summary.setBackground(surface(UiPalette.headGradient(), 18, UiPalette.BORDER));
        summary.setElevation(dp(1));
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
            card.setPadding(dp(16), dp(15), dp(16), dp(14));
            card.setBackground(categorySurface(rule));
            card.setElevation(dp(2));
            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            LinearLayout titleLine = new LinearLayout(this);
            titleLine.setGravity(Gravity.CENTER_VERTICAL);
            ItemIconView marker = new ItemIconView(this);
            marker.setTextSize(15);
            marker.setTextColor(ruleInk(rule));
            marker.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            marker.setGravity(Gravity.CENTER);
            marker.setPadding(dp(7), dp(7), dp(7), dp(7));
            marker.bindIcon(rule.iconImage(), rule.badgeText());
            GradientDrawable markerBackground = round(ruleTint(rule), 22);
            markerBackground.setStroke(dp(1), ruleStroke(rule));
            marker.setBackground(markerBackground);
            titleLine.addView(marker, new LinearLayout.LayoutParams(dp(44), dp(44)));
            TextView itemTitle = text(rule.title, 16, INK, true);
            itemTitle.setLetterSpacing(0.01f);
            LinearLayout.LayoutParams itemTitleParams = new LinearLayout.LayoutParams(0, -2, 1);
            itemTitleParams.leftMargin = dp(12);
            titleLine.addView(itemTitle, itemTitleParams);
            TextView count = text(done >= rule.dailyTarget ? "已达标" : done + " / " + rule.dailyTarget,
                    10, ruleInk(rule), true);
            count.setGravity(Gravity.CENTER);
            count.setPadding(dp(9), dp(5), dp(9), dp(5));
            count.setBackground(round(ruleTint(rule), 10));
            titleLine.addView(count);
            details.addView(titleLine);
            ProgressBar itemProgress = progressBar(rule.dailyTarget, done, ruleInk(rule));
            LinearLayout.LayoutParams itemProgressParams = new LinearLayout.LayoutParams(-1, dp(4));
            itemProgressParams.topMargin = dp(11);
            details.addView(itemProgress, itemProgressParams);
            LinearLayout meta = new LinearLayout(this);
            meta.setGravity(Gravity.CENTER_VERTICAL);
            meta.setPadding(0, dp(10), 0, 0);
            TextView schedule = text(scheduleSummary(rule), 12, ruleInk(rule), false);
            schedule.setPadding(dp(9), dp(5), dp(9), dp(5));
            schedule.setBackground(round(ruleTint(rule), 10));
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
            empty.setBackground(surface(UiPalette.headGradient(), 16, UiPalette.BORDER));
            list.addView(empty);
        }
    }

    private ProgressBar progressBar(int target, int done) {
        return progressBar(target, done, MINT);
    }

    private ProgressBar progressBar(int target, int done, int tint) {
        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false);
        progress.setMax(Math.max(1, target));
        progress.setProgress(Math.min(Math.max(0, done), Math.max(1, target)));
        progress.setProgressTintList(ColorStateList.valueOf(tint));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        return progress;
    }

    /** 事项最终使用的颜色（自选优先，否则按标题自动归类）。 */
    private int ruleInk(ReminderSettings.Rule rule) {
        return UiPalette.categoryInk(UiPalette.colorKind(rule.color, rule.title));
    }

    private int ruleTint(ReminderSettings.Rule rule) {
        return UiPalette.categoryFill(UiPalette.colorKind(rule.color, rule.title));
    }

    private int ruleStroke(ReminderSettings.Rule rule) {
        return UiPalette.categoryStroke(UiPalette.colorKind(rule.color, rule.title));
    }

    private int categoryInk(String title) {
        return UiPalette.categoryInk(UiPalette.categoryKind(title));
    }

    private int categoryTint(String title) {
        return UiPalette.categoryFill(UiPalette.categoryKind(title));
    }

    private GradientDrawable categorySurface(ReminderSettings.Rule rule) {
        int tint = ruleTint(rule);
        GradientDrawable background;
        if (UiPalette.isDark()) {
            background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{UiPalette.blend(0xff0a251c, tint, 0.90f),
                            UiPalette.blend(0xff0a251c, tint, 0.55f),
                            UiPalette.blend(0xff0a251c, tint, 0.30f)});
        } else {
            background = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{UiPalette.blend(0xffffffff, tint, 0.88f),
                            UiPalette.blend(0xffffffff, tint, 0.55f),
                            UiPalette.blend(0xffffffff, tint, 0.22f)});
        }
        background.setCornerRadius(dp(20));
        background.setStroke(dp(1), UiPalette.hairline());
        return background;
    }

    private int categoryBorder(String title) {
        return UiPalette.categoryStroke(UiPalette.categoryKind(title));
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

        final String[] iconValue = {existing == null ? "" : existing.icon};
        LinearLayout iconRow = new LinearLayout(this);
        iconRow.setGravity(Gravity.CENTER_VERTICAL);
        iconRow.setPadding(0, dp(12), 0, 0);
        TextView iconLabel = text("图标", 13, MUTED, true);
        iconRow.addView(iconLabel, new LinearLayout.LayoutParams(0, -2, 1));

        final ItemIconView iconPreview = new ItemIconView(this);
        iconPreview.setTextSize(17);
        iconPreview.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        iconPreview.setTextColor(INK);
        iconPreview.setGravity(Gravity.CENTER);
        iconPreview.setPadding(dp(7), dp(7), dp(7), dp(7));
        final LinearLayout iconChoice = new LinearLayout(this);
        iconChoice.setGravity(Gravity.CENTER_VERTICAL);
        iconChoice.setPadding(dp(5), dp(4), dp(12), dp(4));
        iconChoice.setBackground(round(Color.WHITE, 12));
        iconChoice.addView(iconPreview, new LinearLayout.LayoutParams(dp(40), dp(40)));
        final TextView iconName = text("", 13, MUTED, true);
        iconName.setPadding(dp(10), 0, 0, 0);
        iconChoice.addView(iconName);
        iconRow.addView(iconChoice);
        form.addView(iconRow);
        final Runnable refreshIcon = () -> {
            String value = iconValue[0];
            iconPreview.bindIcon(value, value.isEmpty() ? "首" : value);
            iconPreview.setTextColor(INK);
            iconName.setText(ItemIconPicker.describe(value) + "  ›");
        };
        refreshIcon.run();
        iconChoice.setOnClickListener(v -> showStyledDialog(ItemIconPicker.build(dialogContext(), iconValue[0], picked -> {
            iconValue[0] = picked;
            refreshIcon.run();
        })));

        // 自选颜色：默认跟随名称自动配色，选了就以自选色为准。
        final int[] colorValue = {existing == null ? ReminderSettings.Rule.COLOR_AUTO : existing.color};
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setGravity(Gravity.CENTER_VERTICAL);
        colorRow.setPadding(0, dp(12), 0, 0);
        TextView colorLabel = text("颜色", 13, MUTED, true);
        colorRow.addView(colorLabel, new LinearLayout.LayoutParams(0, -2, 1));
        final TextView colorSwatch = new TextView(this);
        colorSwatch.setGravity(Gravity.CENTER);
        colorSwatch.setTextSize(15);
        colorSwatch.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        final LinearLayout colorChoice = new LinearLayout(this);
        colorChoice.setGravity(Gravity.CENTER_VERTICAL);
        colorChoice.setPadding(dp(5), dp(4), dp(12), dp(4));
        colorChoice.setBackground(round(Color.WHITE, 12));
        colorChoice.addView(colorSwatch, new LinearLayout.LayoutParams(dp(40), dp(40)));
        final TextView colorName = text("", 13, MUTED, true);
        colorName.setPadding(dp(10), 0, 0, 0);
        colorChoice.addView(colorName);
        colorRow.addView(colorChoice);
        form.addView(colorRow);
        final Runnable refreshColor = () -> {
            String title = name.getText().toString().trim();
            int kind = UiPalette.colorKind(colorValue[0], title);
            GradientDrawable swatch = new GradientDrawable();
            swatch.setShape(GradientDrawable.OVAL);
            swatch.setColor(UiPalette.categoryFill(kind));
            swatch.setStroke(dp(1), UiPalette.categoryStroke(kind));
            colorSwatch.setBackground(swatch);
            colorName.setText(ItemColorPicker.describe(colorValue[0]) + "  ›");
        };
        refreshColor.run();
        colorChoice.setOnClickListener(v -> showStyledDialog(ItemColorPicker.build(dialogContext(),
                colorValue[0], picked -> {
                    colorValue[0] = picked;
                    refreshColor.run();
                })));
        name.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence text, int start, int before, int count) { }
            @Override public void afterTextChanged(android.text.Editable text) { refreshColor.run(); }
        });

        form.addView(editorLabel("霸屏提醒语句", true));
        EditText reminderMessage = styledInput(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        reminderMessage.setHint("选填，留空使用事项默认文案");
        reminderMessage.setMinLines(2);
        reminderMessage.setMaxLines(4);
        reminderMessage.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(120)});
        reminderMessage.setText(existing == null ? "" : existing.reminderMessage);
        form.addView(reminderMessage);

        form.addView(editorLabel("霸屏页面", true));
        String[] artworkLabels = ReminderArtworkCatalog.labels();
        String[] artworkIds = new String[artworkLabels.length];
        for (int i = 0; i < artworkIds.length; i++) artworkIds[i] = ReminderArtworkCatalog.idAt(i);
        String[] artworkValue = {existing == null ? ReminderArtworkCatalog.DEFAULT : existing.overlayArtwork};
        LinearLayout artworkRow = new LinearLayout(this);
        artworkRow.setGravity(Gravity.CENTER_VERTICAL);
        artworkRow.setPadding(dp(8), dp(6), dp(10), dp(6));
        artworkRow.setBackground(round(Color.WHITE, 12));
        FrameLayout artworkPreview = new FrameLayout(this);
        artworkPreview.setBackground(round(MINT_PALE, 10));
        artworkRow.addView(artworkPreview, new LinearLayout.LayoutParams(dp(52), dp(58)));
        setArtworkPreview(artworkPreview, artworkValue[0]);
        Button artworkButton = new Button(this);
        styleControlButton(artworkButton);
        artworkButton.setText(ReminderArtworkCatalog.label(artworkValue[0]) + "  ›");
        artworkButton.setOnClickListener(v -> {
            LinearLayout options = new LinearLayout(dialogContext());
            options.setOrientation(LinearLayout.VERTICAL);
            options.setPadding(dp(16), dp(4), dp(16), dp(8));
            ScrollView optionScroll = new ScrollView(dialogContext());
            optionScroll.setVerticalScrollBarEnabled(false);
            for (int i = 0; i < artworkIds.length; i++) {
                final String optionId = artworkIds[i];
                LinearLayout option = new LinearLayout(dialogContext());
                option.setGravity(Gravity.CENTER_VERTICAL);
                option.setPadding(dp(8), dp(5), dp(12), dp(5));
                option.setBackground(round(optionId.equals(artworkValue[0]) ? MINT_PALE : Color.TRANSPARENT, 10));
                FrameLayout thumbnail = new FrameLayout(dialogContext());
                thumbnail.setBackground(round(Color.WHITE, 8));
                option.addView(thumbnail, new LinearLayout.LayoutParams(dp(48), dp(54)));
                setArtworkPreview(thumbnail, optionId);
                TextView optionLabel = text(artworkLabels[i], 15, INK, optionId.equals(artworkValue[0]));
                LinearLayout.LayoutParams optionLabelParams = new LinearLayout.LayoutParams(0, -2, 1);
                optionLabelParams.leftMargin = dp(14);
                option.addView(optionLabel, optionLabelParams);
                if (optionId.equals(artworkValue[0])) {
                    TextView selectedMark = text("✓", 17, GREEN, true);
                    option.addView(selectedMark);
                }
                option.setContentDescription("选择" + artworkLabels[i]);
                option.setOnClickListener(choice -> {
                    artworkValue[0] = optionId;
                    artworkButton.setText(ReminderArtworkCatalog.label(optionId) + "  ›");
                    setArtworkPreview(artworkPreview, optionId);
                    ((AlertDialog) choice.getTag()).dismiss();
                });
                options.addView(option, new LinearLayout.LayoutParams(-1, dp(64)));
            }
            int maxHeight = Math.min(dp(460), getResources().getDisplayMetrics().heightPixels - dp(220));
            optionScroll.addView(options, new ScrollView.LayoutParams(-1, -2));
            optionScroll.setLayoutParams(new LinearLayout.LayoutParams(-1, maxHeight));
            AlertDialog artworkDialog = new AlertDialog.Builder(dialogContext())
                    .setTitle("选择霸屏页面")
                    .setView(optionScroll)
                    .setNegativeButton("取消", null)
                    .create();
            for (int i = 0; i < options.getChildCount(); i++) {
                options.getChildAt(i).setTag(artworkDialog);
            }
            showStyledDialog(artworkDialog);
        });
        LinearLayout.LayoutParams artworkButtonParams = new LinearLayout.LayoutParams(0, dp(48), 1);
        artworkButtonParams.leftMargin = dp(10);
        artworkRow.addView(artworkButton, artworkButtonParams);
        form.addView(artworkRow, new LinearLayout.LayoutParams(-1, -2));


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
        from.setOnClickListener(v -> WheelPicker.time(dialogContext(), "开始时间", "确定",
                start[0] * 60, false, picked -> {
                    start[0] = Math.max(0, Math.min(24 * 60 - 1, picked / 60));
                    fromValue.setText(MainActivity.clock(start[0]));
                }).show());
        to.setOnClickListener(v -> WheelPicker.time(dialogContext(), "结束时间", "确定",
                end[0] * 60, false, picked -> {
                    end[0] = Math.max(0, Math.min(24 * 60 - 1, picked / 60));
                    toValue.setText(MainActivity.clock(end[0]));
                }).show());
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
        // 点击开关时不要让表单滚动/把焦点抢走，保持弹窗内容不动
        enabledSwitch.setFocusable(false);
        enabledSwitch.setFocusableInTouchMode(false);
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
                .setView(formScroll)
                .setNegativeButton("取消", (dialog, which) -> returnHomeAfterEditor())
                .setPositiveButton("保存", null);
        if (existing != null) builder.setNeutralButton("删除", (dialog, which) -> {
            ArrayList<ReminderSettings.Rule> updated = new ArrayList<>(ReminderSettings.rules(this));
            updated.removeIf(rule -> rule.id == id);
            ReminderSettings.saveRules(this, updated);
            renderItems();
            returnHomeAfterEditor();
        });
        AlertDialog editor = builder.create();
        editor.setOnCancelListener(dialog -> returnHomeAfterEditor());
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
            ReminderSettings.Rule rule = specificTimeMode[0]
                    ? ReminderSettings.Rule.withSpecificTimes(id, title, start[0], end[0], interval[0],
                    timePoints, target[0], remindAfterTarget[0], enabled[0])
                    : ReminderSettings.Rule.withIntervalSeconds(id, title, start[0], end[0], interval[0],
                    target[0], remindAfterTarget[0], enabled[0]);
            rule = rule.withReminderMessage(reminderMessage.getText().toString())
                    .withIcon(iconValue[0])
                    .withColor(colorValue[0])
                    .withOverlayArtwork(artworkValue[0]);
            // 原位替换，保持事项在列表里的顺序不变（新增才追加到末尾）
            int replaceIndex = -1;
            for (int i = 0; i < updated.size(); i++) {
                if (updated.get(i).id == id) { replaceIndex = i; break; }
            }
            if (replaceIndex >= 0) updated.set(replaceIndex, rule);
            else updated.add(rule);
            ReminderSettings.saveRules(this, updated);
            renderItems();
            editor.dismiss();
            returnHomeAfterEditor();
        }));
        showStyledDialog(editor);
        if (editor.getWindow() != null) {
            editor.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void returnHomeAfterEditor() {
        if (!returnToHome) return;
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finish();
        overridePendingTransition(0, 0);
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
        View.OnClickListener chooseDate = v -> WheelPicker.date(dialogContext(), "选择日期", selectedDay, picked -> {
            selectedDay.set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH),
                    picked.get(Calendar.DAY_OF_MONTH));
            refresh.run();
        }).show();
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
            int accent = completed ? GREEN : 0xffb35648;
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

    /** 提醒间隔：快捷胶囊 + 「时 / 分 / 秒」滚轮，和霸屏页「临时有事」的滚轮同一套操作。 */
    private void chooseInterval(final int[] interval, final Button button) {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(4), dp(16), dp(4));

        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setOrientation(LinearLayout.HORIZONTAL);
        final AlertDialog[] holder = new AlertDialog[1];
        for (int minutes : new int[]{5, 10, 15, 30}) {
            TextView chip = text(minutes + " 分钟", 13, GREEN, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(0, dp(8), 0, dp(8));
            chip.setBackground(round(MINT_LIGHT, 11));
            final int seconds = minutes * 60;
            chip.setOnClickListener(v -> {
                setInterval(interval, button, seconds);
                if (holder[0] != null) holder[0].dismiss();
            });
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, -2, 1);
            chipParams.rightMargin = dp(6);
            quickRow.addView(chip, chipParams);
        }
        content.addView(quickRow);

        final TextView preview = text(intervalText(interval[0]), 20, UiPalette.COUNTDOWN_INK, true);
        preview.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2);
        previewParams.topMargin = dp(14);
        previewParams.bottomMargin = dp(8);
        content.addView(preview, previewParams);

        LinearLayout wheels = new LinearLayout(this);
        wheels.setGravity(Gravity.CENTER);
        wheels.setOrientation(LinearLayout.HORIZONTAL);
        wheels.setPadding(dp(6), dp(8), dp(6), dp(8));
        wheels.setBackground(round(MINT_PALE, 14));
        NumberPicker hours = WheelPicker.wheel(this, 0, 23, interval[0] / 3600, true);
        NumberPicker minutes = WheelPicker.wheel(this, 0, 59, interval[0] / 60 % 60, true);
        NumberPicker seconds = WheelPicker.wheel(this, 0, 59, interval[0] % 60, true);
        addIntervalWheel(wheels, hours, "小时");
        addIntervalWheel(wheels, minutes, "分钟");
        addIntervalWheel(wheels, seconds, "秒");
        content.addView(wheels, new LinearLayout.LayoutParams(-1, -2));

        final Runnable refresh = () -> preview.setText(intervalText(
                hours.getValue() * 3600 + minutes.getValue() * 60 + seconds.getValue()));
        NumberPicker.OnValueChangeListener changed = (picker, oldValue, newValue) -> refresh.run();
        hours.setOnValueChangedListener(changed);
        minutes.setOnValueChangedListener(changed);
        seconds.setOnValueChangedListener(changed);

        AlertDialog dialog = new AlertDialog.Builder(dialogContext())
                .setTitle("提醒间隔")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        holder[0] = dialog;
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int total = hours.getValue() * 3600 + minutes.getValue() * 60 + seconds.getValue();
                if (total <= 0) {
                    Toast.makeText(this, "提醒间隔至少为 1 秒", Toast.LENGTH_SHORT).show();
                    return;
                }
                setInterval(interval, button, total);
                dialog.dismiss();
            });
            styleDialog(dialog, Math.min(getResources().getDisplayMetrics().widthPixels - dp(36), dp(480)));
        });
        dialog.show();
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
        if (favorite) row.addView(text("常用", 11, UiPalette.WARN, true));
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
        NumberPicker hours = WheelPicker.wheel(this, 0, 23, interval[0] / 3600, true);
        NumberPicker minutes = WheelPicker.wheel(this, 0, 59, interval[0] / 60 % 60, true);
        NumberPicker seconds = WheelPicker.wheel(this, 0, 59, interval[0] % 60, true);
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
                        readFavoriteIntervals().contains(value) ? UiPalette.WARN : MUTED, true);
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
                TextView delete = text("×", 22, 0xffc85a4c, false);
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
        return WheelPicker.wheel(this, min, max, value, true);
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
            row.setBackground(round(MINT_PALE, 10));
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
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, dp(64));
        navParams.leftMargin = dp(14);
        navParams.rightMargin = dp(14);
        navParams.bottomMargin = dp(10);
        screen.addView(nav, navParams);
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
        input.setBackground(UiPalette.inputBackground(this, MINT_PALE, 10));
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
        WheelPicker.style(picker);
    }

    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(UiPalette.surface(color)); d.setCornerRadius(dp(radius)); if (color == Color.WHITE) d.setStroke(dp(1), UiPalette.BORDER); return d; }

    private void setArtworkPreview(FrameLayout preview, String artworkId) {
        preview.removeAllViews();
        int drawable = ReminderArtworkCatalog.drawable(artworkId);
        ImageView image = new ImageView(this);
        image.setImageResource(drawable);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        preview.addView(image, new FrameLayout.LayoutParams(-1, -1));
    }
    private GradientDrawable circle(int color) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setShape(GradientDrawable.OVAL); return d; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
