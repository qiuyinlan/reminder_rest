package io.iftech.healthreminder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/**
 * 豆芽吉祥物：首屏第一视觉焦点，带状态表情。
 * - 没完成：打瞌睡（闭眼 + z）
 * - 过半：睁眼加油
 * - 全完成：蹦跳庆祝
 * 打卡时调用 bounce() 弹一下。
 */
final class SproutMascotView extends View {
    static final int SLEEPY = 0;
    static final int AWAKE = 1;
    static final int CELEBRATE = 2;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF oval = new RectF();

    private int mood = AWAKE;
    private ValueAnimator idle;
    private ValueAnimator bounceAnim;
    private float phase;
    private float scale = 1f;
    private float liftY;

    SproutMascotView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        face.setStyle(Paint.Style.FILL);
    }

    void setMood(int mood) {
        this.mood = mood;
        invalidate();
    }

    /** 打卡瞬间：放大到 1.2 再弹簧回落。 */
    void bounce() {
        if (bounceAnim != null) bounceAnim.cancel();
        scale = 1f;
        bounceAnim = ValueAnimator.ofFloat(0f, 1f);
        bounceAnim.setDuration(420);
        bounceAnim.setInterpolator(AnimKit.SPRING);
        bounceAnim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            // 1 → 1.2 → 1 的过冲由 SPRING 曲线天然完成
            scale = 1f + 0.2f * (float) Math.sin(t * Math.PI);
            liftY = -6f * getResources().getDisplayMetrics().density
                    * (float) Math.sin(t * Math.PI);
            invalidate();
        });
        bounceAnim.start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        idle = ValueAnimator.ofFloat(0f, 1f);
        idle.setDuration(3000);
        idle.setRepeatCount(ValueAnimator.INFINITE);
        idle.setInterpolator(new LinearInterpolator());
        idle.addUpdateListener(a -> {
            phase = (float) a.getAnimatedValue();
            invalidate();
        });
        idle.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (idle != null) idle.cancel();
        if (bounceAnim != null) bounceAnim.cancel();
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

        float size = Math.min(w, h);
        float cx = w / 2f;
        float baseY = h * 0.92f;

        float breathe = (float) Math.sin(phase * Math.PI * 2);
        float sway = mood == SproutMascotView.SLEEPY ? breathe * 1.5f
                : (mood == CELEBRATE ? breathe * 6f : breathe * 3f);

        canvas.save();
        canvas.translate(cx, baseY + liftY);
        canvas.scale(scale, scale);
        canvas.translate(-cx, -baseY);

        float stemW = size * 0.075f;
        float stemH = size * 0.42f;
        float topY = baseY - stemH;

        // 茎
        stroke.setColor(0xff4caf7d);
        stroke.setStrokeWidth(stemW);
        path.reset();
        path.moveTo(cx, baseY);
        path.quadTo(cx + sway * 0.5f, baseY - stemH * 0.5f, cx + sway * 0.3f, topY);
        canvas.drawPath(path, stroke);

        // 两片叶子
        drawLeaf(canvas, cx + sway * 0.3f, topY, size * 0.30f, -38f + sway, 0xff6fd09b);
        drawLeaf(canvas, cx + sway * 0.3f, topY, size * 0.30f, -142f - sway, 0xff4caf7d);

        // 土堆
        fill.setColor(0xffffffff);
        float groundW = size * 0.5f;
        oval.set(cx - groundW / 2f, baseY - size * 0.05f, cx + groundW / 2f, baseY + size * 0.06f);
        canvas.drawOval(oval, fill);

        // 脸
        float faceY = baseY - stemH * 0.42f;
        drawFace(canvas, cx + sway * 0.3f, faceY, size);

        canvas.restore();
    }

    private void drawLeaf(Canvas canvas, float x, float y, float len, float angleDeg, int color) {
        canvas.save();
        canvas.rotate(angleDeg, x, y);
        fill.setColor(color);
        fill.setAlpha(235);
        path.reset();
        path.moveTo(x, y);
        path.cubicTo(x + len * 0.55f, y - len * 0.35f,
                x + len * 0.95f, y - len * 0.05f, x + len * 0.85f, y + len * 0.28f);
        path.cubicTo(x + len * 0.5f, y + len * 0.22f,
                x + len * 0.18f, y + len * 0.12f, x, y);
        canvas.drawPath(path, fill);
        fill.setAlpha(255);
        canvas.restore();
    }

    private void drawFace(Canvas canvas, float cx, float cy, float size) {
        float eyeGap = size * 0.075f;
        float eyeR = size * 0.028f;
        int faceInk = 0xff3d5a4c;

        if (mood == SLEEPY) {
            // 闭眼：两条下弯弧
            stroke.setColor(faceInk);
            stroke.setStrokeWidth(dp(1.6f));
            path.reset();
            path.moveTo(cx - eyeGap - eyeR, cy);
            path.quadTo(cx - eyeGap, cy + eyeR, cx - eyeGap + eyeR, cy);
            canvas.drawPath(path, stroke);
            path.reset();
            path.moveTo(cx + eyeGap - eyeR, cy);
            path.quadTo(cx + eyeGap, cy + eyeR, cx + eyeGap + eyeR, cy);
            canvas.drawPath(path, stroke);
            // 小 z
            face.setColor(faceInk);
            face.setAlpha(150);
            face.setTextSize(size * 0.13f);
            face.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            canvas.drawText("z", cx + size * 0.22f, cy - size * 0.16f, face);
            face.setAlpha(255);

        } else if (mood == CELEBRATE) {
            // 弯月眼 + 张开的笑嘴
            stroke.setColor(faceInk);
            stroke.setStrokeWidth(dp(1.8f));
            path.reset();
            path.moveTo(cx - eyeGap - eyeR, cy + eyeR * 0.4f);
            path.quadTo(cx - eyeGap, cy - eyeR * 0.9f, cx - eyeGap + eyeR, cy + eyeR * 0.4f);
            canvas.drawPath(path, stroke);
            path.reset();
            path.moveTo(cx + eyeGap - eyeR, cy + eyeR * 0.4f);
            path.quadTo(cx + eyeGap, cy - eyeR * 0.9f, cx + eyeGap + eyeR, cy + eyeR * 0.4f);
            canvas.drawPath(path, stroke);

            face.setColor(faceInk);
            oval.set(cx - eyeR * 1.5f, cy + eyeR * 0.6f,
                    cx + eyeR * 1.5f, cy + eyeR * 2.6f);
            canvas.drawArc(oval, 0, 180, true, face);

        } else {
            // 睁眼：实心圆 + 高光
            face.setColor(faceInk);
            canvas.drawCircle(cx - eyeGap, cy, eyeR, face);
            canvas.drawCircle(cx + eyeGap, cy, eyeR, face);
            fill.setColor(0xffffffff);
            canvas.drawCircle(cx - eyeGap + eyeR * 0.35f, cy - eyeR * 0.35f, eyeR * 0.32f, fill);
            canvas.drawCircle(cx + eyeGap + eyeR * 0.35f, cy - eyeR * 0.35f, eyeR * 0.32f, fill);
            // 微笑
            stroke.setColor(faceInk);
            stroke.setStrokeWidth(dp(1.5f));
            path.reset();
            path.moveTo(cx - eyeR, cy + eyeR * 1.3f);
            path.quadTo(cx, cy + eyeR * 2.1f, cx + eyeR, cy + eyeR * 1.3f);
            canvas.drawPath(path, stroke);
        }
    }
}
