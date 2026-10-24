package me.rerere.rikkahub.ui.pages.orbis

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.File02
import me.rerere.hugeicons.stroke.Colors
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.UserGroup
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Avatar
import me.rerere.rikkahub.data.orbis.cloud.OrbisCloudGroupClient
import me.rerere.rikkahub.data.orbis.cloud.OrbisCloudGroupClient.Attachment
import me.rerere.rikkahub.data.orbis.cloud.OrbisCloudGroupClient.CloudGroupMessage
import me.rerere.rikkahub.data.orbis.cloud.OrbisPrivateChatSync
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.richtext.MarkdownNew
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import org.koin.core.context.GlobalContext
import java.io.File

private const val PREFS = "orbis_cloud_group_prefs"
private const val KEY_BUBBLE_ALPHA = "bubble_alpha"
private const val KEY_BG_COLOR = "bg_color"
private const val KEY_BG_IMAGE = "bg_image"

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
    "raphael" -> "拉斐尔"
    "samael" -> "萨麦尔"
    else -> sender
}

private fun formatTs(iso: String): String = runCatching {
    val t = iso.substringBefore("+").substringBefore("Z").take(19)
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
    val d = sdf.parse(t) ?: return@runCatching ""
    val out = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US)
    out.timeZone = java.util.TimeZone.getTimeZone("Asia/Hong_Kong")
    out.format(d)
}.getOrDefault("")

private fun assistantOf(name: String, assistants: List<Assistant>): Assistant? {
    val n = name.trim()
    return assistants.firstOrNull { it.name.contains(n) }
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
    var bgImagePath by remember { mutableStateOf(prefs.getString(KEY_BG_IMAGE, null)) }
    val listState = rememberLazyListState()

    // 单聊里的 AI 列表（头像 + 名字来源）
    val assistants by produceState<List<Assistant>>(emptyList()) {
        value = runCatching {
            GlobalContext.get().get<SettingsStore>().settingsFlow.first { !it.init }.assistants
        }.getOrDefault(emptyList())
    }

    // 小雅自己的头像（Orbis 显示设置里那个）
    var userAvatar by remember { mutableStateOf<Avatar>(Avatar.Dummy) }
    LaunchedEffect(Unit) {
        userAvatar = runCatching {
            GlobalContext.get().get<SettingsStore>().settingsFlow.first { !it.init }.displaySetting.userAvatar
        }.getOrDefault(Avatar.Dummy)
    }

    fun savePrefs() {
        prefs.edit()
            .putFloat(KEY_BUBBLE_ALPHA, bubbleAlpha)
            .putLong(KEY_BG_COLOR, bgColorValue)
            .apply()
        if (bgImagePath != null) prefs.edit().putString(KEY_BG_IMAGE, bgImagePath).apply()
        else prefs.edit().remove(KEY_BG_IMAGE).apply()
    }

    suspend fun refresh() {
        try {
            messages = OrbisCloudGroupClient.fetchMessages()
            error = null
        } catch (e: Exception) {
            error = e.message
        }
    }

    // 背景图选择
    val bgPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    val dest = File(context.filesDir, "cloud_group_bg")
                    context.contentResolver.openInputStream(uri)?.use { ins ->
                        dest.outputStream().use { outs -> ins.copyTo(outs) }
                    }
                    bgImagePath = dest.absolutePath
                    savePrefs()
                }
            }
        }
    }

    // 图片附件选择
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            sending = true
            try {
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val name = "图片.${mime.substringAfterLast('/')}"
                val att = OrbisCloudGroupClient.uploadAttachment(context, uri, name, mime)
                OrbisCloudGroupClient.sendMessage("", att)
                refresh()
            } catch (e: Exception) {
                error = e.message
            } finally {
                sending = false
            }
        }
    }

    // 文件附件选择
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            sending = true
            try {
                val (name, mime) = withContext(Dispatchers.IO) {
                    val n = queryDisplayName(context, uri) ?: "文件"
                    val m = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    n to m
                }
                val att = OrbisCloudGroupClient.uploadAttachment(context, uri, name, mime)
                OrbisCloudGroupClient.sendMessage("", att)
                refresh()
            } catch (e: Exception) {
                error = e.message
            } finally {
                sending = false
            }
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
            // 自定义背景图（优先级高于纯色）
            if (bgImagePath != null) {
                AsyncImage(
                    model = File(bgImagePath!!),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                )
            }
            Scaffold(
                containerColor = Color.Transparent,
                contentColor = OrbisTheme.colors.ink,
                topBar = {
                    OrbisPageHeader(
                        title = "天使轮盘赌（4）",
                        centered = true,
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages) { m ->
                            CloudMessageRow(m, bubbleAlpha, assistants, userAvatar)
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            HugeIcons.Image02,
                            contentDescription = "发图片",
                            tint = OrbisTheme.colors.mutedInk,
                            modifier = Modifier.size(22.dp).clickable { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                        )
                        Icon(
                            HugeIcons.File02,
                            contentDescription = "发文件",
                            tint = OrbisTheme.colors.mutedInk,
                            modifier = Modifier.size(22.dp).clickable { filePicker.launch("*/*") }
                        )
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
                            val selected = bgImagePath == null && bgColorValue == c
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
                                    .clickable { bgImagePath = null; bgColorValue = c }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { bgPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("选择背景图片") }
                        if (bgImagePath != null) {
                            TextButton(onClick = {
                                bgImagePath = null
                                savePrefs()
                            }) { Text("移除图片") }
                        }
                    }
                }
            }
        )
    }
}

