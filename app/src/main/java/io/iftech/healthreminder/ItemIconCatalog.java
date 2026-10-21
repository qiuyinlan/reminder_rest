package io.iftech.healthreminder;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

import java.io.IOException;
import java.io.InputStream;

/**
 * 事项图标库：内置「休息运动」图标包（75 张透明背景 PNG），按 5 个分类分组，
 * 图片放在 assets/item_icons 下，文件名统一为 c分类_序号.png。
 *
 * Rule.icon 的取值约定：
 *  - 空字符串      → 取事项标题第一个字做文字徽标
 *  - 单个字符/表情 → 文字徽标
 *  - "img:图标id"  → 使用内置图片图标（见 {@link #isImageIcon(String)}）
 */
final class ItemIconCatalog {
    /** 图片图标的存储前缀。 */
    static final String PREFIX = "img:";
    /** 内置图标包分类，顺序即选择器里的标签顺序。 */
    static final String[] CATEGORIES = {"休息放松", "轻度运动", "活动提醒", "时间与节奏", "氛围与状态"};

    static final class Icon {
        final String id;
        final String category;
        final String label;
        final String asset;

        Icon(String id, String category, String label, String asset) {
            this.id = id;
            this.category = category;
            this.label = label;
            this.asset = asset;
        }
    }

    private static final Icon[] ICONS = {
            // ---- 休息放松 ----
            new Icon("c1_01", "休息放松", "闭眼休息", "item_icons/c1_01.png"),
            new Icon("c1_02", "休息放松", "放松云朵", "item_icons/c1_02.png"),
            new Icon("c1_03", "休息放松", "呼吸节奏圈", "item_icons/c1_03.png"),
            new Icon("c1_04", "休息放松", "冥想打坐", "item_icons/c1_04.png"),
            new Icon("c1_05", "休息放松", "泡脚", "item_icons/c1_05.png"),
            new Icon("c1_06", "休息放松", "热茶水杯", "item_icons/c1_06.png"),
            new Icon("c1_07", "休息放松", "热敷眼睛", "item_icons/c1_07.png"),
            new Icon("c1_08", "休息放松", "深呼吸", "item_icons/c1_08.png"),
            new Icon("c1_09", "休息放松", "睡前阅读", "item_icons/c1_09.png"),
            new Icon("c1_10", "休息放松", "躺椅放松", "item_icons/c1_10.png"),
            new Icon("c1_11", "休息放松", "听轻音乐", "item_icons/c1_11.png"),
            new Icon("c1_12", "休息放松", "香薰蜡烛", "item_icons/c1_12.png"),
            new Icon("c1_13", "休息放松", "小憩午休", "item_icons/c1_13.png"),
            new Icon("c1_14", "休息放松", "眼罩", "item_icons/c1_14.png"),
            new Icon("c1_15", "休息放松", "夜间模式月亮", "item_icons/c1_15.png"),
            // ---- 轻度运动 ----
            new Icon("c2_01", "轻度运动", "弓步拉伸", "item_icons/c2_01.png"),
            new Icon("c2_02", "轻度运动", "踝关节转动", "item_icons/c2_02.png"),
            new Icon("c2_03", "轻度运动", "肩背开合", "item_icons/c2_03.png"),
            new Icon("c2_04", "轻度运动", "肩部放松", "item_icons/c2_04.png"),
            new Icon("c2_05", "轻度运动", "颈部拉伸", "item_icons/c2_05.png"),
            new Icon("c2_06", "轻度运动", "靠墙静蹲", "item_icons/c2_06.png"),
            new Icon("c2_07", "轻度运动", "髋部拉伸", "item_icons/c2_07.png"),
            new Icon("c2_08", "轻度运动", "平板支撑", "item_icons/c2_08.png"),
            new Icon("c2_09", "轻度运动", "深蹲", "item_icons/c2_09.png"),
            new Icon("c2_10", "轻度运动", "手腕活动", "item_icons/c2_10.png"),
            new Icon("c2_11", "轻度运动", "体侧伸展", "item_icons/c2_11.png"),
            new Icon("c2_12", "轻度运动", "腿部伸展", "item_icons/c2_12.png"),
            new Icon("c2_13", "轻度运动", "腰部扭转", "item_icons/c2_13.png"),
            new Icon("c2_14", "轻度运动", "瑜伽猫牛式", "item_icons/c2_14.png"),
            new Icon("c2_15", "轻度运动", "瑜伽婴儿式", "item_icons/c2_15.png"),
            // ---- 活动提醒 ----
            new Icon("c3_01", "活动提醒", "办公室绕圈", "item_icons/c3_01.png"),
            new Icon("c3_02", "活动提醒", "喝水提醒", "item_icons/c3_02.png"),
            new Icon("c3_03", "活动提醒", "肩颈放松", "item_icons/c3_03.png"),
            new Icon("c3_04", "活动提醒", "举手伸展", "item_icons/c3_04.png"),
            new Icon("c3_05", "活动提醒", "开合跳", "item_icons/c3_05.png"),
            new Icon("c3_06", "活动提醒", "离座活动", "item_icons/c3_06.png"),
            new Icon("c3_07", "活动提醒", "爬楼梯", "item_icons/c3_07.png"),
            new Icon("c3_08", "活动提醒", "起身走动", "item_icons/c3_08.png"),
            new Icon("c3_09", "活动提醒", "散步", "item_icons/c3_09.png"),
            new Icon("c3_10", "活动提醒", "晒太阳", "item_icons/c3_10.png"),
            new Icon("c3_11", "活动提醒", "眼保健操", "item_icons/c3_11.png"),
            new Icon("c3_12", "活动提醒", "原地踏步", "item_icons/c3_12.png"),
            new Icon("c3_13", "活动提醒", "远眺窗外", "item_icons/c3_13.png"),
            new Icon("c3_14", "活动提醒", "站立办公", "item_icons/c3_14.png"),
            new Icon("c3_15", "活动提醒", "转腰活动", "item_icons/c3_15.png"),
            // ---- 时间与节奏 ----
            new Icon("c4_01", "时间与节奏", "10分钟活动", "item_icons/c4_01.png"),
            new Icon("c4_02", "时间与节奏", "25分钟专注", "item_icons/c4_02.png"),
            new Icon("c4_03", "时间与节奏", "5分钟休息", "item_icons/c4_03.png"),
            new Icon("c4_04", "时间与节奏", "打卡勾选", "item_icons/c4_04.png"),
            new Icon("c4_05", "时间与节奏", "倒计时", "item_icons/c4_05.png"),
            new Icon("c4_06", "时间与节奏", "番茄钟", "item_icons/c4_06.png"),
            new Icon("c4_07", "时间与节奏", "进度环", "item_icons/c4_07.png"),
            new Icon("c4_08", "时间与节奏", "连续打卡火焰", "item_icons/c4_08.png"),
            new Icon("c4_09", "时间与节奏", "每日计划", "item_icons/c4_09.png"),
            new Icon("c4_10", "时间与节奏", "每小时提醒", "item_icons/c4_10.png"),
            new Icon("c4_11", "时间与节奏", "闹钟", "item_icons/c4_11.png"),
            new Icon("c4_12", "时间与节奏", "日历休息日", "item_icons/c4_12.png"),
            new Icon("c4_13", "时间与节奏", "时间沙漏", "item_icons/c4_13.png"),
            new Icon("c4_14", "时间与节奏", "完成勋章", "item_icons/c4_14.png"),
            new Icon("c4_15", "时间与节奏", "休息提醒铃", "item_icons/c4_15.png"),
            // ---- 氛围与状态 ----
            new Icon("c5_01", "氛围与状态", "充电电池", "item_icons/c5_01.png"),
            new Icon("c5_02", "氛围与状态", "大脑休息", "item_icons/c5_02.png"),
            new Icon("c5_03", "氛围与状态", "呼吸气泡", "item_icons/c5_03.png"),
            new Icon("c5_04", "氛围与状态", "恢复进度条", "item_icons/c5_04.png"),
            new Icon("c5_05", "氛围与状态", "脊柱舒展", "item_icons/c5_05.png"),
            new Icon("c5_06", "氛围与状态", "绿色能量叶", "item_icons/c5_06.png"),
            new Icon("c5_07", "氛围与状态", "平静水面", "item_icons/c5_07.png"),
            new Icon("c5_08", "氛围与状态", "身体放松", "item_icons/c5_08.png"),
            new Icon("c5_09", "氛围与状态", "心率平稳", "item_icons/c5_09.png"),
            new Icon("c5_10", "氛围与状态", "心情微笑", "item_icons/c5_10.png"),
            new Icon("c5_11", "氛围与状态", "星星夜晚", "item_icons/c5_11.png"),
            new Icon("c5_12", "氛围与状态", "压力下降箭头", "item_icons/c5_12.png"),
            new Icon("c5_13", "氛围与状态", "眼睛保护", "item_icons/c5_13.png"),
            new Icon("c5_14", "氛围与状态", "阳光窗边", "item_icons/c5_14.png"),
            new Icon("c5_15", "氛围与状态", "月亮睡眠", "item_icons/c5_15.png"),
    };

