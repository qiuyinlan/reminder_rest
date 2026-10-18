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
import android.widget.TextView;
import android.widget.Toast;

public final class ReminderActivity extends Activity {
    private static final int GREEN = 0xff277049;
    private String action;
    private String sessionId;
    private long startedAt;
    private boolean waiting;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private TextView elapsedLabel;
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
        action = getIntent().getStringExtra("action");
        if (action == null) action = "歇一歇";
        org.json.JSONObject pendingPrefs = ReminderRecord.pending(this);
        if (pendingPrefs.optBoolean("waiting", false)) {
            action = pendingPrefs.optString("action", action);
            sessionId = pendingPrefs.optString("id", java.util.UUID.randomUUID().toString());
            startedAt = pendingPrefs.optLong("startedAt", System.currentTimeMillis());
            waiting = true;
            showWaiting();
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
            WellnessAnimationView face = new WellnessAnimationView(this,
                    action.contains("护眼") ? WellnessAnimationView.Mood.TIRED : WellnessAnimationView.Mood.SAD);
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
        if (isWaterReminder(action)) {
            WellnessAnimationView droplet = new WellnessAnimationView(this, WellnessAnimationView.Mood.WATER);
            ObjectAnimator dropBob = ObjectAnimator.ofFloat(droplet, View.TRANSLATION_Y, -dp(5), dp(8), -dp(5));
            dropBob.setDuration(1500);
            dropBob.setRepeatCount(ValueAnimator.INFINITE);
            droplet.post(dropBob::start);
            FrameLayout.LayoutParams dropParams = new FrameLayout.LayoutParams(dp(48), dp(48), Gravity.RIGHT | Gravity.TOP);
            dropParams.topMargin = dp(20);
            dropParams.rightMargin = dp(12);
            animation.addView(droplet, dropParams);
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
            ReminderScheduler.scheduleSnooze(this, 5, action);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).cancel(703);
            finish();
        });
        root.addView(snooze);
    }

    private void beginDoing() {
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
        getWindow().setStatusBarColor(0xffe4f2e3);
        getWindow().setNavigationBarColor(0xffe4f2e3);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(26), dp(38), dp(26), dp(26));
        root.setBackgroundColor(0xffe4f2e3);
        setContentView(root);
        root.addView(text("我在这里等你回来", 17, GREEN, true));
        TextView smile = text("☺", 126, GREEN, true);
        smile.setGravity(Gravity.CENTER);
        root.addView(smile, new LinearLayout.LayoutParams(-1, dp(240)));
        smile.animate().scaleX(1.08f).scaleY(1.08f).setDuration(900).withEndAction(() ->
                smile.animate().scaleX(1f).scaleY(1f).setDuration(900).start()).start();
        TextView message = text("正在等你去做：" + titleFor(action), 23, 0xff23342a, true);
        message.setGravity(Gravity.CENTER);
        root.addView(message);
        elapsedLabel = text("00:00", 20, GREEN, true);
        elapsedLabel.setGravity(Gravity.CENTER);
        elapsedLabel.setPadding(0, dp(12), 0, dp(26));
        root.addView(elapsedLabel);
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
        timerHandler.removeCallbacks(tick);
        timerHandler.post(tick);
    }

    private void completeSession() {
        long ended = System.currentTimeMillis();
        ReminderRecord.finish(this, sessionId, action, ReminderRecord.KIND_DONE, startedAt, ended, "");
        for (ReminderSettings.Rule rule : ReminderSettings.rules(this)) {
            if (rule.title.equals(action)) {
                ReminderSettings.markCompleted(this, rule.id, rule.dailyTarget);
                ReminderScheduler.update(this);
                break;
            }
        }
        finish();
    }

    private void askReason() {
        EditText reason = new EditText(this);
        reason.setHint("备注，一句话就好，就1s钟解释，你必须填！");
        reason.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle("这次没做的原因")
                .setMessage("写一句就好，帮自己留个记录。")
                .setView(reason)
                .setNegativeButton("再想想", null)
                .setPositiveButton("提交", (dialog, which) -> {
                    String value = reason.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(this, "请填写一句原因", Toast.LENGTH_SHORT).show();
                        askReason();
                        return;
                    }
                    ReminderRecord.finish(this, sessionId, action, ReminderRecord.KIND_MISSED,
                            startedAt, System.currentTimeMillis(), value);
                    finish();
                }).show();
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
