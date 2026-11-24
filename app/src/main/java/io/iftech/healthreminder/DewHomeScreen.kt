package io.iftech.healthreminder

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 新版首页（Compose）。
 *
 * 页面只有一个主角：今日任务卡。其余模块全部让位。
 * 所有取色/字号/曲线都来自 Dew。
 */
@Composable
fun DewHomeScreen(
    context: Context,
    onOpenTasks: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var tick by remember { mutableIntStateOf(0) }

    val quests = remember(tick) { DailyQuest.quests() }
    val questDone = remember(tick) { DailyQuest.doneQuestCount(context) }
    val habitDone = remember(tick) { DailyQuest.habitCount(context) }
    val taskDone = remember(tick) { DailyQuest.taskCount(context) }
    val streak = remember(tick) { GameProgress.getGlobalStreak(context) }
    val level = remember(tick) { DailyQuest.level(context) }
    val bonusReady = remember(tick) { DailyQuest.bonusAvailable(context) }
    val remindersOn = remember(tick) { ReminderSettings.enabled(context) }

    val totalDone = habitDone + taskDone
    val totalTarget = quests.sumOf { it.target }

    Box(Modifier.fillMaxSize().background(Dew.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            Header(onOpenSettings)
            Spacer(Modifier.height(14.dp))

            StaggeredIn(0) {
                QuestCard(
                    context = context,
                    quests = quests,
                    questDone = questDone,
                    habitDone = habitDone,
                    taskDone = taskDone,
                    totalDone = totalDone,
                    totalTarget = totalTarget,
                    streak = streak,
                    level = level,
                    bonusReady = bonusReady,
                    onRefresh = { tick++ },
                    onOpenTasks = onOpenTasks
                )
            }

            Spacer(Modifier.height(12.dp))
            StaggeredIn(1) { BodyNoteRow(context) }

            Spacer(Modifier.height(12.dp))
            StaggeredIn(2) { ReminderRow(context, remindersOn, taskDone, totalTarget) }

            Spacer(Modifier.height(28.dp))
        }

        // 噪点：极淡一层，制造纸面质感，不拦触摸
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    drawNoise(Dew.Ink.copy(alpha = 0.05f))
                }
        )
    }
}

@Composable
private fun Header(onOpenSettings: () -> Unit) {
    val dateText = remember {
        SimpleDateFormat("M月d日  E", Locale.CHINA).format(Date())
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(dateText, style = MaterialTheme.typography.bodySmall, color = Dew.InkFaint)
            Spacer(Modifier.height(4.dp))
            Text("今天", style = MaterialTheme.typography.headlineMedium, color = Dew.Ink)
        }
        Pressable(onClick = onOpenSettings) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(Dew.ShapeCard)
                    .background(Dew.GlassFill)
                    .border(1.dp, Dew.GlassBorder, Dew.ShapeCard),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(16.dp)) {
                    val w = size.width
                    val h = size.height
                    // 三条横线的「设置」图标
                    for (i in 0..2) {
                        val y = h * (0.25f + i * 0.25f)
                        drawLine(Dew.InkSoft, Offset(0f, y), Offset(w, y), 1.6.dp.toPx(), StrokeCap.Round)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestCard(
    context: Context,
    quests: List<DailyQuest.Quest>,
    questDone: Int,
    habitDone: Int,
    taskDone: Int,
    totalDone: Int,
    totalTarget: Int,
    streak: Int,
    level: Int,
    bonusReady: Boolean,
    onRefresh: () -> Unit,
    onOpenTasks: () -> Unit
) {
    GlassCard {
        // 英雄区：极淡渐变 + 豆芽 + 圆环 + 连胜
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Dew.ShapeCard)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFF5FBF7), Color(0xFFE4F3EA))
                    )
                )
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.home_mascot),
                contentDescription = "豆芽",
                modifier = Modifier.size(84.dp)
            )
            Spacer(Modifier.width(6.dp))
            ProgressRing(
                done = totalDone.coerceAtMost(totalTarget),
                total = totalTarget,
                modifier = Modifier.weight(1f).height(92.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        // 标题 + 等级
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("今日任务", style = MaterialTheme.typography.titleMedium, color = Dew.Ink)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .clip(Dew.ShapeSmall)
                    .background(Dew.Tint)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    "Lv.$level",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dew.Accent
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        quests.forEachIndexed { index, quest ->
            val progress = DailyQuest.questProgress(context, quest).coerceAtMost(quest.target)
            val done = progress >= quest.target
            QuestRow(
                title = quest.title,
                progress = progress,
                target = quest.target,
                done = done,
                onJump = onOpenTasks
            )
            if (index != quests.lastIndex) Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(16.dp))

        // 宝箱进度
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Dew.ShapeCard)
                .background(Dew.Tint.copy(alpha = 0.55f))
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChestIcon(filled = questDone >= quests.size)
            Spacer(Modifier.width(10.dp))
            SegmentBar(
                total = quests.size,
                filled = questDone,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (questDone >= quests.size) "可以领奖啦" else "还剩 ${quests.size - questDone} 个",
                style = MaterialTheme.typography.bodySmall,
                color = if (questDone >= quests.size) Dew.Accent else Dew.InkFaint
            )
        }

        if (bonusReady) {
            Spacer(Modifier.height(12.dp))
            PrimaryButton(text = "领取完成奖励  +40 经验") {
                DailyQuest.claimAllBonus(context)
                DailyQuest.advanceSprint(context)
                onRefresh()
            }
        }
    }
}

