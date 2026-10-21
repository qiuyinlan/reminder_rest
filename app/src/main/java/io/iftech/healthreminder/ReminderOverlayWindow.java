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
import android.widget.TextView;

final class ReminderOverlayWindow {
    private static final int PAGE_BACKGROUND = UiPalette.PAGE;
    private static final int SOFT_BACKGROUND = UiPalette.SOFT;
    private static final int ACCENT = UiPalette.ACCENT;
    private static final int TEXT_PRIMARY = UiPalette.INK;
    private static final int TEXT_MUTED = UiPalette.MUTED;

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
        root.setPadding(dp(context, 20), dp(context, 28), dp(context, 20), dp(context, 18));
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xfff6fcf8, 0xffeeF8f1, 0xffe9f6ef}));

        LinearLayout top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView close = text(context, "×", 28, TEXT_PRIMARY, false);
        close.setGravity(Gravity.CENTER);
        close.setBackground(round(context, Color.WHITE, 40));
        close.setContentDescription("关闭提醒画面");
        close.setOnClickListener(v -> dismiss(context));
        top.addView(close, new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
        TextView overline = text(context, "哪怕做一秒，身体也会舒服一点！\n求求主人了。", 15, TEXT_PRIMARY, true);
        overline.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams overlineParams = new LinearLayout.LayoutParams(0, -2, 1);
        overlineParams.leftMargin = dp(context, 12);
        overlineParams.rightMargin = dp(context, 12);
        top.addView(overline, overlineParams);
        top.addView(new View(context), new LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)));
        root.addView(top);
        root.addView(new View(context), new LinearLayout.LayoutParams(1, 0, 1));

        WellnessAnimationView.Mood mood = action.contains("喝水") || action.contains("饮水") || action.contains("补水")
                ? WellnessAnimationView.Mood.SEED
                : action.contains("护眼") ? WellnessAnimationView.Mood.TIRED : WellnessAnimationView.Mood.SAD;
        WellnessAnimationView face = new WellnessAnimationView(context, mood);
        FrameLayout scene = new FrameLayout(context);
        scene.setBackground(round(context, Color.WHITE, 120));
        scene.setElevation(dp(context, 1));
        scene.addView(face, new FrameLayout.LayoutParams(dp(context, 260), dp(context, 260), Gravity.CENTER));
        root.addView(scene, new LinearLayout.LayoutParams(-1, dp(context, 300)));

        TextView title = text(context, ReminderActivity.titleFor(action), 27, TEXT_PRIMARY, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(context, 20);
        root.addView(title, titleParams);
        TextView plea = text(context, ReminderSettings.reminderMessage(context, ruleId, action), 16, TEXT_MUTED, false);
        plea.setGravity(Gravity.CENTER);
        plea.setLineSpacing(dp(context, 3), 1f);
        plea.setPadding(dp(context, 8), dp(context, 10), dp(context, 8), 0);
        root.addView(plea, new LinearLayout.LayoutParams(-1, -2));
        root.addView(new View(context), new LinearLayout.LayoutParams(1, 0, 1));

        Button start = button(context, "好，我去做", true);
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(dp(context, 280), dp(context, 58));
        startParams.bottomMargin = dp(context, 8);
        root.addView(start, startParams);
        start.setOnClickListener(v -> {
            dismiss(context);
            Intent open = new Intent(context, ReminderActivity.class)
                    .putExtra("action", reminderAction)
                    .putExtra("rule_id", ruleId)
                    .putExtra("begin_immediately", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(open);
        });
        TextView later = text(context, "临时有事，稍后提醒我", 15, TEXT_PRIMARY, true);
        later.setGravity(Gravity.CENTER);
        later.setPadding(dp(context, 10), 0, dp(context, 10), 0);
        later.setBackground(round(context, SOFT_BACKGROUND, 18));
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

        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(root, new android.widget.ScrollView.LayoutParams(-1, -2));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                -1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        try {
            manager.addView(scroll, params);
            currentView = scroll;
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
        drawable.setColor(UiPalette.surface(color));
        drawable.setCornerRadius(dp(context, radius));
        if (color == Color.WHITE) drawable.setStroke(dp(context, 1), UiPalette.BORDER);
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
