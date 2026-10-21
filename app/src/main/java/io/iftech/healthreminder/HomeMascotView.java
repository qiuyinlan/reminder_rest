package io.iftech.healthreminder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

final class HomeMascotView extends ImageView {
    private ValueAnimator animator;
    private float phase;

    HomeMascotView(Context context) {
        super(context);
        setImageResource(R.drawable.home_mascot);
        setScaleType(ScaleType.FIT_CENTER);
        setContentDescription("好好歇会的角色");
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
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        float cycle = (float) Math.sin(phase * Math.PI * 2);
        float breathe = (float) Math.sin(phase * Math.PI * 2 - Math.PI / 2);
        canvas.save();
        canvas.translate(0, -2.5f * getResources().getDisplayMetrics().density * (cycle + 1f));
        canvas.rotate(cycle * 1.5f, getWidth() / 2f, getHeight());
        canvas.scale(1f + breathe * 0.018f, 1f - breathe * 0.012f,
                getWidth() / 2f, getHeight());
        super.onDraw(canvas);
        canvas.restore();
    }
}
