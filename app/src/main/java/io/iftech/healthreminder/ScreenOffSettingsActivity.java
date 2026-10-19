package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationManagerCompat;

public final class ScreenOffSettingsActivity extends Activity {
    private static final int GREEN = 0xff277049;
    private static final int INK = 0xff23342a;
    private static final int MUTED = 0xff65776a;
    private static final int PAPER = 0xfff4f8f1;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (ReminderSettings.enabled(this)) ReminderScheduler.update(this);
        render();
    }

    private void render() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(12), dp(20), dp(24));
        page.setBackgroundColor(PAPER);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹", 30, GREEN, false);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(40), dp(48)));
        TextView title = text("息屏设置", 21, INK, true);
        title.setPadding(dp(8), 0, 0, 0);
        header.addView(title);
        page.addView(header);

        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setPadding(dp(16), dp(4), dp(16), dp(4));
        options.setBackground(round(Color.WHITE, 12));
        Switch alarm = addSwitchRow(options, "统计息屏", ReminderSettings.screenOffAlarm(this));
        TextView screenOffHint = text("关闭屏幕时也计入提醒时间", 11, MUTED, false);
        screenOffHint.setPadding(0, 0, 0, dp(12));
        options.addView(screenOffHint, new LinearLayout.LayoutParams(-1, -2));
        addDivider(options);
        Switch sound = addSwitchRow(options, "息屏闹钟", ReminderSettings.screenOffSound(this));
        addDivider(options);
        Switch vibrate = addSwitchRow(options, "息屏振动", ReminderSettings.screenOffVibrate(this));
        vibrate.setOnCheckedChangeListener((button, checked) ->
                ReminderSettings.prefs(this).edit().putBoolean("screen_off_vibrate", checked).apply());
        page.addView(options, new LinearLayout.LayoutParams(-1, -2));

        TextView permissionsTitle = text("锁屏提醒权限", 16, INK, true);
        LinearLayout.LayoutParams permissionsTitleParams = new LinearLayout.LayoutParams(-1, -2);
        permissionsTitleParams.topMargin = dp(20);
        permissionsTitleParams.bottomMargin = dp(8);
        page.addView(permissionsTitle, permissionsTitleParams);
        addPermissionRow(page, "精确闹钟", exactAlarmAllowed() ? "已允许" : "未开启 · 去设置",
                this::openExactAlarmSettings);
        boolean notificationsAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
                && (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED);
        addPermissionRow(page, "通知权限", notificationsAllowed ? "已允许" : "未开启 · 去设置",
                this::openNotificationSettings);
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager notifications = getSystemService(NotificationManager.class);
            boolean fullScreenAllowed = notifications != null && notifications.canUseFullScreenIntent();
            addPermissionRow(page, "锁屏全屏提醒", fullScreenAllowed ? "已允许" : "未开启 · 去设置",
                    this::openFullScreenSettings);
        }
        TextView recheck = text("重新检查权限", 14, GREEN, true);
        recheck.setGravity(Gravity.CENTER);
        recheck.setPadding(0, dp(10), 0, dp(10));
        recheck.setOnClickListener(v -> render());
        page.addView(recheck, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout ringtoneRow = new LinearLayout(this);
        ringtoneRow.setGravity(Gravity.CENTER_VERTICAL);
        ringtoneRow.setPadding(dp(16), dp(14), dp(16), dp(14));
        ringtoneRow.setBackground(round(Color.WHITE, 12));
        LinearLayout.LayoutParams ringtoneParams = new LinearLayout.LayoutParams(-1, -2);
        ringtoneParams.topMargin = dp(12);
        page.addView(ringtoneRow, ringtoneParams);
        TextView ringtoneTitle = text("选择铃声", 15, INK, true);
        ringtoneRow.addView(ringtoneTitle, new LinearLayout.LayoutParams(0, -2, 1));
        TextView ringtoneValue = text(ringtoneName() + "  ›", 14, GREEN, true);
        ringtoneValue.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        ringtoneRow.addView(ringtoneValue);
        ringtoneRow.setContentDescription("选择铃声，" + ringtoneName());
        ringtoneRow.setOnClickListener(v -> pickAlarmRingtone());
        LinearLayout soundRow = (LinearLayout) sound.getParent();
        LinearLayout vibrateRow = (LinearLayout) vibrate.getParent();
        sound.setOnCheckedChangeListener((button, checked) -> {
            ReminderSettings.prefs(this).edit().putBoolean("screen_off_sound", checked).apply();
            updateControlState(alarm.isChecked(), checked, sound, vibrate,
                    soundRow, vibrateRow, ringtoneRow);
        });
        alarm.setOnCheckedChangeListener((button, checked) -> {
            if (!checked) {
                ReminderSettings.prefs(this).edit().putBoolean("screen_off_alarm", false).apply();
                ReminderScheduler.update(this);
                updateControlState(false, sound.isChecked(), sound, vibrate,
                        soundRow, vibrateRow, ringtoneRow);
                return;
            }
            new AlertDialog.Builder(this).setTitle("统计息屏")
                    .setMessage("息屏时身体也在劳作，我会化成闹钟提醒你的")
                    .setNegativeButton("取消", (dialog, which) -> button.setChecked(false))
                    .setPositiveButton("开启", (dialog, which) -> {
                        ReminderSettings.prefs(this).edit().putBoolean("screen_off_alarm", true).apply();
                        ReminderScheduler.update(this);
                        updateControlState(true, sound.isChecked(), sound, vibrate,
                                soundRow, vibrateRow, ringtoneRow);
                    })
                    .setOnCancelListener(dialog -> button.setChecked(false))
                    .show();
        });
        updateControlState(alarm.isChecked(), sound.isChecked(), sound, vibrate,
                soundRow, vibrateRow, ringtoneRow);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(page);
        setContentView(scroll);
    }

    private Switch addSwitchRow(LinearLayout parent, String title, boolean checked) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        TextView label = text(title, 15, INK, false);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        Switch control = new Switch(this);
        control.setChecked(checked);
        row.addView(control);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return control;
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(0xffe8eee6);
        parent.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    private void updateControlState(boolean screenOffEnabled, boolean soundEnabled,
                                    Switch sound, Switch vibrate, LinearLayout soundRow,
                                    LinearLayout vibrateRow, LinearLayout ringtoneRow) {
        sound.setEnabled(screenOffEnabled);
        vibrate.setEnabled(screenOffEnabled);
        soundRow.setAlpha(screenOffEnabled ? 1f : 0.45f);
        vibrateRow.setAlpha(screenOffEnabled ? 1f : 0.45f);
        boolean ringtoneEnabled = screenOffEnabled && soundEnabled;
        ringtoneRow.setEnabled(ringtoneEnabled);
        ringtoneRow.setAlpha(ringtoneEnabled ? 1f : 0.45f);
    }

    private boolean exactAlarmAllowed() {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager alarms = getSystemService(AlarmManager.class);
        return alarms != null && alarms.canScheduleExactAlarms();
    }

    private void addPermissionRow(LinearLayout parent, String label, String status, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(round(Color.WHITE, 12));
        TextView name = text(label, 15, INK, false);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text(status, 14, status.startsWith("未开启") ? 0xffa33b32 : GREEN, true);
        row.addView(value);
        row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        parent.addView(row, params);
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT < 31) return;
        Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException error) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName())));
        }
    }

    private void openFullScreenSettings() {
        if (Build.VERSION.SDK_INT < 34) return;
        Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                .setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException error) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName())));
        }
    }

    private void openNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(intent);
        } catch (android.content.ActivityNotFoundException error) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:" + getPackageName())));
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 23 && resultCode == RESULT_OK && data != null) {
            Uri selected = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            android.content.SharedPreferences.Editor editor = ReminderSettings.prefs(this).edit();
            if (selected == null) editor.remove("screen_off_ringtone");
            else editor.putString("screen_off_ringtone", selected.toString());
            editor.apply();
            render();
        }
    }

    private String ringtoneName() {
        String stored = ReminderSettings.screenOffRingtone(this);
        Uri uri = stored == null ? null : Uri.parse(stored);
        Ringtone ringtone = RingtoneManager.getRingtone(this,
                uri == null ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : uri);
        return ringtone == null ? "系统默认" : ringtone.getTitle(this);
    }

    private void pickAlarmRingtone() {
        Uri defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        String stored = ReminderSettings.screenOffRingtone(this);
        Intent picker = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "选择息屏闹钟铃声")
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultUri)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        stored == null ? defaultUri : Uri.parse(stored));
        try {
            startActivityForResult(picker, 23);
        } catch (android.content.ActivityNotFoundException error) {
            Toast.makeText(this, "当前系统没有可用的铃声选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView result = new TextView(this);
        result.setText(value);
        result.setTextSize(size);
        result.setTextColor(color);
        if (bold) result.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        return result;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
