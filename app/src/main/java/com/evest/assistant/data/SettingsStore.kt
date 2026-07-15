package com.evest.assistant.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.evest.assistant.BuildConfig
import com.evest.assistant.util.Logger
import org.json.JSONObject

/**
 * All sensitive data (Groq API key, and any "remembered facts"
 * the user asks the assistant to keep) is stored in EncryptedSharedPreferences,
 * backed by the Android Keystore. This means:
 *  - the key never leaves the device in plaintext on disk,
 *  - it survives app restarts,
 *  - it can be changed at any time from Settings without rebuilding the APK.
 *
 * Falls back gracefully to plain SharedPreferences if the Keystore is unavailable
 * (some very old / custom ROMs) so the app never crashes because of this.
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "evest_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        Logger.e("SettingsStore", "Encrypted prefs недоступны, fallback на обычные", t)
        context.getSharedPreferences("evest_prefs_fallback", Context.MODE_PRIVATE)
    }

    // ---- Groq API key ----
    fun getGroqApiKey(): String =
        prefs.getString(KEY_GROQ, null)?.takeIf { it.isNotBlank() }
            ?: BuildConfig.GROQ_API_KEY_DEFAULT

    fun setGroqApiKey(key: String) {
        prefs.edit().putString(KEY_GROQ, key.trim()).apply()
    }

    fun hasGroqApiKey(): Boolean = getGroqApiKey().isNotBlank()

    // ---- Wake word (display/label only; actual detection model is fixed at build time) ----
    fun getWakeWordLabel(): String = prefs.getString(KEY_WAKE_LABEL, "евестевень") ?: "евестевень"
    fun setWakeWordLabel(label: String) { prefs.edit().putString(KEY_WAKE_LABEL, label).apply() }

    // ---- Voice / TTS settings ----
    fun getSpeechRate(): Float = prefs.getFloat(KEY_TTS_RATE, 1.0f)
    fun setSpeechRate(rate: Float) { prefs.edit().putFloat(KEY_TTS_RATE, rate).apply() }

    fun getSpeechPitch(): Float = prefs.getFloat(KEY_TTS_PITCH, 1.0f)
    fun setSpeechPitch(pitch: Float) { prefs.edit().putFloat(KEY_TTS_PITCH, pitch).apply() }

    fun getVoiceName(): String? = prefs.getString(KEY_TTS_VOICE, null)
    fun setVoiceName(name: String?) { prefs.edit().putString(KEY_TTS_VOICE, name).apply() }

    // ---- "Remembered keys" — the assistant can be told to remember arbitrary
    // key/value facts, e.g. "запомни: код от домофона 45К" ----
    fun rememberFact(key: String, value: String) {
        val all = getAllFacts().apply { put(key, value) }
        prefs.edit().putString(KEY_FACTS, all.toString()).apply()
    }

    fun recallFact(key: String): String? = getAllFacts().optString(key, null)

    fun getAllFacts(): JSONObject {
        val raw = prefs.getString(KEY_FACTS, null) ?: return JSONObject()
        return try { JSONObject(raw) } catch (t: Throwable) { JSONObject() }
    }

    fun forgetFact(key: String) {
        val all = getAllFacts().apply { remove(key) }
        prefs.edit().putString(KEY_FACTS, all.toString()).apply()
    }

    // ---- Command history (for UI display) ----
    fun addHistoryEntry(entry: String) {
        val list = getHistory().toMutableList()
        list.add(0, entry)
        while (list.size > 50) list.removeAt(list.size - 1)
        prefs.edit().putString(KEY_HISTORY, JSONObject().apply {
            put("items", org.json.JSONArray(list))
        }.toString()).apply()
    }

    fun getHistory(): List<String> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONObject(raw).getJSONArray("items")
            (0 until arr.length()).map { arr.getString(it) }
        } catch (t: Throwable) { emptyList() }
    }

    companion object {
        private const val KEY_GROQ = "groq_api_key"
        private const val KEY_WAKE_LABEL = "wake_word_label"
        private const val KEY_TTS_RATE = "tts_rate"
        private const val KEY_TTS_PITCH = "tts_pitch"
        private const val KEY_TTS_VOICE = "tts_voice"
        private const val KEY_FACTS = "remembered_facts"
        private const val KEY_HISTORY = "command_history"
    }
}
