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
    private static View currentView;
    private static WindowManager currentManager;

    private ReminderOverlayWindow() { }

    static synchronized boolean show(Context context, String action) {
        return show(context, action, -1);
    }

    static synchronized boolean show(Context context, String action, int ruleId) {
        if (action == null) action = ReminderSettings.nextType(context);
        final String reminderAction = action;
        ReminderSettings.Rule rule = ruleId >= 0 ? ReminderSettings.ruleById(context, ruleId) : null;
        final boolean bodyModule = rule == null || rule.isBodyModule();
        final Class<?> target = rule != null && rule.isTaskAllModule() ? TaskOverviewReminderActivity.class
                : rule != null && rule.isTaskModule() ? TaskReminderActivity.class
                : rule != null && rule.isCardModule() ? CardReminderActivity.class
                : ReminderActivity.class;
        final String overlineText = bodyModule ? "哪怕做一秒，身体也会舒服一点！\n求求主人了。"
                : rule != null && rule.isTaskAllModule() ? "该看今天的清单了"
                : rule != null && rule.isCardModule() ? "到时间看一张卡片了"
                : "该做这件事了";
        final String primaryLabel = bodyModule ? "好，我去做" : "去看看";
        final String heading;
        if (android.os.Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) return false;
        PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (power != null && !power.isInteractive()) return false;
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguard != null && keyguard.isKeyguardLocked()) return false;

        dismiss(context);
        WindowManager manager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (manager == null) return false;
        String artworkId = ReminderSettings.overlayArtwork(context, ruleId);
        boolean customScene = ReminderArtworkCatalog.isCustomScene(artworkId);
        int pageColor = ReminderArtworkCatalog.scenePageColor(artworkId);
        int surfaceColor = ReminderArtworkCatalog.sceneSurfaceColor(artworkId);
        int textColor = ReminderArtworkCatalog.sceneTextColor(artworkId);
        int mutedColor = ReminderArtworkCatalog.sceneMutedColor(artworkId);
        int accentColor = ReminderArtworkCatalog.sceneAccentColor(artworkId);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(context, 20), dp(context, 28), dp(context, 20), dp(context, 18));
        root.setBackgroundColor(pageColor);

        LinearLayout top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView overline = text(context, overlineText, 15, textColor, true);
        overline.setGravity(Gravity.CENTER);
        top.addView(overline, new LinearLayout.LayoutParams(-1, -2));
        root.addView(top);
        root.addView(new View(context), new LinearLayout.LayoutParams(1, 0, 1));

        FrameLayout scene = new FrameLayout(context);
        scene.setBackground(round(context, customScene ? surfaceColor : ReminderArtworkCatalog.drawable(artworkId) == 0
                ? Color.WHITE : UiPalette.SOFT,
                ReminderArtworkCatalog.drawable(artworkId) == 0 ? 120 : 18));
        scene.setElevation(dp(context, 1));
        scene.addView(new ReminderArtworkView(context, artworkId),
                new FrameLayout.LayoutParams(-1, -1));
        root.addView(scene, new LinearLayout.LayoutParams(-1, dp(context, 300)));

        if (bodyModule) {
            heading = ReminderActivity.titleFor(action);
        } else if (rule != null && !rule.title.isEmpty()) {
            heading = rule.title;
        } else if (rule != null && rule.isTaskAllModule()) {
            heading = "今天的清单";
        } else {
            heading = ReminderActivity.titleFor(action);
        }
        TextView title = text(context, heading, 27, textColor, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(context, 20);
        root.addView(title, titleParams);
        String message;
        if (bodyModule) {
            message = ReminderSettings.reminderMessage(context, ruleId, action);
        } else if (!rule.reminderMessage.isEmpty()) {
            message = rule.reminderMessage;
        } else if (rule.isTaskAllModule()) {
            message = "还有没做完的，看一眼勾掉。";
        } else if (rule.isCardModule()) {
            message = "到时间了，看一张卡片。";
        } else {
            message = "现在就做，做完勾掉。";
        }
        TextView plea = text(context, message, 16, mutedColor, false);
        plea.setGravity(Gravity.CENTER);
        plea.setLineSpacing(dp(context, 3), 1f);
        plea.setPadding(dp(context, 8), dp(context, 10), dp(context, 8), 0);
        root.addView(plea, new LinearLayout.LayoutParams(-1, -2));
        root.addView(new View(context), new LinearLayout.LayoutParams(1, 0, 1));

        Button start = button(context, primaryLabel, accentColor, Color.WHITE);
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(dp(context, 280), dp(context, 58));
        startParams.bottomMargin = dp(context, 8);
        root.addView(start, startParams);
        start.setOnClickListener(v -> {
            dismiss(context);
            Intent open = new Intent(context, target)
                    .putExtra("action", reminderAction)
                    .putExtra("rule_id", ruleId)
                    .putExtra("begin_immediately", bodyModule)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            try {
                context.startActivity(open);
            } catch (RuntimeException error) {
                Log.e("ReminderOverlay", "unable to open reminder page", error);
            }
        });
        TextView later = text(context, bodyModule ? "临时有事，稍后提醒我" : "10 分钟后再提醒", 15, textColor, true);
        later.setGravity(Gravity.CENTER);
        later.setPadding(dp(context, 10), 0, dp(context, 10), 0);
        later.setBackground(round(context, customScene ? surfaceColor : UiPalette.SOFT, 18));
        later.setOnClickListener(v -> {
            dismiss(context);
            if (bodyModule) {
                Intent reason = new Intent(context, ReminderActivity.class)
                        .putExtra("action", reminderAction)
                        .putExtra("rule_id", ruleId)
                        .putExtra("enter_reason", true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                context.startActivity(reason);
            } else {
                ReminderScheduler.scheduleSnooze(context, 10, reminderAction, ruleId);
            }
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

    private static Button button(Context context, String value, int backgroundColor, int textColor) {
        Button button = new Button(context);
        button.setText(value);
        button.setTextSize(16);
        button.setTextColor(textColor);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(context, 54));
        button.setPadding(dp(context, 12), 0, dp(context, 12), 0);
        button.setBackground(round(context, backgroundColor, 18));
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
