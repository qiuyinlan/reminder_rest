package io.iftech.healthreminder;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

final class WellnessAnimationView extends View {
    enum Mood { SAD, TIRED, WATER }

    private final Mood mood;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator animator;
    private float phase;

    WellnessAnimationView(Context context, Mood mood) {
        super(context);
        this.mood = mood;
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(mood == Mood.WATER ? 1200 : 2200);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(new LinearInterpolator());
        animator.addUpdateListener(value -> {
            phase = (float) value.getAnimatedValue();
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
        float size = mood == Mood.WATER ? 100f : 180f;
        float scale = Math.min(getWidth(), getHeight()) / size;
        canvas.save();
        canvas.translate((getWidth() - size * scale) / 2f, (getHeight() - size * scale) / 2f);
        canvas.scale(scale, scale);
        if (mood == Mood.WATER) drawWater(canvas); else drawFace(canvas);
        canvas.restore();
    }

    private void drawFace(Canvas canvas) {
        float bob = (float) Math.sin(phase * Math.PI * 2) * 2.5f;
        canvas.save();
        canvas.translate(0, bob);
        fill(0xffd9ebd9);
        canvas.drawCircle(90, 90, 82, paint);
        fill(0xffffdfa8);
        canvas.drawCircle(90, 90, 65, paint);

        float blink = mood == Mood.TIRED ? 5f : 0f;
        fill(0xff37483d);
        if (mood == Mood.TIRED) {
            stroke(0xff37483d, 4);
            canvas.drawLine(59, 70, 76, 70, paint);
            canvas.drawLine(104, 70, 121, 70, paint);
            canvas.drawLine(62, 75 + blink, 73, 75 + blink, paint);
            canvas.drawLine(107, 75 + blink, 118, 75 + blink, paint);
        } else {
            canvas.drawCircle(67, 72, 5, paint);
            canvas.drawCircle(113, 72, 5, paint);
            fill(0xff78b8cf);
            float tear = 3f + (float) Math.sin(phase * Math.PI * 2) * 2f;
            canvas.drawOval(new RectF(58, 78, 64, 88 + tear), paint);
            canvas.drawOval(new RectF(116, 78, 122, 88 + tear), paint);
        }

        fill(0xffee9c83);
        canvas.drawOval(new RectF(48, 88, 69, 97), paint);
        canvas.drawOval(new RectF(111, 88, 132, 97), paint);
        stroke(0xff9b584d, 4);
        Path mouth = new Path();
        if (mood == Mood.TIRED) {
            canvas.drawArc(new RectF(74, 104, 106, 122), 195, 150, false, paint);
        } else {
            mouth.moveTo(72, 119);
            mouth.quadTo(90, 101, 108, 119);
            canvas.drawPath(mouth, paint);
        }
        canvas.restore();
    }

    private void drawWater(Canvas canvas) {
        float bob = (float) Math.sin(phase * Math.PI * 2) * 4f;
        canvas.save();
        canvas.translate(0, bob);
        Path drop = new Path();
        drop.moveTo(50, 8);
        drop.cubicTo(43, 24, 23, 46, 23, 63);
        drop.cubicTo(23, 81, 35, 93, 50, 93);
        drop.cubicTo(65, 93, 77, 81, 77, 63);
        drop.cubicTo(77, 46, 57, 24, 50, 8);
        drop.close();
        fill(0xff258fc2);
        canvas.drawPath(drop, paint);
        fill(0xffbfeaff);
        canvas.drawOval(new RectF(38, 51, 44, 68), paint);
        canvas.restore();
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
