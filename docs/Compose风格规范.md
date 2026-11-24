# Android Compose 风格规范（Mint Dew / 薄荷露珠）

版本 v1.0 · 适用范围：休息豆芽新 UI（Compose 页面）

调性关键词：清新、克制、通透、有呼吸感。像清晨薄荷叶上的露珠。

---

## 0. 三条硬约束

1. 不做高饱和。所有色块饱和度控制在 35% 以内，绿系为主，不允许出现蓝紫渐变。
2. 不用 emoji 做图标。图标一律用矢量（Material Symbols Rounded）或自绘 Path。
3. 不用 Material 默认蓝（`#6750A4` / `#2196F3` 一类）。所有取色只能来自第 1 节的语义色板。

---

## 1. 色板

### 1.1 基础色

| 语义 | 值 | 用法 |
|---|---|---|
| `Bg` 页面底色 | `#F0F7F4` | 全屏背景，唯一底色 |
| `Surface` 卡片 | `#FFFFFF` @ 75% | 玻璃卡片填充 |
| `Primary` 主色 | `#A8D5BA` | 大面积浅绿：选中底、进度轨道、图标底色 |
| `Accent` 强调色 | `#5BA882` | 只用于「要用户看见」的地方：进度弧、完成态、关键数字 |
| `Ink` 主文字 | `#1A2E23` | 标题、正文、关键数字 |
| `InkSoft` 次文字 | `#5F7A6C` | 说明、副标题 |
| `InkFaint` 弱文字 | `#8FA79A` | 占位、单位、时间戳 |
| `Line` 分割线 | `#DCE8E1` | 1dp 分割 |
| `BorderGlass` 玻璃边框 | `#FFFFFF` @ 30% | 卡片 1dp 描边 |
| `WarnSoft` 提示底 | `#FBF3E8` | 权限未开的提示底 |
| `WarnInk` 提示字 | `#9A7B52` | 提示文字，不用正红/橙 |

### 1.2 透明度阶梯

统一用这四档，不要出现别的 alpha：

| 档位 | alpha | 场景 |
|---|---|---|
| 玻璃面 | 0.75 | 卡片底 |
| 玻璃边 | 0.30 | 卡片描边、白色高光 |
| 淡填充 | 0.12 | 主色做的选中底 |
| 极淡 | 0.06 | 分隔、噪点 |

### 1.3 颜色禁令

- 禁止蓝紫渐变（`#7C4DFF`、`#536DFE` 一类）
- 禁止正红（`#F44336`）与警示橙（`#FF9800`）
- 禁止纯黑（`#000000`）文字；最深的字是 `Ink`
- 同一屏最多出现 3 种绿（Primary / Accent / Primary@12%）

---

## 2. 字体

字体族：系统默认（中文用系统黑体即可），数字用 `FontFamily.Monospace` 或 `FontFeatureSettings = "tnum"` 保证等宽对齐。

| Token | 字号 | 行高 | 字重 | 字距 | 用途 |
|---|---|---|---|---|---|
| `displayNum` | 44sp | 48sp | Bold | -1sp | 大号连胜/完成数 |
| `headline` | 26sp | 32sp | SemiBold | -0.4sp | 页面标题「今天」 |
| `title` | 18sp | 24sp | SemiBold | -0.2sp | 卡片标题 |
| `body` | 15sp | 22sp | Normal | 0 | 任务名、正文 |
| `bodyStrong` | 15sp | 22sp | Medium | 0 | 需要强调的正文 |
| `label` | 13sp | 18sp | Medium | 0.1sp | 按钮文字、状态标签 |
| `caption` | 11sp | 16sp | Normal | 0.4sp | 单位、日期、辅助说明 |

规则：

- 一屏最多两级字重变化（Normal + SemiBold），不要出现 Bold 到处跑。
- 数字类（天数、次数、百分比）一律 `displayNum` 或 `title` + 等宽数字。
- 全大写英文/字母间距 0.6sp 以上，中文不加字距。

---

## 3. 形状与圆角

| Token | 值 | 用途 |
|---|---|---|
| `shapeCard` | 16dp | 所有卡片、按钮（统一） |
| `shapePill` | 100dp | 胶囊标签、进度条端点 |
| `shapeSmall` | 10dp | 小徽章、图标底 |
| `shapeBar` | 2dp | 分段进度条的每一格（高度 6dp） |

硬规则：

- 卡片与按钮**统一 16dp**，不允许出现 12/20/24 混用。
- 只在「胶囊」和「进度条小格」两个场景允许破格。
- 卡片内嵌套卡片时，内层用 16dp，靠 8dp 内边距做出层次，不要靠缩小圆角。

---

## 4. 间距

基准 4dp 栅格，只用这几个值：`4 / 8 / 12 / 16 / 20 / 24 / 32 / 40`。

