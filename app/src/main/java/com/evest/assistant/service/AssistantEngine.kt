package com.evest.assistant.service

import android.content.Context
import com.evest.assistant.actions.ActionExecutor
import com.evest.assistant.data.SettingsStore
import com.evest.assistant.llm.GroqClient
import com.evest.assistant.llm.GroqResult
import com.evest.assistant.nlu.IntentType
import com.evest.assistant.nlu.LlmIntentClassifier
import com.evest.assistant.nlu.RuleBasedParser
import com.evest.assistant.stt.OfflineSpeechRecognizer
import com.evest.assistant.tts.VoiceSpeaker
import com.evest.assistant.util.Logger
import com.evest.assistant.wakeword.WakeWordDetector

enum class AssistantState { IDLE, LISTENING, THINKING, SPEAKING, ERROR }

/**
 * Central orchestrator: wires together wake-word detection, offline STT,
 * rule/LLM intent parsing, action execution, and TTS into one pipeline.
 * Used by both the foreground service (background operation) and the UI
 * (for the animated eye / manual "tap to talk" mode).
 */
class AssistantEngine(
    private val context: Context,
    private val settings: SettingsStore
) {
    val wakeWordDetector = WakeWordDetector(context)
    val speechRecognizer = OfflineSpeechRecognizer(context)
    val speaker = VoiceSpeaker(context, settings)
    private val actionExecutor = ActionExecutor(context, settings)
    private val groqClient = GroqClient(apiKeyProvider = { settings.getGroqApiKey() })
    private val llmClassifier = LlmIntentClassifier(groqClient)

    var state: AssistantState = AssistantState.IDLE
        private set

    var onStateChanged: ((AssistantState) -> Unit)? = null
    var onTranscript: ((String) -> Unit)? = null
    var onResponse: ((String) -> Unit)? = null

    private val conversationHistory = mutableListOf<Pair<String, String>>()

    fun initialize() {
        speaker.init()
        speechRecognizer.init(
            onReady = { Logger.i("AssistantEngine", "STT готов") }
        )
        speechRecognizer.onError = { msg -> setState(AssistantState.ERROR); onResponse?.invoke(msg) }

        wakeWordDetector.onWakeWordDetected = { onWakeWord() }
        wakeWordDetector.onError = { msg -> Logger.w("AssistantEngine", msg) }
        wakeWordDetector.preload()
    }

    fun startBackgroundListening() {
        wakeWordDetector.start()
        setState(AssistantState.IDLE)
    }

    fun stopBackgroundListening() {
        wakeWordDetector.stop()
        speechRecognizer.stopListening()
    }

    private fun onWakeWord() {
        Logger.i("AssistantEngine", "Активация по wake word")
        wakeWordDetector.stop() // free the mic for command capture
        setState(AssistantState.LISTENING)
        speechRecognizer.startListening()

        speechRecognizer.onFinalResult = { text ->
            speechRecognizer.stopListening()
            if (text.isNotBlank()) {
                onTranscript?.invoke(text)
                handleUserUtterance(text)
            } else {
                setState(AssistantState.IDLE)
                wakeWordDetector.start()
            }
        }
    }

    /** Entry point also used by the UI's manual "tap to talk" button. */
    fun handleUserUtterance(text: String) {
        settings.addHistoryEntry(text)
        setState(AssistantState.THINKING)

        val ruleResult = RuleBasedParser.parse(text)
        if (ruleResult.type != IntentType.UNKNOWN) {
            executeAndRespond(ruleResult.type, ruleResult)
            return
        }

        // Fall back to LLM classification for anything the rules didn't catch.
        llmClassifier.classify(text) { parsed ->
            if (parsed.type == IntentType.SMALL_TALK || parsed.type == IntentType.UNKNOWN) {
                respondConversationally(text)
            } else {
                executeAndRespond(parsed.type, parsed)
            }
        }
    }

    private fun executeAndRespond(type: IntentType, parsed: com.evest.assistant.nlu.ParsedIntent) {
        val result = actionExecutor.execute(parsed)
        setState(AssistantState.SPEAKING)
        onResponse?.invoke(result.spokenResponse)
        speaker.speak(result.spokenResponse)
        finishTurn()
    }

    private fun respondConversationally(text: String) {
        val systemPrompt = """
            Ты голосовой ассистент по имени Евестевень. Отвечай по-русски,
            кратко (1-3 предложения), дружелюбно и естественно, как в живом
            разговоре голосом. Не используй markdown, эмодзи или списки —
            твой ответ будет озвучен вслух.
        """.trimIndent()

        groqClient.chat(systemPrompt, text, conversationHistory) { result ->
            val reply = when (result) {
                is GroqResult.Success -> {
                    conversationHistory.add("user" to text)
                    conversationHistory.add("assistant" to result.text)
                    if (conversationHistory.size > 10) {
                        repeat(2) { conversationHistory.removeAt(0) }
                    }
                    result.text
                }
                is GroqResult.Error -> result.reason
            }
            setState(AssistantState.SPEAKING)
            onResponse?.invoke(reply)
            speaker.speak(reply)
            finishTurn()
        }
    }

    private fun finishTurn() {
        speaker.onSpeakingStateChanged = { speaking ->
            if (!speaking) {
                setState(AssistantState.IDLE)
                wakeWordDetector.start()
            }
        }
    }

    private fun setState(newState: AssistantState) {
        state = newState
        onStateChanged?.invoke(newState)
    }

    fun shutdown() {
        wakeWordDetector.release()
        speechRecognizer.release()
        speaker.shutdown()
    }
}
