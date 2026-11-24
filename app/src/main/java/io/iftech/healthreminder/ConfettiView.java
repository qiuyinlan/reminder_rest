package io.iftech.healthreminder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** 彩带飘落：开箱后从顶部落下的彩色小片。 */
final class ConfettiView extends View {
    private static final int COUNT = 26;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<float[]> parts = new ArrayList<>(); // {x比例, 速度, 摆动, 大小, 颜色号}
    private final int[] colors = {0xff1f8a5f, 0xff8fd9b6, 0xffffd166, 0xffff8fab, 0xff7ec8e3};
    private final Random random = new Random();
    private ValueAnimator animator;
    private float progress = 1f;

    ConfettiView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        paint.setStyle(Paint.Style.FILL);
        setClickable(false);
        setFocusable(false);
    }

    void start() {
        parts.clear();
        for (int i = 0; i < COUNT; i++) {
            parts.add(new float[]{
                    random.nextFloat(),
                    0.7f + random.nextFloat() * 0.6f,
                    random.nextFloat() * 6.28f,
                    2.5f + random.nextFloat() * 3f,
                    random.nextInt(colors.length)
            });
        }
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(1500);
        animator.setInterpolator(new LinearInterpolator());
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
        float density = getResources().getDisplayMetrics().density;
        float t = progress;

        for (float[] p : parts) {
            float x = p[0] * w + (float) Math.sin(t * 6.28f * 1.5f + p[2]) * 16f * density;
            float y = -20f * density + (h + 40f * density) * t * p[1];
            int alpha = (int) (255 * (1f - Math.max(0f, t - 0.65f) / 0.35f));
            paint.setColor(colors[(int) p[4] % colors.length]);
            paint.setAlpha(Math.max(0, Math.min(255, alpha)));
            float size = p[3] * density;
            canvas.drawRect(x, y, x + size, y + size * 0.6f, paint);
        }
    }
}
