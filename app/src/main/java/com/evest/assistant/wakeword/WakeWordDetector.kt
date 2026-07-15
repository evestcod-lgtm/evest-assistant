package com.evest.assistant.wakeword

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import com.evest.assistant.stt.VoskModelProvider
import com.evest.assistant.util.Logger
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import kotlin.concurrent.thread
import kotlin.math.sqrt

/**
 * Always-listening offline wake-word detector built on Vosk.
 *
 * Why this approach: it requires no third-party account, API key, or
 * pre-trained model file for the wake word specifically — it reuses the
 * same offline Vosk speech model already used for command recognition
 * (see stt/OfflineSpeechRecognizer.kt). Vosk continuously transcribes the
 * microphone stream, and every recognized chunk of text is checked for a
 * fuzzy match against the wake phrase "евестевень".
 *
 * Trade-off (documented honestly, as this is a deliberate choice over
 * Porcupine/openWakeWord): running a full speech-to-text model continuously
 * is heavier than a dedicated keyword-spotting model, so two optimizations
 * are applied to keep battery drain reasonable:
 *
 * 1. Voice Activity Detection (VAD) gate: a lightweight RMS-energy check
 *    runs on every audio frame BEFORE it's handed to Vosk. Vosk only
 *    processes frames where actual sound is present, so during silence
 *    (which is most of the time for a background listener) the heavy
 *    recognizer does no work at all — audio capture keeps running (cheap)
 *    but transcription (expensive) is skipped.
 * 2. Android's built-in hardware/software Noise Suppressor and Acoustic
 *    Echo Canceler effects are attached to the AudioRecord session when
 *    available on the device, improving recognition accuracy in noisy
 *    environments without any extra library or model.
 */
