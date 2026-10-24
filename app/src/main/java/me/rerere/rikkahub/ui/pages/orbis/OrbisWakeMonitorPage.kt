package me.rerere.rikkahub.ui.pages.orbis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Brain02
import me.rerere.rikkahub.data.orbis.cloud.OrbisWakeClient
import me.rerere.rikkahub.data.orbis.cloud.OrbisWakeClient.WakeLog
import me.rerere.rikkahub.ui.components.nav.BackButton
import org.json.JSONObject

private val INSTANCES = listOf(
    "gabriel" to "加百列",
    "shuoye" to "朔夜",
    "raphael" to "拉斐尔",
    "samael" to "萨麦尔",
)

private fun instanceName(key: String): String = INSTANCES.firstOrNull { it.first == key }?.second ?: key

private fun formatHk(iso: String): String = runCatching {
    val t = iso.substringBefore("+").substringBefore("Z").take(19)
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
    val d = sdf.parse(t) ?: return@runCatching ""
    val out = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US)
    out.timeZone = java.util.TimeZone.getTimeZone("Asia/Hong_Kong")
    out.format(d)
}.getOrDefault("")

private fun sourceLabel(s: String): String = when {
    s == "tick" -> "定时醒来"
    s.startsWith("@") -> "被$s"
    else -> s
}

private fun decisionLabel(d: String): String = when (d) {
    "spoke" -> "开口"
    "silent" -> "沉默"
    else -> d
}

private fun toolDetail(raw: String): String = runCatching {
    if (raw.isBlank() || raw == "null") return ""
    val o = JSONObject(raw)
    val lines = mutableListOf<String>()
    val cmd = o.optString("cmd", "")
    when (cmd) {
        "memo" -> lines.add("工具：写记忆")
        "search" -> lines.add("工具：检索聊天记录")
        "" -> {}
        else -> lines.add("命令：$cmd")
    }
    val arg = o.optString("arg", "")
    if (arg.isNotBlank()) lines.add("做了什么：" + arg.replace("\n", " "))
    val res = o.optString("result", "")
    if (res.isNotBlank()) lines.add("结果：" + res.replace("\n", " ").let { if (it.length > 120) it.take(120) + "…" else it })
    val gate = o.optString("gate", "")
    if (gate.isNotBlank()) lines.add("群聊投递：$gate")
    if (o.optString("error", "").isNotBlank()) lines.add("错误：" + o.optString("error"))
    lines.joinToString("\n")
}.getOrDefault("")

@Composable
fun OrbisWakeMonitorPage(hostVisible: Boolean = true) {
    var selected by rememberSaveable { mutableStateOf("shuoye") }
    var logs by remember { mutableStateOf<List<WakeLog>>(emptyList()) }
    var traces by remember { mutableStateOf<List<OrbisWakeClient.ToolTrace>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selected) {
        loading = true
        error = null
        try {
            logs = OrbisWakeClient.fetchLogs(selected, limit = 200)
            traces = OrbisWakeClient.fetchToolTraces(selected, limit = 60)
        } catch (e: Exception) {
            error = e.message ?: "读取失败"
        } finally {
            loading = false
        }
    }

    val name = instanceName(selected)
    OrbisPageSurface {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = OrbisTheme.colors.ink,
            topBar = {
                OrbisPageHeader(
                    title = "唤醒观测台",
                    subtitle = "每次醒来：次数 · 念头 · 发言 · 工具",
                    avatar = { Icon(HugeIcons.Brain02, null) },
                    navigationIcon = { BackButton() },
                    modifier = Modifier.statusBarsPadding()
                )
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                // 分机切换
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    INSTANCES.forEach { (key, label) ->
                        FilterChip(
                            selected = selected == key,
                            onClick = { selected = key },
                            label = {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                        )
                    }
                }

                when {
                    loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = OrbisTheme.colors.accent)
                    }
                    error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(error ?: "", color = OrbisTheme.colors.mutedInk)
                    }
                    logs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("$name 还没有醒来记录", color = OrbisTheme.colors.mutedInk)
                    }
                    else -> WakeTimeline(name, logs, traces)
                }
            }
        }
    }
}

