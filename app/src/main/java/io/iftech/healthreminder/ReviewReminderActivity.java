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
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 复盘霸屏页：复用身体提醒那套「到点直接弹整页」的形态，
 * 但只做一件事——写下一句今天的话，写完「继续」才能点。
 */
public final class ReviewReminderActivity extends Activity {
    private int ruleId = -1;
    private EditText input;
    private TextView continueButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        UiPalette.applyWindow(getWindow());
        ruleId = getIntent().getIntExtra("rule_id", -1);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(UiPalette.PAGE);
        setContentView(page);

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.VERTICAL);
        head.setPadding(dp(22), dp(30), dp(22), dp(18));
        GradientDrawable headBg = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM, UiPalette.headGradient());
        float radius = dp(24);
        headBg.setCornerRadii(new float[]{0, 0, 0, 0, radius, radius, radius, radius});
        head.setBackground(headBg);

        TextView overline = text("每日复盘", 12, UiPalette.MUTED, true);
        head.addView(overline);
        TextView title = text("今天过得怎么样？", 24, UiPalette.INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(6);
        head.addView(title, titleParams);
        TextView hint = text("写一句就行，写完才能继续。", 13, UiPalette.MUTED, false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = dp(6);
        head.addView(hint, hintParams);
        page.addView(head, new LinearLayout.LayoutParams(-1, -2));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(16), dp(20), dp(20));

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
        input.setHint("今天做了什么、心情、明天想改的一件事…");
        input.setText(ReviewStore.read(this));
        if (input.getText() != null) input.setSelection(input.getText().length());
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
        String note = input.getText() == null ? "" : input.getText().toString().trim();
        if (note.isEmpty()) {
            Toast.makeText(this, "先写一句再继续", Toast.LENGTH_SHORT).show();
            return;
        }
        ReviewStore.save(this, note);
        long now = System.currentTimeMillis();
        int id = ruleId >= 0 ? ruleId : ReviewStore.RULE_ID;
        ReminderRecord.finish(this, java.util.UUID.randomUUID().toString(), "每日复盘",
                ReminderRecord.KIND_DONE, now, now, note, ReminderSettings.MODULE_REVIEW);
        ReminderSettings.markCompleted(this, id, 1);
        // 写完立刻刷新下次提醒时间，不必等下一次重排
        ReminderScheduler.update(this);
        finish();
    }

    private TextView text(String value, int size, int color, boolean bold) {
        return FormKit.text(this, value, size, color, bold);
    }

    private int dp(float value) {
        return FormKit.dp(this, value);
    }
}
