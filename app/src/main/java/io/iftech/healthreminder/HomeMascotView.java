package io.iftech.healthreminder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

/**
 * 首页豆芽吉祥物：原版图片 + 呼吸摇摆动画（一直在轻轻晃），
 * 打卡时额外弹一下。
 */
final class HomeMascotView extends ImageView {
    private ValueAnimator animator;
    private ValueAnimator bounceAnim;
    private float phase;
    private float bounceScale = 1f;
    private float bounceLift;

    HomeMascotView(Context context) {
        super(context);
        setImageResource(R.drawable.home_mascot);
        setScaleType(ScaleType.FIT_CENTER);
        setContentDescription("好好歇会的角色");
    }

    /** 打卡瞬间：放大到 1.2 再弹簧回落，同时轻轻跳一下。 */
    void bounce() {
        if (bounceAnim != null) bounceAnim.cancel();
        bounceAnim = ValueAnimator.ofFloat(0f, 1f);
        bounceAnim.setDuration(450);
        bounceAnim.setInterpolator(AnimKit.SPRING);
        bounceAnim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            bounceScale = 1f + 0.2f * (float) Math.sin(t * Math.PI);
            bounceLift = -8f * getResources().getDisplayMetrics().density
                    * (float) Math.sin(t * Math.PI);
            invalidate();
        });
        bounceAnim.start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(2800);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (bounceAnim != null) {
            bounceAnim.cancel();
            bounceAnim = null;
        }
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        float cycle = (float) Math.sin(phase * Math.PI * 2);
        float breathe = (float) Math.sin(phase * Math.PI * 2 - Math.PI / 2);
        canvas.save();
        canvas.translate(0, -2.5f * getResources().getDisplayMetrics().density * (cycle + 1f)
                + bounceLift);
        canvas.rotate(cycle * 1.5f, getWidth() / 2f, getHeight());
        float scaleX = (1f + breathe * 0.018f) * bounceScale;
        float scaleY = (1f - breathe * 0.012f) * bounceScale;
        canvas.scale(scaleX, scaleY, getWidth() / 2f, getHeight());
        super.onDraw(canvas);
        canvas.restore();
    }
}
