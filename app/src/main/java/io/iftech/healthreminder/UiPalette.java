package io.iftech.healthreminder;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.Window;

/**
 * 主题调色板：支持「深绿」（原版磨砂深绿）与「浅绿」（清新薄荷绿）两套主题。
 * 所有颜色字段都是可变静态字段，切换主题时会整体替换；各页面在构建 UI 时
 * 读取当前值，所以切换后需要重建界面（设置页会自动重启到主页）。
 */
final class UiPalette {
    static final String THEME_DARK = "deep_green";
    static final String THEME_LIGHT = "light_green";
    private static final String KEY_THEME = "theme_palette";
    private static final String PREFS = "reminder_settings";

    // ---- 当前生效颜色（由 applyTheme 整体替换）----
    static int ACCENT = 0xff1f8a5f;
    static int PAGE = 0xffeef8f1;
    static int PAGE_LIGHT = 0xfff6fcf8;
    static int SOFT = 0xffd7f0e1;
    static int PALE = 0xffe7f7ee;
    static int SURFACE = 0xffffffff;
    static int INK = 0xff133226;
    static int MUTED = 0xff5c7c6e;
    static int BORDER = 0xffcfe9da;
    static int DISABLED = 0xffbcd3c7;
    static int WARN = 0xffa4672a;
    static int WARN_SOFT = 0xfffff2e2;
    static int WARN_BORDER = 0xffeccba2;
    /** 陪伴类目色（统计页汇总、时间线）。 */
    static int COMPANION = 0xff8a5bab;

    private static boolean darkTheme;
    private static int[] pageGradient = {0xfff6fcf8, 0xffeef8f1, 0xffe9f6ef};
    private static int[] headGradient = {0xffe6f7ee, 0xffddf1ea, 0xffe9f6f2};
    private static int[] catInk = {0xff247a88, 0xff4859b0, 0xff1f7d55, 0xff94650f, 0xff76439a, 0xffaa4a3d, 0xff1f8a5f, 0xff8a5bab};
    private static int[] catFill = {0xffd9f0f3, 0xffe3e8fb, 0xffd8f2e4, 0xfffdeed2, 0xfff0e2f7, 0xfffbe3e1, 0xffe2f5ea, 0xfff0e2f7};
    private static int[] catStroke = {0xffa8dbe2, 0xffb8c3ee, 0xffa7dfc2, 0xffeed0a2, 0xffdbbfe9, 0xfff0beb8, 0xffc3ead9, 0xffdbbfe9};

    private UiPalette() { }

    static void load(Context context) {
        applyTheme(prefs(context).getString(KEY_THEME, THEME_LIGHT));
    }

    static String theme(Context context) {
        return prefs(context).getString(KEY_THEME, THEME_LIGHT);
    }

    static void setTheme(Context context, String theme) {
        prefs(context).edit().putString(KEY_THEME, theme).apply();
        applyTheme(theme);
    }

    static boolean isDark() {
        return darkTheme;
    }

