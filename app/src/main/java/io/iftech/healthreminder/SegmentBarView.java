package io.iftech.healthreminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** 分段进度条：若干小圆角格，完成一格亮一格。 */
final class SegmentBarView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int total = 3;
    private int filled = 0;
    private int onColor = 0xff4caf7d;
    private int offColor = 0xffddede3;
    private float gapDp = 4f;

    SegmentBarView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.FILL);
    }

    void setSegments(int total, int filled, int onColor, int offColor) {
        this.total = Math.max(1, total);
        this.filled = Math.max(0, Math.min(filled, this.total));
        this.onColor = onColor;
        this.offColor = offColor;
        invalidate();
    }

    void setGapDp(float gapDp) {
        this.gapDp = gapDp;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float gap = dp(gapDp);
        float segW = (w - gap * (total - 1)) / total;
        if (segW <= 0) return;
        float r = Math.min(h / 2f, dp(2f));

        for (int i = 0; i < total; i++) {
            float left = i * (segW + gap);
            rect.set(left, 0, left + segW, h);
            paint.setColor(i < filled ? onColor : offColor);
            canvas.drawRoundRect(rect, r, r, paint);
        }
    }
}
