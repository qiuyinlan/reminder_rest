package io.iftech.healthreminder;

import android.app.Activity;
import android.app.NotificationManager;
import android.app.AlertDialog;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;
import android.text.InputType;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ScrollView;

public final class ReminderActivity extends Activity {
    private static final int GREEN = 0xff277049;
    private String action;
    private int ruleId = -1;
    private String sessionId;
    private long startedAt;
    private boolean waiting;
    private boolean reasonFromOverlay;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private TextView elapsedLabel;
    private EditText completionNote;
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (waiting && elapsedLabel != null) {
                long seconds = Math.max(0, System.currentTimeMillis() - startedAt) / 1000;
                elapsedLabel.setText(String.format(java.util.Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60));
                timerHandler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        setReminderSystemBarBackground();
        action = getIntent().getStringExtra("action");
        ruleId = getIntent().getIntExtra("rule_id", -1);
        if (action == null) action = "歇一歇";
        reasonFromOverlay = getIntent().getBooleanExtra("enter_reason", false);
        if (reasonFromOverlay) {
            sessionId = java.util.UUID.randomUUID().toString();
            startedAt = System.currentTimeMillis();
            LinearLayout background = new LinearLayout(this);
            background.setBackgroundColor(0xffe4f2e3);
            setContentView(background);
            askReason();
            return;
        }
        org.json.JSONObject pendingPrefs = ReminderRecord.pending(this);
        if (pendingPrefs.optBoolean("waiting", false)) {
            action = pendingPrefs.optString("action", action);
            sessionId = pendingPrefs.optString("id", java.util.UUID.randomUUID().toString());
            startedAt = pendingPrefs.optLong("startedAt", System.currentTimeMillis());
            waiting = true;
            showWaiting();
            return;
        }
        if (getIntent().getBooleanExtra("begin_immediately", false)) {
            beginDoing();
            return;
        }
        getWindow().setStatusBarColor(0xffe4f2e3);
        getWindow().setNavigationBarColor(0xffe4f2e3);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(26), dp(28), dp(26), dp(26));
        root.setBackgroundColor(0xffe4f2e3);
        setContentView(root);

