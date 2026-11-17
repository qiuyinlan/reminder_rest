package io.iftech.healthreminder;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * done：今天做成的事，一行一句。
 *
 * 只留今天，明天自动清空；右上角一键复制全部，方便贴到日记里。
 */
public final class DoneActivity extends Activity {
    private LinearLayout header;
    private LinearLayout container;

    private final List<String> lines = new ArrayList<>();
    private String draft = "";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());

        FrameLayout shell = new FrameLayout(this);
        shell.setBackgroundColor(UiPalette.PAGE);
        setContentView(shell);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(UiPalette.page());
        shell.addView(root, new FrameLayout.LayoutParams(-1, -1));

        header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(dp(16), dp(8), dp(16), dp(24));
        scroll.addView(container, new ScrollView.LayoutParams(-1, -2));
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        lines.clear();
        lines.addAll(DoneStore.load(this));
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        List<String> fresh = DoneStore.load(this);
        if (fresh.size() != lines.size()) {
            lines.clear();
            lines.addAll(fresh);
            render();
        }
    }

    // -------------------------------------------------------------- 页面骨架

    private void render() {
        if (header.getChildCount() > 0) header.removeAllViews();
        header.addView(buildHeader());

        container.removeAllViews();
        container.addView(buildAddCard());

        if (lines.isEmpty()) {
            container.addView(emptyCard());
        } else {
            container.addView(buildListCard());
        }
        container.addView(footerText());
    }

    private View buildHeader() {
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(20), dp(14), dp(20), dp(16));

        android.graphics.drawable.GradientDrawable bg =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                        UiPalette.headGradient());
        float radius = dp(24);
        bg.setCornerRadii(new float[]{0, 0, 0, 0, radius, radius, radius, radius});
        head.setBackground(bg);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = text("‹ 返回", 14, UiPalette.MUTED, true);
        back.setPadding(dp(2), dp(6), dp(10), dp(6));
        back.setOnClickListener(v -> finish());
        row.addView(back);

        row.addView(text("done", 24, UiPalette.INK, true),
                new LinearLayout.LayoutParams(0, -2, 1f));

        if (!lines.isEmpty()) {
            TextView copy = text("复制", 12, UiPalette.ACCENT, true);
            copy.setPadding(dp(10), dp(6), dp(2), dp(6));
            copy.setOnClickListener(v -> copyAll());
            row.addView(copy);
        }
        head.addView(row);

        TextView meta = text(DoneStore.todayLabel() + " · 今天做成了 " + lines.size() + " 件",
                12, UiPalette.MUTED, false);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = dp(4);
        head.addView(meta, metaParams);
        return head;
    }

    private View buildAddCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(FormKit.round(this, Color.WHITE, 18));
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(12);
        card.setLayoutParams(cardParams);

        final EditText input = FormKit.input(this, InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setHint("刚做完的一件事");
        input.setText(draft);
        input.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable s) {
                draft = s == null ? "" : s.toString();
            }
        });
        card.addView(input, new LinearLayout.LayoutParams(0, dp(48), 1f));

        TextView add = text("记上", 13, Color.WHITE, true);
        add.setGravity(Gravity.CENTER);
        add.setBackground(FormKit.round(this, UiPalette.ACCENT, 14));
        add.setOnClickListener(v -> {
            String line = draft == null ? "" : draft.trim();
            if (line.isEmpty()) {
                Toast.makeText(this, "先写一句", Toast.LENGTH_SHORT).show();
                return;
            }
            lines.add(0, line);
            draft = "";
            input.setText("");
            persist();
            render();
        });
        LinearLayout.LayoutParams addParams = new LinearLayout.LayoutParams(dp(64), dp(44));
        addParams.leftMargin = dp(8);
        card.addView(add, addParams);
        return card;
    }

    private View emptyCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(FormKit.round(this, Color.WHITE, 18));
        card.setPadding(dp(18), dp(22), dp(18), dp(22));
        card.addView(text("今天还没记", 14, UiPalette.INK, true));
        TextView tip = text("做成一件就写一行，回头看着挺踏实的。", 12, UiPalette.MUTED, false);
        tip.setPadding(0, dp(6), 0, 0);
        card.addView(tip);
        return card;
    }

    private View buildListCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(FormKit.round(this, Color.WHITE, 18));
        card.setPadding(dp(14), dp(6), dp(14), dp(10));
        card.setElevation(dp(2));

        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) card.addView(divider());
            card.addView(buildLineRow(i, lines.get(i)));
        }
        return card;
    }

    private View buildLineRow(final int index, final String line) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView mark = text("✓", 12, UiPalette.ACCENT, true);
        mark.setGravity(Gravity.CENTER);
        row.addView(mark, new LinearLayout.LayoutParams(dp(20), dp(20)));

        TextView name = text(line, 14, UiPalette.INK, false);
        name.setMaxLines(3);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nameParams.leftMargin = dp(8);
        row.addView(name, nameParams);

        TextView del = text("×", 15, UiPalette.MUTED, true);
        del.setPadding(dp(8), dp(2), dp(2), dp(2));
        del.setContentDescription("删掉这一行");
        del.setOnClickListener(v -> {
            if (index < lines.size()) lines.remove(index);
            persist();
            render();
        });
        row.addView(del);

        // 长按整行：改这一句
        row.setOnLongClickListener(v -> {
            showEditDialog(index, line);
            return true;
        });
        return row;
    }

    /** 长按改一句。 */
    private void showEditDialog(final int index, final String line) {
        final EditText input = FormKit.textInput(this, "做成的事", line);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(6), dp(20), 0);
        box.addView(input, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("改一下这句")
                .setView(box)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = input.getText() == null
                            ? "" : input.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(this, "不能是空的", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (index >= 0 && index < lines.size()) lines.set(index, value);
                    persist();
                    dialog.dismiss();
                    render();
                }));
        FormKit.showStyled(this, dialog);
    }

    private View divider() {
        View line = new View(this);
        line.setBackgroundColor(UiPalette.hairline());
        line.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(1)));
        return line;
    }

    private View footerText() {
        TextView tip = text("长按一行可以改；这一页每天清空一次，复制后想留就贴到别处。",
                11, UiPalette.MUTED, false);
        tip.setPadding(dp(4), dp(14), dp(4), 0);
        return tip;
    }

    // ------------------------------------------------------------------ 交互

    private void copyAll() {
        StringBuilder builder = new StringBuilder();
        builder.append(DoneStore.todayLabel()).append("\n");
        for (String line : lines) builder.append("- ").append(line).append("\n");
        try {
            ClipboardManager manager =
                    (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (manager != null) {
                manager.setPrimaryClip(ClipData.newPlainText("done", builder.toString().trim()));
                Toast.makeText(this, "复制了 " + lines.size() + " 行", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (RuntimeException ignored) { }
        Toast.makeText(this, "复制失败，手动选一下吧", Toast.LENGTH_SHORT).show();
    }

    private void persist() {
        DoneStore.save(this, lines);
    }

    // ------------------------------------------------------------------ 工具

    private TextView text(String value, int size, int color, boolean bold) {
        return FormKit.text(this, value, size, color, bold);
    }

    private int dp(float value) {
        return FormKit.dp(this, value);
    }
}