@Composable
private fun WakeTimeline(name: String, logs: List<WakeLog>, traces: List<OrbisWakeClient.ToolTrace>) {
    val colors = OrbisTheme.colors
    val today = java.text.SimpleDateFormat("MM-dd", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("Asia/Hong_Kong") }
        .format(java.util.Date())
    val todayLogs = logs.filter { formatHk(it.firedAt).startsWith(today) }
    val spoke = logs.count { it.decision == "spoke" }
    val silent = logs.count { it.decision == "silent" }
    val toolCount = logs.count { it.toolCalls.isNotBlank() && it.toolCalls != "null" }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("$name", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight(790))
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCell("今日醒来", "${todayLogs.size}", Modifier.weight(1f))
                StatCell("开口", "$spoke", Modifier.weight(1f))
                StatCell("沉默", "$silent", Modifier.weight(1f))
                StatCell("工具", "$toolCount", Modifier.weight(1f))
            }
        }
        item {
            Text(
                "工具调用记录",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight(790),
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                "群聊里每一条工具过程（记记忆、检索等）的全文",
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedInk,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (traces.isEmpty()) {
            item {
                Text(
                    "还没有工具调用记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.mutedInk,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        } else {
            items(traces, key = { "trace-" + it.id }) { trace -> TraceCard(trace) }
        }
        item {
            Text(
                "醒来时间轴",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight(790),
                modifier = Modifier.padding(top = 10.dp)
            )
            Text(
                "最近 ${logs.size} 次醒来（香港时间，最多 200 条）",
                style = MaterialTheme.typography.bodySmall,
                color = colors.mutedInk,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        items(logs, key = { it.id }) { log -> WakeCard(log) }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = OrbisTheme.colors
    Column(
        modifier.background(colors.raisedPanel, RoundedCornerShape(14.dp)).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight(790), color = colors.accent)
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.mutedInk)
    }
}

@Composable
private fun TraceCard(trace: OrbisWakeClient.ToolTrace) {
    val colors = OrbisTheme.colors
    Column(
        Modifier.fillMaxWidth().background(colors.accent.copy(alpha = .07f), RoundedCornerShape(14.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(formatHk(trace.createdAt), style = MaterialTheme.typography.labelSmall, color = colors.accent)
        Text(trace.content, style = MaterialTheme.typography.bodySmall, color = colors.ink)
    }
}

@Composable
private fun WakeCard(log: WakeLog) {
    val colors = OrbisTheme.colors
    val tool = toolDetail(log.toolCalls)
    val isSpoke = log.decision == "spoke"
    Column(
        Modifier.fillMaxWidth().background(colors.raisedPanel, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(formatHk(log.firedAt), style = MaterialTheme.typography.labelMedium, color = colors.mutedInk)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isSpoke) colors.accent.copy(alpha = .16f) else colors.border.copy(alpha = .5f)
            ) {
                Text(
                    decisionLabel(log.decision),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSpoke) colors.accent else colors.mutedInk
                )
            }
            Text(sourceLabel(log.source), style = MaterialTheme.typography.labelSmall, color = colors.mutedInk)
            Spacer(Modifier.weight(1f))
            if (log.durationMs > 0) Text(
                "${log.durationMs}ms",
                style = MaterialTheme.typography.labelSmall,
                color = colors.mutedInk,
                fontSize = 10.sp
            )
        }
        if (log.thought.isNotBlank()) {
            Text("念头", style = MaterialTheme.typography.labelSmall, color = colors.accent, fontSize = 10.sp)
            Text(log.thought, style = MaterialTheme.typography.bodySmall, color = colors.ink)
        }
        if (log.reply.isNotBlank()) {
            Text("发言", style = MaterialTheme.typography.labelSmall, color = colors.accent, fontSize = 10.sp)
            Text(log.reply, style = MaterialTheme.typography.bodySmall, color = colors.ink)
        }
        if (tool.isNotBlank()) {
            Text("工具调用", style = MaterialTheme.typography.labelSmall, color = colors.accent, fontSize = 10.sp)
            Text(tool, style = MaterialTheme.typography.bodySmall, color = colors.mutedInk)
        }
    }
}