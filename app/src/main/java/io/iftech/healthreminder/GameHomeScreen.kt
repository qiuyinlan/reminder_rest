package io.iftech.healthreminder

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

/**
 * 游戏化新版首页。
 * 结构：标题栏 → 玩家卡 → 今日主线英雄区 → 每日关卡 → 任务副本 → 奖励之路 → 底部导航
 */
@Composable
fun GameHomeScreen(
    context: Context,
    onOpenTasks: () -> Unit,
    onOpenItems: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var tick by remember { mutableIntStateOf(0) }

    // 从任务页/事项页返回时刷新数据
    val hostActivity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
    androidx.compose.runtime.DisposableEffect(hostActivity) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++
        }
        hostActivity?.lifecycle?.addObserver(observer)
        androidx.compose.runtime.onDispose {
            hostActivity?.lifecycle?.removeObserver(observer)
        }
    }
    var burstKey by remember { mutableIntStateOf(0) }
    var confettiKey by remember { mutableIntStateOf(0) }
    var banner by remember { mutableStateOf<String?>(null) }

    val dayStart = remember(tick) { GameProgress.dayStart(System.currentTimeMillis()) }
    DailyQuest.ensureDay(context)

    val habits = remember(tick) {
        Habits.all(context).filter { it.isScheduledForToday() }
    }
    val tasks = remember(tick) {
        ReminderSettings.listedRules(context).filter { it.isTaskModule() && it.enabled }
    }
    val habitDone = remember(tick) {
        habits.count { Habits.isCompletedToday(context, it.id, dayStart) }
    }
    val taskDone = remember(tick) {
        tasks.count { ReminderRecord.completedOn(context, it.module, it.title, dayStart) }
    }
    val todayActions = habitDone + taskDone
    val streak = remember(tick) { GameProgress.getGlobalStreak(context) }
    val level = remember(tick) { DailyQuest.level(context) }
    val expInLevel = remember(tick) { DailyQuest.levelProgress(context) }

    val totalTarget = habits.size + tasks.size
    val allDone = totalTarget > 0 && todayActions >= totalTarget

    fun afterCheck() {
        SoundKit.tick(context)
        DailyQuest.afterAction(context)
        burstKey++
        tick++
        if (todayActions + 1 == 3) banner = "冲刺模式已解锁，之后每项经验翻倍"
        if (todayActions + 1 >= totalTarget && totalTarget > 0) {
            banner = "今日全部完成，金宝箱到手"
            confettiKey++
        }
    }

    Box(Modifier.fillMaxSize().background(GameColors.Bg)) {
        // 顶部深绿渐变层
        Box(
            Modifier
                .fillMaxWidth()
                .height(230.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(GameColors.DeepGreen, GameColors.Bg)
                    )
                )
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            TopBar(onOpenSettings)
            Spacer(Modifier.height(14.dp))

            Column(Modifier.padding(horizontal = 18.dp)) {
                WavyEntrance(0) {
                    PlayerCard(level, expInLevel, streak, onOpenSettings)
                }

                Spacer(Modifier.height(14.dp))

                WavyEntrance(1) {
                    HeroSection(
                        done = todayActions,
                        total = totalTarget,
                        todayActions = todayActions,
                        burstKey = burstKey
                    )
                }

                Spacer(Modifier.height(20.dp))

                WavyEntrance(2) {
                    SectionTitle("每日关卡", 0)
                    if (habits.isEmpty()) {
                        EmptyHint("还没有微习惯，去事项页加一个")
                    } else {
                        habits.forEachIndexed { i, habit ->
                            val done = Habits.isCompletedToday(context, habit.id, dayStart)
                            LevelRow(
                                index = i,
                                title = habit.title,
                                iconKind = i,
                                done = done,
                                progressText = if (done) "已完成" else "待打卡",
                                burstKey = burstKey,
                                onCheck = {
                                    if (!done) {
                                        Habits.toggleCompletion(context, habit.id, dayStart, true)
                                        afterCheck()
                                    }
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }

                if (tasks.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    WavyEntrance(3) {
                        SectionTitle("任务副本", 1)
                        tasks.forEachIndexed { i, task ->
                            val done = ReminderRecord.completedOn(context, task.module, task.title, dayStart)
                            LevelRow(
                                index = i,
                                title = task.title.ifBlank { "未命名任务" },
                                iconKind = i + 1,
                                done = done,
                                progressText = if (done) "已完成" else "待完成",
                                burstKey = burstKey,
                                onCheck = {
                                    if (!done) {
                                        val now = System.currentTimeMillis()
                                        ReminderRecord.finish(
                                            context,
                                            java.util.UUID.randomUUID().toString(),
                                            task.title,
                                            ReminderRecord.KIND_DONE,
                                            now, now,
                                            "首页打卡",
                                            task.module
                                        )
                                        ReminderSettings.markCompleted(
                                            context, task.id, Math.max(1, task.dailyTarget)
                                        )
                                        afterCheck()
                                    }
                                }
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                WavyEntrance(4) {
                    RewardRoad(todayActions, totalTarget, confettiKey)
                }

                Spacer(Modifier.height(14.dp))

                WavyEntrance(5) {
                    BodyNoteCard(context) { tick++ }
                }

                Spacer(Modifier.height(10.dp))

                WavyEntrance(6) {
                    ReminderSwitchCard(context) { tick++ }
                }

                Spacer(Modifier.height(24.dp))
            }

            BottomBar(
                onHome = { },
                onTasks = onOpenTasks,
                onItems = onOpenItems,
                onStats = onOpenStats
            )
            Spacer(Modifier.height(16.dp))
        }

        // 庆祝横幅
        banner?.let { text ->
            CelebrationBanner(text) {
                banner = null
            }
        }
    }
}

@Composable
private fun TopBar(onOpenSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 闪电
        Canvas(Modifier.size(20.dp)) {
            val w = size.width
            val h = size.height
            val p = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.58f, 0f)
                lineTo(w * 0.16f, h * 0.56f)
                lineTo(w * 0.46f, h * 0.56f)
                lineTo(w * 0.38f, h)
                lineTo(w * 0.86f, h * 0.42f)
                lineTo(w * 0.54f, h * 0.42f)
                close()
            }
            drawPath(p, Color(0xFFFFE9A8))
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "休息豆芽",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.weight(1f)
        )
        BouncyBox(onClick = onOpenSettings) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.size(14.dp)) {
                    val w = size.width
                    drawCircle(Color.White, radius = w * 0.34f, center = center)
                    drawCircle(
                        Color.White, radius = w * 0.34f, center = center,
                        style = Stroke(1.4.dp.toPx())
                    )
                    drawLine(Color.White, Offset(0f, w * 0.5f), Offset(w, w * 0.5f), 1.2.dp.toPx())
                }
            }
        }
    }
}

