package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
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
    private EditText ideaInput;
    private EditText customInput;
    private EditText subInput;

    // 三个任务槽位的 UI 引用
    private TextView mainElapsed;
    private TextView sub1Elapsed;
    private TextView sub2Elapsed;
    private EditText mainTargetInput;
    private EditText sub1TargetInput;
    private EditText sub2TargetInput;

    private boolean pickerOpen;
    /** 刚从系统设置里授权回来，落地就自动把悬浮条打开。 */
    private boolean pendingBubble;
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
        if (pendingBubble) {
            pendingBubble = false;
            if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) {
                FocusBubbleService.setOn(this, true);
                FocusBubbleService.start(this);
            }
        }
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

        // 渲染 3 个并行任务卡片：主线 + 支线 1 + 支线 2
        FocusStore.TaskSlot main = FocusStore.getMain(this);
        FocusStore.TaskSlot sub1 = FocusStore.getSub1(this);
        FocusStore.TaskSlot sub2 = FocusStore.getSub2(this);

        container.addView(buildTaskCard(main, "主线任务",
                () -> completeSlot(FocusStore.KEY_MAIN),
                () -> clearSlot(FocusStore.KEY_MAIN)));
        container.addView(buildTaskCard(sub1, "支线任务 1",
                () -> completeSlot(FocusStore.KEY_SUB1),
                () -> clearSlot(FocusStore.KEY_SUB1)));
        container.addView(buildTaskCard(sub2, "支线任务 2",
                () -> completeSlot(FocusStore.KEY_SUB2),
                () -> clearSlot(FocusStore.KEY_SUB2)));

        // 如果三个槽位都为空，显示选择器
        if (main.isEmpty() && sub1.isEmpty() && sub2.isEmpty()) {
            container.addView(buildPicker());
        }
        container.addView(buildIdeaSection());
        container.addView(buildBubbleCard());
        container.addView(buildFooter());
        refreshLiveTexts();
        // 任务槽位变了就让悬浮条马上跟着改，不用等它自己轮询
        FocusBubbleService.refreshNow(this);
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
        // 刷新三个槽位的倒计时显示
        if (mainElapsed != null) {
            FocusStore.TaskSlot main = FocusStore.getMain(this);
            mainElapsed.setText(main.isRunning() ? "已 " + elapsedText(main.startedAt) : "准备开始");
        }
        if (sub1Elapsed != null) {
            FocusStore.TaskSlot sub1 = FocusStore.getSub1(this);
            sub1Elapsed.setText(sub1.isRunning() ? "已 " + elapsedText(sub1.startedAt) : "准备开始");
        }
        if (sub2Elapsed != null) {
            FocusStore.TaskSlot sub2 = FocusStore.getSub2(this);
            sub2Elapsed.setText(sub2.isRunning() ? "已 " + elapsedText(sub2.startedAt) : "准备开始");
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

    // ---------------------------------------------------------- 任务卡片（3 个并行槽位）

    /**
     * 通用任务卡片构建器
     * @param slot 任务槽位数据
     * @param label 槽位标签（主线/支线 1/支线 2）
     * @param onComplete 完成按钮回调
     * @param onClear 清空按钮回调
     */
    private View buildTaskCard(final FocusStore.TaskSlot slot, final String label,
                               final Runnable onComplete, final Runnable onClear) {
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
        int dotColor = slot.isEmpty() ? UiPalette.MUTED :
                       slot.isRunning() ? UiPalette.ACCENT : UiPalette.SOFT;
        View dot = dotView(dotColor);
        top.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8)));
        TextView labelView = text(label, 12, dotColor, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.leftMargin = dp(8);
        top.addView(labelView, labelParams);

        // 根据槽位选择对应的 elapsed TextView
        TextView elapsedView;
        if (label.equals("主线任务")) {
            elapsedView = mainElapsed = text(slot.isRunning() ? "已 " + elapsedText(slot.startedAt) : "准备开始",
                    12, UiPalette.MUTED, true);
        } else if (label.equals("支线任务 1")) {
            elapsedView = sub1Elapsed = text(slot.isRunning() ? "已 " + elapsedText(slot.startedAt) : "准备开始",
                    12, UiPalette.MUTED, true);
        } else {
            elapsedView = sub2Elapsed = text(slot.isRunning() ? "已 " + elapsedText(slot.startedAt) : "准备开始",
                    12, UiPalette.MUTED, true);
        }
        top.addView(elapsedView);
        card.addView(top);

        // 任务标题或输入框
        if (slot.isEmpty()) {
            // 空槽位显示输入框
            LinearLayout inputRow = new LinearLayout(this);
            inputRow.setGravity(Gravity.CENTER_VERTICAL);
            EditText titleInput = FormKit.input(this, InputType.TYPE_CLASS_TEXT);
            titleInput.setSingleLine(true);
            titleInput.setHint("输入任务名称");
            inputRow.addView(titleInput, new LinearLayout.LayoutParams(0, dp(48), 1f));

            TextView setBtn = pill("设定", UiPalette.ACCENT, Color.WHITE);
            setBtn.setOnClickListener(v -> {
                String title = titleInput.getText().toString().trim();
                if (title.isEmpty()) {
                    Toast.makeText(this, "先写任务名称", Toast.LENGTH_SHORT).show();
                    return;
                }
                // 创建任务规则并设置到对应槽位
                ReminderSettings.Rule rule = FocusStore.createTaskRule(this, title);
                int ruleId = rule != null ? rule.id : -1;
                int targetSec = 25 * 60; // 默认 25 分钟
                if (label.equals("主线任务")) {
                    FocusStore.setMain(this, title, ruleId, targetSec);
                } else if (label.equals("支线任务 1")) {
                    FocusStore.setSub1(this, title, ruleId, targetSec);
                } else {
                    FocusStore.setSub2(this, title, ruleId, targetSec);
                }
                render();
            });
            LinearLayout.LayoutParams setParams = new LinearLayout.LayoutParams(dp(70), dp(48));
            setParams.leftMargin = dp(8);
            inputRow.addView(setBtn, setParams);
            card.addView(inputRow, new LinearLayout.LayoutParams(-1, -2, 1f));
        } else {
            // 有任务显示标题
            TextView name = text(slot.title, 22, UiPalette.INK, true);
            name.setMaxLines(2);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-1, -2);
            nameParams.topMargin = dp(10);
            card.addView(name, nameParams);

            // 时长设置（仅当未开始时显示）
            if (!slot.isRunning()) {
                LinearLayout timeRow = new LinearLayout(this);
                timeRow.setGravity(Gravity.CENTER_VERTICAL);
                timeRow.addView(text("时长:", 13, UiPalette.MUTED, false),
                        new LinearLayout.LayoutParams(-2, -2));

                EditText targetInput;
                if (label.equals("主线任务")) {
                    targetInput = mainTargetInput = FormKit.input(this, InputType.TYPE_CLASS_NUMBER);
                } else if (label.equals("支线任务 1")) {
                    targetInput = sub1TargetInput = FormKit.input(this, InputType.TYPE_CLASS_NUMBER);
                } else {
                    targetInput = sub2TargetInput = FormKit.input(this, InputType.TYPE_CLASS_NUMBER);
                }
                targetInput.setSingleLine(true);
                targetInput.setHint((slot.targetSeconds > 0 ? slot.targetSeconds / 60 : 25) + "分钟");
                targetInput.setMaxWidth(dp(80));
                LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(-2, dp(40));
                inputParams.leftMargin = dp(8);
                inputParams.rightMargin = dp(8);
                timeRow.addView(targetInput, inputParams);

                timeRow.addView(text("分钟", 13, UiPalette.MUTED, false));
                card.addView(timeRow, new LinearLayout.LayoutParams(-1, -2, 1f));
            }

            // 操作按钮
            LinearLayout actions = new LinearLayout(this);
            actions.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
            actionsParams.topMargin = dp(14);
            card.addView(actions, actionsParams);

            if (!slot.isRunning()) {
                // 未开始：显示"开始做"按钮
                TextView start = pill("开始做", UiPalette.ACCENT, Color.WHITE);
                start.setOnClickListener(v -> {
                    // 获取用户设置的时长并更新槽位
                    int targetSec = slot.targetSeconds;
                    EditText input = label.equals("主线任务") ? mainTargetInput :
                                     label.equals("支线任务 1") ? sub1TargetInput : sub2TargetInput;
                    if (input != null && !input.getText().toString().isEmpty()) {
                        try {
                            targetSec = Integer.parseInt(input.getText().toString().trim()) * 60;
                            // 更新槽位的 targetSeconds
                            if (label.equals("主线任务")) {
                                FocusStore.setMain(this, slot.title, slot.ruleId, targetSec);
                            } else if (label.equals("支线任务 1")) {
                                FocusStore.setSub1(this, slot.title, slot.ruleId, targetSec);
                            } else {
                                FocusStore.setSub2(this, slot.title, slot.ruleId, targetSec);
                            }
                        } catch (NumberFormatException e) {
                            targetSec = 25 * 60;
                        }
                    }
                    // 开始任务
                    FocusStore.startSlot(this, slot.slotKey);
                    render();
                });
                actions.addView(start, new LinearLayout.LayoutParams(0, dp(48), 1f));

                TextView clear = pill("清空", UiPalette.PALE, UiPalette.ACCENT);
                clear.setOnClickListener(v -> onClear.run());
                LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
                clearParams.leftMargin = dp(8);
                actions.addView(clear, clearParams);
            } else {
                // 进行中：显示"做完了"、"清空"和"更多"按钮
                TextView done = pill("做完了", UiPalette.ACCENT, Color.WHITE);
                done.setOnClickListener(v -> onComplete.run());
                actions.addView(done, new LinearLayout.LayoutParams(0, dp(48), 1f));

                TextView clear = pill("清空", UiPalette.PALE, UiPalette.ACCENT);
                clear.setOnClickListener(v -> onClear.run());
                LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
                clearParams.leftMargin = dp(8);
                actions.addView(clear, clearParams);

                TextView more = text("⋯", 18, UiPalette.ACCENT, true);
                more.setGravity(Gravity.CENTER);
                more.setBackground(FormKit.round(this, UiPalette.PALE, 12));
                more.setContentDescription("更多");
                more.setOnClickListener(v -> showSlotMenu(slot.slotKey));
                LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(dp(48), dp(48));
                moreParams.leftMargin = dp(8);
                actions.addView(more, moreParams);
            }

            // 显示步骤（如果有）
            if (slot.ruleId >= 0) {
                card.addView(buildSubtaskBlock(slot.ruleId));
            }
        }
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

            block.addView(buildStepsStrip(ruleId, subs, dayStart, 10));
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

    private void showStepMenu(final ReminderSettings.Rule sub) {
        FormKit.options(this, sub.title, new String[]{"改名字", "删掉"}, 1, which -> {
            if (which == 0) renameStep(sub);
            else confirmDeleteSubtask(sub);
        });
    }

    private void renameStep(final ReminderSettings.Rule sub) {
        final EditText input = FormKit.textInput(this, "步骤名字", sub.title);
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
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String title = input.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(this, "名字不能是空的", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ReminderSettings.upsertRule(this, sub.withTitle(title));
                    dialog.dismiss();
                    render();
                }));
        FormKit.showStyled(this, dialog);
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

    /** 显示槽位菜单（放回想法/清空） */
    private void showSlotMenu(final String slotKey) {
        FocusStore.TaskSlot slot;
        if (slotKey.equals(FocusStore.KEY_MAIN)) {
            slot = FocusStore.getMain(this);
        } else if (slotKey.equals(FocusStore.KEY_SUB1)) {
            slot = FocusStore.getSub1(this);
        } else {
            slot = FocusStore.getSub2(this);
        }

        if (slot.isEmpty()) return;

        FormKit.options(this, slot.title,
                new String[]{"放回「刚想到的」", "先放下，不记录"}, -1, which -> {
                    if (which == 0 && !slot.title.isEmpty()) {
                        FocusStore.addIdea(this, slot.title, slot.ruleId);
                    }
                    clearSlot(slotKey);
                });
    }

    /** 完成指定槽位的任务 */
    private void completeSlot(String slotKey) {
        FocusStore.TaskSlot slot;
        if (slotKey.equals(FocusStore.KEY_MAIN)) {
            slot = FocusStore.getMain(this);
        } else if (slotKey.equals(FocusStore.KEY_SUB1)) {
            slot = FocusStore.getSub1(this);
        } else {
            slot = FocusStore.getSub2(this);
        }

        if (slot.isEmpty()) return;

        final long startedAt = slot.startedAt;
        final long now = System.currentTimeMillis();

        ReminderRecord.finish(this, UUID.randomUUID().toString(), slot.title,
                ReminderRecord.KIND_DONE, startedAt > 0 ? startedAt : now, now,
                "专注完成 (" + slot.slotKey + ")", ReminderSettings.MODULE_TASK);

        if (slot.ruleId >= 0) {
            final long dayStart = ReminderRecord.dayStart(now);
            ReminderSettings.Rule rule = ReminderSettings.ruleById(this, slot.ruleId);
            if (rule != null) {
                for (ReminderSettings.Rule sub : Subtasks.of(this, slot.ruleId)) {
                    if (!Subtasks.doneToday(this, slot.ruleId, sub.title, dayStart)) {
                        Subtasks.toggle(this, slot.ruleId, sub.title, false, dayStart);
                    }
                }
                ReminderSettings.markCompleted(this, slot.ruleId, Math.max(1, rule.dailyTarget));
            } else {
                Habits.Habit habit = Habits.byParentId(this, slot.ruleId);
                if (habit != null && !Habits.isCompletedToday(this, habit.id, dayStart)) {
                    Habits.toggleCompletion(this, habit.id, dayStart, true);
                }
            }
        }

        long minutes = startedAt > 0 ? Math.max(1, (now - startedAt) / 60000) : 0;
        doneBanner = minutes > 0
                ? "「" + slot.title + "」做完，用了 " + minutes + " 分钟"
                : "「" + slot.title + "」做完了";
        clearSlot(slotKey);
    }

    /** 清空指定槽位 */
    private void clearSlot(String slotKey) {
        FocusStore.clearSlot(this, slotKey);
        render();
    }

    /** 完成主任务：写一条带真实时长的记录，顺带把步骤全勾上。（保留向后兼容） */
    @Deprecated
    private void completeCurrent() {
        completeSlot(FocusStore.KEY_MAIN);
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
        head.addView(text("添加任务到槽位", 14, UiPalette.INK, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        if (pickerOpen) {
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

        // 微习惯也在候选里：任务页上能看到的，这里都能挑
        List<Habits.Habit> habits = Habits.todayScheduled(this);
        if (!habits.isEmpty()) {
            card.addView(pickerSectionLabel("微习惯"));
            for (int i = 0; i < habits.size(); i++) {
                if (i > 0) card.addView(rowDivider());
                card.addView(buildHabitPickerRow(habits.get(i), dayStart));
            }
        }

        if (pending.isEmpty() && habits.isEmpty()) {
            TextView empty = text("今天清单是空的，直接在下面写一件", 13, UiPalette.MUTED, false);
            empty.setPadding(dp(2), dp(12), dp(2), dp(4));
            card.addView(empty);
        } else if (!pending.isEmpty()) {
            card.addView(pickerSectionLabel("今日任务"));
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

        row.setOnClickListener(v -> showSlotPickerDialog(task.title, task.id));
        return row;
    }

    /** 显示槽位选择对话框：让用户选择将任务添加到主线/支线 1/支线 2 */
    private void showSlotPickerDialog(final String title, final int ruleId) {
        final String[] slots = {"主线任务", "支线任务 1", "支线任务 2"};
        FormKit.options(this, "添加到哪个槽位？", slots, -1, which -> {
            int targetSec = 25 * 60; // 默认 25 分钟
            if (which == 0) {
                FocusStore.setMain(this, title, ruleId, targetSec);
            } else if (which == 1) {
                FocusStore.setSub1(this, title, ruleId, targetSec);
            } else {
                FocusStore.setSub2(this, title, ruleId, targetSec);
            }
            pickerOpen = false;
            doneBanner = "";
            render();
        });
    }

    private View pickerSectionLabel(String name) {
        TextView label = text(name, 11, UiPalette.MUTED, true);
        label.setPadding(dp(2), dp(10), dp(2), dp(4));
        return label;
    }

    /** 微习惯那一行：点一下就当成现在这件事。 */
    private View buildHabitPickerRow(final Habits.Habit habit, long dayStart) {
        final boolean done = Habits.isCompletedToday(this, habit.id, dayStart);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(46));
        row.setPadding(dp(2), dp(10), dp(2), dp(10));

        row.addView(dotView(done ? UiPalette.ACCENT : UiPalette.SOFT),
                new LinearLayout.LayoutParams(dp(6), dp(6)));

        TextView name = text(habit.title, 15, done ? UiPalette.MUTED : UiPalette.INK, false);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = dp(10);
        row.addView(name, nameParams);

        TextView act = text(done ? "已打卡" : "开始", 12,
                done ? UiPalette.MUTED : UiPalette.ACCENT, true);
        act.setPadding(dp(8), dp(4), dp(2), dp(4));
        row.addView(act);

        row.setOnClickListener(v -> {
            showSlotPickerDialog(habit.title, Habits.parentId(habit.id));
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
        showSlotPickerDialog(title, rule != null ? rule.id : -1);
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
        final String title = ideaTitle(idea);
        final boolean done = ReminderRecord.completedOn(this,
                ReminderSettings.MODULE_TASK, title, dayStart);

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(54));
        row.setPadding(dp(14), dp(11), dp(10), dp(11));

        // 圆形勾选框，和任务页主任务完全一致
        GradientDrawable boxShape = new GradientDrawable();
        boxShape.setShape(GradientDrawable.OVAL);
        boxShape.setColor(done ? UiPalette.ACCENT : Color.TRANSPARENT);
        if (!done) boxShape.setStroke(dp(2), UiPalette.DISABLED);
        TextView box = text(done ? "✓" : "", 12, done ? Color.WHITE : UiPalette.DISABLED, true);
        box.setGravity(Gravity.CENTER);
        box.setBackground(boxShape);
        box.setContentDescription(done ? "取消完成" : "标记完成");
        row.addView(box, new LinearLayout.LayoutParams(dp(24), dp(24)));
        box.setOnClickListener(v -> toggleIdeaDone(idea, ruleId, done, dayStart));

        TextView name = text(title, 15, done ? UiPalette.MUTED : UiPalette.INK, false);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        if (done) {
            name.setPaintFlags(name.getPaintFlags()
                    | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        }
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = dp(12);
        row.addView(name, nameParams);

        if (!subs.isEmpty()) {
            TextView steps = text(subs.size() + " 步", 11, UiPalette.MUTED, false);
            LinearLayout.LayoutParams stepsParams = new LinearLayout.LayoutParams(-2, -2);
            stepsParams.leftMargin = dp(8);
            row.addView(steps, stepsParams);
        }

        if (!done) {
            TextView take = text("设为现在", 12, UiPalette.ACCENT, true);
            take.setPadding(dp(10), dp(6), dp(4), dp(6));
            LinearLayout.LayoutParams takeParams = new LinearLayout.LayoutParams(-2, -2);
            takeParams.leftMargin = dp(6);
            row.addView(take, takeParams);
            take.setOnClickListener(v -> confirmTakeIdea(idea));
        }

        TextView chevron = text("›", 17, UiPalette.DISABLED, true);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(-2, -2);
        chevronParams.leftMargin = dp(4);
        row.addView(chevron, chevronParams);

        row.setOnClickListener(v -> openIdeaDetail(ruleId, title));
        row.setOnLongClickListener(v -> {
            showIdeaMenu(idea);
            return true;
        });
        wrapper.addView(row);

        if (!subs.isEmpty()) {
            wrapper.addView(buildStepsStrip(ruleId, subs, dayStart, 38));
        }
        return wrapper;
    }

    /** 步骤条：和任务页一样，15dp 方框 + 文字 + →，横向滑动，点一下勾。 */
    private View buildStepsStrip(final int ruleId, List<ReminderSettings.Rule> subs,
                                 long dayStart, int leftMarginDp) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.setPadding(dp(10), dp(2), dp(6), dp(3));
        scroll.setMinimumHeight(dp(36));

        LinearLayout flow = new LinearLayout(this);
        flow.setOrientation(LinearLayout.HORIZONTAL);
        flow.setGravity(Gravity.CENTER_VERTICAL);

        for (int i = 0; i < subs.size(); i++) {
            final ReminderSettings.Rule sub = subs.get(i);
            final boolean subDone = Subtasks.doneToday(this, ruleId, sub.title, dayStart);

            LinearLayout subBox = new LinearLayout(this);
            subBox.setOrientation(LinearLayout.HORIZONTAL);
            subBox.setGravity(Gravity.CENTER_VERTICAL);
            subBox.setMinimumHeight(dp(32));
            subBox.setPadding(dp(2), dp(2), dp(6), dp(2));

            GradientDrawable cbShape = new GradientDrawable();
            cbShape.setCornerRadius(dp(3));
            cbShape.setColor(subDone ? UiPalette.ACCENT : Color.TRANSPARENT);
            cbShape.setStroke(dp(1), subDone ? Color.TRANSPARENT : UiPalette.DISABLED);
            TextView cb = text(subDone ? "✓" : "", 9, subDone ? Color.WHITE : UiPalette.DISABLED, true);
            cb.setGravity(Gravity.CENTER);
            cb.setBackground(cbShape);
            subBox.addView(cb, new LinearLayout.LayoutParams(dp(15), dp(15)));

            TextView subTitle = text(sub.title, 13, subDone ? UiPalette.MUTED : UiPalette.INK, false);
            if (subDone) {
                subTitle.setPaintFlags(subTitle.getPaintFlags()
                        | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
            }
            LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-2, -2);
            subParams.leftMargin = dp(7);
            subBox.addView(subTitle, subParams);

            subBox.setOnClickListener(v -> {
                boolean now = Subtasks.doneToday(this, ruleId, sub.title, dayStart);
                Subtasks.toggle(this, ruleId, sub.title, now, dayStart);
                render();
            });
            subBox.setOnLongClickListener(v -> {
                showStepMenu(sub);
                return true;
            });
            flow.addView(subBox, new LinearLayout.LayoutParams(-2, -2));

            if (i < subs.size() - 1) {
                TextView arrow = text("→", 11, UiPalette.DISABLED, false);
                LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(-2, -2);
                arrowParams.leftMargin = dp(7);
                arrowParams.rightMargin = dp(7);
                flow.addView(arrow, arrowParams);
            }
        }
        scroll.addView(flow, new HorizontalScrollView.LayoutParams(-2, -2));

        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable band = new GradientDrawable();
        band.setCornerRadius(dp(12));
        band.setColor(UiPalette.blend(UiPalette.SURFACE, UiPalette.ACCENT, 0.07f));
        wrap.setBackground(band);
        LinearLayout.LayoutParams wrapParams = new LinearLayout.LayoutParams(-1, -2);
        wrapParams.leftMargin = dp(leftMarginDp);
        wrapParams.rightMargin = dp(2);
        wrapParams.topMargin = dp(4);
        wrapParams.bottomMargin = dp(6);
        wrap.setLayoutParams(wrapParams);
        wrap.addView(scroll, new LinearLayout.LayoutParams(0, -2, 1f));
        return wrap;
    }

    /** 勾「刚想到的」条目：跟任务页勾任务同一套记录，勾完顺手把步骤也带上。 */
    private void toggleIdeaDone(final FocusStore.Idea idea, int ruleId, boolean done, long dayStart) {
        String title = ideaTitle(idea);
        if (done) {
            ReminderRecord.deleteCompletion(this, ReminderSettings.MODULE_TASK, title, dayStart);
            if (ruleId >= 0) ReminderSettings.unmarkCompleted(this, ruleId);
        } else {
            if (ruleId >= 0) {
                for (ReminderSettings.Rule sub : Subtasks.of(this, ruleId)) {
                    if (!Subtasks.doneToday(this, ruleId, sub.title, dayStart)) {
                        Subtasks.toggle(this, ruleId, sub.title, false, dayStart);
                    }
                }
            }
            long now = System.currentTimeMillis();
            ReminderRecord.finish(this, UUID.randomUUID().toString(), title,
                    ReminderRecord.KIND_DONE, now, now, "刚想到的勾选", ReminderSettings.MODULE_TASK);
            if (ruleId >= 0) {
                ReminderSettings.Rule rule = ReminderSettings.ruleById(this, ruleId);
                ReminderSettings.markCompleted(this, ruleId,
                        rule != null ? Math.max(1, rule.dailyTarget) : 1);
            }
        }
        render();
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

    /** 将想法设为任务：直接弹出槽位选择 */
    private void confirmTakeIdea(final FocusStore.Idea idea) {
        takeIdea(idea);
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

    /** 想法升为任务：让用户选择放到哪个槽位 */
    private void takeIdea(final FocusStore.Idea idea) {
        final int ruleId = idea.ruleId >= 0 ? idea.ruleId : FocusStore.ensureIdeaRule(this, idea);
        final String title = ideaTitle(idea);

        final String[] slots = {"主线任务", "支线任务 1", "支线任务 2"};
        FormKit.options(this, "放到哪个槽位？", slots, -1, which -> {
            int targetSec = 25 * 60;
            if (which == 0) {
                FocusStore.setMain(this, title, ruleId, targetSec);
            } else if (which == 1) {
                FocusStore.setSub1(this, title, ruleId, targetSec);
            } else {
                FocusStore.setSub2(this, title, ruleId, targetSec);
            }
            FocusStore.removeIdea(this, idea.id);
            doneBanner = "";
            pickerOpen = false;
            render();
            Toast.makeText(this, "已添加：「" + title + "」", Toast.LENGTH_SHORT).show();
        });
    }

    /** 长按条目：改名字 / 删掉。 */
    private void showIdeaMenu(final FocusStore.Idea idea) {
        FormKit.options(this, ideaTitle(idea), new String[]{"改名字", "删掉"}, 1, which -> {
            if (which == 0) renameIdea(idea);
            else confirmDeleteIdea(idea);
        });
    }

    private void renameIdea(final FocusStore.Idea idea) {
        final int ruleId = idea.ruleId >= 0 ? idea.ruleId : FocusStore.ensureIdeaRule(this, idea);
        final ReminderSettings.Rule rule = ruleId >= 0
                ? ReminderSettings.ruleById(this, ruleId) : null;

        final EditText input = FormKit.textInput(this, "名字",
                rule != null ? rule.title : idea.title);
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
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String title = input.getText().toString().trim();
                    if (title.isEmpty()) {
                        Toast.makeText(this, "名字不能是空的", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (rule != null) ReminderSettings.upsertRule(this, rule.withTitle(title));
                    FocusStore.renameIdea(this, idea.id, title);
                    dialog.dismiss();
                    render();
                }));
        FormKit.showStyled(this, dialog);
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

    /** 悬浮条开关：退出 App 后仍浮在屏幕上，只显示当前这一件。 */
    private View buildBubbleCard() {
        final boolean on = FocusBubbleService.isOn(this);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(FormKit.round(this, Color.WHITE, 16));
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(18);
        card.setLayoutParams(params);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(text("悬浮条", 14, UiPalette.INK, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(text(on ? "已开" : "已关", 12,
                on ? UiPalette.ACCENT : UiPalette.MUTED, true));
        card.addView(row);

        TextView hint = text(on
                ? "退出 App 后也浮在屏幕上，点它回到这一页，长按收起"
                : "打开后退出 App 也能看到正在做的事", 11, UiPalette.MUTED, false);
        hint.setPadding(0, dp(4), 0, 0);
        card.addView(hint);

        card.setOnClickListener(v -> toggleBubble());
        return card;
    }

    private void toggleBubble() {
        if (FocusBubbleService.isOn(this)) {
            FocusBubbleService.setOn(this, false);
            FocusBubbleService.stop(this);
            render();
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "要先允许「显示在其他应用上层」", Toast.LENGTH_SHORT).show();
            pendingBubble = true;
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (RuntimeException e) {
                pendingBubble = false;
            }
            return;
        }
        FocusBubbleService.setOn(this, true);
        FocusBubbleService.start(this);
        render();
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
