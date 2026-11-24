package io.iftech.healthreminder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 宝箱关卡轨道：7 / 21 / 66 天三个节点横向排列。
 * 已达成的节点点亮，当前目标节点脉动，未达成的灰暗。
 */
final class ChestTrackView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int streak;
    private final int[] milestones = GameSettings.MILESTONES;
    private final String[] icons = GameSettings.CHEST_ICONS;

    private ValueAnimator pulse;
    private float phase;

    ChestTrackView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        line.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.CENTER);
    }

    void setStreak(int streak) {
        this.streak = Math.max(0, streak);
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(1800);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new LinearInterpolator());
        pulse.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        pulse.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (pulse != null) pulse.cancel();
        super.onDetachedFromWindow();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float pad = dp(14);
        float nodeR = dp(20);
        float cy = dp(42);
        int n = milestones.length;
        if (n == 0) return;

        // 当前目标索引：第一个未达成的
        int currentIdx = -1;
        for (int i = 0; i < n; i++) {
            if (streak < milestones[i]) {
                currentIdx = i;
                break;
            }
        }

        float startX = pad + nodeR;
        float endX = w - pad - nodeR;
        float step = n > 1 ? (endX - startX) / (n - 1) : 0f;

        // ---- 连线 ----
        for (int i = 0; i < n - 1; i++) {
            float x1 = startX + step * i;
            float x2 = startX + step * (i + 1);
            boolean reached = streak >= milestones[i];
            line.setStrokeWidth(reached ? dp(4) : dp(2));
            line.setColor(reached ? UiPalette.ACCENT : withAlpha(UiPalette.BORDER, 200));
            canvas.drawLine(x1, cy, x2, cy, line);
        }

        // ---- 节点 ----
        for (int i = 0; i < n; i++) {
            float cx = startX + step * i;
            boolean reached = streak >= milestones[i];

            // 当前目标：脉动外圈
            if (i == currentIdx) {
                float p = (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2));
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(withAlpha(UiPalette.ACCENT, (int) (30 + 40 * p)));
                canvas.drawCircle(cx, cy, nodeR + dp(4) + dp(4) * p, fill);
            }

            // 圆底
            fill.setStyle(Paint.Style.FILL);
            if (reached) {
                fill.setColor(UiPalette.ACCENT);
            } else if (i == currentIdx) {
                fill.setColor(withAlpha(UiPalette.ACCENT, 46));
            } else {
                fill.setColor(withAlpha(UiPalette.MUTED, 26));
            }
            canvas.drawCircle(cx, cy, nodeR, fill);

            // 圆环描边
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(dp(2));
            line.setColor(reached ? UiPalette.ACCENT
                    : (i == currentIdx ? UiPalette.ACCENT : withAlpha(UiPalette.MUTED, 70)));
            canvas.drawCircle(cx, cy, nodeR, line);
            line.setStyle(Paint.Style.STROKE);

            // 图标
            text.setTextSize(dp(18));
            if (reached) {
                text.setAlpha(255);
            } else {
                text.setAlpha(i == currentIdx ? 255 : 90);
            }
            canvas.drawText(icons[Math.min(i, icons.length - 1)], cx, cy + dp(6), text);
            text.setAlpha(255);

            // 天数标签
            text.setTextSize(dp(11));
            text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            text.setColor(reached ? UiPalette.ACCENT : UiPalette.MUTED);
            canvas.drawText(milestones[i] + "天", cx, cy + nodeR + dp(16), text);
            text.setTypeface(android.graphics.Typeface.DEFAULT);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | ((alpha & 0xff) << 24);
    }
}
