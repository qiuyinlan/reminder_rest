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
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
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
    private static final long LONG_PRESS_MS = 600; // 已不再用于进 App，保留给手势判定余量
    /** 贴边收起后的半个圆尺寸：宽度就是圆的一半，直接贴着屏幕边。 */
    private static final int PEEK_W = 28;
    private static final int PEEK_H = 56;
    /** 超时提醒色（红）。 */
    private static final int PEEK_OVERTIME = 0xffd9534f;
    /** 贴边状态：0 悬浮、-1 贴左、1 贴右。 */
    private static final String KEY_EDGE = "edge_side";
    private static final String KEY_EDGE_Y = "edge_y";

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

    /** 收起态的 3 个任务行（主线 + 支线）。 */
    private LinearLayout miniBox;
    /** 展开态的槽位状态区（跟步骤分开放，以前被步骤覆盖掉了）。 */
    private LinearLayout slotBox;

    /** 展开态整块内容的外层滚动容器，任务/想法多了直接滑。 */
    private ScrollView expandScroll;
    /** 展开态顶部切换：0 现在 / 1 白板 / 2 done。 */
    private int boardTab;
    private LinearLayout tabBar;
    /** 「现在」专属区域（音乐/步骤/想法/输入），切到白板或 done 时整块隐藏。 */
    private LinearLayout nowBox;
    /** 展开态头部「正在做」右边的放大按钮，点了进 App。 */
    private TextView expandAppView;
    /** 展开态头部中间的弹性占位，只在展开时生效（收起时保留 1px，不然会把条子撑到全屏）。 */
    private View headSpacer;
    /** 悬浮窗最底部的已完成区。 */
    private LinearLayout doneBox;
    private boolean doneIdeasOpen;

    /** 展开态的陪伴音乐按钮。 */
    private LinearLayout musicRowView;
    private TextView musicLabel;
    private TextView musicState;

    /** 贴边收起的半个圆。 */
    private LinearLayout peekView;
    private WindowManager.LayoutParams peekParams;
    /** 半圆当前贴在哪边：-1 左、1 右。 */
    private int peekSide;
    /** 0 悬浮、-1 贴左、1 贴右。 */
    private int edgeSide;

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

        card = new BubbleCard(this);
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
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.leftMargin = dp(6);
        head.addView(label, labelParams);

        // 放大按钮「紧跟在「正在做」右边（收起态不显示）
        expandAppView = FormKit.text(this, "🔍", 13, UiPalette.ACCENT, true);
        expandAppView.setGravity(Gravity.CENTER);
        expandAppView.setContentDescription("打开当前页面");
        expandAppView.setPadding(dp(8), dp(2), dp(8), dp(2));
        LinearLayout.LayoutParams appParams = new LinearLayout.LayoutParams(-2, -2);
        appParams.leftMargin = dp(2);
        head.addView(expandAppView, appParams);
        expandAppView.setOnClickListener(v -> openCurrentTab());

        View spacer = new View(this);
        headSpacer = spacer;
        head.addView(spacer, new LinearLayout.LayoutParams(1, 1, 0f));

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

        miniBox = new LinearLayout(this);
        miniBox.setOrientation(LinearLayout.VERTICAL);
        headerBox.addView(miniBox, new LinearLayout.LayoutParams(-1, -2));

        expandBox = new LinearLayout(this);
        expandBox.setOrientation(LinearLayout.VERTICAL);

        // 整块内容放进 ScrollView：任务/想法再多也能滑到
        expandScroll = new ScrollView(this);
        expandScroll.setVerticalScrollBarEnabled(false);
        expandScroll.setClipToPadding(false);
        expandScroll.addView(expandBox, new ScrollView.LayoutParams(-1, -2));
        expandScroll.setVisibility(View.GONE);
        card.addView(expandScroll, new LinearLayout.LayoutParams(-1, -2));

        View line = new View(this);
        line.setBackgroundColor(UiPalette.hairline());
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, dp(1));
        lineParams.topMargin = dp(9);
        lineParams.bottomMargin = dp(8);
        expandBox.addView(line, lineParams);

        // 顶部分区切换：现在 / 白板 / done
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(-1, -2);
        tabParams.bottomMargin = dp(6);
        expandBox.addView(tabBar, tabParams);

        // slotBox 三个分区共用，其它「现在」专属区域包进 nowBox
        slotBox = new LinearLayout(this);
        slotBox.setOrientation(LinearLayout.VERTICAL);
        expandBox.addView(slotBox, new LinearLayout.LayoutParams(-1, -2));

        nowBox = new LinearLayout(this);
        nowBox.setOrientation(LinearLayout.VERTICAL);
        expandBox.addView(nowBox, new LinearLayout.LayoutParams(-1, -2));

        nowBox.addView(buildMusicRow());

        stepBox = new LinearLayout(this);
        stepBox.setOrientation(LinearLayout.VERTICAL);
        nowBox.addView(stepBox, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout ideaHead = new LinearLayout(this);
        ideaHead.setGravity(Gravity.CENTER_VERTICAL);
        ideaHead.addView(FormKit.text(this, "刚想到的", 11, UiPalette.MUTED, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        countView = FormKit.text(this, "", 11, UiPalette.ACCENT, true);
        ideaHead.addView(countView);
        nowBox.addView(ideaHead);

        ideaContainer = new LinearLayout(this);
        ideaContainer.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ideasParams = new LinearLayout.LayoutParams(-1, -2);
        ideasParams.topMargin = dp(2);
        nowBox.addView(ideaContainer, ideasParams);

        nowBox.addView(buildInputRow(), new LinearLayout.LayoutParams(-1, -2));

        // 已完成的想法收在最底部
        doneBox = new LinearLayout(this);
        doneBox.setOrientation(LinearLayout.VERTICAL);
        nowBox.addView(doneBox, new LinearLayout.LayoutParams(-1, -2));

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        // 全屏/沉浸模式下也允许顶到屏幕最上面，不被状态栏压住
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
        params.x = dp(16);
        params.y = dp(150);

        try {
            windowManager.addView(card, params);
            attached = true;
        } catch (RuntimeException ignored) {
            attached = false;
        }
        render();

        // 上次是贴边收起的，回到原来的边和高度
        edgeSide = prefs(this).getInt(KEY_EDGE, 0);
        final int savedY = prefs(this).getInt(KEY_EDGE_Y, params.y);
        if (edgeSide != 0) {
            card.post(() -> snapToEdge(edgeSide, savedY));
        }
    }

    /** 展开态里的陪伴音乐：点一下开/关当前选中的声音。 */
    private View buildMusicRow() {
        musicRowView = new LinearLayout(this);
        musicRowView.setOrientation(LinearLayout.HORIZONTAL);
        musicRowView.setGravity(Gravity.CENTER_VERTICAL);
        musicRowView.setPadding(dp(10), dp(7), dp(10), dp(7));
        musicRowView.setBackground(FormKit.round(this, UiPalette.SOFT, 10));

        musicLabel = FormKit.text(this, "", 12, UiPalette.INK, true);
        musicLabel.setSingleLine(true);
        musicRowView.addView(musicLabel, new LinearLayout.LayoutParams(0, -2, 1f));

        musicState = FormKit.text(this, "", 10, UiPalette.MUTED, true);
        LinearLayout.LayoutParams stateParams = new LinearLayout.LayoutParams(-2, -2);
        stateParams.leftMargin = dp(6);
        musicRowView.addView(musicState, stateParams);

        musicRowView.setOnClickListener(v -> toggleMusic());

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(8);
        musicRowView.setLayoutParams(rowParams);
        updateMusicButton();
        return musicRowView;
    }

    private void toggleMusic() {
        boolean play = !CompanionAudioService.isMusicPlaying(this);
        CompanionAudioService.setMusicPlaying(this, play);
        updateMusicButton();
        Intent intent = new Intent(this, CompanionAudioService.class)
                .setAction(CompanionAudioService.ACTION_SET_MUSIC)
                .putExtra(CompanionAudioService.EXTRA_MUSIC_PLAYING, play);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent);
            else startService(intent);
        } catch (RuntimeException e) {
            Toast.makeText(this, "没开起来，回 App 里点一下", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateMusicButton() {
        if (musicLabel == null) return;
        boolean playing = CompanionAudioService.isMusicPlaying(this);
        String sound = CompanionAudioService.soundName(CompanionAudioService.selectedSound(this));
        musicLabel.setText((playing ? "♫ " + sound + " 播放中" : "♫ " + sound));
        if (musicState != null) musicState.setText(playing ? "暂停" : "播放");
        if (musicRowView != null) {
            musicRowView.setBackground(FormKit.round(this,
                    playing ? UiPalette.ACCENT : UiPalette.SOFT, 10));
            musicLabel.setTextColor(playing ? 0xffffffff : UiPalette.INK);
            if (musicState != null) {
                musicState.setTextColor(playing ? 0xccffffff : UiPalette.MUTED);
            }
        }
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

    /**
     * 整块都能拖：收起态任意方向拖，展开态横向拖窗口、竖向留给列表滚动。
     * 用 onInterceptTouchEvent 才能在按钮/列表上也能拖起来。
     */
    private final class BubbleCard extends LinearLayout {
        private float downX;
        private float downY;
        private int startX;
        private int startY;
        private boolean dragging;
        private final int slop;

        BubbleCard(Context context) {
            super(context);
            slop = ViewConfiguration.get(context).getScaledTouchSlop();
        }

        /**
         * 收起态不接受子视图的「别拦我」请求：小窗里几乎每行都能点，
         * 那个可点区域会把拖动手势吞掉，导致只有边角能拖。
         * 展开态保持默认，竖向滑动继续交给 ScrollView。
         */
        @Override
        public void requestDisallowInterceptTouchEvent(boolean disallow) {
            if (expanded) super.requestDisallowInterceptTouchEvent(disallow);
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (params == null) return false;
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX();
                    downY = event.getRawY();
                    startX = params.x;
                    startY = params.y;
                    dragging = false;
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    float dx = event.getRawX() - downX;
                    float dy = event.getRawY() - downY;
                    float ax = Math.abs(dx);
                    float ay = Math.abs(dy);
                    // 展开态：只有明显横向才拖窗口，竖向留给 ScrollView
                    // 收起态：任一方向超过系统滑动阈值就开始拖，避免把点击误判成拖
                    boolean grab = expanded
                            ? (ax > slop * 2 && ax > ay * 1.5f)
                            : (ax > slop || ay > slop);
                    if (grab) {
                        dragging = true;
                        return true;
                    }
                    return false;
                }
                default:
                    return false;
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (params == null) return super.onTouchEvent(event);
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    if (dragging) {
                        params.x = startX + (int) (event.getRawX() - downX);
                        params.y = startY + (int) (event.getRawY() - downY);
                        updateLayout();
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        dragging = false;
                        settleAfterDrag();
                    } else if (!expanded) {
                        // 收起态点空白：展开
                        toggleExpand();
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    // ---------------------------------------------------------- 拖拽/贴边

    /** 松手后决定：贴边收起还是留在原地。 */
    private void settleAfterDrag() {
        if (card == null) return;
        int sw = screenWidth();
        int sh = screenHeight();
        int w = card.getWidth() > 0 ? card.getWidth() : dp(160);
        int h = card.getHeight() > 0 ? card.getHeight() : dp(56);

        if (params.x <= dp(8)) {
            if (expanded) toggleExpand();
            snapToEdge(-1, params.y);
            return;
        }
        if (params.x + w >= sw - dp(8)) {
            if (expanded) toggleExpand();
            snapToEdge(1, params.y);
            return;
        }
        params.x = Math.max(0, Math.min(params.x, sw - w));
        params.y = clampY(params.y, h, sh);
        edgeSide = 0;
        updateLayout();
        persistPos();
    }

    /** 藏成贴在屏幕边上的半个圆。 */
    private void snapToEdge(int side, int y) {
        if (card == null || windowManager == null) return;
        int sw = screenWidth();
        int sh = screenHeight();
        int w = dp(PEEK_W);
        int h = dp(PEEK_H);

        card.setVisibility(View.GONE);

        // 换边了要重建：圆角得朝屏幕内侧
        if (peekView != null && peekSide != side) {
            try {
                windowManager.removeView(peekView);
            } catch (RuntimeException ignored) { }
            peekView = null;
        }
        if (peekView == null) {
            peekView = buildPeek(side, w, h);
            peekParams = new WindowManager.LayoutParams(
                    w, h,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            peekParams.gravity = Gravity.TOP | Gravity.START;
            if (Build.VERSION.SDK_INT >= 28) {
                peekParams.layoutInDisplayCutoutMode =
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }
            peekParams.x = side < 0 ? 0 : sw - w;
            peekParams.y = clampY(y, h, sh);
            try {
                windowManager.addView(peekView, peekParams);
            } catch (RuntimeException ignored) {
                peekView = null;
                card.setVisibility(View.VISIBLE);
                return;
            }
        }
        edgeSide = side;
        peekSide = side;
        // 半个圆直接贴在屏幕边上，不留缝
        peekParams.x = side < 0 ? 0 : sw - w;
        peekParams.y = clampY(y, h, sh);
        try {
            windowManager.updateViewLayout(peekView, peekParams);
        } catch (RuntimeException ignored) { }
        updatePeek();
        persistPos();
    }

    /** 点半个圆 → 恢复成卡片，停在原来那条边上。 */
    private void restoreFromEdge() {
        int sw = screenWidth();
        int w = card != null && card.getWidth() > 0 ? card.getWidth() : dp(160);
        int x = edgeSide < 0 ? dp(14) : Math.max(dp(14), sw - w - dp(14));
        restoreFromEdgeAt(x, peekParams != null ? peekParams.y : params.y);
    }

    /** 把半圆还原成小窗，放到指定位置（从边缘拉出来时用）。 */
    private void restoreFromEdgeAt(int x, int y) {
        if (card == null) return;
        if (peekView != null && windowManager != null) {
            try {
                windowManager.removeView(peekView);
            } catch (RuntimeException ignored) { }
        }
        peekView = null;
        peekSide = 0;

        int sw = screenWidth();
        int sh = screenHeight();
        int w = card.getWidth() > 0 ? card.getWidth() : dp(160);
        int h = card.getHeight() > 0 ? card.getHeight() : dp(56);
        params.x = Math.max(0, Math.min(x, sw - w));
        params.y = clampY(y, h, sh);
        edgeSide = 0;
        card.setVisibility(View.VISIBLE);
        updateLayout();
        persistPos();
        render();
    }

    private LinearLayout buildPeek(int side, int w, int h) {
        LinearLayout shape = new LinearLayout(this);
        shape.setOrientation(LinearLayout.VERTICAL);
        shape.setGravity(Gravity.CENTER);
        shape.setBackground(peekBackground(side, h));
        shape.setElevation(dp(6));
        shape.setContentDescription("展开悬浮窗");

        View dot = new View(this);
        GradientDrawable dotShape = new GradientDrawable();
        dotShape.setShape(GradientDrawable.OVAL);
        dotShape.setColor(0xCCFFFFFF);
        dot.setBackground(dotShape);
        shape.addView(dot, new LinearLayout.LayoutParams(dp(5), dp(5)));

        attachPeekTouch(shape, h);
        return shape;
    }

    /** 只有朝屏幕内侧的两个角是圆的，贴墙那面是直边 —— 这才是个半圆。 */
    private GradientDrawable peekBackground(int side, int h) {
        GradientDrawable bg = new GradientDrawable();
        float r = h / 2f;
        bg.setColor(peekColor());
        if (side < 0) bg.setCornerRadii(new float[]{0, 0, r, r, r, r, 0, 0});
        else bg.setCornerRadii(new float[]{r, r, 0, 0, 0, 0, r, r});
        return bg;
    }

    /** 在跑就是绿的，超时变红，没任务淡绿（不给棕色）。 */
    private int peekColor() {
        FocusStore.TaskSlot main = FocusStore.getMain(this);
        boolean running = main != null && main.isRunning();
        if (running && main.targetSeconds > 0
                && (System.currentTimeMillis() - main.startedAt) / 1000 >= main.targetSeconds) {
            return (0xE6 << 24) | (PEEK_OVERTIME & 0x00FFFFFF);
        }
        if (!running) return (0x99 << 24) | (UiPalette.ACCENT & 0x00FFFFFF);
        return (0xE6 << 24) | (UiPalette.ACCENT & 0x00FFFFFF);
    }

    private void attachPeekTouch(final View circle, final int h) {
        final int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        circle.setOnTouchListener(new View.OnTouchListener() {
            private float downX;
            private float downY;
            private int startX;
            private int startY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (peekParams == null) return false;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = peekParams.x;
                        startY = peekParams.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE: {
                        int dx = (int) (event.getRawX() - downX);
                        int dy = (int) (event.getRawY() - downY);
                        if (!moved && Math.abs(dx) <= slop && Math.abs(dy) <= slop) return true;
                        moved = true;
                        // 横竖都能拖，拖离边缘再松手就还原成小窗
                        peekParams.x = startX + dx;
                        peekParams.y = clampY(startY + dy, h, screenHeight());
                        try {
                            windowManager.updateViewLayout(circle, peekParams);
                        } catch (RuntimeException ignored) { }
                        return true;
                    }
                    case MotionEvent.ACTION_UP: {
                        if (!moved) {
                            // 贴边点一下：直接展开大窗（进 App 只走放大镜）
                            restoreFromEdge();
                            if (!expanded) toggleExpand();
                            return true;
                        }
                        int sw = screenWidth();
                        int x = peekParams.x;
                        boolean pulledOut = peekSide < 0
                                ? x > dp(40)
                                : x < sw - dp(PEEK_W) - dp(40);
                        if (pulledOut) restoreFromEdgeAt(x, peekParams.y);
                        else snapToEdge(peekSide < 0 ? -1 : 1, peekParams.y);
                        persistPos();
                        return true;
                    }
                    default:
                        return false;
                }
            }
        });
    }

    /** 状态变了就换颜色（在跑 / 超时 / 空闲）。 */
    private void updatePeek() {
        if (peekView == null) return;
        try {
            peekView.setBackground(peekBackground(peekSide, dp(PEEK_H)));
        } catch (RuntimeException ignored) { }
    }

    private void persistPos() {
        SharedPreferences.Editor editor = prefs(this).edit().putInt(KEY_EDGE, edgeSide);
        editor.putInt(KEY_EDGE_Y, edgeSide != 0 && peekParams != null ? peekParams.y : params.y);
        editor.apply();
    }

    private int clampY(int y, int viewHeight, int screenH) {
        int min = 0;
        int max = Math.max(min, screenH - viewHeight);
        return Math.max(min, Math.min(y, max));
    }

    private int screenWidth() {
        if (windowManager != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                return windowManager.getCurrentWindowMetrics().getBounds().width();
            }
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            return metrics.widthPixels;
        }
        return getResources().getDisplayMetrics().widthPixels;
    }

    private int screenHeight() {
        if (windowManager != null) {
            if (Build.VERSION.SDK_INT >= 30) {
                return windowManager.getCurrentWindowMetrics().getBounds().height();
            }
            android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            return metrics.heightPixels;
        }
        return getResources().getDisplayMetrics().heightPixels;
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

        // 显示 3 个槽位的状态（主线 + 支线 1 + 支线 2）
        FocusStore.TaskSlot main = FocusStore.getMain(this);
        FocusStore.TaskSlot sub1 = FocusStore.getSub1(this);
        FocusStore.TaskSlot sub2 = FocusStore.getSub2(this);

        // 收起态：主线 + 支线都列出来，小屏也能看全
        titleView.setVisibility(View.GONE);
        elapsedView.setText(expanded && main.isRunning() ? elapsedText(main.startedAt) : "");

        miniBox.setVisibility(expanded ? View.GONE : View.VISIBLE);
        if (!expanded) fillMiniBox(main, sub1, sub2);

        expandScroll.setVisibility(expanded ? View.VISIBLE : View.GONE);
        collapseView.setVisibility(expanded ? View.VISIBLE : View.GONE);
        expandAppView.setVisibility(expanded ? View.VISIBLE : View.GONE);
        if (expanded) {
            fillTabBar();
            fillSlotStatus(); // 按顶部选中的分区渲染
            if (nowBox != null) {
                nowBox.setVisibility(boardTab == 0 ? View.VISIBLE : View.GONE);
            }
            if (boardTab == 0) {
                fillSteps();
                fillIdeas();
                applyInputMode();
                updateMusicButton();
            }
            // 高度跟内容走，最长不超过 300dp（跟以前的展开态差不多高）
            final int maxHeight = Math.min(dp(300), (int) (screenHeight() * 0.5f));
            expandScroll.post(() -> {
                int contentHeight = expandBox.getMeasuredHeight();
                if (contentHeight <= 0) return;
                android.view.ViewGroup.LayoutParams lp = expandScroll.getLayoutParams();
                lp.height = Math.min(contentHeight, maxHeight);
                expandScroll.setLayoutParams(lp);
                updateLayout();
            });
        }

        params.width = expanded ? dp(260) : WindowManager.LayoutParams.WRAP_CONTENT;
        // 贴边后展开，必须把窗口拉回屏幕内，否则右侧/底部会被裁掉
        if (expanded) {
            int sw = screenWidth();
            int sh = screenHeight();
            int w = dp(260);
            if (params.x + w > sw) params.x = Math.max(0, sw - w);
            if (params.x < 0) params.x = 0;
            int visibleH = Math.min(dp(300), (int) (sh * 0.5f));
            int maxY = Math.max(0, sh - visibleH - dp(8));
            if (params.y > maxY) params.y = maxY;
            if (params.y < 0) params.y = 0;
        }
        // 弹性占位只在展开态撑开，收起态必须缩成 1px，否则整条撑满屏幕
        if (headSpacer != null && headSpacer.getLayoutParams() instanceof LinearLayout.LayoutParams) {
            LinearLayout.LayoutParams spacerParams =
                    (LinearLayout.LayoutParams) headSpacer.getLayoutParams();
            spacerParams.width = expanded ? 0 : 1;
            spacerParams.weight = expanded ? 1f : 0f;
            headSpacer.setLayoutParams(spacerParams);
        }
        updateLayout();
        updatePeek();
    }

    /** 收起态：只列任务名，点一下展开大窗（小窗不放进 App 的按钮）。 */
    private void fillMiniBox(FocusStore.TaskSlot main, FocusStore.TaskSlot sub1,
                             FocusStore.TaskSlot sub2) {
        if (miniBox == null) return;
        miniBox.removeAllViews();

        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        boolean any = false;
        for (FocusStore.TaskSlot slot : new FocusStore.TaskSlot[]{main, sub1, sub2}) {
            if (slot.isEmpty()) continue;
            any = true;
            TextView name = FormKit.text(this, slot.title, 12, UiPalette.INK, true);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setMaxWidth(dp(170));
            names.addView(name, new LinearLayout.LayoutParams(-2, -2));
        }
        if (!any) {
            names.addView(FormKit.text(this, "还没挑任务", 11, UiPalette.MUTED, false));
        }
        names.setContentDescription("展开任务列表");
        names.setPadding(0, dp(2), 0, dp(2));
        names.setOnClickListener(v -> toggleExpand());
        miniBox.addView(names, new LinearLayout.LayoutParams(-2, -2));
    }

    /** 统一样式的方框，点一下执行 onClick。 */
    private TextView buildCheckBox(String description, View.OnClickListener onClick) {
        return buildCheckBox(description, false, onClick);
    }

    /** 带完成态的方框：打完勾就是实心主色 + 对勾。 */
    private TextView buildCheckBox(String description, boolean done, View.OnClickListener onClick) {
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(4));
        shape.setColor(done ? UiPalette.ACCENT : Color.TRANSPARENT);
        if (!done) shape.setStroke(dp(1), UiPalette.DISABLED);
        TextView box = FormKit.text(this, done ? "✓" : "", 9,
                done ? Color.WHITE : UiPalette.DISABLED, true);
        box.setGravity(Gravity.CENTER);
        box.setBackground(shape);
        box.setContentDescription(description);
        box.setOnClickListener(onClick);
        return box;
    }

    /** 槽位任务的方框，点一下算完成（实时读槽位，防连点错乱）。 */
    private TextView buildSlotCheckBox(final FocusStore.TaskSlot slot) {
        return buildCheckBox("完成 " + (slot.title.isEmpty() ? "这个任务" : slot.title), v -> {
            FocusStore.TaskSlot currentSlot = slotOf(slot.slotKey);
            if (currentSlot != null && !currentSlot.isEmpty()) {
                completeSlot(slot.slotKey);
            }
        });
    }

    /** 在悬浮窗里直接勾掉一个槽位。 */
    private void completeSlot(String slotKey) {
        FocusStore.TaskSlot slot = slotOf(slotKey);
        if (slot == null || slot.isEmpty()) return;

        long now = System.currentTimeMillis();
        ReminderRecord.finish(this, java.util.UUID.randomUUID().toString(), slot.title,
                ReminderRecord.KIND_DONE, slot.startedAt > 0 ? slot.startedAt : now, now,
                "悬浮窗完成", ReminderSettings.MODULE_TASK);

        if (slot.ruleId >= 0) {
            long dayStart = ReminderRecord.dayStart(now);
            ReminderSettings.Rule rule = ReminderSettings.ruleById(this, slot.ruleId);
            if (rule != null) {
                for (ReminderSettings.Rule sub : Subtasks.of(this, slot.ruleId)) {
                    if (!Subtasks.doneToday(this, slot.ruleId, sub.title, dayStart)) {
                        Subtasks.toggleQuiet(this, slot.ruleId, sub.title, false, dayStart);
                    }
                }
                ReminderSettings.markCompleted(this, slot.ruleId, Math.max(1, rule.dailyTarget));
            }
        }

        FocusStore.clearSlot(this, slotKey);
        Toast.makeText(this, "「" + slot.title + "」做完了", Toast.LENGTH_SHORT).show();
        render();
    }

    private FocusStore.TaskSlot slotOf(String slotKey) {
        if (FocusStore.KEY_MAIN.equals(slotKey)) return FocusStore.getMain(this);
        if (FocusStore.KEY_SUB1.equals(slotKey)) return FocusStore.getSub1(this);
        return FocusStore.getSub2(this);
    }

    /** 展开态任务区：槽位任务 + 今日待办 + 微习惯，可滑动，勾掉即消失。 */
    private void fillSlotStatus() {
        if (slotBox == null) return;
        slotBox.removeAllViews();
        if (boardTab == 1) {
            fillTaskBoard();
            return;
        }
        if (boardTab == 2) {
            fillBoard();
            return;
        }
        if (boardTab == 3) {
            fillDoneList();
            return;
        }

        final LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        FocusStore.TaskSlot main = FocusStore.getMain(this);
        FocusStore.TaskSlot sub1 = FocusStore.getSub1(this);
        FocusStore.TaskSlot sub2 = FocusStore.getSub2(this);

        // 只列「现在」页的槽位任务（主线 + 支线），任务页的不在这里重复
        if (!main.isEmpty()) content.addView(buildSlotStatusRow("主线", main));
        if (!sub1.isEmpty()) content.addView(buildSlotStatusRow("支线 1", sub1));
        if (!sub2.isEmpty()) content.addView(buildSlotStatusRow("支线 2", sub2));

        if (content.getChildCount() == 0) {
            TextView empty = FormKit.text(this, "还没选任务", 11, UiPalette.MUTED, false);
            empty.setPadding(0, dp(4), 0, dp(4));
            content.addView(empty);
        }

        // 外层 expandScroll 统一滚动，这里不再套一层
        slotBox.addView(content, new LinearLayout.LayoutParams(-1, -2));
    }

    /** 顶部三个切换片。 */
    private void fillTabBar() {
        if (tabBar == null) return;
        tabBar.removeAllViews();
        String[] names = {"现在", "任务", "白板", "done"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            boolean active = boardTab == i;
            TextView chip = FormKit.text(this, names[i], 10,
                    active ? Color.WHITE : UiPalette.MUTED, true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(6), dp(4), dp(6), dp(4));
            chip.setBackground(FormKit.round(this,
                    active ? UiPalette.ACCENT : UiPalette.PALE, 9));
            chip.setContentDescription("切换到" + names[i]);
            chip.setOnClickListener(v -> {
                if (boardTab == index) return;
                boardTab = index;
                render();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i > 0) params.leftMargin = dp(4);
            tabBar.addView(chip, params);
        }
    }

    /** 任务分区：任务页的今日任务 + 微习惯，能直接勾、直接加。 */
    private void fillTaskBoard() {
        long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());

        List<ReminderSettings.Rule> pending = new ArrayList<>();
        for (ReminderSettings.Rule task : Tasks.today(this)) {
            if (ReminderRecord.completedOn(this, ReminderSettings.MODULE_TASK, task.title, dayStart)) continue;
            pending.add(task);
        }
        List<Habits.Habit> habits = new ArrayList<>();
        for (Habits.Habit habit : Habits.todayScheduled(this)) {
            if (!Habits.isCompletedToday(this, habit.id, dayStart)) habits.add(habit);
        }

        if (pending.isEmpty() && habits.isEmpty()) {
            TextView empty = FormKit.text(this, "今天都做完了", 11, UiPalette.MUTED, false);
            empty.setPadding(0, dp(4), 0, dp(4));
            slotBox.addView(empty);
        }
        for (final ReminderSettings.Rule task : pending) {
            slotBox.addView(buildTaskBoardRow(task.title, v -> completeTaskRule(task)));
        }
        for (final Habits.Habit habit : habits) {
            slotBox.addView(buildTaskBoardRow(habit.title, v -> completeHabit(habit)));
        }

        slotBox.addView(buildTaskInputRow(), new LinearLayout.LayoutParams(-1, -2));
    }

    private View buildTaskBoardRow(String title, View.OnClickListener onCheck) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        row.addView(buildCheckBox("完成 " + title, onCheck),
                new LinearLayout.LayoutParams(dp(15), dp(15)));

        TextView name = FormKit.text(this, title, 11, UiPalette.INK, false);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = dp(7);
        row.addView(name, nameParams);
        return row;
    }

    /** 悬浮窗勾掉一条任务，顺带把子任务和它占的槽位一起处理。 */
    private void completeTaskRule(ReminderSettings.Rule task) {
        long now = System.currentTimeMillis();
        long dayStart = ReminderRecord.dayStart(now);
        if (ReminderRecord.completedOn(this, ReminderSettings.MODULE_TASK, task.title, dayStart)) return;

        for (ReminderSettings.Rule sub : Subtasks.of(this, task.id)) {
            if (!Subtasks.doneToday(this, task.id, sub.title, dayStart)) {
                Subtasks.toggleQuiet(this, task.id, sub.title, false, dayStart);
            }
        }
        ReminderRecord.finish(this, java.util.UUID.randomUUID().toString(), task.title,
                ReminderRecord.KIND_DONE, now, now, "悬浮窗完成", ReminderSettings.MODULE_TASK);
        ReminderSettings.markCompleted(this, task.id, Math.max(1, task.dailyTarget));

        for (String key : new String[]{FocusStore.KEY_MAIN, FocusStore.KEY_SUB1, FocusStore.KEY_SUB2}) {
            FocusStore.TaskSlot slot = slotOf(key);
            if (!slot.isEmpty() && slot.ruleId == task.id) {
                FocusStore.clearSlot(this, key);
            }
        }
        render();
    }

    /** 悬浮窗给微习惯打卡。 */
    private void completeHabit(Habits.Habit habit) {
        long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
        if (Habits.isCompletedToday(this, habit.id, dayStart)) return;
        Habits.toggleCompletion(this, habit.id, dayStart, true);
        render();
    }

    /** 任务分区底部的一行输入，直接建一条今日任务。 */
    private View buildTaskInputRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(6);
        row.setLayoutParams(rowParams);

        taskInput = new EditText(this);
        taskInput.setHint("加一个任务");
        taskInput.setTextSize(12);
        taskInput.setSingleLine(true);
        taskInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        taskInput.setInputType(InputType.TYPE_CLASS_TEXT);
        taskInput.setTextColor(UiPalette.INK);
        taskInput.setHintTextColor(UiPalette.MUTED);
        taskInput.setPadding(dp(8), dp(6), dp(8), dp(6));
        taskInput.setBackground(UiPalette.inputBackground(this, UiPalette.PALE, 10));
        taskInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) grabWindowFocus();
            else dropWindowFocus();
        });
        taskInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitTaskItem();
                return true;
            }
            return false;
        });
        row.addView(taskInput, new LinearLayout.LayoutParams(0, dp(34), 1f));

        TextView plus = FormKit.text(this, "＋", 15, Color.WHITE, true);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(FormKit.round(this, UiPalette.ACCENT, 9));
        plus.setOnClickListener(v -> submitTaskItem());
        LinearLayout.LayoutParams plusParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        plusParams.leftMargin = dp(6);
        row.addView(plus, plusParams);
        return row;
    }

    private void submitTaskItem() {
        String text = taskInput == null ? "" : taskInput.getText().toString().trim();
        if (text.isEmpty()) {
            dropWindowFocus();
            return;
        }
        ArrayList<Integer> points = new ArrayList<>();
        points.add(9 * 3600);
        ReminderSettings.Rule rule = ReminderSettings.Rule.withSpecificTimes(
                        ReminderSettings.nextRuleId(this), text, 9 * 60, 9 * 60, 24 * 3600,
                        points, 1, true, false)
                .withModule(ReminderSettings.MODULE_TASK);
        ReminderSettings.upsertRule(this, rule);
        taskInput.setText("");
        dropWindowFocus();
        render();
    }

    /** 白板：跟白板页同一份数据（module=board 的规则），点一下勾掉。 */
    private void fillBoard() {
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .format(new java.util.Date());
        long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());

        List<ReminderSettings.Rule> pending = new ArrayList<>();
        List<ReminderSettings.Rule> finished = new ArrayList<>();
        for (ReminderSettings.Rule rule : ReminderSettings.moduleRules(this, ReminderSettings.MODULE_BOARD)) {
            if (!today.equals(rule.deckId)) continue;
            if (ReminderRecord.completedOn(this, ReminderSettings.MODULE_BOARD, rule.title, dayStart)) {
                finished.add(rule);
            } else {
                pending.add(rule);
            }
        }

        if (pending.isEmpty() && finished.isEmpty()) {
            TextView empty = FormKit.text(this, "白板是空的", 11, UiPalette.MUTED, false);
            empty.setPadding(0, dp(4), 0, dp(4));
            slotBox.addView(empty);
        } else {
            if (pending.isEmpty()) {
                TextView doneAll = FormKit.text(this, "都做完了", 11, UiPalette.MUTED, false);
                doneAll.setPadding(0, dp(4), 0, dp(4));
                slotBox.addView(doneAll);
            }
            for (ReminderSettings.Rule rule : pending) {
                slotBox.addView(buildBoardRow(rule, false));
            }

            // 已完成收进底部折叠，不占主列表
            if (!finished.isEmpty()) {
                LinearLayout head = new LinearLayout(this);
                head.setGravity(Gravity.CENTER_VERTICAL);
                head.setPadding(0, dp(4), 0, dp(2));
                head.addView(FormKit.text(this,
                        (boardDoneOpen ? "已完成 ⌃" : "已完成 ⌄"),
                        10, UiPalette.MUTED, true), new LinearLayout.LayoutParams(0, -2, 1f));
                head.setOnClickListener(v -> {
                    boardDoneOpen = !boardDoneOpen;
                    render();
                });
                slotBox.addView(head);
                if (boardDoneOpen) {
                    for (ReminderSettings.Rule rule : finished) {
                        slotBox.addView(buildBoardRow(rule, true));
                    }
                }
            }
        }

        slotBox.addView(buildBoardInputRow(), new LinearLayout.LayoutParams(-1, -2));
    }

    private View buildBoardRow(final ReminderSettings.Rule rule, final boolean done) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        row.addView(buildCheckBox("勾掉 " + rule.title, done, v -> toggleBoardItem(rule)),
                new LinearLayout.LayoutParams(dp(15), dp(15)));

        TextView name = FormKit.text(this, rule.title, 11,
                done ? UiPalette.MUTED : UiPalette.INK, false);
        name.setMaxLines(1);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = dp(7);
        row.addView(name, nameParams);
        return row;
    }

    /** 白板分区底部的一行输入，直接往今日白板加一条。 */
    private View buildBoardInputRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(6);
        row.setLayoutParams(rowParams);

        boardInput = new EditText(this);
        boardInput.setHint("往白板记一条");
        boardInput.setTextSize(12);
        boardInput.setSingleLine(true);
        boardInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        boardInput.setInputType(InputType.TYPE_CLASS_TEXT);
        boardInput.setTextColor(UiPalette.INK);
        boardInput.setHintTextColor(UiPalette.MUTED);
        boardInput.setPadding(dp(8), dp(6), dp(8), dp(6));
        boardInput.setBackground(UiPalette.inputBackground(this, UiPalette.PALE, 10));
        boardInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) grabWindowFocus();
            else dropWindowFocus();
        });
        boardInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitBoardItem();
                return true;
            }
            return false;
        });
        row.addView(boardInput, new LinearLayout.LayoutParams(0, dp(34), 1f));

        TextView plus = FormKit.text(this, "＋", 15, Color.WHITE, true);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(FormKit.round(this, UiPalette.ACCENT, 9));
        plus.setOnClickListener(v -> submitBoardItem());
        LinearLayout.LayoutParams plusParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        plusParams.leftMargin = dp(6);
        row.addView(plus, plusParams);
        return row;
    }

    private void submitBoardItem() {
        String text = boardInput == null ? "" : boardInput.getText().toString().trim();
        if (text.isEmpty()) {
            dropWindowFocus();
            return;
        }
        String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                .format(new java.util.Date());
        ArrayList<Integer> points = new ArrayList<>();
        points.add(9 * 3600);
        ReminderSettings.Rule rule = ReminderSettings.Rule.withSpecificTimes(
                        ReminderSettings.nextRuleId(this), text, 9 * 60, 9 * 60, 24 * 3600,
                        points, 1, true, false)
                .withModule(ReminderSettings.MODULE_BOARD)
                .withDeck(today, false);
        ReminderSettings.upsertRule(this, rule);
        boardInput.setText("");
        dropWindowFocus();
        render();
    }

    /** 在悬浮窗勾掉/取消一条白板条目。 */
    private void toggleBoardItem(ReminderSettings.Rule rule) {
        long now = System.currentTimeMillis();
        long dayStart = ReminderRecord.dayStart(now);
        boolean done = ReminderRecord.completedOn(this,
                ReminderSettings.MODULE_BOARD, rule.title, dayStart);
        if (done) {
            ReminderRecord.deleteCompletion(this, ReminderSettings.MODULE_BOARD, rule.title, dayStart);
            ReminderSettings.unmarkCompleted(this, rule.id);
        } else {
            for (ReminderSettings.Rule sub : Subtasks.of(this, rule.id)) {
                if (!Subtasks.doneToday(this, rule.id, sub.title, dayStart)) {
                    Subtasks.toggleQuiet(this, rule.id, sub.title, false, dayStart);
                }
            }
            ReminderRecord.finish(this, java.util.UUID.randomUUID().toString(), rule.title,
                    ReminderRecord.KIND_DONE, now, now, "白板勾选", ReminderSettings.MODULE_BOARD);
            ReminderSettings.markCompleted(this, rule.id, 1);
        }
        render();
    }

    /** done：今天做成的事，只读。 */
    private void fillDoneList() {
        List<String> lines = DoneStore.load(this);
        if (lines.isEmpty()) {
            TextView empty = FormKit.text(this, "今天还没记录", 11, UiPalette.MUTED, false);
            empty.setPadding(0, dp(4), 0, dp(4));
            slotBox.addView(empty);
        } else {
            for (String line : lines) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(3), 0, dp(3));

                TextView tick = FormKit.text(this, "✓", 9, UiPalette.ACCENT, true);
                tick.setGravity(Gravity.CENTER);
                row.addView(tick, new LinearLayout.LayoutParams(dp(14), -2));

                TextView name = FormKit.text(this, line, 11, UiPalette.INK, false);
                name.setMaxLines(2);
                name.setEllipsize(TextUtils.TruncateAt.END);
                row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
                slotBox.addView(row);
            }
        }
        slotBox.addView(buildDoneInputRow(), new LinearLayout.LayoutParams(-1, -2));
    }

    /** done 分区底部的一行输入：直接在里面记一条。 */
    private View buildDoneInputRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.topMargin = dp(6);
        row.setLayoutParams(rowParams);

        doneInput = new EditText(this);
        doneInput.setHint("今天做成了什么");
        doneInput.setTextSize(12);
        doneInput.setSingleLine(true);
        doneInput.setMaxLines(1);
        doneInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        doneInput.setInputType(InputType.TYPE_CLASS_TEXT);
        doneInput.setTextColor(UiPalette.INK);
        doneInput.setHintTextColor(UiPalette.MUTED);
        doneInput.setPadding(dp(8), dp(6), dp(8), dp(6));
        doneInput.setBackground(UiPalette.inputBackground(this, UiPalette.PALE, 10));
        doneInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) grabWindowFocus();
            else dropWindowFocus();
        });
        doneInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitDone();
                return true;
            }
            return false;
        });
        row.addView(doneInput, new LinearLayout.LayoutParams(0, dp(34), 1f));

        TextView plus = FormKit.text(this, "＋", 15, Color.WHITE, true);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(FormKit.round(this, UiPalette.ACCENT, 9));
        plus.setOnClickListener(v -> submitDone());
        LinearLayout.LayoutParams plusParams = new LinearLayout.LayoutParams(dp(34), dp(34));
        plusParams.leftMargin = dp(6);
        row.addView(plus, plusParams);
        return row;
    }

    private void submitDone() {
        String text = doneInput == null ? "" : doneInput.getText().toString().trim();
        if (text.isEmpty()) {
            dropWindowFocus();
            return;
        }
        List<String> lines = DoneStore.load(this);
        lines.add(text);
        DoneStore.save(this, lines);
        doneInput.setText("");
        dropWindowFocus();
        render();
    }

    private View buildSlotStatusRow(String label, final FocusStore.TaskSlot slot) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(3), 0, dp(3));

        row.addView(buildSlotCheckBox(slot), new LinearLayout.LayoutParams(dp(16), dp(16)));

        TextView labelView = FormKit.text(this, label, 10, UiPalette.MUTED, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.leftMargin = dp(6);
        row.addView(labelView, labelParams);

        TextView titleView = FormKit.text(this, slot.title, 11,
                slot.isRunning() ? UiPalette.ACCENT : UiPalette.INK, true);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, -2, 1f);
        titleParams.leftMargin = dp(4);
        row.addView(titleView, titleParams);

        TextView elapsedView = FormKit.text(this,
                slot.isRunning() ? elapsedText(slot.startedAt) : "",
                9, UiPalette.MUTED, false);
        row.addView(elapsedView);

        row.setOnClickListener(v -> completeSlot(slot.slotKey));
        return row;
    }

    private void fillIdeas() {
        if (ideaContainer == null) return;
        ideaContainer.removeAllViews();
        if (doneBox != null) doneBox.removeAllViews();

        List<FocusStore.Idea> ideas = FocusStore.ideas(this);
        long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());

        List<FocusStore.Idea> pending = new ArrayList<>();
        List<FocusStore.Idea> finished = new ArrayList<>();
        for (FocusStore.Idea idea : ideas) {
            if (ReminderRecord.completedOn(this, ReminderSettings.MODULE_TASK,
                    ideaTitle(idea), dayStart)) {
                finished.add(idea);
            } else {
                pending.add(idea);
            }
        }
        countView.setText(pending.isEmpty() ? "" : String.valueOf(pending.size()));

        if (ideas.isEmpty()) {
            TextView empty = FormKit.text(this, "还没记东西", 11, UiPalette.MUTED, false);
            empty.setPadding(0, dp(5), 0, dp(3));
            ideaContainer.addView(empty);
        } else if (pending.isEmpty()) {
            TextView empty = FormKit.text(this, "都完成了", 11, UiPalette.MUTED, false);
            empty.setPadding(0, dp(5), 0, dp(3));
            ideaContainer.addView(empty);
        } else {
            for (FocusStore.Idea idea : pending) {
                ideaContainer.addView(buildIdeaRow(idea));
            }
        }

        // 已完成的想法移到底部折叠区
        if (doneBox == null || finished.isEmpty()) return;
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, dp(4), 0, dp(2));
        head.addView(FormKit.text(this,
                (doneIdeasOpen ? "已完成 ⌃" : "已完成 ⌄"),
                10, UiPalette.MUTED, true), new LinearLayout.LayoutParams(0, -2, 1f));
        head.setOnClickListener(v -> {
            doneIdeasOpen = !doneIdeasOpen;
            fillIdeas();
        });
        doneBox.addView(head);
        if (doneIdeasOpen) {
            for (FocusStore.Idea idea : finished) {
                doneBox.addView(buildIdeaRow(idea));
            }
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
                        Subtasks.toggleQuiet(this, ruleId, sub.title, false, dayStart);
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

    /** done 输入框的引用。 */
    private EditText doneInput;
    /** 白板输入框的引用。 */
    private EditText boardInput;
    /** 任务分区输入框的引用。 */
    private EditText taskInput;
    /** 白板已完成区是否展开。 */
    private boolean boardDoneOpen;

    /** 放大键：跟随当前 tab 进对应页面。 */
    private void openCurrentTab() {
        Intent intent;
        if (boardTab == 1) {
            intent = new Intent(this, TaskActivity.class);
        } else if (boardTab == 2) {
            intent = new Intent(this, TaskActivity.class)
                    .putExtra("module", ReminderSettings.MODULE_BOARD);
        } else if (boardTab == 3) {
            intent = new Intent(this, DoneActivity.class);
        } else {
            intent = new Intent(this, FocusActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
        } catch (RuntimeException e) {
            try {
                PendingIntent.getActivity(this, 3, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE).send();
            } catch (Exception ignored) { }
        }
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
        if (peekView != null && windowManager != null) {
            try {
                windowManager.removeView(peekView);
            } catch (RuntimeException ignored) { }
        }
        peekView = null;
        peekSide = 0;
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
