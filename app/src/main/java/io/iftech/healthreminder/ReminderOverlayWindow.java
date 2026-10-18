package io.iftech.healthreminder;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

final class ReminderOverlayWindow {
    private static View currentView;
    private static WindowManager currentManager;

    private ReminderOverlayWindow() { }

    static synchronized boolean show(Context context, String action) {
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
        root.setPadding(dp(context, 24), dp(context, 26), dp(context, 24), dp(context, 24));
        root.setBackgroundColor(0xffe4f2e3);

        TextView overline = text(context, "哪怕做一秒，身体也会舒服一点！\n求求主人了。", 17, 0xff277049, true);
        overline.setGravity(Gravity.CENTER);
        overline.setPadding(0, 0, 0, dp(context, 8));
        root.addView(overline, new LinearLayout.LayoutParams(-1, -2));

        WellnessAnimationView face = new WellnessAnimationView(context,
                action.contains("护眼") ? WellnessAnimationView.Mood.TIRED : WellnessAnimationView.Mood.SAD);
        root.addView(face, new LinearLayout.LayoutParams(dp(context, 250), dp(context, 250)));

        TextView title = text(context, ReminderActivity.titleFor(action), 28, 0xff23342a, true);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView plea = text(context, ReminderActivity.pleaFor(action), 17, 0xff53685a, false);
        plea.setGravity(Gravity.CENTER);
        plea.setPadding(0, dp(context, 10), 0, dp(context, 18));
        root.addView(plea, new LinearLayout.LayoutParams(-1, -2));

        Button start = button(context, "好，我去做", true);
        start.setOnClickListener(v -> {
            dismiss(context);
            Intent open = new Intent(context, ReminderActivity.class)
                    .putExtra("action", reminderAction)
                    .putExtra("begin_immediately", true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            context.startActivity(open);
        });
        root.addView(start, new LinearLayout.LayoutParams(-1, dp(context, 54)));

        TextView later = text(context, "临时有事，稍后提醒我", 15, 0xff277049, true);
        later.setGravity(Gravity.CENTER);
        later.setPadding(0, dp(context, 12), 0, dp(context, 4));
        later.setOnClickListener(v -> {
            ReminderRecord.recordSnoozed(context, reminderAction, System.currentTimeMillis());
            ReminderScheduler.scheduleSnooze(context, 5, reminderAction);
            dismiss(context);
        });
        root.addView(later, new LinearLayout.LayoutParams(-1, dp(context, 48)));

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
        button.setTextColor(primary ? Color.WHITE : 0xff277049);
        button.setAllCaps(false);
        button.setBackgroundColor(primary ? 0xff277049 : 0xffd4e8d2);
        return button;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
