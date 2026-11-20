package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Paint;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 复盘记录：点卡片先看今天写了什么，再接着往下写。 */
final class ReviewDialogs {
    interface OnSaved { void onSaved(); }

    private ReviewDialogs() { }

    static void showToday(final Activity activity, final OnSaved onSaved) {
        final String dayKey = ReviewStore.dayKey();
        final String note = ReviewStore.read(activity, dayKey);
        int pad = FormKit.dp(activity, 18);

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(pad, FormKit.dp(activity, 12), pad, FormKit.dp(activity, 4));

        // 顶行：左边日期，右上角「复制全部」
        LinearLayout head = new LinearLayout(activity);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText(friendlyToday());
        title.setTextSize(13);
        title.setTextColor(UiPalette.MUTED);
        title.setPaintFlags(title.getPaintFlags() | Paint.FAKE_BOLD_TEXT_FLAG);
        head.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));

        TextView copy = new TextView(activity);
        copy.setText("复制");
        copy.setTextSize(12);
        copy.setTextColor(UiPalette.ACCENT);
        copy.setPaintFlags(copy.getPaintFlags() | Paint.FAKE_BOLD_TEXT_FLAG);
        copy.setPadding(FormKit.dp(activity, 10), FormKit.dp(activity, 4),
                FormKit.dp(activity, 2), FormKit.dp(activity, 4));
        copy.setOnClickListener(v -> copyToday(activity));
        head.addView(copy);
        panel.addView(head, new LinearLayout.LayoutParams(-1, -2));

        // 今天写的那一句
        TextView body = new TextView(activity);
        body.setText(note.isEmpty() ? "今天还没写。" : note);
        body.setTextSize(15);
        body.setTextColor(note.isEmpty() ? UiPalette.MUTED : UiPalette.INK);
        body.setLineSpacing(FormKit.dp(activity, 4), 1f);
        body.setPadding(0, FormKit.dp(activity, 8), 0, FormKit.dp(activity, 8));
        panel.addView(body, new LinearLayout.LayoutParams(-1, -2));

        // 底下接着写
        final EditText more = FormKit.input(activity, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        more.setGravity(Gravity.TOP | Gravity.LEFT);
        more.setMinLines(2);
        more.setMaxLines(6);
        more.setHint(note.isEmpty() ? "写一句今天" : "接着写点什么…");
        panel.addView(more, new LinearLayout.LayoutParams(-1, -2));

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(panel)
                .setNegativeButton("关闭", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String extra = more.getText() == null ? "" : more.getText().toString().trim();
                    if (extra.isEmpty()) {
                        Toast.makeText(activity, "写一句再保存", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    // 接着写：拼在原来那句后面，不覆盖
                    String merged = note.isEmpty() ? extra : note + "\n" + extra;
                    ReviewStore.save(activity, dayKey, merged);
                    ReviewStore.markDone(activity, ReviewStore.RULE_ID);
                    dialog.dismiss();
                    if (onSaved != null) onSaved.onSaved();
                }));
        FormKit.showStyled(activity, dialog);
    }

    /** 复制全部复盘（完整历史，导出去玩的时候用）。 */
    static void copyAll(Context context) {
        String text = ReviewStore.allAsText(context);
        if (text.isEmpty()) {
            Toast.makeText(context, "还没有复盘", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("复盘", text));
        Toast.makeText(context, "已复制 " + ReviewStore.all(context).size() + " 天复盘",
                Toast.LENGTH_SHORT).show();
    }

    /** 只复制今天那一句（复盘页、悬浮窗的「复制」用）。 */
    static void copyToday(Context context) {
        String note = ReviewStore.read(context).trim();
        if (note.isEmpty()) {
            Toast.makeText(context, "今天还没写", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("复盘", note));
        Toast.makeText(context, "已复制今天的复盘", Toast.LENGTH_SHORT).show();
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
