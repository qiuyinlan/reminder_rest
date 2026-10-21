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
import android.widget.ScrollView;
import android.widget.TextView;

public final class SettingsActivity extends Activity {
    private int GREEN = UiPalette.ACCENT;
    private int MINT_LIGHT = UiPalette.SOFT;
    private int MINT_PALE = UiPalette.PALE;
    private int INK = UiPalette.INK;
    private int MUTED = UiPalette.MUTED;
    private int PAPER = UiPalette.PAGE;
    private int LINE = UiPalette.BORDER;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackground(UiPalette.page());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(10), dp(20), dp(30));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 29, INK, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(Color.WHITE, 14));
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = text("设置", 25, INK, true);
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title);
        page.addView(header);

        TextView subtitle = text("让提醒方式更贴合你的日常", 13, MUTED, false);
        subtitle.setPadding(dp(54), dp(5), 0, 0);
        page.addView(subtitle);

        LinearLayout intro = new LinearLayout(this);
        intro.setGravity(Gravity.CENTER_VERTICAL);
        intro.setPadding(dp(16), dp(16), dp(16), dp(16));
        intro.setBackground(round(MINT_LIGHT, 18));
        intro.setElevation(dp(1));
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(-1, -2);
        introParams.topMargin = dp(22);
        page.addView(intro, introParams);

        TextView introIcon = text("息", 17, GREEN, true);
        introIcon.setGravity(Gravity.CENTER);
        introIcon.setBackground(round(Color.WHITE, 14));
        LinearLayout.LayoutParams introIconParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        introIconParams.rightMargin = dp(13);
        intro.addView(introIcon, introIconParams);

        LinearLayout introCopy = new LinearLayout(this);
        introCopy.setOrientation(LinearLayout.VERTICAL);
        introCopy.addView(text("息屏时也能准时提醒", 16, INK, true));
        TextView introDetail = text("管理息屏计时、闹钟声音、振动和锁屏权限。", 12, MUTED, false);
        introDetail.setPadding(0, dp(5), 0, 0);
        introCopy.addView(introDetail);
        intro.addView(introCopy, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout appearanceHeader = new LinearLayout(this);
        appearanceHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams appearanceHeaderParams = new LinearLayout.LayoutParams(-1, -2);
        appearanceHeaderParams.topMargin = dp(26);
        appearanceHeaderParams.bottomMargin = dp(10);
        page.addView(appearanceHeader, appearanceHeaderParams);
        appearanceHeader.addView(text("外观", 14, INK, true),
                new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout appearanceCard = new LinearLayout(this);
        appearanceCard.setOrientation(LinearLayout.VERTICAL);
        appearanceCard.setPadding(dp(15), dp(15), dp(15), dp(15));
        appearanceCard.setBackground(round(Color.WHITE, 17));
        appearanceCard.setElevation(dp(1));
        page.addView(appearanceCard, new LinearLayout.LayoutParams(-1, -2));
        appearanceCard.addView(text("主题色", 16, INK, true));

        boolean darkThemeNow = UiPalette.isDark();
        LinearLayout themeOptions = new LinearLayout(this);
        themeOptions.setPadding(dp(3), dp(3), dp(3), dp(3));
        themeOptions.setBackground(round(MINT_PALE, 12));
        themeOptions.addView(themePill("深绿", darkThemeNow), new LinearLayout.LayoutParams(0, dp(38), 1));
        themeOptions.addView(themePill("浅绿", !darkThemeNow), new LinearLayout.LayoutParams(0, dp(38), 1));
        LinearLayout.LayoutParams themeOptionsParams = new LinearLayout.LayoutParams(-1, -2);
        themeOptionsParams.topMargin = dp(10);
        appearanceCard.addView(themeOptions, themeOptionsParams);
        TextView themeNote = text("切换后会重新打开首页，已选主题会保留。", 12, MUTED, false);
        themeNote.setPadding(0, dp(9), 0, 0);
        appearanceCard.addView(themeNote);

        LinearLayout sectionHeader = new LinearLayout(this);
        sectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.topMargin = dp(26);
        sectionParams.bottomMargin = dp(10);
        page.addView(sectionHeader, sectionParams);
        sectionHeader.addView(text("提醒与权限", 14, INK, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView sectionTag = text("1 项", 11, GREEN, true);
        sectionTag.setPadding(dp(9), dp(5), dp(9), dp(5));
        sectionTag.setBackground(round(MINT_PALE, 12));
        sectionHeader.addView(sectionTag);

        LinearLayout settingCard = new LinearLayout(this);
        settingCard.setGravity(Gravity.CENTER_VERTICAL);
        settingCard.setPadding(dp(15), dp(15), dp(14), dp(15));
        settingCard.setBackground(round(Color.WHITE, 17));
        settingCard.setElevation(dp(1));
        settingCard.setContentDescription("息屏设置，管理息屏计时、闹钟铃声、振动和锁屏权限");
        settingCard.setOnClickListener(v -> startActivity(new Intent(this, ScreenOffSettingsActivity.class)));
        page.addView(settingCard, new LinearLayout.LayoutParams(-1, -2));

        TextView settingIcon = text("铃", 16, GREEN, true);
        settingIcon.setGravity(Gravity.CENTER);
        settingIcon.setBackground(round(MINT_PALE, 13));
        LinearLayout.LayoutParams settingIconParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        settingIconParams.rightMargin = dp(13);
        settingCard.addView(settingIcon, settingIconParams);

        LinearLayout settingCopy = new LinearLayout(this);
        settingCopy.setOrientation(LinearLayout.VERTICAL);
        settingCopy.addView(text("息屏提醒", 16, INK, true));
        TextView settingDetail = text("计时方式、闹钟铃声、振动与权限检查", 12, MUTED, false);
        settingDetail.setMaxLines(2);
        settingDetail.setPadding(0, dp(5), 0, 0);
        settingCopy.addView(settingDetail);
        settingCard.addView(settingCopy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = text("›", 25, GREEN, false);
        arrow.setGravity(Gravity.CENTER);
        settingCard.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(40)));

        LinearLayout note = new LinearLayout(this);
        note.setOrientation(LinearLayout.VERTICAL);
        note.setPadding(dp(15), dp(13), dp(15), dp(13));
        note.setBackground(round(Color.WHITE, 16));
        note.setElevation(dp(1));
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(13);
        page.addView(note, noteParams);
        note.addView(text("息屏提醒小贴士", 13, INK, true));
        TextView noteBody = text("如果提醒没有按时出现，可以在息屏设置中查看精确闹钟、通知和锁屏全屏提醒权限。", 12, MUTED, false);
        noteBody.setPadding(0, dp(5), 0, 0);
        note.addView(noteBody);
    }

    private TextView themePill(String label, boolean selected) {
        TextView view = text(label, 15, selected ? Color.WHITE : INK, selected);
        view.setGravity(Gravity.CENTER);
        if (selected) view.setBackground(round(GREEN, 10));
        view.setOnClickListener(v -> {
            String target = "深绿".equals(label) ? UiPalette.THEME_DARK : UiPalette.THEME_LIGHT;
            if (target.equals(UiPalette.theme(this))) return;
            UiPalette.setTheme(this, target);
            android.widget.Toast.makeText(this, "已切换为" + label + "主题", android.widget.Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(this, MainActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
        return view;
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
        shape.setStroke(dp(1), UiPalette.BORDER);
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