| 场景 | 值 |
|---|---|
| 页面左右边距 | 20dp |
| 卡片内边距 | 16dp（上下可 14dp） |
| 卡片间距 | 12dp |
| 区块（section）间距 | 24dp |
| 标题与内容 | 12dp |
| 列表行间距 | 12dp |
| 图标与文字 | 10dp |
| 图标本身 | 20dp（行内）/ 24dp（强调） |

节奏要求：**同一屏内的垂直间距只允许出现两级**（如 12dp 与 24dp），不要 8/10/14/18 混用。

---

## 5. 材质与阴影

### 5.1 卡片

```
填充      White 75%
描边      1dp  White 30%（内侧）
阴影      elevation 4dp，色偏绿，不是纯灰
圆角      16dp
```

Compose 里原生阴影默认是黑的，必须显式指定彩色：

```kotlin
Modifier.shadow(
    elevation = 4.dp,
    shape = RoundedCornerShape(16.dp),
    ambientColor = Color(0x1F5BA882),   // 12% 强调绿
    spotColor = Color(0x245BA882)       // 14% 强调绿
)
```

### 5.2 磨砂玻璃的正确做法（重要）

`Modifier.blur(20.dp)` 模糊的是**该组件自己的内容**，不会模糊它背后的页面。所以直接给卡片加 blur，只会把卡里的文字糊掉。

三种可落地方案，按推荐顺序：

**方案 A（推荐，兼容全部版本）：半透明白 + 极淡竖向渐变**

视觉上就是磨砂玻璃，不依赖系统模糊，性能最好。

```kotlin
Box(
    Modifier
        .clip(RoundedCornerShape(16.dp))
        .background(
            Brush.verticalGradient(listOf(
                Color.White.copy(alpha = 0.82f),
                Color.White.copy(alpha = 0.62f)
            ))
        )
        .border(1.dp, Color.White.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
)

**方案 B（API 31+，真背景模糊）：**

把页面背景绘制在一个单独的 `Box`，对它用 `graphicsLayer { renderEffect = BlurEffect(20f, 20f, TileMode.Decal) }`，卡片作为上层半透明层叠上去。低版本自动降级到方案 A。

**方案 C：引入 haze 库**（`dev.chrisbanes.haze`），能直接做背景模糊，代价是多一个依赖。

规范里所有「blur 20dp」的描述，落地时按 A 实现，B/C 作为可选增强。

### 5.3 噪点

根布局叠一层噪点，用来消除大面积纯色渐变带来的塑料感。

```kotlin
// 建议放 res/drawable/noise_tile.xml（128x128 的噪点 bitmap tile）
Box(
    Modifier
        .fillMaxSize()
        .drawWithContent {
            drawContent()
            // 平铺噪点，alpha 0.05
        }
)
```

参数：alpha **0.05**，平铺（`TileMode.Repeated`），必须 `pointerInput` 不拦截触摸。

---

## 6. 组件规范

### 6.1 主按钮

- 高度 48dp，圆角 16dp，填充 `Accent` 渐变（`#6FCFA0 → #5BA882`）或纯 `Accent`
- 文字 `label` 白 100%
- 无按钮阴影（靠色块，不靠 elevation）
- 按下：scale 0.98，阴影加深

### 6.2 次按钮 / 图标按钮

- 白 75% 底 + 1dp 白 30% 边
- 图标 20dp，颜色 `Accent`
- 按下：scale 0.98 + 底色 alpha 提升到 0.9

### 6.3 任务行

- 高 56dp（含行底进度条则 64dp）
- 左：24dp 圆形按钮（未完成 = `Primary@12%` 底 + `Accent` 箭头；完成 = `Accent` 实心 + 白勾）
- 中：`body`，完成态 `InkFaint` + 删除线
- 右：状态标签（完成 = `Accent` 胶囊白字；未完成 = `InkFaint` 的 `x/y`）
- 行底：6dp 高分段进度条，格间 4dp，已完成格 `Accent`，未完成格 `Primary@12%`

### 6.4 进度环

- 直径 88dp，描边 8dp
- 轨道 `#DDEDE3`（Primary 的加深版）
- 弧线 `Accent` 实心，末端圆头
- 弧下垫一层 `Accent@20%` 宽 4dp 的光晕
- 环心：`displayNum` 数字 + 上方 `caption` 「今日」

### 6.5 分段进度条（宝箱）

- 3 格，每格高 6dp、圆角 2dp、格间 4dp
- 已完成 `Accent`，未完成 `Primary@12%`
- 全亮时：整组 scale 1→1.06→1，弹簧曲线；宝箱图标左右摆 6 度

### 6.6 标签

- 高 22dp，左右 9dp，圆角 100dp
- 完成态：`Accent` 底，白字 `caption`
- 中性态：`Primary@12%` 底，`InkSoft` 字

---

## 7. 动效

