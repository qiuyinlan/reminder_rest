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
    /** 倒计时数字色（比主色更深/更亮，保证数字醒目）。 */
    static int COUNTDOWN_INK = 0xff125f45;

    private static boolean darkTheme;
    private static int[] pageGradient = {0xfff6fcf8, 0xffeef8f1, 0xffe9f6ef};
    private static int[] headGradient = {0xffe6f7ee, 0xffddf1ea, 0xffe9f6f2};
    /** 玻璃卡片：极淡渐变白，营造磨砂玻璃的柔光感。 */
    private static int[] glassGradient = {0xffffffff, 0xfff2faf5};
    /** 主状态卡：带一点点主题色的柔光渐变。 */
    private static int[] heroGradient = {0xffdaf3e7, 0xfff4fcf9};
    /** 卡片内的极细分隔线/描边。 */
    private static int hairlineColor = 0x66b9dfcd;
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
            COUNTDOWN_INK = 0xff8cecc0;
            pageGradient = new int[]{PAGE_LIGHT, PAGE, 0xff02140f};
            headGradient = new int[]{0xff0b3e2a, 0xff12352c, 0xff072b1d};
            glassGradient = new int[]{0xff1c3d33, 0xff0f2b23};
            heroGradient = new int[]{0xff0f4d35, 0xff06291b};
            hairlineColor = 0x33a5e8c0;
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
            COUNTDOWN_INK = 0xff125f45;
            pageGradient = new int[]{0xfff6fcf8, 0xffeef8f1, 0xffe7f6ed};
            headGradient = new int[]{0xffccefdd, 0xffe0f7ea, 0xfff7fdfb};
            glassGradient = new int[]{0xffffffff, 0xfff3fbf7};
            heroGradient = new int[]{0xffc4ecda, 0xfff7fdfb};
            hairlineColor = 0x66b9dfcd;
            catInk = new int[]{0xff247a88, 0xff4859b0, 0xff1f7d55, 0xff94650f, 0xff76439a, 0xffaa4a3d, 0xff1f8a5f, 0xff8a5bab};
            catFill = new int[]{0xffd9f0f3, 0xffe3e8fb, 0xffd8f2e4, 0xfffdeed2, 0xfff0e2f7, 0xfffbe3e1, 0xffe2f5ea, 0xfff0e2f7};
            catStroke = new int[]{0xffa8dbe2, 0xffb8c3ee, 0xffa7dfc2, 0xffeed0a2, 0xffdbbfe9, 0xfff0beb8, 0xffc3ead9, 0xffdbbfe9};
        }
    }

    /** 白色/近白色统一映射为当前主题的卡片底色（浅绿=白，深绿=深玻璃）。 */
    static int surface(int color) {
        return color == 0xffffffff || color == 0xfffefefe ? SURFACE : color;
    }

    /** Readable input surface shared by editable fields across screens and dialogs. */
    static GradientDrawable inputBackground(Context context, int lightFill, int radiusDp) {
        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable background = new GradientDrawable();
        background.setColor(darkTheme ? 0xff1b493b : surface(lightFill));
        background.setCornerRadius(Math.round(radiusDp * density));
        background.setStroke(Math.max(1, Math.round(density)), darkTheme ? ACCENT : BORDER);
        return background;
    }

    /** 页面渐变背景。 */
    static GradientDrawable page() {
        return new GradientDrawable(GradientDrawable.Orientation.TL_BR, pageGradient.clone());
    }

    /** 头部/汇总卡渐变颜色（浅绿为薄荷白渐变，深绿为深绿玻璃渐变）。 */
    static int[] headGradient() {
        return headGradient.clone();
    }

    /** 玻璃卡片渐变（极淡的上下渐变，制造柔光玻璃质感）。 */
    static int[] glassGradient() {
        return glassGradient.clone();
    }

    /** 主状态卡渐变（比普通卡片多一点主题色调）。 */
    static int[] heroGradient() {
        return heroGradient.clone();
    }

    /** 卡片内部极细分隔线颜色。 */
    static int hairline() {
        return hairlineColor;
    }

    /** 颜色混合：以 base 为底、按比例混入 overlay，用于给卡片叠一层极淡的类目色。 */
    static int blend(int base, int overlay, float overlayAmount) {
        float keep = 1f - overlayAmount;
        int r = Math.round(((base >> 16) & 0xff) * keep + ((overlay >> 16) & 0xff) * overlayAmount);
        int g = Math.round(((base >> 8) & 0xff) * keep + ((overlay >> 8) & 0xff) * overlayAmount);
        int b = Math.round((base & 0xff) * keep + (overlay & 0xff) * overlayAmount);
        return 0xff000000 | (r << 16) | (g << 8) | b;
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

    /** 可选的类目色数量（事项自选颜色的取值范围）。 */
    static final int COLOR_COUNT = 8;

    /**
     * 事项颜色的最终类目编号：用户在事项里选过就用自选色，否则按标题自动归类。
     */
    static int colorKind(int overrideKind, String title) {
        return overrideKind >= 0 && overrideKind < COLOR_COUNT ? overrideKind : categoryKind(title);
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
