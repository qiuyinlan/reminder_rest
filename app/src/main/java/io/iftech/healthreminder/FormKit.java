package io.iftech.healthreminder;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 表单控件工具箱：把 ItemsActivity 里那套编辑弹窗的视觉语言抽出来，
 * 让任务 / 卡片的新界面跟「添加事项」长得一模一样。
 */
final class FormKit {
    private FormKit() { }

    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context context, String value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    /** 右下角圆形加号按钮。 */
    static TextView fab(Context context, Runnable onClick) {
        TextView fab = new TextView(context);
        fab.setText("＋");
        fab.setTextSize(26);
        fab.setTextColor(Color.WHITE);
        fab.setGravity(Gravity.CENTER);
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(UiPalette.ACCENT);
        fab.setBackground(shape);
        fab.setElevation(dp(context, 8));
        fab.setContentDescription("新建");
        fab.setOnClickListener(v -> onClick.run());
        return fab;
    }

    static GradientDrawable round(Context context, int color, int radius) {        GradientDrawable shape = new GradientDrawable();
        shape.setColor(UiPalette.surface(color));
        shape.setCornerRadius(dp(context, radius));
        if (color == Color.WHITE) shape.setStroke(dp(context, 1), UiPalette.BORDER);
        return shape;
    }

    static EditText input(Context context, int inputType) {
        EditText input = new EditText(context);
        input.setInputType(inputType);
        input.setTextColor(UiPalette.INK);
        input.setHintTextColor(UiPalette.MUTED);
        input.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
        input.setMinHeight(dp(context, 48));
        input.setBackground(UiPalette.inputBackground(context, UiPalette.PALE, 10));
        return input;
    }

    static TextView editorLabel(Context context, String value, boolean separated) {
        TextView label = text(context, value, 13, UiPalette.MUTED, true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = separated ? dp(context, 14) : 0;
        params.bottomMargin = dp(context, 6);
        label.setLayoutParams(params);
        return label;
    }

    /** 分段选择器底槽（浅绿胶囊）。 */
    static LinearLayout segmentGroup(Context context) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setPadding(dp(context, 5), dp(context, 4), dp(context, 5), dp(context, 4));
        group.setBackground(round(context, UiPalette.PALE, 12));
        return group;
    }

    static TextView segmentOption(Context context, String value, boolean selected) {
        TextView option = text(context, value, 13, selected ? UiPalette.INK : UiPalette.MUTED, selected);
        option.setGravity(Gravity.CENTER);
        option.setPadding(dp(context, 8), dp(context, 6), dp(context, 8), dp(context, 6));
        option.setBackground(round(context, selected ? Color.WHITE : Color.TRANSPARENT, 10));
        return option;
    }

    static void setSegmentSelected(TextView option, boolean selected) {
        option.setTextColor(selected ? UiPalette.INK : UiPalette.MUTED);
        option.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        option.setBackground(round(option.getContext(), selected ? Color.WHITE : Color.TRANSPARENT, 10));
    }

    static Button controlButton(Context context) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setTextColor(UiPalette.ACCENT);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinHeight(dp(context, 46));
        button.setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8));
        button.setBackground(round(context, UiPalette.SOFT, 10));
        return button;
    }

    /** 浅底时间字段：上小标题 + 大号时间值。 */
    static LinearLayout timeField(Context context, String title, String value) {
        LinearLayout field = new LinearLayout(context);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        field.setBackground(round(context, UiPalette.PALE, 10));
        field.addView(text(context, title, 11, UiPalette.MUTED, false));
        TextView valueView = text(context, value, 17, UiPalette.INK, true);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(-1, -2);
        valueParams.topMargin = dp(context, 2);
        field.addView(valueView, valueParams);
        field.setContentDescription(title + " " + value);
        field.setClickable(true);
        return field;
    }

    static void updateTimeField(LinearLayout field, String value) {
        if (field.getChildCount() < 2) return;
        ((TextView) field.getChildAt(1)).setText(value);
    }

    static void showStyled(Context context, AlertDialog dialog) {
        showStyled(context, dialog,
                Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(context, 36), dp(context, 480)));
    }

    static void showStyled(Context context, AlertDialog dialog, int width) {
        dialog.show();
        styleDialog(context, dialog, width);
    }

    static void styleDialog(Context context, AlertDialog dialog, int width) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(round(context, Color.WHITE, 20));
            window.setDimAmount(0.34f);
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
        }
        int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(UiPalette.INK);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        TextView message = dialog.findViewById(android.R.id.message);
        if (message != null) {
            message.setTextColor(UiPalette.MUTED);
            message.setLineSpacing(dp(context, 2), 1f);
        }
        for (int buttonId : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
                AlertDialog.BUTTON_NEUTRAL}) {
            Button button = dialog.getButton(buttonId);
            if (button == null) continue;
            button.setAllCaps(false);
            button.setTextSize(14);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setTextColor(buttonId == AlertDialog.BUTTON_POSITIVE ? UiPalette.ACCENT : UiPalette.MUTED);
        }
        if (dialog.getListView() != null) {
            dialog.getListView().setBackgroundColor(UiPalette.SURFACE);
            dialog.getListView().setDivider(new ColorDrawable(UiPalette.BORDER));
            dialog.getListView().setDividerHeight(dp(context, 1));
            dialog.getListView().setSelector(new ColorDrawable(UiPalette.SOFT));
        }
    }

    static EditText textInput(Context context, String hint, String value) {
        EditText input = input(context, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setHint(hint);
        input.setText(value == null ? "" : value);
        return input;
    }
}
