package io.iftech.healthreminder;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 任务总览提醒页：一个页面列出全部任务，勾一个记一个，
 * 不再像单个任务提醒那样一次只推一件。
 */
public final class TaskOverviewReminderActivity extends Activity {
    private int ruleId = -1;
    private LinearLayout listContainer;
    private TextView summary;
    private long dayStart;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        ruleId = getIntent().getIntExtra("rule_id", -1);
        dayStart = ReminderRecord.dayStart(System.currentTimeMillis());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(18));
        root.setBackgroundColor(UiPalette.PAGE);
        setContentView(root);

        summary = text("", 12, UiPalette.MUTED, false);
        summary.setGravity(Gravity.END);
        root.addView(summary, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp(14), 0, dp(6));
        scroll.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView close = actionButton("关闭", UiPalette.ACCENT, Color.WHITE);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-1, dp(50));
        closeParams.topMargin = dp(8);
        root.addView(close, closeParams);
        close.setOnClickListener(v -> dismissPage());

        TextView snooze = actionButton("稍后", UiPalette.SOFT, UiPalette.INK);
        LinearLayout.LayoutParams snoozeParams = new LinearLayout.LayoutParams(-1, dp(46));
        snoozeParams.topMargin = dp(10);
        root.addView(snooze, snoozeParams);
        snooze.setOnClickListener(v -> {
            ReminderScheduler.scheduleCustomSnooze(this, 10, "任务总览", ruleId);
            finish();
        });

        renderTasks();
    }

    /** 今天所有任务都勾完了吗（或压根没任务）；闹钟到点时用它决定要不要再弹。 */
    static boolean allTasksDone(android.content.Context context) {
        long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
        for (ReminderSettings.Rule task : Tasks.today(context)) {
            if (!task.enabled) continue;
            if (!ReminderRecord.completedOn(context, ReminderSettings.MODULE_TASK, task.title, dayStart)) return false;
        }
        return true;
    }

    private void renderTasks() {
        listContainer.removeAllViews();
        List<ReminderSettings.Rule> tasks = Tasks.today(this);
        List<ReminderSettings.Rule> shown = new ArrayList<>();
        for (ReminderSettings.Rule task : tasks) {
            if (task.enabled) shown.add(task);
        }
        int doneCount = 0;
        for (ReminderSettings.Rule task : shown) {
            if (ReminderRecord.completedOn(this, ReminderSettings.MODULE_TASK, task.title, dayStart)) doneCount++;
        }
        summary.setText(shown.isEmpty() ? ""
                : String.format(Locale.getDefault(), "%d/%d", doneCount, shown.size()));
        if (shown.isEmpty()) {
            TextView empty = text("任务清单里还没有任务。\n回任务页加几个，到点我再来喊你。", 14, UiPalette.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(20), dp(30), dp(20), dp(30));
            listContainer.addView(empty, new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (ReminderSettings.Rule task : shown) {
            listContainer.addView(taskRow(task));
        }
    }

    private LinearLayout taskRow(ReminderSettings.Rule task) {
        boolean done = ReminderRecord.completedOn(this, ReminderSettings.MODULE_TASK, task.title, dayStart);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(Color.WHITE, 16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(10);
        card.setLayoutParams(params);

        TextView box = text(done ? "✓" : "", 15, Color.WHITE, true);
        box.setGravity(Gravity.CENTER);
        box.setBackground(round(done ? UiPalette.ACCENT : 0x00000000, 8));
        card.addView(box, new LinearLayout.LayoutParams(dp(26), dp(26)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textsParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textsParams.leftMargin = dp(14);
        card.addView(texts, textsParams);

        TextView name = text(task.title, 16, done ? UiPalette.MUTED : UiPalette.INK, true);
        if (done) name.setPaintFlags(name.getPaintFlags() | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        texts.addView(name);
        String hint = task.isOnceSchedule() ? "今天一次"
                : task.specificTimeMode ? "固定时间 · " + timeText(task) : "每 " + intervalText(task) + " 一次";
        texts.addView(text(hint, 12, UiPalette.MUTED, false));

        card.setOnClickListener(v -> toggle(task, done));
        return card;
    }

    private void toggle(ReminderSettings.Rule task, boolean done) {
        if (done) {
            ReminderRecord.deleteCompletion(this, ReminderSettings.MODULE_TASK, task.title, dayStart);
        } else {
            long now = System.currentTimeMillis();
            ReminderRecord.finish(this, java.util.UUID.randomUUID().toString(), task.title,
                    ReminderRecord.KIND_DONE, now, now, "总览页勾选", ReminderSettings.MODULE_TASK);
        }
        renderTasks();
    }

    private void dismissPage() {
        boolean allDone = true;
        for (ReminderSettings.Rule task : ReminderSettings.moduleRules(this, ReminderSettings.MODULE_TASK)) {
            if (!ReminderRecord.completedOn(this, ReminderSettings.MODULE_TASK, task.title, dayStart)) {
                allDone = false;
                break;
            }
        }
        if (ruleId >= 0 && !allDone) ReminderScheduler.scheduleNext(this, ruleId);
        finish();
    }

    private String timeText(ReminderSettings.Rule rule) {
        List<Integer> points = rule.timePointsSeconds;
        if (points.isEmpty()) return "--:--";
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < points.size(); i++) {
            if (i > 0) text.append("、");
            int point = points.get(i);
            text.append(String.format(Locale.getDefault(), "%02d:%02d", point / 3600, (point % 3600) / 60));
        }
        return text.toString();
    }

    private String intervalText(ReminderSettings.Rule rule) {
        int minutes = Math.max(1, rule.intervalSeconds / 60);
        if (minutes < 60) return minutes + " 分钟";
        int hours = minutes / 60;
        int rest = minutes % 60;
        return rest == 0 ? hours + " 小时" : hours + " 小时 " + rest + " 分钟";
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
