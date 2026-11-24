package io.iftech.healthreminder;

import android.animation.ValueAnimator;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

/**
 * Q 弹动效工具箱：一条过冲弹簧曲线走天下。
 *
 * 纪律：
 * - 只动 scale / translation / alpha，不动宽高和布局位置
 * - 每个动画 0.25~0.5 秒内结束，弹一下就停
 * - 同屏同时最多 2 个动画
 */
final class AnimKit {
    /** cubic-bezier(0.34, 1.56, 0.64, 1)：冲过目标约 10% 再弹回。 */
    static final PathInterpolator SPRING = new PathInterpolator(0.34f, 1.56f, 0.64f, 1f);
    /** 收尾用的平滑曲线（数字滚动等不适合过冲的地方）。 */
    static final PathInterpolator EASE_OUT = new PathInterpolator(0.22f, 1f, 0.36f, 1f);

    private AnimKit() { }

    static float dp(View v, float value) {
        return value * v.getResources().getDisplayMetrics().density;
    }

    /** 按钮/可点行按压：按下缩到 0.92，松手弹簧回 1。 */
    static void press(final View view) {
        if (view == null) return;
        view.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().cancel();
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(90).start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1f).scaleY(1f)
                            .setDuration(300).setInterpolator(SPRING).start();
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    /** 卡片波浪入场：从下方 12dp 浮上来 + 淡入，每张延后 60ms。 */
    static void waveIn(final View view, int index) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(dp(view, 12));
        view.animate()
                .alpha(1f).translationY(0f)
                .setStartDelay(index * 60L)
                .setDuration(420)
                .setInterpolator(SPRING)
                .start();
    }

    /** 圆点/图标爆开：先放大到 overshoot 再弹簧回落。 */
    static void pop(final View view, float overshoot) {
        if (view == null) return;
        view.animate().cancel();
        view.animate().scaleX(overshoot).scaleY(overshoot)
                .setDuration(120)
                .withEndAction(() -> view.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(380)
                        .setInterpolator(SPRING)
                        .start())
                .start();
    }

    /** 数字滚动：旧数字上滑淡出，新数字从下滑入。 */
    static void roll(final TextView view, final String value) {
        if (view == null) return;
        if (value.contentEquals(view.getText())) return;
        final float h = view.getHeight() > 0 ? view.getHeight() : dp(view, 20);
        view.animate().cancel();
        view.animate()
                .translationY(-h * 0.6f).alpha(0f)
                .setDuration(125)
                .setInterpolator(EASE_OUT)
                .withEndAction(() -> {
                    view.setText(value);
                    view.setTranslationY(h * 0.6f);
                    view.animate().translationY(0f).alpha(1f)
                            .setDuration(125)
                            .setInterpolator(EASE_OUT)
                            .start();
                })
                .start();
    }

    /** 从下方 40dp 蹦进来，落地后回调（宝箱出场用）。 */
    static void bounceIn(final View view, final Runnable onLanded) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(dp(view, 40));
        view.animate()
                .alpha(1f).translationY(0f)
                .setDuration(450)
                .setInterpolator(SPRING)
                .withEndAction(() -> {
                    if (onLanded != null) view.postDelayed(onLanded, 200);
                })
                .start();
    }

    /** 宝箱抖动：左右摆几下再停（弹簧曲线）。 */
    static void shake(final View view) {
        if (view == null) return;
        view.animate().cancel();
        view.setRotation(0f);
        android.animation.ObjectAnimator anim = android.animation.ObjectAnimator
                .ofFloat(view, "rotation", 0f, -9f, 9f, -6f, 6f, -3f, 0f);
        anim.setDuration(620);
        anim.setInterpolator(SPRING);
        anim.start();
    }

    /** 轻轻的呼吸：只给同屏一个循环元素用。 */
    static ValueAnimator breathe(final View view, long periodMs) {
        final ValueAnimator animator = ValueAnimator.ofFloat(1f, 1.03f, 1f);
        animator.setDuration(periodMs);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setInterpolator(EASE_OUT);
        animator.addUpdateListener(a -> {
            float s = (float) a.getAnimatedValue();
            view.setScaleX(s);
            view.setScaleY(s);
        });
        animator.start();
        return animator;
    }
}
