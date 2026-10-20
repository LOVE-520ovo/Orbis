package me.rerere.rikkahub.ui.pages.orbis

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Colors
import me.rerere.hugeicons.stroke.UserGroup
import me.rerere.rikkahub.data.orbis.cloud.OrbisCloudGroupClient
import me.rerere.rikkahub.data.orbis.cloud.OrbisCloudGroupClient.CloudGroupMessage
import me.rerere.rikkahub.data.orbis.cloud.OrbisPrivateChatSync
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.MarkdownNew

private const val PREFS = "orbis_cloud_group_prefs"
private const val KEY_BUBBLE_ALPHA = "bubble_alpha"
private const val KEY_BG_COLOR = "bg_color"

private val BG_PRESETS = listOf(
    0xFF0B0E14L, // 默认深空
    0xFF1A1420L, // 紫夜
    0xFF12201AL, // 苔绿
    0xFF20150B, // 暖棕
    0xFF0F1622L, // 靛蓝
    0xFF2A1515L, // 酒红
)

private fun displayName(sender: String): String = when (sender) {
    "xiaoya" -> "小雅"
    "gabriel" -> "加百列"
    "shuoye" -> "朔夜"
    "samael" -> "萨麦尔"
    else -> sender
}

@Composable
fun OrbisCloudGroupPage(hostVisible: Boolean = true) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<CloudGroupMessage>>(emptyList()) }
    var input by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var bubbleAlpha by remember { mutableStateOf(prefs.getFloat(KEY_BUBBLE_ALPHA, 0.9f)) }
    var bgColorValue by remember { mutableStateOf(prefs.getLong(KEY_BG_COLOR, BG_PRESETS.first())) }
    val listState = rememberLazyListState()

    fun savePrefs() {
        prefs.edit().putFloat(KEY_BUBBLE_ALPHA, bubbleAlpha).putLong(KEY_BG_COLOR, bgColorValue).apply()
    }

    suspend fun refresh() {
        try {
            messages = OrbisCloudGroupClient.fetchMessages()
            error = null
        } catch (e: Exception) {
            error = e.message
        }
    }

    LaunchedEffect(Unit) {
        // 首次进入：单聊历史全量回补到云端（后台跑，不阻塞群聊流）
        launch { runCatching { OrbisPrivateChatSync.syncOnce(context) } }
        var tick = 0
        while (true) {
            refresh()
            tick++
            // 每 20 轮（约 60 秒）增量同步一次单聊
            if (tick % 20 == 0) {
                launch { runCatching { OrbisPrivateChatSync.syncOnce(context) } }
            }
            delay(3000)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    OrbisPageSurface {
        Box(Modifier.fillMaxSize().background(Color(bgColorValue))) {
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = OrbisTheme.colors.ink,
                topBar = {
                    OrbisPageHeader(
                        title = "云端群聊",
                        subtitle = "朔夜 · 加百列 · 服务器唤醒",
                        avatar = { Icon(HugeIcons.UserGroup, null) },
                        navigationIcon = { BackButton() },
                        actions = {
                            Icon(
                                HugeIcons.Colors,
                                contentDescription = "外观",
                                tint = OrbisTheme.colors.ink,
                                modifier = Modifier.size(22.dp).clickable { showSettings = true }
                            )
                        },
                        modifier = Modifier.statusBarsPadding()
                    )
                }
            ) { padding ->
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (error != null) {
                        Text("连接失败：$error", color = Color(0xFFE8B4A0),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 20.dp))
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(messages) { m -> CloudMessageBubble(m, bubbleAlpha) }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("说点什么…") },
                            maxLines = 4
                        )
                        Button(
                            onClick = {
                                val text = input.trim()
                                if (text.isEmpty() || sending) return@Button
                                scope.launch {
                                    sending = true
                                    try {
                                        OrbisCloudGroupClient.sendMessage(text)
                                        input = ""
                                        refresh()
                                    } catch (e: Exception) {
                                        error = e.message
                                    } finally {
                                        sending = false
                                    }
                                }
                            },
                            enabled = !sending
                        ) {
                            if (sending) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
                            else Text("发送")
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false; savePrefs() },
            confirmButton = {
                TextButton(onClick = { showSettings = false; savePrefs() }) { Text("好") }
            },
            title = { Text("群聊外观") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("气泡透明度：${(bubbleAlpha * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = OrbisTheme.colors.mutedInk)
                    Slider(
                        value = bubbleAlpha,
                        onValueChange = { bubbleAlpha = it },
                        valueRange = 0.15f..1f
                    )
                    Text("群聊背景", style = MaterialTheme.typography.bodySmall,
                        color = OrbisTheme.colors.mutedInk)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BG_PRESETS.forEach { c ->
                            val selected = bgColorValue == c
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .background(Color(c), CircleShape)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) OrbisTheme.colors.accent
                                        else OrbisTheme.colors.border,
                                        shape = CircleShape
                                    )
                                    .clickable { bgColorValue = c }
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun CloudMessageBubble(m: CloudGroupMessage, bubbleAlpha: Float) {
    val mine = m.sender == "xiaoya"
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier.padding(4.dp),
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start
        ) {
            Text(
                displayName(m.sender),
                style = MaterialTheme.typography.labelSmall,
                color = OrbisTheme.colors.mutedInk
            )
            Box(
                Modifier
                    .background(
                        if (mine) OrbisTheme.colors.accent.copy(alpha = bubbleAlpha * 0.16f)
                        else OrbisTheme.colors.tintedPanel.copy(alpha = bubbleAlpha),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (m.kind == "system") {
                    Text(m.content, style = MaterialTheme.typography.bodySmall, color = OrbisTheme.colors.mutedInk)
                } else {
                    MarkdownNew(m.content)
                }
            }
        }
    }
}
