package io.iftech.healthreminder;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 宝箱开箱弹窗：箱体从底部蹦上来，停一下开盖，彩带飘落。 */
final class ChestDialog {
    private ChestDialog() { }

    static void show(final Activity activity, final String chestName, final String reward) {
        final Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        final FrameLayout frame = new FrameLayout(activity);

        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(activity, 26), dp(activity, 30), dp(activity, 26), dp(activity, 22));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(UiPalette.surface(Color.WHITE));
        bg.setCornerRadius(dp(activity, 22));
        bg.setStroke(dp(activity, 1), UiPalette.BORDER);
        card.setBackground(bg);
        frame.addView(card, new FrameLayout.LayoutParams(dp(activity, 280), -2, Gravity.CENTER));

        final TextView box = new TextView(activity);
        box.setText("\uD83C\uDF81");
        box.setTextSize(58);
        box.setGravity(Gravity.CENTER);
        card.addView(box);

        TextView title = new TextView(activity);
        title.setText("获得 " + chestName + "宝箱");
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(UiPalette.INK);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
        titleParams.topMargin = dp(activity, 10);
        card.addView(title, titleParams);

        TextView rewardView = new TextView(activity);
        rewardView.setText(reward);
        rewardView.setTextSize(14);
        rewardView.setTextColor(UiPalette.MUTED);
        rewardView.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rewardParams = new LinearLayout.LayoutParams(-2, -2);
        rewardParams.topMargin = dp(activity, 6);
        card.addView(rewardView, rewardParams);

        TextView close = new TextView(activity);
        close.setText("收下");
        close.setTextSize(15);
        close.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        close.setTextColor(Color.WHITE);
        close.setGravity(Gravity.CENTER);
        GradientDrawable btn = new GradientDrawable();
        btn.setColor(UiPalette.ACCENT);
        btn.setCornerRadius(dp(activity, 20));
        close.setBackground(btn);
        close.setPadding(dp(activity, 28), dp(activity, 10), dp(activity, 28), dp(activity, 10));
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-2, -2);
        closeParams.topMargin = dp(activity, 18);
        card.addView(close, closeParams);
        close.setOnClickListener(v -> dialog.dismiss());
        AnimKit.press(close);

        // 彩带层盖在卡片上方
        final ConfettiView confetti = new ConfettiView(activity);
        frame.addView(confetti, new FrameLayout.LayoutParams(-1, -1));

        dialog.setContentView(frame);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(-1, -1);
            window.setDimAmount(0.45f);
        }

        // 箱体蹦上来 → 停 200ms → 开盖 + 彩带
        AnimKit.bounceIn(box, () -> {
            box.setText("\u2728");
            AnimKit.pop(box, 1.35f);
            confetti.start();
        });
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
