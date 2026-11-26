package io.iftech.healthreminder;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 任务行的统一外观。任务页、白板、「现在」页都从这里取，
 * 免得同一件事在三处长得不一样。
 */
final class TaskRowKit {
    static final int CHECK_SIZE = 24;
    static final int TITLE_SIZE = 16;

    private TaskRowKit() { }

    /** 圆形勾选框：未完成是空心圈，完成是实心主色 + 对勾。 */
    static TextView circleCheck(Context context, boolean done, View.OnClickListener onClick) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(done ? UiPalette.ACCENT : Color.TRANSPARENT);
        if (!done) shape.setStroke(FormKit.dp(context, 2), UiPalette.DISABLED);
        TextView box = FormKit.text(context, done ? "✓" : "", 12,
                done ? Color.WHITE : UiPalette.DISABLED, true);
        box.setGravity(Gravity.CENTER);
        box.setBackground(shape);
        box.setContentDescription(done ? "取消完成" : "标记完成");
        if (onClick != null) box.setOnClickListener(onClick);
        return box;
    }

    /** 立刻把勾选框设成完成/未完成外观（用于点击后先显示打勾，延迟刷新）。 */
    static void applyCircleCheck(Context context, TextView box, boolean done) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(done ? UiPalette.ACCENT : Color.TRANSPARENT);
        if (!done) shape.setStroke(FormKit.dp(context, 2), UiPalette.DISABLED);
        box.setText(done ? "\u2713" : "");
        box.setTextColor(done ? Color.WHITE : UiPalette.DISABLED);
        box.setBackground(shape);
        box.setContentDescription(done ? "\u53d6\u6d88\u5b8c\u6210" : "\u6807\u8bb0\u5b8c\u6210");
    }

    /** 任务标题：完成后变灰 + 删除线。 */
    static TextView title(Context context, String value, boolean done) {
        TextView view = FormKit.text(context, value, TITLE_SIZE,
                done ? UiPalette.MUTED : UiPalette.INK, false);
        view.setMaxLines(1);
        view.setEllipsize(TextUtils.TruncateAt.END);
        if (done) {
            view.setPaintFlags(view.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }
        return view;
    }

    /** 任务主行：统一的高度和内边距。 */
    static LinearLayout mainRow(Context context) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(FormKit.dp(context, 52));
        row.setPadding(FormKit.dp(context, 14), FormKit.dp(context, 12),
                FormKit.dp(context, 14), FormKit.dp(context, 12));
        return row;
    }

    static LinearLayout.LayoutParams checkParams(Context context) {
        return new LinearLayout.LayoutParams(
                FormKit.dp(context, CHECK_SIZE), FormKit.dp(context, CHECK_SIZE));
    }

    static LinearLayout.LayoutParams titleParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
        params.leftMargin = FormKit.dp(context, 12);
        return params;
    }

    /** 分组头：色点 + 名字 + 计数胶囊 + 右侧虚线 + 可选动作文字。 */
    static LinearLayout sectionHeader(Context context, String name, String count,
                                      String action, View.OnClickListener onAction) {
        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(FormKit.dp(context, 6), FormKit.dp(context, 14),
                FormKit.dp(context, 6), FormKit.dp(context, 8));

        View dot = new View(context);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(UiPalette.ACCENT);
        dot.setBackground(dotBg);
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                FormKit.dp(context, 6), FormKit.dp(context, 6));
        dotParams.rightMargin = FormKit.dp(context, 8);
        header.addView(dot, dotParams);

        header.addView(FormKit.text(context, name, 13, UiPalette.INK, true),
                new LinearLayout.LayoutParams(-2, -2));

        if (count != null && !count.isEmpty()) {
            TextView countView = FormKit.text(context, count, 11, UiPalette.MUTED, true);
            GradientDrawable chipBg = new GradientDrawable();
            chipBg.setCornerRadius(FormKit.dp(context, 8));
            chipBg.setColor(UiPalette.PALE);
            countView.setBackground(chipBg);
            countView.setPadding(FormKit.dp(context, 7), FormKit.dp(context, 1),
                    FormKit.dp(context, 7), FormKit.dp(context, 1));
            LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-2, -2);
            countParams.leftMargin = FormKit.dp(context, 8);
            header.addView(countView, countParams);
        }

        View line = new View(context);
        line.setBackgroundColor(UiPalette.BORDER);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(0, FormKit.dp(context, 1), 1f);
        lineParams.leftMargin = FormKit.dp(context, 12);
        lineParams.rightMargin = FormKit.dp(context, 12);
        header.addView(line, lineParams);

        if (action != null) {
            TextView actionView = FormKit.text(context, action, 12, UiPalette.ACCENT, true);
            actionView.setPadding(FormKit.dp(context, 4), FormKit.dp(context, 2),
                    FormKit.dp(context, 2), FormKit.dp(context, 2));
            if (onAction != null) actionView.setOnClickListener(onAction);
            header.addView(actionView, new LinearLayout.LayoutParams(-2, -2));
        }
        return header;
    }

    /** 白色分组卡片，圆角 16，细描边。 */
    static LinearLayout groupCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, UiPalette.glassGradient());
        bg.setCornerRadius(FormKit.dp(context, 18));
        bg.setStroke(FormKit.dp(context, 1), UiPalette.hairline());
        card.setBackground(bg);
        card.setClipToOutline(true);
        card.setElevation(FormKit.dp(context, 2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = FormKit.dp(context, 6);
        card.setLayoutParams(params);
        return card;
    }

    static View divider(Context context) {
        View line = new View(context);
        line.setBackgroundColor(UiPalette.hairline());
        line.setLayoutParams(new LinearLayout.LayoutParams(-1, FormKit.dp(context, 1)));
        return line;
    }
}
