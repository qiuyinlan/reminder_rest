package io.iftech.healthreminder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class CompanionLandingActivity extends Activity {
    private static final int GREEN = 0xff277049;
    private static final int MUTED = 0xff65776a;
    private static final int PAPER = 0xfff4f8f1;
    private android.animation.ObjectAnimator breatheX;
    private android.animation.ObjectAnimator breatheY;
    /* private boolean focusMode; */
    /* private TextView companionOption; */
    /* private TextView focusOption; */
    private TextView prompt;
    private TextView detail;
    private TextView start;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(Color.WHITE);

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(PAPER);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(26), dp(44), dp(26), dp(26));
        screen.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView title = text("陪伴", 23, GREEN, true);
        content.addView(title);
        /*
        LinearLayout modes = new LinearLayout(this);
        modes.setPadding(dp(4), dp(4), dp(4), dp(4));
        modes.setBackground(round(0xffe4ece2, 22));
        companionOption = modeOption("陪伴");
        focusOption = modeOption("专注");
        companionOption.setOnClickListener(v -> setFocusMode(false));
        focusOption.setOnClickListener(v -> setFocusMode(true));
        modes.addView(companionOption, new LinearLayout.LayoutParams(0, dp(40), 1));
        modes.addView(focusOption, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams modesParams = new LinearLayout.LayoutParams(-1, -2);
        modesParams.topMargin = dp(14);
        content.addView(modes, modesParams);
        */
        WellnessAnimationView face = new WellnessAnimationView(this, WellnessAnimationView.Mood.BREATHING);
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(dp(224), dp(224));
        faceParams.topMargin = dp(30);
        content.addView(face, faceParams);
        breatheX = android.animation.ObjectAnimator.ofFloat(face, View.SCALE_X, 1f, 1.09f, 1f);
        breatheY = android.animation.ObjectAnimator.ofFloat(face, View.SCALE_Y, 1f, 1.09f, 1f);
        breatheX.setDuration(4200);
        breatheY.setDuration(4200);
        breatheX.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        breatheY.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        breatheX.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        breatheY.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        breatheX.start();
        breatheY.start();

        prompt = text("现在开始一段安静的陪伴", 17, 0xff23342a, true);
        prompt.setGravity(Gravity.CENTER);
        prompt.setPadding(0, dp(32), 0, dp(10));
        content.addView(prompt);
        detail = text("可选择喜欢的环境音，随时结束。", 13, MUTED, false);
        detail.setGravity(Gravity.CENTER);
        content.addView(detail);

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        start = text("开始陪伴", 16, Color.WHITE, true);
        start.setGravity(Gravity.CENTER);
        start.setBackground(round(GREEN, 18));
        start.setOnClickListener(v -> {
            Intent companion = new Intent(this, CompanionActivity.class);
            /* companion.putExtra(CompanionAudioService.EXTRA_FOCUS_MODE, focusMode); */
            startActivity(companion);
            overridePendingTransition(0, 0);
            finish();
        });
        content.addView(start, new LinearLayout.LayoutParams(-1, dp(54)));
        // setFocusMode(false);

        screen.addView(createNavigation(), new LinearLayout.LayoutParams(-1, dp(60)));
        setContentView(screen);
    }

    /*
    private TextView modeOption(String label) {
        TextView option = text(label, 14, MUTED, true);
        option.setGravity(Gravity.CENTER);
        return option;
    }
    */

    /*
    private void setFocusMode(boolean focus) {
        focusMode = focus;
        companionOption.setTextColor(focus ? MUTED : Color.WHITE);
        companionOption.setBackground(round(focus ? Color.TRANSPARENT : GREEN, 18));
        focusOption.setTextColor(focus ? Color.WHITE : MUTED);
        focusOption.setBackground(round(focus ? GREEN : Color.TRANSPARENT, 18));
        prompt.setText(focus ? "给自己一段专注时间" : "现在开始一段安静的陪伴");
        detail.setText(focus ? "安静做手边的事，结束时会记下专注时长。" : "可选择喜欢的环境音，随时结束。");
        start.setText(focus ? "开始专注" : "开始陪伴");
    }
    */

    @Override protected void onDestroy() {
        if (breatheX != null) breatheX.cancel();
        if (breatheY != null) breatheY.cancel();
        super.onDestroy();
    }

    private LinearLayout createNavigation() {
        return BottomNavigationBar.create(this, BottomNavigationBar.COMPANION,
                () -> open(MainActivity.class),
                () -> open(ItemsActivity.class),
                null,
                () -> open(StatsActivity.class));
    }

    private void open(Class<?> destination) {
        startActivity(new Intent(this, destination));
        overridePendingTransition(0, 0);
        finish();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
