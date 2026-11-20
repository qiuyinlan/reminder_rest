package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 复盘记录：一条一条存，长按某条单独改 / 删。 */
final class ReviewDialogs {
    interface OnSaved { void onSaved(); }

    private ReviewDialogs() { }

    /** 改某一条（改成空等于删掉）。 */
    static void editEntry(final Activity activity, final String dayKey, final int index,
                          String text, final OnSaved onSaved) {
        final EditText input = FormKit.input(activity, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setGravity(Gravity.TOP | Gravity.LEFT);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setText(text);
        if (input.getText() != null) input.setSelection(input.getText().length());

        LinearLayout box = new LinearLayout(activity);
        box.setPadding(FormKit.dp(activity, 20), FormKit.dp(activity, 6),
                FormKit.dp(activity, 20), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(friendlyDay(dayKey))
                .setView(box)
                .setNegativeButton("取消", null)
                .setNeutralButton("删除", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String value = input.getText() == null ? "" : input.getText().toString().trim();
                if (value.isEmpty()) {
                    Toast.makeText(activity, "写一句再保存", Toast.LENGTH_SHORT).show();
                    return;
                }
                ReviewStore.update(activity, dayKey, index, value);
                dialog.dismiss();
                if (onSaved != null) onSaved.onSaved();
            });
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                ReviewStore.remove(activity, dayKey, index);
                dialog.dismiss();
                if (onSaved != null) onSaved.onSaved();
            });
        });
        FormKit.showStyled(activity, dialog);
    }

    /** 给某一天补一条新的。 */
    static void addEntry(final Activity activity, final String dayKey, final OnSaved onSaved) {
        final EditText input = FormKit.input(activity, InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setGravity(Gravity.TOP | Gravity.LEFT);
        input.setMinLines(3);
        input.setMaxLines(8);
        input.setHint("这一天做了什么、心情、想改的一件事…");

        LinearLayout box = new LinearLayout(activity);
        box.setPadding(FormKit.dp(activity, 20), FormKit.dp(activity, 6),
                FormKit.dp(activity, 20), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(friendlyDay(dayKey))
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String value = input.getText() == null ? "" : input.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(activity, "写一句再保存", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    ReviewStore.add(activity, dayKey, value);
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

    /** 只复制今天那几条（复盘页、悬浮窗的「复制」用）。 */
    static void copyToday(Context context) {
        String text = ReviewStore.read(context).trim();
        if (text.isEmpty()) {
            Toast.makeText(context, "今天还没写", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("复盘", text));
        Toast.makeText(context, "已复制今天的复盘", Toast.LENGTH_SHORT).show();
    }

    /** 复制某一天那几条（统计页用）。 */
    static void copyDay(Context context, String dayKey) {
        String text = ReviewStore.read(context, dayKey).trim();
        if (text.isEmpty()) {
            Toast.makeText(context, "这一天还没写", Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard =
                (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("复盘", text));
        Toast.makeText(context, "已复制 " + friendlyDay(dayKey) + " 的复盘", Toast.LENGTH_SHORT).show();
    }

    /** 2026-11-20 → 11月20日 周四。 */
    static String friendlyDay(String key) {
        try {
            java.util.Date date = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                    .parse(key);
            return date == null ? key
                    : new java.text.SimpleDateFormat("M月d日 EEE", java.util.Locale.CHINA).format(date);
        } catch (Exception ignored) {
            return key;
        }
    }
}
