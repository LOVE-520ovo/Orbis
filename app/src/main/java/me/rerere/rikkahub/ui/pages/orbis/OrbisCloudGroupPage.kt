package me.rerere.rikkahub.ui.pages.orbis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.UserGroup
import me.rerere.rikkahub.data.orbis.cloud.OrbisCloudGroupClient
import me.rerere.rikkahub.data.orbis.cloud.OrbisCloudGroupClient.CloudGroupMessage
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.MarkdownNew

private fun displayName(sender: String): String = when (sender) {
    "xiaoya" -> "小雅"
    "gabriel" -> "加百列"
    "shuoye" -> "朔夜"
    "samael" -> "萨麦尔"
    else -> sender
}

@Composable
fun OrbisCloudGroupPage(hostVisible: Boolean = true) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<CloudGroupMessage>>(emptyList()) }
    var input by rememberSaveable { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    suspend fun refresh() {
        try {
            messages = OrbisCloudGroupClient.fetchMessages()
            error = null
        } catch (e: Exception) {
            error = e.message
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            refresh()
            delay(3000)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
    }

    OrbisPageSurface {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = OrbisTheme.colors.ink,
            topBar = {
                OrbisPageHeader(
                    title = "云端群聊",
                    subtitle = "朔夜 · 加百列 · 服务器唤醒",
                    avatar = { Icon(HugeIcons.UserGroup, null) },
                    navigationIcon = { BackButton() },
                    modifier = Modifier.statusBarsPadding()
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                if (error != null) {
                    Text("连接失败：$error", color = Color(0xFFC0392B),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 20.dp))
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { m -> CloudMessageBubble(m) }
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

@Composable
private fun CloudMessageBubble(m: CloudGroupMessage) {
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
                        if (mine) OrbisTheme.colors.accent.copy(alpha = 0.14f)
                        else OrbisTheme.colors.tintedPanel,
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
