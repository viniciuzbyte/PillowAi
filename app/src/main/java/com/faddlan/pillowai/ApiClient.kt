package com.faddlan.pillowai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {

    private const val SYSTEM =
        "You are Pillow AI, a warm and thoughtful assistant. Reply in the language the user writes in. " +
            "Use Markdown. Always put code, HTML, configs and commands inside fenced code blocks with a language tag."

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    fun send(history: List<Message>): String {
        val messages = JSONArray()
        history.filter { !it.typing }.forEach {
            messages.put(JSONObject().put("role", it.role).put("content", it.text))
        }
        val body = JSONObject()
            .put("model", BuildConfig.MODEL)
            .put("max_tokens", 4096)
            .put("system", SYSTEM)
            .put("messages", messages)

        val request = Request.Builder()
            .url(BuildConfig.API_URL)
            .header("x-api-key", BuildConfig.API_KEY)
            .header("anthropic-version", "2023-06-01")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(raw).optJSONObject("error")?.optString("message")
                }.getOrNull()
                throw IOException(if (detail.isNullOrBlank()) "HTTP ${response.code}" else detail)
            }
            val content = JSONObject(raw).getJSONArray("content")
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") sb.append(block.optString("text"))
            }
            val result = sb.toString()
            if (result.isBlank()) throw IOException("Empty response")
            return result
        }
    }
}
