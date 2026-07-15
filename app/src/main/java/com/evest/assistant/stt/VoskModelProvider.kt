package com.evest.assistant.stt

import android.content.Context
import com.evest.assistant.util.Logger
import org.vosk.Model
import org.vosk.android.StorageService
import java.io.IOException

/**
 * Loads the single Vosk offline speech model once and shares it between
 * the wake-word listener (wakeword/WakeWordDetector.kt) and the command
 * recognizer (stt/OfflineSpeechRecognizer.kt). Both features use the exact
 * same Russian model — loading it twice would double memory usage and
 * startup time for no benefit.
 */
object VoskModelProvider {
    private const val MODEL_ASSET_DIR = "vosk-model"

    @Volatile
    private var model: Model? = null

    @Volatile
    private var loading = false

    private val pendingCallbacks = mutableListOf<(Model?) -> Unit>()

    fun get(context: Context, callback: (Model?) -> Unit) {
        val existing = model
        if (existing != null) {
            callback(existing)
            return
        }

        synchronized(this) {
            if (model != null) {
                callback(model)
                return
            }
            pendingCallbacks.add(callback)
            if (loading) return
            loading = true
        }

        StorageService.unpack(
            context, MODEL_ASSET_DIR, "model",
            { unpackedModel: Model ->
                Logger.i("VoskModelProvider", "Общая модель Vosk загружена")
                synchronized(this) {
                    model = unpackedModel
                    loading = false
                    pendingCallbacks.forEach { it(unpackedModel) }
                    pendingCallbacks.clear()
                }
            },
            { exception: IOException ->
                Logger.e("VoskModelProvider", "Не удалось загрузить модель Vosk", exception)
                synchronized(this) {
                    loading = false
                    pendingCallbacks.forEach { it(null) }
                    pendingCallbacks.clear()
                }
            }
        )
    }

    fun isLoaded(): Boolean = model != null
}
