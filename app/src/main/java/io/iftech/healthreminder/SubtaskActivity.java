package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

/** 单个任务的子任务清单：在这里加子任务、勾完成，父任务页也会同步显示。 */
public final class SubtaskActivity extends Activity {
    static final String EXTRA_PARENT_ID = "parent_id";

    private int parentId = -1;
    private LinearLayout listContainer;
    private EditText input;
    private TextView parentTitle;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());
        parentId = getIntent().getIntExtra(EXTRA_PARENT_ID, -1);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiPalette.PAGE);
        setContentView(root);

        root.addView(buildHeader(), new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(dp(16), dp(4), dp(16), dp(12));
        scroll.addView(listContainer, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(buildAddBar(), new LinearLayout.LayoutParams(-1, -2));

        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(18), dp(20), dp(8));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = FormKit.text(this, "‹ 返回", 14, UiPalette.MUTED, true);
        back.setPadding(dp(2), dp(6), dp(12), dp(6));
        back.setOnClickListener(v -> finish());
        row.addView(back);

        parentTitle = FormKit.text(this, "", 22, UiPalette.INK, true);
        parentTitle.setOnClickListener(v -> editTitle());
        row.addView(parentTitle, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView more = FormKit.text(this, "⋯", 18, UiPalette.MUTED, true);
        more.setGravity(Gravity.CENTER);
        more.setPadding(dp(12), dp(2), dp(4), dp(2));
        more.setContentDescription("更多");
        more.setOnClickListener(v -> FormKit.options(this, null,
                new String[]{"改名字", "提醒设置"}, -1, which -> {
                    if (which == 0) editTitle();
                    else {
                        startActivity(new Intent(this, TaskActivity.class)
                                .putExtra(TaskActivity.EXTRA_EDIT_RULE_ID, parentId));
                        finish();
                    }
                }));
        row.addView(more);
        header.addView(row);

        return header;
    }

    private View buildAddBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(16), dp(8), dp(16), dp(14));

        input = FormKit.input(this, InputType.TYPE_CLASS_TEXT);
        input.setTextSize(15);
        input.setSingleLine(true);
        bar.addView(input, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView add = FormKit.text(this, "添加", 15, Color.WHITE, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(FormKit.round(this, UiPalette.ACCENT, 12));
        add.setOnClickListener(v -> addSubtask());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(72), dp(48));
        addParams.leftMargin = dp(8);
        bar.addView(add, addParams);
        return bar;
    }

    /** 点标题改名字：任务名改了，完成记录按新名字算。 */
    private void editTitle() {
        ReminderSettings.Rule parent = ReminderSettings.ruleById(this, parentId);
        if (parent == null) return;
        final EditText input = FormKit.textInput(this, "任务名字", parent.title);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(10), dp(18), dp(0));
        form.addView(input, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("改名字")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = input.getText().toString().trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "名字不能是空的", Toast.LENGTH_SHORT).show();
                return;
            }
            ReminderSettings.upsertRule(this, parent.withTitle(title));
            dialog.dismiss();
            render();
        }));
        FormKit.showStyled(this, dialog);
    }

    private void addSubtask() {
        String title = input.getText().toString().trim();
        if (title.isEmpty()) return;
        List<ReminderSettings.Rule> existing = Subtasks.of(this, parentId);
        int step = existing.isEmpty() ? 0 : (existing.get(existing.size() - 1).step + 1);
        Subtasks.add(this, parentId, title, step);
        input.setText("");
        render();
    }

    private void render() {
        ReminderSettings.Rule parent = ReminderSettings.ruleById(this, parentId);
        parentTitle.setText(parent == null || parent.title.isEmpty() ? "任务" : parent.title);

        long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
        List<ReminderSettings.Rule> items = Subtasks.of(this, parentId);
        int done = Subtasks.count(this, parentId, dayStart);

        listContainer.removeAllViews();
        if (items.isEmpty()) {
            return;
        }
        for (ReminderSettings.Rule item : items) {
            listContainer.addView(subtaskRow(item, dayStart));
        }
    }

    private View subtaskRow(ReminderSettings.Rule item, long dayStart) {
        boolean done = Subtasks.doneToday(this, parentId, item.title, dayStart);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(FormKit.round(this, Color.WHITE, 14));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        card.setLayoutParams(params);

        TextView box = FormKit.text(this, done ? "✓" : "", 14, Color.WHITE, true);
        box.setGravity(Gravity.CENTER);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(7));
        shape.setColor(done ? UiPalette.ACCENT : UiPalette.SOFT);
        if (!done) shape.setStroke(dp(1), UiPalette.BORDER);
        box.setBackground(shape);
        card.addView(box, new LinearLayout.LayoutParams(dp(24), dp(24)));

        TextView name = FormKit.text(this, item.title, 15, done ? UiPalette.MUTED : UiPalette.INK, false);
        if (done) name.setPaintFlags(name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = dp(12);
        card.addView(name, nameParams);

        card.setOnClickListener(v -> {
            Subtasks.toggle(this, parentId, item.title, done, dayStart);
            render();
        });
        card.setOnLongClickListener(v -> {
            confirmDelete(item);
            return true;
        });
        return card;
    }

    private void confirmDelete(ReminderSettings.Rule item) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("删除子任务")
                .setMessage("删掉「" + item.title + "」？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    Subtasks.delete(this, item);
                    render();
                })
                .create();
        FormKit.showStyled(this, dialog);
    }

    private int dp(float value) {
        return FormKit.dp(this, value);
    }
}
