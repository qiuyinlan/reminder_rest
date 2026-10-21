package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class CompanionLandingActivity extends Activity {
    private static final int ACCENT = UiPalette.ACCENT;
    private static final int MINT_LIGHT = UiPalette.SOFT;
    private static final int MINT_PALE = UiPalette.PALE;
    private static final int INK = UiPalette.INK;
    private static final int MUTED = UiPalette.MUTED;
    private static final int PAPER = UiPalette.PAGE;
    private android.animation.ObjectAnimator breatheX;
    private android.animation.ObjectAnimator breatheY;
    private boolean focusMode;
    private TextView companionOption;
    private TextView focusOption;
    private TextView prompt;
    private TextView detail;
    private TextView start;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackground(UiPalette.page());

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(32), dp(24), dp(24));
        screen.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView title = text("陪伴", 23, INK, true);
        title.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        content.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView intro = text("给自己一点安静时间", 14, MUTED, false);
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(-1, -2);
        introParams.topMargin = dp(5);
        content.addView(intro, introParams);

        LinearLayout modes = new LinearLayout(this);
        modes.setPadding(dp(4), dp(4), dp(4), dp(4));
        modes.setBackground(round(MINT_PALE, 12));
        companionOption = modeOption("轻松陪伴");
        focusOption = modeOption("专注陪伴");
        companionOption.setOnClickListener(v -> setFocusMode(false));
        focusOption.setOnClickListener(v -> setFocusMode(true));
        modes.addView(companionOption, new LinearLayout.LayoutParams(0, dp(40), 1));
        modes.addView(focusOption, new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout modeHeading = new LinearLayout(this);
        modeHeading.setGravity(Gravity.CENTER_VERTICAL);
        TextView modeLabel = text("陪伴方式", 14, MUTED, true);
        modeHeading.addView(modeLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView info = text("i", 13, ACCENT, true);
        info.setGravity(Gravity.CENTER);
        info.setTextColor(ACCENT);
        info.setBackground(round(MINT_PALE, 14));
        info.setContentDescription("陪伴方式说明");
        info.setOnClickListener(v -> showStyledDialog(new AlertDialog.Builder(dialogContext())
                .setTitle("陪伴方式")
                .setMessage("轻松陪伴可以切到后台；专注陪伴会固定在全屏页面，系统会提示你固定此应用，固定期间不能切换到其他应用。")
                .setPositiveButton("知道了", null)
                .create()));
        modeHeading.addView(info, new LinearLayout.LayoutParams(dp(28), dp(28)));
        LinearLayout modeCard = new LinearLayout(this);
        modeCard.setOrientation(LinearLayout.VERTICAL);
        modeCard.setPadding(dp(16), dp(14), dp(16), dp(14));
        modeCard.setBackground(round(Color.WHITE, 18));
        modeCard.setElevation(dp(1));
        modeCard.addView(modeHeading, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams modesParams = new LinearLayout.LayoutParams(-1, -2);
        modesParams.topMargin = dp(10);
        modeCard.addView(modes, modesParams);
        LinearLayout.LayoutParams modeCardParams = new LinearLayout.LayoutParams(-1, -2);
        modeCardParams.topMargin = dp(20);
        content.addView(modeCard, modeCardParams);

        WellnessAnimationView face = new WellnessAnimationView(this, WellnessAnimationView.Mood.BREATHING);
        LinearLayout illustration = new LinearLayout(this);
        illustration.setGravity(Gravity.CENTER);
        illustration.setBackground(round(MINT_LIGHT, 18));
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(dp(208), dp(208));
        illustration.addView(face, faceParams);
        LinearLayout.LayoutParams illustrationParams = new LinearLayout.LayoutParams(-1, dp(232));
        illustrationParams.topMargin = dp(18);
        content.addView(illustration, illustrationParams);
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

        prompt = text("现在开始一段安静的陪伴", 17, INK, true);
        prompt.setGravity(Gravity.CENTER);
        prompt.setPadding(0, dp(16), 0, dp(8));
        content.addView(prompt);
        detail = text("可选择喜欢的环境音，随时结束。", 13, MUTED, false);
        detail.setGravity(Gravity.CENTER);
        content.addView(detail);

        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        start = text("开始陪伴", 16, Color.WHITE, true);
        start.setGravity(Gravity.CENTER);
        start.setBackground(round(ACCENT, 16));
        start.setElevation(dp(1));
        start.setOnClickListener(v -> {
            Intent companion = new Intent(this, CompanionActivity.class);
            companion.putExtra(CompanionAudioService.EXTRA_FOCUS_MODE, focusMode);
            startActivity(companion);
            overridePendingTransition(0, 0);
            finish();
        });
        content.addView(start, new LinearLayout.LayoutParams(-1, dp(54)));
        setFocusMode(false);
        screen.addView(createNavigation(), new LinearLayout.LayoutParams(-1, dp(60)));
        setContentView(screen);
    }

    private TextView modeOption(String label) {
        TextView option = text(label, 14, MUTED, true);
        option.setGravity(Gravity.CENTER);
        return option;
    }

    private void setFocusMode(boolean focus) {
        focusMode = focus;
        companionOption.setTextColor(focus ? MUTED : ACCENT);
        companionOption.setBackground(round(focus ? Color.TRANSPARENT : MINT_LIGHT, 10));
        focusOption.setTextColor(focus ? ACCENT : MUTED);
        focusOption.setBackground(round(focus ? MINT_LIGHT : Color.TRANSPARENT, 10));
        prompt.setText(focus ? "给自己一段专注时间" : "现在开始一段安静的陪伴");
        detail.setText(focus ? "霸屏专注，结束后会记录陪伴时长。" : "可以切到后台，随时回来或结束。");
        start.setText(focus ? "开始专注陪伴" : "开始轻松陪伴");
    }

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
        shape.setColor(UiPalette.surface(color));
        shape.setCornerRadius(dp(radius));
        if (color == Color.WHITE) shape.setStroke(dp(1), UiPalette.BORDER);
        return shape;
    }

    private Context dialogContext() {
        return new ContextThemeWrapper(this, R.style.ItemsDialogTheme);
    }

    private void showStyledDialog(AlertDialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(round(Color.WHITE, 16));
            window.setDimAmount(0.34f);
            window.setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(36), dp(480)),
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(INK);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(MUTED);
            message.setLineSpacing(dp(2), 1f);
        }
        for (int buttonId : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
                AlertDialog.BUTTON_NEUTRAL}) {
            android.widget.Button button = dialog.getButton(buttonId);
            if (button == null) continue;
            button.setAllCaps(false);
            button.setTextSize(14);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setTextColor(buttonId == AlertDialog.BUTTON_POSITIVE ? ACCENT : MUTED);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
