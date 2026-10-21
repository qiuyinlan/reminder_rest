package io.iftech.healthreminder;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 「选择颜色」弹窗：一屏放完全部颜色，点一下即选用并关闭。
 * 取值沿用 ReminderSettings.Rule.color：-1 表示自动（按事项名称配色），
 * 0..7 对应 UiPalette 的类目色。
 */
final class ItemColorPicker {
    interface OnColorPicked {
        void onPicked(int kind);
    }

    private static final int AUTO = -1;
    private static final int COLUMNS = 3;
    private static final String[] LABELS = {"翠绿", "水蓝", "靛蓝", "琥珀", "薰衣草", "珊瑚", "森绿", "梅紫"};
    private static final int[] KINDS = {
            UiPalette.CAT_WALK, UiPalette.CAT_WATER, UiPalette.CAT_EYES, UiPalette.CAT_NECK,
            UiPalette.CAT_RELAX, UiPalette.CAT_OTHER, UiPalette.CAT_COMPANION, UiPalette.CAT_FOCUS
    };

    private ItemColorPicker() { }

    /** 事项颜色在列表里的说明文字，例如「自动 · 跟随名称」「翠绿」。 */
    static String describe(int value) {
        int index = labelIndex(value);
        return index < 0 ? "自动 · 跟随名称" : LABELS[index];
    }

    /**
     * 构建选择弹窗（未 show）。调用方负责 show 以及自己的弹窗样式。
     *
     * @param context 弹窗上下文（可传主题包装后的 Context）
     * @param current 当前颜色（-1 为自动）
     * @param onPick  选中回调（拿到新的颜色值）
     */
    static AlertDialog build(final Context context, int current, final OnColorPicked onPick) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(px(context, 14), px(context, 6), px(context, 14), px(context, 2));

        final int[] choices = new int[KINDS.length + 1];
        choices[0] = AUTO;
        System.arraycopy(KINDS, 0, choices, 1, KINDS.length);

        final AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("选择颜色")
                .setView(root)
                .setNegativeButton("取消", null)
                .create();

        for (int start = 0; start < choices.length; start += COLUMNS) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int column = 0; column < COLUMNS; column++) {
                int index = start + column;
                if (index >= choices.length) {
                    row.addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1));
                    continue;
                }
                int kind = choices[index];
                row.addView(cell(context, kind, kind == current, dialog, onPick),
                        new LinearLayout.LayoutParams(0, -2, 1));
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = px(context, 4);
            root.addView(row, params);
        }

        dialog.setOnShowListener(ignored -> styleDialog(context, dialog));
        return dialog;
    }

    private static View cell(Context context, int kind, boolean selected,
                             final AlertDialog dialog, final OnColorPicked onPick) {
        final int value = kind;
        LinearLayout view = new LinearLayout(context);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setGravity(Gravity.CENTER_HORIZONTAL);
        view.setPadding(px(context, 4), px(context, 8), px(context, 4), px(context, 6));
        view.setBackground(selected ? selectedBackground(context) : plainBackground(context));

        // 色块里只放纯色圆点，选中的那个显示对勾，不再逐个重复事项首字。
        TextView swatch = new TextView(context);
        swatch.setGravity(Gravity.CENTER);
        swatch.setTextSize(15);
        swatch.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        swatch.setText(selected ? "✓" : "");
        swatch.setTextColor(Color.WHITE);
        swatch.setBackground(swatchBackground(kind, selected));
        view.addView(swatch, new LinearLayout.LayoutParams(px(context, 44), px(context, 44)));

        TextView label = new TextView(context);
        label.setText(kind < 0 ? "自动" : LABELS[labelIndex(kind)]);
        label.setTextSize(11);
        label.setTextColor(selected ? UiPalette.ACCENT : UiPalette.MUTED);
        label.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.topMargin = px(context, 5);
        view.addView(label, labelParams);

        view.setOnClickListener(v -> {
            onPick.onPicked(value);
            dialog.dismiss();
        });
        return view;
    }

    private static GradientDrawable swatchBackground(int kind, boolean selected) {
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        if (kind < 0) {
            background.setColor(UiPalette.SOFT);
            background.setStroke(2, UiPalette.BORDER);
        } else {
            background.setColor(UiPalette.categoryFill(kind));
            background.setStroke(2, UiPalette.categoryStroke(kind));
        }
        if (selected) {
            background.setColor(kind < 0 ? UiPalette.ACCENT : UiPalette.categoryInk(kind));
            background.setStroke(2, UiPalette.ACCENT);
        }
        return background;
    }

    private static GradientDrawable plainBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(px(context, 14));
        background.setColor(Color.TRANSPARENT);
        return background;
    }

    private static GradientDrawable selectedBackground(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(px(context, 14));
        background.setColor(UiPalette.SOFT);
        background.setStroke(px(context, 1), UiPalette.BORDER);
        return background;
    }

    private static int labelIndex(int kind) {
        for (int i = 0; i < KINDS.length; i++) if (KINDS[i] == kind) return i;
        return -1;
    }

    private static void styleDialog(Context context, AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(px(context, 20));
            background.setColor(UiPalette.SURFACE);
            window.setBackgroundDrawable(background);
            window.setDimAmount(0.34f);
            int width = Math.min(context.getResources().getDisplayMetrics().widthPixels - px(context, 42),
                    px(context, 420));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(UiPalette.INK);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        android.widget.Button cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (cancel != null) {
            cancel.setAllCaps(false);
            cancel.setTextSize(14);
            cancel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            cancel.setTextColor(UiPalette.MUTED);
        }
    }

    private static int px(Context context, int dp) {
        return Math.round(dp * context.getResources().getDisplayMetrics().density);
    }
}