class WakeWordDetector(
    private val context: Context
) {
    var onWakeWordDetected: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val sampleRate = 16000
    private val frameSize = 3200 // 200ms at 16kHz, 16-bit mono
    private val vadEnergyThreshold = 350.0

    // Wake phrase variants to tolerate minor Vosk misrecognitions of the
    // invented word "евестевень" (fuzzy contains-match, not exact).
    private val wakePhraseVariants = listOf(
        "евестевень", "евест евень", "евестовень", "евистевень",
        "явестевень", "евестевен", "евестевене"
    )

    private var model: Model? = null
    private var audioRecord: AudioRecord? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var listening = false
    private var modelReady = false

    /** Loads the shared Vosk model once. Cheap to call repeatedly; no-op if already loaded. */
    fun preload(onReady: (() -> Unit)? = null) {
        if (modelReady) {
            onReady?.invoke()
            return
        }
        VoskModelProvider.get(context) { loadedModel ->
            if (loadedModel != null) {
                model = loadedModel
                modelReady = true
                Logger.i("WakeWordDetector", "Модель Vosk для wake-word готова (общая с STT)")
                onReady?.invoke()
            } else {
                onError?.invoke("Офлайн-модель распознавания речи не найдена. Активация по «евестевень» не будет работать, пока модель не установлена — см. README в assets/vosk-model.")
            }
        }
    }

    fun start() {
        if (listening) return
        val m = model
        if (m == null) {
            preload { start() }
            return
        }

        val minBufSize = AudioRecord.getMinBufferSize(
            sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufSize <= 0) {
            onError?.invoke("Микрофон недоступен на этом устройстве.")
            return
        }

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufSize * 4
            )
        } catch (se: SecurityException) {
            onError?.invoke("Нет разрешения на микрофон.")
            return
        }

        if (record.state != AudioRecord.STATE_INITIALIZED) {
            onError?.invoke("Не удалось инициализировать запись с микрофона.")
            record.release()
            return
        }
        audioRecord = record
        attachNoiseSuppression(record.audioSessionId)

        val recognizer = try {
            Recognizer(m, sampleRate.toFloat())
        } catch (t: Throwable) {
            Logger.e("WakeWordDetector", "Не удалось создать распознаватель", t)
            onError?.invoke("Ошибка запуска распознавания wake-word.")
            release()
            return
        }

        listening = true
        record.startRecording()
        Logger.i("WakeWordDetector", "Слушаю «евестевень» офлайн (Vosk + VAD энергосбережение)")

        thread(name = "WakeWordAudioThread") {
            val buffer = ShortArray(frameSize)
            var silentFrameStreak = 0
            while (listening) {
                val read = record.read(buffer, 0, frameSize)
                if (read <= 0) continue

                // --- Battery optimization: skip Vosk entirely on silence ---
                val energy = rmsEnergy(buffer, read)
                if (energy < vadEnergyThreshold) {
                    silentFrameStreak++
                    // Periodically reset the recognizer's internal state during
                    // long silences so it doesn't accumulate stale partial
                    // hypotheses; this is a cheap operation compared to actual
                    // decoding.
                    if (silentFrameStreak == 25) {
                        try { recognizer.reset() } catch (_: Throwable) {}
                    }
                    continue
                }
                silentFrameStreak = 0

                val bytes = shortsToBytes(buffer, read)
                try {
                    val gotFinal = recognizer.acceptWaveForm(bytes, bytes.size)
                    val hypothesis = if (gotFinal) recognizer.result else recognizer.partialResult
                    val text = extractText(hypothesis)
                    if (text != null && matchesWakePhrase(text)) {
                        Logger.i("WakeWordDetector", "Wake word обнаружен в тексте: \"$text\"")
                        try { recognizer.reset() } catch (_: Throwable) {}
                        onWakeWordDetected?.invoke()
                    }
                } catch (t: Throwable) {
                    Logger.e("WakeWordDetector", "Ошибка распознавания в фоновом прослушивании", t)
                }
            }
            try { recognizer.close() } catch (_: Throwable) {}
        }
    }

    private fun attachNoiseSuppression(audioSessionId: Int) {
        try {
            if (NoiseSuppressor.isAvailable()) {
                noiseSuppressor = NoiseSuppressor.create(audioSessionId)?.apply { enabled = true }
                Logger.i("WakeWordDetector", "Подавление шума включено")
            }
        } catch (t: Throwable) {
            Logger.w("WakeWordDetector", "NoiseSuppressor недоступен на этом устройстве")
        }
        try {
            if (AcousticEchoCanceler.isAvailable()) {
                echoCanceler = AcousticEchoCanceler.create(audioSessionId)?.apply { enabled = true }
            }
        } catch (t: Throwable) {
            Logger.w("WakeWordDetector", "AcousticEchoCanceler недоступен на этом устройстве")
        }
    }

    private fun rmsEnergy(buffer: ShortArray, len: Int): Double {
        var sum = 0.0
        for (i in 0 until len) {
            sum += (buffer[i].toDouble()) * (buffer[i].toDouble())
        }
        return sqrt(sum / len)
    }

    private fun shortsToBytes(shorts: ShortArray, len: Int): ByteArray {
        val bytes = ByteArray(len * 2)
        for (i in 0 until len) {
            bytes[i * 2] = (shorts[i].toInt() and 0xFF).toByte()
            bytes[i * 2 + 1] = ((shorts[i].toInt() shr 8) and 0xFF).toByte()
        }
        return bytes
    }

    private fun extractText(jsonHypothesis: String?): String? {
        if (jsonHypothesis == null) return null
        return try {
            val json = JSONObject(jsonHypothesis)
            val text = json.optString("text", json.optString("partial", ""))
            text.trim().lowercase().ifBlank { null }
        } catch (t: Throwable) {
            null
        }
    }

    private fun matchesWakePhrase(text: String): Boolean {
        return wakePhraseVariants.any { variant -> text.contains(variant) }
    }

    fun stop() {
        listening = false
        try {
            audioRecord?.stop()
        } catch (t: Throwable) {
            Logger.e("WakeWordDetector", "Ошибка остановки записи", t)
        }
        noiseSuppressor?.release()
        echoCanceler?.release()
        noiseSuppressor = null
        echoCanceler = null
        audioRecord?.release()
        audioRecord = null
    }

    /** Full teardown, including the shared Vosk model — call only when the engine is shutting down. */
    fun release() {
        stop()
        model = null
        modelReady = false
    }
}
