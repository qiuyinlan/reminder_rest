package io.iftech.healthreminder;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

final class ReminderOverlayWindow {
    private static final int PAGE_BACKGROUND = 0xfff5faf8;
    private static final int SOFT_BACKGROUND = 0xffe2f7ef;
    private static final int ACCENT = 0xff62c79f;
    private static final int TEXT_PRIMARY = 0xff2a2f2d;
    private static final int TEXT_MUTED = 0xff78827e;

    private static View currentView;
    private static WindowManager currentManager;

    private ReminderOverlayWindow() { }

    static synchronized boolean show(Context context, String action) {
        return show(context, action, -1);
    }

    static synchronized boolean show(Context context, String action, int ruleId) {
        if (action == null) action = ReminderSettings.nextType(context);
        final String reminderAction = action;
        if (android.os.Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) return false;
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (power != null && !power.isInteractive()) return false;
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguard != null && keyguard.isKeyguardLocked()) return false;

        dismiss(context);
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (manager == null) return false;
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(context, 20), dp(context, 26), dp(context, 20), dp(context, 18));
        root.setBackgroundColor(PAGE_BACKGROUND);

        ScrollView pageScroll = new ScrollView(context);
        pageScroll.setFillViewport(false);
        pageScroll.setVerticalScrollBarEnabled(false);
        LinearLayout reminderCard = new LinearLayout(context);
        reminderCard.setOrientation(LinearLayout.VERTICAL);
        reminderCard.setGravity(Gravity.CENTER_HORIZONTAL);
        reminderCard.setPadding(dp(context, 20), dp(context, 18), dp(context, 20), dp(context, 20));
        reminderCard.setBackground(round(context, Color.WHITE, 28));
        pageScroll.addView(reminderCard, new ScrollView.LayoutParams(-1, -2));
        root.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView overline = text(context, "哪怕做一秒，身体也会舒服一点！\n求求主人了。", 13, ACCENT, true);
        overline.setGravity(Gravity.CENTER);
        overline.setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8));
        overline.setBackground(round(context, SOFT_BACKGROUND, 18));
        reminderCard.addView(overline, new LinearLayout.LayoutParams(-2, -2));

        WellnessAnimationView.Mood mood = action.contains("喝水") || action.contains("饮水") || action.contains("补水")
                ? WellnessAnimationView.Mood.THIRSTY
                : action.contains("护眼") ? WellnessAnimationView.Mood.TIRED : WellnessAnimationView.Mood.SAD;
        WellnessAnimationView face = new WellnessAnimationView(context, mood);
        FrameLayout scene = new FrameLayout(context);
        scene.setBackground(round(context, SOFT_BACKGROUND, 24));
        scene.addView(face, new FrameLayout.LayoutParams(dp(context, 210), dp(context, 210), Gravity.CENTER));
        LinearLayout.LayoutParams sceneParams = new LinearLayout.LayoutParams(-1, dp(context, 205));
        sceneParams.topMargin = dp(context, 12);
        sceneParams.bottomMargin = dp(context, 16);
        reminderCard.addView(scene, sceneParams);

        TextView title = text(context, ReminderActivity.titleFor(action), 27, TEXT_PRIMARY, true);
        title.setGravity(Gravity.CENTER);
        reminderCard.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView plea = text(context, ReminderActivity.pleaFor(action), 16, TEXT_MUTED, false);
        plea.setGravity(Gravity.CENTER);
        plea.setLineSpacing(dp(context, 3), 1f);
        plea.setPadding(0, dp(context, 10), 0, dp(context, 4));
        reminderCard.addView(plea, new LinearLayout.LayoutParams(-1, -2));


        Button start = button(context, "好，我去做", true);
        start.setOnClickListener(v -> {
            dismiss(context);
            Intent open = new Intent(context, ReminderActivity.class)
                    .putExtra("action", reminderAction)
                    .putExtra("rule_id", ruleId)
                    .putExtra("begin_immediately", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(open);
        });
        root.addView(start, new LinearLayout.LayoutParams(-1, dp(context, 54)));

        TextView later = text(context, "临时有事，稍后提醒我", 15, ACCENT, true);
        later.setGravity(Gravity.CENTER);
        later.setPadding(0, 0, 0, 0);
        later.setBackground(round(context, SOFT_BACKGROUND, 16));
        later.setOnClickListener(v -> {
            Intent reason = new Intent(context, ReminderActivity.class)
                    .putExtra("action", reminderAction)
                    .putExtra("rule_id", ruleId)
                    .putExtra("enter_reason", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            dismiss(context);
            context.startActivity(reason);
        });
        LinearLayout.LayoutParams laterParams = new LinearLayout.LayoutParams(-1, dp(context, 48));
        laterParams.topMargin = dp(context, 8);
        root.addView(later, laterParams);

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                -1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            manager.addView(root, params);
            currentView = root;
            currentManager = manager;
            return true;
        } catch (WindowManager.BadTokenException | SecurityException error) {
            Log.e("ReminderOverlay", "Unable to attach reminder overlay", error);
            return false;
        }
    }

    static synchronized void dismiss(Context context) {
        if (currentView == null || currentManager == null) return;
        try { currentManager.removeView(currentView); } catch (IllegalArgumentException ignored) { }
        currentView = null;
        currentManager = null;
    }

    private static TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private static Button button(Context context, String value, boolean primary) {
        Button button = new Button(context);
        button.setText(value);
        button.setTextSize(16);
        button.setTextColor(primary ? Color.WHITE : ACCENT);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(context, 54));
        button.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        button.setBackground(round(context, primary ? ACCENT : SOFT_BACKGROUND, 18));
        return button;
    }

    private static GradientDrawable round(Context context, int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radius));
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