@Composable
private fun PlayerCard(level: Int, expPercent: Int, streak: Int, onOpenSettings: () -> Unit) {
    GameCard(gold = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 等级徽章
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(GameColors.Gold, Color(0xFFFFB347)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Lv", style = MaterialTheme.typography.bodySmall, color = Color.White)
                    RollingNumber(
                        value = level,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }
            }
            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "连续 $streak 天",
                        style = MaterialTheme.typography.bodyLarge,
                        color = GameColors.Ink
                    )
                    Spacer(Modifier.width(6.dp))
                    Canvas(Modifier.size(12.dp)) {
                        // 小火苗
                        val w = size.width
                        val h = size.height
                        val p = androidx.compose.ui.graphics.Path().apply {
                            moveTo(w * 0.5f, 0f)
                            cubicTo(w, h * 0.35f, w * 0.9f, h, w * 0.5f, h)
                            cubicTo(w * 0.1f, h, 0f, h * 0.35f, w * 0.5f, 0f)
                            close()
                        }
                        drawPath(p, Color(0xFFFF9F5A))
                    }
                }
                Spacer(Modifier.height(8.dp))
                // 经验条
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(GameColors.Track)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(expPercent.coerceIn(0, 100) / 100f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF6FCFA0), GameColors.Primary)
                                )
                            )
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "经验 $expPercent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = GameColors.InkSoft
                )
            }

            Spacer(Modifier.width(12.dp))
            BouncyBox(onClick = onOpenSettings) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(GameColors.ChipGray),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(13.dp)) {
                        for (i in 0..2) {
                            val y = size.height * (0.25f + i * 0.25f)
                            drawLine(
                                GameColors.InkSoft, Offset(0f, y), Offset(size.width, y),
                                1.4.dp.toPx(), StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroSection(done: Int, total: Int, todayActions: Int, burstKey: Int) {
    GameCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("今日主线", style = MaterialTheme.typography.bodySmall, color = GameColors.InkSoft)
            Spacer(Modifier.height(6.dp))

            Box(contentAlignment = Alignment.Center) {
                BouncyRing(
                    progress = if (total > 0) done.toFloat() / total else 0f,
                    modifier = Modifier.fillMaxWidth(),
                    label = if (total > 0) "$done/$total" else "0"
                )
                StarBurst(trigger = burstKey, modifier = Modifier.size(96.dp))
            }

            Spacer(Modifier.height(10.dp))
            MilestoneRow(todayActions)
        }
    }
}

@Composable
private fun MilestoneRow(todayActions: Int) {
    val milestones = listOf(
        Triple(3, "冲刺", GameColors.Primary),
        Triple(6, "铜箱", Color(0xFFCD8F5A)),
        Triple(9, "银箱", Color(0xFF9FB2BA)),
        Triple(12, "金箱", GameColors.Gold)
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        milestones.forEach { (need, name, color) ->
            val reached = todayActions >= need
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(if (reached) color else GameColors.Track),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(Modifier.size(11.dp)) {
                        if (reached) {
                            drawLine(
                                Color.White, Offset(0f, size.height * 0.55f),
                                Offset(size.width * 0.4f, size.height * 0.9f),
                                1.8.dp.toPx(), StrokeCap.Round
                            )
                            drawLine(
                                Color.White, Offset(size.width * 0.4f, size.height * 0.9f),
                                Offset(size.width, size.height * 0.15f),
                                1.8.dp.toPx(), StrokeCap.Round
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "$need $name",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (reached) GameColors.Ink else GameColors.InkSoft
                )
            }
        }
    }
}

@Composable
private fun LevelRow(
    index: Int,
    title: String,
    iconKind: Int,
    done: Boolean,
    progressText: String,
    burstKey: Int,
    onCheck: () -> Unit
) {
    GameCard(modifier = Modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LevelIcon(kind = iconKind, done = done)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (done) GameColors.InkSoft else GameColors.Ink,
                    textDecoration = if (done) TextDecoration.LineThrough else null
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = GameColors.InkSoft
                )
            }
            Spacer(Modifier.width(10.dp))
            Box(contentAlignment = Alignment.Center) {
                CapsuleButton(
                    text = if (done) "已完成" else "打卡",
                    done = done,
                    onClick = onCheck
                )
                StarBurst(trigger = burstKey, modifier = Modifier.size(60.dp))
            }
        }
    }
}

