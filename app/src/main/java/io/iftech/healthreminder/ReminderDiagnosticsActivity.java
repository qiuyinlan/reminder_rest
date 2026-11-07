package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlarmManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.NotificationManagerCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 排查「到点不提醒」：显示开关、权限、每条规则的下一闹钟，并可手动重排/测试。 */
public final class ReminderDiagnosticsActivity extends Activity {
    private LinearLayout content;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(UiPalette.page());
        setContentView(root);
        root.addView(header(), new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(8), dp(18), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        render();
    }

    private LinearLayout header() {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(18), dp(10), dp(18), dp(6));
        TextView back = text("‹", 29, UiPalette.INK, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(Color.WHITE, 14));
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView title = text("提醒诊断", 22, UiPalette.INK, true);
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title);
        return header;
    }

    private void render() {
        content.removeAllViews();
        content.addView(card("状态", statusText()), margin());
        content.addView(card("规则的下一闹钟", rulesText()), margin());
        content.addView(card("最近闹钟记录", debugText()), margin());

        TextView reschedule = button("重新安排全部提醒", true);
        reschedule.setOnClickListener(v -> {
            ReminderScheduler.update(this);
            ReminderDebug.note(this, "手动点击重新安排全部提醒");
            Toast.makeText(this, "已重新安排", Toast.LENGTH_SHORT).show();
            render();
        });
        content.addView(reschedule, topMargin(14));

        TextView testAlarm = button("测试霸屏页", false);
        testAlarm.setOnClickListener(v -> {
            startActivity(new Intent(this, ReminderActivity.class)
                    .putExtra("action", "测试提醒").putExtra("rule_id", -1));
        });
        content.addView(testAlarm, topMargin(9));

        TextView testNotify = button("测试通知/全屏", false);
        testNotify.setOnClickListener(v -> {
            ReminderDebug.note(this, "手动点击测试通知");
            ReminderReceiver.post(this, "测试提醒", -1);
            Toast.makeText(this, "已发出测试通知", Toast.LENGTH_SHORT).show();
        });
        content.addView(testNotify, topMargin(9));

        TextView openScreen = button("打开息屏提醒设置", false);
        openScreen.setOnClickListener(v -> startActivity(new Intent(this, ScreenOffSettingsActivity.class)));
        content.addView(openScreen, topMargin(9));
    }

    private String statusText() {
        AlarmManager alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
        boolean exact = Build.VERSION.SDK_INT < 31 || (alarms != null && alarms.canScheduleExactAlarms());
        boolean notify = NotificationManagerCompat.from(this).areNotificationsEnabled();
        boolean overlay = Settings.canDrawOverlays(this);
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        boolean battery = power != null && power.isIgnoringBatteryOptimizations(getPackageName());
        return "总开关：" + yesNo(ReminderSettings.enabled(this))
                + "\n息屏闹钟：" + yesNo(ReminderSettings.screenOffAlarm(this))
                + "\n精确闹钟权限：" + yesNo(exact)
                + "\n通知权限：" + yesNo(notify)
                + "\n悬浮窗权限：" + yesNo(overlay)
                + "\n电池优化白名单：" + yesNo(battery)
                + "\n当前界面在前台：" + yesNo(ReminderApplication.isInForeground());
    }

    private String rulesText() {
        List<ReminderSettings.Rule> rules = ReminderSettings.scheduledRules(this);
        if (rules.isEmpty()) return "没有任何规则。";
        StringBuilder text = new StringBuilder();
        for (ReminderSettings.Rule rule : rules) {
            String module = rule.isBodyModule() ? "身体" : rule.isTaskModule() ? "任务"
                    : rule.isCardModule() ? "卡片" : "其他";
            long next = ReminderScheduler.scheduledAt(this, rule.id);
            text.append(module).append(" · ").append(rule.title)
                    .append(rule.enabled ? "" : "（已关闭）").append("\n");
            text.append("  下一闹钟：").append(next <= 0 ? "未安排" : time(next)).append("\n");
        }
        return text.toString();
    }

    private String debugText() {
        String log = ReminderDebug.all(this);
        return log.isEmpty() ? "还没有闹钟触发记录。" : log;
    }

    private LinearLayout card(String title, String body) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(13), dp(15), dp(13));
        card.setBackground(round(Color.WHITE, 16));
        card.setElevation(dp(1));
        card.addView(text(title, 15, UiPalette.INK, true));
        TextView detail = text(body, 12, UiPalette.MUTED, false);
        detail.setLineSpacing(dp(3), 1f);
        detail.setTextIsSelectable(true);
        detail.setPadding(0, dp(7), 0, 0);
        card.addView(detail);
        return card;
    }

    private TextView button(String label, boolean primary) {
        TextView view = text(label, 15, primary ? Color.WHITE : UiPalette.INK, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(primary ? round(UiPalette.ACCENT, 14) : round(Color.WHITE, 14));
        view.setPadding(0, dp(12), 0, dp(12));
        return view;
    }

    private LinearLayout.LayoutParams margin() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        return params;
    }

    private LinearLayout.LayoutParams topMargin(int dp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = this.dp(dp);
        return params;
    }

    private String time(long millis) {
        return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(new Date(millis));
    }

    private String yesNo(boolean value) {
        return value ? "正常" : "未开启 / 未授权";
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

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
