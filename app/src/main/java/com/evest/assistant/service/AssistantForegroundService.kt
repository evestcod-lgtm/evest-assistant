package com.evest.assistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.evest.assistant.EvestApp
import com.evest.assistant.R
import com.evest.assistant.data.SettingsStore
import com.evest.assistant.ui.MainActivity
import com.evest.assistant.util.Logger

/**
 * Foreground service that keeps the wake-word listener (Vosk-based, always-on) alive
 * even when the app is backgrounded or the screen is off/locked.
 *
 * On Android, any microphone-using background work must run inside a
 * foreground service with a visible notification (Android 8+), and since
 * Android 14 the service must additionally declare a service type
 * ("microphone" — already set in AndroidManifest.xml). A partial WakeLock
 * is held so the CPU doesn't fully suspend and cut off audio processing
 * while the screen is off.
 */
class AssistantForegroundService : Service() {

    companion object {
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.evest.assistant.action.STOP"
    }

    private lateinit var engine: AssistantEngine
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        val settings = SettingsStore(applicationContext)
        engine = AssistantEngine(applicationContext, settings)
        engine.initialize()
        acquireWakeLock()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIF_ID, buildNotification())
        engine.startBackgroundListening()
        Logger.i("ForegroundService", "Служба запущена, слушаю wake word в фоне")
        return START_STICKY
    }

    private fun buildNotification(): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, EvestApp.CHANNEL_ID_SERVICE)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EvestAssistant::WakeWordLock")
            wakeLock?.setReferenceCounted(false)
            wakeLock?.acquire(12 * 60 * 60 * 1000L /* 12h safety cap */)
        } catch (t: Throwable) {
            Logger.e("ForegroundService", "Не удалось получить WakeLock", t)
        }
    }

    override fun onDestroy() {
        engine.shutdown()
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
