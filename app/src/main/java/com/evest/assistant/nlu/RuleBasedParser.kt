package com.evest.assistant.nlu

/**
 * Fast, offline, rule-based parser for Russian voice commands.
 *
 * Rationale: most commands ("включи фонарик", "позвони маме", "открой ютуб")
 * are simple and repetitive. Handling them with regex/keyword rules means:
 *   - zero network latency,
 *   - works even if Groq is unreachable,
 *   - doesn't burn API quota on trivial commands.
 *
 * Only when nothing matches here does the app fall back to the LLM classifier
 * (see LlmIntentClassifier) for open-ended understanding.
 */
object RuleBasedParser {

    private val appAliases = mapOf(
        "ютуб" to "com.google.android.youtube",
        "youtube" to "com.google.android.youtube",
        "яндекс музык" to "com.yandex.music",
        "телеграм" to "org.telegram.messenger",
        "telegram" to "org.telegram.messenger",
        "вотсап" to "com.whatsapp",
        "whatsapp" to "com.whatsapp",
        "карты" to "com.google.android.apps.maps",
        "карта" to "com.google.android.apps.maps",
        "браузер" to "com.android.chrome",
        "хром" to "com.android.chrome",
        "настройки" to "android.settings"
    )

    fun parse(text: String): ParsedIntent {
        val t = text.trim().lowercase()

        // --- flashlight ---
        if (contains(t, "фонарик") && contains(t, "выключи", "отключи", "погаси")) {
            return ParsedIntent(IntentType.FLASHLIGHT_OFF, rawText = text)
        }
        if (contains(t, "фонарик") && contains(t, "включи", "зажги")) {
            return ParsedIntent(IntentType.FLASHLIGHT_ON, rawText = text)
        }

        // --- NFC ---
        if (contains(t, "nfc", "нфс", "эн эф си") && contains(t, "выключи", "отключи")) {
            return ParsedIntent(IntentType.NFC_OFF, rawText = text)
        }
        if (contains(t, "nfc", "нфс", "эн эф си") && contains(t, "включи", "оплат")) {
            return ParsedIntent(IntentType.NFC_ON, rawText = text)
        }

        // --- music ---
        if (contains(t, "останови музыку", "выключи музыку", "стоп музыка", "пауза музык")) {
            return ParsedIntent(IntentType.STOP_MUSIC, rawText = text)
        }
        if (contains(t, "моя волна", "мою волну")) {
            return ParsedIntent(IntentType.PLAY_MUSIC, mapOf("mode" to "wave"), text)
        }
        if (contains(t, "включи музыку", "запусти музыку", "включи яндекс музык")) {
            return ParsedIntent(IntentType.PLAY_MUSIC, mapOf("mode" to "default"), text)
        }

        // --- youtube search ---
        extractAfter(t, "найди на ютубе", "найди видео на ютубе про", "найди в ютубе видео про", "найди видео про", "найди на youtube")?.let { query ->
            return ParsedIntent(IntentType.SEARCH_YOUTUBE, mapOf("query" to query), text)
        }

        // --- web search ---
        extractAfter(t, "найди в интернете", "найди в гугле", "погугли", "поищи в интернете")?.let { query ->
            return ParsedIntent(IntentType.SEARCH_WEB, mapOf("query" to query), text)
        }

        // --- calls ---
        extractAfter(t, "позвони маме")?.let {
            return ParsedIntent(IntentType.CALL_CONTACT, mapOf("contact" to "мама"), text)
        }
        extractAfter(t, "позвони папе")?.let {
            return ParsedIntent(IntentType.CALL_CONTACT, mapOf("contact" to "папа"), text)
        }
        extractAfter(t, "позвони", "набери номер", "набери")?.let { name ->
            return ParsedIntent(IntentType.CALL_CONTACT, mapOf("contact" to name), text)
        }

        // --- maps / navigation ---
        extractAfter(t, "проложи маршрут до", "построй маршрут до", "маршрут до", "как добраться до", "веди меня до", "веди меня в")?.let { dest ->
            return ParsedIntent(IntentType.OPEN_MAPS_ROUTE, mapOf("destination" to dest), text)
        }

        // --- alarms / reminders ---
        extractAfter(t, "поставь будильник на")?.let { time ->
            return ParsedIntent(IntentType.SET_ALARM, mapOf("time" to time), text)
        }
        Regex("напомни (?:мне )?через (\\d+)\\s*(минут|час|секунд)\\w*(?: (?:о|про|что) )?(.*)")
            .find(t)?.let { m ->
                return ParsedIntent(
                    IntentType.REMIND,
                    mapOf("amount" to m.groupValues[1], "unit" to m.groupValues[2], "text" to m.groupValues[3].trim()),
                    text
                )
            }

        // --- remember / recall facts ---
        Regex("запомни(?:,)? (?:что )?(.+?)(?: это | - | это: |: )(.+)").find(t)?.let { m ->
            return ParsedIntent(IntentType.REMEMBER_FACT, mapOf("key" to m.groupValues[1].trim(), "value" to m.groupValues[2].trim()), text)
        }
        extractAfter(t, "что ты помнишь про", "напомни что такое", "какой у меня")?.let { key ->
            return ParsedIntent(IntentType.RECALL_FACT, mapOf("key" to key), text)
        }

        // --- weather ---
        if (contains(t, "погода", "какая погода", "погоду")) {
            return ParsedIntent(IntentType.WEATHER, rawText = text)
        }

        // --- messages ---
        extractAfter(t, "отправь сообщение")?.let { rest ->
            return ParsedIntent(IntentType.SEND_MESSAGE, mapOf("raw" to rest), text)
        }

        // --- direct app opens ---
        if (contains(t, "открой телеграм", "открой telegram")) return ParsedIntent(IntentType.OPEN_TELEGRAM, rawText = text)
        if (contains(t, "открой вотсап", "открой whatsapp")) return ParsedIntent(IntentType.OPEN_WHATSAPP, rawText = text)
        if (contains(t, "открой браузер", "открой хром")) return ParsedIntent(IntentType.OPEN_BROWSER, rawText = text)
        if (contains(t, "открой настройки")) return ParsedIntent(IntentType.OPEN_SETTINGS, rawText = text)
        if (contains(t, "открой ютуб") && !contains(t, "найди")) return ParsedIntent(IntentType.OPEN_APP, mapOf("package" to "com.google.android.youtube", "name" to "YouTube"), text)

        extractAfter(t, "открой приложение", "открой")?.let { appName ->
            val pkg = appAliases.entries.firstOrNull { appName.contains(it.key) }?.value
            return ParsedIntent(
                IntentType.OPEN_APP,
                mapOf("name" to appName.trim(), "package" to (pkg ?: "")),
                text
            )
        }

        return ParsedIntent(IntentType.UNKNOWN, rawText = text, confidence = 0f)
    }

    private fun contains(text: String, vararg needles: String) = needles.any { text.contains(it) }

    /** Returns the remainder of the text after the first matching trigger phrase, or null if none matched. */
    private fun extractAfter(text: String, vararg triggers: String): String? {
        for (trigger in triggers.sortedByDescending { it.length }) {
            val idx = text.indexOf(trigger)
            if (idx >= 0) {
                val rest = text.substring(idx + trigger.length).trim()
                return rest.ifBlank { "" }
            }
        }
        return null
    }
}
