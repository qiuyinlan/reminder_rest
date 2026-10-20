package io.iftech.healthreminder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

final class TypingSceneView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator typing;
    private float phase;

    TypingSceneView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        typing = ValueAnimator.ofFloat(0f, 1f);
        typing.setDuration(760);
        typing.setRepeatCount(ValueAnimator.INFINITE);
        typing.setInterpolator(new LinearInterpolator());
        typing.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue();
            invalidate();
        });
        typing.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (typing != null) typing.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth() / 320f, getHeight() / 220f);
        canvas.save();
        canvas.translate((getWidth() - 320 * scale) / 2f, (getHeight() - 220 * scale) / 2f);
        canvas.scale(scale, scale);
        scene(canvas);
        canvas.restore();
    }

    private void scene(Canvas c) {
        fill(0xfff2f8f6);
        c.drawRoundRect(new RectF(19, 15, 301, 205), 34, 34, paint);

        // Quiet window and chair establish a familiar desk scene without visual clutter.
        fill(0xffe2f3eb);
        c.drawRoundRect(new RectF(225, 28, 280, 72), 10, 10, paint);
        stroke(0xffcbded4, 2);
        c.drawLine(252, 29, 252, 71, paint);
        c.drawLine(226, 50, 279, 50, paint);

        stroke(0xff747c79, 10);
        c.drawRoundRect(new RectF(72, 91, 145, 153), 22, 22, paint);
        c.drawLine(91, 145, 87, 168, paint);
        c.drawLine(128, 145, 135, 168, paint);

        // Monitor.
        fill(0xff59615e);
        c.drawRoundRect(new RectF(193, 52, 276, 113), 9, 9, paint);
        fill(0xfffbfdfc);
        c.drawRoundRect(new RectF(198, 57, 271, 107), 5, 5, paint);
        fill(0xff7fcca7);
        c.drawRoundRect(new RectF(205, 66, 250, 70), 2, 2, paint);
        fill(0xffd6e7de);
        c.drawRoundRect(new RectF(205, 76, 261, 79), 2, 2, paint);
        c.drawRoundRect(new RectF(205, 84, 247, 87), 2, 2, paint);
        fill(phase < .5f ? 0xff62c79f : 0x4462c79f);
        c.drawRect(205, 92, 207, 99, paint);
        fill(0xff747c79);
        c.drawRoundRect(new RectF(229, 113, 240, 124), 3, 3, paint);
        c.drawRoundRect(new RectF(215, 122, 254, 127), 3, 3, paint);

        // Seated typist.
        fill(0xffedc09b);
        c.drawRoundRect(new RectF(118, 91, 135, 112), 7, 7, paint);
        fill(0xff5a5e5c);
        Path hair = new Path();
        hair.moveTo(112, 80);
        hair.cubicTo(111, 61, 126, 51, 140, 60);
        hair.cubicTo(148, 65, 149, 76, 145, 84);
        hair.cubicTo(137, 77, 128, 76, 119, 85);
        hair.close();
        c.drawPath(hair, paint);
        fill(0xff4d5351);
        c.drawCircle(128, 84, 2, paint);
        fill(0xffd48c73);
        c.drawCircle(135, 88, 2, paint);

        fill(0xff88cfab);
        Path shirt = new Path();
        shirt.moveTo(118, 106);
        shirt.quadTo(128, 99, 140, 108);
        shirt.quadTo(150, 123, 153, 148);
        shirt.lineTo(105, 148);
        shirt.quadTo(107, 121, 118, 106);
        c.drawPath(shirt, paint);

        float tap = phase < .5f ? 0f : 4f;
        stroke(0xffedc09b, 9);
        c.drawLine(142, 116, 168, 128 + tap, paint);
        c.drawLine(108, 117, 150, 132 + 4f - tap, paint);
        c.drawLine(168, 128 + tap, 184, 138 + tap, paint);
        c.drawLine(150, 132 + 4f - tap, 178, 138 + 4f - tap, paint);

        // Desk, keyboard and mug sit in front of the hands.
        fill(0xffb9825c);
        c.drawRoundRect(new RectF(44, 145, 286, 154), 4, 4, paint);
        c.drawRoundRect(new RectF(74, 153, 81, 187), 3, 3, paint);
        c.drawRoundRect(new RectF(250, 153, 257, 187), 3, 3, paint);
        fill(0xffe6f2ec);
        c.drawRoundRect(new RectF(174, 139, 216, 145), 3, 3, paint);
        stroke(0xffb6c9bf, 1);
        for (int i = 0; i < 5; i++) c.drawLine(179 + i * 7, 141, 179 + i * 7, 143, paint);
        fill(0xfff4f7f0);
        c.drawRoundRect(new RectF(57, 127, 75, 145), 4, 4, paint);
        stroke(0xfff4f7f0, 3);
        c.drawArc(new RectF(70, 130, 81, 141), -70, 240, false, paint);
        fill(0xff96c6ac);
        c.drawRoundRect(new RectF(61, 123, 71, 130), 2, 2, paint);
    }

    private void fill(int color) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
        paint.setStrokeWidth(1);
    }

    private void stroke(int color, float width) {
        paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }
}
