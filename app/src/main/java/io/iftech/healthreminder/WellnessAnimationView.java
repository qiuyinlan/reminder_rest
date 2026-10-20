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
    enum Mood { SAD, TIRED, BREATHING, THIRSTY, SEED, WATER }

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
        if (mood == Mood.WATER) drawWater(canvas);
        else if (mood == Mood.SEED) drawSeed(canvas);
        else drawFace(canvas);
        canvas.restore();
    }

    private void drawSeed(Canvas canvas) {
        float cycle = (float) Math.sin(phase * Math.PI * 2);
        canvas.save();
        canvas.translate(0, cycle * 4f);

        // A soft bean-shaped body with a sprout that sways independently.
        fill(0xff73bd79);
        canvas.drawOval(new RectF(35, 55, 145, 157), paint);
        fill(0xff91cf83);
        canvas.drawOval(new RectF(43, 61, 137, 151), paint);
        fill(0xffb6df91);
        canvas.drawOval(new RectF(52, 67, 91, 137), paint);

        canvas.save();
        canvas.rotate(cycle * 9f, 90, 62);
        stroke(0xff579b61, 6);
        canvas.drawLine(90, 68, 90, 34, paint);
        fill(0xff59ae70);
        Path leaf = new Path();
        leaf.moveTo(89, 46);
        leaf.cubicTo(62, 42, 57, 22, 60, 17);
        leaf.cubicTo(79, 16, 91, 27, 91, 42);
        leaf.close();
        canvas.drawPath(leaf, paint);
        Path leafRight = new Path();
        leafRight.moveTo(92, 40);
        leafRight.cubicTo(96, 19, 116, 12, 126, 17);
        leafRight.cubicTo(125, 35, 110, 45, 92, 46);
        leafRight.close();
        fill(0xff79c77b);
        canvas.drawPath(leafRight, paint);
        stroke(0xff9cdaa0, 2);
        canvas.drawLine(68, 23, 85, 39, paint);
        canvas.drawLine(117, 22, 99, 39, paint);
        canvas.restore();

        // Tiny arms lift as the seed bobs, giving the character a welcoming motion.
        float armLift = cycle * 3f;
        stroke(0xff579b61, 5);
        canvas.drawLine(43, 112, 28, 102 - armLift, paint);
        canvas.drawLine(137, 112, 152, 102 - armLift, paint);
        fill(0xff3d5946);
        canvas.drawCircle(70, 103, 4, paint);
        canvas.drawCircle(110, 103, 4, paint);
        stroke(0xff47794f, 3);
        canvas.drawArc(new RectF(78, 108, 102, 126), 20, 140, false, paint);
        fill(0xffef9d91);
        canvas.drawOval(new RectF(55, 111, 68, 117), paint);
        canvas.drawOval(new RectF(112, 111, 125, 117), paint);

        float dropY = (phase % .5f) * 18f;
        Path drop = new Path();
        drop.moveTo(155, 55 + dropY);
        drop.cubicTo(151, 62 + dropY, 147, 67 + dropY, 147, 72 + dropY);
        drop.cubicTo(147, 78 + dropY, 155, 80 + dropY, 159, 75 + dropY);
        drop.cubicTo(163, 70 + dropY, 159, 63 + dropY, 155, 55 + dropY);
        drop.close();
        fill(0xff5db9c1);
        canvas.drawPath(drop, paint);
        canvas.restore();
    }

    private void drawFace(Canvas canvas) {
        float bob = (float) Math.sin(phase * Math.PI * 2) * (mood == Mood.BREATHING ? 5f : 2.5f);
        canvas.save();
        canvas.translate(0, bob);
        fill(0xffe5f4ec);
        canvas.drawCircle(90, 90, 82, paint);
        fill(0xffffdfa8);
        canvas.drawCircle(90, 90, 65, paint);

        float blink = mood == Mood.TIRED ? 5f : 0f;
        fill(0xff4b5350);
        if (mood == Mood.TIRED) {
            stroke(0xff4b5350, 4);
            canvas.drawLine(59, 70, 76, 70, paint);
            canvas.drawLine(104, 70, 121, 70, paint);
            canvas.drawLine(62, 75 + blink, 73, 75 + blink, paint);
            canvas.drawLine(107, 75 + blink, 118, 75 + blink, paint);
        } else if (mood == Mood.BREATHING) {
            stroke(0xff4b5350, 4);
            canvas.drawArc(new RectF(56, 65, 78, 81), 20, 140, false, paint);
            canvas.drawArc(new RectF(102, 65, 124, 81), 20, 140, false, paint);
        } else if (mood == Mood.THIRSTY) {
            stroke(0xff765448, 4);
            canvas.drawLine(57, 61, 77, 67, paint);
            canvas.drawLine(103, 67, 123, 61, paint);
            fill(0xff4b5350);
            canvas.drawOval(new RectF(63, 70, 75, 83), paint);
            canvas.drawOval(new RectF(105, 70, 117, 83), paint);
            float sweatY = 2f + (float) Math.sin(phase * Math.PI * 2) * 4f;
            Path sweat = new Path();
            sweat.moveTo(143, 38 + sweatY);
            sweat.cubicTo(140, 44 + sweatY, 135, 50 + sweatY, 135, 54 + sweatY);
            sweat.cubicTo(135, 60 + sweatY, 144, 60 + sweatY, 144, 54 + sweatY);
            sweat.cubicTo(144, 50 + sweatY, 141, 44 + sweatY, 143, 38 + sweatY);
            sweat.close();
            fill(0xff7fc8c1);
            canvas.drawPath(sweat, paint);
        } else {
            canvas.drawCircle(67, 72, 5, paint);
            canvas.drawCircle(113, 72, 5, paint);
            fill(0xff83cfc5);
            float tear = 3f + (float) Math.sin(phase * Math.PI * 2) * 2f;
            canvas.drawOval(new RectF(58, 78, 64, 88 + tear), paint);
            canvas.drawOval(new RectF(116, 78, 122, 88 + tear), paint);
        }

        fill(0xffee9c83);
        canvas.drawOval(new RectF(48, 88, 69, 97), paint);
        canvas.drawOval(new RectF(111, 88, 132, 97), paint);
        stroke(0xff9b584d, 4);
        Path mouth = new Path();
        if (mood == Mood.THIRSTY) {
            fill(0xff7b3f45);
            float gasp = (float) Math.sin(phase * Math.PI * 2) * 2f;
            canvas.drawOval(new RectF(76, 106, 104, 139 + gasp), paint);
            fill(0xffef9789);
            canvas.drawOval(new RectF(84, 126 + gasp, 96, 136 + gasp), paint);
        } else if (mood == Mood.TIRED) {
            canvas.drawArc(new RectF(74, 104, 106, 122), 195, 150, false, paint);
        } else if (mood == Mood.BREATHING) {
            canvas.drawArc(new RectF(70, 101, 110, 127), 20, 140, false, paint);
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
        fill(0xff65bfae);
        canvas.drawPath(drop, paint);
        fill(0xffc7ece2);
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
