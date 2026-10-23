package me.rerere.rikkahub.ui.pages.orbis

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Puzzle
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalNavController

/** 插件页：所有 Orbis 插件从这里进。新插件在此登记。 */
@Composable
fun OrbisPluginsPage(hostVisible: Boolean = true) {
    val navigator = LocalNavController.current
    OrbisPageSurface {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = OrbisTheme.colors.ink,
            topBar = {
                OrbisPageHeader(
                    title = "插件",
                    subtitle = "给 Orbis 加的扩展，都在这里",
                    avatar = { Icon(HugeIcons.Puzzle, null) },
                    navigationIcon = { BackButton() },
                    modifier = Modifier.statusBarsPadding()
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OrbisNavigationCard(
                    OrbisNavigationItem(
                        "wake-monitor",
                        "唤醒观测台",
                        "每个 AI 醒来多少次、念头、发言、工具调用，分机看",
                        HugeIcons.Puzzle,
                        { navigator.navigate(Screen.OrbisWakeMonitor) }
                    ),
                    Modifier.fillMaxWidth()
                )
                Text(
                    "新插件写好之后，在这里挂一张卡片就能进。",
                    style = MaterialTheme.typography.bodySmall,
                    color = OrbisTheme.colors.mutedInk
                )
            }
        }
    }
}
