package io.iftech.healthreminder;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Locale;

/**
 * 全局统一的滚轮选择器（和霸屏页「临时有事」里的滚轮同一个样子）：
 * 选时间一律是「时 / 分 / 秒」滚轮，选日期一律是「年 / 月 / 日」滚轮，
 * 不再使用系统自带的 TimePickerDialog / DatePickerDialog。
 *
 * 另外 NumberPicker 在 AppCompat 深色主题下默认用白色文字，
 * 放进浅色对话框后一滚动就“看不见”（数字发白），这里统一改成主题文字色。
 */
final class WheelPicker {
    interface OnSeconds { void onPicked(int secondsOfDay); }

    interface OnDate { void onPicked(Calendar value); }

    private WheelPicker() { }

    // ---------------------------------------------------------------- 滚轮本体

    /** 浅色主题用浅色上下文创建滚轮，保证数字是深色；深色主题相反。 */
    private static Context wheelContext(Context context) {
        return new android.view.ContextThemeWrapper(context,
                UiPalette.isDark() ? android.R.style.Theme_Material : android.R.style.Theme_Material_Light);
    }

    /** 建一个统一外观的滚轮。twoDigits=true 时显示成 05、09 这种补零样式。 */
    static NumberPicker wheel(Context context, int min, int max, int value, boolean twoDigits) {
        NumberPicker picker = new NumberPicker(wheelContext(context));
        picker.setMinValue(min);
        picker.setMaxValue(max);
        picker.setWrapSelectorWheel(max - min > 1);
        if (twoDigits) picker.setFormatter(number -> String.format(Locale.ROOT, "%02d", number));
        picker.setValue(Math.max(min, Math.min(max, value)));
        style(picker);
        return picker;
    }

    /** 选中值由内部 EditText 画，其余滚动项由内部画笔画，两处都涂成主题文字色。 */
    static void style(NumberPicker picker) {
        picker.setBackgroundColor(Color.TRANSPARENT);
        picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
        for (int i = 0; i < picker.getChildCount(); i++) {
            View child = picker.getChildAt(i);
            if (child instanceof EditText) {
                EditText value = (EditText) child;
                value.setTextColor(UiPalette.INK);
                value.setTextSize(20);
                value.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
                value.setBackgroundColor(Color.TRANSPARENT);
            }
        }
        try {
            java.lang.reflect.Field wheelPaint = NumberPicker.class.getDeclaredField("mSelectorWheelPaint");
            wheelPaint.setAccessible(true);
            Object paint = wheelPaint.get(picker);
            if (paint instanceof Paint) ((Paint) paint).setColor(UiPalette.INK);
        } catch (Exception ignored) {
            // 部分厂商皮肤隐藏了该字段，滚轴文字颜色退回主题配色。
        }
        picker.invalidate();
    }

