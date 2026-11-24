package io.iftech.healthreminder

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 游戏化配色：在 Dew 基础上补深绿与金色。 */
object GameColors {
    val Bg = Color(0xFFEAF6EF)
    val DeepGreen = Color(0xFF2E7D5B)
    val Card = Color.White
    val Primary = Color(0xFF4CAF7D)
    val Track = Color(0xFFDDEDE3)
    val Ink = Color(0xFF3D5A4C)
    val InkSoft = Color(0xFF8FAA9C)
    val Gold = Color(0xFFFFC96B)
    val GoldSoft = Color(0x33FFC96B)
    val Shadow = Color(0x264CAF7D)
    val ChipGray = Color(0xFFF1F5F2)
}

val GameShape = RoundedCornerShape(20.dp)
val GamePill = RoundedCornerShape(100.dp)

fun qSpring() = spring<Float>(
    dampingRatio = Spring.DampingRatioMediumBouncy,
    stiffness = Spring.StiffnessMedium
)

/** 按压回弹：0.92 缩放，弹簧回弹。 */
@Composable
fun BouncyBox(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.92f else 1f,
        animationSpec = qSpring(),
        label = "press"
    )
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(
                if (onClick != null) {
                    Modifier.androidxClickable(interaction, enabled, onClick)
                } else Modifier
            )
    ) { content() }
}

private fun Modifier.androidxClickable(
    interaction: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit
): Modifier = this.then(
    androidx.compose.foundation.clickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onClick = onClick
    )
)

/** 白卡：20dp 圆角 + 绿调软阴影，可选金色描边发光。 */
@Composable
fun GameCard(
    modifier: Modifier = Modifier,
    gold: Boolean = false,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (gold) 8.dp else 4.dp,
                shape = GameShape,
                ambientColor = if (gold) GameColors.GoldSoft else GameColors.Shadow,
                spotColor = if (gold) GameColors.GoldSoft else GameColors.Shadow
            )
            .clip(GameShape)
            .background(GameColors.Card)
            .then(
                if (gold) Modifier.border(1.dp, GameColors.Gold, GameShape)
                else Modifier.border(1.dp, Color(0x14000000), GameShape)
            )
            .padding(16.dp),
        content = content
    )
}

/** 果冻打卡点：勾选时放大到 1.35 再弹回。 */
@Composable
fun JellyDot(checked: Boolean, onClick: () -> Unit) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(checked) {
        if (checked) {
            scale.snapTo(1.35f)
            scale.animateTo(1f, qSpring())
        }
    }
    Box(
        Modifier
            .size(28.dp)
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(CircleShape)
            .background(if (checked) GameColors.Primary else Color.Transparent)
            .border(2.dp, if (checked) GameColors.Primary else GameColors.Track, CircleShape)
            .androidxClickable(remember { MutableInteractionSource() }, true, onClick),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            Canvas(Modifier.size(12.dp)) {
                val w = size.width
                val h = size.height
                drawLine(Color.White, Offset(w * 0.08f, h * 0.55f), Offset(w * 0.4f, h * 0.88f), 2.dp.toPx(), StrokeCap.Round)
                drawLine(Color.White, Offset(w * 0.4f, h * 0.88f), Offset(w * 0.94f, h * 0.14f), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

/** 星星粒子：触发后 5 颗向外飞 20dp 淡出，500ms。 */
@Composable
fun StarBurst(trigger: Int, modifier: Modifier = Modifier) {
    val p = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            p.snapTo(0f)
            p.animateTo(1f, tween(500))
        }
    }
    Canvas(modifier) {
        val t = p.value
        if (t >= 1f) return@Canvas
        repeat(5) { i ->
            val angle = (i * 72f - 90f) * PI.toFloat() / 180f
            val dist = 20.dp.toPx() * t
            drawCircle(
                color = GameColors.Primary.copy(alpha = 1f - t),
                radius = 3.dp.toPx(),
                center = center + Offset(cos(angle) * dist, sin(angle) * dist)
            )
        }
    }
}

/** 环形进度：低阻尼弹簧，弹着涨落。 */
@Composable
fun BouncyRing(
    progress: Float,
    modifier: Modifier = Modifier,
    label: String = "",
    labelColor: Color = GameColors.Ink
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(progress) {
        anim.animateTo(
            progress,
            spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
        )
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val stroke = 8.dp.toPx()
            val d = minOf(size.width, size.height) - stroke
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            val arcSize = Size(d, d)
            drawArc(
                color = GameColors.Track, startAngle = -90f, sweepAngle = 360f,
                useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            if (anim.value > 0f) {
                drawArc(
                    color = GameColors.Primary, startAngle = -90f,
                    sweepAngle = 360f * anim.value.coerceIn(0f, 1f),
                    useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = labelColor)
        }
    }
}

/** 数字上下滚动切换，0.25 秒。 */
@Composable
fun RollingNumber(value: Int, style: TextStyle, color: Color) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            (slideInVertically { it } + fadeIn(tween(250))) togetherWith
                    (slideOutVertically { -it } + fadeOut(tween(250)))
        },
        label = "roll"
    ) { v ->
        Text("$v", style = style, color = color)
    }
}

/** 列表入场：延后 index*60ms，从下方 12dp 浮入。 */
@Composable
fun WavyEntrance(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        shown = true
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(320), label = "a")
    val dy by animateDpAsState(if (shown) 0.dp else 12.dp, qSpring(), label = "d")
    Box(Modifier.graphicsLayer { this.alpha = alpha }.offset(y = dy)) { content() }
}