        TextView overline = text("哪怕做一秒，身体也会舒服一点！\n求求主人了。", 17, GREEN, true);
        overline.setGravity(Gravity.CENTER);
        overline.setLineSpacing(dp(3), 1f);
        root.addView(overline);
        FrameLayout animation = new FrameLayout(this);
        if (isTypingReminder(action)) {
            animation.addView(new TypingSceneView(this), new FrameLayout.LayoutParams(-1, -1));
        } else {
            WellnessAnimationView.Mood mood = isWaterReminder(action) ? WellnessAnimationView.Mood.THIRSTY
                    : action.contains("护眼") ? WellnessAnimationView.Mood.TIRED : WellnessAnimationView.Mood.SAD;
            WellnessAnimationView face = new WellnessAnimationView(this,
                    mood);
            face.setScaleX(.82f);
            face.setScaleY(.82f);
            face.setAlpha(0f);
            face.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(620)
                    .setInterpolator(new OvershootInterpolator(1.15f)).start();
            ObjectAnimator bob = ObjectAnimator.ofFloat(face, View.TRANSLATION_Y, 0f, -dp(8), 0f);
            bob.setDuration(2400);
            bob.setStartDelay(620);
            bob.setRepeatCount(ValueAnimator.INFINITE);
            face.post(bob::start);
            animation.addView(face, new FrameLayout.LayoutParams(dp(260), dp(250), Gravity.CENTER));
        }
        int sceneHeight = isTypingReminder(action) ? 210 : 260;
        LinearLayout.LayoutParams sceneParams = new LinearLayout.LayoutParams(-1, dp(sceneHeight));
        sceneParams.topMargin = dp(14);
        root.addView(animation, sceneParams);
        TextView title = text(titleFor(action), isTypingReminder(action) ? 27 : 30, 0xff23342a, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title);
        TextView plea = text(pleaFor(action), 17, 0xff53685a, false);
        plea.setGravity(Gravity.CENTER);
        plea.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams pleaParams = new LinearLayout.LayoutParams(-1, -2);
        pleaParams.topMargin = dp(10);
        root.addView(plea, pleaParams);
        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));

        Button done = button("好啦，我去做", GREEN, Color.WHITE);
        done.setOnClickListener(v -> beginDoing());
        root.addView(done, new LinearLayout.LayoutParams(-1, dp(54)));
        TextView snooze = text("临时有事，稍后提醒我", 15, GREEN, true);
        snooze.setGravity(Gravity.CENTER);
        snooze.setPadding(0, dp(15), 0, dp(8));
        snooze.setOnClickListener(v -> {
            ReminderReceiver.stopAlarmVibration(this, "user chose snooze");
            ReminderRecord.recordSnoozed(this, action, System.currentTimeMillis());
            ReminderScheduler.scheduleSnooze(this, 5, action, ruleId);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(703);
            finish();
        });
        root.addView(snooze);
    }

    private void beginDoing() {
        ReminderReceiver.stopAlarmVibration(this, "user started reminder action");
        waiting = true;
        sessionId = java.util.UUID.randomUUID().toString();
        startedAt = System.currentTimeMillis();
        ReminderRecord.begin(this, sessionId, action, startedAt);
        try {
            getSharedPreferences("health_records", MODE_PRIVATE).edit().putString("pending", new org.json.JSONObject()
                    .put("waiting", true).put("id", sessionId).put("action", action).put("startedAt", startedAt).toString()).apply();
        } catch (org.json.JSONException ignored) { }
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(703);
        showWaiting();
    }

    private void showWaiting() {
        setReminderSystemBarBackground();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(26), dp(38), dp(26), dp(26));
        root.setBackgroundColor(0xffe4f2e3);
        setContentView(root);
        root.addView(text("我在这里等你回来", 17, GREEN, true));
        WellnessAnimationView smile = new WellnessAnimationView(this, WellnessAnimationView.Mood.BREATHING);
        root.addView(smile, new LinearLayout.LayoutParams(dp(240), dp(240)));
        TextView message = text("正在等你去做：" + titleFor(action), 23, 0xff23342a, true);
        message.setGravity(Gravity.CENTER);
        root.addView(message);
        elapsedLabel = text("00:00", 20, GREEN, true);
        elapsedLabel.setGravity(Gravity.CENTER);
        elapsedLabel.setPadding(0, dp(12), 0, dp(26));
        root.addView(elapsedLabel);
        completionNote = new EditText(this);
        completionNote.setSingleLine(true);
        completionNote.setTextSize(15);
        completionNote.setTextColor(0xff23342a);
        completionNote.setHintTextColor(0xff87968b);
        completionNote.setHint("做了什么？（选填）");
        completionNote.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        completionNote.setPadding(dp(14), dp(10), dp(14), dp(10));
        completionNote.setBackground(round(Color.WHITE, 12));
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, dp(50));
        noteParams.bottomMargin = dp(12);
        root.addView(completionNote, noteParams);
        View spacer = new View(this);
        root.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        Button completed = button("已做完", GREEN, Color.WHITE);
        completed.setOnClickListener(v -> completeSession());
        root.addView(completed, new LinearLayout.LayoutParams(-1, dp(54)));
        TextView missed = text("临时有事，没做", 15, GREEN, true);
        missed.setGravity(Gravity.CENTER);
        missed.setPadding(0, dp(16), 0, dp(8));
        missed.setOnClickListener(v -> askReason());
        root.addView(missed);
        completionNote.clearFocus();
        root.setFocusableInTouchMode(true);
        root.requestFocus();
        timerHandler.removeCallbacks(tick);
        timerHandler.post(tick);
    }

    private void setReminderSystemBarBackground() {
        getWindow().setStatusBarColor(0xffe4f2e3);
        getWindow().setNavigationBarColor(0xffe4f2e3);
        getWindow().getDecorView().setBackgroundColor(0xffe4f2e3);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private void completeSession() {
        long ended = System.currentTimeMillis();
        String note = completionNote == null ? "" : completionNote.getText().toString().trim();
        ReminderRecord.finish(this, sessionId, action, ReminderRecord.KIND_DONE, startedAt, ended, note);
        for (ReminderSettings.Rule rule : ReminderSettings.rules(this)) {
            if (rule.title.equals(action)) {
                ReminderSettings.markCompleted(this, rule.id, rule.dailyTarget);
                ReminderScheduler.resetNext(this, rule.id);
                ReminderScheduler.update(this);
                break;
            }
        }
        finish();
    }

    private void askReason() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(22), dp(22), dp(22), dp(16));
        content.setBackground(round(Color.WHITE, 22));

        TextView title = text("临时有事", 21, 0xff23342a, true);
        content.addView(title);

        TextView subtitle = text("记下为什么没能完成，也可以选择稍后提醒。", 14, 0xff718076, false);
        subtitle.setPadding(0, dp(7), 0, dp(18));
        content.addView(subtitle);

        EditText reason = new EditText(this);
        reason.setHint("什么事比我还重要！");
        reason.setTextSize(16);
        reason.setTextColor(0xff23342a);
        reason.setHintTextColor(0xff93a197);
        reason.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        reason.setSingleLine(true);
        reason.setPadding(dp(14), dp(10), dp(14), dp(10));
        reason.setBackground(round(0xfff4f7f3, 12));
        LinearLayout.LayoutParams reasonParams = new LinearLayout.LayoutParams(-1, -2);
        reasonParams.topMargin = dp(4);
        content.addView(reason, reasonParams);

        LinearLayout snoozeHeader = new LinearLayout(this);
        snoozeHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView snoozeTitle = text("稍后提醒我", 14, 0xff23342a, true);
        snoozeHeader.addView(snoozeTitle, new LinearLayout.LayoutParams(0, -2, 1));
        Switch customDelay = new Switch(this);
        customDelay.setText("自选");
        customDelay.setTextColor(0xff617466);
        snoozeHeader.addView(customDelay);
        LinearLayout.LayoutParams snoozeHeaderParams = new LinearLayout.LayoutParams(-1, -2);
        snoozeHeaderParams.topMargin = dp(14);
        content.addView(snoozeHeader, snoozeHeaderParams);

        LinearLayout delayRow = new LinearLayout(this);
        delayRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView delayLabel = text("延后时间", 12, 0xff718076, false);
        delayRow.addView(delayLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView delayValue = text("下一次到时再提醒", 13, GREEN, true);
        delayRow.addView(delayValue);
        LinearLayout.LayoutParams delayRowParams = new LinearLayout.LayoutParams(-1, -2);
        delayRowParams.topMargin = dp(4);
        content.addView(delayRow, delayRowParams);

        LinearLayout delayPickers = new LinearLayout(this);
        delayPickers.setGravity(Gravity.CENTER);
        delayPickers.setPadding(dp(12), dp(2), dp(12), dp(2));
        delayPickers.setBackground(round(0xfff4f7f3, 12));
        LinearLayout hourColumn = new LinearLayout(this);
        hourColumn.setGravity(Gravity.CENTER);
        hourColumn.setOrientation(LinearLayout.HORIZONTAL);
        NumberPicker hourPicker = new NumberPicker(this);
        hourPicker.setMinValue(0);
        hourPicker.setMaxValue(23);
        hourPicker.setValue(0);
        NumberPicker minutePicker = new NumberPicker(this);
        minutePicker.setMinValue(0);
        minutePicker.setMaxValue(59);
        minutePicker.setValue(15);
        hourColumn.addView(hourPicker, new LinearLayout.LayoutParams(dp(88), dp(112)));
        hourColumn.addView(text("小时", 13, 0xff718076, false));
        LinearLayout minuteColumn = new LinearLayout(this);
        minuteColumn.setGravity(Gravity.CENTER);
        minuteColumn.setOrientation(LinearLayout.HORIZONTAL);
        minuteColumn.addView(minutePicker, new LinearLayout.LayoutParams(dp(88), dp(112)));
        minuteColumn.addView(text("分钟", 13, 0xff718076, false));
        delayPickers.addView(hourColumn, new LinearLayout.LayoutParams(0, -2, 1));
        delayPickers.addView(minuteColumn, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams pickerParams = new LinearLayout.LayoutParams(-1, -2);
        pickerParams.topMargin = dp(8);
        content.addView(delayPickers, pickerParams);

        TextView defaultDelay = text("不选的话，默认下一次到时再提醒", 12, 0xff718076, false);
        defaultDelay.setPadding(0, 0, 0, dp(3));
        content.addView(defaultDelay);
        TextView warning = text("想想清楚，最好是真的有事！\n不要骗别人把自己也骗了！", 12,
                0xffbd5147, false);
        warning.setLineSpacing(dp(2), 1f);
        warning.setPadding(dp(10), dp(8), dp(10), dp(8));
        warning.setBackground(round(0xffffefed, 8));
        content.addView(warning);

        customDelay.setOnCheckedChangeListener((button, checked) -> {
            updateDelayLabel(delayValue, hourPicker.getValue(), minutePicker.getValue(), checked);
        });
        NumberPicker.OnValueChangeListener onDelayChanged = (picker, oldValue, newValue) -> {
            if (!customDelay.isChecked()) customDelay.setChecked(true);
            updateDelayLabel(delayValue, hourPicker.getValue(), minutePicker.getValue(), true);
        };
        hourPicker.setOnValueChangedListener(onDelayChanged);
        minutePicker.setOnValueChangedListener(onDelayChanged);

        TextView error = text("请写一句原因后再提交", 13, 0xffbd5147, false);
        error.setVisibility(View.GONE);
        error.setPadding(dp(2), dp(6), 0, 0);
        content.addView(error);

        reason.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) error.setVisibility(View.GONE);
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(content);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(scroll).create();
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setPadding(0, dp(18), 0, 0);
        TextView cancel = text("再想想", 15, 0xff617167, true);
        cancel.setGravity(Gravity.CENTER);
        cancel.setPadding(dp(18), dp(12), dp(18), dp(12));
        cancel.setOnClickListener(v -> {
            dialog.dismiss();
            if (reasonFromOverlay) returnToOverlay();
        });
        TextView submit = text("提交记录", 15, Color.WHITE, true);
        submit.setGravity(Gravity.CENTER);
        submit.setPadding(dp(22), dp(12), dp(22), dp(12));
        submit.setBackground(round(GREEN, 14));
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(-2, -2);
        submitParams.leftMargin = dp(8);
        actions.addView(cancel);
        actions.addView(submit, submitParams);
        content.addView(actions);

        submit.setOnClickListener(v -> {
            String value = reason.getText().toString().trim();
            if (value.isEmpty()) {
                error.setVisibility(View.VISIBLE);
                reason.requestFocus();
                return;
            }
            ReminderRecord.finish(this, sessionId, action, ReminderRecord.KIND_MISSED,
                    startedAt, System.currentTimeMillis(), value);
            if (customDelay.isChecked()) {
                ReminderScheduler.scheduleCustomSnooze(this,
                        delayMinutes(hourPicker.getValue(), minutePicker.getValue()), action, ruleId);
            }
            ReminderReceiver.stopAlarmVibration(this, "user submitted follow-up");
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(703);
            dialog.dismiss();
            finish();
        });
        dialog.setOnCancelListener(ignored -> {
            if (reasonFromOverlay) returnToOverlay();
        });
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(36), -2);
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void returnToOverlay() {
        finish();
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> ReminderOverlayWindow.show(getApplicationContext(), action, ruleId), 180);
    }

    private void updateDelayLabel(TextView label, int hours, int minutes, boolean custom) {
        if (!custom) {
            label.setText("下一次到时再提醒");
            return;
        }
        int totalMinutes = delayMinutes(hours, minutes);
        label.setText(String.format(java.util.Locale.getDefault(), "%dh%02dmin 后再提醒我",
                totalMinutes / 60, totalMinutes % 60));
    }

    private int delayMinutes(int hours, int minutes) {
        return Math.max(1, hours * 60 + minutes);
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    @Override protected void onPause() { super.onPause(); timerHandler.removeCallbacks(tick); }
    @Override protected void onResume() { super.onResume(); if (waiting) timerHandler.post(tick); }

    static String titleFor(String action) {
        if (isWaterReminder(action)) return "多喝点水吧";
        if (isWalkingReminder(action)) return "起来走走吧";
        if (isPostureReminder(action)) return "坐直了吗？";
        switch (action) {
            case "护眼": return "看看远处吧";
            default: return "休息一小会儿";
        }
    }

    static String pleaFor(String action) {
        if (isWaterReminder(action)) return "你的身体口渴啦！多补充些水分，帮助身体维持正常代谢。";
        if (isWalkingReminder(action)) return "久坐会让下肢和腰部都很辛苦。站起来走一走，让身体舒展开。";
        if (isPostureReminder(action)) return "手肘自然弯曲，把键盘放近些；肩膀放松，别让肩颈一直受力。";
        switch (action) {
            case "护眼": return "眼睛有点累啦，看看远处 20 秒。";
            default: return "你已经认真一阵子了，歇一歇吧。";
        }
    }

    static boolean isWaterReminder(String action) {
        return action.contains("喝水") || action.contains("饮水") || action.contains("补水");
    }

    static boolean isWalkingReminder(String action) {
        return action.contains("走") || action.contains("久坐") || action.contains("活动");
    }

    static boolean isPostureReminder(String action) {
        return action.contains("颈") || action.contains("肩") || action.contains("坐姿") || action.contains("打字") || action.contains("姿势");
    }

    private boolean isTypingReminder(String action) {
        return isWalkingReminder(action) || isPostureReminder(action);
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String value, int backgroundColor, int textColor) {
        Button button = new Button(this);
        button.setText(value);
        button.setTextSize(16);
        button.setTextColor(textColor);
        button.setAllCaps(false);
        GradientDrawable background = new GradientDrawable();
        background.setColor(backgroundColor);
        background.setCornerRadius(dp(16));
        button.setBackground(background);
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
