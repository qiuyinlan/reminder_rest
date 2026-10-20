package io.iftech.healthreminder;

import android.graphics.drawable.GradientDrawable;

final class UiPalette {
    static final int ACCENT = 0xff00d78a;
    static final int PAGE = 0xff031d17;
    static final int PAGE_LIGHT = 0xff073126;
    static final int SOFT = 0xff124635;
    static final int PALE = 0xff0c382b;
    static final int SURFACE = 0xe61a3a30;
    static final int INK = 0xfff2f8f3;
    static final int MUTED = 0xffb0c8ba;
    static final int BORDER = 0x667de1b1;
    static final int DISABLED = 0xff537568;

    static int surface(int color) {
        return color == 0xffffffff || color == 0xfffefefe ? SURFACE : color;
    }

    static GradientDrawable page() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{PAGE_LIGHT, PAGE, 0xff02140f});
    }

    private UiPalette() { }
}