@Composable
private fun QuestRow(
    title: String,
    progress: Int,
    target: Int,
    done: Boolean,
    onJump: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            JumpDot(active = done, onClick = onJump)
            Spacer(Modifier.width(10.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (done) Dew.InkFaint else Dew.Ink,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f)
            )
            if (done) {
                Box(
                    Modifier
                        .clip(Dew.ShapePill)
                        .background(
                            Brush.linearGradient(listOf(Dew.AccentBright, Dew.Accent))
                        )
                        .padding(horizontal = 9.dp, vertical = 3.dp)
                ) {
                    Text("已完成", style = MaterialTheme.typography.bodySmall, color = Color.White)
                }
            } else {
                Text(
                    "$progress/$target",
                    style = MaterialTheme.typography.labelLarge,
                    color = Dew.InkFaint
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.padding(start = 34.dp)) {
            SegmentBar(total = target, filled = progress, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** 行首圆形跳转按钮：浅绿底箭头，完成后实心绿勾。 */
@Composable
private fun JumpDot(active: Boolean, onClick: () -> Unit) {
    Pressable(onClick = onClick) {
        Box(
            Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (active) Brush.linearGradient(listOf(Dew.AccentBright, Dew.Accent))
                    else Brush.linearGradient(listOf(Color(0xFFEAF7F0), Color(0xFFE0F2E7)))
                )
                .border(
                    1.dp,
                    if (active) Color.Transparent else Dew.Primary.copy(alpha = 0.5f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(if (active) 11.dp else 8.dp)) {
                val w = size.width
                val h = size.height
                if (active) {
                    // 勾
                    drawLine(Color.White, Offset(w * 0.12f, h * 0.55f), Offset(w * 0.42f, h * 0.85f), 1.8.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White, Offset(w * 0.42f, h * 0.85f), Offset(w * 0.9f, h * 0.2f), 1.8.dp.toPx(), StrokeCap.Round)
                } else {
                    // 右箭头
                    drawLine(Dew.Accent, Offset(w * 0.25f, 0f), Offset(w * 0.8f, h / 2f), 1.6.dp.toPx(), StrokeCap.Round)
                    drawLine(Dew.Accent, Offset(w * 0.25f, h), Offset(w * 0.8f, h / 2f), 1.6.dp.toPx(), StrokeCap.Round)
                }
            }
        }
    }
}

/** 分段进度条：每格圆角 2dp，完成即亮绿。 */
@Composable
private fun SegmentBar(total: Int, filled: Int, modifier: Modifier = Modifier) {
    val segments = total.coerceAtLeast(1)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(segments) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < filled) Dew.Accent else Dew.Track)
            )
        }
    }
}

