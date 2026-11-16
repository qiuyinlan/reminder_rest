package io.iftech.healthreminder;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 悬浮条：退出 App 后仍浮在屏幕上的半透明长条。
 *
 * 点一下展开 —— 看「刚想到的」，还能当场 ➕ 记一条；
 * 长按才进 App（原来点一下就跳走，太容易误触）。
 */
public final class FocusBubbleService extends Service {
    private static final String PREFS = "focus_bubble";
    private static final String KEY_ON = "on";
    private static final String CHANNEL = "focus_bubble";
    private static final int NOTICE_ID = 4301;
    /** 长按多久算「进 App」。 */
    private static final long LONG_PRESS_MS = 600;
    /** 展开后最多列几条想法，剩下的回 App 里看。 */
    private static final int MAX_IDEAS = 4;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private LinearLayout card;
    private LinearLayout headerBox;
    private LinearLayout expandBox;
    private LinearLayout ideaContainer;
    private LinearLayout stepBox;
    private TextView modeView;
    private TextView titleView;
    private TextView elapsedView;
    private TextView countView;
    private TextView collapseView;
    private EditText ideaInput;

    private boolean expanded;
    private boolean attached;
    /** 0 = 记想法，1 = 给当前这件事加步骤。 */
    private int inputMode;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 30 * 1000L);
        }
    };

    // ------------------------------------------------------------ 开关状态

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean isOn(Context context) {
        return prefs(context).getBoolean(KEY_ON, false);
    }

    static void setOn(Context context, boolean on) {
        prefs(context).edit().putBoolean(KEY_ON, on).apply();
    }

    static void start(Context context) {
        Intent intent = new Intent(context, FocusBubbleService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent);
            else context.startService(intent);
        } catch (RuntimeException ignored) {
            // 后台不允许起前台服务时静默失败，回到 App 再打开
        }
    }

    static void stop(Context context) {
        context.stopService(new Intent(context, FocusBubbleService.class));
    }

    /** 主任务变了就踢一下服务，让条子立刻跟着改。 */
    static void refreshNow(Context context) {
        if (!isOn(context)) return;
        try {
            context.startService(new Intent(context, FocusBubbleService.class));
        } catch (RuntimeException ignored) { }
    }

    // ------------------------------------------------------------ 生命周期

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        UiPalette.load(this);
        startForeground(NOTICE_ID, buildNotice());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        showBubble();
        handler.post(ticker);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        render();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(ticker);
        detach();
        super.onDestroy();
    }

    // -------------------------------------------------------------- 悬浮条

    private void showBubble() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
            setOn(this, false);
            stopSelf();
            return;
        }
        if (windowManager == null) return;

        card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(20));
        // 半透明卡片：0xD8 = 85% 不透明，既不挡内容又看得清字
        bg.setColor((0xD8 << 24) | (UiPalette.SURFACE & 0x00FFFFFF));
        bg.setStroke(dp(1), UiPalette.BORDER);
        card.setBackground(bg);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        card.setElevation(dp(8));

        headerBox = new LinearLayout(this);
        headerBox.setOrientation(LinearLayout.VERTICAL);
        card.addView(headerBox, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(this);
        GradientDrawable dotShape = new GradientDrawable();
        dotShape.setShape(GradientDrawable.OVAL);
        dotShape.setColor(UiPalette.ACCENT);
        dot.setBackground(dotShape);
        head.addView(dot, new LinearLayout.LayoutParams(dp(7), dp(7)));

        TextView label = FormKit.text(this, "正在做", 10, UiPalette.MUTED, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.leftMargin = dp(6);
        head.addView(label, labelParams);

        elapsedView = FormKit.text(this, "", 10, UiPalette.MUTED, false);
        head.addView(elapsedView);

        // 展开态的收起按钮（只在展开时出现，避免误触）
        collapseView = FormKit.text(this, "－", 14, UiPalette.ACCENT, true);
        collapseView.setGravity(Gravity.CENTER);
        collapseView.setContentDescription("收起");
        collapseView.setPadding(dp(8), dp(1), dp(2), dp(1));
        LinearLayout.LayoutParams collapseParams = new LinearLayout.LayoutParams(-2, -2);
        collapseParams.leftMargin = dp(4);
        head.addView(collapseView, collapseParams);
        collapseView.setOnClickListener(v -> {
            if (expanded) toggleExpand();
        });
        headerBox.addView(head);

        titleView = FormKit.text(this, "", 13, UiPalette.INK, true);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(3);
        headerBox.addView(titleView, titleParams);

        expandBox = new LinearLayout(this);
        expandBox.setOrientation(LinearLayout.VERTICAL);
        expandBox.setVisibility(View.GONE);
        card.addView(expandBox, new LinearLayout.LayoutParams(-1, -2));

        View line = new View(this);
        line.setBackgroundColor(UiPalette.hairline());
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, dp(1));
        lineParams.topMargin = dp(9);
        lineParams.bottomMargin = dp(8);
        expandBox.addView(line, lineParams);

        stepBox = new LinearLayout(this);
        stepBox.setOrientation(LinearLayout.VERTICAL);
        expandBox.addView(stepBox, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout ideaHead = new LinearLayout(this);
        ideaHead.setGravity(Gravity.CENTER_VERTICAL);
        ideaHead.addView(FormKit.text(this, "刚想到的", 11, UiPalette.MUTED, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        countView = FormKit.text(this, "", 11, UiPalette.ACCENT, true);
        ideaHead.addView(countView);
        expandBox.addView(ideaHead);

        ideaContainer = new LinearLayout(this);
        ideaContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ideasParams = new LinearLayout.LayoutParams(-1, -2);
        ideasParams.topMargin = dp(2);
        expandBox.addView(ideaContainer, ideasParams);

        expandBox.addView(buildInputRow(), new LinearLayout.LayoutParams(-1, -2));
        // 展开后手指按在列表/步骤上也应该能长按进 App
        expandBox.setOnLongClickListener(v -> {
            openFocus();
            return true;
        });

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(16);
        params.y = dp(150);

        attachTouch();
        try {
            windowManager.addView(card, params);
            attached = true;
        } catch (RuntimeException ignored) {
            attached = false;
        }
        render();
    }

    private View buildInputRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(8);
        row.setLayoutParams(rowParams);

        // 模式标签：记想法 / 加步骤
        modeView = FormKit.text(this, "想法", 10, UiPalette.MUTED, true);
        modeView.setGravity(Gravity.CENTER);
        modeView.setPadding(dp(6), dp(5), dp(6), dp(5));
        modeView.setBackground(FormKit.round(this, UiPalette.SOFT, 8));
        modeView.setOnClickListener(v -> {
            if (!FocusStore.hasCurrent(this)) {
                Toast.makeText(this, "先挑一件现在做的事", Toast.LENGTH_SHORT).show();
                return;
            }
            inputMode = inputMode == 1 ? 0 : 1;
            applyInputMode();
        });
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(-2, -2);
        modeParams.rightMargin = dp(6);
        row.addView(modeView, modeParams);

        ideaInput = new EditText(this);
        ideaInput.setHint("记一件，别现在做");
        ideaInput.setTextSize(12);
        ideaInput.setSingleLine(true);
        ideaInput.setMaxLines(1);
        ideaInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        ideaInput.setInputType(InputType.TYPE_CLASS_TEXT);
        ideaInput.setTextColor(UiPalette.INK);
        ideaInput.setHintTextColor(UiPalette.MUTED);
        ideaInput.setPadding(dp(8), dp(6), dp(8), dp(6));
        ideaInput.setBackground(UiPalette.inputBackground(this, UiPalette.PALE, 10));
        ideaInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) grabWindowFocus();
            else dropWindowFocus();
        });
        ideaInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitInput();
                return true;
            }
            return false;
        });
        row.addView(ideaInput, new LinearLayout.LayoutParams(0, dp(36), 1f));

        TextView plus = FormKit.text(this, "＋", 16, Color.WHITE, true);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(FormKit.round(this, UiPalette.ACCENT, 10));
        plus.setOnClickListener(v -> submitInput());
        LinearLayout.LayoutParams plusParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        plusParams.leftMargin = dp(6);
        row.addView(plus, plusParams);
        return row;
    }

    private void attachTouch() {
        card.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startX;
            private int startY;
            private long downAt;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    // 展开态里手按在列表/输入区上，交给子视图自己处理
                    if (expanded && event.getY() > headerBox.getHeight()) return false;
                }
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        downAt = System.currentTimeMillis();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int dx = (int) (event.getRawX() - downX);
                        int dy = (int) (event.getRawY() - downY);
                        if (Math.abs(dx) > dp(12) || Math.abs(dy) > dp(12)) moved = true;
                        if (!moved) return true;
                        params.x = startX + dx;
                        params.y = startY + dy;
                        updateLayout();
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            if (System.currentTimeMillis() - downAt >= LONG_PRESS_MS) {
                                openFocus();
                            } else if (!expanded) {
                                // 展开态点空白不再收起，收起只走右上角 －，防止误触
                                toggleExpand();
                            }
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void updateLayout() {
        if (windowManager == null || !attached || card == null) return;
        try {
            windowManager.updateViewLayout(card, params);
        } catch (RuntimeException ignored) { }
    }

    private void toggleExpand() {
        expanded = !expanded;
        if (!expanded) dropWindowFocus();
        render();
    }

    private void render() {
        if (card == null) return;

        String title = FocusStore.currentTitle(this);
        boolean has = title != null && !title.isEmpty();
        titleView.setText(has ? title : "点一下挑一件事，长按进 App");
        elapsedView.setText(has ? elapsedText(FocusStore.currentStartedAt(this)) : "");

        expandBox.setVisibility(expanded ? View.VISIBLE : View.GONE);
        collapseView.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (expanded) {
            fillSteps();
            fillIdeas();
            applyInputMode();
        }

        params.width = expanded ? dp(238) : WindowManager.LayoutParams.WRAP_CONTENT;
        updateLayout();
    }

    private void fillIdeas() {
        if (ideaContainer == null) return;
        ideaContainer.removeAllViews();

        List<FocusStore.Idea> ideas = FocusStore.ideas(this);
        countView.setText(ideas.isEmpty() ? "" : String.valueOf(ideas.size()));

        if (ideas.isEmpty()) {
            TextView empty = FormKit.text(this, "还没记东西", 11, UiPalette.MUTED, false);
            empty.setPadding(0, dp(5), 0, dp(3));
            ideaContainer.addView(empty);
            return;
        }

        int shown = Math.min(ideas.size(), MAX_IDEAS);
        for (int i = 0; i < shown; i++) {
            ideaContainer.addView(buildIdeaRow(ideas.get(i)));
        }
        if (ideas.size() > shown) {
            TextView rest = FormKit.text(this, "还有 " + (ideas.size() - shown) + " 条在 App 里",
                    10, UiPalette.MUTED, false);
            rest.setPadding(0, dp(4), 0, 0);
            ideaContainer.addView(rest);
        }
    }

    private View buildIdeaRow(final FocusStore.Idea idea) {
        final int ruleId = idea.ruleId >= 0 ? idea.ruleId : FocusStore.ensureIdeaRule(this, idea);
        final long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
        final String title = ideaTitle(idea);
        final boolean done = ReminderRecord.completedOn(this,
                ReminderSettings.MODULE_TASK, title, dayStart);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        // 圆形勾选框：在悬浮窗里也能直接勾掉一条
        GradientDrawable boxShape = new GradientDrawable();
        boxShape.setShape(GradientDrawable.OVAL);
        boxShape.setColor(done ? UiPalette.ACCENT : Color.TRANSPARENT);
        if (!done) boxShape.setStroke(dp(1), UiPalette.DISABLED);
        TextView box = FormKit.text(this, done ? "✓" : "", 8,
                done ? Color.WHITE : UiPalette.DISABLED, true);
        box.setGravity(Gravity.CENTER);
        box.setBackground(boxShape);
        box.setContentDescription(done ? "取消完成" : "标记完成");
        row.addView(box, new LinearLayout.LayoutParams(dp(15), dp(15)));
        box.setOnClickListener(v -> toggleIdeaDone(idea, ruleId, title, dayStart));

        TextView name = FormKit.text(this, title, 12,
                done ? UiPalette.MUTED : UiPalette.INK, false);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        if (done) {
            name.setPaintFlags(name.getPaintFlags()
                    | android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);
        }
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = dp(7);
        row.addView(name, nameParams);

        if (!done) {
            TextView take = FormKit.text(this, "↗", 13, UiPalette.ACCENT, true);
            take.setPadding(dp(8), 0, dp(2), 0);
            take.setContentDescription("设为现在");
            take.setOnClickListener(v -> takeIdea(idea));
            row.addView(take);
        }
        return row;
    }

    private String ideaTitle(FocusStore.Idea idea) {
        if (idea.ruleId >= 0) {
            ReminderSettings.Rule rule = ReminderSettings.ruleById(this, idea.ruleId);
            if (rule != null && !rule.title.isEmpty()) return rule.title;
        }
        return idea.title;
    }

    /** 在悬浮窗里勾掉一条想法：写和 App 内同一套完成记录。 */
    private void toggleIdeaDone(FocusStore.Idea idea, int ruleId, String title, long dayStart) {
        boolean done = ReminderRecord.completedOn(this,
                ReminderSettings.MODULE_TASK, title, dayStart);
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
            ReminderRecord.finish(this, java.util.UUID.randomUUID().toString(), title,
                    ReminderRecord.KIND_DONE, now, now, "悬浮条勾选", ReminderSettings.MODULE_TASK);
            if (ruleId >= 0) {
                ReminderSettings.Rule rule = ReminderSettings.ruleById(this, ruleId);
                ReminderSettings.markCompleted(this, ruleId,
                        rule != null ? Math.max(1, rule.dailyTarget) : 1);
            }
        }
        render();
    }

    private void submitInput() {
        String text = ideaInput == null ? "" : ideaInput.getText().toString().trim();
        if (text.isEmpty()) {
            dropWindowFocus();
            return;
        }
        if (inputMode == 1) {
            addStep(text);
        } else {
            ReminderSettings.Rule rule = FocusStore.createTaskRule(this, text);
            FocusStore.addIdea(this, text, rule != null ? rule.id : -1);
            Toast.makeText(this, "记下了", Toast.LENGTH_SHORT).show();
        }
        ideaInput.setText("");
        dropWindowFocus();
        render();
    }

    /** 给「正在做」这件事加一步，和 App 里的步骤是同一份数据。 */
    private void addStep(String text) {
        int ruleId = FocusStore.currentRuleId(this);
        if (ruleId < 0) {
            Toast.makeText(this, "先挑一件现在做的事", Toast.LENGTH_SHORT).show();
            return;
        }
        List<ReminderSettings.Rule> subs = Subtasks.of(this, ruleId);
        int step = subs.isEmpty() ? 0 : subs.get(subs.size() - 1).step + 1;
        Subtasks.add(this, ruleId, text, step);
        Toast.makeText(this, "加了这一步", Toast.LENGTH_SHORT).show();
    }

    private void applyInputMode() {
        if (ideaInput == null || modeView == null) return;
        boolean stepMode = inputMode == 1 && FocusStore.hasCurrent(this);
        if (!FocusStore.hasCurrent(this)) inputMode = 0;
        modeView.setText(stepMode ? "步骤" : "想法");
        modeView.setTextColor(stepMode ? UiPalette.ACCENT : UiPalette.MUTED);
        modeView.setBackground(FormKit.round(this,
                stepMode ? UiPalette.PALE : UiPalette.SOFT, 8));
        ideaInput.setHint(stepMode ? "给现在这件事加一步" : "记一件，别现在做");
    }

    /** 展开时的那一小条步骤：能勾，和主任务是同一份数据。 */
    private void fillSteps() {
        if (stepBox == null) return;
        stepBox.removeAllViews();

        int ruleId = FocusStore.currentRuleId(this);
        boolean has = FocusStore.hasCurrent(this) && ruleId >= 0;
        stepBox.setVisibility(has ? View.VISIBLE : View.GONE);
        if (!has) return;

        final long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
        List<ReminderSettings.Rule> subs = Subtasks.of(this, ruleId);
        int done = 0;
        for (ReminderSettings.Rule sub : subs) {
            if (Subtasks.doneToday(this, ruleId, sub.title, dayStart)) done++;
        }

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(FormKit.text(this, "步骤", 11, UiPalette.MUTED, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        if (!subs.isEmpty()) {
            head.addView(FormKit.text(this, done + " / " + subs.size(), 11,
                    done >= subs.size() ? UiPalette.ACCENT : UiPalette.MUTED, true));
        }
        LinearLayout.LayoutParams headParams = new LinearLayout.LayoutParams(-1, -2);
        headParams.bottomMargin = dp(3);
        stepBox.addView(head, headParams);

        if (subs.isEmpty()) {
            TextView empty = FormKit.text(this, "还没拆步骤 · 点下面的「想法」切成「步骤」就能加",
                    10, UiPalette.MUTED, false);
            empty.setPadding(0, dp(2), 0, dp(2));
            stepBox.addView(empty);
            return;
        }

        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        LinearLayout flow = new LinearLayout(this);
        flow.setOrientation(LinearLayout.HORIZONTAL);
        flow.setGravity(Gravity.CENTER_VERTICAL);

        for (int i = 0; i < subs.size(); i++) {
            final ReminderSettings.Rule sub = subs.get(i);
            final boolean subDone = Subtasks.doneToday(this, ruleId, sub.title, dayStart);

            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.HORIZONTAL);
            box.setGravity(Gravity.CENTER_VERTICAL);
            box.setMinimumHeight(dp(28));
            box.setPadding(dp(2), dp(2), dp(4), dp(2));

            GradientDrawable cbShape = new GradientDrawable();
            cbShape.setCornerRadius(dp(3));
            cbShape.setColor(subDone ? UiPalette.ACCENT : Color.TRANSPARENT);
            cbShape.setStroke(dp(1), subDone ? Color.TRANSPARENT : UiPalette.DISABLED);
            TextView cb = FormKit.text(this, subDone ? "✓" : "", 9,
                    subDone ? Color.WHITE : UiPalette.DISABLED, true);
            cb.setGravity(Gravity.CENTER);
            cb.setBackground(cbShape);
            box.addView(cb, new LinearLayout.LayoutParams(dp(14), dp(14)));

            TextView name = FormKit.text(this, sub.title, 12,
                    subDone ? UiPalette.MUTED : UiPalette.INK, false);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(-2, -2);
            nameParams.leftMargin = dp(6);
            box.addView(name, nameParams);

            box.setOnClickListener(v -> {
                // 实时取状态，不用渲染时的旧值，避免连点时状态错乱
                boolean now = Subtasks.doneToday(this, ruleId, sub.title, dayStart);
                Subtasks.toggle(this, ruleId, sub.title, now, dayStart);
                render();
            });
            flow.addView(box, new LinearLayout.LayoutParams(-2, -2));

            if (i < subs.size() - 1) {
                TextView arrow = FormKit.text(this, "→", 10, UiPalette.DISABLED, false);
                LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(-2, -2);
                arrowParams.leftMargin = dp(5);
                arrowParams.rightMargin = dp(5);
                flow.addView(arrow, arrowParams);
            }
        }
        scroll.addView(flow, new HorizontalScrollView.LayoutParams(-2, -2));
        stepBox.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
    }

    /** 想法变当前主任务：和 App 里一样，搬过去就不再留在想法表。 */
    private void takeIdea(FocusStore.Idea idea) {
        int ruleId = idea.ruleId >= 0 ? idea.ruleId : FocusStore.ensureIdeaRule(this, idea);
        String title = idea.title;
        if (ruleId >= 0) {
            ReminderSettings.Rule rule = ReminderSettings.ruleById(this, ruleId);
            if (rule != null && !rule.title.isEmpty()) title = rule.title;
        }
        FocusStore.setCurrent(this, title, ruleId);
        FocusStore.removeIdea(this, idea.id);
        render();
        Toast.makeText(this, "现在做：" + title, Toast.LENGTH_SHORT).show();
    }

    /** 输入需要窗口拿到焦点，平时又不能让条子抢焦点。 */
    private void grabWindowFocus() {
        if (params == null || (params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0) return;
        params.flags = params.flags & ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        params.y = Math.min(params.y, dp(120));
        updateLayout();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && ideaInput != null) {
            ideaInput.post(() -> imm.showSoftInput(ideaInput, InputMethodManager.SHOW_IMPLICIT));
        }
    }

    private void dropWindowFocus() {
        if (params == null || (params.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0) return;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null && ideaInput != null) {
            imm.hideSoftInputFromWindow(ideaInput.getWindowToken(), 0);
            ideaInput.clearFocus();
        }
        params.flags = params.flags | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        updateLayout();
    }

    private void openFocus() {
        Intent intent = new Intent(this, FocusActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            // 部分系统拦后台启动，退到 PendingIntent
            try {
                PendingIntent.getActivity(this, 2, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE).send();
            } catch (Exception ignored) { }
        }
    }

    private void detach() {
        if (card != null && windowManager != null && attached) {
            try {
                windowManager.removeView(card);
            } catch (RuntimeException ignored) { }
        }
        attached = false;
        card = null;
    }

    // ------------------------------------------------------------ 通知/工具

    private Notification buildNotice() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26 && manager != null) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "悬浮条",
                    NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            channel.setDescription("「现在」悬浮条运行时的常驻通知");
            manager.createNotificationChannel(channel);
        }

        Intent intent = new Intent(this, FocusActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        builder.setContentTitle("现在 · 悬浮条")
                .setContentText("点一下回到「现在」页")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .setContentIntent(pending);
        return builder.build();
    }

    private String elapsedText(long startedAt) {
        if (startedAt <= 0) return "";
        long minutes = (System.currentTimeMillis() - startedAt) / 60000;
        if (minutes < 1) return "刚开头";
        if (minutes < 60) return minutes + " 分钟";
        long hours = minutes / 60;
        long rest = minutes % 60;
        return rest == 0 ? hours + " 小时" : hours + " 小时 " + rest + " 分";
    }

    private int dp(float value) {
        return FormKit.dp(this, value);
    }
}
