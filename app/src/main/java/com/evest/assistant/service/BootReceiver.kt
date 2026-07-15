package com.evest.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.evest.assistant.util.Logger

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Logger.i("BootReceiver", "Устройство перезагружено, запускаю службу")
            val serviceIntent = Intent(context, AssistantForegroundService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } catch (t: Throwable) {
                // On some OEMs, background start restrictions may block this;
                // user can still open the app manually to resume the service.
                Logger.e("BootReceiver", "Не удалось автозапустить службу после перезагрузки", t)
            }
        }
    }
}
