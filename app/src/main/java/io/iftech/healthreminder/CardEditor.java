package io.iftech.healthreminder;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.ContextThemeWrapper;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

/** 新建 / 编辑卡片的统一弹窗，卡片页和卡片集详情页共用。 */
final class CardEditor {
    interface OnSaved { void onSaved(); }

    private CardEditor() { }

    /** presetDeck 不为空时隐藏卡片集输入框（在某个卡片集内部添加）。 */
    static void create(Activity activity, String presetDeck, final OnSaved onSaved) {
        ContextThemeWrapper context = new ContextThemeWrapper(activity, R.style.ItemsDialogTheme);
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(activity, 18), dp(activity, 6), dp(activity, 18), dp(activity, 4));

        final EditText deckName;
        if (presetDeck == null) {
            form.addView(FormKit.editorLabel(activity, "卡片集名称", false));
            deckName = FormKit.textInput(activity, "默认：我的卡片", "");
            selectAllOnTap(deckName);
            form.addView(deckName, new LinearLayout.LayoutParams(-1, -2));
        } else {
            deckName = null;
        }

        form.addView(FormKit.editorLabel(activity, "正面内容", presetDeck != null));
        EditText front = FormKit.input(activity,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        front.setMinLines(3);
        front.setMaxLines(8);
        form.addView(front, new LinearLayout.LayoutParams(-1, -2));

        form.addView(FormKit.editorLabel(activity, "背面答案（选填）", true));
        EditText back = FormKit.input(activity,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        back.setMinLines(2);
        back.setMaxLines(6);
        form.addView(back, new LinearLayout.LayoutParams(-1, -2));

        CheckBox asTask = new CheckBox(activity);
        asTask.setText("同时加进任务清单（可在霸屏页勾选完成）");
        asTask.setTextSize(13);
        asTask.setTextColor(UiPalette.INK);
        LinearLayout.LayoutParams taskParams = new LinearLayout.LayoutParams(-1, -2);
        taskParams.topMargin = dp(activity, 10);
        form.addView(asTask, taskParams);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("新建卡片")
                .setView(form)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String text = front.getText().toString().trim();
                    if (text.isEmpty()) {
                        front.setError("正面内容不能为空");
                        return;
                    }
                    String deck = presetDeck != null ? presetDeck
                            : deckName == null ? "" : deckName.getText().toString().trim();
                    CardDeck.addManualCard(activity, deck, text, back.getText().toString(),
                            "", asTask.isChecked());
                    Toast.makeText(activity, "已新建卡片", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    onSaved.onSaved();
                }));
        FormKit.showStyled(activity, dialog);
    }

    static void edit(Activity activity, final CardDeck deck, final CardDeck.Card card, final OnSaved onSaved) {
        ContextThemeWrapper context = new ContextThemeWrapper(activity, R.style.ItemsDialogTheme);
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(activity, 18), dp(activity, 6), dp(activity, 18), dp(activity, 4));

        form.addView(FormKit.editorLabel(activity, "正面内容", false));
        EditText front = FormKit.input(activity,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        front.setText(card.question);
        front.setMinLines(3);
        front.setMaxLines(8);
        form.addView(front, new LinearLayout.LayoutParams(-1, -2));

        form.addView(FormKit.editorLabel(activity, "背面答案（选填）", true));
        EditText back = FormKit.input(activity,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        back.setText(card.answer);
        back.setMinLines(2);
        back.setMaxLines(6);
        form.addView(back, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle("编辑卡片")
                .setView(form)
                .setNegativeButton("取消", null)
                .setNeutralButton("删除", (d, w) -> confirmDelete(activity, deck, card, onSaved))
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String text = front.getText().toString().trim();
                    if (text.isEmpty()) {
                        front.setError("正面内容不能为空");
                        return;
                    }
                    CardDeck.updateCard(activity, deck.id, card.id, text,
                            back.getText().toString(), card.note);
                    Toast.makeText(activity, "已保存", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    onSaved.onSaved();
                }));
        FormKit.showStyled(activity, dialog);
    }

    private static void confirmDelete(Activity activity, final CardDeck deck,
                                      final CardDeck.Card card, final OnSaved onSaved) {
        new AlertDialog.Builder(new ContextThemeWrapper(activity, R.style.ItemsDialogTheme))
                .setTitle("删除这张卡片？")
                .setMessage(card.question)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    CardDeck.deleteCard(activity, deck.id, card.id);
                    Toast.makeText(activity, "已删除卡片", Toast.LENGTH_SHORT).show();
                    onSaved.onSaved();
                })
                .show();
    }

    private static void selectAllOnTap(final EditText input) {
        input.setOnFocusChangeListener((view, focused) -> {
            if (focused) input.selectAll();
        });
        input.setOnClickListener(view -> input.selectAll());
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
