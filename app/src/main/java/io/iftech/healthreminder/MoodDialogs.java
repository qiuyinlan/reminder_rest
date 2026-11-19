package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

/** 心情记录弹窗：新增和编辑共用一套样式，首页和统计页都能调。 */
final class MoodDialogs {
    interface OnSaved { void onSaved(); }

    private MoodDialogs() { }

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
