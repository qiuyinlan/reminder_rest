package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.util.List;

/** 心情记录弹窗：新增和编辑共用一套样式，首页和统计页都能调。 */
final class MoodDialogs {
    interface OnSaved { void onSaved(); }

    private MoodDialogs() { }

    /** 今日心情面板：先看今天记了几条，再决定补一条还是改一条。 */
    static void showToday(final Activity activity, final OnSaved onSaved) {
        long dayStart = ReminderRecord.dayStart(System.currentTimeMillis());
        final List<MoodStore.Entry> today = MoodStore.onDay(activity, dayStart);

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(FormKit.dp(activity, 18), FormKit.dp(activity, 4),
                FormKit.dp(activity, 18), FormKit.dp(activity, 4));

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(today.isEmpty() ? "今天还没记心情" : "今天记了 " + today.size() + " 次")
                .setView(panel)
                .setNegativeButton("关闭", null)
                .setPositiveButton("记一条", null)
                .create();

        if (today.isEmpty()) {
            android.widget.TextView empty = new android.widget.TextView(activity);
            empty.setText("想到就记一句，不用憋着。");
            empty.setTextSize(13);
            empty.setTextColor(UiPalette.MUTED);
            empty.setPadding(0, FormKit.dp(activity, 6), 0, FormKit.dp(activity, 6));
            panel.addView(empty);
        } else {
            for (final MoodStore.Entry entry : today) {
                LinearLayout row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(0, FormKit.dp(activity, 8), 0, 0);
                android.widget.TextView mood = new android.widget.TextView(activity);
                mood.setText(clock(entry.at) + "  " + entry.mood);
                mood.setTextSize(14);
                mood.setTextColor(UiPalette.INK);
                row.addView(mood);
                if (!entry.relief.isEmpty()) {
                    android.widget.TextView relief = new android.widget.TextView(activity);
                    relief.setText("缓解：" + entry.relief);
                    relief.setTextSize(12);
                    relief.setTextColor(UiPalette.MUTED);
                    relief.setPadding(0, FormKit.dp(activity, 3), 0, 0);
                    row.addView(relief);
                }
                row.setOnClickListener(v -> {
                    dialog.dismiss();
                    edit(activity, entry, onSaved);
                });
                panel.addView(row);
            }
        }

        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    dialog.dismiss();
                    add(activity, onSaved);
                }));
        FormKit.showStyled(activity, dialog);
    }

    private static String clock(long at) {
        return new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                .format(new java.util.Date(at));
    }

    static void add(final Activity activity, final OnSaved onSaved) {
        final EditText mood = FormKit.textInput(activity, "现在什么心情？比如「有点烦躁」", "");
        final EditText relief = FormKit.textInput(activity, "做什么能缓解（选填）", "");
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(FormKit.dp(activity, 20), FormKit.dp(activity, 6),
                FormKit.dp(activity, 20), 0);
        box.addView(mood, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams reliefParams = new LinearLayout.LayoutParams(-1, -2);
        reliefParams.topMargin = FormKit.dp(activity, 8);
        box.addView(relief, reliefParams);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("记一下心情")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String value = mood.getText() == null ? "" : mood.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(activity, "先写一句心情", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    MoodStore.add(activity, value,
                            relief.getText() == null ? "" : relief.getText().toString());
                    dialog.dismiss();
                    if (onSaved != null) onSaved.onSaved();
                }));
        FormKit.showStyled(activity, dialog);
    }

    static void edit(final Activity activity, final MoodStore.Entry entry, final OnSaved onSaved) {
        final EditText mood = FormKit.textInput(activity, "心情", entry.mood);
        final EditText relief = FormKit.textInput(activity, "做什么能缓解（选填）", entry.relief);
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(FormKit.dp(activity, 20), FormKit.dp(activity, 6),
                FormKit.dp(activity, 20), 0);
        box.addView(mood, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams reliefParams = new LinearLayout.LayoutParams(-1, -2);
        reliefParams.topMargin = FormKit.dp(activity, 8);
        box.addView(relief, reliefParams);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("改一下这条")
                .setView(box)
                .setNegativeButton("取消", null)
                .setNeutralButton("删除", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String value = mood.getText() == null ? "" : mood.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(activity, "心情不能空着", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    MoodStore.update(activity, entry.withMood(value,
                            relief.getText() == null ? "" : relief.getText().toString()));
                    dialog.dismiss();
                    if (onSaved != null) onSaved.onSaved();
                }));
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, "删除", (d, which) -> {
            MoodStore.delete(activity, entry.id);
            dialog.dismiss();
            if (onSaved != null) onSaved.onSaved();
        });
        FormKit.showStyled(activity, dialog);
    }
}
