package io.iftech.healthreminder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 今日任务卡顶部的一行状态条：
 * 左侧小环形进度（今日完成 / 目标）+ 右侧嫩芽连胜。
 * 纯净白卡上的线条风格，不画自己的背景。
 */
final class GameHeroView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();

    private int completed;
    private int total;
    private int streak;
    private int level;

    private ValueAnimator pulse;
    private float phase;

    GameHeroView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.LEFT);
    }

    void setData(int completed, int total, int streak, int level) {
        this.completed = Math.max(0, completed);
        this.total = Math.max(0, total);
        this.streak = Math.max(0, streak);
        this.level = Math.max(0, Math.min(5, level));
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(2600);
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

        float cy = h / 2f;
        float ringR = Math.min(h / 2f - dp(6), dp(22));
        float cx = dp(4) + ringR;
        float ringStroke = dp(5);

        // 轨道：加深的浅绿
        stroke.setStrokeWidth(ringStroke);
        stroke.setColor(0xffddede3);
        canvas.drawCircle(cx, cy, ringR, stroke);

        // 进度弧：主绿
        float progress = total > 0 ? Math.min(1f, completed / (float) total) : 0f;
        if (progress > 0f) {
            stroke.setColor(UiPalette.ACCENT);
            arc.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
            canvas.drawArc(arc, -90f, 360f * progress, false, stroke);
        }

        // 环心数字：已完成/总数
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        text.setColor(UiPalette.INK);
        text.setTextSize(dp(13));
        canvas.drawText(total > 0 ? (completed + "/" + total) : "0", cx, cy + dp(4.5f), text);

        // 右侧：嫩芽 + 连胜天数
        float x = cx + ringR + dp(14);
        if (x > w - dp(40)) return;

        drawSprout(canvas, x + dp(9), cy, dp(9), UiPalette.ACCENT);

        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        text.setColor(UiPalette.INK);
        text.setTextSize(dp(15));
        String streakText = streak + " 天";
        canvas.drawText(streakText, x + dp(24), cy + dp(5), text);

        text.setTypeface(android.graphics.Typeface.DEFAULT);
        text.setColor(UiPalette.MUTED);
        text.setTextSize(dp(11));
        canvas.drawText("连续坚持", x + dp(24) + text.measureText(streakText) + dp(4), cy + dp(4.5f), text);
    }

    /** 小嫩芽：等级越高叶子越多、越高。 */
    private void drawSprout(Canvas canvas, float cx, float cy, float size, int color) {
        stroke.setColor(color);
        stroke.setStrokeWidth(dp(1.6f));
        float baseY = cy + size * 0.85f;
        float stemTop = cy - size * (0.15f + level * 0.06f);

        canvas.drawLine(cx, baseY, cx, stemTop, stroke);

        int leaves = Math.min(3, Math.max(1, level));
        for (int i = 0; i < leaves; i++) {
            float y = stemTop + size * (0.22f + i * 0.3f);
            float span = size * (0.80f - i * 0.12f);
            android.graphics.Path p = new android.graphics.Path();
            p.moveTo(cx, y);
            p.cubicTo(cx - span * 0.6f, y - span * 0.7f,
                    cx - span, y + span * 0.1f, cx - span * 0.85f, y + span * 0.45f);
            p.cubicTo(cx - span * 0.4f, y + span * 0.4f, cx - span * 0.1f, y + span * 0.2f, cx, y);
            canvas.drawPath(p, stroke);

            android.graphics.Path mirror = new android.graphics.Path();
            mirror.moveTo(cx, y);
            mirror.cubicTo(cx + span * 0.6f, y - span * 0.7f,
                    cx + span, y + span * 0.1f, cx + span * 0.85f, y + span * 0.45f);
            mirror.cubicTo(cx + span * 0.4f, y + span * 0.4f, cx + span * 0.1f, y + span * 0.2f, cx, y);
            canvas.drawPath(mirror, stroke);
        }

        if (level >= 4) {
            fill.setStyle(Paint.Style.FILL);
            fill.setColor(color);
            canvas.drawCircle(cx, stemTop - size * 0.1f, size * 0.22f, fill);
        }
    }
}
