package io.iftech.healthreminder;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Service;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.ComponentName;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.accessibility.AccessibilityManager;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationManagerCompat;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity {
    private int GREEN = UiPalette.ACCENT;
    private int INK = UiPalette.INK;
    private int MUTED = UiPalette.MUTED;
    private int PAPER = UiPalette.PAGE;
    private int MINT_LIGHT = UiPalette.SOFT;
    private int MINT_PALE = UiPalette.PALE;
    private int LINE = UiPalette.BORDER;
    private LinearLayout body;
    private ScrollView scroll;
    private boolean reopenPermissionGuide;
    private TextView completedMetric;
    private ProgressBar todayProgress;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        boolean newItem = getIntent().getBooleanExtra("new_item", false);
        int editId = getIntent().getIntExtra("edit_id", -1);
        if (newItem || editId >= 0) {
            Intent editor = new Intent(this, ItemsActivity.class).putExtra("return_to_home", true);
            if (newItem) editor.putExtra("new_item", true);
            if (editId >= 0) editor.putExtra("edit_id", editId);
            startActivity(editor);
            finish();
            return;
        }
        UiPalette.applyWindow(getWindow());
        ReminderScheduler.update(this);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (body != null) {
            render();
            if (reopenPermissionGuide) {
                reopenPermissionGuide = false;
                showPermissionChecklist();
            }
        }
    }

    private void render() {
        int previousScrollY = scroll == null ? 0 : scroll.getScrollY();
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackground(UiPalette.page());
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(UiPalette.page());
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(14), dp(20), dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(screen);
        ScrollView currentScroll = scroll;
        currentScroll.post(() -> currentScroll.scrollTo(0, previousScrollY));

        TextView date = label(DateFormat.getDateInstance(DateFormat.FULL)
                .format(Calendar.getInstance().getTime()), 12, MUTED, false);
        date.setPadding(0, 0, 0, dp(4));
        body.addView(date);
        LinearLayout top = row();
        TextView brand = label("休息一下", 28, INK, true);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView settingsButton = label("设置  ›", 13, INK, true);
        settingsButton.setGravity(Gravity.CENTER);
        settingsButton.setPadding(dp(13), dp(8), dp(13), dp(8));
        settingsButton.setBackground(outline(Color.WHITE, UiPalette.BORDER, 18));
        settingsButton.setContentDescription("设置");
        settingsButton.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        top.addView(settingsButton);
        body.addView(top);
        TextView intro = label("给眼睛和身体留一点放松时间。", 13, MUTED, false);
        LinearLayout.LayoutParams introParams = full();
        introParams.bottomMargin = dp(16);
        body.addView(intro, introParams);

        List<ReminderSettings.Rule> rules = ReminderSettings.rules(this);
        addTodayStatusCard(rules);
        addPermissionBanner();

        LinearLayout sectionHead = row();
        sectionHead.setPadding(0, dp(17), 0, dp(9));
        sectionHead.addView(label("提醒事项", 19, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView manageItems = label("管理全部  ›", 12, INK, true);
        manageItems.setGravity(Gravity.CENTER);
        manageItems.setPadding(dp(11), dp(7), dp(11), dp(7));
        manageItems.setTextColor(GREEN);
        manageItems.setBackground(outline(Color.WHITE, UiPalette.BORDER, 16));
        manageItems.setOnClickListener(v -> startActivity(new Intent(this, ItemsActivity.class)));
        sectionHead.addView(manageItems);
        body.addView(sectionHead);
        LinearLayout schedule = new LinearLayout(this);
        schedule.setOrientation(LinearLayout.VERTICAL);
        schedule.setPadding(0, 0, 0, 0);
        schedule.setBackgroundColor(Color.TRANSPARENT);
        boolean addedBuiltIn = false;
        for (ReminderSettings.Rule rule : rules) {
            addRuleRow(schedule, rule);
            addedBuiltIn = true;
        }
        if (!addedBuiltIn) addEmptyMessage(schedule, "还没有提醒事项，请到事项页添加。");
        body.addView(schedule, full());

        section("使用与权限");
        LinearLayout usage = columnCard();
        long used = UsageAccess.todayMillis(this);
        String usageValue = UsageAccess.hasAccess(this) ? UsageAccess.format(used) : "开启使用情况访问";
        addSettingRow(usage, "今日屏幕使用", usageValue, UsageAccess.hasAccess(this) ? "已排除白名单应用" : "授权后统计各应用的前台时长", this::openUsageSettings);
        addDivider(usage);
        Switch onlyWhenUsed = new Switch(this);
        styleSwitch(onlyWhenUsed);
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
        usage.addView(onlyWhenUsed, new LinearLayout.LayoutParams(-1, dp(52)));

        addDivider(usage);
        addSettingRow(usage, "白名单应用", ReminderSettings.whitelist(this).size() + " 个", "排除统计和软拦截", () -> startActivity(new Intent(this, AppPickerActivity.class)));
        addDivider(usage);
        addSettingRow(usage, "限制应用", ReminderSettings.blockPackages(this).size() + " 个", "选择需要提醒休息的应用", () -> {
            startActivity(new Intent(this, AppPickerActivity.class).putExtra("mode", "block"));
        });
        addDivider(usage);
        Switch blockEnabled = new Switch(this);
        styleSwitch(blockEnabled);
        blockEnabled.setText("启用指定 App 软拦截");
        blockEnabled.setTextColor(INK);
        blockEnabled.setTextSize(15);
        blockEnabled.setChecked(ReminderSettings.blockEnabled(this));
        blockEnabled.setOnCheckedChangeListener((button, checked) -> {
            if (checked && ReminderSettings.blockPackages(this).isEmpty()) {
                button.setChecked(false);
                ReminderSettings.prefs(this).edit().putBoolean("enable_block_after_picker", true).apply();
                startActivityForResult(new Intent(this, AppPickerActivity.class).putExtra("mode", "block"), 22);
                Toast.makeText(this, "请先选择要限制的应用", Toast.LENGTH_SHORT).show();
                return;
            }
            ReminderSettings.prefs(this).edit().putBoolean("block_enabled", checked).apply();
            if (checked && !isWatchServiceEnabled()) {
                Toast.makeText(this, "请在系统页面手动启用应用休息提醒服务", Toast.LENGTH_LONG).show();
                openAccessibilitySettings();
            }
            render();
        });
        usage.addView(blockEnabled, new LinearLayout.LayoutParams(-1, dp(52)));
        TextView blockHint = label(isWatchServiceEnabled()
                ? "无障碍服务已启用，只读取前台应用名称。系统仍允许 Home 键或手势退出。"
                : "需在系统无障碍设置中手动启用；不读取屏幕内容，也不能阻止 Home 键退出。", 11, MUTED, false);
        blockHint.setPadding(dp(2), 0, dp(2), dp(10));
        usage.addView(blockHint);
        body.addView(usage, full());

        LinearLayout nav = BottomNavigationBar.create(this, BottomNavigationBar.HOME, null,
                () -> { startActivity(new Intent(this, ItemsActivity.class)); overridePendingTransition(0, 0); },
                () -> { startActivity(new Intent(this, CompanionLandingActivity.class)); overridePendingTransition(0, 0); },
                () -> { startActivity(new Intent(this, StatsActivity.class)); overridePendingTransition(0, 0); });
        screen.addView(nav, new LinearLayout.LayoutParams(-1, dp(60)));
    }

    private void addTodayStatusCard(List<ReminderSettings.Rule> rules) {
        int completed = 0;
        int target = 0;
        for (ReminderSettings.Rule rule : rules) {
            completed += ReminderSettings.completedToday(this, rule.id);
            target += rule.dailyTarget;
        }

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(12));
        card.setBackground(homeStatusBackground());

        LinearLayout overview = row();
        WellnessAnimationView face = new WellnessAnimationView(this, WellnessAnimationView.Mood.SAD);
        ObjectAnimator bob = ObjectAnimator.ofFloat(face, View.TRANSLATION_Y, 0f, -dp(3), 0f);
        bob.setDuration(2400);
        bob.setRepeatCount(ValueAnimator.INFINITE);
        face.post(bob::start);
        overview.addView(face, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(9), 0, dp(4), 0);
        copy.addView(label(ReminderSettings.enabled(this) ? "提醒正在运行" : "提醒已暂停", 16, INK, true));
        TextView greeting = label(rules.isEmpty()
                ? "添加计划，按时照顾自己" : "忙了一阵子啦，给身体留点空档", 11, MUTED, false);
        greeting.setPadding(0, dp(4), 0, 0);
        copy.addView(greeting);
        overview.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        Switch enabled = new Switch(this);
        styleSwitch(enabled);
        enabled.setChecked(ReminderSettings.enabled(this));
        enabled.setContentDescription(ReminderSettings.enabled(this) ? "暂停今日提醒" : "开启今日提醒");
        enabled.setOnCheckedChangeListener((button, checked) -> {
            ReminderSettings.setEnabled(this, checked);
            if (checked && Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 91);
            }
            Toast.makeText(this, checked ? "提醒已开启" : "提醒已暂停", Toast.LENGTH_SHORT).show();
            render();
        });
        overview.addView(enabled);
        card.addView(overview);

        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.topMargin = dp(12);
        dividerParams.bottomMargin = dp(10);
        card.addView(divider, dividerParams);

        LinearLayout metrics = row();
        metrics.addView(statusMetric("提醒事项", rules.size() + " 项"),
                new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams completionParams = new LinearLayout.LayoutParams(0, -2, 1);
        completionParams.leftMargin = dp(8);
        metrics.addView(statusMetric("今日完成", completed + " / " + target + " 次"), completionParams);
        card.addView(metrics);

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false);
        progress.setMax(Math.max(1, target));
        progress.setProgress(Math.min(completed, Math.max(1, target)));
        progress.setProgressTintList(ColorStateList.valueOf(GREEN));
        progress.setProgressBackgroundTintList(ColorStateList.valueOf(LINE));
        todayProgress = progress;
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(4));
        progressParams.topMargin = dp(9);
        card.addView(progress, progressParams);

        TextView delivery = label("提醒通过系统通知送达", 10, MUTED, false);
        LinearLayout.LayoutParams deliveryParams = full();
        deliveryParams.topMargin = dp(7);
        card.addView(delivery, deliveryParams);

        LinearLayout.LayoutParams cardParams = full();
        cardParams.bottomMargin = dp(12);
        body.addView(card, cardParams);
    }

    private LinearLayout statusMetric(String title, String value) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setPadding(dp(10), dp(8), dp(10), dp(8));
        metric.setBackground(outline("今日完成".equals(title) ? UiPalette.SOFT : UiPalette.PALE, UiPalette.BORDER, 12));
        metric.addView(label(title, 11, MUTED, false));
        TextView amount = label(value, 17, INK, true);
        amount.setPadding(0, dp(2), 0, 0);
        if ("今日完成".equals(title)) completedMetric = amount;
        metric.addView(amount);
        return metric;
    }

    private void addPermissionBanner() {
        boolean notification = notificationPermissionReady();
        boolean exactAlarm = exactAlarmAllowed();
        boolean fullScreen = fullScreenIntentAllowed();
        boolean usage = UsageAccess.hasAccess(this);
        boolean accessibility = isWatchServiceEnabled();
        boolean overlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this);
        boolean batteryExempt = isIgnoringBatteryOptimizations();
        boolean ready = notification && exactAlarm && fullScreen && overlay && batteryExempt
                && (!ReminderSettings.usageOnly(this) || usage)
                && (!ReminderSettings.blockEnabled(this) || accessibility);
        LinearLayout banner = row();
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(11), dp(9), dp(12), dp(9));
        banner.setBackground(outline(ready ? UiPalette.SOFT : UiPalette.WARN_SOFT,
                ready ? UiPalette.BORDER : UiPalette.WARN_BORDER, 14));
        int statusColor = ready ? INK : UiPalette.WARN;
        TextView mark = label(ready ? "✓" : "!", 15, ready ? GREEN : statusColor, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(round(ready ? MINT_LIGHT : UiPalette.WARN_SOFT, 10));
        banner.addView(mark, new LinearLayout.LayoutParams(dp(32), dp(32)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, dp(5), 0);
        copy.addView(label(ready ? "提醒权限已就绪" : "提醒权限待检查", 13, statusColor, true));
        TextView detail = label(ready ? "通知、闹钟与后台运行可用" : "点击检查通知、闹钟和后台设置", 11, MUTED, false);
        detail.setPadding(0, dp(2), 0, 0);
        copy.addView(detail);
        banner.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        banner.addView(label("›", 20, MUTED, false));
        banner.setOnClickListener(v -> showPermissionChecklist());
        LinearLayout.LayoutParams params = full();
        params.bottomMargin = dp(2);
        body.addView(banner, params);
    }

    private void showPermissionChecklist() {
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(20), dp(4), dp(20), 0);
        boolean notification = notificationPermissionReady();
        AlertDialog guide = new AlertDialog.Builder(this).setTitle("完成系统配置")
                .setView(list).setNeutralButton("重新检查", null).setPositiveButton("完成", null).create();
        TextView explanation = label("允许悬浮窗后，屏幕亮且未锁定时会覆盖当前应用；锁屏时由系统通知提醒。", 12, MUTED, false);
        explanation.setPadding(0, 0, 0, dp(8));
        list.addView(explanation, 0);
        addPermissionRow(list, guide, "通知权限", notification, () -> {
            boolean runtimeGranted = Build.VERSION.SDK_INT < 33
                    || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            if (Build.VERSION.SDK_INT >= 33 && !runtimeGranted) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 91);
            } else {
                openNotificationSettings();
            }
        });
        addPermissionRow(list, guide, "精确闹钟", exactAlarmAllowed(), this::openExactAlarmSettings);
        if (Build.VERSION.SDK_INT >= 34) {
            addPermissionRow(list, guide, "锁屏全屏提醒", fullScreenIntentAllowed(), this::openFullScreenSettings);
        }
        addPermissionRow(list, guide, "使用情况访问", UsageAccess.hasAccess(this), this::openUsageSettings);
        addPermissionRow(list, guide, "无障碍服务（读取当前 App 名称）", isWatchServiceEnabled(), this::openAccessibilitySettings);
        addPermissionRow(list, guide, "悬浮窗（其他 App 上显示提醒）", Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this), this::openOverlaySettings);
        addPermissionRow(list, guide, "限制 App 选择", !ReminderSettings.blockPackages(this).isEmpty(), () -> startActivity(new Intent(this, AppPickerActivity.class).putExtra("mode", "block")));
        addPermissionRow(list, guide, "后台运行（忽略电池优化）", isIgnoringBatteryOptimizations(), this::openBatteryOptimizationSettings);
        addActionRow(list, guide, "自启动管理", "打开 Vivo 自启动设置", this::openAutoStartSettings);
        addActionRow(list, guide, "保活壁纸", isWellnessWallpaperSet() ? "已设为壁纸 · 管理壁纸" : "打开系统预览并手动设为壁纸", this::openKeepAliveWallpaper);
        TextView vendorGuide = label("Vivo 还需在系统设置中允许自启动和后台运行，并在最近任务卡片下拉锁定；这些选项需手动开启。", 12, MUTED, false);
        vendorGuide.setPadding(0, dp(10), 0, dp(8));
        list.addView(vendorGuide);
        TextView testOverlay = label("测试覆盖提醒", 15, INK, true);
        testOverlay.setPadding(0, dp(14), 0, dp(14));
        testOverlay.setOnClickListener(v -> {
            guide.dismiss();
            body.postDelayed(() -> {
                if (!ReminderOverlayWindow.show(this, ReminderSettings.nextType(this))) {
                    Toast.makeText(this, "覆盖提醒未显示，请检查悬浮窗权限并保持屏幕解锁", Toast.LENGTH_LONG).show();
                }
            }, 300);
        });
        list.addView(testOverlay);
        guide.show();
        styleDialog(guide);
        guide.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            guide.dismiss();
            render();
            body.post(this::showPermissionChecklist);
        });
    }

    private void addPermissionRow(LinearLayout parent, AlertDialog guide, String title, boolean ready, Runnable action) {
        TextView row = label((ready ? "✓ " : "○ ") + title + (ready ? "  已完成" : "  去设置"), 15, ready ? INK : UiPalette.WARN, true);
        row.setPadding(0, dp(13), 0, dp(13));
        row.setOnClickListener(v -> {
            guide.dismiss();
            reopenPermissionGuide = true;
            action.run();
        });
        parent.addView(row);
    }

    private boolean notificationPermissionReady() {
        boolean runtimeGranted = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
        return runtimeGranted && NotificationManagerCompat.from(this).areNotificationsEnabled();
    }

    private boolean exactAlarmAllowed() {
        if (Build.VERSION.SDK_INT < 31) return true;
        android.app.AlarmManager alarms = getSystemService(android.app.AlarmManager.class);
        return alarms != null && alarms.canScheduleExactAlarms();
    }

    private boolean fullScreenIntentAllowed() {
        if (Build.VERSION.SDK_INT < 34) return true;
        android.app.NotificationManager notifications = getSystemService(android.app.NotificationManager.class);
        return notifications != null && notifications.canUseFullScreenIntent();
    }

    private void openNotificationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch (Exception unavailable) {
            openAppDetails("无法打开通知设置");
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception unavailable) {
            openAppDetails("无法打开精确闹钟设置");
        }
    }

    private void openFullScreenSettings() {
        if (Build.VERSION.SDK_INT < 34) return;
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception unavailable) {
            openAppDetails("无法打开全屏提醒设置");
        }
    }

    private void addActionRow(LinearLayout parent, AlertDialog guide, String title, String detail, Runnable action) {
        LinearLayout row = row();
        row.setPadding(0, dp(12), 0, dp(12));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(title, 15, INK, true));
        TextView subtitle = label(detail, 12, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, 0);
        copy.addView(subtitle);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(label("前往  ›", 13, INK, true));
        row.setOnClickListener(v -> {
            guide.dismiss();
            reopenPermissionGuide = true;
            action.run();
        });
        parent.addView(row);
    }

    private void openAutoStartSettings() {
        ComponentName[] candidates = {
                new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                new ComponentName("com.iqoo.secure", "com.iqoo.secure.safeguard.PurviewTabActivity"),
                new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity")
        };
        for (ComponentName candidate : candidates) {
            try {
                Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(candidate);
                startActivity(intent);
                return;
            } catch (Exception ignored) { }
        }
        openAppDetails("未找到 Vivo 自启动管理页面，请在本应用信息中查找自启动权限");
    }

    private void openAppDetails(String fallbackMessage) {
        try {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception error) {
            Toast.makeText(this, fallbackMessage, Toast.LENGTH_LONG).show();
        }
    }

    private boolean isWellnessWallpaperSet() {
        android.app.WallpaperInfo info = android.app.WallpaperManager.getInstance(this).getWallpaperInfo();
        return info != null && getPackageName().equals(info.getPackageName());
    }

    private void openKeepAliveWallpaper() {
        ComponentName wallpaper = new ComponentName(this, WellnessWallpaperService.class);
        Intent preview = new Intent(android.app.WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                .putExtra(android.app.WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, wallpaper);
        try {
            startActivity(preview);
        } catch (Exception unavailable) {
            try {
                startActivity(new Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER));
            } catch (Exception error) {
                Toast.makeText(this, "无法打开系统壁纸设置", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void openOverlaySettings() {
        if (Build.VERSION.SDK_INT < 23) return;
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception error) {
            Toast.makeText(this, "无法打开悬浮窗设置", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        android.os.PowerManager manager = (android.os.PowerManager) getSystemService(POWER_SERVICE);
        return manager != null && manager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void openBatteryOptimizationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private boolean isBuiltIn(String title) {
        for (String type : ReminderSettings.TYPES) if (type.equals(title)) return true;
        return false;
    }

    private void addEmptyMessage(LinearLayout parent, String message) {
        TextView empty = label(message, 13, MUTED, false);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(dp(8), dp(18), dp(8), dp(18));
        empty.setOnClickListener(v -> startActivity(new Intent(this, ItemsActivity.class)));
        parent.addView(empty);
    }

    private void addRuleRow(LinearLayout parent, ReminderSettings.Rule rule) {
        int done = ReminderSettings.completedToday(this, rule.id);
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(dp(12), dp(11), dp(12), dp(10));
        item.setBackground(outline(iconTint(rule.title), iconBorder(rule.title), 16));
        item.setElevation(dp(1));

        LinearLayout main = row();
        TextView badge = label(rule.title.isEmpty() ? "·" : rule.title.substring(0, 1), 14, iconColor(rule.title), true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(iconTint(rule.title), 10));
        main.addView(badge, new LinearLayout.LayoutParams(dp(34), dp(34)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, dp(6), 0);
        LinearLayout titleLine = row();
        titleLine.addView(label(rule.title, 15, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView targetBadge = label(done >= rule.dailyTarget ? "达标" : done + "/" + rule.dailyTarget,
                10, iconColor(rule.title), true);
        targetBadge.setGravity(Gravity.CENTER);
        targetBadge.setPadding(dp(7), dp(4), dp(7), dp(4));
        targetBadge.setBackground(round(iconTint(rule.title), 12));
        titleLine.addView(targetBadge);
        copy.addView(titleLine);
        TextView schedule = label(compactScheduleSummary(rule), 11, MUTED, false);
        schedule.setSingleLine(true);
        schedule.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams scheduleParams = full();
        scheduleParams.topMargin = dp(3);
        copy.addView(schedule, scheduleParams);
        main.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));

        Switch active = new Switch(this);
        styleSwitch(active);
        active.setChecked(rule.enabled);
        active.setContentDescription((rule.enabled ? "停用" : "启用") + rule.title);
        active.setOnCheckedChangeListener((button, checked) -> saveRule(rule.withEnabled(checked)));
        main.addView(active);
        item.addView(main);

        String nextText;
        long nextAt = 0L;
        boolean ticking = false;
        if (done >= rule.dailyTarget && !rule.remindAfterTarget) {
            nextText = "✓ 今日目标已完成";
        } else if (!rule.enabled || !ReminderSettings.enabled(this)) {
            nextText = "提醒已暂停";
        } else {
            nextAt = ReminderScheduler.scheduledAt(this, rule.id);
            if (nextAt <= 0L) {
                nextAt = ReminderScheduler.nextTime(rule, System.currentTimeMillis());
            }
            ticking = true;
            nextText = countdownText(nextAt - System.currentTimeMillis());
            if (done >= rule.dailyTarget) nextText = "目标已达成，仍提醒 · " + nextText;
        }
        TextView countdown = label(nextText, 11, done >= rule.dailyTarget ? INK : MUTED, done >= rule.dailyTarget);
        countdown.setSingleLine(true);
        countdown.setEllipsize(android.text.TextUtils.TruncateAt.END);
        if (ticking) startCountdown(countdown, rule);
        LinearLayout actions = row();
        LinearLayout.LayoutParams actionsParams = full();
        actionsParams.topMargin = dp(7);
        item.addView(actions, actionsParams);
        actions.addView(countdown, new LinearLayout.LayoutParams(0, dp(30), 1));
        TextView complete = new TextView(this);
        complete.setText("提前完成一次");
        complete.setTextSize(11);
        complete.setTextColor(iconColor(rule.title));
        complete.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        complete.setAllCaps(false);
        complete.setGravity(Gravity.CENTER);
        complete.setPadding(dp(10), dp(5), dp(10), dp(5));
        complete.setMinHeight(dp(30));
        complete.setIncludeFontPadding(false);
        complete.setBackground(round(iconTint(rule.title), 14));
        complete.setOnClickListener(v -> {
            long now = System.currentTimeMillis();
            ReminderSettings.markCompleted(this, rule.id, rule.dailyTarget);
            ReminderScheduler.resetNext(this, rule.id);
            String recordId = java.util.UUID.randomUUID().toString();
            ReminderRecord.finish(this, recordId, rule.title, ReminderRecord.KIND_DONE,
                    now, now, "提前完成一次");
            ReminderScheduler.update(this);
            int updatedDone = ReminderSettings.completedToday(this, rule.id);
            targetBadge.setText(updatedDone >= rule.dailyTarget ? "达标" : updatedDone + "/" + rule.dailyTarget);

            int allDone = 0;
            int allTarget = 0;
            for (ReminderSettings.Rule current : ReminderSettings.rules(this)) {
                allDone += ReminderSettings.completedToday(this, current.id);
                allTarget += current.dailyTarget;
            }
            if (completedMetric != null) completedMetric.setText(allDone + " / " + allTarget + " 次");
            if (todayProgress != null) {
                todayProgress.setMax(Math.max(1, allTarget));
                todayProgress.setProgress(Math.min(allDone, Math.max(1, allTarget)));
            }

            boolean targetReached = updatedDone >= rule.dailyTarget;
            if (targetReached && !rule.remindAfterTarget) {
                countdown.setText("✓ 今日目标已完成");
                countdown.setTextColor(INK);
            } else {
                long refreshedNextAt = ReminderScheduler.scheduledAt(this, rule.id);
                if (refreshedNextAt <= 0L) refreshedNextAt = ReminderScheduler.nextTime(rule, now);
                countdown.setText((targetReached ? "目标已达成，仍提醒 · " : "")
                        + countdownText(refreshedNextAt - now));
                countdown.setTextColor(targetReached ? INK : MUTED);
            }
        });
        actions.addView(complete);
        item.setOnClickListener(v -> openItemEditor(rule.id));
        item.setOnLongClickListener(v -> { openItemEditor(rule.id); return true; });
        LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(-1, -2);
        itemParams.bottomMargin = dp(9);
        parent.addView(item, itemParams);
    }

    private String compactScheduleSummary(ReminderSettings.Rule rule) {
        if (!rule.specificTimeMode) {
            return clock(rule.start) + "–" + clock(rule.end) + " · " + intervalText(rule.intervalSeconds);
        }
        StringBuilder summary = new StringBuilder("每天 ");
        int shown = Math.min(2, rule.timePointsSeconds.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) summary.append("、");
            int point = rule.timePointsSeconds.get(i);
            if (point % 60 == 0) {
                summary.append(String.format(java.util.Locale.getDefault(), "%02d:%02d",
                        point / 3600, point / 60 % 60));
            } else {
                summary.append(String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d",
                        point / 3600, point / 60 % 60, point % 60));
            }
        }
        if (rule.timePointsSeconds.size() > shown) summary.append(" +").append(rule.timePointsSeconds.size() - shown);
        return summary.toString();
    }

    private String scheduleSummary(ReminderSettings.Rule rule) {
        if (!rule.specificTimeMode) {
            return clock(rule.start) + " - " + clock(rule.end) + "  ·  " + intervalText(rule.intervalSeconds);
        }
        StringBuilder summary = new StringBuilder("每天 ");
        for (int i = 0; i < rule.timePointsSeconds.size(); i++) {
            if (i > 0) summary.append("、");
            int point = rule.timePointsSeconds.get(i);
            summary.append(String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d",
                    point / 3600, point / 60 % 60, point % 60));
        }
        return summary.toString();
    }

    private void openItemEditor(int ruleId) {
        startActivity(new Intent(this, ItemsActivity.class)
                .putExtra("edit_id", ruleId)
                .putExtra("return_to_home", true));
    }

    private String countdownText(long remainingMillis) {
        long seconds = Math.max(0, (remainingMillis + 999L) / 1000L);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        return String.format(java.util.Locale.getDefault(), "还差%dh%02dmin%02ds提醒", hours, minutes, remainder);
    }

    private void startCountdown(TextView view, ReminderSettings.Rule rule) {
        Runnable ticker = new Runnable() {
            @Override public void run() {
                if (!view.isAttachedToWindow()) return;
                long now = System.currentTimeMillis();
                int done = ReminderSettings.completedToday(MainActivity.this, rule.id);
                boolean targetReached = done >= rule.dailyTarget;
                long nextAt = ReminderScheduler.scheduledAt(MainActivity.this, rule.id);
                if (nextAt <= 0L) nextAt = ReminderScheduler.nextTime(rule, now);
                String value = countdownText(nextAt - now);
                if (targetReached) value = "目标已达成，仍提醒 · " + value;
                view.setText(value);
                view.setTextColor(targetReached ? INK : MUTED);
                view.postDelayed(this, 1000);
            }
        };
        view.post(ticker);
    }

    private int iconColor(String title) {
        return UiPalette.categoryInk(UiPalette.categoryKind(title));
    }

    private int iconTint(String title) {
        return UiPalette.categoryFill(UiPalette.categoryKind(title));
    }

    private void editRule(ReminderSettings.Rule existing) {
        final int id = existing == null ? ReminderSettings.nextRuleId(this) : existing.id;
        final int[] start = {existing == null ? 9 * 60 : existing.start};
        final int[] end = {existing == null ? 18 * 60 : existing.end};
        final int[] interval = {existing == null ? 30 * 60 : existing.intervalSeconds};
        final int[] target = {existing == null ? 3 : existing.dailyTarget};
        final boolean[] remindAfterTarget = {existing != null && existing.remindAfterTarget};
        final boolean[] active = {existing == null || existing.enabled};
        LinearLayout form = new LinearLayout(this);
        form.setPadding(dp(20), dp(8), dp(20), 0);
        form.setOrientation(LinearLayout.VERTICAL);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("事项名称，例如：起来活动一下");
        name.setText(existing == null ? "" : existing.title);
        form.addView(name);
        TextView fromButton = timeField("开始时间", start[0]);
        TextView toButton = timeField("结束时间", end[0]);
        Button intervalButton = button("提醒间隔  " + intervalText(interval[0]), () -> {});
        fromButton.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> { start[0] = h * 60 + m; fromButton.setText("开始时间\n" + clock(start[0])); }, start[0] / 60, start[0] % 60, true).show());
        toButton.setOnClickListener(v -> new TimePickerDialog(this, (view, h, m) -> { end[0] = h * 60 + m; toButton.setText("结束时间\n" + clock(end[0])); }, end[0] / 60, end[0] % 60, true).show());
        int[] values = {5, 10, 15, 20, 30, 45, 60, 90};
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) labels[i] = "每 " + values[i] + " 分钟";
        java.util.Set<String> savedIntervals = ReminderSettings.prefs(this)
                .getStringSet("custom_item_intervals_seconds", java.util.Collections.emptySet());
        ArrayList<Integer> customIntervals = new ArrayList<>();
        for (String value : savedIntervals) {
            try { customIntervals.add(Integer.parseInt(value)); } catch (NumberFormatException ignored) { }
        }
        customIntervals.sort(Integer::compareTo);
        String[] intervalOptions = new String[customIntervals.size() + labels.length + 1];
        for (int i = 0; i < customIntervals.size(); i++) intervalOptions[i] = "自定义 · " + intervalText(customIntervals.get(i));
        intervalOptions[customIntervals.size()] = "新增自定义时间…";
        System.arraycopy(labels, 0, intervalOptions, customIntervals.size() + 1, labels.length);
        intervalButton.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("提醒间隔")
                .setItems(intervalOptions, (dialog, which) -> {
                    if (which < customIntervals.size()) {
                        interval[0] = customIntervals.get(which);
                        intervalButton.setText("提醒间隔  " + intervalText(interval[0]));
                    } else if (which == customIntervals.size()) {
                        chooseCustomInterval(interval, intervalButton);
                    } else {
                        interval[0] = values[which - customIntervals.size() - 1] * 60;
                        intervalButton.setText("提醒间隔  " + intervalText(interval[0]));
                    }
                }).show());
        Switch enabledSwitch = new Switch(this);
        enabledSwitch.setText("启用此事项");
        enabledSwitch.setChecked(active[0]);
        enabledSwitch.setOnCheckedChangeListener((button, checked) -> active[0] = checked);
        form.addView(fromButton);
        form.addView(toButton);
        form.addView(intervalButton);
        final Button[] targetButtonHolder = new Button[1];
        Button targetButton = button("每日目标  " + target[0] + " 次", () -> {
            final EditText input = new EditText(this); input.setInputType(2); input.setText(String.valueOf(target[0]));
            new AlertDialog.Builder(this).setTitle("每天想完成几次").setView(input).setNegativeButton("取消", null).setPositiveButton("确定", (d,w) -> { try { target[0] = Math.max(1, Integer.parseInt(input.getText().toString())); targetButtonHolder[0].setText("每日目标  " + target[0] + " 次"); } catch (Exception ignored) {} }).show();
        });
        targetButtonHolder[0] = targetButton;
        form.addView(targetButton);
        TextView afterTargetLabel = label("达标后是否继续提醒", 14, INK, true);
        afterTargetLabel.setPadding(0, dp(8), 0, dp(2));
        form.addView(afterTargetLabel);
        RadioGroup afterTargetOptions = new RadioGroup(this);
        afterTargetOptions.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton stopAfterTarget = new RadioButton(this);
        stopAfterTarget.setId(1);
        stopAfterTarget.setText("否，停止提醒");
        RadioButton keepAfterTarget = new RadioButton(this);
        keepAfterTarget.setId(2);
        keepAfterTarget.setText("是，继续提醒");
        afterTargetOptions.addView(stopAfterTarget, new RadioGroup.LayoutParams(0, -2, 1));
        afterTargetOptions.addView(keepAfterTarget, new RadioGroup.LayoutParams(0, -2, 1));
        afterTargetOptions.check(remindAfterTarget[0] ? 2 : 1);
        afterTargetOptions.setOnCheckedChangeListener((group, checkedId) -> remindAfterTarget[0] = checkedId == 2);
        form.addView(afterTargetOptions);
        form.addView(enabledSwitch);
        AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle(existing == null ? "添加提醒事项" : "编辑提醒事项")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String title = name.getText().toString().trim();
                    if (title.isEmpty()) { Toast.makeText(this, "请输入事项名称", Toast.LENGTH_SHORT).show(); return; }
                    ArrayList<ReminderSettings.Rule> updated = new ArrayList<>(ReminderSettings.rules(this));
                    updated.removeIf(rule -> rule.id == id);
                    updated.add(ReminderSettings.Rule.withIntervalSeconds(id, title, start[0], end[0], interval[0], target[0], remindAfterTarget[0], active[0])
                            .withReminderMessage(existing == null ? "" : existing.reminderMessage));
                    saveRules(updated);
                });
        if (existing != null) dialog.setNeutralButton("删除", (d, w) -> {
            ArrayList<ReminderSettings.Rule> updated = new ArrayList<>(ReminderSettings.rules(this));
            updated.removeIf(rule -> rule.id == id);
            saveRules(updated);
        });
        dialog.show();
    }

    private void chooseCustomInterval(int[] interval, Button intervalButton) {
        LinearLayout fields = row();
        fields.setGravity(Gravity.CENTER);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        fields.setPadding(dp(18), dp(12), dp(18), dp(4));
        NumberPicker hours = new NumberPicker(this);
        hours.setMinValue(0);
        hours.setMaxValue(23);
        hours.setValue(interval[0] / 3600);
        hours.setWrapSelectorWheel(false);
        NumberPicker minutes = new NumberPicker(this);
        minutes.setMinValue(0);
        minutes.setMaxValue(59);
        minutes.setValue(interval[0] / 60 % 60);
        NumberPicker seconds = new NumberPicker(this);
        seconds.setMinValue(0);
        seconds.setMaxValue(59);
        seconds.setWrapSelectorWheel(false);
        seconds.setValue(interval[0] % 60);
        minutes.setWrapSelectorWheel(false);
        addIntervalWheel(fields, hours, "小时");
        addIntervalWheel(fields, minutes, "分钟");
        addIntervalWheel(fields, seconds, "秒");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("自定义提醒间隔")
                .setView(fields)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int totalSeconds = hours.getValue() * 3600 + minutes.getValue() * 60 + seconds.getValue();
            if (totalSeconds == 0) {
                Toast.makeText(this, "提醒间隔至少为 1 秒", Toast.LENGTH_SHORT).show();
                return;
            }
            interval[0] = totalSeconds;
            java.util.Set<String> saved = new java.util.HashSet<>(ReminderSettings.prefs(this)
                    .getStringSet("custom_item_intervals_seconds", java.util.Collections.emptySet()));
            saved.add(Integer.toString(totalSeconds));
            ReminderSettings.prefs(this).edit().putStringSet("custom_item_intervals_seconds", saved).commit();
            intervalButton.setText("提醒间隔  " + intervalText(totalSeconds));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private String intervalText(int seconds) {
        int hours = seconds / 3600;
        int minutes = seconds / 60 % 60;
        int remainder = seconds % 60;
        StringBuilder result = new StringBuilder("每 ");
        if (hours > 0) result.append(hours).append(" 小时 ");
        if (minutes > 0) result.append(minutes).append(" 分钟 ");
        if (remainder > 0 || (hours == 0 && minutes == 0)) result.append(remainder).append(" 秒");
        return result.toString().trim();
    }

    private void addIntervalWheel(LinearLayout parent, NumberPicker picker, String unit) {
        LinearLayout column = new LinearLayout(this);
        column.setGravity(Gravity.CENTER);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(picker, new LinearLayout.LayoutParams(dp(76), dp(112)));
        TextView label = label(unit, 13, MUTED, true);
        label.setGravity(Gravity.CENTER);
        column.addView(label, new LinearLayout.LayoutParams(-1, dp(24)));
        parent.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private TextView timeField(String title, int minute) {
        TextView field = label(title + "\n" + clock(minute), 15, INK, true);
        field.setGravity(Gravity.CENTER);
        field.setPadding(dp(10), dp(12), dp(10), dp(12));
        field.setBackground(round(MINT_PALE, 10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(7);
        field.setLayoutParams(params);
        return field;
    }

    private void saveRule(ReminderSettings.Rule changed) {
        ArrayList<ReminderSettings.Rule> updated = new ArrayList<>(ReminderSettings.rules(this));
        for (int i = 0; i < updated.size(); i++) if (updated.get(i).id == changed.id) updated.set(i, changed);
        saveRules(updated);
    }

    private void saveRules(List<ReminderSettings.Rule> updated) {
        ReminderSettings.saveRules(this, updated);
        render();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != 22) return;
        boolean enable = ReminderSettings.prefs(this).getBoolean("enable_block_after_picker", false);
        ReminderSettings.prefs(this).edit().remove("enable_block_after_picker").apply();
        if (enable && !ReminderSettings.blockPackages(this).isEmpty()) {
            ReminderSettings.prefs(this).edit().putBoolean("block_enabled", true).apply();
            if (!isWatchServiceEnabled()) openAccessibilitySettings();
        }
        render();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 91) {
            render();
            if (reopenPermissionGuide) {
                reopenPermissionGuide = false;
                showPermissionChecklist();
            }
        }
    }

    private void openUsageSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        } catch (Exception error) {
            Toast.makeText(this, "无法打开使用情况访问设置", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isWatchServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Service.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        for (AccessibilityServiceInfo info : manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
            if (info.getResolveInfo() == null) continue;
            ServiceInfo serviceInfo = info.getResolveInfo().serviceInfo;
            if (serviceInfo != null && getPackageName().equals(serviceInfo.packageName)
                    && AppWatchService.class.getName().equals(serviceInfo.name)) return true;
        }
        return false;
    }

    private void openAccessibilitySettings() {
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } catch (Exception error) {
            Toast.makeText(this, "无法打开无障碍设置", Toast.LENGTH_SHORT).show();
        }
    }

    static String clock(int minute) {
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60);
    }

    private void section(String title) {
        TextView heading = label(title, 18, INK, true);
        LinearLayout.LayoutParams params = full();
        params.topMargin = dp(20);
        params.bottomMargin = dp(9);
        body.addView(heading, params);
    }

    private LinearLayout columnCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(4), dp(14), dp(4));
        card.setBackground(outline(Color.WHITE, UiPalette.BORDER, 16));
        card.setElevation(dp(1));
        return card;
    }

    private GradientDrawable homeStatusBackground() {
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR, UiPalette.headGradient());
        background.setCornerRadius(dp(18));
        background.setStroke(dp(1), UiPalette.BORDER);
        return background;
    }

    private int iconBorder(String title) {
        return UiPalette.categoryStroke(UiPalette.categoryKind(title));
    }

    private GradientDrawable outline(int fillColor, int strokeColor, int radius) {
        GradientDrawable background = round(fillColor, radius);
        background.setStroke(dp(1), strokeColor);
        return background;
    }

    private void addSettingRow(LinearLayout parent, String title, String value, String hint, Runnable action) {
        LinearLayout row = row();
        row.setPadding(dp(2), dp(9), dp(2), dp(9));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(title, 15, INK, true));
        TextView detail = label(hint, 11, MUTED, false);
        detail.setPadding(0, dp(3), 0, 0);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView val = label(value + "  ›", 14, INK, true);
        val.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        row.addView(val, new LinearLayout.LayoutParams(-2, -1));
        row.setOnClickListener(v -> action.run());
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        parent.addView(divider, params);
    }

    private void styleSwitch(Switch toggle) {
        int[][] states = new int[][]{
                new int[]{android.R.attr.state_checked},
                new int[]{}
        };
        toggle.setThumbTintList(new ColorStateList(states, new int[]{Color.WHITE, Color.WHITE}));
        toggle.setTrackTintList(new ColorStateList(states, new int[]{GREEN, UiPalette.DISABLED}));
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView label(String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private Button button(String text, Runnable action) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(INK);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(UiPalette.surface(color));
        drawable.setCornerRadius(dp(radius));
        if (color == Color.WHITE) drawable.setStroke(dp(1), UiPalette.BORDER);
        return drawable;
    }

    private void styleDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(round(Color.WHITE, 18));
            dialog.getWindow().setDimAmount(0.34f);
        }
        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(INK);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextColor(MUTED);
        for (int buttonId : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
                AlertDialog.BUTTON_NEUTRAL}) {
            Button action = dialog.getButton(buttonId);
            if (action == null) continue;
            action.setAllCaps(false);
            action.setTextColor(buttonId == AlertDialog.BUTTON_POSITIVE ? INK : MUTED);
            action.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
    }

    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
