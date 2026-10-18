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
import android.widget.TextView;

public final class BlockActivity extends Activity {
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
        getWindow().setStatusBarColor(0xffe4f2e3);
        getWindow().setNavigationBarColor(0xffe4f2e3);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xffe4f2e3);
        setContentView(root);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER_HORIZONTAL);
        content.setPadding(dp(24), dp(36), dp(24), dp(24));
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        TextView title = text("先休息一下，好不好？", 26, 0xff23342a, true);
        title.setGravity(Gravity.CENTER);
        content.addView(title);
        WellnessAnimationView face = new WellnessAnimationView(this, WellnessAnimationView.Mood.SAD);
        LinearLayout.LayoutParams faceParams = new LinearLayout.LayoutParams(dp(260), dp(260));
        faceParams.topMargin = dp(20);
        content.addView(face, faceParams);
        TextView plea = text("这个应用正在你的休息名单里。\n起来喝口水、看看远处，再回来也不迟。", 17, 0xff53685a, false);
        plea.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams pleaParams = new LinearLayout.LayoutParams(-1, -2);
        pleaParams.topMargin = dp(14);
        content.addView(plea, pleaParams);
        View spacer = new View(this);
        content.addView(spacer, new LinearLayout.LayoutParams(1, 0, 1));
        Button home = button("先回到桌面", true);
        home.setOnClickListener(v -> leaveForBreak());
        content.addView(home, new LinearLayout.LayoutParams(-1, dp(54)));
        Button continueApp = button("我知道了，继续打开", false);
        continueApp.setOnClickListener(v -> continueBlockedApp());
        content.addView(continueApp, new LinearLayout.LayoutParams(-1, dp(54)));
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
        button.setTextColor(primary ? Color.WHITE : 0xff277049);
        GradientDrawable background = new GradientDrawable();
        background.setColor(primary ? 0xff277049 : 0xffd4e8d2);
        background.setCornerRadius(dp(16));
        button.setBackground(background);
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
