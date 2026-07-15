package com.evest.assistant.nlu

import com.evest.assistant.llm.GroqClient
import com.evest.assistant.llm.GroqResult
import com.evest.assistant.util.Logger
import org.json.JSONObject

/**
 * When RuleBasedParser can't confidently match a command, we ask Groq to
 * classify the utterance into one of our known intents and extract slots,
 * returning strict JSON. This combines the speed/reliability of rules with
 * the flexibility of an LLM for phrasing the rules didn't anticipate.
 */
class LlmIntentClassifier(private val groqClient: GroqClient) {

    private val systemPrompt = """
        Ты классификатор голосовых команд для Android-ассистента "Евестевень".
        Определи намерение пользователя и верни СТРОГО JSON без пояснений, в формате:
        {"intent": "ОДИН_ИЗ_СПИСКА", "slots": {"ключ": "значение"}, "is_command": true/false}

        Список intent: OPEN_APP, SEARCH_YOUTUBE, CALL_CONTACT, DIAL_NUMBER, PLAY_MUSIC,
        STOP_MUSIC, SEARCH_WEB, REMIND, SET_ALARM, FLASHLIGHT_ON, FLASHLIGHT_OFF,
        OPEN_SETTINGS, OPEN_MAPS_ROUTE, NFC_ON, NFC_OFF, REMEMBER_FACT, RECALL_FACT,
        SEND_MESSAGE, OPEN_TELEGRAM, OPEN_WHATSAPP, OPEN_BROWSER, WEATHER, SMALL_TALK, UNKNOWN.

        Если это не команда, а обычный разговор/вопрос — верни SMALL_TALK и is_command=false.
        Slots заполняй только релевантными полями (например query, contact, destination, time, key, value).
        Ответ должен быть ТОЛЬКО валидным JSON, ничего больше.
    """.trimIndent()

    fun classify(text: String, callback: (ParsedIntent) -> Unit) {
        groqClient.chat(systemPrompt, text) { result ->
            when (result) {
                is GroqResult.Success -> callback(parseJsonSafely(result.text, text))
                is GroqResult.Error -> {
                    Logger.w("LlmIntentClassifier", "Groq недоступен (${result.reason}), считаю SMALL_TALK")
                    callback(ParsedIntent(IntentType.SMALL_TALK, rawText = text, confidence = 0.3f))
                }
            }
        }
    }

    private fun parseJsonSafely(raw: String, originalText: String): ParsedIntent {
        return try {
            val cleaned = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            val json = JSONObject(cleaned)
            val intentName = json.optString("intent", "UNKNOWN")
            val type = try { IntentType.valueOf(intentName) } catch (e: Exception) { IntentType.UNKNOWN }
            val slotsJson = json.optJSONObject("slots")
            val slots = mutableMapOf<String, String>()
            slotsJson?.keys()?.forEach { key -> slots[key] = slotsJson.getString(key) }
            ParsedIntent(type, slots, originalText, confidence = 0.8f)
        } catch (t: Throwable) {
            Logger.e("LlmIntentClassifier", "Не удалось разобрать JSON от Groq: $raw", t)
            ParsedIntent(IntentType.SMALL_TALK, rawText = originalText, confidence = 0.2f)
        }
    }
}
