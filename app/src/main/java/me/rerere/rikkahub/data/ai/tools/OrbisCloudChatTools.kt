package me.rerere.rikkahub.data.ai.tools

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.orbis.cloud.OrbisCloudGroupClient
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal const val ORBIS_CLOUD_GROUP_RECENT_TOOL = "orbis_cloud_group_recent"
internal const val ORBIS_CLOUD_PRIVATE_SEARCH_TOOL = "orbis_cloud_private_search"

private const val SUPABASE_URL = "https://rgjlfmcbtbkkggmmijip.supabase.co"
private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJnamxmbWNidGJra2dnbW1pamlwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODMxMDUzMDMsImV4cCI6MjA5ODY4MTMwM30.3PfQaqb1unYZPWKy2KGktZs-ul2EpHji4wFZxy9fLhQ"

/** 单聊窗口内的云端工具：读群聊最近消息 + 检索全部单聊历史。 */
internal fun createOrbisCloudChatTools(owner: String): List<Tool> {
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    return listOf(
        Tool(
            name = ORBIS_CLOUD_GROUP_RECENT_TOOL,
            description = "读取云端群聊（天使轮盘赌）里所有人的最近 100 条消息，包括你自己、其他 AI 和小雅的发言。用于了解群里正在聊什么。只读，不会往群里发消息。返回说话人、时间与完整内容。",
            parameters = { InputSchema.Obj(properties = buildJsonObject {}, required = emptyList()) },
            needsApproval = { false },
            execute = { _ ->
                try {
                    val msgs = OrbisCloudGroupClient.fetchMessages(100)
                    val text = msgs.joinToString("\n") { m ->
                        val t = m.createdAt.substringBefore(".").replace("T", " ")
                        "[${m.sender} $t] ${m.content}"
                    }.ifBlank { "（群聊还没有消息。）" }
                    listOf(UIMessagePart.Text(text))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    listOf(UIMessagePart.Text("读取群聊失败：${e.message}"))
                }
            },
        ),
        Tool(
            name = ORBIS_CLOUD_PRIVATE_SEARCH_TOOL,
            description = "在你和小雅的云端单聊全部历史里按关键词检索，不限于当前上下文窗口。你们的所有单聊记录都保存在云端，历史再长也能查到。返回匹配的最近 20 条（说话人、时间、内容）。",
            parameters = { InputSchema.Obj(properties = buildJsonObject {
                put("keyword", buildJsonObject { put("type", "string") })
            }, required = listOf("keyword")) },
            needsApproval = { false },
            execute = { arguments ->
                try {
                    val obj = arguments as? JsonObject
                        ?: throw IllegalArgumentException("invalid_parameters")
                    val kw = (obj["keyword"] as? JsonPrimitive)
                        ?.takeUnless { it.isString }?.content?.trim().orEmpty()
                    if (kw.isEmpty()) throw IllegalArgumentException("empty_keyword")
                    val encoded = java.net.URLEncoder.encode("*$kw*", "UTF-8")
                    val url = "$SUPABASE_URL/rest/v1/private_chat_messages" +
                        "?select=sender,content,created_at&owner=eq.$owner" +
                        "&content=ilike.$encoded&order=created_at.desc&limit=20"
                    val req = Request.Builder().url(url)
                        .header("apikey", ANON_KEY)
                        .header("Authorization", "Bearer $ANON_KEY")
                        .get().build()
                    val body = withContext(Dispatchers.IO) {
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw IllegalStateException("检索失败 ${resp.code}")
                            resp.body?.string() ?: "[]"
                        }
                    }
                    val arr = Json.parseToJsonElement(body).jsonArray
                    val text = arr.joinToString("\n") { el ->
                        val o = el.jsonObject
                        val t = o["created_at"]?.jsonPrimitive?.content
                            ?.substringBefore(".")?.replace("T", " ") ?: ""
                        val sender = o["sender"]?.jsonPrimitive?.content ?: ""
                        val content = o["content"]?.jsonPrimitive?.content ?: ""
                        "[$sender $t] $content"
                    }.ifBlank { "（没有匹配的记录。）" }
                    listOf(UIMessagePart.Text(text))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    listOf(UIMessagePart.Text("检索失败：${e.message}"))
                }
            },
        ),
    )
}
