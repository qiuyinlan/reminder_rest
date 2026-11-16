package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 「现在」页：先定下此刻要做的一件，其他念头一律丢进「刚想到的」。
 *
 * 设计的重点不是清单，而是**只留一条主线**：
 * 顶部永远是当前主任务（大字 + 已专注时长），
 * 想跑偏的念头只能占一行位置，排到下面去，做完再回来挑。
 */
public final class FocusActivity extends Activity {
    /** 候选任务最多展示几行，剩下的回任务页看。 */
    private static final int MAX_PICKER_ROWS = 8;

    private LinearLayout headerHost;
    private LinearLayout container;
    private TextView headerMeta;
    private TextView currentElapsed;
    private EditText ideaInput;
    private EditText customInput;
    private EditText subInput;

    private boolean pickerOpen;
    private String doneBanner = "";
    private String ideaDraft = "";
    private String customDraft = "";
    private String subDraft = "";

    private final Handler ticker = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshLiveTexts();
            ticker.postDelayed(this, 60 * 1000L);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());

        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(UiPalette.PAGE);
        setContentView(shell);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(UiPalette.page());
        shell.addView(root, new FrameLayout.LayoutParams(-1, -1));

        headerHost = new LinearLayout(this);
        headerHost.setOrientation(LinearLayout.VERTICAL);
        root.addView(headerHost, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(8), dp(16), dp(24));
        scroll.addView(container, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    @Override
    protected void onResume() {
        super.onResume();
        pickerOpen = !FocusStore.hasCurrent(this);
        render();
        ticker.removeCallbacks(tick);
        ticker.postDelayed(tick, 60 * 1000L);
    }

    @Override
    protected void onPause() {
        ticker.removeCallbacks(tick);
        super.onPause();
    }

    // ------------------------------------------------------------ 页面骨架

    private void render() {
        captureDrafts();
        if (headerHost != null) {
            headerHost.removeAllViews();
            headerHost.addView(buildHeader());
        }
        if (container == null) return;
        container.removeAllViews();

        if (!doneBanner.isEmpty()) container.addView(buildDoneBanner());

        boolean hasCurrent = FocusStore.hasCurrent(this);
        if (hasCurrent) {
            container.addView(buildCurrentCard());
            if (pickerOpen) container.addView(buildPicker());
        } else {
            container.addView(buildEmptyCard());
            container.addView(buildPicker());
        }
        container.addView(buildIdeaSection());
        container.addView(buildFooter());
        refreshLiveTexts();
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setPadding(dp(20), dp(14), dp(20), dp(16));

        GradientDrawable headBg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                UiPalette.headGradient());
        float radius = dp(24);
        headBg.setCornerRadii(new float[]{0, 0, 0, 0, radius, radius, radius, radius});
        header.setBackground(headBg);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹ 返回", 14, UiPalette.MUTED, true);
        back.setPadding(dp(2), dp(6), dp(10), dp(6));
        back.setOnClickListener(v -> finish());
        row.addView(back);

        row.addView(text("现在", 24, UiPalette.INK, true),
                new LinearLayout.LayoutParams(0, -2, 1f));

        TextView tasks = text("任务 ›", 12, UiPalette.MUTED, true);
        tasks.setPadding(dp(8), dp(6), dp(2), dp(6));
        tasks.setOnClickListener(v -> {
            startActivity(new Intent(this, TaskActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            overridePendingTransition(0, 0);
            finish();
        });
        row.addView(tasks);
        header.addView(row);

        headerMeta = text("", 12, UiPalette.MUTED, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(4);
        header.addView(headerMeta, metaParams);
        return header;
    }

    /** 每 60 秒只刷这两行文字，不重建整页（否则输入框里的字会丢）。 */
    private void refreshLiveTexts() {
        if (headerMeta != null) headerMeta.setText(metaText());
        if (currentElapsed != null) {
            if (FocusStore.hasCurrent(this)) {
                currentElapsed.setText("已 " + elapsedText(FocusStore.currentStartedAt(this)));
            } else {
                currentElapsed.setText("");
            }
        }
    }

    private String metaText() {
        java.text.SimpleDateFormat format =
                new java.text.SimpleDateFormat("M月d日 EEEE", Locale.CHINA);
        String today = format.format(new java.util.Date());
        int[] stats = todayFocus();
        if (stats[0] <= 0) return today + " · 今天还没专注";
        return today + " · 专注 " + stats[0] + " 段 " + stats[1] + " 分钟";
    }

    /** 今天的专注段数与分钟数：只数本页「做完了」写下的记录。 */
    private int[] todayFocus() {
        long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
        int segments = 0;
        long minutes = 0;
        for (ReminderRecord record : ReminderRecord.all(this)) {
            if (!record.isModule(ReminderSettings.MODULE_TASK)) continue;
            if (!ReminderRecord.KIND_DONE.equals(record.kind)) continue;
            if (record.reason == null || !record.reason.startsWith("专注")) continue;
            if (record.endedAt < dayStart) continue;
            segments++;
            long delta = record.endedAt - record.startedAt;
            if (delta > 0) minutes += Math.max(1, delta / 60000);
        }
        return new int[]{segments, (int) minutes};
    }

    // ---------------------------------------------------------- 当前主任务

    private View buildCurrentCard() {
        final String title = FocusStore.currentTitle(this);
        final int ruleId = FocusStore.currentRuleId(this);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                UiPalette.heroGradient());
        bg.setCornerRadius(dp(20));
        bg.setStroke(dp(1), UiPalette.BORDER);
        card.setBackground(bg);
        card.setPadding(dp(18), dp(16), dp(18), dp(14));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(14);
        card.setLayoutParams(cardParams);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        View dot = dotView(UiPalette.ACCENT);
        top.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView label = text("正在做", 12, UiPalette.ACCENT, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.leftMargin = dp(8);
        top.addView(label, labelParams);
        currentElapsed = text("已 " + elapsedText(FocusStore.currentStartedAt(this)),
                12, UiPalette.MUTED, true);
        top.addView(currentElapsed);
        card.addView(top);

        TextView name = text(title, 22, UiPalette.INK, true);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
        nameParams.topMargin = dp(10);
        card.addView(name, nameParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.topMargin = dp(14);
        card.addView(actions, actionsParams);

        TextView done = pill("做完了", UiPalette.ACCENT, Color.WHITE);
        done.setOnClickListener(v -> completeCurrent());
        actions.addView(done, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView swap = pill("换一个", UiPalette.PALE, UiPalette.ACCENT);
        swap.setOnClickListener(v -> {
            pickerOpen = true;
            doneBanner = "";
            render();
        });
        LinearLayout.LayoutParams swapParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        swapParams.leftMargin = dp(8);
        actions.addView(swap, swapParams);

        TextView more = text("⋯", 18, UiPalette.ACCENT, true);
        more.setGravity(Gravity.CENTER);
        more.setBackground(FormKit.round(this, UiPalette.PALE, 12));
        more.setContentDescription("更多");
        more.setOnClickListener(v -> showCurrentMenu());
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        moreParams.leftMargin = dp(8);
        actions.addView(more, moreParams);

        if (ruleId >= 0) {
            card.addView(buildSubtaskBlock(ruleId));
        }
        return card;
    }

    private View buildEmptyCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(FormKit.round(this, Color.WHITE, 18));
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(14);
        card.setLayoutParams(cardParams);

        card.addView(text("现在做什么？", 20, UiPalette.INK, true));
        TextView hint = text("只挑一件。想到别的，先丢到最下面的「刚想到的」。", 13, UiPalette.MUTED, false);
        hint.setPadding(0, dp(6), 0, 0);
        card.addView(hint);
        return card;
    }

    /** 主任务下的步骤：直接在这里勾、也能现场加一条。 */
    private View buildSubtaskBlock(final int ruleId) {
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams blockParams = new LinearLayout.LayoutParams(-1, -2);
        blockParams.topMargin = dp(14);
        block.setLayoutParams(blockParams);

        long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
        List<ReminderSettings.Rule> subs = Subtasks.of(this, ruleId);
        int doneCount = 0;
        for (ReminderSettings.Rule sub : subs) {
            if (Subtasks.doneToday(this, ruleId, sub.title, dayStart)) doneCount++;
        }

        if (!subs.isEmpty()) {
            LinearLayout head = new LinearLayout(this);
            head.setGravity(Gravity.CENTER_VERTICAL);
            head.addView(text("步骤", 12, UiPalette.MUTED, true),
                    new LinearLayout.LayoutParams(0, -2, 1f));
            head.addView(text(doneCount + " / " + subs.size(), 12,
                    doneCount >= subs.size() ? UiPalette.ACCENT : UiPalette.MUTED, true));
            LinearLayout.LayoutParams headParams = new LinearLayout.LayoutParams(-1, -2);
            headParams.bottomMargin = dp(4);
            block.addView(head, headParams);

            for (ReminderSettings.Rule sub : subs) {
                block.addView(buildSubtaskRow(ruleId, sub, dayStart));
            }
        }

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        subInput = FormKit.input(this, InputType.TYPE_CLASS_TEXT);
        subInput.setSingleLine(true);
        subInput.setHint("再拆一步（可选）");
        subInput.setText(subDraft);
        inputRow.addView(subInput, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView add = text("＋", 18, UiPalette.ACCENT, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(FormKit.round(this, Color.WHITE, 10));
        add.setOnClickListener(v -> addSubtask(ruleId));
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        addParams.leftMargin = dp(8);
        inputRow.addView(add, addParams);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-1, -2);
        inputParams.topMargin = dp(8);
        block.addView(inputRow, inputParams);
        return block;
    }

    private View buildSubtaskRow(final int ruleId, final ReminderSettings.Rule sub, final long dayStart) {
        final boolean done = Subtasks.doneToday(this, ruleId, sub.title, dayStart);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(2), dp(8), dp(2), dp(8));

        TextView box = text(done ? "✓" : "", 11, done ? Color.WHITE : UiPalette.DISABLED, true);
        box.setGravity(Gravity.CENTER);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(5));
        shape.setColor(done ? UiPalette.ACCENT : Color.TRANSPARENT);
        if (!done) shape.setStroke(dp(1), UiPalette.DISABLED);
        box.setBackground(shape);
        row.addView(box, new LinearLayout.LayoutParams(dp(18), dp(18)));

        TextView name = text(sub.title, 14, done ? UiPalette.MUTED : UiPalette.INK, false);
        name.setMaxLines(2);
        if (done) name.setPaintFlags(name.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = dp(10);
        row.addView(name, nameParams);

        row.setOnClickListener(v -> {
            Subtasks.toggle(this, ruleId, sub.title, done, dayStart);
            render();
        });
        row.setOnLongClickListener(v -> {
            confirmDeleteSubtask(sub);
            return true;
        });
        return row;
    }

    private void addSubtask(int ruleId) {
        String title = subInput == null ? "" : subInput.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "先写这一步做什么", Toast.LENGTH_SHORT).show();
            return;
        }
        List<ReminderSettings.Rule> existing = Subtasks.of(this, ruleId);
        int step = existing.isEmpty() ? 0 : (existing.get(existing.size() - 1).step + 1);
        Subtasks.add(this, ruleId, title, step);
        subDraft = "";
        if (subInput != null) subInput.setText("");
        render();
    }

    private void confirmDeleteSubtask(final ReminderSettings.Rule sub) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("删掉这一步？")
                .setMessage(sub.title)
                .setNegativeButton("留着", null)
                .setPositiveButton("删掉", (d, w) -> {
                    Subtasks.delete(this, sub);
                    render();
                })
                .create();
        FormKit.showStyled(this, dialog);
    }

    private void showCurrentMenu() {
        FormKit.options(this, null,
                new String[]{"放回「刚想到的」", "先放下，不记录"}, -1, which -> {
                    String title = FocusStore.currentTitle(this);
                    if (which == 0 && !title.isEmpty()) FocusStore.addIdea(this, title);
                    FocusStore.clearCurrent(this);
                    doneBanner = "";
                    pickerOpen = true;
                    render();
                });
    }

    /** 完成主任务：写一条带真实时长的记录，顺带把步骤全勾上。 */
    private void completeCurrent() {
        final String title = FocusStore.currentTitle(this);
        if (title.isEmpty()) return;
        final int ruleId = FocusStore.currentRuleId(this);
        final long startedAt = FocusStore.currentStartedAt(this);
        final long now = System.currentTimeMillis();

        ReminderRecord.finish(this, UUID.randomUUID().toString(), title,
                ReminderRecord.KIND_DONE, startedAt > 0 ? startedAt : now, now,
                "专注完成", ReminderSettings.MODULE_TASK);

        if (ruleId >= 0) {
            ReminderSettings.Rule rule = ReminderSettings.ruleById(this, ruleId);
            if (rule != null) {
                long dayStart = ReminderRecord.dayStart(now);
                for (ReminderSettings.Rule sub : Subtasks.of(this, ruleId)) {
                    if (!Subtasks.doneToday(this, ruleId, sub.title, dayStart)) {
                        Subtasks.toggle(this, ruleId, sub.title, false, dayStart);
                    }
                }
                ReminderSettings.markCompleted(this, ruleId, Math.max(1, rule.dailyTarget));
            }
        }

        long minutes = startedAt > 0 ? Math.max(1, (now - startedAt) / 60000) : 0;
        doneBanner = minutes > 0
                ? "「" + title + "」做完，用了 " + minutes + " 分钟"
                : "「" + title + "」做完了";
        FocusStore.clearCurrent(this);
        pickerOpen = true;
        render();
    }

    private View buildDoneBanner() {
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        banner.setBackground(FormKit.round(this, UiPalette.PALE, 14));
        banner.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(12);
        banner.setLayoutParams(params);

        banner.addView(text("✓ " + doneBanner, 13, UiPalette.ACCENT, true));
        TextView next = text("挑下一件吧，或者先歇会儿。", 12, UiPalette.MUTED, false);
        next.setPadding(0, dp(4), 0, 0);
        banner.addView(next);
        banner.setOnClickListener(v -> {
            doneBanner = "";
            render();
        });
        return banner;
    }

    // -------------------------------------------------------------- 挑一件

    private View buildPicker() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(FormKit.round(this, Color.WHITE, 18));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(14);
        card.setLayoutParams(cardParams);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text(FocusStore.hasCurrent(this) ? "换一件" : "从今天的任务挑一件",
                        14, UiPalette.INK, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        if (FocusStore.hasCurrent(this)) {
            TextView collapse = text("收起", 12, UiPalette.MUTED, true);
            collapse.setPadding(dp(10), dp(4), dp(2), dp(4));
            collapse.setOnClickListener(v -> {
                pickerOpen = false;
                render();
            });
            head.addView(collapse);
        }
        card.addView(head);

        long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
        List<Integer> ideaIds = FocusStore.ideaRuleIds(this);
        List<ReminderSettings.Rule> pending = new ArrayList<>();
        for (ReminderSettings.Rule task : Tasks.today(this)) {
            if (ideaIds.contains(task.id)) continue;
            if (!ReminderRecord.completedOn(this, ReminderSettings.MODULE_TASK, task.title, dayStart)) {
                pending.add(task);
            }
        }

        if (pending.isEmpty()) {
            TextView empty = text("今天清单是空的，直接在下面写一件", 13, UiPalette.MUTED, false);
            empty.setPadding(dp(2), dp(12), dp(2), dp(4));
            card.addView(empty);
        } else {
            int shown = Math.min(pending.size(), MAX_PICKER_ROWS);
            for (int i = 0; i < shown; i++) {
                if (i > 0) card.addView(rowDivider());
                card.addView(buildPickerRow(pending.get(i)));
            }
            if (pending.size() > shown) {
                TextView rest = text("还有 " + (pending.size() - shown) + " 件在任务页", 12,
                        UiPalette.MUTED, false);
                rest.setPadding(dp(2), dp(8), dp(2), 0);
                card.addView(rest);
            }
        }

        LinearLayout customRow = new LinearLayout(this);
        customRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams customParams = new LinearLayout.LayoutParams(-1, -2);
        customParams.topMargin = dp(12);
        customRow.setLayoutParams(customParams);

        customInput = FormKit.input(this, InputType.TYPE_CLASS_TEXT);
        customInput.setSingleLine(true);
        customInput.setHint("或者，自己写一件");
        customInput.setText(customDraft);
        customRow.addView(customInput, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView start = pill("开始", UiPalette.ACCENT, Color.WHITE);
        start.setOnClickListener(v -> startCustom());
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(dp(70), dp(48));
        startParams.leftMargin = dp(8);
        customRow.addView(start, startParams);
        card.addView(customRow);
        return card;
    }

    private View buildPickerRow(final ReminderSettings.Rule task) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(46));
        row.setPadding(dp(2), dp(10), dp(2), dp(10));

        row.addView(dotView(UiPalette.SOFT), new LinearLayout.LayoutParams(dp(6), dp(6)));

        TextView name = text(task.title.isEmpty() ? "未命名任务" : task.title, 15,
                UiPalette.INK, false);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = dp(10);
        row.addView(name, nameParams);

        TextView act = text("开始", 12, UiPalette.ACCENT, true);
        act.setPadding(dp(8), dp(4), dp(2), dp(4));
        row.addView(act);

        row.setOnClickListener(v -> {
            FocusStore.setCurrent(this, task.title, task.id);
            pickerOpen = false;
            doneBanner = "";
            render();
        });
        return row;
    }

    private void startCustom() {
        String title = customInput == null ? "" : customInput.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "先写一件要做的事", Toast.LENGTH_SHORT).show();
            return;
        }
        ReminderSettings.Rule rule = FocusStore.createTaskRule(this, title);
        customDraft = "";
        if (customInput != null) customInput.setText("");
        FocusStore.setCurrent(this, title, rule != null ? rule.id : -1);
        pickerOpen = false;
        doneBanner = "";
        render();
    }

    // ------------------------------------------------------------ 刚想到的

    private View buildIdeaSection() {
        LinearLayout section = new LinearLayout(this);
        section.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.topMargin = dp(2);
        section.setLayoutParams(sectionParams);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("刚想到的", 14, UiPalette.INK, true));

        final List<FocusStore.Idea> ideas = FocusStore.ideas(this);
        if (!ideas.isEmpty()) {
            TextView count = text(String.valueOf(ideas.size()), 11, UiPalette.ACCENT, true);
            count.setGravity(Gravity.CENTER);
            count.setBackground(FormKit.round(this, UiPalette.PALE, 9));
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(dp(20), dp(20));
            countParams.leftMargin = dp(8);
            head.addView(count, countParams);
        }
        section.addView(head);

        TextView hint = text("手头这件做完再说。先记下来，就不会丢。", 12, UiPalette.MUTED, false);
        hint.setPadding(0, dp(4), 0, dp(10));
        section.addView(hint);

        LinearLayout inputRow = new LinearLayout(this);
        inputRow.setGravity(Gravity.CENTER_VERTICAL);
        ideaInput = FormKit.input(this, InputType.TYPE_CLASS_TEXT);
        ideaInput.setSingleLine(true);
        ideaInput.setHint("突然想做的事…");
        ideaInput.setText(ideaDraft);
        inputRow.addView(ideaInput, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView add = pill("记下", UiPalette.ACCENT, Color.WHITE);
        add.setOnClickListener(v -> addIdea());
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(70), dp(48));
        addParams.leftMargin = dp(8);
        inputRow.addView(add, addParams);
        section.addView(inputRow);

        if (!ideas.isEmpty()) {
            section.addView(buildIdeaCard(ideas));
        }
        return section;
    }

    /** 想法清单和任务页一样：一张白卡装住，行间细线分隔。 */
    private View buildIdeaCard(List<FocusStore.Idea> ideas) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(16));
        bg.setColor(UiPalette.SURFACE);
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setElevation(dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(10);
        card.setLayoutParams(params);

        for (int i = 0; i < ideas.size(); i++) {
            if (i > 0) card.addView(rowDivider());
            card.addView(buildIdeaRow(ideas.get(i)));
        }
        return card;
    }

    private View buildIdeaRow(final FocusStore.Idea idea) {
        final int ruleId = idea.ruleId >= 0 ? idea.ruleId : FocusStore.ensureIdeaRule(this, idea);
        final long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
        final List<ReminderSettings.Rule> subs = ruleId >= 0
                ? Subtasks.of(this, ruleId) : new ArrayList<ReminderSettings.Rule>();

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(54));
        row.setPadding(dp(14), dp(11), dp(10), dp(11));

        TextView name = text(ideaTitle(idea), 15, UiPalette.INK, false);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        if (!subs.isEmpty()) {
            TextView steps = text(subs.size() + " 步", 11, UiPalette.MUTED, false);
            LinearLayout.LayoutParams stepsParams = new LinearLayout.LayoutParams(-2, -2);
            stepsParams.leftMargin = dp(8);
            row.addView(steps, stepsParams);
        }

        TextView take = text("设为现在", 12, UiPalette.ACCENT, true);
        take.setPadding(dp(10), dp(6), dp(4), dp(6));
        LinearLayout.LayoutParams takeParams = new LinearLayout.LayoutParams(-2, -2);
        takeParams.leftMargin = dp(6);
        row.addView(take, takeParams);

        TextView chevron = text("›", 17, UiPalette.DISABLED, true);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(-2, -2);
        chevronParams.leftMargin = dp(4);
        row.addView(chevron, chevronParams);

        row.setOnClickListener(v -> openIdeaDetail(ruleId, ideaTitle(idea)));
        take.setOnClickListener(v -> confirmTakeIdea(idea));
        row.setOnLongClickListener(v -> {
            confirmDeleteIdea(idea);
            return true;
        });
        wrapper.addView(row);

        if (!subs.isEmpty()) {
            wrapper.addView(buildIdeaSteps(ruleId, subs, dayStart));
        }
        return wrapper;
    }

    /** 想法下的步骤：横向滑动，点一下就勾。 */
    private View buildIdeaSteps(final int ruleId, List<ReminderSettings.Rule> subs, long dayStart) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);

        LinearLayout strip = new LinearLayout(this);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setPadding(dp(14), 0, dp(14), dp(12));

        for (final ReminderSettings.Rule sub : subs) {
            final boolean done = Subtasks.doneToday(this, ruleId, sub.title, dayStart);
            TextView chip = text((done ? "✓ " : "") + sub.title, 12,
                    done ? Color.WHITE : UiPalette.INK, false);
            chip.setMaxLines(1);
            chip.setEllipsize(TextUtils.TruncateAt.END);
            chip.setPadding(dp(10), dp(6), dp(10), dp(6));
            chip.setBackground(FormKit.round(this, done ? UiPalette.ACCENT : UiPalette.PALE, 10));
            LinearLayout.LayoutParams chipParams =
                    new LinearLayout.LayoutParams(-2, -2);
            chipParams.rightMargin = dp(6);
            strip.addView(chip, chipParams);
            chip.setOnClickListener(v -> {
                Subtasks.toggle(this, ruleId, sub.title, done, dayStart);
                render();
            });
        }
        scroll.addView(strip);
        return scroll;
    }

    private String ideaTitle(FocusStore.Idea idea) {
        if (idea.ruleId >= 0) {
            ReminderSettings.Rule rule = ReminderSettings.ruleById(this, idea.ruleId);
            if (rule != null && !rule.title.isEmpty()) return rule.title;
        }
        return idea.title;
    }

    private void openIdeaDetail(int ruleId, String title) {
        if (ruleId < 0) {
            Toast.makeText(this, "这条还没建好，稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivity(new Intent(this, SubtaskActivity.class)
                .putExtra(SubtaskActivity.EXTRA_PARENT_ID, ruleId)
                .putExtra(SubtaskActivity.EXTRA_PARENT_TITLE, title));
    }

    /** 切换当前主任务是个大事，别一点就跳走。 */
    private void confirmTakeIdea(final FocusStore.Idea idea) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("设为现在？")
                .setMessage("接下来只做「" + ideaTitle(idea) + "」，别的先排在后面。")
                .setNegativeButton("再等等", null)
                .setPositiveButton("开始", (d, w) -> takeIdea(idea))
                .create();
        FormKit.showStyled(this, dialog);
    }

    private void addIdea() {
        String title = ideaInput == null ? "" : ideaInput.getText().toString().trim();
        if (title.isEmpty()) {
            Toast.makeText(this, "写一句想做的事", Toast.LENGTH_SHORT).show();
            return;
        }
        ReminderSettings.Rule rule = FocusStore.createTaskRule(this, title);
        FocusStore.addIdea(this, title, rule != null ? rule.id : -1);
        ideaDraft = "";
        // 输入框本身也要清空，否则 render 时 captureDrafts 会把旧文字抓回来
        if (ideaInput != null) ideaInput.setText("");
        render();
    }

    /** 想法升为主任务：规则已经存在，直接搬过去。 */
    private void takeIdea(final FocusStore.Idea idea) {
        boolean had = FocusStore.hasCurrent(this);
        int ruleId = idea.ruleId >= 0 ? idea.ruleId : FocusStore.ensureIdeaRule(this, idea);
        FocusStore.setCurrent(this, ideaTitle(idea), ruleId);
        FocusStore.removeIdea(this, idea.id);
        doneBanner = "";
        pickerOpen = false;
        render();
        if (had) {
            Toast.makeText(this, "现在做：「" + idea.title + "」", Toast.LENGTH_SHORT).show();
        }
    }

    private void confirmDeleteIdea(final FocusStore.Idea idea) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("删掉这条？")
                .setMessage(idea.title)
                .setNegativeButton("留着", null)
                .setPositiveButton("删掉", (d, w) -> {
                    FocusStore.removeIdea(this, idea.id);
                    render();
                })
                .create();
        FormKit.showStyled(this, dialog);
    }

    private View buildFooter() {
        TextView footer = text("这里只留当前这一件 · 其他都在「刚想到的」", 11, UiPalette.MUTED, false);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, dp(8));
        return footer;
    }

    // ------------------------------------------------------------ 小工具

    private void captureDrafts() {
        if (ideaInput != null) ideaDraft = ideaInput.getText().toString();
        if (customInput != null) customDraft = customInput.getText().toString();
        if (subInput != null) subDraft = subInput.getText().toString();
    }

    private String elapsedText(long startedAt) {
        if (startedAt <= 0) return "刚开头";
        long minutes = (System.currentTimeMillis() - startedAt) / 60000;
        if (minutes < 1) return "刚开头";
        if (minutes < 60) return minutes + " 分钟";
        long hours = minutes / 60;
        long rest = minutes % 60;
        return rest == 0 ? hours + " 小时" : hours + " 小时 " + rest + " 分钟";
    }

    private String agoText(long createdAt) {
        if (createdAt <= 0) return "";
        long minutes = Math.max(0, (System.currentTimeMillis() - createdAt) / 60000);
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + " 分钟前";
        long hours = minutes / 60;
        if (hours < 24) return hours + " 小时前";
        return (hours / 24) + " 天前";
    }

    private View dotView(int color) {
        View view = new View(this);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(color);
        view.setBackground(shape);
        return view;
    }

    private View rowDivider() {
        View line = new View(this);
        line.setBackgroundColor(UiPalette.hairline());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.leftMargin = dp(18);
        line.setLayoutParams(params);
        return line;
    }

    private TextView pill(String label, int fill, int ink) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(15);
        view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        view.setTextColor(ink);
        view.setGravity(Gravity.CENTER);
        view.setMaxLines(1);
        view.setBackground(FormKit.round(this, fill, 12));
        return view;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        return FormKit.text(this, value, size, color, bold);
    }

    private int dp(float value) {
        return FormKit.dp(this, value);
    }
}
