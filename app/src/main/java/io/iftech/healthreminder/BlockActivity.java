package io.iftech.healthreminder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class BlockActivity extends Activity {
    private int PAGE_BACKGROUND = UiPalette.PAGE;
    private int SOFT_BACKGROUND = UiPalette.SOFT;
    private int ACCENT = UiPalette.ACCENT;
    private int TEXT_PRIMARY = UiPalette.INK;
    private int TEXT_MUTED = UiPalette.MUTED;
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        render();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        render();
    }

    private void render() {
        UiPalette.applyWindow(getWindow());
        FrameLayout root = new FrameLayout(this);
        root.setBackground(UiPalette.page());
        setContentView(root);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(20), dp(12), dp(20), dp(14));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        ScrollView pageScroll = new ScrollView(this);
        pageScroll.setFillViewport(false);
        pageScroll.setVerticalScrollBarEnabled(false);
        LinearLayout blockCard = new LinearLayout(this);
        blockCard.setOrientation(LinearLayout.VERTICAL);
        blockCard.setGravity(Gravity.CENTER_HORIZONTAL);
        blockCard.setPadding(dp(20), dp(20), dp(20), dp(22));
        blockCard.setBackground(round(Color.WHITE, 20));
        blockCard.setElevation(dp(1));
        pageScroll.addView(blockCard, new ScrollView.LayoutParams(-1, -2));
        content.addView(pageScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        TextView title = text("先休息一下，好不好？", 26, TEXT_PRIMARY, true);
        title.setGravity(Gravity.CENTER);
        blockCard.addView(title);
        ReminderArtworkView face = new ReminderArtworkView(this, "sad");
        FrameLayout scene = new FrameLayout(this);
        scene.setBackground(round(SOFT_BACKGROUND, 18));
        scene.addView(face, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(-1, dp(205));
        faceParams.topMargin = dp(14);
        faceParams.bottomMargin = dp(16);
        blockCard.addView(scene, faceParams);
        TextView plea = text("这个应用正在你的休息名单里。\n起来喝口水、看看远处，再回来也不迟。", 16, TEXT_MUTED, false);
        plea.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pleaParams = new LinearLayout.LayoutParams(-1, -2);
        pleaParams.topMargin = dp(14);
        blockCard.addView(plea, pleaParams);
        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(1, 0));
        Button home = button("先回到桌面", true);
        home.setOnClickListener(v -> leaveForBreak());
        content.addView(home, new LinearLayout.LayoutParams(-1, dp(54)));
        Button continueApp = button("我知道了，继续打开", false);
        continueApp.setOnClickListener(v -> continueBlockedApp());
        LinearLayout.LayoutParams continueParams = new LinearLayout.LayoutParams(-1, dp(54));
        continueParams.topMargin = dp(8);
        content.addView(continueApp, continueParams);
    }

    @Override
    public void onBackPressed() {
        leaveForBreak();
    }

    private void leaveForBreak() {
        ReminderSettings.prefs(this).edit().putLong("block_bypass_until", System.currentTimeMillis() + 2_000L).apply();
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private void continueBlockedApp() {
        ReminderSettings.prefs(this).edit().putLong("block_bypass_until", System.currentTimeMillis() + 2 * 60 * 60_000L).apply();
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

    private Button button(String title, boolean primary) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTextColor(primary ? Color.WHITE : ACCENT);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(54));
        button.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? ACCENT : SOFT_BACKGROUND);
        background.setCornerRadius(dp(18));
        if (!primary) background.setStroke(dp(1), UiPalette.BORDER);
        button.setBackground(background);
        button.setElevation(dp(1));
        return button;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(UiPalette.surface(color));
        drawable.setCornerRadius(dp(radius));
        if (color == Color.WHITE) drawable.setStroke(dp(1), UiPalette.BORDER);
        return drawable;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