    /** 解码后的原图缓存（每张约 60x60，几十 KB）。 */
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(128) {
        @Override protected int sizeOf(String key, Bitmap value) {
            return 1;
        }
    };

    private ItemIconCatalog() { }

    /** 是否为内置图片图标（"img:xxx"）。 */
    static boolean isImageIcon(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /** 图标 id → 存储值。 */
    static String valueFor(String id) {
        return PREFIX + id;
    }

    /** 存储值 → 图标 id；不是图片图标时返回空串。 */
    static String idOf(String value) {
        return isImageIcon(value) ? value.substring(PREFIX.length()) : "";
    }

    static Icon find(String id) {
        if (id == null) return null;
        for (Icon icon : ICONS) if (icon.id.equals(id)) return icon;
        return null;
    }

    static Icon[] all() {
        return ICONS;
    }

    /** 指定分类下的图标，顺序与图标包一致。 */
    static Icon[] ofCategory(String category) {
        int count = 0;
        for (Icon icon : ICONS) if (icon.category.equals(category)) count++;
        Icon[] result = new Icon[count];
        int index = 0;
        for (Icon icon : ICONS) if (icon.category.equals(category)) result[index++] = icon;
        return result;
    }

    /** 存储值对应的中文名，用于按钮文案/预览；未知返回空串。 */
    static String label(String value) {
        Icon icon = find(idOf(value));
        return icon == null ? "" : icon.label;
    }

    /** 读取内置图标位图（带缓存）；失败返回 null，调用方回退到文字徽标。 */
    static Bitmap bitmap(Context context, String value) {
        String id = idOf(value);
        if (id.isEmpty()) return null;
        Icon icon = find(id);
        if (icon == null) return null;
        Bitmap cached = CACHE.get(icon.id);
        if (cached != null && !cached.isRecycled()) return cached;
        AssetManager assets = context.getApplicationContext().getAssets();
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream stream = assets.open(icon.asset)) {
            Bitmap decoded = BitmapFactory.decodeStream(stream, null, options);
            if (decoded != null) CACHE.put(icon.id, decoded);
            return decoded;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }
}