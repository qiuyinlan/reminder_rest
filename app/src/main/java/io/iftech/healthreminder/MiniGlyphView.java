package io.iftech.healthreminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * 单色线性小图标：书 / 清单 / 卡片 / 嫩芽。
 * 统一线宽、圆头，跟随主题色，替代 emoji 混排。
 */
final class MiniGlyphView extends View {
    static final int BOOK = 0;
    static final int CHECKLIST = 1;
    static final int CARDS = 2;
    static final int SPROUT = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path1 = new Path();
    private final Path path2 = new Path();
    private int kind = BOOK;
    private int color = 0xff4caf7d;
    /** 嫩芽等级 0-5：0 种子，5 小树。 */
    private int level = 0;

    MiniGlyphView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void setKind(int kind, int color) {
        this.kind = kind;
        this.color = color;
        invalidate();
    }

    void setSproutLevel(int level, int color) {
        this.kind = SPROUT;
        this.level = Math.max(0, Math.min(5, level));
        this.color = color;
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float size = Math.min(w, h) * 0.78f;
        float cx = w / 2f;
        float cy = h / 2f;
        float left = cx - size / 2f;
        float top = cy - size / 2f;

        paint.setColor(color);
        paint.setStrokeWidth(Math.max(dp(1.4f), size * 0.075f));
        paint.setAlpha(255);
        path1.reset();
        path2.reset();

        if (kind == BOOK) {
            // 摊开的书：中缝 + 两侧曲线
            path1.moveTo(cx, top + size * 0.22f);
            path1.lineTo(cx, top + size * 0.82f);
            path2.moveTo(cx, top + size * 0.25f);
            path2.cubicTo(cx - size * 0.18f, top + size * 0.14f,
                    left + size * 0.04f, top + size * 0.2f, left, top + size * 0.3f);
            path2.lineTo(left, top + size * 0.8f);
            path2.cubicTo(left + size * 0.06f, top + size * 0.72f,
                    cx - size * 0.18f, top + size * 0.68f, cx, top + size * 0.8f);
            path2.moveTo(cx, top + size * 0.25f);
            path2.cubicTo(cx + size * 0.18f, top + size * 0.14f,
                    left + size * 0.96f, top + size * 0.2f, left + size, top + size * 0.3f);
            path2.lineTo(left + size, top + size * 0.8f);
            path2.cubicTo(left + size * 0.94f, top + size * 0.72f,
                    cx + size * 0.18f, top + size * 0.68f, cx, top + size * 0.8f);
            canvas.drawPath(path1, paint);
            canvas.drawPath(path2, paint);

        } else if (kind == CHECKLIST) {
            // 三行清单，左侧小勾
            for (int i = 0; i < 3; i++) {
                float y = top + size * (0.26f + i * 0.24f);
                path1.moveTo(left, y);
                path1.lineTo(left + size * 0.16f, y + size * 0.07f);
                path1.lineTo(left + size * 0.3f, y - size * 0.08f);
                paint.setAlpha(i == 0 ? 255 : 140);
                canvas.drawPath(path1, paint);
                path1.reset();
                path2.moveTo(left + size * 0.44f, y);
                path2.lineTo(left + size * 0.98f, y);
                canvas.drawPath(path2, paint);
                path2.reset();
            }
            paint.setAlpha(255);

        } else if (kind == CARDS) {
            // 两张叠起来的卡片
            paint.setAlpha(140);
            path1.addRoundRect(left + size * 0.12f, top + size * 0.06f,
                    left + size * 0.86f, top + size * 0.62f, size * 0.12f, size * 0.12f,
                    Path.Direction.CW);
            canvas.drawPath(path1, paint);
            paint.setAlpha(255);
            path2.addRoundRect(left + size * 0.22f, top + size * 0.32f,
                    left + size * 0.96f, top + size * 0.94f, size * 0.12f, size * 0.12f,
                    Path.Direction.CW);
            canvas.drawPath(path2, paint);

        } else {
            drawSprout(canvas, cx, top, size);
        }
    }

    /** 嫩芽随等级长高：0 种子、2 一片叶、4 两片叶、5 小树冠。 */
    private void drawSprout(Canvas canvas, float cx, float top, float size) {
        float baseY = top + size * 0.94f;
        float stemTop = top + size * (0.72f - level * 0.09f);

        // 茎
        path1.reset();
        path1.moveTo(cx, baseY);
        path1.lineTo(cx, stemTop);
        canvas.drawPath(path1, paint);

        if (level == 0) {
            // 种子：一个小圆
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, baseY - size * 0.04f, size * 0.09f, paint);
            paint.setStyle(Paint.Style.STROKE);
            return;
        }

        // 叶子（左右各一片，等级越高越多）
        int leaves = Math.min(3, level);
        for (int i = 0; i < leaves; i++) {
            float y = stemTop + size * (0.10f + i * 0.14f);
            float span = size * (0.26f - i * 0.03f);
            path2.reset();
            path2.moveTo(cx, y);
            path2.cubicTo(cx - span * 0.7f, y - span * 0.5f,
                    cx - span, y + span * 0.15f, cx - span * 0.85f, y + span * 0.4f);
            path2.cubicTo(cx - span * 0.4f, y + span * 0.35f, cx - span * 0.1f, y + span * 0.2f, cx, y);
            canvas.drawPath(path2, paint);
            path2.reset();
            path2.moveTo(cx, y);
            path2.cubicTo(cx + span * 0.7f, y - span * 0.5f,
                    cx + span, y + span * 0.15f, cx + span * 0.85f, y + span * 0.4f);
            path2.cubicTo(cx + span * 0.4f, y + span * 0.35f, cx + span * 0.1f, y + span * 0.2f, cx, y);
            canvas.drawPath(path2, paint);
        }

        if (level >= 4) {
            // 小树冠
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(cx, stemTop - size * 0.1f, size * 0.13f, paint);
            canvas.drawCircle(cx - size * 0.12f, stemTop + size * 0.02f, size * 0.09f, paint);
            canvas.drawCircle(cx + size * 0.12f, stemTop + size * 0.02f, size * 0.09f, paint);
            paint.setStyle(Paint.Style.STROKE);
        }
    }
}
