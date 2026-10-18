package io.iftech.healthreminder;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class StatsActivity extends Activity {
    private static final int GREEN = 0xff277049;
    private static final int INK = 0xff23342a;
    private static final int MUTED = 0xff65776a;
    private static final int PAPER = 0xfff4f8f1;
    private Calendar month = Calendar.getInstance();
    private Calendar selected = Calendar.getInstance();
    private String filter = ReminderRecord.KIND_DONE;
    private LinearLayout content;
    private TextView monthLabel;
    private float touchX;
    private float touchY;
    private long lastMonthLabelTap;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        month.set(Calendar.DAY_OF_MONTH, 1);
        render();
    }

    private void render() {
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(PAPER);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(18), dp(20), dp(20));
        scroll.addView(content);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(screen);

        TextView title = text("每日记录", 24, INK, true);
        content.addView(title);
        TextView help = text("看看自己每天照顾身体的時刻", 13, MUTED, false);
        help.setPadding(0, dp(3), 0, dp(16));
        content.addView(help);

        LinearLayout calendar = new LinearLayout(this);
        calendar.setOrientation(LinearLayout.VERTICAL);
        calendar.setPadding(dp(14), dp(14), dp(14), dp(12));
        calendar.setBackground(round(Color.WHITE, 18));
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView previous = text("‹", 28, GREEN, true);
        TextView next = text("›", 28, GREEN, true);
        monthLabel = text("", 17, INK, true);
        previous.setOnClickListener(v -> shiftMonth(-1));
        next.setOnClickListener(v -> shiftMonth(1));
        monthLabel.setOnClickListener(v -> {
            long now = android.os.SystemClock.elapsedRealtime();
            if (now - lastMonthLabelTap < 450) {
                Calendar today = Calendar.getInstance();
                selected = (Calendar) today.clone();
                month = (Calendar) today.clone();
                month.set(Calendar.DAY_OF_MONTH, 1);
                render();
            }
            lastMonthLabelTap = now;
        });
        head.addView(previous, new LinearLayout.LayoutParams(dp(42), dp(40)));
        head.addView(monthLabel, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(next, new LinearLayout.LayoutParams(dp(42), dp(40)));
        calendar.addView(head);
        String[] week = {"日", "一", "二", "三", "四", "五", "六"};
        LinearLayout weekRow = new LinearLayout(this);
        for (String day : week) {
            TextView cell = text(day, 12, MUTED, false);
            cell.setGravity(Gravity.CENTER);
            weekRow.addView(cell, new LinearLayout.LayoutParams(0, dp(30), 1));
        }
        calendar.addView(weekRow);
        buildDays(calendar);
        content.addView(calendar);

        TextView dayHeading = text(DateFormat.getDateInstance(DateFormat.FULL).format(selected.getTime()), 16, INK, true);
        dayHeading.setPadding(0, dp(20), 0, dp(12));
        content.addView(dayHeading);
        int[] totals = totalsForDay(selected);
        TextView summary = text("完成 " + totals[0] + " 次    未完成 " + totals[1] + " 次", 13, MUTED, false);
        summary.setPadding(0, 0, 0, dp(12));
        content.addView(summary);
        LinearLayout pills = new LinearLayout(this);
        pills.setPadding(dp(4), dp(4), dp(4), dp(4));
        pills.setBackground(round(0xffe4ece2, 24));
        TextView done = pill("做了", ReminderRecord.KIND_DONE.equals(filter));
        TextView missed = pill("没做", ReminderRecord.KIND_MISSED.equals(filter));
        done.setOnClickListener(v -> { filter = ReminderRecord.KIND_DONE; render(); });
        missed.setOnClickListener(v -> { filter = ReminderRecord.KIND_MISSED; render(); });
        pills.addView(done, new LinearLayout.LayoutParams(0, dp(40), 1));
        pills.addView(missed, new LinearLayout.LayoutParams(0, dp(40), 1));
        content.addView(pills);
        showTimeline();
        addNavigation(screen);
    }

    private void shiftMonth(int amount) { month.add(Calendar.MONTH, amount); render(); }

    private void buildDays(LinearLayout calendar) {
        monthLabel.setText(new SimpleDateFormat("yyyy年 M月", Locale.CHINA).format(month.getTime()));
        Calendar cursor = (Calendar) month.clone();
        int offset = cursor.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY;
        cursor.add(Calendar.DAY_OF_MONTH, -offset);
        for (int week = 0; week < 6; week++) {
            LinearLayout row = new LinearLayout(this);
            for (int day = 0; day < 7; day++) {
                Calendar cellDate = (Calendar) cursor.clone();
                TextView cell = text(String.valueOf(cellDate.get(Calendar.DAY_OF_MONTH)), 13,
                        cellDate.get(Calendar.MONTH) == month.get(Calendar.MONTH) ? INK : 0xffb5bfb6, false);
                cell.setGravity(Gravity.CENTER);
                boolean today = sameDay(cellDate, Calendar.getInstance());
                boolean isSelected = sameDay(cellDate, selected);
                if (today || isSelected) {
                    GradientDrawable marker = new GradientDrawable();
                    marker.setShape(GradientDrawable.OVAL);
                    marker.setColor(isSelected ? 0xffdcebdc : Color.TRANSPARENT);
                    if (today) marker.setStroke(dp(1), GREEN);
                    cell.setBackground(marker);
                }
                cell.setLayoutParams(new LinearLayout.LayoutParams(dp(34), dp(34)));
                LinearLayout wrapper = new LinearLayout(this);
                wrapper.setGravity(Gravity.CENTER);
                wrapper.addView(cell);
                wrapper.setOnClickListener(v -> { selected = cellDate; render(); });
                row.addView(wrapper, new LinearLayout.LayoutParams(0, dp(40), 1));
                cursor.add(Calendar.DAY_OF_MONTH, 1);
            }
            calendar.addView(row);
        }
    }

    private void showTimeline() {
        List<ReminderRecord> records = ReminderRecord.all(this);
        int count = 0;
        for (ReminderRecord record : records) {
            if (!sameDay(record.startedAt, selected) || !record.kind.equals(filter)) continue;
            count++;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(16), dp(12), dp(16), dp(12));
            item.setBackground(round(Color.WHITE, 14));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(8);
            item.addView(text(time(record.startedAt) + "  ·  " + record.action, 15, INK, true));
            if (ReminderRecord.KIND_DONE.equals(filter)) {
                long elapsed = Math.max(0, record.endedAt - record.startedAt) / 1000;
                item.addView(text("完成 " + time(record.endedAt) + "  ·  用时 " + elapsed + " 秒", 12, MUTED, false));
            } else {
                item.addView(text("拒绝于 " + time(record.endedAt), 12, MUTED, false));
                item.addView(text("原因：" + record.reason, 13, MUTED, false));
            }
            content.addView(item, params);
        }
        if (count == 0) {
            TextView empty = text(ReminderRecord.KIND_DONE.equals(filter) ? "这一天还没有完成记录" : "这一天没有未完成记录", 13, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(30), 0, dp(30));
            content.addView(empty);
        }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) { touchX = event.getX(); touchY = event.getY(); }
        boolean handled = super.dispatchTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float dx = event.getX() - touchX;
            float dy = event.getY() - touchY;
            if (Math.abs(dx) > dp(70) && Math.abs(dx) > Math.abs(dy) && touchY < dp(430)) {
                shiftMonth(dx > 0 ? -1 : 1);
                return true;
            }
        }
        return handled;
    }

    private int[] totalsForDay(Calendar day) {
        int[] totals = new int[2];
        for (ReminderRecord record : ReminderRecord.all(this)) {
            if (!sameDay(record.startedAt, day)) continue;
            totals[ReminderRecord.KIND_DONE.equals(record.kind) ? 0 : 1]++;
        }
        return totals;
    }

    private void addNavigation(LinearLayout screen) {
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        nav.setBackgroundColor(Color.WHITE);
        TextView home = text("⌂\n首页", 13, MUTED, true);
        TextView stats = text("◷\n统计", 13, GREEN, true);
        home.setGravity(Gravity.CENTER);
        stats.setGravity(Gravity.CENTER);
        home.setOnClickListener(v -> { finish(); overridePendingTransition(0, 0); });
        TextView items = text("事项", 13, MUTED, true);
        items.setGravity(Gravity.CENTER);
        setTabText(home, "⌂", "首页"); setTabText(items, "▦", "事项"); setTabText(stats, "◷", "统计");
        home.setText("⌂\n首页"); items.setText("▦\n事项"); stats.setText("◷\n统计");
        items.setOnClickListener(v -> { startActivity(new Intent(this, ItemsActivity.class)); overridePendingTransition(0, 0); finish(); });
        nav.addView(home, new LinearLayout.LayoutParams(0, dp(60), 1));
        nav.addView(items, new LinearLayout.LayoutParams(0, dp(60), 1));
        nav.addView(stats, new LinearLayout.LayoutParams(0, dp(60), 1));
        screen.addView(nav);
    }

    private void setTabText(TextView tab, String icon, String title) {
        android.text.SpannableString value = new android.text.SpannableString(icon + "\n" + title);
        value.setSpan(new android.text.style.RelativeSizeSpan(icon.equals("◷") ? 2f : 1.65f), 0, icon.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        tab.setText(value);
    }

    private TextView pill(String label, boolean selected) {
        TextView view = text(label, 14, selected ? Color.WHITE : MUTED, true);
        view.setGravity(Gravity.CENTER);
        if (selected) view.setBackground(round(GREEN, 20));
        return view;
    }

    private boolean sameDay(long time, Calendar day) {
        Calendar value = Calendar.getInstance();
        value.setTimeInMillis(time);
        return value.get(Calendar.YEAR) == day.get(Calendar.YEAR)
                && value.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR);
    }

    private boolean sameDay(Calendar left, Calendar right) { return sameDay(left.getTimeInMillis(), right); }
    private String time(long value) { return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(value)); }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return view;
    }
    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable(); shape.setColor(color); shape.setCornerRadius(dp(radius)); return shape;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
