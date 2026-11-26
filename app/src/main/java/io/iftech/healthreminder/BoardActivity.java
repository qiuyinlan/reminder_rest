package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 宝箱：收件箱。随手记任务或文字，一直攒，不按天清空。
 * 顶部可以随机抽一条出来做，不紧急的任务也可以从任务页移进来。
 */
public final class BoardActivity extends Activity {
    private LinearLayout header;
    private LinearLayout container;
    private EditText composerInput;
    private TextView taskChipView;
    private TextView noteChipView;

    private final List<BoardStore.Item> items = new ArrayList<>();
    private int composerType = BoardStore.TYPE_TASK;
    private boolean completedOpen;
    private long drawnId = -1L;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        composerType = BoardStore.lastType(this);
        migrateBoardRules();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(UiPalette.page());
        setContentView(root);

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(4), dp(16), dp(16));
        scroll.addView(container, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        root.addView(buildComposer(), new LinearLayout.LayoutParams(-1, -2));

        items.clear();
        items.addAll(BoardStore.load(this));
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (container == null) return;
        items.clear();
        items.addAll(BoardStore.load(this));
        render();
    }

    /** 旧版白板是 module=board 的规则，升级到宝箱时搬过来，避免这几天记的东西消失。 */
    private void migrateBoardRules() {
        List<ReminderSettings.Rule> board = ReminderSettings.moduleRules(this, ReminderSettings.MODULE_BOARD);
        if (board.isEmpty()) return;
        List<BoardStore.Item> saved = BoardStore.load(this);
        long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
        for (ReminderSettings.Rule rule : board) {
            BoardStore.Item item = new BoardStore.Item();
            item.id = BoardStore.nextId();
            item.type = BoardStore.TYPE_TASK;
            item.title = rule.title;
            item.done = ReminderRecord.completedOn(this, ReminderSettings.MODULE_BOARD,
                    rule.title, dayStart);
            item.createdAt = System.currentTimeMillis();
            for (ReminderSettings.Rule sub : Subtasks.of(this, rule.id)) {
                BoardStore.Sub entry = new BoardStore.Sub();
                entry.title = sub.title;
                entry.done = Subtasks.doneToday(this, rule.id, sub.title, dayStart);
                item.subs.add(entry);
            }
            saved.add(item);
            Subtasks.deleteForParent(this, rule.id);
            ReminderSettings.deleteRule(this, rule.id);
        }
        BoardStore.save(this, saved);
    }

    // ------------------------------------------------------------ 页面

    private void render() {
        header.removeAllViews();
        header.addView(buildHeader());
        container.removeAllViews();
        container.addView(buildDrawCard());
        container.addView(buildListArea());
        container.addView(footer());
    }

    private View buildHeader() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(14), dp(20), dp(12));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, UiPalette.headGradient());
        float radius = dp(24);
        bg.setCornerRadii(new float[]{0, 0, 0, 0, radius, radius, radius, radius});
        head.setBackground(bg);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("‹ 返回", 14, UiPalette.MUTED, true);
        back.setPadding(dp(2), dp(6), dp(12), dp(6));
        back.setOnClickListener(v -> finish());
        row.addView(back);
        row.addView(text("宝箱", 26, UiPalette.INK, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        if (!items.isEmpty()) {
            TextView wipe = text("清空", 12, UiPalette.MUTED, true);
            wipe.setPadding(dp(8), dp(6), dp(2), dp(6));
            wipe.setOnClickListener(v -> confirmClear());
            row.addView(wipe);
        }
        head.addView(row);

        TextView meta = text("不紧急的先丢进来，攒着慢慢处理 · 共 " + items.size() + " 条",
                12, UiPalette.MUTED, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(3);
        head.addView(meta, metaParams);
        return head;
    }

    // ------------------------------------------------------------ 抽一下

    private View buildDrawCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xffeaf8f1, 0xffddf1e7});
        bg.setCornerRadius(dp(18));
        bg.setStroke(dp(1), UiPalette.hairline());
        card.setBackground(bg);
        card.setElevation(dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        card.setLayoutParams(params);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text("抽一下", 14, UiPalette.INK, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView draw = text("抽一个", 12, Color.WHITE, true);
        draw.setGravity(Gravity.CENTER);
        draw.setPadding(dp(12), dp(6), dp(12), dp(6));
        draw.setBackground(FormKit.round(this, UiPalette.ACCENT, 12));
        draw.setOnClickListener(v -> drawOne());
        top.addView(draw);
        card.addView(top);

        BoardStore.Item drawn = drawnItem();
        if (drawn == null) {
            TextView hint = text(items.isEmpty()
                    ? "宝箱还是空的，先在下面丢一条进来"
                    : "无聊的时候点「抽一个」，随机拿一条出来做", 12, UiPalette.MUTED, false);
            hint.setPadding(0, dp(8), 0, 0);
            card.addView(hint);
            return card;
        }

        TextView type = text(drawn.type == BoardStore.TYPE_NOTE ? "随手记" : "任务",
                10, drawn.type == BoardStore.TYPE_NOTE ? UiPalette.MUTED : UiPalette.ACCENT, true);
        type.setPadding(0, dp(10), 0, dp(3));
        card.addView(type);

        TextView body = text(drawn.title, 15, UiPalette.INK, false);
        body.setPadding(0, 0, 0, dp(8));
        card.addView(body);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        if (drawn.type == BoardStore.TYPE_TASK) {
            TextView done = text(drawn.done ? "已处理" : "做了", 12,
                    drawn.done ? UiPalette.MUTED : Color.WHITE, true);
            done.setGravity(Gravity.CENTER);
            done.setPadding(dp(12), dp(6), dp(12), dp(6));
            done.setBackground(FormKit.round(this,
                    drawn.done ? UiPalette.PALE : UiPalette.ACCENT, 12));
            done.setOnClickListener(v -> {
                drawn.done = !drawn.done;
                persist();
                drawnId = -1L;
                render();
            });
            actions.addView(done);
            actions.addView(space(dp(6)));
        }
        TextView convert = text("转为正式任务", 12, UiPalette.ACCENT, true);
        convert.setPadding(dp(8), dp(6), dp(8), dp(6));
        convert.setOnClickListener(v -> convertToTask(drawn));
        actions.addView(convert);
        actions.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView close = text("收起", 12, UiPalette.MUTED, true);
        close.setPadding(dp(8), dp(6), dp(2), dp(6));
        close.setOnClickListener(v -> {
            drawnId = -1L;
            render();
        });
        actions.addView(close);
        card.addView(actions);
        return card;
    }

    private View space(int width) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(width, 1));
        return view;
    }

    private BoardStore.Item drawnItem() {
        for (BoardStore.Item item : items) {
            if (item.id == drawnId) return item;
        }
        return null;
    }

    private void drawOne() {
        List<BoardStore.Item> pool = new ArrayList<>();
        for (BoardStore.Item item : items) {
            if (item.type == BoardStore.TYPE_TASK && item.done) continue;
            pool.add(item);
        }
        if (pool.isEmpty()) {
            drawnId = -1L;
            render();
            Toast.makeText(this, "宝箱里没有可抽的了", Toast.LENGTH_SHORT).show();
            return;
        }
        BoardStore.Item picked;
        if (pool.size() > 1 && drawnId >= 0) {
            List<BoardStore.Item> filtered = new ArrayList<>();
            for (BoardStore.Item item : pool) {
                if (item.id != drawnId) filtered.add(item);
            }
            picked = filtered.get(new Random().nextInt(filtered.size()));
        } else {
            picked = pool.get(new Random().nextInt(pool.size()));
        }
        drawnId = picked.id;
        render();
    }

    // ------------------------------------------------------------ 列表

    private View buildListArea() {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);

        List<BoardStore.Item> pending = new ArrayList<>();
        List<BoardStore.Item> done = new ArrayList<>();
        for (int i = items.size() - 1; i >= 0; i--) {
            BoardStore.Item item = items.get(i);
            if (item.type == BoardStore.TYPE_TASK && item.done) done.add(item);
            else pending.add(item);
        }

        if (!pending.isEmpty()) {
            area.addView(sectionLabel("收件箱"));
            LinearLayout card = TaskRowKit.groupCard(this);
            for (int i = 0; i < pending.size(); i++) {
                if (i > 0) card.addView(TaskRowKit.divider(this));
                card.addView(buildItemRow(pending.get(i)));
            }
            area.addView(card);
        } else {
            area.addView(sectionLabel("收件箱"));
            TextView empty = text("还没有东西，下面随手记一条", 12, UiPalette.MUTED, false);
            empty.setPadding(dp(6), dp(4), dp(6), dp(12));
            area.addView(empty);
        }

        if (!done.isEmpty()) {
            View head = TaskRowKit.sectionHeader(this, "已完成", String.valueOf(done.size()),
                    completedOpen ? "⌃" : "⌄", null);
            head.setOnClickListener(v -> {
                completedOpen = !completedOpen;
                render();
            });
            area.addView(head);
            if (completedOpen) {
                LinearLayout card = TaskRowKit.groupCard(this);
                for (int i = 0; i < done.size(); i++) {
                    if (i > 0) card.addView(TaskRowKit.divider(this));
                    card.addView(buildItemRow(done.get(i)));
                }
                area.addView(card);
            }
        }
        return area;
    }

    private View sectionLabel(String label) {
        TextView view = text(label, 11, UiPalette.MUTED, true);
        view.setLetterSpacing(0.12f);
        view.setPadding(dp(6), dp(14), dp(6), dp(6));
        return view;
    }

    private View buildItemRow(final BoardStore.Item item) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(4), 0, dp(4));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(8), dp(8), dp(8));

        if (item.type == BoardStore.TYPE_TASK) {
            row.addView(TaskRowKit.circleCheck(this, item.done,
                            v -> toggleDone(item)),
                    new LinearLayout.LayoutParams(dp(22), dp(22)));
        } else {
            View dot = new View(this);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(UiPalette.DISABLED);
            dot.setBackground(dotBg);
            row.addView(dot, new LinearLayout.LayoutParams(dp(6), dp(6)));
        }

        TextView title = text(item.title, 15,
                item.done ? UiPalette.MUTED : UiPalette.INK, false);
        title.setMaxLines(Integer.MAX_VALUE);
        title.setEllipsize(null);
        if (item.done) title.setPaintFlags(title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = item.type == BoardStore.TYPE_TASK ? dp(10) : dp(8);
        row.addView(title, titleParams);

        TextView more = text("⋯", 18, UiPalette.MUTED, true);
        more.setGravity(Gravity.CENTER);
        more.setPadding(dp(8), 0, dp(2), 0);
        more.setOnClickListener(v -> showItemMenu(item));
        row.addView(more);
        box.addView(row);

        for (final BoardStore.Sub sub : item.subs) {
            LinearLayout subRow = new LinearLayout(this);
            subRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams subRowParams = new LinearLayout.LayoutParams(-1, -2);
            subRowParams.leftMargin = dp(40);
            subRowParams.rightMargin = dp(10);
            subRow.setLayoutParams(subRowParams);
            TextView mark = text(sub.done ? "✓" : "·", 12,
                    sub.done ? UiPalette.ACCENT : UiPalette.MUTED, true);
            subRow.addView(mark, new LinearLayout.LayoutParams(dp(16), dp(16)));
            TextView subTitle = text(sub.title, 13,
                    sub.done ? UiPalette.MUTED : UiPalette.INK, false);
            subTitle.setPaintFlags(sub.done
                    ? subTitle.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG
                    : subTitle.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(0, -2, 1f);
            subParams.leftMargin = dp(4);
            subRow.addView(subTitle, subParams);
            subRow.setOnClickListener(v -> {
                sub.done = !sub.done;
                persist();
                render();
            });
            box.addView(subRow);
        }
        return box;
    }

    private void toggleDone(BoardStore.Item item) {
        if (item.type == BoardStore.TYPE_NOTE) return;
        item.done = !item.done;
        if (item.done) {
            for (BoardStore.Sub sub : item.subs) sub.done = true;
            if (drawnId == item.id) drawnId = -1L;
        }
        persist();
        render();
    }

    private void showItemMenu(final BoardStore.Item item) {
        boolean task = item.type == BoardStore.TYPE_TASK;
        String[] labels = task
                ? new String[]{"编辑", "加子任务", "转为正式任务", "删除"}
                : new String[]{"编辑", "转为正式任务", "删除"};
        FormKit.options(this, null, labels, -1, which -> {
            if (task) {
                if (which == 0) editItem(item);
                else if (which == 1) addSubtask(item);
                else if (which == 2) convertToTask(item);
                else confirmDelete(item);
            } else {
                if (which == 0) editItem(item);
                else if (which == 1) convertToTask(item);
                else confirmDelete(item);
            }
        });
    }

    private void editItem(final BoardStore.Item item) {
        final EditText input = FormKit.textInput(this, "写点什么", item.title);
        input.setSingleLine(false);
        input.setMinLines(2);
        input.setMaxLines(6);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(6), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("编辑")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    if (value.isEmpty()) return;
                    item.title = value;
                    persist();
                    render();
                })
                .create();
        FormKit.showStyled(this, dialog);
    }

    private void addSubtask(final BoardStore.Item item) {
        final EditText input = FormKit.textInput(this, "子任务", "");
        LinearLayout box = new LinearLayout(this);
        box.setPadding(dp(20), dp(6), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("加个步骤")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("加上", (d, w) -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    if (value.isEmpty()) return;
                    BoardStore.Sub sub = new BoardStore.Sub();
                    sub.title = value;
                    item.subs.add(sub);
                    persist();
                    render();
                })
                .create();
        FormKit.showStyled(this, dialog);
    }

    private void confirmDelete(final BoardStore.Item item) {
        new AlertDialog.Builder(this)
                .setTitle("从宝箱删除")
                .setMessage("删除「" + shortTitle(item.title) + "」？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    items.remove(item);
                    if (drawnId == item.id) drawnId = -1L;
                    persist();
                    render();
                })
                .show();
    }

    private void convertToTask(final BoardStore.Item item) {
        String full = item.title == null ? "" : item.title.trim();
        if (full.isEmpty()) return;
        String title = full;
        int line = full.indexOf('\n');
        if (line > 0) title = full.substring(0, line).trim();
        if (title.length() > 40) title = title.substring(0, 40).trim();
        ArrayList<Integer> points = new ArrayList<>();
        points.add(9 * 3600);
        ReminderSettings.Rule rule = ReminderSettings.Rule.withSpecificTimes(
                        ReminderSettings.nextRuleId(this), title, 9 * 60, 9 * 60, 24 * 3600,
                        points, 1, true, false)
                .withModule(ReminderSettings.MODULE_TASK)
                .withReminderMessage(full);
        ReminderSettings.upsertRule(this, rule);
        int step = 0;
        for (BoardStore.Sub sub : item.subs) {
            Subtasks.add(this, rule.id, sub.title, step++);
        }
        items.remove(item);
        if (drawnId == item.id) drawnId = -1L;
        persist();
        render();
        Toast.makeText(this, "已转为任务，可在任务页设置提醒", Toast.LENGTH_SHORT).show();
    }

    private void confirmClear() {
        FormKit.options(this, "清空宝箱？", new String[]{"全部清空", "算了"}, 0, which -> {
            if (which != 0) return;
            items.clear();
            drawnId = -1L;
            BoardStore.clear(this);
            render();
        });
    }

    private void persist() {
        BoardStore.save(this, items);
    }

    // ------------------------------------------------------------ 输入区

    private View buildComposer() {
        LinearLayout area = new LinearLayout(this);
        area.setOrientation(LinearLayout.VERTICAL);
        area.setPadding(dp(12), dp(8), dp(12), dp(10));
        area.setBackground(UiPalette.page());

        LinearLayout types = new LinearLayout(this);
        types.setGravity(Gravity.CENTER_VERTICAL);
        taskChipView = typeChip("任务", BoardStore.TYPE_TASK);
        noteChipView = typeChip("随手记", BoardStore.TYPE_NOTE);
        types.addView(taskChipView);
        types.addView(noteChipView, new LinearLayout.LayoutParams(-2, -2));
        ((LinearLayout.LayoutParams) noteChipView.getLayoutParams()).leftMargin = dp(6);
        area.addView(types);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.BOTTOM);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(6);
        area.addView(row, rowParams);

        composerInput = FormKit.input(this, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        composerInput.setHint("随手写点什么，整段存成一条");
        composerInput.setTextSize(14);
        composerInput.setMinLines(1);
        composerInput.setMaxLines(5);
        composerInput.setSingleLine(false);
        composerInput.setPadding(dp(10), dp(8), dp(10), dp(8));
        composerInput.setBackground(UiPalette.inputBackground(this, Color.WHITE, 12));
        row.addView(composerInput, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView send = text("放进宝箱", 12, Color.WHITE, true);
        send.setGravity(Gravity.CENTER);
        send.setPadding(dp(10), dp(10), dp(10), dp(10));
        send.setBackground(FormKit.round(this, UiPalette.ACCENT, 12));
        send.setOnClickListener(v -> submitComposer());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(-2, -2);
        sendParams.leftMargin = dp(6);
        row.addView(send, sendParams);
        updateTypeChips(taskChipView, noteChipView);
        return area;
    }

    private TextView typeChip(String label, final int type) {
        final TextView chip = text(label, 12, UiPalette.MUTED, true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        chip.setOnClickListener(v -> {
            composerType = type;
            BoardStore.setLastType(this, type);
            updateTypeChips(taskChipView, noteChipView);
            if (composerInput != null) composerInput.requestFocus();
        });
        return chip;
    }

    private void updateTypeChips(TextView taskChip, TextView noteChip) {
        taskChip.setTextColor(composerType == BoardStore.TYPE_TASK ? Color.WHITE : UiPalette.MUTED);
        taskChip.setBackground(FormKit.round(this,
                composerType == BoardStore.TYPE_TASK ? UiPalette.ACCENT : UiPalette.PALE, 10));
        noteChip.setTextColor(composerType == BoardStore.TYPE_NOTE ? Color.WHITE : UiPalette.MUTED);
        noteChip.setBackground(FormKit.round(this,
                composerType == BoardStore.TYPE_NOTE ? UiPalette.ACCENT : UiPalette.PALE, 10));
    }

    private void submitComposer() {
        String value = composerInput == null || composerInput.getText() == null
                ? "" : composerInput.getText().toString().trim();
        if (value.isEmpty()) {
            Toast.makeText(this, "先写点什么", Toast.LENGTH_SHORT).show();
            return;
        }
        BoardStore.Item item = new BoardStore.Item();
        item.id = BoardStore.nextId();
        item.type = composerType;
        item.title = value;
        item.createdAt = System.currentTimeMillis();
        items.add(item);
        persist();
        composerInput.setText("");
        render();
        if (composerInput != null) composerInput.requestFocus();
    }

    // ------------------------------------------------------------ 工具

    private String shortTitle(String value) {
        if (value == null) return "";
        int line = value.indexOf('\n');
        String first = line >= 0 ? value.substring(0, line) : value;
        return first.length() > 18 ? first.substring(0, 18) + "…" : first;
    }

    private String timeText(long millis) {
        return new SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(new Date(millis));
    }

    private View footer() {
        TextView tip = text("宝箱会一直攒着，不按天清空。", 11, UiPalette.MUTED, false);
        tip.setPadding(dp(6), dp(12), dp(6), 0);
        return tip;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        return FormKit.text(this, value, size, color, bold);
    }

    private int dp(float value) {
        return FormKit.dp(this, value);
    }
}
