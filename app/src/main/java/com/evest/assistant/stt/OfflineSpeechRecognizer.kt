package com.evest.assistant.stt

import android.content.Context
import com.evest.assistant.util.Logger
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService

/**
 * Offline Russian speech-to-text using Vosk.
 *
 * Why Vosk offline instead of Android's cloud SpeechRecognizer:
 *  - works with no internet connection (per project requirement),
 *  - no per-request cost or Google account dependency,
 *  - predictable latency, doesn't depend on Google Play Services' online STT.
 *
 * Trade-off (documented honestly): Vosk's small Russian model (~45 MB, model
 * "vosk-model-small-ru-0.22") is noticeably less accurate than Google's cloud
 * recognizer, especially with background noise or uncommon words. It must be
 * unpacked from app/src/main/assets/vosk-model/ on first run (see README for
 * how to obtain and place the model files — they are NOT included in this
 * source zip because of size; the build script downloads them).
 */
class OfflineSpeechRecognizer(private val context: Context) {

    private var model: Model? = null
    private var speechService: SpeechService? = null
    var onPartialResult: ((String) -> Unit)? = null
    var onFinalResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun init(onReady: (() -> Unit)? = null) {
        VoskModelProvider.get(context) { loadedModel ->
            if (loadedModel != null) {
                model = loadedModel
                Logger.i("OfflineSTT", "Модель Vosk готова (общая с wake-word)")
                onReady?.invoke()
            } else {
                onError?.invoke("Офлайн-модель распознавания речи не найдена или повреждена. Смотрите README, как её установить.")
            }
        }
    }

    fun startListening() {
        val m = model
        if (m == null) {
            onError?.invoke("Модель распознавания речи ещё не загружена.")
            return
        }
        try {
            val recognizer = Recognizer(m, 16000.0f)
            speechService = SpeechService(recognizer, 16000.0f)
            speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    hypothesis?.let { extractText(it)?.let { text -> onPartialResult?.invoke(text) } }
                }

                override fun onResult(hypothesis: String?) {
                    hypothesis?.let { extractText(it)?.let { text -> onFinalResult?.invoke(text) } }
                }

                override fun onFinalResult(hypothesis: String?) {
                    hypothesis?.let { extractText(it)?.let { text -> onFinalResult?.invoke(text) } }
                }

                override fun onError(exception: Exception?) {
                    Logger.e("OfflineSTT", "Ошибка распознавания", exception)
                    onError?.invoke("Ошибка распознавания речи.")
                }

                override fun onTimeout() {
                    Logger.w("OfflineSTT", "Таймаут распознавания")
                }
            })
        } catch (t: Throwable) {
            Logger.e("OfflineSTT", "Не удалось запустить прослушивание", t)
            onError?.invoke("Не удалось запустить микрофон для распознавания.")
        }
    }

    fun stopListening() {
        speechService?.stop()
        speechService?.shutdown()
        speechService = null
    }

    private fun extractText(jsonHypothesis: String): String? {
        return try {
            val json = org.json.JSONObject(jsonHypothesis)
            val text = json.optString("text", json.optString("partial", ""))
            text.trim().ifBlank { null }
        } catch (t: Throwable) {
            null
        }
    }

    fun release() {
        stopListening()
        model = null
    }
}