/** 宝箱：解锁时左右颤两下。 */
@Composable
fun ShakingChest(unlocked: Boolean, content: @Composable () -> Unit) {
    val shake = remember { Animatable(0f) }
    LaunchedEffect(unlocked) {
        if (unlocked) {
            listOf(-3f, 3f, -2f, 2f, 0f).forEach { shake.animateTo(it, tween(45)) }
        } else {
            shake.snapTo(0f)
        }
    }
    Box(Modifier.graphicsLayer { rotationZ = shake.value * 2f }) { content() }
}

/** 彩带：从顶部飘落 1.5 秒。 */
@Composable
fun Confetti(trigger: Int, modifier: Modifier = Modifier) {
    val p = remember { Animatable(1f) }
    val seeds = remember {
        (0 until 24).map { i ->
            floatArrayOf(
                ((i * 37) % 100) / 100f,
                0.75f + ((i * 53) % 50) / 100f,
                ((i * 71) % 100) / 100f * 6.28f,
                2.5f + ((i * 29) % 30) / 10f,
                (i % 5).toFloat()
            )
        }
    }
    LaunchedEffect(trigger) {
        if (trigger > 0) {
            p.snapTo(0f)
            p.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
        }
    }
    val colors = listOf(
        GameColors.Primary, Color(0xFF8FD9B6), GameColors.Gold,
        Color(0xFFFF8FAB), Color(0xFF7EC8E3)
    )
    Canvas(modifier) {
        val t = p.value
        if (t >= 1f) return@Canvas
        val d = this.density
        seeds.forEach { s ->
            val x = s[0] * size.width + sin(t * 6.28f * 1.5f + s[2]) * 16f * d
            val y = -20f * d + (size.height + 40f * d) * t * s[1]
            val a = (1f - ((t - 0.65f).coerceAtLeast(0f) / 0.35f)).coerceIn(0f, 1f)
            drawRect(
                color = colors[(s[4].toInt()) % colors.size].copy(alpha = a),
                topLeft = Offset(x, y),
                size = Size(s[3] * d, s[3] * d * 0.6f)
            )
        }
    }
}

/** 实心胶囊按钮。 */
@Composable
fun CapsuleButton(
    text: String,
    done: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    BouncyBox(
        onClick = onClick,
        enabled = !done,
        modifier = modifier
    ) {
        Box(
            Modifier
                .clip(GamePill)
                .background(
                    if (done) GameColors.ChipGray
                    else Brush.linearGradient(listOf(Color(0xFF6FCFA0), GameColors.Primary))
                )
                .padding(horizontal = 14.dp, vertical = 7.dp)
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                color = if (done) GameColors.InkSoft else Color.White
            )
        }
    }
}

/** 分段进度条。 */
@Composable
fun SegmentBar(total: Int, filled: Int, modifier: Modifier = Modifier, height: Int = 6) {
    val n = total.coerceAtLeast(1)
    Row(modifier) {
        repeat(n) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(height.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i < filled) GameColors.Primary else GameColors.Track)
            )
            if (i != n - 1) Spacer(Modifier.size(4.dp))
        }
    }
}

/** 宝箱图标（自绘）。 */
@Composable
fun ChestGlyph(unlocked: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val c = if (unlocked) GameColors.Gold else GameColors.InkSoft
        drawRoundRect(
            color = c.copy(alpha = 0.9f),
            topLeft = Offset(w * 0.1f, h * 0.42f),
            size = Size(w * 0.8f, h * 0.48f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f)
        )
        drawRoundRect(
            color = c,
            topLeft = Offset(w * 0.06f, h * 0.22f),
            size = Size(w * 0.88f, h * 0.24f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f)
        )
        drawRect(
            color = Color.White.copy(alpha = 0.92f),
            topLeft = Offset(w * 0.42f, h * 0.42f),
            size = Size(w * 0.16f, h * 0.2f)
        )
    }
}

/** 圆形图标块：关卡图标底色。 */
@Composable
fun LevelIcon(kind: Int, done: Boolean, modifier: Modifier = Modifier) {
    val tint = if (done) GameColors.ChipGray else GameColors.Primary.copy(alpha = 0.16f)
    val ink = if (done) GameColors.InkSoft else GameColors.Primary
    Box(
        modifier
            .size(34.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(17.dp)) {
            val w = size.width
            val h = size.height
            when (kind % 3) {
                0 -> { // 书
                    drawLine(ink, Offset(w * 0.5f, h * 0.18f), Offset(w * 0.5f, h * 0.85f), 1.6.dp.toPx(), StrokeCap.Round)
                    drawLine(ink, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.15f, h * 0.3f), 1.6.dp.toPx(), StrokeCap.Round)
                    drawLine(ink, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.85f, h * 0.3f), 1.6.dp.toPx(), StrokeCap.Round)
                }
                1 -> { // 清单
                    for (i in 0..2) {
                        val y = h * (0.25f + i * 0.25f)
                        drawLine(ink, Offset(w * 0.15f, y), Offset(w * 0.9f, y), 1.6.dp.toPx(), StrokeCap.Round)
                    }
                }
                else -> { // 卡片
                    drawRoundRect(
                        color = ink,
                        topLeft = Offset(w * 0.18f, h * 0.22f),
                        size = Size(w * 0.64f, h * 0.56f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
                        style = Stroke(1.6.dp.toPx())
                    )
                }
            }
        }
    }
}
