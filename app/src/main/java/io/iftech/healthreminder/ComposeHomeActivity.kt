package io.iftech.healthreminder

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

/**
 * 新版首页容器：只负责挂 Compose 内容，业务逻辑仍在原有 Java 数据层。
 */
class ComposeHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiPalette.applyWindow(window)
        setContent {
            DewTheme {
                DewHomeScreen(
                    context = this,
                    onOpenTasks = {
                        startActivity(Intent(this, TaskActivity::class.java))
                    },
                    onOpenSettings = {
                        startActivity(Intent(this, SettingsActivity::class.java))
                    }
                )
            }
        }
    }
}
