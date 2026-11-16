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
    private TextView titleView;
    private TextView elapsedView;
    private TextView countView;
    private EditText ideaInput;

    private boolean expanded;
    private boolean attached;

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
                addIdea();
                return true;
            }
            return false;
        });
        row.addView(ideaInput, new LinearLayout.LayoutParams(0, dp(36), 1f));

        TextView plus = FormKit.text(this, "＋", 16, Color.WHITE, true);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(FormKit.round(this, UiPalette.ACCENT, 10));
        plus.setOnClickListener(v -> addIdea());
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
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true;
                        if (!moved) return true;
                        params.x = startX + dx;
                        params.y = startY + dy;
                        updateLayout();
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            if (System.currentTimeMillis() - downAt >= LONG_PRESS_MS) openFocus();
                            else toggleExpand();
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
        if (expanded) {
            fillIdeas();
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
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(5), 0, dp(5));

        View dot = new View(this);
        GradientDrawable dotShape = new GradientDrawable();
        dotShape.setShape(GradientDrawable.OVAL);
        dotShape.setColor(UiPalette.SOFT);
        dot.setBackground(dotShape);
        row.addView(dot, new LinearLayout.LayoutParams(dp(5), dp(5)));

        TextView name = FormKit.text(this, idea.title, 12, UiPalette.INK, false);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = dp(8);
        row.addView(name, nameParams);

        TextView take = FormKit.text(this, "↗", 13, UiPalette.ACCENT, true);
        take.setPadding(dp(8), 0, dp(2), 0);
        take.setContentDescription("设为现在");
        take.setOnClickListener(v -> takeIdea(idea));
        row.addView(take);
        return row;
    }

    private void addIdea() {
        String text = ideaInput == null ? "" : ideaInput.getText().toString().trim();
        if (text.isEmpty()) {
            dropWindowFocus();
            return;
        }
        ReminderSettings.Rule rule = FocusStore.createTaskRule(this, text);
        FocusStore.addIdea(this, text, rule != null ? rule.id : -1);
        ideaInput.setText("");
        dropWindowFocus();
        render();
        Toast.makeText(this, "记下了", Toast.LENGTH_SHORT).show();
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
        } catch (RuntimeException ignored) { }
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
