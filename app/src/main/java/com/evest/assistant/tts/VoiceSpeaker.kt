package com.evest.assistant.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import com.evest.assistant.data.SettingsStore
import com.evest.assistant.util.Logger
import java.util.Locale
import java.util.UUID

/**
 * Wraps Android's built-in TextToSpeech engine.
 *
 * Why Android TTS and not a cloud TTS API:
 *  - works fully offline, zero latency cost, zero extra API key,
 *  - Russian voices are already installed on virtually every Android device
 *    (Google TTS engine ships with ru-RU by default),
 *  - user can pick from any voice already installed on their phone (including
 *    higher-quality "network" voices if Google's TTS engine downloaded them),
 *  - most natural-sounding option that reliably works across OEMs without
 *    bundling a huge on-device neural TTS model in the APK.
 */
class VoiceSpeaker(
    private val context: Context,
    private val settings: SettingsStore
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private val pendingQueue = mutableListOf<String>()
    var onSpeakingStateChanged: ((Boolean) -> Unit)? = null

    fun init(onReady: (() -> Unit)? = null) {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ready = true
                applyVoiceSettings()
                setupListener()
                pendingQueue.forEach { speak(it) }
                pendingQueue.clear()
                onReady?.invoke()
                Logger.i("TTS", "Движок TTS готов")
            } else {
                Logger.e("TTS", "Не удалось инициализировать TTS, статус=$status")
            }
        }
    }

    private fun setupListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                onSpeakingStateChanged?.invoke(true)
            }
            override fun onDone(utteranceId: String?) {
                onSpeakingStateChanged?.invoke(false)
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                onSpeakingStateChanged?.invoke(false)
            }
        })
    }

    private fun applyVoiceSettings() {
        val engine = tts ?: return
        val locale = Locale("ru", "RU")
        val result = engine.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Logger.w("TTS", "Русский голос недоступен на этом устройстве, использую системный по умолчанию")
        }
        engine.setSpeechRate(settings.getSpeechRate())
        engine.setPitch(settings.getSpeechPitch())

        settings.getVoiceName()?.let { voiceName ->
            val voice = engine.voices?.firstOrNull { it.name == voiceName }
            if (voice != null) engine.voice = voice
        }
    }

    /** Returns available Russian (or all, as fallback) voices for the settings screen. */
    fun availableVoices(): List<Voice> {
        val engine = tts ?: return emptyList()
        val voices = engine.voices ?: return emptyList()
        val ru = voices.filter { it.locale.language == "ru" }
        return if (ru.isNotEmpty()) ru.toList() else voices.toList()
    }

    fun speak(text: String) {
        val engine = tts
        if (engine == null || !ready) {
            pendingQueue.add(text)
            return
        }
        applyVoiceSettings()
        val id = UUID.randomUUID().toString()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}
