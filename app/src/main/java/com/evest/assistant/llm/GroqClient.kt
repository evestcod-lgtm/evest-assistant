package com.evest.assistant.llm

import com.evest.assistant.util.Logger
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class GroqResult {
    data class Success(val text: String) : GroqResult()
    data class Error(val reason: String) : GroqResult()
}

/**
 * Minimal Groq Chat Completions client.
 * Uses the OpenAI-compatible /openai/v1/chat/completions endpoint on Groq's API.
 *
 * Designed to never crash the app:
 *  - missing key -> immediate Error result, no network call attempted
 *  - no network -> Error result with a human-readable Russian message
 *  - malformed response -> Error result, logged
 */
class GroqClient(
    private val apiKeyProvider: () -> String,
    private val model: String = "llama-3.3-70b-versatile"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val endpoint = "https://api.groq.com/openai/v1/chat/completions"

    fun chat(
        systemPrompt: String,
        userMessage: String,
        history: List<Pair<String, String>> = emptyList(),
        callback: (GroqResult) -> Unit
    ) {
        val apiKey = apiKeyProvider()
        if (apiKey.isBlank()) {
            callback(GroqResult.Error("API-ключ Groq не задан. Откройте настройки и вставьте ключ."))
            return
        }

        val messages = JSONArray().apply {
            put(JSONObject().put("role", "system").put("content", systemPrompt))
            history.forEach { (role, content) ->
                put(JSONObject().put("role", role).put("content", content))
            }
            put(JSONObject().put("role", "user").put("content", userMessage))
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("temperature", 0.6)
            put("max_tokens", 600)
        }

        val request = Request.Builder()
            .url(endpoint)
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Logger.e("GroqClient", "Сетевая ошибка", e)
                callback(GroqResult.Error("Нет соединения с интернетом или сервис Groq недоступен."))
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                response.use { resp ->
                    val raw = resp.body?.string()
                    if (!resp.isSuccessful || raw == null) {
                        val reason = when (resp.code) {
                            401 -> "Groq отклонил ключ API. Проверьте его в настройках."
                            429 -> "Превышен лимит запросов к Groq. Попробуйте через минуту."
                            in 500..599 -> "Сервис Groq временно недоступен."
                            else -> "Groq вернул ошибку (код ${resp.code})."
                        }
                        Logger.e("GroqClient", "HTTP ${resp.code}: $raw")
                        callback(GroqResult.Error(reason))
                        return
                    }
                    try {
                        val json = JSONObject(raw)
                        val text = json.getJSONArray("choices")
                            .getJSONObject(0)
                            .getJSONObject("message")
                            .getString("content")
                        callback(GroqResult.Success(text.trim()))
                    } catch (t: Throwable) {
                        Logger.e("GroqClient", "Не удалось разобрать ответ", t)
                        callback(GroqResult.Error("Не удалось разобрать ответ от Groq."))
                    }
                }
            }
        })
    }
}
