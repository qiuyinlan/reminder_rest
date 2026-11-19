package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 复盘记录：点卡片先看今天写了什么，再决定补写还是改。 */
final class ReviewDialogs {
    interface OnSaved { void onSaved(); }

    private ReviewDialogs() { }

    static void showToday(final Activity activity, final OnSaved onSaved) {
        String dayKey = ReviewStore.dayKey();
        final String note = ReviewStore.read(activity, dayKey);

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(FormKit.dp(activity, 18), FormKit.dp(activity, 4),
                FormKit.dp(activity, 18), FormKit.dp(activity, 4));

        TextView body = new TextView(activity);
        body.setText(note.isEmpty() ? "今天还没写。" : note);
        body.setTextSize(15);
        body.setTextColor(note.isEmpty() ? UiPalette.MUTED : UiPalette.INK);
        body.setLineSpacing(FormKit.dp(activity, 4), 1f);
        body.setPadding(0, FormKit.dp(activity, 6), 0, FormKit.dp(activity, 6));
        panel.addView(body, new LinearLayout.LayoutParams(-1, -2));

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(friendlyToday())
                .setView(panel)
                .setNegativeButton("关闭", null)
                .setPositiveButton(note.isEmpty() ? "写一句" : "改一句", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    dialog.dismiss();
                    edit(activity, dayKey, note, onSaved);
                }));
        FormKit.showStyled(activity, dialog);
    }

    static void edit(final Activity activity, final String dayKey, String note, final OnSaved onSaved) {
        final EditText input = FormKit.input(activity, android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setGravity(android.view.Gravity.TOP | android.view.Gravity.LEFT);
        input.setMinLines(4);
        input.setMaxLines(10);
        input.setText(note);
        if (input.getText() != null) input.setSelection(input.getText().length());
        input.setHint("今天做了什么、心情、明天想改的一件事…");

        LinearLayout box = new LinearLayout(activity);
        box.setPadding(FormKit.dp(activity, 20), FormKit.dp(activity, 6),
                FormKit.dp(activity, 20), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(dayKey)
                .setView(box)
                .setNegativeButton("取消", null)
                .setNeutralButton("清空", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(activity, "写一句再保存", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ReviewStore.save(activity, dayKey, value);
                    dialog.dismiss();
                    if (onSaved != null) onSaved.onSaved();
                }));
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "清空", (d, which) -> {
            ReviewStore.save(activity, dayKey, "");
            dialog.dismiss();
            if (onSaved != null) onSaved.onSaved();
        });
        FormKit.showStyled(activity, dialog);
    }

    private static String friendlyToday() {
        return new java.text.SimpleDateFormat("M月d日 EEEE", java.util.Locale.CHINA)
                .format(new java.util.Date()) + " · 复盘";
    }
}
