package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 分类选择器：卡片式弹窗，选中态用主色，禁止原生 setItems。
 * 选完即生效；右上角「＋」新建分类后自动选中新建的那个。
 */
final class CategoryPicker {
    interface OnPick {
        void onPick(TaskCategories.Category category);
    }

    private CategoryPicker() { }

    static void show(final Activity activity, final int selectedId, final OnPick onPick) {
        final List<TaskCategories.Category> cats = TaskCategories.all(activity);

        LinearLayout sheet = new LinearLayout(activity);
        sheet.setOrientation(LinearLayout.VERTICAL);
        sheet.setPadding(FormKit.dp(activity, 14), FormKit.dp(activity, 2),
                FormKit.dp(activity, 14), FormKit.dp(activity, 12));

        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);

        final AlertDialog[] holder = new AlertDialog[1];
        for (TaskCategories.Category cat : cats) {
            list.addView(row(activity, cat, cat.id == selectedId, v -> {
                if (holder[0] != null) holder[0].dismiss();
                onPick.onPick(cat);
            }));
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(list);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1,
                cats.size() > 6 ? FormKit.dp(activity, 320) : -2);
        sheet.addView(scroll, scrollParams);

        // 新建分类
        View hairline = new View(activity);
        hairline.setBackgroundColor(UiPalette.BORDER);
        LinearLayout.LayoutParams hairlineParams = new LinearLayout.LayoutParams(-1,
                FormKit.dp(activity, 1));
        hairlineParams.topMargin = FormKit.dp(activity, 6);
        sheet.addView(hairline, hairlineParams);

        TextView add = FormKit.text(activity, "＋ 新建分类", 14, UiPalette.ACCENT, true);
        add.setGravity(Gravity.CENTER_VERTICAL);
        add.setMinHeight(FormKit.dp(activity, 48));
        add.setPadding(FormKit.dp(activity, 12), 0, 0, 0);
        add.setBackground(ripple(activity));
        add.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            askNewName(activity, onPick);
        });
        sheet.addView(add, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("分类")
                .setView(sheet)
                .setNegativeButton("取消", null)
                .create();
        holder[0] = dialog;
        FormKit.showStyled(activity, dialog);
    }

    private static View row(final Activity activity, final TaskCategories.Category cat,
                           boolean selected, View.OnClickListener onClick) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(FormKit.dp(activity, 48));
        row.setPadding(FormKit.dp(activity, 12), 0, FormKit.dp(activity, 12), 0);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = FormKit.dp(activity, 2);
        row.setLayoutParams(params);

        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(FormKit.dp(activity, 12));
        if (selected) {
            bg.setColor(UiPalette.SOFT);
            bg.setStroke(FormKit.dp(activity, 1), UiPalette.ACCENT);
        } else {
            bg.setColor(Color.TRANSPARENT);
        }
        row.setBackground(bg);

        TextView name = FormKit.text(activity, cat.name, 15,
                selected ? UiPalette.ACCENT : UiPalette.INK, selected);
        name.setSingleLine(true);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        if (selected) {
            TextView check = FormKit.text(activity, "✓", 14, UiPalette.ACCENT, true);
            check.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            row.addView(check);
        }

        row.setOnClickListener(onClick);
        if (!selected) {
            row.setForeground(ripple(activity));
        }
        return row;
    }

    /** 编辑页里的分类胶囊按钮，点开选择器。 */
    static TextView chip(Activity activity, String name) {
        TextView chip = FormKit.text(activity, name + "  ›", 13, UiPalette.INK, true);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(FormKit.dp(activity, 10));
        bg.setColor(UiPalette.PALE);
        chip.setBackground(bg);
        chip.setGravity(Gravity.CENTER);
        chip.setMinHeight(FormKit.dp(activity, 36));
        chip.setPadding(FormKit.dp(activity, 12), 0, FormKit.dp(activity, 12), 0);
        chip.setClickable(true);
        return chip;
    }

    /** 选中后刷新胶囊文字。 */
    static void updateChip(TextView chip, String name) {
        chip.setText(name + "  ›");
    }

    private static void askNewName(final Activity activity, final OnPick onPick) {
        final EditText input = FormKit.textInput(activity, "分类名", "");
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(FormKit.dp(activity, 18), FormKit.dp(activity, 10),
                FormKit.dp(activity, 18), 0);
        form.addView(input, new LinearLayout.LayoutParams(-1, -2));

        final AlertDialog[] holder = new AlertDialog[1];
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("新建分类")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("创建", null)
                .create();
        holder[0] = dialog;
        dialog.setOnShowListener(ignored ->
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(activity, "写个名字", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    TaskCategories.Category cat = TaskCategories.add(activity, name);
                    holder[0].dismiss();
                    if (onPick != null) onPick.onPick(cat);
                }));
        FormKit.showStyled(activity, dialog);
    }

    private static android.graphics.drawable.Drawable ripple(Activity activity) {
        TypedValue value = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return activity.getDrawable(value.resourceId);
    }
}
