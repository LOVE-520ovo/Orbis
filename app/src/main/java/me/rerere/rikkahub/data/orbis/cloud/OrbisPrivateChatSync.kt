package me.rerere.rikkahub.data.orbis.cloud

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.AppDatabase
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.koin.core.context.GlobalContext
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * 单聊同步：把 Orbis 本地 Room 库里的单聊消息回补到 Supabase private_chat_messages，
 * 供服务器引擎实例醒来时读取（private_n 窗口）。
 *
 * 水位按 conversationId -> 已同步最大 node_index 存本地 prefs：
 * 首次运行做全量回补，之后只同步新节点。
 */
object OrbisPrivateChatSync {
    private const val TAG = "OrbisPrivateSync"
    private const val SUPABASE_URL = "https://rgjlfmcbtbkkggmmijip.supabase.co"
    private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJnamxmbWNidGJra2dnbW1pamlwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODMxMDUzMDMsImV4cCI6MjA5ODY4MTMwM30.3PfQaqb1unYZPWKy2KGktZs-ul2EpHji4wFZxy9fLhQ"
    private const val PREFS = "orbis_private_sync"
    private const val KEY_WM_PREFIX = "wm_"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 助手名 -> 服务器实例 owner。不认识的名字返回 null（不同步）。 */
    fun ownerOf(assistantName: String): String? {
        val n = assistantName.trim()
        return when {
            n.contains("朔夜") -> "shuoye"
            n.contains("加百列") -> "gabriel"
            n.contains("萨麦尔") || n.contains("薩麥爾") || n.contains("samael", true) -> "samael"
            else -> null
        }
    }

    /**
     * 执行一次同步（全量回补 + 增量），返回本次成功写入的消息条数。
     */
    suspend fun syncOnce(context: Context): Int = withContext(Dispatchers.IO) {
        val koin = GlobalContext.get()
        val db = koin.get<AppDatabase>()
        val settings = koin.get<SettingsStore>().settingsFlow.first { !it.init }
        val ownerByAssistant = settings.assistants
            .associate { it.id.toString() to ownerOf(it.name) }
            .filterValues { it != null }
            .mapValues { it.value!! }
        if (ownerByAssistant.isEmpty()) return@withContext 0

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var synced = 0
        for (conv in db.conversationDao().getAll().first()) {
            val owner = ownerByAssistant[conv.assistantId] ?: continue
            val watermark = prefs.getInt(KEY_WM_PREFIX + conv.id, -1)
            val nodes = db.messageNodeDao().getNodesOfConversation(conv.id)
                .filter { it.nodeIndex > watermark }
            if (nodes.isEmpty()) continue

            var maxIndex = watermark
            for (node in nodes) {
                val arr = runCatching {
                    JsonInstant.parseToJsonElement(node.messages).jsonArray
                }.getOrNull() ?: continue
                for (el in arr) {
                    val o = el.jsonObject
                    val role = o["role"]?.jsonPrimitive?.content ?: continue
                    val parts = o["parts"]?.jsonArray ?: continue
                    val text = parts.mapNotNull { p ->
                        val po = p.jsonObject
                        if (po["type"]?.jsonPrimitive?.content == "text")
                            po["text"]?.jsonPrimitive?.content else null
                    }.joinToString("\n").trim()
                    if (text.isEmpty()) continue
                    val sender = if (role == "user") "xiaoya" else owner
                    val body = buildJsonObject {
                        put("owner", owner)
                        put("peer", "xiaoya")
                        put("sender", sender)
                        put("content", text)
                        put("created_at", Instant.now().toString())
                    }.toString()
                    val req = Request.Builder()
                        .url("$SUPABASE_URL/rest/v1/private_chat_messages")
                        .header("apikey", ANON_KEY)
                        .header("Authorization", "Bearer $ANON_KEY")
                        .header("Prefer", "return=minimal")
                        .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            Log.w(TAG, "sync fail ${resp.code} conv=${conv.id} node=${node.id}")
                            return@use
                        }
                        synced++
                    }
                }
                maxIndex = maxOf(maxIndex, node.nodeIndex)
            }
            prefs.edit().putInt(KEY_WM_PREFIX + conv.id, maxIndex).apply()
        }
        synced
    }
}
