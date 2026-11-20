package io.iftech.healthreminder;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 复盘霸屏页：上面显示今天已经记下的那一条，下面接着写。
 * 写一句才能点「继续」，继续会把新内容接在原来那句后面。
 */
public final class ReviewReminderActivity extends Activity {
    private int ruleId = -1;
    private EditText input;
    private TextView continueButton;
    private TextView noteView;
    private TextView headerTitle;
    private TextView headerHint;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        UiPalette.applyWindow(getWindow());
        ruleId = getIntent().getIntExtra("rule_id", -1);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(UiPalette.PAGE);
        setContentView(page);

        final String note = ReviewStore.read(this);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(22), dp(30), dp(22), dp(18));
        GradientDrawable headBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, UiPalette.headGradient());
        float radius = dp(24);
        headBg.setCornerRadii(new float[]{0, 0, 0, 0, radius, radius, radius, radius});
        head.setBackground(headBg);

        // 第一行：左边「复盘」，右上角「复制」（只复制今天）
        LinearLayout topRow = new LinearLayout(this);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        topRow.addView(text("复盘", 12, UiPalette.MUTED, true),
                new LinearLayout.LayoutParams(0, -2, 1f));
        TextView copyAll = text("复制", 12, UiPalette.ACCENT, true);
        copyAll.setPadding(dp(10), dp(4), dp(2), dp(4));
        copyAll.setContentDescription("复制今天的复盘");
        copyAll.setOnClickListener(v -> ReviewDialogs.copyToday(this));
        topRow.addView(copyAll);
        head.addView(topRow, new LinearLayout.LayoutParams(-1, -2));

        headerTitle = text(note.isEmpty() ? "今天过得怎么样？" : "今天记的这一句", 24,
                UiPalette.INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(6);
        head.addView(headerTitle, titleParams);
        headerHint = text(note.isEmpty() ? "写一句就行，写完接着写。" : "下面接着写，会接在后面。",
                13, UiPalette.MUTED, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(6);
        head.addView(headerHint, hintParams);
        page.addView(head, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(16), dp(20), dp(20));

        // 今天已经记下的：只读展示（保存后原地刷新，不退出页面）
        noteView = text(note, 15, UiPalette.INK, false);
        noteView.setLineSpacing(dp(4), 1f);
        noteView.setPadding(dp(14), dp(12), dp(14), dp(12));
        noteView.setBackground(FormKit.round(this, UiPalette.PALE, 14));
        noteView.setVisibility(note.isEmpty() ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.bottomMargin = dp(14);
        body.addView(noteView, noteParams);

        input = new EditText(this);
        input.setTextSize(16);
        input.setTextColor(UiPalette.INK);
        input.setHintTextColor(UiPalette.MUTED);
        input.setGravity(Gravity.TOP | Gravity.LEFT);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(4);
        input.setMaxLines(8);
        input.setPadding(dp(14), dp(12), dp(14), dp(12));
        input.setBackground(FormKit.round(this, UiPalette.PALE, 14));
        input.setHint(note.isEmpty()
                ? "今天做了什么、心情、明天想改的一件事…"
                : "接着写点什么…");
        body.addView(input, new LinearLayout.LayoutParams(-1, -2));

        continueButton = text("继续", 16, Color.WHITE, true);
        continueButton.setGravity(Gravity.CENTER);
        continueButton.setPadding(0, dp(14), 0, dp(14));
        continueButton.setContentDescription("保存并继续");
        continueButton.setOnClickListener(v -> submit());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2);
        buttonParams.topMargin = dp(18);
        body.addView(continueButton, buttonParams);

        refreshButton();
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable s) { refreshButton(); }
        });

        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    /** 没写东西时按钮是灰的，点不动。 */
    private void refreshButton() {
        boolean ready = input != null && input.getText() != null
                && !input.getText().toString().trim().isEmpty();
        continueButton.setAlpha(ready ? 1f : 0.4f);
        continueButton.setEnabled(ready);
        continueButton.setBackground(FormKit.round(this,
                ready ? UiPalette.ACCENT : UiPalette.DISABLED, 14));
    }

    private void submit() {
        String extra = input.getText() == null ? "" : input.getText().toString().trim();
        if (extra.isEmpty()) {
            Toast.makeText(this, "先写一句再继续", Toast.LENGTH_SHORT).show();
            return;
        }
        String today = ReviewStore.read(this);
        boolean firstOfDay = today.isEmpty();
        // 接着写：拼在今天那句后面，不覆盖
        String note = firstOfDay ? extra : today + "\n" + extra;
        ReviewStore.save(this, note);
        // 只有今天第一条才记完成、推明天，避免多写几条把完成次数刷高
        if (firstOfDay) {
            long now = System.currentTimeMillis();
            int id = ruleId >= 0 ? ruleId : ReviewStore.RULE_ID;
            ReminderRecord.finish(this, java.util.UUID.randomUUID().toString(), "复盘",
                    ReminderRecord.KIND_DONE, now, now, note, ReminderSettings.MODULE_REVIEW);
            ReviewStore.markDone(this, id);
        }
        // 不退出页面：上面刷新成刚写好的内容，下面清空接着写下一条
        noteView.setText(note);
        noteView.setVisibility(View.VISIBLE);
        headerTitle.setText("今天记的这一句");
        headerHint.setText("下面接着写，会接在后面。");
        input.setText("");
        input.setHint("接着写点什么…");
        input.requestFocus();
        refreshButton();
        Toast.makeText(this, "已记下", Toast.LENGTH_SHORT).show();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        return FormKit.text(this, value, size, color, bold);
    }

    private int dp(float value) {
        return FormKit.dp(this, value);
    }
}
