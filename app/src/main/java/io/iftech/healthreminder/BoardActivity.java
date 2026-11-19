package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * 白板：今天随手记的清单。
 *
 * 跟任务页的区别是「只活今天」——不进任务表、不提醒、不统计，
 * 明天打开自动是空的。任务可以带子任务，点一下勾掉。
 */
public final class BoardActivity extends Activity {
    private LinearLayout header;
    private LinearLayout container;

    private final List<BoardStore.Item> items = new ArrayList<>();
    private String draft = "";
    /** 已完成区是否展开（默认收起，跟任务页一样）。 */
    private boolean completedOpen;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());
        migrateLegacy();
        // 白板直接复用任务页 UI，只把数据源换成 module=board
        startActivity(new Intent(this, TaskActivity.class)
                .putExtra("module", ReminderSettings.MODULE_BOARD));
        overridePendingTransition(0, 0);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    /** 把旧的本地白板数据搬进 module=board 的规则，只搬一次。 */
    private void migrateLegacy() {
        List<BoardStore.Item> legacy = BoardStore.load(this);
        if (legacy.isEmpty()) return;
        // 如果加载回来的是旧日期的数据（不应该发生，因为 BoardStore.load 会清空），直接丢弃
        String today = BoardStore.todayKey();
        if (!today.equals(prefs().getString(BoardStore.KEY_DAY, ""))) {
            BoardStore.clear(this);
            return;
        }
        for (BoardStore.Item item : legacy) {
            ArrayList<Integer> points = new ArrayList<>();
            points.add(9 * 3600);
            ReminderSettings.Rule rule = ReminderSettings.Rule.withSpecificTimes(
                            ReminderSettings.nextRuleId(this), item.title, 9 * 60, 9 * 60, 24 * 3600,
                            points, 1, true, false)
                    .withModule(ReminderSettings.MODULE_BOARD)
                    .withDeck(today, false);
            ReminderSettings.upsertRule(this, rule);

            if (item.done) {
                long now = System.currentTimeMillis();
                ReminderRecord.finish(this, java.util.UUID.randomUUID().toString(), item.title,
                        ReminderRecord.KIND_DONE, now, now, "白板导入",
                        ReminderSettings.MODULE_BOARD);
                ReminderSettings.markCompleted(this, rule.id, 1);
            }
            long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
            int step = 0;
            for (BoardStore.Sub sub : item.subs) {
                Subtasks.add(this, rule.id, sub.title, step++);
                if (sub.done) {
                    Subtasks.toggleQuiet(this, rule.id, sub.title, sub.done, dayStart);
                }
            }
        }
        BoardStore.clear(this);
    }

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences(BoardStore.PREFS, Context.MODE_PRIVATE);
    }

    // -------------------------------------------------------------- 页面骨架

    private void render() {
        if (header.getChildCount() > 0) header.removeAllViews();
        header.addView(buildHeader());

        container.removeAllViews();
        container.addView(hintText());
        container.addView(buildAddCard());

        if (items.isEmpty()) {
            container.addView(emptyCard());
        } else {
            List<BoardStore.Item> pending = new ArrayList<>();
            List<BoardStore.Item> finished = new ArrayList<>();
            for (BoardStore.Item item : items) {
                if (item.done) finished.add(item);
                else pending.add(item);
            }
            if (!pending.isEmpty()) container.addView(buildListCard(pending));
            if (!finished.isEmpty()) container.addView(buildDoneSection(finished));
        }
        container.addView(footerText());
    }

    private View buildHeader() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(14), dp(20), dp(16));

        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        UiPalette.headGradient());
        float radius = dp(24);
        bg.setCornerRadii(new float[]{0, 0, 0, 0, radius, radius, radius, radius});
        head.setBackground(bg);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹ 返回", 14, UiPalette.MUTED, true);
        back.setPadding(dp(2), dp(6), dp(10), dp(6));
        back.setOnClickListener(v -> finish());
        row.addView(back);

        row.addView(text("白板", 24, UiPalette.INK, true),
                new LinearLayout.LayoutParams(0, -2, 1f));

        if (!items.isEmpty()) {
            TextView wipe = text("清空", 12, UiPalette.MUTED, true);
            wipe.setPadding(dp(8), dp(6), dp(2), dp(6));
            wipe.setOnClickListener(v -> confirmWipe());
            row.addView(wipe);
        }
        head.addView(row);

        TextView meta = text(BoardStore.todayLabel() + " · 只留今天，明天自动清空",
                12, UiPalette.MUTED, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(4);
        head.addView(meta, metaParams);
        return head;
    }

    private View hintText() {
        TextView hint = text("写给自己看的，不用进任务清单。", 12, UiPalette.MUTED, false);
        hint.setPadding(dp(4), 0, dp(4), dp(10));
        return hint;
    }

    private View buildAddCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(FormKit.round(this, Color.WHITE, 18));
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);

        final EditText input = FormKit.input(this, InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setHint("今天想记点什么");
        input.setText(draft);
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                draft = s == null ? "" : s.toString();
            }
        });
        card.addView(input, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView add = text("加上", 13, Color.WHITE, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(FormKit.round(this, UiPalette.ACCENT, 14));
        add.setOnClickListener(v -> {
            String title = draft == null ? "" : draft.trim();
            if (title.isEmpty()) {
                Toast.makeText(this, "先写点什么", Toast.LENGTH_SHORT).show();
                return;
            }
            BoardStore.Item item = new BoardStore.Item();
            item.id = BoardStore.nextId();
            item.title = title;
            items.add(item);
            draft = "";
            input.setText("");
            persist();
            render();
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(64), dp(44));
        addParams.leftMargin = dp(8);
        card.addView(add, addParams);
        return card;
    }

    private View emptyCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(FormKit.round(this, Color.WHITE, 18));
        card.setPadding(dp(18), dp(22), dp(18), dp(22));
        card.addView(text("白板还是空的", 14, UiPalette.INK, true));
        TextView tip = text("在上面写一件，今天就只看这一张纸。", 12, UiPalette.MUTED, false);
        tip.setPadding(0, dp(6), 0, 0);
        card.addView(tip);
        return card;
    }

    private View buildListCard(List<BoardStore.Item> pending) {
        LinearLayout card = TaskRowKit.groupCard(this);
        for (int i = 0; i < pending.size(); i++) {
            if (i > 0) card.addView(TaskRowKit.divider(this));
            card.addView(buildItemRow(pending.get(i)));
        }
        return card;
    }

    /** 已完成：跟任务页一样的底部折叠区。 */
    private View buildDoneSection(List<BoardStore.Item> finished) {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);

        View head = TaskRowKit.sectionHeader(this, "已完成", String.valueOf(finished.size()),
                completedOpen ? "⌃" : "⌄", null);
        head.setOnClickListener(v -> {
            completedOpen = !completedOpen;
            render();
        });
        section.addView(head);

        if (completedOpen) {
            LinearLayout card = TaskRowKit.groupCard(this);
            for (int i = 0; i < finished.size(); i++) {
                if (i > 0) card.addView(TaskRowKit.divider(this));
                card.addView(buildItemRow(finished.get(i)));
            }
            section.addView(card);
        }
        return section;
    }

    private View buildItemRow(final BoardStore.Item item) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        // 跟任务页、「现在」页同一套行外观（TaskRowKit）
        LinearLayout row = TaskRowKit.mainRow(this);
        row.addView(TaskRowKit.circleCheck(this, item.done, v -> toggleItem(item)),
                TaskRowKit.checkParams(this));

        TextView title = TaskRowKit.title(this, item.title, item.done);
        row.addView(title, TaskRowKit.titleParams(this));

        TextView plus = text("＋", 15, UiPalette.ACCENT, true);
        plus.setGravity(Gravity.CENTER);
        plus.setPadding(dp(8), dp(2), dp(8), dp(2));
        plus.setContentDescription("加子任务");
        plus.setOnClickListener(v -> promptSubtask(item));
        row.addView(plus);

        TextView del = text("×", 16, UiPalette.MUTED, true);
        del.setGravity(Gravity.CENTER);
        del.setPadding(dp(8), dp(2), dp(2), dp(2));
        del.setContentDescription("删掉这一条");
        del.setOnClickListener(v -> {
            items.remove(item);
            persist();
            render();
        });
        row.addView(del);

        // 点行进子任务（跟任务页一样），勾完成走左边的圈
        row.setOnClickListener(v -> promptSubtask(item));
        box.addView(row);

        for (final BoardStore.Sub sub : item.subs) {
            box.addView(buildSubRow(item, sub));
        }
        return box;
    }

    private void toggleItem(BoardStore.Item item) {
        item.done = !item.done;
        if (item.done) {
            for (BoardStore.Sub sub : item.subs) sub.done = true;
        }
        persist();
        render();
    }

    private View buildSubRow(final BoardStore.Item item, final BoardStore.Sub sub) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.leftMargin = dp(36);
        rowParams.topMargin = dp(4);
        row.setLayoutParams(rowParams);

        final TextView mark = text(sub.done ? "✓" : "·", 12,
                sub.done ? UiPalette.ACCENT : UiPalette.MUTED, true);
        mark.setGravity(Gravity.CENTER);
        row.addView(mark, new LinearLayout.LayoutParams(dp(18), dp(18)));

        final TextView name = text(sub.title, 13,
                sub.done ? UiPalette.MUTED : UiPalette.INK, false);
        name.setMaxLines(2);
        if (sub.done) name.setPaintFlags(name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = dp(4);
        row.addView(name, nameParams);

        TextView del = text("×", 14, UiPalette.MUTED, true);
        del.setPadding(dp(8), 0, dp(2), 0);
        del.setOnClickListener(v -> {
            item.subs.remove(sub);
            persist();
            render();
        });
        row.addView(del);

        row.setOnClickListener(v -> {
            sub.done = !sub.done;
            persist();
            render();
        });
        return row;
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(UiPalette.hairline());
        line.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return line;
    }

    private View footerText() {
        TextView tip = text("这一页明天会自动变空，不用清理。", 11, UiPalette.MUTED, false);
        tip.setPadding(dp(4), dp(14), dp(4), 0);
        return tip;
    }

    // ------------------------------------------------------------------ 交互

    private void promptSubtask(final BoardStore.Item item) {
        final EditText input = FormKit.textInput(this, "子任务，比如「查资料」", "");
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(6), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("给「" + item.title + "」加个步骤")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("加上", (d, which) -> {
                    String text = input.getText() == null ? "" : input.getText().toString().trim();
                    if (text.isEmpty()) return;
                    BoardStore.Sub sub = new BoardStore.Sub();
                    sub.title = text;
                    item.subs.add(sub);
                    persist();
                    render();
                })
                .create();
        FormKit.showStyled(this, dialog);
    }

    private void confirmWipe() {
        FormKit.options(this, "清空白板？", new String[]{"清空今天的", "算了"}, 0, which -> {
            if (which != 0) return;
            items.clear();
            draft = "";
            BoardStore.clear(this);
            render();
        });
    }

    private void persist() {
        BoardStore.save(this, items);
    }

    // ------------------------------------------------------------------ 工具

    private TextView text(String value, int size, int color, boolean bold) {
        return FormKit.text(this, value, size, color, bold);
    }

    private int dp(float value) {
        return FormKit.dp(this, value);
    }
}
