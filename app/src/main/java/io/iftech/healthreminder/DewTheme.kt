package io.iftech.healthreminder

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Mint Dew 设计令牌。所有新 Compose 页面只能从这里取色/字号/曲线。
 */
object Dew {
    // 基础色：白底 + 绿卡片
    val Bg = Color(0xFFFFFFFF)
    val Surface = Color(0xFFE8F5EE)
    val Primary = Color(0xFFA8D5BA)
    val Accent = Color(0xFF5BA882)
    val AccentBright = Color(0xFF6FCFA0)
    val Ink = Color(0xFF1A2E23)
    val InkSoft = Color(0xFF5F7A6C)
    val InkFaint = Color(0xFF8FA79A)
    val Line = Color(0xFFDCE8E1)
    val Track = Color(0xFFDDEDE3)

    // 提示色（不用正红/警示橙）
    val WarnSoft = Color(0xFFFBF3E8)
    val WarnInk = Color(0xFF9A7B52)

    // 宝箱/奖励唯一金色点缀
    val AccentGold = Color(0xFFFFC96B)

    // 粉彩辅助色：只允许极小面积点缀（单屏合计 <= 10%）
    val PastelCream = Color(0xFFFBF3E3)
    val PastelBlush = Color(0xFFF7EAEA)

    // 分层：L2 内嵌层填充
    val LayerFillTop get() = Color.White.copy(alpha = 0.88f)
    val LayerFillBottom get() = Color.White.copy(alpha = 0.66f)

    // 透明度阶梯
    const val ALPHA_GLASS = 0.75f
    const val ALPHA_BORDER = 0.30f
    const val ALPHA_TINT = 0.12f
    const val ALPHA_FAINT = 0.06f

    val GlassFill get() = Color(0xFFE8F5EE).copy(alpha = 0.92f)
    val GlassBorder get() = Color(0xFFA8D5BA).copy(alpha = 0.55f)
    val Tint get() = Primary.copy(alpha = ALPHA_TINT)

    // 圆角
    val ShapeCard = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
    val ShapePill = androidx.compose.foundation.shape.RoundedCornerShape(100.dp)
    val ShapeSmall = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)

    // 动效曲线
    val Spring = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val EaseOut = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    // 阴影
    val ShadowAmbient = Color(0x1F5BA882)
    val ShadowSpot = Color(0x245BA882)
}

private fun base(size: Int, line: Int, weight: FontWeight, tracking: Float = 0f) = TextStyle(
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    fontFamily = FontFamily.Default,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None
    )
)

val DewTypography = Typography(
    displayLarge = base(44, 48, FontWeight.Bold, (-1f)),
    headlineMedium = base(26, 32, FontWeight.SemiBold, (-0.4f)),
    titleMedium = base(18, 24, FontWeight.SemiBold, (-0.2f)),
    bodyLarge = base(15, 22, FontWeight.Normal),
    labelLarge = base(13, 18, FontWeight.Medium, 0.1f),
    bodySmall = base(11, 16, FontWeight.Normal, 0.4f)
)

@Composable
fun DewTheme(content: @Composable () -> Unit) {
    // 刻意只做浅色：这套调性靠淡绿底色成立，深色模式会破坏通透感
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Dew.Accent,
            onPrimary = Color.White,
            secondary = Dew.Primary,
            onSecondary = Dew.Ink,
            background = Dew.Bg,
            onBackground = Dew.Ink,
            surface = Dew.Surface,
            onSurface = Dew.Ink,
            surfaceVariant = Dew.Tint,
            onSurfaceVariant = Dew.InkSoft,
            outline = Dew.Line
        ),
        typography = DewTypography,
        content = content
    )
}
