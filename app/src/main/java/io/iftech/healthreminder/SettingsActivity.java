package io.iftech.healthreminder;

import android.app.Activity;
import android.app.Service;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsActivity extends Activity {
    private int GREEN = UiPalette.ACCENT;
    private int MINT_LIGHT = UiPalette.SOFT;
    private int MINT_PALE = UiPalette.PALE;
    private int INK = UiPalette.INK;
    private int MUTED = UiPalette.MUTED;
    private int PAPER = UiPalette.PAGE;
    private int LINE = UiPalette.BORDER;
    /** 设置页根容器，各个 addXxxSection() 共用一个。 */
    private LinearLayout page;

    @Override protected void onResume() {
        super.onResume();
        AppUpdateManager.resumePendingInstall(this);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackground(UiPalette.page());

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(10), dp(20), dp(30));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));
        setContentView(scroll);
        scroll.post(() -> FormKit.hideEmptyText(scroll));

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

        TextView subtitle = text("", 13, MUTED, false);
        subtitle.setPadding(dp(54), dp(5), 0, 0);

        LinearLayout membership = new LinearLayout(this);
        membership.setGravity(Gravity.CENTER_VERTICAL);
        membership.setPadding(dp(15), dp(14), dp(14), dp(14));
        membership.setBackground(round(Color.WHITE, 14));
        membership.setOnClickListener(v -> startActivity(new Intent(this, MembershipActivity.class)));
        LinearLayout.LayoutParams membershipParams = new LinearLayout.LayoutParams(-1, -2);
        membershipParams.topMargin = dp(16);
        page.addView(membership, membershipParams);
        TextView memberMark = text("会", 17, GREEN, true);
        memberMark.setGravity(Gravity.CENTER);
        memberMark.setBackground(round(MINT_PALE, 12));
        LinearLayout.LayoutParams memberMarkParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        memberMarkParams.rightMargin = dp(12);
        membership.addView(memberMark, memberMarkParams);
        LinearLayout memberCopy = new LinearLayout(this);
        memberCopy.setOrientation(LinearLayout.VERTICAL);
        memberCopy.addView(text("会员与账号", 15, INK, true));
        TextView memberDetail = text("", 12, MUTED, false);
        memberDetail.setPadding(0, dp(4), 0, 0);
        membership.addView(memberCopy, new LinearLayout.LayoutParams(0, -2, 1));
        membership.addView(text("›", 23, GREEN, false));

        LinearLayout update = new LinearLayout(this);
        update.setGravity(Gravity.CENTER_VERTICAL);
        update.setPadding(dp(15), dp(13), dp(14), dp(13));
        update.setBackground(round(Color.WHITE, 14));
        update.setOnClickListener(v -> AppUpdateManager.check(this, true));
        LinearLayout.LayoutParams updateParams = new LinearLayout.LayoutParams(-1, -2);
        updateParams.topMargin = dp(9);
        page.addView(update, updateParams);
        TextView updateMark = text("↻", 21, GREEN, true);
        updateMark.setGravity(Gravity.CENTER);
        updateMark.setBackground(round(MINT_PALE, 12));
        LinearLayout.LayoutParams updateMarkParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        updateMarkParams.rightMargin = dp(12);
        update.addView(updateMark, updateMarkParams);
        LinearLayout updateCopy = new LinearLayout(this);
        updateCopy.setOrientation(LinearLayout.VERTICAL);
        updateCopy.addView(text("检查应用更新", 15, INK, true));
        TextView updateDetail = text("当前 " + BuildConfig.VERSION_NAME
                + " (" + BuildConfig.VERSION_CODE + ")", 12, MUTED, false);
        updateDetail.setPadding(0, dp(4), 0, 0);
        updateCopy.addView(updateDetail);
        update.addView(updateCopy, new LinearLayout.LayoutParams(0, -2, 1));
        update.addView(text("›", 23, GREEN, false));

        LinearLayout profileHeader = new LinearLayout(this);
        profileHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams profileHeaderParams = new LinearLayout.LayoutParams(-1, -2);
        profileHeaderParams.topMargin = dp(26);
        profileHeaderParams.bottomMargin = dp(10);
        page.addView(profileHeader, profileHeaderParams);
        profileHeader.addView(text("个人资料", 14, INK, true),
                new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout profileCard = new LinearLayout(this);
        profileCard.setOrientation(LinearLayout.VERTICAL);
        profileCard.setPadding(dp(15), dp(15), dp(15), dp(15));
        profileCard.setBackground(round(Color.WHITE, 17));
        profileCard.setElevation(dp(1));
        page.addView(profileCard, new LinearLayout.LayoutParams(-1, -2));
        profileCard.addView(text("性别", 16, INK, true));
        TextView genderDetail = text("",
                12, MUTED, false);
        genderDetail.setLineSpacing(dp(3), 1f);
        genderDetail.setPadding(0, dp(6), 0, 0);

        LinearLayout genderOptions = new LinearLayout(this);
        genderOptions.setPadding(dp(4), dp(4), dp(4), dp(4));
        genderOptions.setBackground(round(MINT_PALE, 13));
        String gender = UserProfile.gender(this);
        genderOptions.addView(choicePill("女生", UserProfile.FEMALE.equals(gender), () -> changeGender(UserProfile.FEMALE)),
                new LinearLayout.LayoutParams(0, dp(40), 1));
        genderOptions.addView(choicePill("男生", UserProfile.MALE.equals(gender), () -> changeGender(UserProfile.MALE)),
                new LinearLayout.LayoutParams(0, dp(40), 1));
        genderOptions.addView(choicePill("不指定", UserProfile.UNSET.equals(gender), () -> changeGender(UserProfile.UNSET)),
                new LinearLayout.LayoutParams(0, dp(40), 1));
        LinearLayout.LayoutParams genderOptionsParams = new LinearLayout.LayoutParams(-1, -2);
        genderOptionsParams.topMargin = dp(12);
        profileCard.addView(genderOptions, genderOptionsParams);

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
        TextView introDetail = text("", 12, MUTED, false);
        introDetail.setPadding(0, dp(5), 0, 0);
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
        TextView themeNote = text("", 12, MUTED, false);
        themeNote.setPadding(0, dp(9), 0, 0);

        LinearLayout sectionHeader = new LinearLayout(this);
        sectionHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.topMargin = dp(26);
        sectionParams.bottomMargin = dp(10);
        page.addView(sectionHeader, sectionParams);
        sectionHeader.addView(text("提醒与权限", 14, INK, true),
                new LinearLayout.LayoutParams(0, -2, 1));
        TextView sectionTag = text("3 项", 11, GREEN, true);
        sectionTag.setPadding(dp(9), dp(5), dp(9), dp(5));
        sectionTag.setBackground(round(MINT_PALE, 12));
        sectionHeader.addView(sectionTag);

        LinearLayout gameCard = new LinearLayout(this);
        gameCard.setGravity(Gravity.CENTER_VERTICAL);
        gameCard.setPadding(dp(15), dp(13), dp(14), dp(13));
        gameCard.setBackground(round(Color.WHITE, 17));
        gameCard.setElevation(dp(1));
        LinearLayout.LayoutParams gameCardParams = new LinearLayout.LayoutParams(-1, -2);
        page.addView(gameCard, gameCardParams);

        LinearLayout gameCopy = new LinearLayout(this);
        gameCopy.setOrientation(LinearLayout.VERTICAL);
        gameCopy.addView(text("游戏机制", 16, INK, true));
        TextView gameDetail = text("连胜、宝箱、补签，让坚持更有趣", 12, MUTED, false);
        gameDetail.setPadding(0, dp(5), 0, 0);
        gameCopy.addView(gameDetail);
        gameCard.addView(gameCopy, new LinearLayout.LayoutParams(0, -2, 1));

        Switch gameSwitch = new Switch(this);
        gameSwitch.setChecked(GameSettings.isEnabled(this));
        gameSwitch.setContentDescription("启用游戏机制");
        gameSwitch.setOnCheckedChangeListener((button, checked) -> GameSettings.setEnabled(this, checked));
        gameCard.addView(gameSwitch);

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
        TextView settingDetail = text("", 12, MUTED, false);
        settingDetail.setMaxLines(2);
        settingDetail.setPadding(0, dp(5), 0, 0);
        settingCard.addView(settingCopy, new LinearLayout.LayoutParams(0, -2, 1));

        TextView arrow = text("›", 25, GREEN, false);
        arrow.setGravity(Gravity.CENTER);
        settingCard.addView(arrow, new LinearLayout.LayoutParams(dp(24), dp(40)));

        LinearLayout reviewCard = new LinearLayout(this);
        reviewCard.setGravity(Gravity.CENTER_VERTICAL);
        reviewCard.setPadding(dp(15), dp(13), dp(14), dp(13));
        reviewCard.setBackground(round(Color.WHITE, 17));
        reviewCard.setElevation(dp(1));
        LinearLayout.LayoutParams reviewCardParams = new LinearLayout.LayoutParams(-1, -2);
        reviewCardParams.topMargin = dp(9);
        page.addView(reviewCard, reviewCardParams);

        LinearLayout reviewCopy = new LinearLayout(this);
        reviewCopy.setOrientation(LinearLayout.VERTICAL);
        reviewCopy.addView(text("复盘提醒", 16, INK, true));
        TextView reviewDetail = text("关掉后不在提醒事项里显示", 12, MUTED, false);
        reviewDetail.setPadding(0, dp(5), 0, 0);
        reviewCopy.addView(reviewDetail);
        reviewCard.addView(reviewCopy, new LinearLayout.LayoutParams(0, -2, 1));

        Switch reviewSwitch = new Switch(this);
        reviewSwitch.setChecked(ReviewStore.visible(this));
        reviewSwitch.setContentDescription("显示复盘提醒");
        reviewSwitch.setOnCheckedChangeListener((button, checked) -> ReviewStore.setVisible(this, checked));
        reviewCard.addView(reviewSwitch);

        LinearLayout waterCard = new LinearLayout(this);
        waterCard.setGravity(Gravity.CENTER_VERTICAL);
        waterCard.setPadding(dp(15), dp(13), dp(14), dp(13));
        waterCard.setBackground(round(Color.WHITE, 17));
        waterCard.setElevation(dp(1));
        LinearLayout.LayoutParams waterCardParams = new LinearLayout.LayoutParams(-1, -2);
        waterCardParams.topMargin = dp(9);
        page.addView(waterCard, waterCardParams);

        LinearLayout waterCopy = new LinearLayout(this);
        waterCopy.setOrientation(LinearLayout.VERTICAL);
        waterCopy.addView(text("喝水记录", 16, INK, true));
        TextView waterDetail = text("关掉后首页水杯和统计里的喝水都不显示", 12, MUTED, false);
        waterDetail.setPadding(0, dp(5), 0, 0);
        waterCopy.addView(waterDetail);
        waterCard.addView(waterCopy, new LinearLayout.LayoutParams(0, -2, 1));

        Switch waterSwitch = new Switch(this);
        waterSwitch.setChecked(WaterLog.enabled(this));
        waterSwitch.setContentDescription("显示并记录喝水");
        waterSwitch.setOnCheckedChangeListener((button, checked) -> WaterLog.setEnabled(this, checked));
        waterCard.addView(waterSwitch);

        LinearLayout diagnostics = new LinearLayout(this);
        diagnostics.setGravity(Gravity.CENTER_VERTICAL);
        diagnostics.setPadding(dp(15), dp(14), dp(14), dp(14));
        diagnostics.setBackground(round(Color.WHITE, 17));
        diagnostics.setElevation(dp(1));
        diagnostics.setOnClickListener(v -> startActivity(new Intent(this, ReminderDiagnosticsActivity.class)));
        LinearLayout.LayoutParams diagnosticsParams = new LinearLayout.LayoutParams(-1, -2);
        diagnosticsParams.topMargin = dp(9);
        page.addView(diagnostics, diagnosticsParams);
        TextView diagMark = text("诊", 16, GREEN, true);
        diagMark.setGravity(Gravity.CENTER);
        diagMark.setBackground(round(MINT_PALE, 13));
        LinearLayout.LayoutParams diagMarkParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        diagMarkParams.rightMargin = dp(13);
        diagnostics.addView(diagMark, diagMarkParams);
        LinearLayout diagCopy = new LinearLayout(this);
        diagCopy.setOrientation(LinearLayout.VERTICAL);
        diagCopy.addView(text("提醒诊断", 16, INK, true));
        TextView diagDetail = text("", 12, MUTED, false);
        diagDetail.setPadding(0, dp(5), 0, 0);
        diagnostics.addView(diagCopy, new LinearLayout.LayoutParams(0, -2, 1));
        diagnostics.addView(text("›", 25, GREEN, false));

        LinearLayout dataHeader = new LinearLayout(this);
        dataHeader.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams dataHeaderParams = new LinearLayout.LayoutParams(-1, -2);
        dataHeaderParams.topMargin = dp(26);
        dataHeaderParams.bottomMargin = dp(10);
        page.addView(dataHeader, dataHeaderParams);
        dataHeader.addView(text("数据", 14, INK, true),
                new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout exportCard = new LinearLayout(this);
        exportCard.setGravity(Gravity.CENTER_VERTICAL);
        exportCard.setPadding(dp(15), dp(14), dp(14), dp(14));
        exportCard.setBackground(round(Color.WHITE, 17));
        exportCard.setElevation(dp(1));
        exportCard.setContentDescription("全部数据备份，把提醒、任务、复盘、喝水等打包成一个文件");
        exportCard.setOnClickListener(v -> startActivity(new Intent(this, BodyExportActivity.class)));
        page.addView(exportCard, new LinearLayout.LayoutParams(-1, -2));
        TextView exportMark = text("出", 16, GREEN, true);
        exportMark.setGravity(Gravity.CENTER);
        exportMark.setBackground(round(MINT_PALE, 13));
        LinearLayout.LayoutParams exportMarkParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        exportMarkParams.rightMargin = dp(13);
        exportCard.addView(exportMark, exportMarkParams);
        LinearLayout exportCopy = new LinearLayout(this);
        exportCopy.setOrientation(LinearLayout.VERTICAL);
        exportCopy.addView(text("全部数据备份", 16, INK, true));
        TextView exportDetail = text("提醒、任务、复盘、喝水…打包成一个文件", 12, MUTED, false);
        exportDetail.setPadding(0, dp(5), 0, 0);
        exportCopy.addView(exportDetail);
        exportCard.addView(exportCopy, new LinearLayout.LayoutParams(0, -2, 1));
        exportCard.addView(text("›", 25, GREEN, false));

        LinearLayout importCard = new LinearLayout(this);
        importCard.setGravity(Gravity.CENTER_VERTICAL);
        importCard.setPadding(dp(15), dp(14), dp(14), dp(14));
        importCard.setBackground(round(Color.WHITE, 17));
        importCard.setElevation(dp(1));
        importCard.setContentDescription("恢复全部数据，用备份文件还原并覆盖本机");
        importCard.setOnClickListener(v -> startActivity(new Intent(this, BodyImportActivity.class)));
        LinearLayout.LayoutParams importCardParams = new LinearLayout.LayoutParams(-1, -2);
        importCardParams.topMargin = dp(9);
        page.addView(importCard, importCardParams);
        TextView importMark = text("入", 16, GREEN, true);
        importMark.setGravity(Gravity.CENTER);
        importMark.setBackground(round(MINT_PALE, 13));
        LinearLayout.LayoutParams importMarkParams = new LinearLayout.LayoutParams(dp(46), dp(46));
        importMarkParams.rightMargin = dp(13);
        importCard.addView(importMark, importMarkParams);
        LinearLayout importCopy = new LinearLayout(this);
        importCopy.setOrientation(LinearLayout.VERTICAL);
        importCopy.addView(text("恢复全部数据", 16, INK, true));
        TextView importDetail = text("用备份文件还原，会覆盖本机数据", 12, MUTED, false);
        importDetail.setPadding(0, dp(5), 0, 0);
        importCard.addView(importCopy, new LinearLayout.LayoutParams(0, -2, 1));
        importCard.addView(text("›", 25, GREEN, false));

        String crashLog = CrashLog.last(this);
        if (!crashLog.isEmpty()) {
            LinearLayout crashCard = new LinearLayout(this);
            crashCard.setGravity(Gravity.CENTER_VERTICAL);
            crashCard.setPadding(dp(15), dp(14), dp(14), dp(14));
            crashCard.setBackground(round(Color.WHITE, 17));
            crashCard.setElevation(dp(1));
            crashCard.setOnClickListener(v -> showCrashLog(CrashLog.last(this)));
            LinearLayout.LayoutParams crashParams = new LinearLayout.LayoutParams(-1, -2);
            crashParams.topMargin = dp(9);
            page.addView(crashCard, crashParams);
            TextView crashMark = text("!", 16, UiPalette.WARN, true);
            crashMark.setGravity(Gravity.CENTER);
            crashMark.setBackground(round(MINT_PALE, 13));
            LinearLayout.LayoutParams crashMarkParams = new LinearLayout.LayoutParams(dp(46), dp(46));
            crashMarkParams.rightMargin = dp(13);
            crashCard.addView(crashMark, crashMarkParams);
            LinearLayout crashCopy = new LinearLayout(this);
            crashCopy.setOrientation(LinearLayout.VERTICAL);
            crashCopy.addView(text("上次崩溃日志", 16, INK, true));
            TextView crashDetail = text("", 12, MUTED, false);
            crashDetail.setPadding(0, dp(5), 0, 0);
            crashCard.addView(crashCopy, new LinearLayout.LayoutParams(0, -2, 1));
            crashCard.addView(text("›", 25, GREEN, false));
        }

        // 打卡音效
        LinearLayout soundCard = new LinearLayout(this);
        soundCard.setGravity(Gravity.CENTER_VERTICAL);
        soundCard.setPadding(dp(15), dp(9), dp(14), dp(9));
        soundCard.setBackground(round(Color.WHITE, 17));
        soundCard.setElevation(dp(1));
        LinearLayout.LayoutParams soundParams = new LinearLayout.LayoutParams(-1, -2);
        soundParams.topMargin = dp(26);
        page.addView(soundCard, soundParams);
        LinearLayout soundCopy = new LinearLayout(this);
        soundCopy.setOrientation(LinearLayout.VERTICAL);
        soundCopy.addView(text("打卡音效", 15, INK, true));
        TextView soundSub = text("完成时「嘸」一声，可随时静音", 12, MUTED, false);
        soundSub.setPadding(0, dp(4), 0, 0);
        soundCopy.addView(soundSub);
        soundCard.addView(soundCopy, new LinearLayout.LayoutParams(0, -2, 1));
        Switch soundSwitch = new Switch(this);
        soundSwitch.setChecked(SoundKit.enabled(this));
        soundSwitch.setOnCheckedChangeListener((button, checked) -> {
            SoundKit.setEnabled(this, checked);
            if (checked) SoundKit.tick(this);
        });
        soundCard.addView(soundSwitch);

        // 使用与权限（从首页搬来）
        addUsageSection();
    }

    /** 使用权限 / 白名单 / 软拦截：这些是设置项，不该占首页。 */
    private void addUsageSection() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
        headerParams.topMargin = dp(26);
        headerParams.bottomMargin = dp(10);
        page.addView(header, headerParams);
        header.addView(text("使用与权限", 14, INK, true),
                new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(4), dp(15), dp(4));
        card.setBackground(round(Color.WHITE, 17));
        card.setElevation(dp(1));
        page.addView(card, new LinearLayout.LayoutParams(-1, -2));

        long used = UsageAccess.todayMillis(this);
        boolean hasAccess = UsageAccess.hasAccess(this);
        card.addView(settingRow("今日屏幕使用",
                hasAccess ? UsageAccess.format(used) : "点击开启使用情况访问",
                this::openUsageSettings));
        card.addView(dividerLine());

        Switch onlyWhenUsed = new Switch(this);
        onlyWhenUsed.setText("仅在最近有屏幕使用时提醒");
        onlyWhenUsed.setTextColor(INK);
        onlyWhenUsed.setTextSize(15);
        onlyWhenUsed.setChecked(ReminderSettings.usageOnly(this));
        onlyWhenUsed.setOnCheckedChangeListener((button, checked) -> {
            if (checked && !UsageAccess.hasAccess(this)) {
                button.setChecked(false);
                openUsageSettings();
                return;
            }
            ReminderSettings.prefs(this).edit().putBoolean("usage_only", checked).apply();
            ReminderScheduler.update(this);
        });
        card.addView(onlyWhenUsed, new LinearLayout.LayoutParams(-1, dp(52)));
        card.addView(dividerLine());

        card.addView(settingRow("白名单应用",
                ReminderSettings.whitelist(this).size() + " 个 · 不统计也不拦截",
                () -> startActivity(new Intent(this, AppPickerActivity.class))));
        card.addView(dividerLine());
        card.addView(settingRow("限制应用",
                ReminderSettings.blockPackages(this).size() + " 个 · 到点提示休息",
                () -> startActivity(new Intent(this, AppPickerActivity.class).putExtra("mode", "block"))));
        card.addView(dividerLine());

        Switch blockEnabled = new Switch(this);
        blockEnabled.setText("启用指定 App 软拦截");
        blockEnabled.setTextColor(INK);
        blockEnabled.setTextSize(15);
        blockEnabled.setChecked(ReminderSettings.blockEnabled(this));
        blockEnabled.setOnCheckedChangeListener((button, checked) -> {
            if (checked && ReminderSettings.blockPackages(this).isEmpty()) {
                button.setChecked(false);
                ReminderSettings.prefs(this).edit().putBoolean("enable_block_after_picker", true).apply();
                Toast.makeText(this, "请先选择要限制的应用", Toast.LENGTH_SHORT).show();
                startActivity(new Intent(this, AppPickerActivity.class).putExtra("mode", "block"));
                return;
            }
            ReminderSettings.prefs(this).edit().putBoolean("block_enabled", checked).apply();
            if (checked && !isWatchServiceEnabled()) {
                Toast.makeText(this, "请在系统页面手动启用应用休息提醒服务", Toast.LENGTH_LONG).show();
                openAccessibilitySettings();
            }
            recreate();
        });
        card.addView(blockEnabled, new LinearLayout.LayoutParams(-1, dp(52)));
    }

    private LinearLayout settingRow(String title, String detail, Runnable onClick) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(14), 0, dp(14));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 15, INK, true));
        if (detail != null && !detail.isEmpty()) {
            TextView sub = text(detail, 12, MUTED, false);
            sub.setPadding(0, dp(4), 0, 0);
            copy.addView(sub);
        }
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        if (onClick != null) {
            row.addView(text("›", 22, GREEN, false));
            row.setOnClickListener(v -> onClick.run());
        }
        return row;
    }

    private View dividerLine() {
        View v = new View(this);
        v.setBackgroundColor(LINE);
        v.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return v;
    }

    private void openUsageSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (Exception error) {
            Toast.makeText(this, "无法打开使用情况访问设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception error) {
            Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isWatchServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Service.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        for (AccessibilityServiceInfo info :
                manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() == null) continue;
            ServiceInfo serviceInfo = info.getResolveInfo().serviceInfo;
            if (serviceInfo != null && getPackageName().equals(serviceInfo.packageName)
                    && AppWatchService.class.getName().equals(serviceInfo.name)) return true;
        }
        return false;
    }

    private void showCrashLog(String log) {
        TextView body = text(log, 11, INK, false);
        body.setTextIsSelectable(true);
        body.setPadding(dp(14), dp(10), dp(14), dp(10));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(body);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.addView(scroll, new android.widget.FrameLayout.LayoutParams(-1, dp(360)));
        new android.app.AlertDialog.Builder(this)
                .setTitle("上次崩溃日志")
                .setView(frame)
                .setPositiveButton("复制", (dialog, which) -> {
                    CrashLog.copy(this, log);
                    Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("清空", (dialog, which) -> {
                    CrashLog.clear(this);
                    recreate();
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void changeGender(String gender) {
        if (gender.equals(UserProfile.gender(this))) return;
        UserProfile.setGender(this, gender);
        Toast.makeText(this, "已保存：" + UserProfile.label(this), Toast.LENGTH_SHORT).show();
        recreate();
    }

    private TextView choicePill(String label, boolean selected, Runnable onPick) {
        TextView view = text(label, 13, selected ? Color.WHITE : INK, selected);
        view.setGravity(Gravity.CENTER);
        view.setBackground(selected ? round(GREEN, 10) : round(Color.TRANSPARENT, 10));
        view.setOnClickListener(v -> onPick.run());
        return view;
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