    static void applyTheme(String theme) {
        if (THEME_DARK.equals(theme)) {
            darkTheme = true;
            ACCENT = 0xff00d78a;
            PAGE = 0xff031d17;
            PAGE_LIGHT = 0xff073126;
            SOFT = 0xff124635;
            PALE = 0xff0c382b;
            SURFACE = 0xe61a3a30;
            INK = 0xfff2f8f3;
            MUTED = 0xffb0c8ba;
            BORDER = 0x667de1b1;
            DISABLED = 0xff537568;
            WARN = 0xffe8987f;
            WARN_SOFT = 0xff402c24;
            WARN_BORDER = 0xffaa7152;
            COMPANION = 0xffd7a6eb;
            pageGradient = new int[]{PAGE_LIGHT, PAGE, 0xff02140f};
            headGradient = new int[]{0xff0b3325, 0xff12352c, 0xff142d3c};
            catInk = new int[]{0xff70d6dd, 0xffa8b9ff, 0xff67d49b, 0xffffce78, 0xffd7a6eb, 0xffffa497, 0xff77d5bf, 0xffd7a6eb};
            catFill = new int[]{0xff173b40, 0xff292f4c, 0xff173d2d, 0xff49371f, 0xff3b2947, 0xff482b28, 0xff1e3b33, 0xff3b2947};
            catStroke = new int[]{0xff39717a, 0xff59648d, 0xff34724f, 0xff8a6935, 0xff765187, 0xff89514a, 0xff3f8377, 0xff765187};
        } else {
            darkTheme = false;
            ACCENT = 0xff1f8a5f;
            PAGE = 0xffeef8f1;
            PAGE_LIGHT = 0xfff6fcf8;
            SOFT = 0xffd7f0e1;
            PALE = 0xffe7f7ee;
            SURFACE = 0xffffffff;
            INK = 0xff133226;
            MUTED = 0xff5c7c6e;
            BORDER = 0xffcfe9da;
            DISABLED = 0xffbcd3c7;
            WARN = 0xffa4672a;
            WARN_SOFT = 0xfffff2e2;
            WARN_BORDER = 0xffeccba2;
            COMPANION = 0xff8a5bab;
            pageGradient = new int[]{0xfff6fcf8, 0xffeef8f1, 0xffe9f6ef};
            headGradient = new int[]{0xffe6f7ee, 0xffddf1ea, 0xffe9f6f2};
            catInk = new int[]{0xff247a88, 0xff4859b0, 0xff1f7d55, 0xff94650f, 0xff76439a, 0xffaa4a3d, 0xff1f8a5f, 0xff8a5bab};
            catFill = new int[]{0xffd9f0f3, 0xffe3e8fb, 0xffd8f2e4, 0xfffdeed2, 0xfff0e2f7, 0xfffbe3e1, 0xffe2f5ea, 0xfff0e2f7};
            catStroke = new int[]{0xffa8dbe2, 0xffb8c3ee, 0xffa7dfc2, 0xffeed0a2, 0xffdbbfe9, 0xfff0beb8, 0xffc3ead9, 0xffdbbfe9};
        }
    }

    /** 白色/近白色统一映射为当前主题的卡片底色（浅绿=白，深绿=深玻璃）。 */
    static int surface(int color) {
        return color == 0xffffffff || color == 0xfffefefe ? SURFACE : color;
    }

    /** 页面渐变背景。 */
    static GradientDrawable page() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, pageGradient.clone());
    }

    /** 头部/汇总卡渐变颜色（浅绿为薄荷白渐变，深绿为深绿玻璃渐变）。 */
    static int[] headGradient() {
        return headGradient.clone();
    }

    // ---- 分类颜色（事项图标/时间线）----
    static int categoryKind(String title) {
        if (title == null) return CAT_OTHER;
        if (title.contains("水") || title.contains("喝")) return CAT_WATER;
        if (title.contains("眼") || title.contains("看")) return CAT_EYES;
        if (title.contains("专注陪伴")) return CAT_FOCUS;
        if (title.contains("陪伴")) return CAT_COMPANION;
        if (title.contains("走") || title.contains("活动")) return CAT_WALK;
        if (title.contains("颈") || title.contains("肩")) return CAT_NECK;
        if (title.contains("放松") || title.contains("呼吸")) return CAT_RELAX;
        return CAT_OTHER;
    }

    static int categoryInk(int kind) {
        return kind < catInk.length ? catInk[kind] : catInk[CAT_OTHER];
    }

    static int categoryFill(int kind) {
        return kind < catFill.length ? catFill[kind] : catFill[CAT_OTHER];
    }

    static int categoryStroke(int kind) {
        return kind < catStroke.length ? catStroke[kind] : catStroke[CAT_OTHER];
    }

    /** 应用系统栏外观：浅色主题用深色图标，深色主题用浅色图标。 */
    static void applyWindow(Window window) {
        window.setStatusBarColor(PAGE);
        window.setNavigationBarColor(PAGE);
        window.setBackgroundDrawable(page());
        int lightIcons = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        window.getDecorView().setSystemUiVisibility(darkTheme ? 0 : lightIcons);
    }

    static final int CAT_WATER = 0;
    static final int CAT_EYES = 1;
    static final int CAT_WALK = 2;
    static final int CAT_NECK = 3;
    static final int CAT_RELAX = 4;
    static final int CAT_OTHER = 5;
    static final int CAT_COMPANION = 6;
    static final int CAT_FOCUS = 7;

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, 0);
    }
}
