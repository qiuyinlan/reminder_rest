package io.iftech.healthreminder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

final class ReminderArtworkView extends ImageView {
    private ValueAnimator animator;
    private float phase;

    ReminderArtworkView(Context context, String artworkId) {
        super(context);
        setImageResource(ReminderArtworkCatalog.drawable(artworkId));
        setScaleType(ScaleType.FIT_CENTER);
        setContentDescription(ReminderArtworkCatalog.label(artworkId));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(3200);
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
        canvas.save();
        canvas.translate(0, -3f * getResources().getDisplayMetrics().density * (cycle + 1f));
        canvas.scale(1f + cycle * 0.012f, 1f + cycle * 0.008f,
                getWidth() / 2f, getHeight());
        super.onDraw(canvas);
        canvas.restore();
    }
}
