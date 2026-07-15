package com.evest.assistant.nlu

enum class IntentType {
    OPEN_APP,
    SEARCH_YOUTUBE,
    CALL_CONTACT,
    DIAL_NUMBER,
    PLAY_MUSIC,
    STOP_MUSIC,
    SEARCH_WEB,
    REMIND,
    SET_ALARM,
    FLASHLIGHT_ON,
    FLASHLIGHT_OFF,
    OPEN_SETTINGS,
    OPEN_MAPS_ROUTE,
    NFC_ON,
    NFC_OFF,
    REMEMBER_FACT,
    RECALL_FACT,
    SEND_MESSAGE,
    OPEN_TELEGRAM,
    OPEN_WHATSAPP,
    OPEN_BROWSER,
    WEATHER,
    SMALL_TALK,
    UNKNOWN
}

data class ParsedIntent(
    val type: IntentType,
    val slots: Map<String, String> = emptyMap(),
    val rawText: String,
    val confidence: Float = 1.0f
)