@Composable
private fun RewardRoad(todayActions: Int, totalTarget: Int, confettiKey: Int) {
    val steps = listOf(3, 6, 9, 12)
    val reached = steps.count { todayActions >= it }
    val allUnlocked = totalTarget > 0 && todayActions >= totalTarget

    Box {
        GameCard(gold = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShakingChest(unlocked = allUnlocked) {
                    ChestGlyph(unlocked = reached > 0, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "奖励之路",
                        style = MaterialTheme.typography.bodyLarge,
                        color = GameColors.Ink
                    )
                    Spacer(Modifier.height(6.dp))
                    SegmentBar(total = steps.size, filled = reached, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(5.dp))
                    Text(
                        when {
                            allUnlocked -> "全部达成，金宝箱已解锁"
                            todayActions >= 9 -> "再完成 ${12 - todayActions} 项开金宝箱"
                            todayActions >= 6 -> "再看 3 项开银宝箱"
                            todayActions >= 3 -> "再完成 ${6 - todayActions} 项开铜宝箱"
                            else -> "完成 3 项解锁冲刺模式"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = GameColors.InkSoft
                    )
                }
            }
        }
        Confetti(trigger = confettiKey, modifier = Modifier.matchParentSize())
    }
}

@Composable
private fun SectionTitle(text: String, iconKind: Int) {
    Row(
        Modifier.padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LevelIcon(kind = iconKind, done = false, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = GameColors.Ink)
    }
}

@Composable
private fun EmptyHint(text: String) {
    GameCard {
        Text(text, style = MaterialTheme.typography.bodySmall, color = GameColors.InkSoft)
    }
}

@Composable
private fun CelebrationBanner(text: String, onDismiss: () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) { shown = true }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (shown) 1f else 0.85f,
        animationSpec = qSpring(),
        label = "banner"
    )
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "bannerAlpha"
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0x33000000)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(horizontal = 32.dp)
                .graphicsLayerScale(scale, alpha)
                .clip(GameShape)
                .background(GameColors.Card)
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "达成",
                style = MaterialTheme.typography.bodySmall,
                color = GameColors.InkSoft
            )
            Spacer(Modifier.height(8.dp))
            Text(text, style = MaterialTheme.typography.titleMedium, color = GameColors.Ink)
            Spacer(Modifier.height(16.dp))
            CapsuleButton(text = "继续", onClick = onDismiss)
        }
    }
}

private fun Modifier.graphicsLayerScale(scale: Float, alpha: Float): Modifier =
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
        this.alpha = alpha
    }

