package io.iftech.healthreminder;

final class ReminderArtworkCatalog {
    static final String DEFAULT = "default";

    static final class Artwork {
        final String id;
        final String label;
        final int drawable;

        Artwork(String id, String label, int drawable) {
            this.id = id;
            this.label = label;
            this.drawable = drawable;
        }
    }

    private static final Artwork[] CUSTOM = {
            new Artwork("sprout", "新芽角色", R.drawable.home_mascot),
            new Artwork("reading", "阅读角色", R.drawable.companion_reading),
            new Artwork("relax", "轻松陪伴", R.drawable.beans_relax),
            new Artwork("busy", "临时有事", R.drawable.beans_busy),
            new Artwork("doing", "开始行动", R.drawable.beans_doing),
            new Artwork("sad", "委屈豆芽", R.drawable.beans_sad),
            new Artwork("hula", "呼啦圈豆芽", R.drawable.beans_hula),
            new Artwork("run", "跑步豆芽", R.drawable.beans_run),
            new Artwork("eat", "吃饭豆芽", R.drawable.beans_eat),
            new Artwork("thinking", "电脑前思考", R.drawable.beans_thinking),
            new Artwork("sleepy", "趴桌困了", R.drawable.beans_sleepy),
            new Artwork("sleep_scene", "豆芽睡觉 · 深蓝", R.drawable.routine_sleep),
            new Artwork("morning_scene", "豆芽起床 · 晨光黄", R.drawable.routine_morning)
    };

    private ReminderArtworkCatalog() { }

    static String[] labels() {
        String[] labels = new String[CUSTOM.length + 1];
        labels[0] = "委屈豆芽（默认）";
        for (int i = 0; i < CUSTOM.length; i++) labels[i + 1] = CUSTOM[i].label;
        return labels;
    }

    static String idAt(int index) {
        return index == 0 ? DEFAULT : CUSTOM[index - 1].id;
    }

    static String label(String id) {
        if (DEFAULT.equals(id)) return "委屈豆芽（默认）";
        Artwork artwork = find(id);
        return artwork == null ? "委屈豆芽（默认）" : artwork.label;
    }

    static int drawable(String id) {
        if (DEFAULT.equals(id)) return R.drawable.beans_sad;
        Artwork artwork = find(id);
        return artwork == null ? R.drawable.beans_sad : artwork.drawable;
    }

    static boolean isSleepScene(String id) { return "sleep_scene".equals(id); }
    static boolean isMorningScene(String id) { return "morning_scene".equals(id); }
    static boolean isCustomScene(String id) { return isSleepScene(id) || isMorningScene(id); }
    static int scenePageColor(String id) {
        return isSleepScene(id) ? 0xff0b1933 : isMorningScene(id) ? 0xfffff0bf : UiPalette.PAGE;
    }
    static int sceneSurfaceColor(String id) {
        return isSleepScene(id) ? 0xff18345c : isMorningScene(id) ? 0xffffe28f : UiPalette.SOFT;
    }
    static int sceneTextColor(String id) {
        return isSleepScene(id) ? 0xfff2f6ff : isMorningScene(id) ? 0xff513b13 : UiPalette.INK;
    }
    static int sceneMutedColor(String id) {
        return isSleepScene(id) ? 0xffc1d0e5 : isMorningScene(id) ? 0xff796638 : UiPalette.MUTED;
    }
    static int sceneAccentColor(String id) {
        return isSleepScene(id) ? 0xff4f9df1 : isMorningScene(id) ? 0xffd99a16 : UiPalette.ACCENT;
    }

    private static Artwork find(String id) {
        for (Artwork artwork : CUSTOM) if (artwork.id.equals(id)) return artwork;
        return null;
    }
}