    /** 把滚轮和单位文字竖排成一列放进横向容器。 */
    private static void addColumn(Context context, LinearLayout parent, NumberPicker picker, String unit) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER);
        column.addView(picker, new LinearLayout.LayoutParams(dp(context, 74), dp(context, 120)));
        TextView label = new TextView(context);
        label.setText(unit);
        label.setTextSize(12);
        label.setTextColor(UiPalette.MUTED);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        label.setGravity(Gravity.CENTER);
        column.addView(label, new LinearLayout.LayoutParams(-1, dp(context, 22)));
        parent.addView(column, new LinearLayout.LayoutParams(0, -2, 1));
    }

    // ---------------------------------------------------------------- 选时间

    /**
     * 「时 : 分 [: 秒]」滚轮弹窗。
     *
     * @param secondsOfDay 初始值：当天第几秒
     * @param withSeconds  是否要「秒」这一列
     */
    static AlertDialog time(Context context, String title, String confirmLabel,
                            int secondsOfDay, boolean withSeconds, final OnSeconds onPicked) {
        int safe = ((secondsOfDay % 86400) + 86400) % 86400;
        final NumberPicker hours = wheel(context, 0, 23, safe / 3600, true);
        final NumberPicker minutes = wheel(context, 0, 59, safe / 60 % 60, true);
        final NumberPicker seconds = withSeconds ? wheel(context, 0, 59, safe % 60, true) : null;

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(context, 6), dp(context, 10), dp(context, 6), dp(context, 10));
        card.setBackground(round(context, UiPalette.PALE, 16));
        addColumn(context, card, hours, "时");
        addColumn(context, card, minutes, "分");
        if (seconds != null) addColumn(context, card, seconds, "秒");

        final TextView preview = new TextView(context);
        preview.setGravity(Gravity.CENTER);
        preview.setTextSize(22);
        preview.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        preview.setTextColor(UiPalette.COUNTDOWN_INK);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2);
        previewParams.bottomMargin = dp(context, 10);
        previewParams.topMargin = dp(context, 4);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 4));
        content.addView(preview, previewParams);
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));

        final Runnable refresh = () -> preview.setText(seconds == null
                ? String.format(Locale.ROOT, "%02d:%02d", hours.getValue(), minutes.getValue())
                : String.format(Locale.ROOT, "%02d:%02d:%02d",
                hours.getValue(), minutes.getValue(), seconds.getValue()));
        refresh.run();
        NumberPicker.OnValueChangeListener changed = (picker, oldValue, newValue) -> refresh.run();
        hours.setOnValueChangedListener(changed);
        minutes.setOnValueChangedListener(changed);
        if (seconds != null) seconds.setOnValueChangedListener(changed);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton(confirmLabel, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int total = hours.getValue() * 3600 + minutes.getValue() * 60
                        + (seconds == null ? 0 : seconds.getValue());
                onPicked.onPicked(total);
                dialog.dismiss();
            });
            styleDialog(context, dialog);
        });
        return dialog;
    }

    // ---------------------------------------------------------------- 选日期

    /** 「年 / 月 / 日」滚轮弹窗。 */
    static AlertDialog date(Context context, String title, Calendar value, final OnDate onPicked) {
        final Calendar base = (Calendar) (value == null ? Calendar.getInstance() : value).clone();
        final int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        final NumberPicker year = wheel(context, currentYear - 3, currentYear + 3,
                base.get(Calendar.YEAR), false);
        final NumberPicker month = wheel(context, 1, 12, base.get(Calendar.MONTH) + 1, true);
        final NumberPicker day = wheel(context, 1, 31, base.get(Calendar.DAY_OF_MONTH), true);
        year.setFormatter(number -> number + " 年");
        month.setFormatter(number -> number + " 月");
        day.setFormatter(number -> number + " 日");

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(context, 6), dp(context, 10), dp(context, 6), dp(context, 10));
        card.setBackground(round(context, UiPalette.PALE, 16));
        addColumn(context, card, year, "年");
        addColumn(context, card, month, "月");
        addColumn(context, card, day, "日");

        final TextView preview = new TextView(context);
        preview.setGravity(Gravity.CENTER);
        preview.setTextSize(18);
        preview.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        preview.setTextColor(UiPalette.INK);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(-1, -2);
        previewParams.bottomMargin = dp(context, 10);
        previewParams.topMargin = dp(context, 4);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 4));
        content.addView(preview, previewParams);
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));

        final boolean[] adjusting = {false};
        final Runnable refresh = () -> {
            if (adjusting[0]) return;
            adjusting[0] = true;
            Calendar probe = Calendar.getInstance();
            probe.set(year.getValue(), month.getValue() - 1, 1);
            int maxDay = probe.getActualMaximum(Calendar.DAY_OF_MONTH);
            if (day.getMaxValue() != maxDay) {
                int wanted = Math.min(day.getValue(), maxDay);
                day.setMaxValue(maxDay);
                day.setValue(wanted);
            }
            adjusting[0] = false;
            preview.setText(String.format(Locale.CHINA, "%d 年 %d 月 %d 日",
                    year.getValue(), month.getValue(), day.getValue()));
        };
        NumberPicker.OnValueChangeListener changed = (picker, oldValue, newValue) -> {
            refresh.run();
            if (!adjusting[0]) day.invalidate();
        };
        year.setOnValueChangedListener(changed);
        month.setOnValueChangedListener(changed);
        day.setOnValueChangedListener(changed);
        refresh.run();

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                Calendar result = (Calendar) base.clone();
                result.set(year.getValue(), month.getValue() - 1, day.getValue());
                onPicked.onPicked(result);
                dialog.dismiss();
            });
            styleDialog(context, dialog);
        });
        return dialog;
    }

    // ---------------------------------------------------------------- 外观

    /** 统一弹窗外观：主题圆角卡片 + 按钮配色。 */
    static void styleDialog(Context context, AlertDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(round(context, UiPalette.SURFACE, 20));
            window.setDimAmount(0.34f);
            int width = Math.min(context.getResources().getDisplayMetrics().widthPixels - dp(context, 36),
                    dp(context, 460));
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        int titleId = context.getResources().getIdentifier("alertTitle", "id", "android");
        TextView title = titleId == 0 ? null : dialog.findViewById(titleId);
        if (title != null) {
            title.setTextColor(UiPalette.INK);
            title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        for (int buttonId : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE,
                AlertDialog.BUTTON_NEUTRAL}) {
            android.widget.Button button = dialog.getButton(buttonId);
            if (button == null) continue;
            button.setAllCaps(false);
            button.setTextSize(14);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setTextColor(buttonId == AlertDialog.BUTTON_POSITIVE ? UiPalette.ACCENT : UiPalette.MUTED);
        }
    }

    static GradientDrawable round(Context context, int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(UiPalette.surface(color));
        shape.setCornerRadius(dp(context, radius));
        if (color == Color.WHITE) shape.setStroke(dp(context, 1), UiPalette.BORDER);
        return shape;
    }

    static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