统一曲线：`CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)`（过冲弹簧）
收尾曲线：`CubicBezierEasing(0.2f, 0f, 0f, 1f)`

| 场景 | 参数 |
|---|---|
| 按钮/卡片按下 | scale 0.98，spring(dampingRatio = 0.55f, stiffness = 240f)，阴影 4dp→8dp |
| 松手回弹 | 同上曲线返回 scale 1.0，180ms |
| 页面元素入场 | 每项 `alpha 0→1` + `translationY 12dp→0`，时长 320ms，**依次递增 delay 40ms** |
| 数字变化 | 旧值 `translationY 0→-12dp` + alpha→0（120ms），新值从 +12dp 淡入（120ms） |
| 进度弧变化 | 用 `animateFloatAsState` 配弹簧曲线，不要线性 |
| 完成瞬间 | 圆点 scale 1→1.35→1，同时 6 颗亮绿粒子向外 20dp 淡出，500ms |

约束：

- 单次动画不超过 500ms
- 同屏同时进行的不超过 2 个动画
- 只动 `scale / translate / alpha`，不动宽高与 layout 位置
- 全屏最多 1 个循环动画（豆芽呼吸）

---

## 8. Compose 落地代码骨架

### 8.1 令牌

```kotlin
object Dew {
    val Bg        = Color(0xFFF0F7F4)
    val Surface   = Color(0xFFFFFFFF)
    val Primary   = Color(0xFFA8D5BA)
    val Accent    = Color(0xFF5BA882)
    val Ink       = Color(0xFF1A2E23)
    val InkSoft   = Color(0xFF5F7A6C)
    val InkFaint  = Color(0xFF8FA79A)
    val Line      = Color(0xFFDCE8E1)

    val GlassFill   = Surface.copy(alpha = 0.75f)
    val GlassBorder = Surface.copy(alpha = 0.30f)
    val Tint        = Primary.copy(alpha = 0.12f)

    val WarnSoft = Color(0xFFFBF3E8)
    val WarnInk  = Color(0xFF9A7B52)

    val CardShape = RoundedCornerShape(16.dp)
    val PillShape = RoundedCornerShape(100.dp)

    val Spring = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val EaseOut = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
```

### 8.2 玻璃卡

```kotlin
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .shadow(
                elevation = 4.dp,
                shape = Dew.CardShape,
                ambientColor = Dew.Accent.copy(alpha = 0.12f),
                spotColor = Dew.Accent.copy(alpha = 0.14f)
            )
            .clip(Dew.CardShape)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.82f), Color.White.copy(alpha = 0.62f))
                )
            )
            .border(1.dp, Dew.GlassBorder, Dew.CardShape)
            .padding(16.dp),
        content = content
    )
}
```

### 8.3 按压反馈

```kotlin
fun Modifier.pressScale(interactionSource: MutableInteractionSource) = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 240f),
        label = "press"
    )
    this.graphicsLayer { scaleX = scale; scaleY = scale }
}
```

### 8.4 依次入场

```kotlin
@Composable
fun StaggeredIn(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(index * 40L); shown = true }
    val alpha by animateFloatAsState(if (shown) 1f else 0f,
        tween(320, easing = Dew.EaseOut), label = "fade")
    val dy by animateDpAsState(if (shown) 0.dp else 12.dp,
        tween(320, easing = Dew.EaseOut), label = "rise")
    Box(Modifier.graphicsLayer { this.alpha = alpha }.offset(y = dy)) { content() }
}
```

---

## 9. 自检清单（提交前逐条过）

- [ ] 页面底色是 `#F0F7F4`，没有第二种底色
- [ ] 没有蓝紫渐变、没有 emoji、没有 Material 默认蓝、没有正红
- [ ] 所有卡片圆角 16dp、按钮圆角 16dp
- [ ] 卡片是 75% 白 + 1dp 白 30% 边 + 4dp 绿色阴影
- [ ] 根布局有 alpha 0.05 噪点层，且不拦截点击
- [ ] 按压有 scale 0.98 反馈
- [ ] 列表入场是 40ms 递增的 fade + 上浮
- [ ] 全屏最多一个循环动画
- [ ] 垂直间距只用了两级
- [ ] 数字用等宽数字，环心数字视觉居中

---

## 10. 与现有 View 体系的关系

当前 App 主体是传统 View（纯代码构建 UI），本规范用于**新页面**（Compose）以及后续迁移。

接入需要：

```groovy
buildFeatures { compose true }
composeOptions { kotlinCompilerExtensionVersion "1.5.14" }
implementation platform("androidx.compose:compose-bom:2024.09.02")
implementation "androidx.compose.material3:material3"
implementation "androidx.compose.ui:ui"
```

迁移期两套 UI 并存，配色以本规范为准，View 侧的 `UiPalette` 按第 1 节同步改值，保证视觉一致。
