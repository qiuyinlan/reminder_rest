package io.iftech.healthreminder;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 各页面共用的「今日进度」英雄卡：和首页今日任务卡同一套视觉。
 * 大数字 + 等级/经验 + 连续天数 + 分段进度 + 宝箱提示。
 */
final class ProgressHeroView extends LinearLayout {
    private ProgressHeroView(Context context, String label, int done, int total,
                             int level, int expProgress, int streak, String motivation) {
        super(context);
        setOrientation(VERTICAL);
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xffc4ecda, 0xfff7fdfb});
        background.setCornerRadius(dp(18));
        setBackground(background);
        setElevation(dp(2));
        setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, new LinearLayout.LayoutParams(-1, -2));

        HomeMascotView mascot = new HomeMascotView(context);
        row.addView(mascot, new LinearLayout.LayoutParams(dp(72), dp(72)));

        LinearLayout column = new LinearLayout(context);
        column.setOrientation(VERTICAL);
        LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, -2, 1f);
        columnParams.leftMargin = dp(10);
        row.addView(column, columnParams);

        LinearLayout top = new LinearLayout(context);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(text(label, 13, UiPalette.INK, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView levelChip = text("Lv." + level + " · " + expProgress + "/100", 9,
                UiPalette.ACCENT, true);
        levelChip.setGravity(Gravity.CENTER);
        levelChip.setPadding(dp(7), dp(2), dp(7), dp(2));
        levelChip.setBackground(round(UiPalette.PALE, 10));
        top.addView(levelChip);
        column.addView(top);

        LinearLayout numbers = new LinearLayout(context);
        numbers.setGravity(Gravity.BOTTOM);
        TextView doneView = text(String.valueOf(Math.max(0, done)), 32, UiPalette.ACCENT, true);
        doneView.setIncludeFontPadding(false);
        numbers.addView(doneView);
        TextView totalView = text(" / " + Math.max(0, total), 16, UiPalette.MUTED, false);
        totalView.setIncludeFontPadding(false);
        totalView.setPadding(0, 0, 0, dp(3));
        numbers.addView(totalView);
        numbers.addView(new View(context), new LinearLayout.LayoutParams(0, 1, 1f));
        TextView streakView = text(streak > 0 ? "连续 " + streak + " 天" : "今天开个头",
                10, UiPalette.WARN, true);
        streakView.setGravity(Gravity.CENTER);
        streakView.setPadding(dp(8), dp(3), dp(8), dp(3));
        streakView.setBackground(round(UiPalette.WARN_SOFT, 10));
        numbers.addView(streakView);
        LinearLayout.LayoutParams numbersParams = new LinearLayout.LayoutParams(-1, -2);
        numbersParams.topMargin = dp(1);
        column.addView(numbers, numbersParams);

        SegmentView segments = new SegmentView(context);
        segments.setProgress(done, total);
        LinearLayout.LayoutParams segmentParams = new LinearLayout.LayoutParams(-1, dp(6));
        segmentParams.topMargin = dp(5);
        column.addView(segments, segmentParams);

        LinearLayout bottom = new LinearLayout(context);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        ChestView chest = new ChestView(context);
        chest.setFilled(total > 0 && done >= total);
        bottom.addView(chest, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView tip = text(motivation, 11, UiPalette.MUTED, false);
        tip.setMaxLines(1);
        LinearLayout.LayoutParams tipParams = new LinearLayout.LayoutParams(0, -2, 1f);
        tipParams.leftMargin = dp(5);
        bottom.addView(tip, tipParams);
        LinearLayout.LayoutParams bottomParams = new LinearLayout.LayoutParams(-1, -2);
        bottomParams.topMargin = dp(5);
        column.addView(bottom, bottomParams);
    }

    static ProgressHeroView create(Context context, String label, int done, int total,
                                   String motivation) {
        return new ProgressHeroView(context, label, done, total,
                DailyQuest.level(context), DailyQuest.levelProgress(context),
                GameProgress.getGlobalStreak(context), motivation);
    }

    static String motivationText(int done, int total) {
        if (total <= 0) return "今天没有安排任务";
        if (done >= total) return "今日全清，明天继续";
        if (done == 0) return "先做一个，点亮第一格";
        if (total - done <= 2) return "还差 " + (total - done) + " 件就全清";
        return "今天已完成 " + done + " 件，保持节奏";
    }

    private TextView text(String value, int size, int color, boolean bold) {
        return FormKit.text(getContext(), value, size, color, bold);
    }

    private GradientDrawable round(int color, int radius) {
        return FormKit.round(getContext(), color, radius);
    }

    private int dp(float value) {
        return FormKit.dp(getContext(), value);
    }

    /** 分段进度条：总数少时一格一格画，太多时退化成连续条。 */
    private static final class SegmentView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int total;
        private int filled;

        SegmentView(Context context) {
            super(context);
        }

        void setProgress(int done, int total) {
            this.total = Math.max(0, total);
            this.filled = Math.max(0, Math.min(done, this.total));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) return;
            float radius = h / 2f;
            if (total <= 10) {
                int count = Math.max(1, total);
                float gap = dp(4);
                float cell = (w - gap * (count - 1)) / count;
                for (int i = 0; i < count; i++) {
                    paint.setColor(i < filled ? UiPalette.ACCENT : UiPalette.SOFT);
                    float left = i * (cell + gap);
                    canvas.drawRoundRect(new RectF(left, 0, left + cell, h), radius, radius, paint);
                }
            } else {
                paint.setColor(UiPalette.SOFT);
                canvas.drawRoundRect(new RectF(0, 0, w, h), radius, radius, paint);
                if (filled > 0) {
                    paint.setColor(UiPalette.ACCENT);
                    canvas.drawRoundRect(new RectF(0, 0, w * filled / total, h), radius, radius, paint);
                }
            }
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }

    /** 小自绘宝箱，跟首页那个同一个形状。 */
    private static final class ChestView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private boolean filled;

        ChestView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        void setFilled(boolean value) {
            filled = value;
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth();
            float h = getHeight();
            int body = filled ? 0xfff0b95e : UiPalette.DISABLED;
            paint.setColor(body);
            canvas.drawRoundRect(new RectF(w * 0.1f, h * 0.44f, w * 0.9f, h * 0.92f),
                    w * 0.1f, w * 0.1f, paint);
            paint.setColor(filled ? 0xffe2a343 : UiPalette.DISABLED);
            canvas.drawRoundRect(new RectF(w * 0.04f, h * 0.24f, w * 0.96f, h * 0.5f),
                    w * 0.1f, w * 0.1f, paint);
            paint.setColor(0xffffffff);
            canvas.drawRect(new RectF(w * 0.42f, h * 0.44f, w * 0.58f, h * 0.62f), paint);
        }
    }
}
