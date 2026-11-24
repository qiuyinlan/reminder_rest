package io.iftech.healthreminder

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

internal class PermissionItem(
    val key: String,
    val title: String,
    val detail: String,
    val ok: Boolean
)

/**
 * 提醒权限检查：从首页搬进设置，逐项显示状态并可跳转。
 */
class PermissionCheckActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiPalette.applyWindow(window)
        setContent {
            DewTheme {
                PermissionScreen(
                    checks = buildChecks(),
                    onOpen = { key -> openSetting(key) },
                    onBack = { finish() }
                )
            }
        }
    }

    private fun buildChecks(): List<PermissionItem> = listOf(
        PermissionItem(
            "notify", "通知",
            if (notificationOk()) "已开启" else "开启后才能收到提醒",
            notificationOk()
        ),
        PermissionItem(
            "exact", "精确闹钟",
            if (exactAlarmOk()) "已允许" else "允许后提醒才准点",
            exactAlarmOk()
        ),
        PermissionItem(
            "fullscreen", "全屏提醒",
            if (fullScreenOk()) "已允许" else "锁屏时也能全屏弹出",
            fullScreenOk()
        ),
        PermissionItem(
            "overlay", "悬浮窗",
            if (overlayOk()) "已允许" else "用于霸屏提醒",
            overlayOk()
        ),
        PermissionItem(
            "battery", "后台不被限制",
            if (batteryOk()) "已加入白名单" else "防止息屏后被系统杀掉",
            batteryOk()
        ),
        PermissionItem(
            "usage", "使用情况访问",
            if (UsageAccess.hasAccess(this)) "已授权" else "统计屏幕使用时长",
            UsageAccess.hasAccess(this)
        ),
        PermissionItem(
            "accessibility", "应用休息提醒服务",
            if (watchServiceEnabled()) "已启用" else "软拦截需要无障碍服务",
            watchServiceEnabled()
        )
    )

    private fun notificationOk(): Boolean {
        val runtime = Build.VERSION.SDK_INT < 33 ||
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        return runtime && NotificationManagerCompat.from(this).areNotificationsEnabled()
    }

    private fun exactAlarmOk(): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        val alarms = getSystemService(AlarmManager::class.java) ?: return false
        return alarms.canScheduleExactAlarms()
    }

    private fun fullScreenOk(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val nm = getSystemService(NotificationManager::class.java) ?: return false
        return nm.canUseFullScreenIntent()
    }

    private fun overlayOk(): Boolean =
        Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)

    private fun batteryOk(): Boolean {
        val pm = getSystemService(PowerManager::class.java) ?: return false
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun watchServiceEnabled(): Boolean {
        val manager = getSystemService(AccessibilityManager::class.java) ?: return false
        for (info in manager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )) {
            val si = info.resolveInfo?.serviceInfo ?: continue
            if (packageName == si.packageName && AppWatchService::class.java.name == si.name) {
                return true
            }
        }
        return false
    }

    private fun openSetting(key: String) {
        try {
            when (key) {
                "notify" -> startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
                "exact" -> startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:$packageName")
                    )
                )
                "fullscreen" -> startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    )
                )
                "overlay" -> startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
                "battery" -> startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")
                    )
                )
                "usage" -> startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                "accessibility" -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        } catch (ignored: Exception) {
            // 某些 ROM 没有对应设置页
        }
    }
}

@Composable
internal fun PermissionScreen(
    checks: List<PermissionItem>,
    onOpen: (String) -> Unit,
    onBack: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(Dew.Bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBack)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("提醒权限", style = MaterialTheme.typography.headlineMedium, color = Dew.Ink)
                    Text(
                        "逐项检查，缺哪项点哪项",
                        style = MaterialTheme.typography.bodySmall,
                        color = Dew.InkFaint
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            checks.forEach { check ->
                GlassCard {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(if (check.ok) Dew.Accent else Dew.Track),
                            contentAlignment = Alignment.Center
                        ) {
                            if (check.ok) {
                                Canvas(Modifier.size(10.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    drawLine(
                                        Color.White,
                                        Offset(w * 0.1f, h * 0.55f),
                                        Offset(w * 0.42f, h * 0.88f),
                                        1.8.dp.toPx(),
                                        StrokeCap.Round
                                    )
                                    drawLine(
                                        Color.White,
                                        Offset(w * 0.42f, h * 0.88f),
                                        Offset(w * 0.92f, h * 0.15f),
                                        1.8.dp.toPx(),
                                        StrokeCap.Round
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(check.title, style = MaterialTheme.typography.labelLarge, color = Dew.Ink)
                            Text(check.detail, style = MaterialTheme.typography.bodySmall, color = Dew.InkFaint)
                        }
                        if (!check.ok) {
                            Box(
                                Modifier
                                    .clip(Dew.ShapePill)
                                    .background(Dew.Accent.copy(alpha = 0.12f))
                                    .clickable { onOpen(check.key) }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text("去开启", style = MaterialTheme.typography.bodySmall, color = Dew.Accent)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 240f),
        label = "back"
    )
    Box(
        Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .size(38.dp)
            .clip(Dew.ShapeCard)
            .background(Dew.GlassFill)
            .border(1.dp, Dew.GlassBorder, Dew.ShapeCard)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onBack
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(9.dp)) {
            val w = size.width
            val h = size.height
            drawLine(Dew.InkSoft, Offset(w, 0f), Offset(0f, h / 2f), 1.6.dp.toPx(), StrokeCap.Round)
            drawLine(Dew.InkSoft, Offset(w, h), Offset(0f, h / 2f), 1.6.dp.toPx(), StrokeCap.Round)
        }
    }
}
