package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;

public final class ItemsActivity extends Activity {
    private static final int GREEN = 0xff277049;
    private static final int INK = 0xff23342a;
    private static final int MUTED = 0xff65776a;
    private static final int PAPER = 0xfff4f8f1;
    private LinearLayout list;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        FrameLayout frame = new FrameLayout(this);
        LinearLayout screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setBackgroundColor(PAPER);
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(20), dp(20), dp(20), dp(84));
        scroll.addView(list);
        screen.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        addNavigation(screen);
        frame.addView(screen);
        TextView add = text("＋", 30, Color.WHITE, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(circle(GREEN));
        add.setElevation(dp(8));
        FrameLayout.LayoutParams fab = new FrameLayout.LayoutParams(dp(58), dp(58), Gravity.RIGHT | Gravity.BOTTOM);
        fab.setMargins(0, 0, dp(22), dp(80));
        frame.addView(add, fab);
        add.setOnClickListener(v -> editRule(null));
        setContentView(frame);
        renderItems();
    }

    @Override protected void onResume() { super.onResume(); if (list != null) renderItems(); }

    private void renderItems() {
        list.removeAllViews();
        list.addView(text("事项", 25, INK, true));
        TextView date = text("今天的完成进度", 13, MUTED, false);
        date.setPadding(0, dp(5), 0, dp(18));
        list.addView(date);
        for (ReminderSettings.Rule rule : ReminderSettings.rules(this)) {
            int done = ReminderSettings.completedToday(this, rule.id);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(14));
            card.setBackground(round(Color.WHITE, 14));
            LinearLayout details = new LinearLayout(this);
            details.setOrientation(LinearLayout.VERTICAL);
            details.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1));
            TextView name = text(rule.title, 16, INK, true);
            TextView progress = text(done >= rule.dailyTarget ? "✓ 已完成  " + done + "/" + rule.dailyTarget : "今日完成  " + done + "/" + rule.dailyTarget + " 次", 16, GREEN, true);
            progress.setPadding(0, dp(7), 0, 0);
            TextView schedule = text(MainActivity.clock(rule.start) + " - " + MainActivity.clock(rule.end) + "  ·  " + intervalText(rule.intervalSeconds), 12, MUTED, false);
            schedule.setPadding(0, dp(5), 0, 0);
            TextView afterTarget = text(rule.remindAfterTarget ? "达标后：继续提醒" : "达标后：停止提醒", 12, MUTED, false);
            afterTarget.setPadding(0, dp(4), 0, 0);
            details.addView(name); details.addView(progress); details.addView(schedule); details.addView(afterTarget);
            card.addView(details);
            TextView star = text("★", 32, done >= rule.dailyTarget ? 0xffffc928 : 0xffc8ceca, true);
            star.setGravity(Gravity.CENTER);
            star.setWidth(dp(48));
            if (done >= rule.dailyTarget) {
                star.setShadowLayer(dp(12), 0, 0, 0xffffd84d);
                ObjectAnimator pulseX = ObjectAnimator.ofFloat(star, View.SCALE_X, 1f, 1.12f);
                ObjectAnimator pulseY = ObjectAnimator.ofFloat(star, View.SCALE_Y, 1f, 1.12f);
                for (ObjectAnimator pulse : new ObjectAnimator[]{pulseX, pulseY}) { pulse.setDuration(850); pulse.setRepeatMode(ValueAnimator.REVERSE); pulse.setRepeatCount(ValueAnimator.INFINITE); pulse.start(); }
            }
            card.addView(star, new LinearLayout.LayoutParams(dp(48), dp(52)));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.bottomMargin = dp(10);
            list.addView(card, params);
            card.setOnClickListener(v -> editRule(rule));
            card.setOnLongClickListener(v -> { showItemHistory(rule); return true; });
        }
        if (ReminderSettings.rules(this).isEmpty()) list.addView(text("点击右下角 + 添加提醒事项", 14, MUTED, false));
    }

    private void editRule(ReminderSettings.Rule existing) {
        int id = existing == null ? ReminderSettings.nextRuleId(this) : existing.id;
        int[] start = {existing == null ? 9 * 60 : existing.start};
        int[] end = {existing == null ? 18 * 60 : existing.end};
        int[] interval = {existing == null ? 30 * 60 : existing.intervalSeconds};
        int[] target = {existing == null ? 3 : existing.dailyTarget};
        boolean[] enabled = {existing == null || existing.enabled};
        boolean[] remindAfterTarget = {existing != null && existing.remindAfterTarget};
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(6), dp(20), 0);
        EditText name = new EditText(this);
        name.setSingleLine(true);
        name.setHint("事项名称");
        name.setText(existing == null ? "" : existing.title);
        form.addView(name);
        TextView from = timeField("开始时间", start[0]);
        TextView to = timeField("结束时间", end[0]);
        from.setOnClickListener(v -> new TimePickerDialog(this, (picker, hour, minute) -> { start[0] = hour * 60 + minute; from.setText("开始时间\n" + MainActivity.clock(start[0])); }, start[0] / 60, start[0] % 60, true).show());
        to.setOnClickListener(v -> new TimePickerDialog(this, (picker, hour, minute) -> { end[0] = hour * 60 + minute; to.setText("结束时间\n" + MainActivity.clock(end[0])); }, end[0] / 60, end[0] % 60, true).show());
        form.addView(from); form.addView(to);
        Button intervalButton = new Button(this); intervalButton.setAllCaps(false);
        intervalButton.setText("提醒间隔  " + intervalText(interval[0]));
        intervalButton.setOnClickListener(v -> chooseInterval(interval, intervalButton));
        form.addView(intervalButton);
        Button targetButton = new Button(this); targetButton.setAllCaps(false); targetButton.setText("每日目标  " + target[0] + " 次");
        targetButton.setOnClickListener(v -> {
            EditText input = new EditText(this); input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER); input.setText(String.valueOf(target[0]));
            new AlertDialog.Builder(this).setTitle("每天想完成几次").setView(input).setNegativeButton("取消", null)
                    .setPositiveButton("确定", (dialog, which) -> { try { target[0] = Math.max(1, Integer.parseInt(input.getText().toString())); targetButton.setText("每日目标  " + target[0] + " 次"); } catch (NumberFormatException ignored) { } }).show();
        });
        form.addView(targetButton);
        TextView goalLabel = text("达标后是否继续提醒", 14, INK, true); goalLabel.setPadding(0, dp(5), 0, 0); form.addView(goalLabel);
        RadioGroup goalOptions = new RadioGroup(this); goalOptions.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton stop = new RadioButton(this); stop.setId(1); stop.setText("否，停止");
        RadioButton continueReminding = new RadioButton(this); continueReminding.setId(2); continueReminding.setText("是，继续");
        goalOptions.addView(stop, new RadioGroup.LayoutParams(0, -2, 1)); goalOptions.addView(continueReminding, new RadioGroup.LayoutParams(0, -2, 1));
        goalOptions.check(remindAfterTarget[0] ? 2 : 1); goalOptions.setOnCheckedChangeListener((group, checkedId) -> remindAfterTarget[0] = checkedId == 2); form.addView(goalOptions);
        Switch enabledSwitch = new Switch(this); enabledSwitch.setText("启用此事项"); enabledSwitch.setChecked(enabled[0]);
        enabledSwitch.setOnCheckedChangeListener((button, checked) -> enabled[0] = checked); form.addView(enabledSwitch);
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(existing == null ? "添加提醒事项" : "编辑提醒事项")
                .setView(form).setNegativeButton("取消", null).setPositiveButton("保存", (dialog, which) -> {
                    String title = name.getText().toString().trim();
                    if (title.isEmpty()) { Toast.makeText(this, "请输入事项名称", Toast.LENGTH_SHORT).show(); return; }
                    ArrayList<ReminderSettings.Rule> updated = new ArrayList<>(ReminderSettings.rules(this)); updated.removeIf(rule -> rule.id == id);
                    updated.add(ReminderSettings.Rule.withIntervalSeconds(id, title, start[0], end[0], interval[0], target[0], remindAfterTarget[0], enabled[0]));
                    ReminderSettings.saveRules(this, updated); renderItems();
                });
        if (existing != null) builder.setNeutralButton("删除", (dialog, which) -> { ArrayList<ReminderSettings.Rule> updated = new ArrayList<>(ReminderSettings.rules(this)); updated.removeIf(rule -> rule.id == id); ReminderSettings.saveRules(this, updated); renderItems(); });
        builder.show();
    }

    private void showItemHistory(ReminderSettings.Rule rule) {
        Calendar selectedDay = Calendar.getInstance();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(18), dp(20), dp(12));
        panel.setBackground(round(Color.WHITE, 20));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        titleGroup.addView(text(rule.title, 19, INK, true));
        TextView subtitle = text("今天每一次照顾自己，都值得记下来", 12, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, 0);
        titleGroup.addView(subtitle);
        header.addView(titleGroup, new LinearLayout.LayoutParams(0, -2, 1));
        View calendarButton = new CalendarButton();
        calendarButton.setContentDescription("选择日期");
        calendarButton.setBackground(round(0xffedf6ec, 12));
        header.addView(calendarButton, new LinearLayout.LayoutParams(dp(42), dp(42)));
        panel.addView(header);

        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        dateRow.setPadding(0, dp(14), 0, dp(10));
        TextView previous = text("‹", 28, GREEN, true);
        previous.setGravity(Gravity.CENTER);
        dateRow.addView(previous, new LinearLayout.LayoutParams(dp(42), dp(38)));
        TextView dateLabel = text("", 14, INK, true);
        dateLabel.setGravity(Gravity.CENTER);
        dateRow.addView(dateLabel, new LinearLayout.LayoutParams(0, dp(38), 1));
        TextView next = text("›", 28, GREEN, true);
        next.setGravity(Gravity.CENTER);
        dateRow.addView(next, new LinearLayout.LayoutParams(dp(42), dp(38)));
        panel.addView(dateRow);

        TextView totals = text("", 13, MUTED, true);
        totals.setPadding(0, 0, 0, dp(10));
        panel.addView(totals);

        LinearLayout history = new LinearLayout(this);
        history.setOrientation(LinearLayout.VERTICAL);
        ScrollView historyScroll = new ScrollView(this);
        historyScroll.setFillViewport(true);
        historyScroll.addView(history);
        panel.addView(historyScroll, new LinearLayout.LayoutParams(-1, dp(300)));

        Runnable refresh = () -> renderItemHistory(rule, selectedDay, dateLabel, totals, history);
        View.OnClickListener chooseDate = v -> new DatePickerDialog(this, (picker, year, month, day) -> {
            selectedDay.set(year, month, day);
            refresh.run();
        }, selectedDay.get(Calendar.YEAR), selectedDay.get(Calendar.MONTH), selectedDay.get(Calendar.DAY_OF_MONTH)).show();
        calendarButton.setOnClickListener(chooseDate);
        dateLabel.setOnClickListener(chooseDate);
        previous.setOnClickListener(v -> { selectedDay.add(Calendar.DAY_OF_MONTH, -1); refresh.run(); });
        next.setOnClickListener(v -> { selectedDay.add(Calendar.DAY_OF_MONTH, 1); refresh.run(); });
        refresh.run();

        AlertDialog dialog = new AlertDialog.Builder(this).setView(panel).create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels - dp(32), -2);
        }
    }

    private void renderItemHistory(ReminderSettings.Rule rule, Calendar day, TextView dateLabel,
                                   TextView totals, LinearLayout history) {
        dateLabel.setText(new SimpleDateFormat("yyyy年M月d日  EEEE", Locale.CHINA).format(day.getTime()));
        ArrayList<ReminderRecord> records = new ArrayList<>();
        int done = 0;
        int missed = 0;
        for (ReminderRecord record : ReminderRecord.all(this)) {
            if (!rule.title.equals(record.action) || !sameDay(record.startedAt, day)) continue;
            if (ReminderRecord.KIND_DONE.equals(record.kind)) done++;
            else if (ReminderRecord.KIND_MISSED.equals(record.kind)) missed++;
            else continue;
            records.add(record);
        }
        Collections.sort(records, Comparator.comparingLong(record -> record.startedAt));
        totals.setText("完成 " + done + " 次    没做 " + missed + " 次");
        history.removeAllViews();
        for (ReminderRecord record : records) {
            boolean completed = ReminderRecord.KIND_DONE.equals(record.kind);
            int accent = completed ? GREEN : 0xffbf665a;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.TOP);
            row.setPadding(dp(12), dp(11), dp(12), dp(11));
            row.setBackground(round(completed ? 0xffedf6ec : 0xfffff1ed, 12));
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.bottomMargin = dp(8);
            TextView marker = text(completed ? "✓" : "·", 18, accent, true);
            marker.setGravity(Gravity.CENTER);
            row.addView(marker, new LinearLayout.LayoutParams(dp(24), dp(24)));
            LinearLayout detail = new LinearLayout(this);
            detail.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(0, -2, 1);
            detailParams.leftMargin = dp(8);
            row.addView(detail, detailParams);
            detail.addView(text(completed ? "做到了" : "这次没做", 14, INK, true));
            String note = record.reason == null ? "" : record.reason.trim();
            if (completed && "提前完成".equals(note)) note = "提前完成一次";
            if (!note.isEmpty()) {
                TextView noteView = text(note, 13, MUTED, false);
                noteView.setPadding(0, dp(3), 0, 0);
                detail.addView(noteView);
            }
            TextView time = text(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(record.startedAt), 11, accent, true);
            time.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
            row.addView(time, new LinearLayout.LayoutParams(-2, dp(24)));
            history.addView(row, rowParams);
        }
        if (records.isEmpty()) {
            TextView empty = text("这一天还没有“" + rule.title + "”的记录", 13, MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(32), 0, dp(32));
            history.addView(empty);
        }
    }

    private boolean sameDay(long timestamp, Calendar day) {
        Calendar value = Calendar.getInstance();
        value.setTimeInMillis(timestamp);
        return value.get(Calendar.YEAR) == day.get(Calendar.YEAR)
                && value.get(Calendar.DAY_OF_YEAR) == day.get(Calendar.DAY_OF_YEAR);
    }

    private final class CalendarButton extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CalendarButton() {
            super(ItemsActivity.this);
            paint.setColor(GREEN);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.8f));
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = getResources().getDisplayMetrics().density;
            float x = (getWidth() - dp(20)) / 2f;
            float y = (getHeight() - dp(20)) / 2f;
            canvas.save();
            canvas.translate(x, y);
            canvas.scale(scale, scale);
            canvas.drawRoundRect(new RectF(2, 4, 18, 19), 2, 2, paint);
            canvas.drawLine(2, 8, 18, 8, paint);
            canvas.drawLine(6, 2, 6, 6, paint);
            canvas.drawLine(14, 2, 14, 6, paint);
            paint.setStyle(Paint.Style.FILL);
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 3; col++) canvas.drawCircle(6 + col * 4, 12 + row * 3, 0.8f, paint);
            }
            paint.setStyle(Paint.Style.STROKE);
            canvas.restore();
        }
    }

    private TextView timeField(String title, int minute) {
        TextView field = text(title + "\n" + MainActivity.clock(minute), 15, GREEN, true); field.setGravity(Gravity.CENTER);
        field.setPadding(dp(10), dp(10), dp(10), dp(10)); field.setBackground(round(0xffe7f1e5, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(6); field.setLayoutParams(params); return field;
    }

    private void chooseInterval(int[] interval, Button button) {
        List<Integer> saved = readSavedIntervals();
        List<Integer> favorites = readFavoriteIntervals();
        saved.sort((a, b) -> {
            int favoriteOrder = Boolean.compare(favorites.contains(b), favorites.contains(a));
            return favoriteOrder != 0 ? favoriteOrder : Integer.compare(a, b);
        });
        ArrayList<String> options = new ArrayList<>();
        for (int value : saved) options.add((favorites.contains(value) ? "★ 常用 · " : "自定义 · ") + intervalText(value));
        int savedCount = options.size();
        options.add("自定义时间…");
        options.add("管理自定义时间…");
        options.add("每 5 分钟");
        options.add("每 10 分钟");
        options.add("每 15 分钟");
        options.add("每 20 分钟");
        options.add("每 30 分钟");
        options.add("每 45 分钟");
        options.add("每 1 小时");
        options.add("每 1 小时 30 分钟");
        new AlertDialog.Builder(this).setTitle("提醒间隔").setItems(options.toArray(new String[0]), (dialog, which) -> {
            if (which < savedCount) {
                setInterval(interval, button, saved.get(which));
            } else if (which == savedCount) {
                showCustomIntervalPicker(interval, button);
            } else if (which == savedCount + 1) {
                manageSavedIntervals(interval, button);
            } else {
                int[] values = {5, 10, 15, 20, 30, 45, 60, 90};
                setInterval(interval, button, values[which - savedCount - 2] * 60);
            }
        }).show();
    }

    private void showCustomIntervalPicker(int[] interval, Button button) {
        LinearLayout fields = new LinearLayout(this);
        fields.setGravity(Gravity.CENTER);
        fields.setOrientation(LinearLayout.HORIZONTAL);
        fields.setPadding(dp(12), dp(8), dp(12), dp(8));
        fields.setBackground(round(0xfff1f6ef, 12));
        NumberPicker hours = new NumberPicker(this);
        hours.setMinValue(0);
        hours.setMaxValue(23);
        hours.setWrapSelectorWheel(false);
        hours.setValue(interval[0] / 3600);
        NumberPicker minutes = new NumberPicker(this);
        minutes.setMinValue(0);
        minutes.setMaxValue(59);
        minutes.setWrapSelectorWheel(false);
        minutes.setValue(interval[0] / 60 % 60);
        NumberPicker seconds = new NumberPicker(this);
        seconds.setMinValue(0);
        seconds.setMaxValue(59);
        seconds.setWrapSelectorWheel(false);
        seconds.setValue(interval[0] % 60);
        addIntervalWheel(fields, hours, "小时");
        addIntervalWheel(fields, minutes, "分钟");
        addIntervalWheel(fields, seconds, "秒");
        AlertDialog pickerDialog = new AlertDialog.Builder(this).setTitle("自定义提醒间隔")
                .setMessage("选择提醒间隔")
                .setView(fields).setNegativeButton("取消", null)
                .setNeutralButton("收藏置顶", null).setPositiveButton("确定", null).create();
        pickerDialog.setOnShowListener(ignored -> {
            pickerDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int total = hours.getValue() * 3600 + minutes.getValue() * 60 + seconds.getValue();
                if (total <= 0) {
                    Toast.makeText(this, "提醒间隔至少为 1 秒", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!addSavedInterval(total)) {
                    Toast.makeText(this, "保存自定义时间失败，请重试", Toast.LENGTH_SHORT).show();
                    return;
                }
                setInterval(interval, button, total);
                Toast.makeText(this, "已保存自定义时间，下次可在间隔选项顶部选择", Toast.LENGTH_SHORT).show();
                pickerDialog.dismiss();
            });
            pickerDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                int total = hours.getValue() * 3600 + minutes.getValue() * 60 + seconds.getValue();
                if (total <= 0) {
                    Toast.makeText(this, "提醒间隔至少为 1 秒", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!addSavedInterval(total)) {
                    Toast.makeText(this, "保存自定义时间失败，请重试", Toast.LENGTH_SHORT).show();
                    return;
                }
                List<Integer> favorites = readFavoriteIntervals();
                if (!favorites.contains(total)) favorites.add(total);
                writeIntervalList("favorite_item_intervals_seconds", favorites);
                setInterval(interval, button, total);
                pickerDialog.dismiss();
            });
        });
        pickerDialog.show();
    }

    private void manageSavedIntervals(int[] interval, Button button) {
        List<Integer> saved = readSavedIntervals();
        saved.sort(Integer::compareTo);
        if (saved.isEmpty()) {
            Toast.makeText(this, "还没有自定义时间", Toast.LENGTH_SHORT).show();
            return;
        }
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(16), dp(8), dp(16), dp(8));
        AlertDialog manager = new AlertDialog.Builder(this).setTitle("管理自定义时间")
                .setView(rows).setPositiveButton("完成", null).create();
        Runnable[] refresh = new Runnable[1];
        refresh[0] = () -> {
            rows.removeAllViews();
            List<Integer> current = readSavedIntervals();
            current.sort(Integer::compareTo);
            for (int value : current) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                TextView label = text(intervalText(value), 15, INK, true);
                row.addView(label, new LinearLayout.LayoutParams(0, dp(48), 1));
                TextView favorite = text(readFavoriteIntervals().contains(value) ? "★" : "☆", 22,
                        readFavoriteIntervals().contains(value) ? 0xffd59b28 : MUTED, true);
                favorite.setGravity(Gravity.CENTER);
                favorite.setContentDescription("收藏到顶部");
                favorite.setOnClickListener(v -> {
                    List<Integer> updated = readFavoriteIntervals();
                    if (updated.contains(value)) updated.remove(Integer.valueOf(value));
                    else updated.add(value);
                    writeIntervalList("favorite_item_intervals_seconds", updated);
                    refresh[0].run();
                });
                row.addView(favorite, new LinearLayout.LayoutParams(dp(44), dp(48)));
                TextView delete = text("×", 22, 0xffbd5147, false);
                delete.setGravity(Gravity.CENTER);
                delete.setContentDescription("删除自定义时间");
                delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                        .setMessage("删除 " + intervalText(value) + "？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (d, w) -> {
                            List<Integer> updated = readSavedIntervals();
                            updated.remove(Integer.valueOf(value));
                            writeIntervalList("custom_item_intervals_seconds", updated);
                            List<Integer> starred = readFavoriteIntervals();
                            starred.remove(Integer.valueOf(value));
                            writeIntervalList("favorite_item_intervals_seconds", starred);
                            refresh[0].run();
                            if (interval[0] == value) setInterval(interval, button, 30 * 60);
                        }).show());
                row.addView(delete, new LinearLayout.LayoutParams(dp(40), dp(48)));
                rows.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
            }
        };
        manager.setOnShowListener(ignored -> refresh[0].run());
        manager.show();
    }

    private List<Integer> readSavedIntervals() {
        return readIntervalList("custom_item_intervals_seconds", "custom_item_intervals", 60);
    }

    private List<Integer> readFavoriteIntervals() {
        return readIntervalList("favorite_item_intervals_seconds", "favorite_item_intervals", 60);
    }

    private List<Integer> readIntervalList(String key, String legacyKey, int legacyMultiplier) {
        java.util.Set<String> stored = ReminderSettings.prefs(this).getStringSet(key, null);
        if (stored == null) {
            java.util.Set<String> legacy = ReminderSettings.prefs(this).getStringSet(legacyKey, java.util.Collections.emptySet());
            ArrayList<Integer> migrated = new ArrayList<>();
            for (String value : legacy) {
                try { migrated.add(Integer.parseInt(value) * legacyMultiplier); }
                catch (NumberFormatException ignored) { }
            }
            if (!migrated.isEmpty()) writeIntervalList(key, migrated);
            stored = ReminderSettings.prefs(this).getStringSet(key, java.util.Collections.emptySet());
        }
        ArrayList<Integer> values = new ArrayList<>();
        for (String value : new java.util.HashSet<>(stored)) {
            try {
                int seconds = Integer.parseInt(value);
                if (seconds > 0 && seconds <= 86399 && !values.contains(seconds)) values.add(seconds);
            } catch (NumberFormatException ignored) { }
        }
        return values;
    }

    private boolean writeIntervalList(String key, List<Integer> values) {
        java.util.Set<String> stored = new java.util.HashSet<>();
        for (int value : values) stored.add(Integer.toString(value));
        return ReminderSettings.prefs(this).edit().putStringSet(key, stored).commit();
    }

    private boolean addSavedInterval(int value) {
        List<Integer> saved = readSavedIntervals();
        if (!saved.contains(value)) saved.add(value);
        return writeIntervalList("custom_item_intervals_seconds", saved)
                && readSavedIntervals().contains(value);
    }

    private void setInterval(int[] interval, Button button, int value) {
        interval[0] = value;
        button.setText("提醒间隔  " + intervalText(value));
    }

    private void addIntervalWheel(LinearLayout parent, NumberPicker picker, String unit) {
        LinearLayout column = new LinearLayout(this);
        column.setGravity(Gravity.CENTER);
        column.setOrientation(LinearLayout.VERTICAL);
        column.addView(picker, new LinearLayout.LayoutParams(dp(76), dp(112)));
        TextView label = text(unit, 13, MUTED, true);
        label.setGravity(Gravity.CENTER);
        column.addView(label, new LinearLayout.LayoutParams(-1, dp(24)));
        parent.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private String intervalText(int seconds) {
        int h = seconds / 3600, m = seconds / 60 % 60, s = seconds % 60;
        StringBuilder label = new StringBuilder("每 ");
        if (h > 0) label.append(h).append(" 小时 ");
        if (m > 0) label.append(m).append(" 分钟 ");
        if (s > 0 || (h == 0 && m == 0)) label.append(s).append(" 秒");
        return label.toString().trim();
    }

    private void addNavigation(LinearLayout screen) {
        LinearLayout nav = BottomNavigationBar.create(this, BottomNavigationBar.ITEMS,
                () -> { finish(); overridePendingTransition(0, 0); },
                null,
                () -> { startActivity(new Intent(this, CompanionLandingActivity.class)); overridePendingTransition(0, 0); finish(); },
                () -> { startActivity(new Intent(this, StatsActivity.class)); overridePendingTransition(0, 0); finish(); });
        screen.addView(nav, new LinearLayout.LayoutParams(-1, dp(60)));
    }

    private TextView text(String value, int size, int color, boolean bold) { TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return t; }
    private GradientDrawable round(int color, int radius) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private GradientDrawable circle(int color) { GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setShape(GradientDrawable.OVAL); return d; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
