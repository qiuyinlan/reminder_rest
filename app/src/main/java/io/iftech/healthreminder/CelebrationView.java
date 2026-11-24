package io.iftech.healthreminder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 完成打卡时的粒子爆发：从中心向四周飞散并淡出。
 * 叠在英雄卡上层，用 burst() 触发一次。
 */
final class CelebrationView extends View {
    private static final int COUNT = 6;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<float[]> parts = new ArrayList<>(); // {angle, speed, size}
    private ValueAnimator animator;
    private float progress = 1f;
    private int color = 0xff1f8a5f;
    private final Random random = new Random();

    CelebrationView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paint.setStyle(Paint.Style.FILL);
        setClickable(false);
        setFocusable(false);
    }

    void burst(int color) {
        this.color = color;
        parts.clear();
        for (int i = 0; i < COUNT; i++) {
            float angle = (float) (Math.PI * 2 * i / COUNT - Math.PI / 2
                    + (random.nextDouble() - 0.5) * 0.4);
            float speed = 0.85f + random.nextFloat() * 0.3f;
            float size = 1.8f + random.nextFloat() * 1.4f;
            parts.add(new float[]{angle, speed, size});
        }
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(500);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progress >= 1f) return;
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w * 0.28f; // 环形进度圆心附近
        float cy = h / 2f;
        float travelDistance = 20f * getResources().getDisplayMetrics().density;
        float density = getResources().getDisplayMetrics().density;

        float t = progress;
        int alpha = (int) (255 * (1f - t) * (1f - t));
        paint.setColor(color);
        paint.setAlpha(Math.max(0, alpha));

        float travel = travelDistance * (float) Math.sqrt(t);
        for (float[] p : parts) {
            float x = cx + (float) Math.cos(p[0]) * travel * p[1];
            float y = cy + (float) Math.sin(p[0]) * travel * p[1] + t * t * 10f * density;
            float radius = p[2] * density * (1f - t * 0.5f);
            canvas.drawCircle(x, y, radius, paint);
        }
    }
}
