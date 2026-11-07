package io.iftech.healthreminder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** 任务到点后的全屏提醒页。复用 ReminderRecord 做记录，module = task。 */
public final class TaskReminderActivity extends Activity {
    private String action = "";
    private int ruleId = -1;
    private String sessionId;
    private long startedAt;
    private String note = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        action = getIntent().getStringExtra("action");
        ruleId = getIntent().getIntExtra("rule_id", -1);
        if (action == null) action = "任务";
        ReminderSettings.Rule rule = ReminderSettings.ruleById(this, ruleId);
        if (rule != null) note = rule.reminderMessage;
        sessionId = java.util.UUID.randomUUID().toString();
        startedAt = System.currentTimeMillis();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(22), dp(28), dp(22), dp(24));
        root.setBackgroundColor(UiPalette.PAGE);
        setContentView(root);

        TextView overline = text("该做这件事了", 13, UiPalette.MUTED, true);
        overline.setGravity(Gravity.CENTER);
        overline.setPadding(dp(14), dp(8), dp(14), dp(8));
        overline.setBackground(round(UiPalette.SOFT, 12));
        root.addView(overline);

        ScrollView scroll = new ScrollView(this);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(22), dp(26), dp(22), dp(26));
        card.setBackground(round(Color.WHITE, 20));
        card.setElevation(dp(2));
        scroll.addView(card, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1f);
        scrollParams.topMargin = dp(16);
        root.addView(scroll, scrollParams);

        TextView title = text(action, 27, UiPalette.INK, true);
        title.setGravity(Gravity.CENTER);
        card.addView(title);

        if (!note.isEmpty()) {
            TextView noteView = text(note, 15, UiPalette.MUTED, false);
            noteView.setGravity(Gravity.CENTER);
            noteView.setLineSpacing(dp(3), 1f);
            LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
            noteParams.topMargin = dp(12);
            card.addView(noteView, noteParams);
        }

        TextView clock = text(nowText(), 13, UiPalette.MUTED, false);
        clock.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams clockParams = new LinearLayout.LayoutParams(-1, -2);
        clockParams.topMargin = dp(18);
        card.addView(clock, clockParams);

        TextView done = actionButton("做完了", UiPalette.ACCENT, Color.WHITE);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, dp(50));
        doneParams.topMargin = dp(20);
        root.addView(done, doneParams);
        done.setOnClickListener(v -> finishRecord(ReminderRecord.KIND_DONE, ""));

        TextView snooze = actionButton("5 分钟后提醒", UiPalette.SOFT, UiPalette.INK);
        LinearLayout.LayoutParams snoozeParams = new LinearLayout.LayoutParams(-1, dp(46));
        snoozeParams.topMargin = dp(10);
        root.addView(snooze, snoozeParams);
        snooze.setOnClickListener(v -> {
            ReminderScheduler.scheduleSnooze(this, 5, action, ruleId);
            finish();
        });

        TextView skip = actionButton("今天不做", Color.TRANSPARENT, UiPalette.MUTED);
        LinearLayout.LayoutParams skipParams = new LinearLayout.LayoutParams(-1, dp(46));
        skipParams.topMargin = dp(6);
        root.addView(skip, skipParams);
        skip.setOnClickListener(v -> finishRecord(ReminderRecord.KIND_MISSED, "今天不做"));
    }

    private void finishRecord(String kind, String reason) {
        ReminderRecord.finish(this, sessionId, action, kind, startedAt,
                System.currentTimeMillis(), reason, ReminderSettings.MODULE_TASK);
        ReminderScheduler.scheduleNext(this, ruleId);
        finish();
    }

    private String nowText() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        return String.format(Locale.getDefault(), "%d月%d日 %02d:%02d",
                cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH),
                cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE));
    }

    private TextView actionButton(String label, int background, int textColor) {
        TextView view = text(label, 16, textColor, true);
        view.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(background);
        bg.setCornerRadius(dp(16));
        view.setBackground(bg);
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
