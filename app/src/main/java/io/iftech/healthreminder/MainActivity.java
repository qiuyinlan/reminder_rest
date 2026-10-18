package io.iftech.healthreminder;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Service;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class MainActivity extends Activity {
    private static final int GREEN = Color.rgb(39, 112, 73);
    private static final int INK = Color.rgb(35, 52, 42);
    private static final int MUTED = Color.rgb(101, 119, 106);
    private static final int PAPER = Color.rgb(244, 248, 241);
    private LinearLayout body;
    private boolean reopenPermissionGuide;
    private boolean checkExactAlarmOnResume;
    private boolean exactAlarmAllowedBeforeSettings;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        render();
        ReminderScheduler.update(this);
        if (getIntent().getBooleanExtra("new_item", false)) editRule(null);
        int editId = getIntent().getIntExtra("edit_id", -1);
        if (editId >= 0) for (ReminderSettings.Rule rule : ReminderSettings.rules(this)) if (rule.id == editId) { editRule(rule); break; }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (body != null) {
            render();
            if (checkExactAlarmOnResume) {
                checkExactAlarmOnResume = false;
                if (exactAlarmAllowedBeforeSettings != ReminderSettings.exactAlarmsAllowed(this)) {
                    ReminderScheduler.update(this);
                }
            }
            if (reopenPermissionGuide) {
                reopenPermissionGuide = false;
                showPermissionChecklist();
            }
        }
    }

    private void render() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(PAPER);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(PAPER);
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(12), dp(20), dp(28));
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(screen);

        LinearLayout top = row();
        TextView brand = label("好好歇会", 24, INK, true);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(label(ReminderSettings.enabled(this) ? "● 正在守护" : "○ 已暂停", 12,
                ReminderSettings.enabled(this) ? GREEN : MUTED, true));
        body.addView(top);
        TextView date = label(DateFormat.getDateInstance(DateFormat.FULL).format(Calendar.getInstance().getTime()), 12, MUTED, false);
        date.setPadding(0, dp(2), 0, dp(14));
        body.addView(date);
        addPermissionBanner();

        LinearLayout hero = row();
        hero.setGravity(Gravity.CENTER_VERTICAL);
        hero.setPadding(dp(14), dp(10), dp(14), dp(10));
        hero.setBackground(round(0xffe5f1e2, 20));
        WellnessAnimationView face = new WellnessAnimationView(this, WellnessAnimationView.Mood.SAD);
        ObjectAnimator bob = ObjectAnimator.ofFloat(face, View.TRANSLATION_Y, 0f, -dp(4), 0f);
        bob.setDuration(2200);
        bob.setRepeatCount(ValueAnimator.INFINITE);
        face.post(bob::start);
        face.setOnClickListener(v -> startActivity(new Intent(this, ReminderActivity.class)
                .putExtra("action", ReminderSettings.nextType(this))));
        hero.addView(face, new LinearLayout.LayoutParams(dp(94), dp(94)));
        LinearLayout greeting = new LinearLayout(this);
        greeting.setOrientation(LinearLayout.VERTICAL);
        greeting.setPadding(dp(10), 0, 0, 0);
        greeting.addView(label("忙了一陣子啦", 18, INK, true));
        TextView sub = label("給眼睛和身體一點溫柔的空檔。", 13, MUTED, false);
        sub.setPadding(0, dp(4), 0, 0);
        greeting.addView(sub);
        hero.addView(greeting, new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(hero, full());

        LinearLayout sectionHead = row();
        sectionHead.setPadding(0, dp(20), 0, dp(8));
        sectionHead.addView(label("提醒事項", 17, INK, true), new LinearLayout.LayoutParams(0, -2, 1));
        body.addView(sectionHead);
        LinearLayout schedule = columnCard();
        List<ReminderSettings.Rule> rules = ReminderSettings.rules(this);
        boolean addedBuiltIn = false;
        for (ReminderSettings.Rule rule : rules) {
            if (addedBuiltIn) addDivider(schedule);
            addRuleRow(schedule, rule);
            addedBuiltIn = true;
        }
        if (!addedBuiltIn) addEmptyMessage(schedule, "还没有提醒事项，请到事项页添加。");
        body.addView(schedule, full());

        section("使用與权限");
        LinearLayout usage = columnCard();
        long used = UsageAccess.todayMillis(this);
        String usageValue = UsageAccess.hasAccess(this) ? UsageAccess.format(used) : "开启使用情况访问";
        addSettingRow(usage, "今日屏幕使用", usageValue, UsageAccess.hasAccess(this) ? "已排除白名单应用" : "授权后统计各应用的前台时长", this::openUsageSettings);
        addDivider(usage);
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
        usage.addView(onlyWhenUsed, new LinearLayout.LayoutParams(-1, dp(52)));

        addDivider(usage);
        addSettingRow(usage, "白名单应用", ReminderSettings.whitelist(this).size() + " 个", "排除统计和软拦截", () -> startActivity(new Intent(this, AppPickerActivity.class)));
        addDivider(usage);
        addSettingRow(usage, "限制应用", ReminderSettings.blockPackages(this).size() + " 个", "选择需要提醒休息的应用", () -> {
            startActivity(new Intent(this, AppPickerActivity.class).putExtra("mode", "block"));
        });
        addDivider(usage);
        Switch blockEnabled = new Switch(this);
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

        LinearLayout enablePanel = new LinearLayout(this);
        enablePanel.setGravity(Gravity.CENTER_VERTICAL);
        enablePanel.setPadding(dp(16), dp(13), dp(16), dp(13));
        enablePanel.setBackground(round(0xff277049, 18));
        LinearLayout enableCopy = new LinearLayout(this);
        enableCopy.setOrientation(LinearLayout.VERTICAL);
        enableCopy.addView(label(ReminderSettings.enabled(this) ? "今天的提醒正在运行" : "开启今天的温柔提醒", 16, Color.WHITE, true));
        enableCopy.addView(label(ReminderSettings.enabled(this) ? "按每项计划准时来找你" : "按你的时间表，记得喝水和休息", 12, 0xffd8ebdc, false));
        enablePanel.addView(enableCopy, new LinearLayout.LayoutParams(0, -2, 1));
        Switch enabled = new Switch(this);
        enabled.setChecked(ReminderSettings.enabled(this));
        enabled.setOnCheckedChangeListener((button, checked) -> {
            ReminderSettings.setEnabled(this, checked);
            if (checked && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 91);
            }
            Toast.makeText(this, checked ? "提醒已开启" : "提醒已暂停", Toast.LENGTH_SHORT).show();
            render();
        });
        enabled.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        enabled.setTrackTintList(android.content.res.ColorStateList.valueOf(0xffa8cfb2));
        enablePanel.addView(enabled);
        LinearLayout.LayoutParams enableParams = full();
        enableParams.topMargin = dp(6);
        body.addView(enablePanel, enableParams);
        TextView foot = label("提醒通过系统通知送达", 11, MUTED, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(12), 0, 0);
        body.addView(foot);

        LinearLayout nav = row();
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(18), dp(4), dp(18), dp(4));
        nav.setBackgroundColor(Color.WHITE);
        TextView homeTab = label("⌂\n首页", 13, GREEN, true);
        homeTab.setGravity(Gravity.CENTER);
        TextView statsTab = label("◷\n统计", 13, MUTED, true);
        statsTab.setGravity(Gravity.CENTER);
        TextView itemsTab = label("▦\n事项", 13, MUTED, true);
        itemsTab.setGravity(Gravity.CENTER);
        setTabText(homeTab, "⌂", "首页"); setTabText(itemsTab, "▦", "事项"); setTabText(statsTab, "◷", "统计");
        nav.addView(homeTab, new LinearLayout.LayoutParams(0, dp(56), 1));
        nav.addView(itemsTab, new LinearLayout.LayoutParams(0, dp(56), 1));
        nav.addView(statsTab, new LinearLayout.LayoutParams(0, dp(56), 1));
        itemsTab.setOnClickListener(v -> { startActivity(new Intent(this, ItemsActivity.class)); overridePendingTransition(0, 0); });
        statsTab.setOnClickListener(v -> { startActivity(new Intent(this, StatsActivity.class)); overridePendingTransition(0, 0); });
        screen.addView(nav, new LinearLayout.LayoutParams(-1, dp(64)));
    }

    private void addPermissionBanner() {
        boolean notification = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        boolean usage = UsageAccess.hasAccess(this);
        boolean accessibility = isWatchServiceEnabled();
        boolean fullScreen = ReminderSettings.fullScreenIntentAllowed(this);
        boolean exactAlarm = ReminderSettings.exactAlarmsAllowed(this);
        boolean ready = notification && usage && accessibility && fullScreen && exactAlarm;
        LinearLayout banner = new LinearLayout(this);
        banner.setGravity(Gravity.CENTER_VERTICAL);
        banner.setPadding(dp(14), dp(11), dp(14), dp(11));
        banner.setBackground(round(ready ? 0xffe5f2e4 : 0xffffe7e4, 14));
        int statusColor = ready ? GREEN : 0xffa33b32;
        TextView copy = label(ready ? "权限配置已完成\n提醒与休息拦截权限均已开启" : "权限配置未完成\n完成后提醒和 App 休息拦截才会正常工作", 13, statusColor, true);
        banner.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView go = label(ready ? "查看  ›" : "去设置  ›", 13, statusColor, true);
        banner.addView(go);
        banner.setOnClickListener(v -> showPermissionChecklist());
        LinearLayout.LayoutParams params = full();
        params.bottomMargin = dp(14);
        body.addView(banner, params);
    }

    private void showPermissionChecklist() {
        LinearLayout list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL); list.setPadding(dp(20), dp(4), dp(20), 0);
        boolean notification = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        AlertDialog guide = new AlertDialog.Builder(this).setTitle("完成系统配置")
                .setView(list).setPositiveButton("完成", null).create();
        TextView explanation = label("按需逐项打开系统权限；设备已解锁时，系统仍可能将全屏提醒显示为通知横幅。", 12, MUTED, false);
        explanation.setPadding(0, 0, 0, dp(8));
        list.addView(explanation, 0);
        addPermissionRow(list, guide, "通知权限", notification, () -> { if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 91); });
        addPermissionRow(list, guide, "精确闹钟（按设定时间提醒）", ReminderSettings.exactAlarmsAllowed(this), this::openExactAlarmSettings);
        addPermissionRow(list, guide, "使用情况访问", UsageAccess.hasAccess(this), this::openUsageSettings);
        addPermissionRow(list, guide, "无障碍服务（读取当前 App 名称）", isWatchServiceEnabled(), this::openAccessibilitySettings);
        addPermissionRow(list, guide, "全屏提醒权限", ReminderSettings.fullScreenIntentAllowed(this), this::openFullScreenIntentSettings);
        addPermissionRow(list, guide, "限制 App 选择", !ReminderSettings.blockPackages(this).isEmpty(), () -> startActivity(new Intent(this, AppPickerActivity.class).putExtra("mode", "block")));
        guide.show();
    }

    private void addPermissionRow(LinearLayout parent, AlertDialog guide, String title, boolean ready, Runnable action) {
        TextView row = label((ready ? "✓ " : "○ ") + title + (ready ? "  已完成" : "  去设置"), 15, ready ? GREEN : 0xffa33b32, true);
        row.setPadding(0, dp(13), 0, dp(13));
        if (!ready) row.setOnClickListener(v -> {
            guide.dismiss();
            reopenPermissionGuide = true;
            action.run();
        });
        parent.addView(row);
    }

    private void openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT < 34) return;
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < 31) return;
        exactAlarmAllowedBeforeSettings = ReminderSettings.exactAlarmsAllowed(this);
        checkExactAlarmOnResume = true;
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception error) {
            Toast.makeText(this, "无法打开精确闹钟设置", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean isBuiltIn(String title) {
        for (String type : ReminderSettings.TYPES) if (type.equals(title)) return true;
        return false;
    }

    private void addEmptyMessage(LinearLayout parent, String message) {
        TextView empty = label(message, 13, MUTED, false);
        empty.setPadding(dp(4), dp(16), dp(4), dp(16));
        parent.addView(empty);
    }

    private void addRuleRow(LinearLayout parent, ReminderSettings.Rule rule) {
        LinearLayout item = row();
        item.setPadding(dp(2), dp(12), dp(2), dp(12));
        TextView badge = label(rule.title.isEmpty() ? "·" : rule.title.substring(0, 1), 14, iconColor(rule.title), true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(iconTint(rule.title), 12));
        item.addView(badge, new LinearLayout.LayoutParams(dp(38), dp(38)));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(11), 0, dp(6), 0);
        copy.addView(label(rule.title, 15, INK, true));
        int done = ReminderSettings.completedToday(this, rule.id);
        copy.addView(label(clock(rule.start) + " - " + clock(rule.end) + "  ·  " + intervalText(rule.interval) + "  ·  今日 " + done + "/" + rule.dailyTarget, 12, MUTED, false));
        String nextText;
        long nextAt = 0L;
        boolean ticking = false;
        if (done >= rule.dailyTarget && !rule.remindAfterTarget) {
            nextText = "✓ 今日目标已完成";
        } else if (!rule.enabled || !ReminderSettings.enabled(this)) {
            nextText = "提醒已暂停";
        } else {
            nextAt = ReminderScheduler.nextTime(rule, System.currentTimeMillis());
            ticking = true;
            nextText = countdownText(nextAt - System.currentTimeMillis());
            if (done >= rule.dailyTarget) nextText = "目标已达成，仍提醒 · " + nextText;
        }
        TextView countdown = label(nextText, 12, done >= rule.dailyTarget ? GREEN : MUTED, done >= rule.dailyTarget);
        copy.addView(countdown);
        if (ticking) startCountdown(countdown, nextAt, done >= rule.dailyTarget);
        TextView complete = label(done >= rule.dailyTarget ? "✓ 已完成" : "完成一次", 12, done >= rule.dailyTarget ? GREEN : 0xff547b5e, true);
        complete.setPadding(0, dp(5), 0, 0);
        complete.setOnClickListener(v -> { if (ReminderSettings.markCompleted(this, rule.id, rule.dailyTarget)) ReminderScheduler.update(this); render(); });
        copy.addView(complete);
        item.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        Switch active = new Switch(this);
        active.setChecked(rule.enabled);
        active.setOnCheckedChangeListener((button, checked) -> saveRule(new ReminderSettings.Rule(rule.id, rule.title, rule.start, rule.end, rule.interval, rule.dailyTarget, rule.remindAfterTarget, checked)));
        item.addView(active);
        if (!rule.enabled) item.setAlpha(.58f);
        item.setOnClickListener(v -> editRule(rule));
        item.setOnLongClickListener(v -> { editRule(rule); return true; });
        parent.addView(item, new LinearLayout.LayoutParams(-1, -2));
    }

    private String countdownText(long remainingMillis) {
        long seconds = Math.max(0, (remainingMillis + 999L) / 1000L);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        return String.format(java.util.Locale.getDefault(), "还差%dh%02dmin%02ds提醒", hours, minutes, remainder);
    }

    private void startCountdown(TextView view, long nextAt, boolean targetReached) {
        Runnable ticker = new Runnable() {
            @Override public void run() {
                if (!view.isAttachedToWindow()) return;
                String value = countdownText(nextAt - System.currentTimeMillis());
                if (targetReached) value = "目标已达成，仍提醒 · " + value;
                view.setText(value);
                view.setTextColor(targetReached ? GREEN : MUTED);
                view.postDelayed(this, 1000);
            }
        };
        view.post(ticker);
    }

    private int iconColor(String title) {
        if (title.contains("喝") || title.contains("水")) return 0xff287b95;
        if (title.contains("眼") || title.contains("看")) return 0xff527947;
        if (title.contains("颈") || title.contains("肩")) return 0xffa76c42;
        return 0xff6e6aa0;
    }

    private int iconTint(String title) {
        if (title.contains("喝") || title.contains("水")) return 0xffe0f0f2;
        if (title.contains("眼") || title.contains("看")) return 0xffe6f0df;
        if (title.contains("颈") || title.contains("肩")) return 0xfff3e8dc;
        return 0xffeceaf5;
    }

    private void editRule(ReminderSettings.Rule existing) {
        final int id = existing == null ? ReminderSettings.nextRuleId(this) : existing.id;
        final int[] start = {existing == null ? 9 * 60 : existing.start};
        final int[] end = {existing == null ? 18 * 60 : existing.end};
        final int[] interval = {existing == null ? 30 : existing.interval};
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
        String[] intervalOptions = new String[labels.length + 1];
        intervalOptions[0] = "自定义";
        System.arraycopy(labels, 0, intervalOptions, 1, labels.length);
        intervalButton.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle("提醒间隔")
                .setItems(intervalOptions, (dialog, which) -> {
                    if (which == 0) {
                        chooseCustomInterval(interval, intervalButton);
                    } else {
                        interval[0] = values[which - 1];
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
                    updated.add(new ReminderSettings.Rule(id, title, start[0], end[0], interval[0], target[0], remindAfterTarget[0], active[0]));
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
        fields.setPadding(dp(18), dp(12), dp(18), dp(4));
        NumberPicker hours = new NumberPicker(this);
        hours.setMinValue(0);
        hours.setMaxValue(23);
        hours.setValue(interval[0] / 60);
        hours.setWrapSelectorWheel(false);
        NumberPicker minutes = new NumberPicker(this);
        minutes.setMinValue(0);
        minutes.setMaxValue(59);
        minutes.setValue(interval[0] % 60);
        minutes.setWrapSelectorWheel(false);
        fields.addView(hours, new LinearLayout.LayoutParams(dp(88), dp(132)));
        TextView hourLabel = label("小时", 14, INK, true);
        hourLabel.setGravity(Gravity.CENTER_VERTICAL);
        fields.addView(hourLabel);
        fields.addView(minutes, new LinearLayout.LayoutParams(dp(88), dp(132)));
        TextView minuteLabel = label("分钟", 14, INK, true);
        minuteLabel.setGravity(Gravity.CENTER_VERTICAL);
        fields.addView(minuteLabel);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("自定义提醒间隔")
                .setView(fields)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int totalMinutes = hours.getValue() * 60 + minutes.getValue();
            if (totalMinutes == 0) {
                Toast.makeText(this, "提醒间隔至少为 1 分钟", Toast.LENGTH_SHORT).show();
                return;
            }
            interval[0] = totalMinutes;
            intervalButton.setText("提醒间隔  " + intervalText(totalMinutes));
            dialog.dismiss();
        }));
        dialog.show();
    }

    private String intervalText(int minutes) {
        int hours = minutes / 60;
        int remainder = minutes % 60;
        if (hours == 0) return "每 " + remainder + " 分钟";
        if (remainder == 0) return "每 " + hours + " 小时";
        return "每 " + hours + " 小时 " + remainder + " 分钟";
    }

    private TextView timeField(String title, int minute) {
        TextView field = label(title + "\n" + clock(minute), 15, GREEN, true);
        field.setGravity(Gravity.CENTER);
        field.setPadding(dp(10), dp(12), dp(10), dp(12));
        field.setBackground(round(0xffe7f1e5, 12));
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
        TextView heading = label(title, 14, MUTED, true);
        heading.setPadding(0, dp(17), 0, dp(8));
        body.addView(heading);
    }

    private LinearLayout columnCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(4), dp(14), dp(4));
        card.setBackground(round(Color.WHITE, 16));
        return card;
    }

    private void addSettingRow(LinearLayout parent, String title, String value, String hint, Runnable action) {
        LinearLayout row = row();
        row.setPadding(dp(2), dp(10), dp(2), dp(10));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(label(title, 15, INK, true));
        TextView detail = label(hint, 11, MUTED, false);
        detail.setPadding(0, dp(3), 0, 0);
        copy.addView(detail);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView val = label(value + "  ›", 14, GREEN, true);
        val.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        row.addView(val, new LinearLayout.LayoutParams(-2, -1));
        row.setOnClickListener(v -> action.run());
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(0xffe9eee8);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        parent.addView(divider, params);
    }

    private void setTabText(TextView tab, String icon, String title) {
        android.text.SpannableString value = new android.text.SpannableString(icon + "\n" + title);
        value.setSpan(new android.text.style.RelativeSizeSpan(icon.equals("◷") ? 2f : 1.65f), 0, icon.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tab.setText(value);
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
        button.setTextColor(GREEN);
        button.setAllCaps(false);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        return drawable;
    }

    private LinearLayout.LayoutParams full() { return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
