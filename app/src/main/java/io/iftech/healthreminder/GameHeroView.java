package io.iftech.healthreminder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 今日任务卡的状态条：大号环形进度（今日完成 / 目标）+ 连胜天数。
 * 豆芽吉祥物由 SproutMascotView 单独放在旁边，这里不再画嫩芽。
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
        float ringR = Math.min(h / 2f - dp(2), dp(45)); // 直径 ≈ 90dp
        float cx = ringR + dp(2);
        float ringStroke = dp(8);

        // 轨道：加深浅绿
        stroke.setStrokeWidth(ringStroke);
        stroke.setColor(0xffddede3);
        canvas.drawCircle(cx, cy, ringR, stroke);

        // 进度弧：实心主绿
        float progress = total > 0 ? Math.min(1f, completed / (float) total) : 0f;
        if (progress > 0f) {
            stroke.setColor(0xff4caf7d);
            arc.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
            canvas.drawArc(arc, -90f, 360f * progress, false, stroke);
        }

        // 环心：大号 x/y
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        text.setColor(UiPalette.INK);
        text.setTextSize(dp(21));
        canvas.drawText(total > 0 ? (completed + "/" + total) : "0", cx, cy + dp(4f), text);

        text.setTypeface(android.graphics.Typeface.DEFAULT);
        text.setColor(UiPalette.MUTED);
        text.setTextSize(dp(10));
        canvas.drawText("今日", cx, cy - dp(13f), text);

        // 右侧：连胜数字（重音）
        float x = cx + ringR + dp(16);
        if (x > w - dp(30)) return;

        text.setTextAlign(Paint.Align.LEFT);
        text.setTypeface(android.graphics.Typeface.DEFAULT);
        text.setColor(UiPalette.MUTED);
        text.setTextSize(dp(11));
        canvas.drawText("连续坚持", x, cy - dp(12f), text);

        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        text.setColor(0xff4caf7d);
        text.setTextSize(dp(26));
        String num = String.valueOf(streak);
        canvas.drawText(num, x, cy + dp(14f), text);
        float numW = text.measureText(num);

        text.setTypeface(android.graphics.Typeface.DEFAULT);
        text.setColor(UiPalette.MUTED);
        text.setTextSize(dp(12));
        canvas.drawText("天", x + numW + dp(3), cy + dp(13f), text);
    }
}
