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
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 悬浮条：退出 App 后仍浮在屏幕上的一个半透明长条，只显示「正在做什么 · 多久」。
 *
 * 用前台服务保活，通知压到最低优先级；点条子回「现在」页，拖动可换位置，长按收起。
 */
public final class FocusBubbleService extends Service {
    private static final String PREFS = "focus_bubble";
    private static final String KEY_ON = "on";
    private static final String CHANNEL = "focus_bubble";
    private static final int NOTICE_ID = 4301;
    /** 长按多久算「收起」，避免和拖动/点按打架。 */
    private static final long LONG_PRESS_MS = 700;

    private WindowManager windowManager;
    private WindowManager.LayoutParams params;
    private LinearLayout card;
    private TextView titleView;
    private TextView elapsedView;
    private boolean attached;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refresher = new Runnable() {
        @Override
        public void run() {
            refresh();
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

    /** 主任务变了就踢一下服务，让条子立刻跟着改，不用等 30 秒。 */
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
        handler.post(refresher);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        refresh();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(refresher);
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
        card.setPadding(dp(14), dp(9), dp(16), dp(10));
        card.setElevation(dp(8));

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
        card.addView(head);

        titleView = FormKit.text(this, "", 13, UiPalette.INK, true);
        titleView.setMaxLines(1);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        titleView.setMaxWidth(dp(210));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-2, -2);
        titleParams.topMargin = dp(2);
        card.addView(titleView, titleParams);

        card.setMinimumWidth(dp(120));

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(16);
        params.y = dp(160);

        attachTouch();
        try {
            windowManager.addView(card, params);
            attached = true;
        } catch (RuntimeException ignored) {
            attached = false;
        }
        refresh();
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
                        if (windowManager != null && attached) {
                            try {
                                windowManager.updateViewLayout(card, params);
                            } catch (RuntimeException ignored) { }
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!moved) {
                            if (System.currentTimeMillis() - downAt >= LONG_PRESS_MS) {
                                collapse();
                            } else {
                                openFocus();
                            }
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void openFocus() {
        Intent intent = new Intent(this, FocusActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(intent);
        } catch (RuntimeException ignored) { }
    }

    /** 长按收起：关开关并停服务，下次在「现在」页再打开。 */
    private void collapse() {
        setOn(this, false);
        android.widget.Toast.makeText(this, "悬浮条已收起", android.widget.Toast.LENGTH_SHORT).show();
        stopSelf();
    }

    private void refresh() {
        if (titleView == null) return;
        String title = FocusStore.currentTitle(this);
        if (title.isEmpty()) {
            titleView.setText("点一下，挑一件现在做的事");
            elapsedView.setText("");
            return;
        }
        titleView.setText(title);
        elapsedView.setText(elapsedText(FocusStore.currentStartedAt(this)));
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
