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
import android.content.res.ColorStateList;
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
    private int GREEN = UiPalette.ACCENT;
    /** 开关选中态的强调绿（浅色主题下只有 ACCENT 在浅底上足够可读）。 */
    private int MINT = UiPalette.ACCENT;
    /** 浅绿面板 / 开关选中轨道底色。 */
    private int MINT_LIGHT = UiPalette.SOFT;
    private int MINT_PALE = UiPalette.PALE;
    /** 开关未选中态：浅底上的禁用灰绿与浅轨道。 */
    private int SWITCH_OFF = UiPalette.DISABLED;
    private int TRACK_OFF = UiPalette.PAGE;
    /** 未授权提示：浅底琥珀色文字 + 警示底色与描边。 */
    private int WARN = UiPalette.WARN;
    private int WARN_SOFT = UiPalette.WARN_SOFT;
    private int WARN_BORDER = UiPalette.WARN_BORDER;
    private int INK = UiPalette.INK;
    private int MUTED = UiPalette.MUTED;
    private int PAPER = UiPalette.PAGE;
    private int LINE = UiPalette.BORDER;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (ReminderSettings.enabled(this)) ReminderScheduler.update(this);
        render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackground(UiPalette.page());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(10), dp(20), dp(30));
        page.setBackground(UiPalette.page());
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
        TextView title = text("息屏设置", 25, INK, true);
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title);
        page.addView(header);

        TextView subtitle = text("设置屏幕关闭后的提醒方式", 13, MUTED, false);
        subtitle.setPadding(dp(54), dp(5), 0, 0);
        page.addView(subtitle);

        LinearLayout intro = new LinearLayout(this);
        intro.setGravity(Gravity.CENTER_VERTICAL);
        intro.setPadding(dp(16), dp(15), dp(16), dp(15));
        intro.setBackground(round(MINT_LIGHT, 18));
        intro.setElevation(dp(1));
        LinearLayout.LayoutParams introParams = new LinearLayout.LayoutParams(-1, -2);
        introParams.topMargin = dp(20);
        page.addView(intro, introParams);

        TextView introIcon = text("时", 17, GREEN, true);
        introIcon.setGravity(Gravity.CENTER);
        introIcon.setBackground(round(Color.WHITE, 14));
        LinearLayout.LayoutParams introIconParams = new LinearLayout.LayoutParams(dp(44), dp(44));
        introIconParams.rightMargin = dp(13);
        intro.addView(introIcon, introIconParams);

        LinearLayout introCopy = new LinearLayout(this);
        introCopy.setOrientation(LinearLayout.VERTICAL);
        introCopy.addView(text("让息屏提醒稳定响起", 16, INK, true));
        TextView introDetail = text("按需开启计时、闹钟和振动，并确认系统权限。", 12, MUTED, false);
        introDetail.setPadding(0, dp(5), 0, 0);
        introCopy.addView(introDetail);
        intro.addView(introCopy, new LinearLayout.LayoutParams(0, -2, 1));

        addSectionHeading(page, "提醒方式", "息屏时继续计时，到点后按你的选择提醒。");

        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setPadding(dp(15), dp(3), dp(15), dp(3));
        options.setBackground(round(Color.WHITE, 17));
        options.setElevation(dp(1));
        Switch alarm = addSwitchRow(options, "息屏计时并提醒",
                "关闭屏幕时继续累计，到点后通过闹钟提醒。", ReminderSettings.screenOffAlarm(this));
        addDivider(options);
        Switch sound = addSwitchRow(options, "播放闹钟铃声",
                "提醒触发时播放所选铃声。", ReminderSettings.screenOffSound(this));
        addDivider(options);
        Switch vibrate = addSwitchRow(options, "同时振动",
                "响铃提醒时同步振动。", ReminderSettings.screenOffVibrate(this));
        vibrate.setOnCheckedChangeListener((button, checked) ->
                ReminderSettings.prefs(this).edit().putBoolean("screen_off_vibrate", checked).apply());

        LinearLayout ringtoneRow = new LinearLayout(this);
        ringtoneRow.setGravity(Gravity.CENTER_VERTICAL);
        ringtoneRow.setPadding(0, dp(12), 0, dp(12));
        addDivider(options);
        TextView ringtoneIcon = text("♫", 18, GREEN, true);
        ringtoneIcon.setGravity(Gravity.CENTER);
        ringtoneIcon.setBackground(round(MINT_PALE, 12));
        LinearLayout.LayoutParams ringtoneIconParams = new LinearLayout.LayoutParams(dp(40), dp(40));
        ringtoneIconParams.rightMargin = dp(11);
        ringtoneRow.addView(ringtoneIcon, ringtoneIconParams);
        LinearLayout ringtoneCopy = new LinearLayout(this);
        ringtoneCopy.setOrientation(LinearLayout.VERTICAL);
        ringtoneCopy.addView(text("闹钟铃声", 14, INK, true));
        TextView ringtoneName = text(ringtoneName(), 12, MUTED, false);
        ringtoneName.setMaxLines(1);
        ringtoneName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams ringtoneNameParams = new LinearLayout.LayoutParams(-1, -2);
        ringtoneNameParams.topMargin = dp(3);
        ringtoneCopy.addView(ringtoneName, ringtoneNameParams);
        ringtoneRow.addView(ringtoneCopy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView ringtoneValue = text("›", 23, GREEN, false);
        ringtoneValue.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        ringtoneRow.addView(ringtoneValue);
        ringtoneRow.setContentDescription("选择闹钟铃声，" + ringtoneName());
        ringtoneRow.setOnClickListener(v -> pickAlarmRingtone());
        options.addView(ringtoneRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams optionsParams = new LinearLayout.LayoutParams(-1, -2);
        page.addView(options, optionsParams);

        addSectionHeading(page, "后台提醒权限", "点击未开启的项目，前往系统设置授权。");

        LinearLayout permissions = new LinearLayout(this);
        permissions.setOrientation(LinearLayout.VERTICAL);
        permissions.setPadding(dp(15), dp(3), dp(15), dp(3));
        permissions.setBackground(round(Color.WHITE, 17));
        permissions.setElevation(dp(1));
        addPermissionRow(permissions, "精确闹钟", "让提醒按设定时间准确触发。", exactAlarmAllowed(),
                this::openExactAlarmSettings);
        boolean notificationsAllowed = NotificationManagerCompat.from(this).areNotificationsEnabled()
                && (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED);
        addPermissionRow(permissions, "通知权限", "允许应用显示提醒通知。", notificationsAllowed,
                this::openNotificationSettings);
        if (Build.VERSION.SDK_INT >= 34) {
            NotificationManager notifications = getSystemService(NotificationManager.class);
            boolean fullScreenAllowed = notifications != null && notifications.canUseFullScreenIntent();
            addPermissionRow(permissions, "锁屏全屏提醒", "息屏时允许闹钟界面显示在锁屏上。", fullScreenAllowed,
                    this::openFullScreenSettings);
        }
        page.addView(permissions, new LinearLayout.LayoutParams(-1, -2));

        TextView recheck = text("刷新权限状态", 13, GREEN, true);
        recheck.setGravity(Gravity.CENTER);
        recheck.setPadding(dp(14), dp(11), dp(14), dp(11));
        recheck.setBackground(round(MINT_PALE, 16));
        recheck.setOnClickListener(v -> render());
        LinearLayout.LayoutParams recheckParams = new LinearLayout.LayoutParams(-1, -2);
        recheckParams.topMargin = dp(11);
        page.addView(recheck, recheckParams);

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
            AlertDialog confirm = new AlertDialog.Builder(this).setTitle("息屏计时并提醒")
                    .setMessage("开启后，屏幕关闭时仍会累计使用时间，到达提醒时间后播放闹钟。请确认下方所需权限已开启。")
                    .setNegativeButton("取消", (dialog, which) -> button.setChecked(false))
                    .setPositiveButton("开启", (dialog, which) -> {
                        ReminderSettings.prefs(this).edit().putBoolean("screen_off_alarm", true).apply();
                        ReminderScheduler.update(this);
                        updateControlState(true, sound.isChecked(), sound, vibrate,
                                soundRow, vibrateRow, ringtoneRow);
                    })
                    .setOnCancelListener(dialog -> button.setChecked(false))
                    .create();
            confirm.setOnShowListener(dialog -> styleDialog(confirm));
            confirm.show();
        });
        updateControlState(alarm.isChecked(), sound.isChecked(), sound, vibrate,
                soundRow, vibrateRow, ringtoneRow);
    }

    private void addSectionHeading(LinearLayout page, String title, String detail) {
        LinearLayout heading = new LinearLayout(this);
        heading.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(22);
        params.bottomMargin = dp(9);
        page.addView(heading, params);
        heading.addView(text(title, 15, INK, true));
        TextView caption = text(detail, 12, MUTED, false);
        caption.setPadding(0, dp(3), 0, 0);
        heading.addView(caption);
    }

    private Switch addSwitchRow(LinearLayout parent, String title, String detail, boolean checked) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(11), 0, dp(11));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 14, INK, true));
        TextView hint = text(detail, 12, MUTED, false);
        hint.setPadding(0, dp(4), 0, 0);
        copy.addView(hint);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        Switch control = new Switch(this);
        control.setChecked(checked);
        control.setShowText(false);
        control.setThumbTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{MINT, SWITCH_OFF}));
        control.setTrackTintList(new ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{MINT_LIGHT, TRACK_OFF}));
        row.addView(control);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return control;
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(LINE);
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

    private void addPermissionRow(LinearLayout parent, String label, String detail,
                                  boolean allowed, Runnable action) {
        if (parent.getChildCount() > 0) addDivider(parent);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(12), 0, dp(12));
        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(label, 14, INK, true));
        TextView description = text(detail, 12, MUTED, false);
        description.setPadding(0, dp(4), 0, 0);
        copy.addView(description);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text(allowed ? "已开启" : "去开启", 11, allowed ? GREEN : WARN, true);
        value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        value.setBackground(allowed ? round(MINT_PALE, 12) : round(WARN_SOFT, 12, WARN_BORDER));
        value.setPadding(dp(9), dp(7), dp(9), dp(7));
        row.addView(value);
        TextView arrow = text("›", 21, MUTED, false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, new LinearLayout.LayoutParams(dp(22), dp(36)));
        row.setOnClickListener(v -> action.run());
        row.setContentDescription(label + (allowed ? "，已开启" : "，去设置开启"));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
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
        return round(color, radius, UiPalette.BORDER);
    }

    private GradientDrawable round(int color, int radius, int strokeColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(UiPalette.surface(color));
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), strokeColor);
        return shape;
    }

    private void styleDialog(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(round(Color.WHITE, 20));
        }
        int titleId = getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(INK);
            title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) message.setTextColor(MUTED);
        for (int id : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) {
            android.widget.Button button = dialog.getButton(id);
            if (button != null) button.setTextColor(id == AlertDialog.BUTTON_POSITIVE ? GREEN : MUTED);
        }
    }

    private int dp(int value) { return (int) (value * getResources().getDisplayMetrics().density + 0.5f); }
}