@Composable
private fun BottomBar(
    onHome: () -> Unit,
    onTasks: () -> Unit,
    onItems: () -> Unit,
    onStats: () -> Unit
) {
    val tabs = listOf("首页" to onHome, "任务" to onTasks, "事项" to onItems, "统计" to onStats)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(GameShape)
            .background(GameColors.Card)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { i, (name, action) ->
            val active = i == 0
            BouncyBox(onClick = action) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(20.dp)) {
                        val c = if (active) GameColors.Primary else GameColors.InkSoft
                        val w = size.width
                        val h = size.height
                        when (i) {
                            0 -> {
                                drawRoundRect(
                                    color = c,
                                    topLeft = Offset(w * 0.15f, h * 0.35f),
                                    size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.55f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
                                    style = Stroke(1.7.dp.toPx())
                                )
                                drawLine(c, Offset(w * 0.1f, h * 0.35f), Offset(w * 0.5f, h * 0.08f), 1.7.dp.toPx(), StrokeCap.Round)
                                drawLine(c, Offset(w * 0.5f, h * 0.08f), Offset(w * 0.9f, h * 0.35f), 1.7.dp.toPx(), StrokeCap.Round)
                            }
                            1 -> {
                                for (k in 0..2) {
                                    val y = h * (0.22f + k * 0.28f)
                                    drawLine(c, Offset(w * 0.15f, y), Offset(w * 0.85f, y), 1.7.dp.toPx(), StrokeCap.Round)
                                }
                            }
                            2 -> {
                                drawRoundRect(
                                    color = c,
                                    topLeft = Offset(w * 0.15f, h * 0.15f),
                                    size = androidx.compose.ui.geometry.Size(w * 0.7f, h * 0.7f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f),
                                    style = Stroke(1.7.dp.toPx())
                                )
                                drawCircle(c, radius = w * 0.08f, center = Offset(w * 0.5f, h * 0.5f))
                            }
                            else -> {
                                drawLine(c, Offset(w * 0.15f, h * 0.85f), Offset(w * 0.15f, h * 0.45f), 1.7.dp.toPx(), StrokeCap.Round)
                                drawLine(c, Offset(w * 0.5f, h * 0.85f), Offset(w * 0.5f, h * 0.25f), 1.7.dp.toPx(), StrokeCap.Round)
                                drawLine(c, Offset(w * 0.85f, h * 0.85f), Offset(w * 0.85f, h * 0.6f), 1.7.dp.toPx(), StrokeCap.Round)
                            }
                        }
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        name,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (active) GameColors.Primary else GameColors.InkSoft
                    )
                }
            }
        }
    }
}

/** 身体记录：折叠在卡片里，点开弹输入框；水滴直接记喝水。 */
@Composable
private fun BodyNoteCard(context: Context, onChange: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    val today = remember { java.util.Calendar.getInstance() }
    val saved = remember(editing) { DailyBodyNote.read(context, today) }
    val water = remember { if (WaterLog.enabled(context)) WaterLog.todayTotal(context) else 0 }

    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "今天身体怎么样",
                    style = MaterialTheme.typography.bodyLarge,
                    color = GameColors.Ink
                )
                if (saved.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        saved,
                        style = MaterialTheme.typography.bodySmall,
                        color = GameColors.InkSoft,
                        maxLines = 1
                    )
                }
            }

            if (WaterLog.enabled(context)) {
                BouncyBox(
                    onClick = {
                        val activity = context as? android.app.Activity
                        if (activity != null) {
                            WaterDialogs.showAmount(activity) { onChange() }
                        }
                    }
                ) {
                    Box(
                        Modifier
                            .clip(GamePill)
                            .background(GameColors.Primary.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (water > 0) "\uD83D\uDCA7 ${water}ml" else "\uD83D\uDCA7",
                            style = MaterialTheme.typography.bodySmall,
                            color = GameColors.Ink
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
            }

            CapsuleButton(text = "记一句", onClick = { editing = true })
        }
    }

    if (editing) {
        var text by remember { mutableStateOf(saved) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = false },
            title = {
                Text("今天身体怎么样", style = MaterialTheme.typography.titleMedium, color = GameColors.Ink)
            },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 3,
                    placeholder = {
                        Text("睡得好不好、精神、酸痛…随便写一句", style = MaterialTheme.typography.bodySmall)
                    }
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    DailyBodyNote.save(context, today, text)
                    editing = false
                    onChange()
                }) { Text("保存", color = GameColors.Primary) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { editing = false }) {
                    Text("取消", color = GameColors.InkSoft)
                }
            }
        )
    }
}

/** 提醒运行开关：放在页面最下方，轻量。 */
@Composable
private fun ReminderSwitchCard(context: Context, onChange: () -> Unit) {
    var on by remember { mutableStateOf(ReminderSettings.enabled(context)) }
    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(if (on) GameColors.Primary else GameColors.Track)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("提醒运行中", style = MaterialTheme.typography.bodySmall, color = GameColors.Ink)
                Text(
                    if (on) "到点会提醒你休息" else "已暂停，不会打扰",
                    style = MaterialTheme.typography.bodySmall,
                    color = GameColors.InkSoft
                )
            }
            androidx.compose.material3.Switch(
                checked = on,
                onCheckedChange = {
                    on = it
                    ReminderSettings.setEnabled(context, it)
                    onChange()
                },
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = GameColors.Primary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = GameColors.Track,
                    uncheckedBorderColor = Color.Transparent
                )
            )
        }
    }
}
