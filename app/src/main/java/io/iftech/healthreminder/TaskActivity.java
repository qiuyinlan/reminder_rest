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
import android.widget.HorizontalScrollView;
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
    private boolean completedExpanded;
    private boolean futureExpanded;
    private android.util.SparseIntArray subtaskScrollPositions = new android.util.SparseIntArray();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());
        android.widget.FrameLayout shell = new android.widget.FrameLayout(this);
        shell.setBackgroundColor(UiPalette.PAGE);
        setContentView(shell);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(UiPalette.page());
        shell.addView(root, new android.widget.FrameLayout.LayoutParams(-1, -1));

        root.addView(buildHeader(), new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(16), dp(4), dp(16), dp(96));
        scroll.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));


        LinearLayout nav = BottomNavigationBar.create(this, BottomNavigationBar.TASKS,
                () -> { startActivity(new Intent(this, MainActivity.class)); overridePendingTransition(0, 0); finish(); },
                null,
                () -> { startActivity(new Intent(this, CardActivity.class)); overridePendingTransition(0, 0); finish(); },
                () -> { startActivity(new Intent(this, ItemsActivity.class)); overridePendingTransition(0, 0); finish(); },
                () -> { startActivity(new Intent(this, CompanionLandingActivity.class)); overridePendingTransition(0, 0); finish(); },
                () -> { startActivity(new Intent(this, StatsActivity.class)); overridePendingTransition(0, 0); finish(); });
        LinearLayout.LayoutParams navParams = new LinearLayout.LayoutParams(-1, dp(64));
        navParams.leftMargin = dp(14);
        navParams.rightMargin = dp(14);
        navParams.bottomMargin = dp(10);
        root.addView(nav, navParams);

        TextView fab = FormKit.fab(this, this::quickAdd);
        android.widget.FrameLayout.LayoutParams fabParams =
                new android.widget.FrameLayout.LayoutParams(dp(56), dp(56));
        fabParams.gravity = Gravity.BOTTOM | Gravity.END;
        fabParams.rightMargin = dp(20);
        fabParams.bottomMargin = dp(86);
        shell.addView(fab, fabParams);
    }

    /** 右下角加号：只问一句名字，其他拿默认值。 */
    private void quickAdd() {
        final EditText input = FormKit.textInput(this, "任务名字", "");
        final int[] categoryId = {0};
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(10), dp(18), dp(0));
        form.addView(input, new LinearLayout.LayoutParams(-1, -2));

        // 分类选择行
        final TextView[] catBtnRef = {null};
        LinearLayout catRow = new LinearLayout(this);
        catRow.setOrientation(LinearLayout.HORIZONTAL);
        catRow.setGravity(Gravity.CENTER_VERTICAL);
        catRow.setPadding(0, dp(10), 0, 0);
        TextView catLabel = text("分类", 12, UiPalette.MUTED, false);
        catRow.addView(catLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        final TextView catBtn = CategoryPicker.chip(this, "默认");
        catBtnRef[0] = catBtn;
        catBtn.setOnClickListener(v -> pickCategory(categoryId, catBtn));
        catRow.addView(catBtn);
        form.addView(catRow, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("新任务")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String title = input.getText().toString().trim();
                if (title.isEmpty()) {
                    Toast.makeText(this, "先写个名字", Toast.LENGTH_SHORT).show();
                    return;
                }
                ArrayList<Integer> points = new ArrayList<>();
                points.add(9 * 3600);
                ReminderSettings.Rule rule = ReminderSettings.Rule.withSpecificTimes(
                                ReminderSettings.nextRuleId(this), title, 9 * 60, 9 * 60, 24 * 3600,
                                points, 1, true, false)
                        .withModule(ReminderSettings.MODULE_TASK)
                        .withCategory(categoryId[0]);
                ReminderSettings.upsertRule(this, rule);
                dialog.dismiss();
                reload();
            });
        });
        FormKit.showStyled(this, dialog);
    }

    private void pickCategory(final int[] selectedId, final TextView display) {
        CategoryPicker.show(this, selectedId[0], cat -> {
            selectedId[0] = cat.id;
            CategoryPicker.updateChip(display, cat.name);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        reload();
        int editId = getIntent().getIntExtra(EXTRA_EDIT_RULE_ID, -1);
        if (editId >= 0) {
            getIntent().removeExtra(EXTRA_EDIT_RULE_ID);
            ReminderSettings.Rule rule = ReminderSettings.ruleById(this, editId);
            if (rule != null) listContainer.post(() -> showEditor(rule));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        subtaskScrollPositions.clear();
    }

    static final String EXTRA_EDIT_RULE_ID = "edit_rule_id";

    // ---------------------------------------------------------------- 顶部

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(16), dp(20), dp(8));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("任务", 25, UiPalette.INK, true);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView more = text("⋯", 20, UiPalette.MUTED, true);
        more.setGravity(Gravity.CENTER);
        more.setPadding(dp(12), dp(2), dp(4), dp(2));
        more.setContentDescription("更多");
        more.setOnClickListener(v -> showMoreMenu());
        row.addView(more);
        header.addView(row);
        return header;
    }

    private void showMoreMenu() {
        FormKit.options(this, null, new String[]{"总览提醒", "重置今日", "清空全部任务"}, 2, which -> {
            if (which == 0) showOverviewEditor();
            else if (which == 1) confirmResetToday();
            else confirmClearAll();
        });
    }

    // ---------------------------------------------------------------- 列表

    private void reload() {
        // 清除子任务滚动位置，让每次都能自动滚动到第一个未完成
        subtaskScrollPositions.clear();
        
        List<ReminderSettings.Rule> tasks = Tasks.today(this);
        long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
        
        // 按分类分组
        List<TaskCategories.Category> cats = TaskCategories.all(this);
        java.util.Map<Integer, List<ReminderSettings.Rule>> byCat = new java.util.HashMap<>();
        java.util.Map<Integer, List<ReminderSettings.Rule>> doneByCat = new java.util.HashMap<>();
        for (TaskCategories.Category cat : cats) {
            byCat.put(cat.id, new ArrayList<>());
            doneByCat.put(cat.id, new ArrayList<>());
        }
        
        for (ReminderSettings.Rule task : tasks) {
            // 分类被删掉或数据异常时，退回默认分类，不能让任务凭空消失
            int catId = byCat.containsKey(task.categoryId) ? task.categoryId : 0;
            if (ReminderRecord.completedOn(this, ReminderSettings.MODULE_TASK, task.title, dayStart)) {
                List<ReminderSettings.Rule> list = doneByCat.get(catId);
                if (list != null) list.add(task);
            } else {
                List<ReminderSettings.Rule> list = byCat.get(catId);
                if (list != null) list.add(task);
            }
        }
        
        listContainer.removeAllViews();
        listContainer.addView(buildOverviewCard());

        if (tasks.isEmpty()) {
            listContainer.addView(buildHabitSection(dayStart));
            return;
        }
        
        // 按分类渲染（只有一个分类时不显示分组标题）
        boolean showHeaders = cats.size() > 1;
        for (TaskCategories.Category cat : cats) {
            List<ReminderSettings.Rule> pending = byCat.get(cat.id);
            List<ReminderSettings.Rule> done = doneByCat.get(cat.id);
            if (pending != null && !pending.isEmpty()) {
                if (showHeaders) {
                    listContainer.addView(buildCategoryHeader(cat, pending.size() + (done != null ? done.size() : 0)));
                }
                if (!showHeaders || cat.expanded) {
                    for (ReminderSettings.Rule task : pending) {
                        listContainer.addView(buildRow(task, dayStart));
                    }
                }
            }
        }
        
        // 未分类的其他任务（之后/已完成）
        List<ReminderSettings.Rule> future = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (ReminderSettings.Rule task : Tasks.all(this)) {
            if (!Tasks.isForToday(task, now)) future.add(task);
        }
        if (!future.isEmpty()) {
            listContainer.addView(buildFutureSection(future));
        }
        
        List<ReminderSettings.Rule> allDone = new ArrayList<>();
        for (TaskCategories.Category cat : cats) {
            List<ReminderSettings.Rule> done = doneByCat.get(cat.id);
            if (done != null) allDone.addAll(done);
        }
        if (!allDone.isEmpty()) {
            listContainer.addView(buildCompletedSection(allDone, dayStart));
        }

        // 微习惯板块：固定放在页面最下面
        listContainer.addView(buildHabitSection(dayStart));
    }

    // ------------------------------------------------------------ 微习惯

    private View buildHabitSection(long dayStart) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        section.setPadding(0, dp(18), 0, dp(8));

        // 顶部汇总卡片
        LinearLayout summaryCard = new LinearLayout(this);
        summaryCard.setOrientation(LinearLayout.HORIZONTAL);
        summaryCard.setGravity(Gravity.CENTER_VERTICAL);
        summaryCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        GradientDrawable summaryBg = new GradientDrawable();
        summaryBg.setColor(UiPalette.PALE);
        summaryBg.setCornerRadius(dp(16));
        summaryCard.setBackground(summaryBg);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(-1, -2);
        summaryParams.bottomMargin = dp(12);
        section.addView(summaryCard, summaryParams);

        TextView title = text("微习惯", 18, UiPalette.INK, true);
        summaryCard.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        // 圆形进度环
        int completed = Habits.countCompletedToday(this, dayStart);
        List<Habits.Habit> todayHabits = Habits.todayScheduled(this);
        int total = todayHabits.size();
        int percent = total > 0 ? (completed * 100 / total) : 0;

        TextView progressRing = new TextView(this);
        progressRing.setText(percent + "%");
        progressRing.setTextSize(18);
        progressRing.setTypeface(null, Typeface.BOLD);
        progressRing.setTextColor(UiPalette.INK);
        progressRing.setGravity(Gravity.CENTER);
        progressRing.setBackground(FormKit.round(this, Color.WHITE, 24));
        progressRing.setMinimumWidth(dp(48));
        progressRing.setMinimumHeight(dp(48));
        progressRing.setPadding(dp(10), dp(12), dp(10), dp(10));
        summaryCard.addView(progressRing);

        // 今日习惯列表标题
        LinearLayout listHeader = new LinearLayout(this);
        listHeader.setOrientation(LinearLayout.HORIZONTAL);
        listHeader.setGravity(Gravity.CENTER_VERTICAL);
        listHeader.setPadding(dp(4), dp(12), dp(4), dp(8));
        TextView listTitle = text("今日习惯", 15, UiPalette.INK, true);
        listHeader.addView(listTitle, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView count = text(completed + "/" + total + " 个已完成", 12, UiPalette.MUTED, false);
        listHeader.addView(count);
        section.addView(listHeader);

        // 添加按钮
        TextView addBtn = text("+ 添加", 13, UiPalette.ACCENT, true);
        addBtn.setPadding(dp(10), dp(6), dp(10), dp(6));
        addBtn.setOnClickListener(v -> addHabit());
        listHeader.addView(addBtn);

        // 习惯列表
        if (todayHabits.isEmpty()) {
            TextView empty = text("今天没有要做的习惯", 12, UiPalette.MUTED, false);
            empty.setPadding(dp(4), dp(4), dp(4), dp(10));
            section.addView(empty);
        } else {
            for (Habits.Habit habit : todayHabits) {
                section.addView(buildHabitRow(habit, dayStart));
            }
        }

        return section;
    }

    private void addHabit() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(18), dp(20), dp(4));

        TextView heading = text("新习惯", 18, UiPalette.INK, true);
        heading.setGravity(Gravity.CENTER);
        box.addView(heading, new LinearLayout.LayoutParams(-1, -2));

        TextView sub = text("默认每天都要做，之后点条目就能改频率", 12, UiPalette.MUTED, false);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-1, -2);
        subParams.topMargin = dp(6);
        subParams.bottomMargin = dp(16);
        box.addView(sub, subParams);

        final EditText input = FormKit.textInput(this, "比如：打八段锦、颈椎训练", "");
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setTextSize(15);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String title = input.getText().toString().trim();
                if (title.isEmpty()) {
                    Toast.makeText(this, "先写个名字", Toast.LENGTH_SHORT).show();
                    return;
                }
                Habits.add(this, title);
                dialog.dismiss();
                reload();
            });
        });
        FormKit.showStyled(this, dialog);
    }

    private View buildHabitRow(Habits.Habit habit, long dayStart) {
        boolean done = Habits.isCompletedToday(this, habit.id, dayStart);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(52));
        row.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setCornerRadius(dp(12));
        rowBg.setColor(Color.WHITE);
        rowBg.setStroke(dp(1), UiPalette.BORDER);
        row.setBackground(rowBg);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(8);
        row.setLayoutParams(rowParams);

        // Checkbox
        GradientDrawable cbShape = new GradientDrawable();
        cbShape.setCornerRadius(dp(6));
        cbShape.setColor(done ? UiPalette.ACCENT : Color.TRANSPARENT);
        if (!done) cbShape.setStroke(dp(1), 0xff9ca3af);
        TextView cb = text(done ? "✓" : "", 12, done ? Color.WHITE : 0xff9ca3af, true);
        cb.setGravity(Gravity.CENTER);
        cb.setBackground(cbShape);
        LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        row.addView(cb, cbParams);
        cb.setOnClickListener(v -> {
            Habits.toggleCompletion(this, habit.id, dayStart, !done);
            reload();
        });

        // 标题
        TextView title = text(habit.title, 15, done ? UiPalette.MUTED : UiPalette.INK, false);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (done) title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = dp(12);
        row.addView(title, titleParams);

        // 7 个圆点
        int[] bits = {1, 2, 4, 8, 16, 32, 64};
        LinearLayout dotsRow = new LinearLayout(this);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int bit : bits) {
            TextView dot = new TextView(this);
            dot.setText(Habits.getDayLabel(bit));
            dot.setTextSize(10);
            dot.setGravity(Gravity.CENTER);
            boolean selected = (habit.frequencyMask & bit) != 0;
            dot.setTextColor(selected ? UiPalette.ACCENT : UiPalette.DISABLED);
            dot.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            dotParams.leftMargin = dp(3);
            dotsRow.addView(dot, dotParams);
        }
        row.addView(dotsRow);

        // 点击进入编辑
        row.setOnClickListener(v -> {
            Intent intent = new Intent(this, HabitEditor.class);
            intent.putExtra(HabitEditor.EXTRA_HABIT_ID, habit.id);
            startActivityForResult(intent, REQUEST_EDIT_HABIT);
        });

        return row;
    }

    private static final int REQUEST_EDIT_HABIT = 5001;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_EDIT_HABIT && resultCode == RESULT_OK) {
            reload();
        }
    }

    // ------------------------------------------------------------ 分类

    private View buildCategoryHeader(TaskCategories.Category cat, int totalCount) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(dp(44));
        header.setPadding(dp(6), dp(12), dp(6), dp(6));

        // 分类名
        TextView label = text(cat.name, 12, UiPalette.MUTED, true);
        header.addView(label, new LinearLayout.LayoutParams(-2, -2));

        // 右侧细线，把分类名和右边空白隔开
        View line = new View(this);
        line.setBackgroundColor(UiPalette.BORDER);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(0, dp(1), 1f);
        lineParams.leftMargin = dp(10);
        lineParams.rightMargin = dp(10);
        header.addView(line, lineParams);

        // 展开／收起箭头
        TextView arrow = text(cat.expanded ? "⌄" : "›", 13, UiPalette.ACCENT, true);
        header.addView(arrow, new LinearLayout.LayoutParams(-2, -2));

        header.setOnClickListener(v -> {
            TaskCategories.toggleExpanded(this, cat.id);
            reload();
        });
        return header;
    }

    /** 之后的任务：设成未来日期的任务收在这里，默认收起。 */
    private View buildFutureSection(List<ReminderSettings.Rule> future) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);

        View hairline = new View(this);
        hairline.setBackgroundColor(UiPalette.BORDER);
        LinearLayout.LayoutParams hairlineParams = new LinearLayout.LayoutParams(-1, dp(1));
        hairlineParams.topMargin = dp(10);
        section.addView(hairline, hairlineParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(12), dp(4), dp(8));
        header.addView(text("之后 " + future.size(), 12, UiPalette.MUTED, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(text(futureExpanded ? "⌃" : "⌄", 13, UiPalette.ACCENT, true));
        header.setOnClickListener(v -> {
            futureExpanded = !futureExpanded;
            reload();
        });
        section.addView(header);

        if (futureExpanded) {
            for (ReminderSettings.Rule task : future) {
                section.addView(buildFutureRow(task));
            }
        }
        return section;
    }

    private View buildFutureRow(ReminderSettings.Rule task) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(44));
        row.setPadding(dp(6), dp(10), dp(6), dp(10));

        TextView title = text(task.title.isEmpty() ? "未命名任务" : task.title, 15, UiPalette.INK, false);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTimeInMillis(task.onceAt);
        row.addView(text(String.format(Locale.getDefault(), "%d月%d日",
                cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH)),
                12, UiPalette.MUTED, false));

        row.setOnClickListener(v -> openSubtasks(task));
        row.setOnLongClickListener(v -> {
            showTaskMenu(task);
            return true;
        });
        return row;
    }

    /** 已完成：一条细线下方的折叠区，默认收起。 */
    private View buildCompletedSection(List<ReminderSettings.Rule> finished, long dayStart) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);

        View hairline = new View(this);
        hairline.setBackgroundColor(UiPalette.BORDER);
        LinearLayout.LayoutParams hairlineParams = new LinearLayout.LayoutParams(-1, dp(1));
        hairlineParams.topMargin = dp(10);
        section.addView(hairline, hairlineParams);

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(4), dp(12), dp(4), dp(8));
        TextView label = text("已完成 " + finished.size(), 12, UiPalette.MUTED, true);
        header.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = text(completedExpanded ? "⌃" : "⌄", 13, UiPalette.ACCENT, true);
        header.addView(arrow);
        header.setOnClickListener(v -> {
            completedExpanded = !completedExpanded;
            reload();
        });
        section.addView(header);

        if (completedExpanded) {
            for (ReminderSettings.Rule task : finished) {
                section.addView(buildRow(task, dayStart));
            }
        }
        return section;
    }

    /** 顶部「总览提醒」：每 N 分钟弹一页，列出全部任务。 */
    private View buildOverviewCard() {
        ReminderSettings.Rule rule = overviewRule();
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(FormKit.round(this, Color.WHITE, 16));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);

        String detail = rule == null ? "总览提醒" : "每 " + overviewIntervalText(rule);
        card.addView(text(detail, 15, rule != null && rule.enabled ? UiPalette.INK : UiPalette.MUTED, true),
                new LinearLayout.LayoutParams(0, -2, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(rule != null && rule.enabled);
        toggle.setOnCheckedChangeListener((button, checked) -> setOverviewEnabled(checked));
        card.addView(toggle);

        card.setOnClickListener(v -> showOverviewEditor());
        return card;
    }

    private ReminderSettings.Rule overviewRule() {
        for (ReminderSettings.Rule rule : ReminderSettings.scheduledRules(this)) {
            if (rule.isTaskAllModule()) return rule;
        }
        return null;
    }

    private void setOverviewEnabled(boolean enabled) {
        ReminderSettings.Rule existing = overviewRule();
        ReminderSettings.Rule rule;
        if (existing == null) {
            if (!enabled) return;
            rule = ReminderSettings.Rule.withIntervalSeconds(ReminderSettings.nextRuleId(this),
                    "任务总览", 9 * 60, 21 * 60, 30 * 60, 9999, true, true)
                    .withModule(ReminderSettings.MODULE_TASK_ALL);
        } else {
            rule = existing.withEnabled(enabled);
        }
        ReminderSettings.upsertRule(this, rule);
        reload();
    }

    private void showOverviewEditor() {
        ReminderSettings.Rule existing = overviewRule();
        final int[] window = {existing == null ? 9 * 60 : existing.start,
                existing == null ? 21 * 60 : existing.end};
        final int[] minutes = {existing == null ? 30 : Math.max(1, existing.intervalSeconds / 60)};

        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(10), dp(18), dp(4));

        LinearLayout timeRow = new LinearLayout(this);
        timeRow.setOrientation(LinearLayout.HORIZONTAL);
        final LinearLayout startField = FormKit.timeField(this, "开始", clockText(window[0]));
        final LinearLayout endField = FormKit.timeField(this, "结束", clockText(window[1]));
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(0, -2, 1f);
        startParams.rightMargin = dp(6);
        timeRow.addView(startField, startParams);
        LinearLayout.LayoutParams endParams = new LinearLayout.LayoutParams(0, -2, 1f);
        endParams.leftMargin = dp(6);
        timeRow.addView(endField, endParams);
        form.addView(timeRow);

        startField.setOnClickListener(v -> pickMinutes(window[0], picked -> {
            window[0] = picked;
            FormKit.updateTimeField(startField, clockText(window[0]));
        }));
        endField.setOnClickListener(v -> pickMinutes(window[1], picked -> {
            window[1] = picked;
            FormKit.updateTimeField(endField, clockText(window[1]));
        }));

        form.addView(FormKit.editorLabel(this, "隔多久弹一次", true));
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        form.addView(chips);
        final int[] presets = {15, 30, 45, 60};
        final List<TextView> chipViews = new ArrayList<>();
        final Runnable refreshChips = () -> {
            for (int i = 0; i < chipViews.size(); i++) {
                boolean selected = minutes[0] == presets[i];
                TextView chip = chipViews.get(i);
                chip.setTextColor(selected ? Color.WHITE : UiPalette.INK);
                chip.setBackground(FormKit.round(this, selected ? UiPalette.ACCENT : UiPalette.PALE, 10));
            }
        };
        for (int preset : presets) {
            TextView chip = text(preset + " 分", 13, UiPalette.INK, false);
            chip.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(0, dp(40), 1f);
            chipParams.rightMargin = dp(6);
            chips.addView(chip, chipParams);
            chipViews.add(chip);
            chip.setOnClickListener(v -> {
                minutes[0] = preset;
                refreshChips.run();
            });
        }
        refreshChips.run();

        final EditText custom = FormKit.input(this, InputType.TYPE_CLASS_NUMBER);
        custom.setHint("自定义分钟数");
        custom.setText(String.valueOf(minutes[0]));
        custom.setSelection(custom.getText().length());
        LinearLayout.LayoutParams customParams = new LinearLayout.LayoutParams(-1, -2);
        customParams.topMargin = dp(10);
        form.addView(custom, customParams);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("总览提醒")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int picked = minutes[0];
            String typed = custom.getText().toString().trim();
            if (!typed.isEmpty()) {
                try {
                    picked = Math.max(1, Math.min(720, Integer.parseInt(typed)));
                } catch (NumberFormatException ignored) { }
            }
            int start = window[0];
            int end = window[1];
            if (end == start) end = (start + 60) % (24 * 60);
            int id = existing == null ? ReminderSettings.nextRuleId(this) : existing.id;
            ReminderSettings.Rule rule = ReminderSettings.Rule.withIntervalSeconds(id, "任务总览",
                    start, end, Math.max(60, picked * 60), 9999, true, true)
                    .withModule(ReminderSettings.MODULE_TASK_ALL);
            ReminderSettings.upsertRule(this, rule);
            dialog.dismiss();
            reload();
            Toast.makeText(this, "已保存 " + clockText(start) + "-" + clockText(end)
                    + " · 每 " + picked + " 分钟", Toast.LENGTH_SHORT).show();
            warnIfCannotPop();
        }));
        FormKit.showStyled(this, dialog);
    }

    private void warnIfCannotPop() {
        if (android.os.Build.VERSION.SDK_INT < 23
                || android.provider.Settings.canDrawOverlays(this)) return;
        new AlertDialog.Builder(this)
                .setTitle("还差一个权限")
                .setMessage("总览页到点要直接弹到屏幕上，需要允许「显示在其他应用上层」。")
                .setNegativeButton("稍后", null)
                .setPositiveButton("去开启", (d, w) -> {
                    try {
                        startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:" + getPackageName())));
                    } catch (Exception error) {
                        Toast.makeText(this, "无法打开悬浮窗设置", Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    private interface OnMinutePicked { void onPicked(int minuteOfDay); }

    private void pickMinutes(int current, final OnMinutePicked onPicked) {
        new android.app.TimePickerDialog(this, (view, hour, minute) -> onPicked.onPicked(hour * 60 + minute),
                current / 60, current % 60, true).show();
    }

    private String clockText(int minuteOfDay) {
        return String.format(Locale.getDefault(), "%02d:%02d", (minuteOfDay / 60) % 24, minuteOfDay % 60);
    }

    private String overviewIntervalText(ReminderSettings.Rule rule) {
        int minutes = Math.max(1, rule.intervalSeconds / 60);
        if (minutes < 60) return minutes + " 分钟";
        int hours = minutes / 60;
        int rest = minutes % 60;
        return rest == 0 ? hours + " 小时" : hours + " 小时 " + rest + " 分钟";
    }

    private View buildRow(ReminderSettings.Rule task, long dayStart) {
        boolean done = ReminderRecord.completedOn(this, ReminderSettings.MODULE_TASK, task.title, dayStart);
        List<ReminderSettings.Rule> subtasks = Subtasks.of(this, task.id);
        boolean allSubDone = subtasks.isEmpty() ? false : Subtasks.allCompleted(this, task.id, dayStart);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, dp(4), 0, dp(4));

        // 主任务行：框框 + 标题
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPadding(dp(6), dp(10), dp(6), dp(10));

        GradientDrawable rowBg = new GradientDrawable();
        rowBg.setCornerRadius(dp(12));
        rowBg.setColor(Color.WHITE);
        rowBg.setStroke(dp(1), done ? UiPalette.ACCENT : UiPalette.BORDER);
        row.setBackground(rowBg);

        GradientDrawable boxShape = new GradientDrawable();
        boxShape.setCornerRadius(dp(6));
        boxShape.setColor(done ? UiPalette.ACCENT : Color.TRANSPARENT);
        if (!done) boxShape.setStroke(dp(1), UiPalette.BORDER);
        TextView box = text(done ? "✓" : "", 12, Color.WHITE, true);
        box.setGravity(Gravity.CENTER);
        box.setBackground(boxShape);
        box.setContentDescription(done ? "取消完成" : "标记完成");
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        row.addView(box, boxParams);
        box.setOnClickListener(v -> toggleDone(task, done, dayStart, allSubDone));

        TextView title = text(task.title.isEmpty() ? "未命名任务" : task.title, 16,
                done ? UiPalette.MUTED : UiPalette.INK, false);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (done) title.setPaintFlags(title.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = dp(12);
        row.addView(title, titleParams);

        row.setOnClickListener(v -> openSubtasks(task));
        row.setOnLongClickListener(v -> {
            showTaskMenu(task);
            return true;
        });
        wrapper.addView(row);

        // 子任务流程视图（横向）
        if (!subtasks.isEmpty()) {
            wrapper.addView(buildSubtaskFlow(task, subtasks, dayStart));
        }
        return wrapper;
    }

    /** 横向流程式子任务：☐ 步骤 1 → ☐ 步骤 2 → ☐ 步骤 3（可横向滚动） */
    private View buildSubtaskFlow(final ReminderSettings.Rule task, List<ReminderSettings.Rule> subtasks, long dayStart) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(true);
        scroll.setFillViewport(false);
        scroll.setPadding(dp(38), dp(6), dp(4), dp(6));
        scroll.setMinimumHeight(dp(44));
        
        // 恢复之前保存的滚动位置
        int savedScroll = subtaskScrollPositions.get(task.id, 0);
        scroll.scrollTo(savedScroll, 0);

        LinearLayout flow = new LinearLayout(this);
        flow.setOrientation(LinearLayout.HORIZONTAL);
        flow.setGravity(Gravity.CENTER_VERTICAL);

        final int[] dragStartStep = {-1};
        final float[] dragStartX = {0};
        final List<View> boxes = new ArrayList<>();
        final List<Boolean> dones = new ArrayList<>();

        for (int i = 0; i < subtasks.size(); i++) {
            ReminderSettings.Rule sub = subtasks.get(i);
            boolean subDone = Subtasks.doneToday(this, task.id, sub.title, dayStart);

            // 子任务框：checkbox + 标题
            LinearLayout subBox = new LinearLayout(this);
            subBox.setOrientation(LinearLayout.HORIZONTAL);
            subBox.setGravity(Gravity.CENTER_VERTICAL);
            subBox.setMinimumHeight(dp(36));
            GradientDrawable subBg = new GradientDrawable();
            subBg.setCornerRadius(dp(10));
            subBg.setColor(subDone ? UiPalette.PALE : UiPalette.SOFT);
            subBg.setStroke(dp(1), UiPalette.BORDER);
            subBox.setBackground(subBg);
            subBox.setPadding(dp(8), dp(4), dp(8), dp(4));

            GradientDrawable cbShape = new GradientDrawable();
            cbShape.setCornerRadius(dp(4));
            cbShape.setColor(subDone ? UiPalette.ACCENT : Color.TRANSPARENT);
            // 未勾选时用灰色边框
            cbShape.setStroke(dp(1), subDone ? Color.TRANSPARENT : 0xff9ca3af);
            TextView cb = text(subDone ? "✓" : "", 10, subDone ? Color.WHITE : 0xff9ca3af, true);
            cb.setGravity(Gravity.CENTER);
            cb.setBackground(cbShape);
            LinearLayout.LayoutParams cbParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            subBox.addView(cb, cbParams);
            cb.setOnClickListener(v -> toggleSubtask(task, sub, !subDone, dayStart));

            TextView subTitle = text(sub.title, 13, subDone ? UiPalette.MUTED : UiPalette.INK, false);
            if (subDone) subTitle.setPaintFlags(subTitle.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-2, -2);
            subParams.leftMargin = dp(6);
            subBox.addView(subTitle, subParams);

            subBox.setOnClickListener(v -> toggleSubtask(task, sub, !subDone, dayStart));
            
            // 长按拖动排序
            subBox.setOnLongClickListener(v -> {
                dragStartStep[0] = sub.step;
                dragStartX[0] = 0;
                Toast.makeText(TaskActivity.this, "拖动调整顺序", Toast.LENGTH_SHORT).show();
                return false;
            });
            
            subBox.setOnTouchListener((v, event) -> {
                if (dragStartStep[0] >= 0) {
                    if (event.getAction() == android.view.MotionEvent.ACTION_MOVE) {
                        dragStartX[0] += event.getX();
                    } else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                        if (dragStartX[0] > dp(80) && sub.step > 0) {
                            // 向右拖，和前一个交换
                            Subtasks.swapStep(TaskActivity.this, task.id, sub.step, sub.step - 1);
                        } else if (dragStartX[0] < -dp(80) && sub.step < subtasks.size() - 1) {
                            // 向左拖，和后一个交换
                            Subtasks.swapStep(TaskActivity.this, task.id, sub.step, sub.step + 1);
                        }
                        dragStartStep[0] = -1;
                        dragStartX[0] = 0;
                        reload();
                    }
                    return true;
                }
                return false;
            });

            LinearLayout.LayoutParams flowParams = new LinearLayout.LayoutParams(-2, -2);
            flow.addView(subBox, flowParams);
            boxes.add(subBox);
            dones.add(subDone);

            // 箭头 →
            if (i < subtasks.size() - 1) {
                TextView arrow = text("→", 14, UiPalette.MUTED, false);
                LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(-2, -2);
                arrowParams.leftMargin = dp(8);
                arrowParams.rightMargin = dp(8);
                flow.addView(arrow, arrowParams);
            }
        }
        scroll.addView(flow, new HorizontalScrollView.LayoutParams(-2, -2));
        
        // 滚动变化时保存位置
        scroll.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            subtaskScrollPositions.put(task.id, scrollX);
        });
        
        // 布局完成后，滚动到第一个未完成子任务的真实位置
        scroll.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                if (flow.getWidth() == 0 || scroll.getWidth() == 0) return; // 还没测量，等下一轮
                int targetLeft = -1;
                for (int i = 0; i < boxes.size(); i++) {
                    if (!dones.get(i)) {
                        targetLeft = boxes.get(i).getLeft();
                        break;
                    }
                }
                if (targetLeft < 0) {
                    scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    return;
                }
                int x = Math.max(0, targetLeft - scroll.getPaddingLeft());
                scroll.scrollTo(x, 0);
                subtaskScrollPositions.put(task.id, x);
                scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });
        
        return scroll;
    }

    private void toggleDone(ReminderSettings.Rule task, boolean done, long dayStart, boolean allSubDone) {
        if (done) {
            ReminderRecord.deleteCompletion(this, ReminderSettings.MODULE_TASK, task.title, dayStart);
            ReminderSettings.unmarkCompleted(this, task.id);
        } else {
            // 勾选大任务时，自动把所有子任务一并勾选
            List<ReminderSettings.Rule> subtasks = Subtasks.of(this, task.id);
            if (!subtasks.isEmpty()) {
                for (ReminderSettings.Rule sub : subtasks) {
                    Subtasks.toggle(this, task.id, sub.title, false, dayStart);
                }
            }
            long now = System.currentTimeMillis();
            ReminderRecord.finish(this, java.util.UUID.randomUUID().toString(), task.title,
                    ReminderRecord.KIND_DONE, now, now, "任务清单勾选", ReminderSettings.MODULE_TASK);
            ReminderSettings.markCompleted(this, task.id, Math.max(1, task.dailyTarget));
        }
        reload();
    }

    private void toggleSubtask(ReminderSettings.Rule task, ReminderSettings.Rule sub, boolean done, long dayStart) {
        Subtasks.toggle(this, task.id, sub.title, !done, dayStart);
        reload();
    }

    private void showSubtaskMenu(ReminderSettings.Rule task, ReminderSettings.Rule sub, long dayStart) {
        FormKit.options(this, sub.title, new String[]{"重命名", "删除"}, 0, which -> {
            if (which == 0) {
                final EditText input = FormKit.textInput(this, "子任务名字", sub.title);
                new AlertDialog.Builder(this)
                    .setTitle("重命名子任务")
                    .setView(input)
                    .setNegativeButton("取消", null)
                    .setPositiveButton("保存", (d, w) -> {
                        String newTitle = input.getText().toString().trim();
                        if (!newTitle.isEmpty() && !newTitle.equals(sub.title)) {
                            Subtasks.delete(TaskActivity.this, sub);
                            Subtasks.add(TaskActivity.this, task.id, newTitle, sub.step);
                            reload();
                        }
                    })
                    .show();
            } else confirmDeleteSubtask(task, sub);
        });
    }

    private void confirmDeleteSubtask(ReminderSettings.Rule task, ReminderSettings.Rule sub) {
        new AlertDialog.Builder(this)
                .setTitle("删除子任务")
                .setMessage("确定要删除「" + sub.title + "」吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    Subtasks.delete(this, sub);
                    reload();
                })
                .show();
    }

    private void openSubtasks(ReminderSettings.Rule task) {
        startActivity(new Intent(this, SubtaskActivity.class)
                .putExtra(SubtaskActivity.EXTRA_PARENT_ID, task.id));
    }

    private void showTaskMenu(ReminderSettings.Rule task) {
        String toggleLabel = task.enabled ? "关闭单独提醒" : "开启单独提醒";
        FormKit.options(this, task.title.isEmpty() ? "未命名任务" : task.title,
                new String[]{"分类 · " + getCategoryName(task.categoryId), toggleLabel, "删除任务"}, 2, which -> {
                    if (which == 0) changeCategory(task);
                    else if (which == 1) {
                        ReminderSettings.upsertRule(this, task.withEnabled(!task.enabled));
                        reload();
                    } else confirmDelete(task);
                });
    }

    private void changeCategory(ReminderSettings.Rule task) {
        CategoryPicker.show(this, task.categoryId, cat -> {
            ReminderSettings.upsertRule(this, task.withCategory(cat.id));
            reload();
            Toast.makeText(this, "已移到「" + cat.name + "」", Toast.LENGTH_SHORT).show();
        });
    }

    private String getCategoryName(int id) {
        for (TaskCategories.Category cat : TaskCategories.all(this)) {
            if (cat.id == id) return cat.name;
        }
        return "默认";
    }

    private void quickAddSubtask(ReminderSettings.Rule task) {
        final EditText input = FormKit.textInput(this, "子任务名字", "");
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(10), dp(18), dp(0));
        form.addView(input, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("添加子任务")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("添加", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = input.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "先写个名字", Toast.LENGTH_SHORT).show();
                return;
            }
            List<ReminderSettings.Rule> existing = Subtasks.of(this, task.id);
            int step = existing.isEmpty() ? 0 : (existing.get(existing.size() - 1).step + 1);
            Subtasks.add(this, task.id, title, step);
            dialog.dismiss();
            reload();
        }));
        FormKit.showStyled(this, dialog);
    }

    private String describe(ReminderSettings.Rule rule) {
        String base = describeBase(rule);
        return rule.enabled ? base : "未单独提醒 · 只在总览页出现";
    }

    private String describeBase(ReminderSettings.Rule rule) {
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
                    Subtasks.deleteForParent(this, rule.id);
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
                    ReminderSettings.clearModule(this, ReminderSettings.MODULE_SUBTASK);
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

        // 分类选择行
        LinearLayout catRow = new LinearLayout(this);
        catRow.setOrientation(LinearLayout.HORIZONTAL);
        catRow.setGravity(Gravity.CENTER_VERTICAL);
        catRow.setPadding(0, dp(10), 0, 0);
        TextView catLabel = text("分类", 12, UiPalette.MUTED, false);
        catRow.addView(catLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        final int[] categoryId = {existing == null ? 0 : existing.categoryId};
        final TextView catBtn = CategoryPicker.chip(this, getCategoryName(categoryId[0]));
        catBtn.setOnClickListener(v -> pickCategory(categoryId, catBtn));
        catRow.addView(catBtn);
        form.addView(catRow, new LinearLayout.LayoutParams(-1, -2));

        EditText note = FormKit.input(this, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
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
                    draftMode, points, daysMask[0], onceAt[0], window, intervalMinutes[0])
                    .withCategory(categoryId[0]);
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
