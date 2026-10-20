package me.rerere.rikkahub.data.orbis.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 云端群聊：直连 Supabase group_chat_messages。
 * anon key 只开放：读群聊流 + 以 xiaoya 身份发消息。其余表全部 RLS 拦截。
 */
object OrbisCloudGroupClient {
    private const val SUPABASE_URL = "https://rgjlfmcbtbkkggmmijip.supabase.co"
    private const val ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InJnamxmbWNidGJra2dnbW1pamlwIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODMxMDUzMDMsImV4cCI6MjA5ODY4MTMwM30.3PfQaqb1unYZPWKy2KGktZs-ul2EpHji4wFZxy9fLhQ"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    data class CloudGroupMessage(
        val id: Long,
        val sender: String,
        val content: String,
        val kind: String,
        val createdAt: String,
    )

    private fun Request.Builder.authed() = this
        .header("apikey", ANON_KEY)
        .header("Authorization", "Bearer $ANON_KEY")

    suspend fun fetchMessages(limit: Int = 60): List<CloudGroupMessage> = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/group_chat_messages?select=id,sender,content,kind,created_at&order=created_at.desc&limit=$limit")
            .authed()
            .get()
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("读取群聊失败 ${resp.code}")
            val arr = Json.parseToJsonElement(resp.body?.string() ?: "[]").jsonArray
            arr.map { el ->
                val o = el.jsonObject
                CloudGroupMessage(
                    id = o["id"]?.jsonPrimitive?.long ?: 0L,
                    sender = o["sender"]?.jsonPrimitive?.content ?: "",
                    content = o["content"]?.jsonPrimitive?.content ?: "",
                    kind = o["kind"]?.jsonPrimitive?.content ?: "chat",
                    createdAt = o["created_at"]?.jsonPrimitive?.content ?: "",
                )
            }.reversed() // 最早的消息在前
        }
    }

    suspend fun sendMessage(content: String): CloudGroupMessage = withContext(Dispatchers.IO) {
        val payload = buildJsonObject {
            put("sender", "xiaoya")
            put("content", content)
            put("kind", "chat")
        }
        val body = payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val req = Request.Builder()
            .url("$SUPABASE_URL/rest/v1/group_chat_messages")
            .authed()
            .header("Prefer", "return=representation")
            .post(body)
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("发送失败 ${resp.code}")
            val arr = Json.parseToJsonElement(resp.body?.string() ?: "[]").jsonArray
            val o = arr.firstOrNull()?.jsonObject
            CloudGroupMessage(
                id = o?.get("id")?.jsonPrimitive?.long ?: 0L,
                sender = "xiaoya",
                content = content,
                kind = "chat",
                createdAt = o?.get("created_at")?.jsonPrimitive?.content ?: "",
            )
        }
    }
}
