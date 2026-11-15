package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** 微习惯编辑器：修改名字和频率（7 个圆点）。 */
public final class HabitEditor extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());
        
        int habitId = getIntent().getIntExtra(EXTRA_HABIT_ID, -1);
        if (habitId < 0) {
            finish();
            return;
        }

        Habits.Habit found = null;
        for (Habits.Habit h : Habits.all(this)) {
            if (h.id == habitId) {
                found = h;
                break;
            }
        }
        if (found == null) {
            finish();
            return;
        }
        final Habits.Habit habit = found;

        android.widget.FrameLayout shell = new android.widget.FrameLayout(this);
        shell.setBackgroundColor(UiPalette.PAGE);
        setContentView(shell);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiPalette.PAGE);
        shell.addView(root, new android.widget.FrameLayout.LayoutParams(-1, -1));

        // 头部
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(10));
        TextView title = text("编辑习惯", 24, UiPalette.INK, true);
        header.addView(title);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout scroll = new LinearLayout(this);
        scroll.setOrientation(LinearLayout.VERTICAL);
        scroll.setPadding(dp(16), dp(4), dp(16), dp(96));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        // 名字编辑
        scroll.addView(text("习惯名称", 13, UiPalette.MUTED, true));
        EditText nameInput = new EditText(this);
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nameInput.setText(habit.title);
        nameInput.setBackground(UiPalette.inputBackground(this, Color.WHITE, 10));
        nameInput.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
        nameParams.topMargin = dp(8);
        nameParams.bottomMargin = dp(20);
        scroll.addView(nameInput, nameParams);

        // 频率选择
        scroll.addView(text("重复频率", 13, UiPalette.MUTED, true));
        TextView hint = text("点击圆点切换选择", 12, UiPalette.MUTED, false);
        hint.setPadding(0, dp(4), 0, dp(12));
        scroll.addView(hint);

        LinearLayout freqRow = new LinearLayout(this);
        freqRow.setOrientation(LinearLayout.HORIZONTAL);
        freqRow.setGravity(Gravity.CENTER);
        scroll.addView(freqRow, new LinearLayout.LayoutParams(-1, -2));

        final int[] mask = {habit.frequencyMask};
        String[] labels = {"一", "二", "三", "四", "五", "六", "日"};
        int[] bits = {1, 2, 4, 8, 16, 32, 64};

        List<TextView> dots = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams colParams = new LinearLayout.LayoutParams(0, -2, 1f);
            colParams.rightMargin = dp(4);
            freqRow.addView(col, colParams);

            TextView dot = new TextView(this);
            dot.setText(labels[i]);
            dot.setTextSize(14);
            dot.setTypeface(null, Typeface.BOLD);
            dot.setGravity(Gravity.CENTER);
            dot.setBackground(FormKit.round(this, Color.WHITE, 20));
            dot.setElevation(dp(2));
            dot.setMinimumWidth(dp(40));
            dot.setMinimumHeight(dp(40));
            dot.setPadding(dp(10), dp(8), dp(10), dp(8));
            col.addView(dot, new LinearLayout.LayoutParams(-2, -2));

            boolean selected = (mask[0] & bits[i]) != 0;
            updateDot(dot, selected);

            final int bit = bits[i];
            final TextView dotRef = dot;
            dot.setOnClickListener(v -> {
                if ((mask[0] & bit) != 0) {
                    mask[0] &= ~bit;
                } else {
                    mask[0] |= bit;
                }
                updateDot(dotRef, (mask[0] & bit) != 0);
            });

            dots.add(dot);
        }

        // 底部按钮
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(-1, -2);
        btnParams.topMargin = dp(30);
        scroll.addView(buttons, btnParams);

        TextView cancel = text("取消", 15, UiPalette.MUTED, true);
        cancel.setPadding(dp(20), dp(12), dp(20), dp(12));
        cancel.setOnClickListener(v -> finish());
        buttons.addView(cancel);

        TextView delete = text("删除", 15, 0xffc85a4c, true);
        delete.setPadding(dp(20), dp(12), dp(20), dp(12));
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("删除习惯")
                .setMessage("确定删除「" + habit.title + "」？完成记录会一起删掉。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    Habits.delete(this, habit.id);
                    Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                })
                .show());
        buttons.addView(delete);

        TextView save = text("保存", 15, Color.WHITE, true);
        save.setPadding(dp(20), dp(12), dp(20), dp(12));
        GradientDrawable saveBg = new GradientDrawable();
        saveBg.setColor(UiPalette.ACCENT);
        saveBg.setCornerRadius(dp(24));
        save.setBackground(saveBg);
        save.setOnClickListener(v -> {
            String name = nameInput.getText().toString().trim();
            if (name.isEmpty()) {
                Toast.makeText(this, "先写个名字", Toast.LENGTH_SHORT).show();
                return;
            }
            if (mask[0] == 0) {
                Toast.makeText(this, "至少选一天", Toast.LENGTH_SHORT).show();
                return;
            }
            habit.frequencyMask = mask[0];
            habit.title = name;
            Habits.update(this, habit);
            Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
            setResult(RESULT_OK);
            finish();
        });
        buttons.addView(save);
    }

    private void updateDot(TextView dot, boolean selected) {
        if (selected) {
            dot.setTextColor(Color.WHITE);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(UiPalette.ACCENT);
            bg.setCornerRadius(dp(20));
            dot.setBackground(bg);
        } else {
            dot.setTextColor(UiPalette.MUTED);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(Color.WHITE);
            bg.setStroke(dp(1), UiPalette.BORDER);
            bg.setCornerRadius(dp(20));
            dot.setBackground(bg);
        }
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    static final String EXTRA_HABIT_ID = "habit_id";
}
