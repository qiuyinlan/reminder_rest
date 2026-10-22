package io.iftech.healthreminder;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class MembershipActivity extends Activity {
    static final String EXTRA_REASON = "membership_reason";
    static final String EXTRA_STATUS = "membership_status";
    private MembershipRepository repository;
    private LinearLayout body;
    private TextView status;
    private TextView refreshButton;
    private EditText email;
    private EditText code;
    private EditText redeemCode;
    private TextView sendButton;
    private TextView loginButton;
    private TextView redeemButton;
    private TextView logoutButton;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiPalette.applyWindow(getWindow());
        repository = MembershipAccess.repository(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackground(UiPalette.page());
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(22), dp(18), dp(22), dp(30));
        scroll.addView(body);
        setContentView(scroll);
        render();
        refreshStatus(getIntent().getStringExtra(EXTRA_STATUS));
    }

    private void render() {
        TextView back = text("‹", 30, UiPalette.INK, false);
        back.setGravity(Gravity.CENTER);
        back.setBackground(round(Color.WHITE, 14));
        back.setContentDescription("返回");
        back.setOnClickListener(v -> finish());
        body.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));

        TextView title = text("会员中心", 25, UiPalette.INK, true);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(24);
        body.addView(title, titleParams);
        String reason = getIntent().getStringExtra(EXTRA_REASON);
        TextView description = text(reason == null || reason.isEmpty()
                ? "登录后查看权益或兑换会员码" : reason, 14, UiPalette.MUTED, false);
        description.setPadding(0, dp(7), 0, 0);
        body.addView(description);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(17), dp(17), dp(17), dp(17));
        panel.setBackground(round(Color.WHITE, 14));
        LinearLayout.LayoutParams panelParams = new LinearLayout.LayoutParams(-1, -2);
        panelParams.topMargin = dp(20);
        body.addView(panel, panelParams);
        status = text("正在查询会员状态…", 15, UiPalette.INK, true);
        panel.addView(status);
        refreshButton = action("刷新会员状态", false);
        refreshButton.setOnClickListener(v -> refreshStatus(null));
        panel.addView(refreshButton, top(10));

        email = field("邮箱地址", android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        code = field("6 位邮箱验证码", android.text.InputType.TYPE_CLASS_NUMBER);
        sendButton = action("发送验证码", false);
        loginButton = action("登录", true);
        redeemCode = field("人工发放的兑换码", android.text.InputType.TYPE_CLASS_TEXT);
        redeemButton = action("兑换会员", false);
        logoutButton = action("退出登录", false);
        if (!repository.cachedEmail().isEmpty()) email.setText(repository.cachedEmail());
        sendButton.setOnClickListener(v -> sendCode());
        loginButton.setOnClickListener(v -> verifyCode());
        redeemButton.setOnClickListener(v -> redeem());
        logoutButton.setOnClickListener(v -> logout());
        body.addView(email, top(16));
        body.addView(sendButton, top(10));
        body.addView(code, top(10));
        body.addView(loginButton, top(10));
        body.addView(redeemCode, top(18));
        body.addView(redeemButton, top(10));
        body.addView(logoutButton, top(10));
        updateActions();
    }

    private void refreshStatus(String initialMessage) {
        if (!repository.isLoggedIn()) {
            status.setText(initialMessage == null ? "免费功能无需登录。登录后可查询会员权益或提交兑换码。" : initialMessage);
            updateActions();
            return;
        }
        status.setText("正在从服务端查询账号和会员权益…");
        run(() -> repository.refreshAccountAndEntitlement(), result -> {
            if (result.unauthorized) {
                status.setText(result.message);
                if (email.getText().length() == 0) email.setText(repository.cachedEmail());
            } else if (!result.success) {
                status.setText(result.message + "。可重试，免费功能仍可离线使用。");
            } else {
                status.setText((result.email == null ? repository.cachedEmail() : result.email)
                        + "\n" + (result.active ? "会员有效" : "未开通或已过期")
                        + (result.validUntil > 0 ? "\n有效期至 " + java.text.DateFormat.getDateTimeInstance()
                        .format(new java.util.Date(result.validUntil * 1000L)) : result.active ? "\n永久有效" : ""));
            }
            updateActions();
        });
    }

    private void sendCode() {
        String value = email.getText().toString().trim();
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(value).matches()) {
            status.setText("请输入有效的邮箱地址");
            return;
        }
        run(() -> repository.sendCode(value), result -> status.setText(result.message));
    }

    private void verifyCode() {
        String emailValue = email.getText().toString().trim();
        String codeValue = code.getText().toString().trim();
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(emailValue).matches() || !codeValue.matches("\\d{6}")) {
            status.setText("请填写有效邮箱和 6 位验证码");
            return;
        }
        run(() -> repository.verifyCode(emailValue, codeValue), result -> {
            status.setText(result.message);
            code.setText("");
            if (result.success) refreshStatus(null);
        });
    }

    private void redeem() {
        String value = redeemCode.getText().toString().trim();
        if (value.isEmpty()) { status.setText("请输入兑换码"); return; }
        run(() -> repository.redeem(value), result -> {
            status.setText(result.message + (result.success ? (result.active ? "，会员有效" : "，请刷新权益状态") : ""));
            redeemCode.setText("");
            updateActions();
            if (result.success) refreshStatus(null);
        });
    }

    private void logout() {
        run(() -> repository.logout(), result -> {
            status.setText(result.success ? "已退出登录" : result.message + "；本机账号状态已清除");
            code.setText("");
            redeemCode.setText("");
            updateActions();
        });
    }

    private void run(java.util.concurrent.Callable<MembershipRepository.Result> task,
                     java.util.function.Consumer<MembershipRepository.Result> done) {
        setBusy(true);
        new Thread(() -> {
            MembershipRepository.Result result;
            try { result = task.call(); }
            catch (Exception e) { result = MembershipRepository.Result.fail("操作失败，请重试", false); }
            MembershipRepository.Result finalResult = result;
            runOnUiThread(() -> {
                setBusy(false);
                done.accept(finalResult);
            });
        }, "membership-request").start();
    }

    private void setBusy(boolean busy) {
        sendButton.setEnabled(!busy);
        loginButton.setEnabled(!busy);
        redeemButton.setEnabled(!busy);
        logoutButton.setEnabled(!busy);
        refreshButton.setEnabled(!busy);
        status.setTextColor(busy ? UiPalette.MUTED : UiPalette.INK);
    }

    private void updateActions() {
        boolean loggedIn = repository.isLoggedIn();
        email.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        sendButton.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        code.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        loginButton.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        redeemCode.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        redeemButton.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        logoutButton.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
    }

    private EditText field(String hint, int inputType) {
        EditText field = new EditText(this);
        field.setSingleLine(true);
        field.setTextSize(15);
        field.setTextColor(UiPalette.INK);
        field.setHintTextColor(UiPalette.MUTED);
        field.setHint(hint);
        field.setInputType(inputType);
        field.setPadding(dp(13), 0, dp(13), 0);
        field.setBackground(round(UiPalette.PAGE, 10));
        return field;
    }

    private TextView action(String label, boolean primary) {
        TextView button = text(label, 15, primary ? Color.WHITE : UiPalette.ACCENT, true);
        button.setGravity(Gravity.CENTER);
        button.setBackground(round(primary ? UiPalette.ACCENT : UiPalette.SOFT, 10));
        return button;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private LinearLayout.LayoutParams top(int margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(48));
        params.topMargin = dp(margin);
        return params;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(UiPalette.surface(color));
        shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1), UiPalette.BORDER);
        return shape;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
