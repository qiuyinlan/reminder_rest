package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;
import android.widget.Toast;

/** 喝水滚轮：10–500ml 滑动选，默认停在上次选的量。 */
final class WaterDialogs {
    interface OnSaved { void onSaved(); }

    private WaterDialogs() { }

    static void showAmount(final Activity activity, final OnSaved onSaved) {
        int initial = WaterLog.lastAmount(activity);
        final NumberPicker picker = WheelPicker.wheel(activity,
                WaterLog.MIN_ML / WaterLog.STEP_ML, WaterLog.MAX_ML / WaterLog.STEP_ML,
                initial / WaterLog.STEP_ML, false);
        picker.setFormatter(value -> (value * WaterLog.STEP_ML) + " ml");

        final TextView preview = new TextView(activity);
        preview.setGravity(Gravity.CENTER);
        preview.setTextSize(28);
        preview.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        preview.setTextColor(UiPalette.ACCENT);
        preview.setText(initial + " ml");

        LinearLayout wheelRow = new LinearLayout(activity);
        wheelRow.setGravity(Gravity.CENTER);
        wheelRow.addView(picker, new LinearLayout.LayoutParams(dp(activity, 150), dp(activity, 150)));

        picker.setOnValueChangedListener((p, oldValue, newValue) ->
                preview.setText((newValue * WaterLog.STEP_ML) + " ml"));

        TextView hint = new TextView(activity);
        hint.setText("上下滑动，一次记 10–500ml");
        hint.setTextSize(12);
        hint.setTextColor(UiPalette.MUTED);
        hint.setGravity(Gravity.CENTER);

        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(activity, 18), dp(activity, 8), dp(activity, 18), dp(activity, 4));
        content.addView(preview, new LinearLayout.LayoutParams(-1, -2));
        content.addView(wheelRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(activity, 4);
        content.addView(hint, hintParams);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("喝了多少水")
                .setView(content)
                .setNegativeButton("取消", null)
                .setPositiveButton("记下", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                int ml = picker.getValue() * WaterLog.STEP_ML;
                WaterLog.add(activity, ml);
                Toast.makeText(activity, "记下 " + ml + " ml", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                if (onSaved != null) onSaved.onSaved();
            });
            WheelPicker.styleDialog(activity, dialog);
        });
        dialog.show();
    }

    private static int dp(Activity activity, int value) {
        return FormKit.dp(activity, value);
    }
}