private fun queryDisplayName(context: Context, uri: Uri): String? {
    var name: String? = null
    val cursor: Cursor? = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) name = it.getString(idx)
        }
    }
    return name
}

@Composable
private fun CloudMessageRow(
    m: CloudGroupMessage,
    bubbleAlpha: Float,
    assistants: List<Assistant>,
    userAvatar: Avatar,
) {
    // 工具过程消息：居中灰条
    if (m.kind == "tool") {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                m.content,
                style = MaterialTheme.typography.bodySmall,
                color = OrbisTheme.colors.mutedInk,
                modifier = Modifier
                    .background(
                        OrbisTheme.colors.tintedPanel.copy(alpha = 0.55f),
                        RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
        return
    }

    val mine = m.sender == "xiaoya"
    val name = displayName(m.sender)
    val assistant = if (mine) null else assistantOf(name, assistants)
    val ts = formatTs(m.createdAt)

    if (mine) {
        // 小雅：右侧，头像 + 昵称 + 单个气泡 + 时间戳
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                Modifier.widthIn(max = 300.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text("小雅Lys", style = MaterialTheme.typography.labelSmall,
                    color = OrbisTheme.colors.mutedInk)
                CloudBubble(m, bubbleAlpha, mine)
                if (ts.isNotBlank()) {
                    Text(ts, style = MaterialTheme.typography.labelSmall,
                        color = OrbisTheme.colors.mutedInk)
                }
            }
            UIAvatar(
                name = "小雅Lys",
                value = userAvatar,
                modifier = Modifier.padding(start = 8.dp).size(30.dp)
            )
        }
        return
    }

    // AI：按空行拆成多个气泡，每个气泡带头像 + 昵称，只有最后一个气泡带时间戳
    val segments = m.content.split(Regex("\\n{2,}")).filter { it.isNotBlank() }
    val parts = if (segments.size <= 1) listOf(m.content) else segments
    Column(Modifier.fillMaxWidth()) {
        parts.forEachIndexed { idx, seg ->
            val isLast = idx == parts.lastIndex
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                UIAvatar(
                    name = name,
                    value = assistant?.avatar ?: Avatar.Dummy,
                    modifier = Modifier.size(30.dp)
                )
                Column(
                    Modifier.widthIn(max = 300.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(assistant?.name?.ifBlank { name } ?: name,
                        style = MaterialTheme.typography.labelSmall,
                        color = OrbisTheme.colors.mutedInk)
                    val segMsg = if (idx == 0) {
                        m.copy(content = seg)
                    } else {
                        m.copy(content = seg, attachmentUrl = "", attachmentName = "", attachmentType = "")
                    }
                    CloudBubble(segMsg, bubbleAlpha, mine)
                    if (isLast && ts.isNotBlank()) {
                        Text(ts, style = MaterialTheme.typography.labelSmall,
                            color = OrbisTheme.colors.mutedInk)
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudBubble(m: CloudGroupMessage, bubbleAlpha: Float, mine: Boolean) {
    Column(
        Modifier
            .background(
                if (mine) OrbisTheme.colors.accent.copy(alpha = bubbleAlpha * 0.16f)
                else OrbisTheme.colors.tintedPanel.copy(alpha = bubbleAlpha),
                RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // 附件
        if (m.attachmentUrl.isNotBlank()) {
            if (m.attachmentType == "image") {
                AsyncImage(
                    model = m.attachmentUrl,
                    contentDescription = m.attachmentName,
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .heightIn(max = 280.dp),
                )
            } else {
                Row(
                    Modifier.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(HugeIcons.File02, null,
                        modifier = Modifier.size(16.dp),
                        tint = OrbisTheme.colors.mutedInk)
                    Text(
                        m.attachmentName,
                        style = MaterialTheme.typography.bodySmall,
                        color = OrbisTheme.colors.ink
                    )
                }
            }
        }
        if (m.content.isNotBlank()) {
            if (m.kind == "system") {
                Text(m.content, style = MaterialTheme.typography.bodySmall,
                    color = OrbisTheme.colors.mutedInk)
            } else {
                // 字号与单聊一致（bodyMedium）
                MarkdownNew(m.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}