package io.iftech.healthreminder;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

final class BottomNavigationBar {
    static final int HOME = 0;
    static final int ITEMS = 1;
    static final int COMPANION = 2;
    static final int STATS = 3;

    private static final String[] LABELS = {"首页", "事项", "陪伴", "统计"};

    private BottomNavigationBar() { }

    static LinearLayout create(Activity activity, int selected, Runnable... actions) {
        LinearLayout bar = new LinearLayout(activity);
        bar.setGravity(Gravity.CENTER);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackground(barBackground(activity));
        bar.setElevation(dp(activity, 1));
        for (int i = 0; i < LABELS.length; i++) {
            final int index = i;
            boolean active = i == selected;
            int color = active ? UiPalette.ACCENT : UiPalette.MUTED;
            LinearLayout cell = new LinearLayout(activity);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setFocusable(true);
            cell.setClickable(actions != null && i < actions.length && actions[i] != null);

            Icon icon = new Icon(activity, i, color);
            FrameLayout iconStage = new FrameLayout(activity);
            iconStage.setBackground(active
                    ? pill(activity, UiPalette.SOFT, 13)
                    : pill(activity, Color.TRANSPARENT, 13));
            iconStage.addView(icon, new FrameLayout.LayoutParams(-1, -1));
            cell.addView(iconStage, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 26)));

            TextView label = new TextView(activity);
            label.setText(LABELS[i]);
            label.setTextSize(12);
            label.setTextColor(color);
            label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
            label.setGravity(Gravity.CENTER);
            label.setIncludeFontPadding(false);
            LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, dp(activity, 18));
            labelParams.topMargin = dp(activity, 3);
            cell.addView(label, labelParams);

            if (actions != null && i < actions.length && actions[i] != null) {
                cell.setOnClickListener(v -> actions[index].run());
            }
            bar.addView(cell, new LinearLayout.LayoutParams(0, dp(activity, 60), 1));
        }
        return bar;
    }

    /** 白色卡片底 + 顶部 1dp 浅绿描边，顶部两角圆角，贴在页面底部。 */
    private static GradientDrawable barBackground(Activity activity) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(UiPalette.SURFACE);
        float radius = dp(activity, 16);
        shape.setCornerRadii(new float[]{radius, radius, radius, radius, 0f, 0f, 0f, 0f});
        shape.setStroke(dp(activity, 1), UiPalette.BORDER);
        return shape;
    }

    private static GradientDrawable pill(Activity activity, int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(UiPalette.surface(color));
        shape.setCornerRadius(dp(activity, radius));
        return shape;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static final class Icon extends View {
        private final int type;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Icon(Activity activity, int type, int color) {
            super(activity);
            this.type = type;
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.2f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float density = getResources().getDisplayMetrics().density;
            float size = 22 * density;
            canvas.save();
            canvas.translate((getWidth() - size) / 2f, (getHeight() - size) / 2f);
            canvas.scale(density, density);
            switch (type) {
                case HOME: drawHome(canvas); break;
                case ITEMS: drawItems(canvas); break;
                case COMPANION: drawHeart(canvas); break;
                default: drawStats(canvas); break;
            }
            canvas.restore();
        }

        private void drawHome(Canvas canvas) {
            Path roof = new Path();
            roof.moveTo(3, 11);
            roof.lineTo(12, 3.5f);
            roof.lineTo(21, 11);
            canvas.drawPath(roof, paint);
            canvas.drawRoundRect(new RectF(5.5f, 10, 18.5f, 21), 1.5f, 1.5f, paint);
            canvas.drawLine(10, 21, 10, 15, paint);
            canvas.drawLine(14, 21, 14, 15, paint);
        }

        private void drawItems(Canvas canvas) {
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    float left = 3.5f + col * 6.2f;
                    float top = 3.5f + row * 6.2f;
                    canvas.drawRoundRect(new RectF(left, top, left + 4.2f, top + 4.2f), 0.7f, 0.7f, paint);
                }
            }
        }

        private void drawHeart(Canvas canvas) {
            Path heart = new Path();
            heart.moveTo(12, 20.5f);
            heart.cubicTo(10.5f, 19, 3.2f, 13, 3.2f, 8.7f);
            heart.cubicTo(3.2f, 3.2f, 10, 2, 12, 7);
            heart.cubicTo(14, 2, 20.8f, 3.2f, 20.8f, 8.7f);
            heart.cubicTo(20.8f, 13, 13.5f, 19, 12, 20.5f);
            canvas.drawPath(heart, paint);
        }

        private void drawStats(Canvas canvas) {
            canvas.drawCircle(12, 12, 9, paint);
            canvas.drawLine(12, 6.5f, 12, 12, paint);
            canvas.drawLine(12, 12, 16.5f, 14.5f, paint);
            canvas.drawCircle(12, 12, 1, paint);
        }
    }
}
