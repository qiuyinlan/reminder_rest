# 浅绿色主题改造规范（Light Green Theme Spec）

> 更新：现在支持在设置页「外观 → 主题色」切换「深绿 / 浅绿」两套主题。
> `UiPalette` 的两套颜色表即为本规范第四节之外的深绿原值（深绿主题完整保留原版磨砂深绿配色），
> 页面代码通过 `UiPalette.*` 可变静态字段读取当前主题，切换后自动重启到首页生效。

本文件是本次 UI 改版（深绿磨砂 → 浅绿清新）的唯一事实来源。所有页面必须遵守本规范，
保证视觉一致。

## 一、调色板（UiPalette.java 已就绪，禁止再改常量值）

| 常量 | 值 | 用途 |
| --- | --- | --- |
| ACCENT | 0xff1f8a5f | 主色：强调文字、按钮、进度条、选中态 |
| PAGE | 0xffeef8f1 | 页面底色（薄荷绿） |
| PAGE_LIGHT | 0xfff6fcf8 | 页面渐变起始（几乎白） |
| SOFT | 0xffd7f0e1 | 浅绿标签/胶囊底色 |
| PALE | 0xffe7f7ee | 更淡绿色面板底色 |
| SURFACE | 0xffffffff | 卡片底色：白色玻璃卡片 |
| INK | 0xff133226 | 主文字（深墨绿） |
| MUTED | 0xff5c7c6e | 次级文字（灰绿） |
| BORDER | 0xffcfe9da | 卡片 1dp 描边 |
| DISABLED | 0xffbcd3c7 | 关闭态/禁用态 |
| WARN | 0xffa4672a | 警示文字（浅底琥珀） |
| WARN_SOFT | 0xfffff2e2 | 警示底色 |
| WARN_BORDER | 0xffeccba2 | 警示描边 |

页面一律用 `UiPalette.page()` 作为背景（浅绿渐变）；卡片一律调用现有的
`round()/outline()/surface()` 辅助方法生成，填白色（`Color.WHITE` 会被
`UiPalette.surface()` 映射为 SURFACE）。

## 二、语义色替换对照表（深色主题 → 浅色主题）

### 1. 事项图标（8 组颜色，MainActivity/ItemsActivity/StatsActivity 中的
`iconColor`/`iconTint`/`iconBorder` 三个方法必须使用下列对应值）

| 关键词 | iconColor（文字/描边深色） | iconTint（浅色填充） | iconBorder（浅色描边） |
| --- | --- | --- | --- |
| 水 | 0xff247a88 | 0xffd9f0f3 | 0xffa8dbe2 |
| 眼/看 | 0xff4859b0 | 0xffe3e8fb | 0xffb8c3ee |
| 走/活动 | 0xff1f7d55 | 0xffd8f2e4 | 0xffa7dfc2 |
| 颈/肩 | 0xff94650f | 0xfffdeed2 | 0xffeeD0a2 |
| 放松/呼吸 | 0xff76439a | 0xfff0e2f7 | 0xffdbbfe9 |
| 其他默认 | 0xffaa4a3d | 0xfffbe3e1 | 0xfff0beb8 |

### 2. 通用深色 → 浅色

- 深绿页面/卡片底（如 0xff031d17、0xff02140f、0xff073126、0xff124635、0xff0c382b、
  0xff102d24、0xff15382a、0xff183d30、0xff203a45、0xff0b3325、0xff12352c、0xff142d3c、
  0xff173b40、0xff292f4c、0xff173d2d、0xff49371f、0xff3b2947、0xff482b28 等）
  → 使用对应语义：普通卡片 `Color.WHITE`；强调卡片用 `UiPalette.PALE`/`UiPalette.SOFT`；
  头部渐变用浅绿渐变（见下）。
- 偏亮主色 `0xff00d78a` → `UiPalette.ACCENT`（0xff1f8a5f）。
- 浅文字 `0xfff2f8f3` → `UiPalette.INK`。
- 灰文字 `0xffb0c8ba`、`0xff9aa59c` → `UiPalette.MUTED`。
- 描边 `0x667de1b1` → `UiPalette.BORDER`。
- 警示亮橙 `0xffffa08f`、`0xffff9a8a` → `UiPalette.WARN`；警示底 `0xff402c24`、
  `0xff623d2e` → `UiPalette.WARN_SOFT`；警示描边 `0xffaa7152` → `UiPalette.WARN_BORDER`。
- 删除红 `0xffbd5147` → 0xffc85a4c（浅底可读即可，可保留原值）。
- 紫色陪伴色 `0xffd7a6eb` → 0xff8a5bab，底色 0xfff0e2f7。
- 头部状态卡渐变（homeStatusBackground 等）→ 浅绿渐变，如
  `{0xffe6f7ee, 0xffddf1ea, 0xffe9f6f2}`，圆角 18dp + 1dp `UiPalette.BORDER` 描边。

### 3. 浅色底上的文字可读性

所有原先"白字/亮字压在深色上"的 Text，改为 INK（深墨绿）；次要文字用 MUTED；
禁用文字用 DISABLED。任何仍以深色为填充的背景必须换成白色或浅绿底，保证对比度。

## 三、美化要求（保持一致）

1. 页面背景：薄荷绿渐变（`UiPalette.page()`），卡片白色 + 1dp 浅绿描边 + 圆角 14–18dp。
2. 卡片可加 `elevation(dp(1))~dp(2))` 营造轻微浮起感（原已有则保留/微调）。
3. 胶囊/标签：圆角 10–14dp，填充 `UiPalette.SOFT` 或图标浅色 tint，文字用对应深色。
4. 按钮/强调操作：主色 ACCENT；次要操作用浅绿胶囊 + INK 文字。
5. 分隔线用 `UiPalette.BORDER`（或 line 常量）。
6. 标题字号与现有层级保持不变，行距可微调；中文文案不变。
7. 动画、逻辑、交互、存储键名一律不改；只改颜色、间距、圆角、阴影等视觉参数。

## 四、禁止事项

- 不得引入任何 gradle 依赖或新资源文件（保持纯 Java 程序化绘制）。
- 不得大幅重构方法签名；不得改动 ReminderSettings 的持久化键名。
- 不得修改 UiPalette.java、styles.xml 中的值（已由主流程定稿）。
