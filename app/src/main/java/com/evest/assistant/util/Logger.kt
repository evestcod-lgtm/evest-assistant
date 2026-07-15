package com.evest.assistant.util

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Lightweight logger: writes to Logcat AND keeps a rolling in-memory history
 * so the Settings screen can show a "log of errors" without needing file I/O
 * permissions or a crash-reporting SDK.
 */
object Logger {
    private const val TAG = "EvestAssistant"
    private const val MAX_ENTRIES = 200

    data class Entry(val time: String, val level: String, val message: String)

    private val history = CopyOnWriteArrayList<Entry>()
    private val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun init(context: Context) {
        i("Logger", "Evest Assistant запущен")
    }

    fun i(tag: String, msg: String) {
        Log.i(TAG, "[$tag] $msg")
        push("INFO", tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(TAG, "[$tag] $msg")
        push("WARN", tag, msg)
    }

    fun e(tag: String, msg: String, throwable: Throwable? = null) {
        Log.e(TAG, "[$tag] $msg", throwable)
        push("ERROR", tag, msg + (throwable?.let { ": ${it.message}" } ?: ""))
    }

    private fun push(level: String, tag: String, msg: String) {
        history.add(0, Entry(fmt.format(Date()), level, "[$tag] $msg"))
        while (history.size > MAX_ENTRIES) {
            history.removeAt(history.size - 1)
        }
    }

    fun getHistory(): List<Entry> = history.toList()

    fun clear() = history.clear()
}
