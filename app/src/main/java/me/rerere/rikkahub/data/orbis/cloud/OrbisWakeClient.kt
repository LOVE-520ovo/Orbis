package me.rerere.rikkahub.data.orbis.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 唤醒观测台数据源：直连 Supabase kli_wake_log（anon 只读，RLS 限最近 14 天）。
 */
object OrbisWakeClient {
    private const val SUPABASE_URL = "https://rgjlfmcbtbkkggmmijip.supabase.co"
    private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJnamxmbWNidGJra2dnbW1pamlwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODMxMDUzMDMsImV4cCI6MjA5ODY4MTMwM30.3PfQaqb1unYZPWKy2KGktZs-ul2EpHji4wFZxy9fLhQ"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    data class WakeLog(
        val id: Long,
        val owner: String,
        val firedAt: String,
        val source: String,
        val decision: String,
        val thought: String,
        val reply: String,
        val toolCalls: String,
        val durationMs: Long,
        val model: String,
    )

    private fun Request.Builder.authed() = this
        .header("apikey", ANON_KEY)
        .header("Authorization", "Bearer $ANON_KEY")

    suspend fun fetchLogs(owner: String, limit: Int = 200): List<WakeLog> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/kli_wake_log?select=id,owner,fired_at,source,decision,thought,reply_text,tool_calls,duration_ms,model&owner=eq.$owner&order=fired_at.desc&limit=$limit")
            .authed()
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("读取唤醒日志失败 ${resp.code}")
            val arr = Json.parseToJsonElement(resp.body?.string() ?: "[]").jsonArray
            arr.map { el ->
                val o = el.jsonObject
                WakeLog(
                    id = o["id"]?.jsonPrimitive?.long ?: 0L,
                    owner = o["owner"]?.jsonPrimitive?.content ?: "",
                    firedAt = o["fired_at"]?.jsonPrimitive?.content ?: "",
                    source = o["source"]?.jsonPrimitive?.content ?: "",
                    decision = o["decision"]?.jsonPrimitive?.content ?: "",
                    thought = o["thought"]?.jsonPrimitive?.content ?: "",
                    reply = o["reply_text"]?.jsonPrimitive?.content ?: "",
                    toolCalls = runCatching { o["tool_calls"]?.toString() }.getOrNull() ?: "",
                    durationMs = runCatching { o["duration_ms"]?.jsonPrimitive?.long }.getOrNull() ?: 0L,
                    model = o["model"]?.jsonPrimitive?.content ?: "",
                )
            }
        }
    }
}