/** 宝箱小图标：自绘，不用 emoji。 */
@Composable
private fun ChestIcon(filled: Boolean) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val body = if (filled) Dew.Accent else Dew.InkFaint
        // 箱体
        drawRoundRect(
            color = body.copy(alpha = 0.85f),
            topLeft = Offset(w * 0.1f, h * 0.42f),
            size = Size(w * 0.8f, h * 0.48f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f)
        )
        // 箱盖
        drawRoundRect(
            color = body,
            topLeft = Offset(w * 0.06f, h * 0.24f),
            size = Size(w * 0.88f, h * 0.24f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.1f)
        )
        // 锁扣
        drawRect(
            color = Color.White.copy(alpha = 0.9f),
            topLeft = Offset(w * 0.42f, h * 0.42f),
            size = Size(w * 0.16f, h * 0.2f)
        )
    }
}

/** 大圆环：轨道浅绿，进度弧实心主绿，底下垫一层光晕。 */
@Composable
private fun ProgressRing(done: Int, total: Int, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = if (total > 0) done.toFloat() / total else 0f,
        animationSpec = tween(520, easing = Dew.Spring),
        label = "ring"
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val glow = 4.dp.toPx()
            val pad = stroke / 2f + glow
            val diameter = minOf(size.width, size.height) - pad * 2
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)

            drawArc(
                color = Dew.Track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            if (animated > 0f) {
                drawArc(
                    color = Dew.Accent.copy(alpha = 0.18f),
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke + glow, cap = StrokeCap.Round)
                )
                drawArc(
                    color = Dew.Accent,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (total > 0) "$done/$total" else "0",
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 22.sp, lineHeight = 26.sp),
                color = Dew.Ink
            )
            Text("今日", style = MaterialTheme.typography.bodySmall, color = Dew.InkFaint)
        }
    }
}

/** 玻璃卡：半透明白 + 极淡渐变 + 1dp 白边 + 绿调阴影。 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = Dew.ShapeCard,
                ambientColor = Dew.ShadowAmbient,
                spotColor = Dew.ShadowSpot
            )
            .clip(Dew.ShapeCard)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.82f), Color.White.copy(alpha = 0.62f))
                )
            )
            .border(1.dp, Dew.GlassBorder, Dew.ShapeCard)
            .padding(16.dp),
        content = content
    )
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Pressable(onClick = onClick) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(Dew.ShapeCard)
                .background(Brush.linearGradient(listOf(Dew.AccentBright, Dew.Accent)))
                .padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, color = Color.White)
        }
    }
}

/** 按下缩到 0.98，松手弹回。 */
@Composable
private fun Pressable(onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 240f),
        label = "press"
    )
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick
            )
    ) { content() }
}

/** 依次入场：淡入 + 上浮 12dp，每项延后 40ms。 */
@Composable
private fun StaggeredIn(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 40L)
        shown = true
    }
    val alpha by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(320, easing = Dew.EaseOut),
        label = "fade"
    )
    val dy by animateDpAsState(
        if (shown) 0.dp else 12.dp,
        tween(320, easing = Dew.EaseOut),
        label = "rise"
    )
    Box(Modifier.graphicsLayer { this.alpha = alpha }.offset(y = dy)) { content() }
}

@Composable
private fun BodyNoteRow(context: Context) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("今天身体怎么样", style = MaterialTheme.typography.labelLarge, color = Dew.Ink)
            Spacer(Modifier.weight(1f))
            Text("记一句", style = MaterialTheme.typography.bodySmall, color = Dew.Accent)
        }
    }
}

@Composable
private fun ReminderRow(context: Context, remindersOn: Boolean, done: Int, target: Int) {
    var on by remember { mutableStateOf(remindersOn) }
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(if (on) Dew.Accent else Dew.Track)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (on) "提醒运行中" else "提醒已暂停",
                    style = MaterialTheme.typography.labelLarge,
                    color = Dew.Ink
                )
                Text(
                    "今日 $done/$target 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = Dew.InkSoft
                )
            }
            Switch(
                checked = on,
                onCheckedChange = {
                    on = it
                    ReminderSettings.setEnabled(context, it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Dew.Accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Dew.Track,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}

/** 极淡噪点：固定种子，避免每帧变化。 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawNoise(color: Color) {
    val step = 7.dp.toPx()
    val seed = 20261124
    var y = 0f
    var i = seed
    while (y < size.height) {
        var x = 0f
        while (x < size.width) {
            i = i * 1103515245 + 12345
            val v = ((i ushr 16) and 0xFF) / 255f
            if (v > 0.72f) {
                drawCircle(color, radius = 0.7.dp.toPx(), center = Offset(x, y))
            }
            x += step
        }
        y += step
    }
}

