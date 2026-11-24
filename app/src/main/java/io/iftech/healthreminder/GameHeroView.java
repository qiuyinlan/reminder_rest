package io.iftech.healthreminder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 首页游戏英雄卡：
 * 左侧环形今日进度 + 右侧连胜火焰和等级称号，整体渐变底 + 呼吸光晕。
 * 纯 Canvas 绘制，跟随主题色。
 */
final class GameHeroView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arc = new RectF();
    private final RectF card = new RectF();

    private int completed;
    private int total;
    private int streak;
    private String levelTitle = "";

    private ValueAnimator pulse;
    private float phase;

    GameHeroView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        text.setTextAlign(Paint.Align.LEFT);
    }

    void setData(int completed, int total, int streak, String levelTitle) {
        this.completed = Math.max(0, completed);
        this.total = Math.max(0, total);
        this.streak = Math.max(0, streak);
        this.levelTitle = levelTitle == null ? "" : levelTitle;
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        pulse = ValueAnimator.ofFloat(0f, 1f);
        pulse.setDuration(2400);
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

        float radius = dp(18);
        card.set(0, 0, w, h);

        // 渐变底
        int[] hero = UiPalette.heroGradient();
        fill.setStyle(Paint.Style.FILL);
        fill.setShader(new LinearGradient(0, 0, w, h, hero, null, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(card, radius, radius, fill);
        fill.setShader(null);

        // 描边
        stroke.setShader(null);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(UiPalette.BORDER);
        arc.set(card);
        arc.inset(dp(0.5f), dp(0.5f));
        canvas.drawRoundRect(arc, radius, radius, stroke);

        // ---- 左侧环形进度 ----
        float pad = dp(18);
        float cy = h / 2f;
        float ringR = Math.min(h / 2f - pad, dp(42));
        float cx = pad + ringR;
        float ringStroke = dp(9);

        // 呼吸光晕
        float glow = (float) (0.5 + 0.5 * Math.sin(phase * Math.PI * 2));
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(withAlpha(UiPalette.ACCENT, (int) (26 + 22 * glow)));
        canvas.drawCircle(cx, cy, ringR + dp(5) + dp(2) * glow, fill);

        // 底环
        stroke.setStrokeWidth(ringStroke);
        stroke.setColor(withAlpha(UiPalette.ACCENT, 40));
        arc.set(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
        canvas.drawCircle(cx, cy, ringR, stroke);

        // 进度弧
        float progress = total > 0 ? Math.min(1f, completed / (float) total) : 0f;
        if (progress > 0f) {
            stroke.setColor(UiPalette.ACCENT);
            canvas.drawArc(arc, -90f, 360f * progress, false, stroke);
        }

        // 环中心数字
        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        text.setColor(UiPalette.INK);
        text.setTextSize(dp(21));
        String big = total > 0 ? (completed + "/" + total) : "0";
        canvas.drawText(big, cx, cy + dp(7), text);

        text.setTextSize(dp(10));
        text.setTypeface(android.graphics.Typeface.DEFAULT);
        text.setColor(UiPalette.MUTED);
        canvas.drawText("今日", cx, cy - dp(12), text);

        // ---- 右侧：火焰 + 连胜 + 等级 ----
        text.setTextAlign(Paint.Align.LEFT);
        float x = cx + ringR + dp(20);
        float avail = w - x - pad;
        if (avail < dp(40)) return;

        // 火焰 emoji
        text.setTypeface(android.graphics.Typeface.DEFAULT);
        text.setTextSize(dp(22));
        canvas.drawText("🔥", x, cy - dp(24), text);

        // 连胜大数字
        text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        text.setColor(UiPalette.ACCENT);
        text.setTextSize(dp(38));
        String streakText = String.valueOf(streak);
        canvas.drawText(streakText, x, cy + dp(15), text);
        float streakWidth = text.measureText(streakText);

        // 「天连胜」
        text.setTypeface(android.graphics.Typeface.DEFAULT);
        text.setColor(UiPalette.MUTED);
        text.setTextSize(dp(13));
        canvas.drawText("天连胜", x + streakWidth + dp(4), cy + dp(13), text);

        // 等级称号 pill
        if (!levelTitle.isEmpty()) {
            text.setTextSize(dp(11));
            float tw = text.measureText(levelTitle);
            float pillH = dp(20);
            float pillW = tw + dp(16);
            if (x + pillW <= w - pad) {
                fill.setStyle(Paint.Style.FILL);
                fill.setColor(withAlpha(UiPalette.ACCENT, 34));
                RectF pill = new RectF(x, cy + dp(24), x + pillW, cy + dp(24) + pillH);
                canvas.drawRoundRect(pill, pillH / 2f, pillH / 2f, fill);
                text.setColor(UiPalette.ACCENT);
                text.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
                canvas.drawText(levelTitle, x + dp(8), cy + dp(24) + pillH / 2f + dp(4), text);
            }
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | ((alpha & 0xff) << 24);
    }
}
