package io.iftech.healthreminder;

import android.graphics.drawable.GradientDrawable;

/**
 * 浅绿色主题调色板：页面底色是柔和的薄荷绿渐变，卡片为白色玻璃质感，
 * 主色是清晰可读的深绿，整体清爽、轻盈。
 */
final class UiPalette {
    /** 主色：清晰可读的中深绿，用于强调文字、按钮、进度条。 */
    static final int ACCENT = 0xff1f8a5f;
    /** 页面底色（薄荷绿）。 */
    static final int PAGE = 0xffeef8f1;
    /** 页面渐变起始色（更浅一点的薄荷白）。 */
    static final int PAGE_LIGHT = 0xfff6fcf8;
    /** 浅绿标签底色。 */
    static final int SOFT = 0xffd7f0e1;
    /** 更淡的绿色面板底色。 */
    static final int PALE = 0xffe7f7ee;
    /** 卡片底色：白色玻璃卡片。 */
    static final int SURFACE = 0xffffffff;
    /** 主文字色：深墨绿。 */
    static final int INK = 0xff133226;
    /** 次级文字色：灰绿。 */
    static final int MUTED = 0xff5c7c6e;
    /** 卡片描边：浅绿。 */
    static final int BORDER = 0xffcfe9da;
    /** 关闭态/禁用态：浅灰绿。 */
    static final int DISABLED = 0xffbcd3c7;

    /** 暖色警示（未完成/待设置），浅底上的可读琥珀色。 */
    static final int WARN = 0xffa4672a;
    /** 警示底色。 */
    static final int WARN_SOFT = 0xfffff2e2;
    /** 警示描边。 */
    static final int WARN_BORDER = 0xffeccba2;

    static int surface(int color) {
        return color == 0xffffffff || color == 0xfffefefe ? SURFACE : color;
    }

    static GradientDrawable page() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{PAGE_LIGHT, PAGE, 0xffe9f6ef});
    }

    private UiPalette() { }
}
