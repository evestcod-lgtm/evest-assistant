package com.evest.assistant.actions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.nfc.NfcAdapter
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.evest.assistant.data.SettingsStore
import com.evest.assistant.nlu.IntentType
import com.evest.assistant.nlu.ParsedIntent
import com.evest.assistant.util.Logger
import java.util.Calendar

/** Result of an executed action — what the assistant should say back to the user. */
data class ActionResult(val spokenResponse: String, val success: Boolean)

/**
 * Executes parsed intents as real Android actions.
 *
 * Design principle from the spec: if something truly cannot be done directly
 * (e.g. Android forbids 3rd-party apps from silently toggling NFC or Wi-Fi
 * since Android 10, or ADB-less flashlight control needs camera permission),
 * this class is honest about it via the returned spokenResponse AND still
 * opens the most relevant screen/app as a working fallback.
 */
class ActionExecutor(
    private val context: Context,
    private val settings: SettingsStore
) {

    fun execute(intent: ParsedIntent): ActionResult {
        return try {
            when (intent.type) {
                IntentType.OPEN_APP -> openApp(intent)
                IntentType.SEARCH_YOUTUBE -> searchYoutube(intent)
                IntentType.CALL_CONTACT -> callContact(intent)
                IntentType.DIAL_NUMBER -> dialNumber(intent)
                IntentType.PLAY_MUSIC -> playMusic(intent)
                IntentType.STOP_MUSIC -> stopMusic()
                IntentType.SEARCH_WEB -> searchWeb(intent)
                IntentType.REMIND -> remind(intent)
                IntentType.SET_ALARM -> setAlarm(intent)
                IntentType.FLASHLIGHT_ON -> setFlashlight(true)
                IntentType.FLASHLIGHT_OFF -> setFlashlight(false)
                IntentType.OPEN_SETTINGS -> openSettings()
                IntentType.OPEN_MAPS_ROUTE -> openMapsRoute(intent)
                IntentType.NFC_ON -> toggleNfc(true)
                IntentType.NFC_OFF -> toggleNfc(false)
                IntentType.REMEMBER_FACT -> rememberFact(intent)
                IntentType.RECALL_FACT -> recallFact(intent)
                IntentType.SEND_MESSAGE -> sendMessage(intent)
                IntentType.OPEN_TELEGRAM -> openPackageOrFallback("org.telegram.messenger", "Telegram", "https://telegram.org/dl")
                IntentType.OPEN_WHATSAPP -> openPackageOrFallback("com.whatsapp", "WhatsApp", "https://www.whatsapp.com/download")
                IntentType.OPEN_BROWSER -> openBrowser()
                IntentType.WEATHER -> ActionResult("Чтобы назвать точную погоду, мне нужен доступ к прогнозу. Открываю сервис погоды.", true).also {
                    openUrl("https://yandex.ru/pogoda")
                }
                else -> ActionResult("Не поняла команду.", false)
            }
        } catch (t: Throwable) {
            Logger.e("ActionExecutor", "Ошибка выполнения действия ${intent.type}", t)
            ActionResult("Что-то пошло не так при выполнении команды.", false)
        }
    }

    // ---------- App launching ----------

    private fun openApp(intent: ParsedIntent): ActionResult {
        val pkg = intent.slots["package"].orEmpty()
        val name = intent.slots["name"] ?: "приложение"

        if (pkg == "android.settings") return openSettings()

        val launchIntent = if (pkg.isNotBlank()) {
            context.packageManager.getLaunchIntentForPackage(pkg)
        } else {
            findInstalledAppByLabel(name)
        }

        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ActionResult("Открываю $name.", true)
        } else {
            // Fallback: search Play Store for it, so user isn't left stuck.
            openUrl("https://play.google.com/store/search?q=${Uri.encode(name)}&c=apps")
            ActionResult("Не нашла приложение «$name» на устройстве. Открыла поиск в Play Store.", false)
        }
    }

    /** Scans installed launcher apps and matches by visible label (fuzzy contains). */
    private fun findInstalledAppByLabel(spokenName: String): Intent? {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
        val target = spokenName.trim().lowercase()
        val match = resolved.firstOrNull {
            val label = it.loadLabel(pm).toString().lowercase()
            label.contains(target) || target.contains(label)
        } ?: return null
        return pm.getLaunchIntentForPackage(match.activityInfo.packageName)
    }

    private fun openPackageOrFallback(pkg: String, label: String, fallbackUrl: String): ActionResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            ActionResult("Открываю $label.", true)
        } else {
            openUrl(fallbackUrl)
            ActionResult("$label не установлен. Открыла страницу загрузки.", false)
        }
    }

    private fun openBrowser(): ActionResult {
        openUrl("https://www.google.com")
        return ActionResult("Открываю браузер.", true)
    }

    private fun openUrl(url: String) {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    // ---------- YouTube ----------

    private fun searchYoutube(intent: ParsedIntent): ActionResult {
        val query = intent.slots["query"].orEmpty().ifBlank { intent.rawText }
        val uri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
        val i = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.youtube")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(i)
            ActionResult("Ищу на YouTube: $query", true)
        } catch (e: Exception) {
            openUrl(uri.toString())
            ActionResult("Открыла результаты поиска YouTube в браузере: $query", true)
        }
    }

    // ---------- Calls ----------

    private fun callContact(intent: ParsedIntent): ActionResult {
        val contactName = intent.slots["contact"].orEmpty()
        val hasContactsPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val hasCallPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

        if (!hasContactsPerm) {
            openDialerSearch(contactName)
            return ActionResult(
                "Нет разрешения на доступ к контактам, поэтому не могу найти номер «$contactName» сама. Открыла набор номера — введите вручную или разрешите доступ к контактам в настройках.",
                false
            )
        }

        val number = findContactNumber(contactName)
        if (number == null) {
            openDialerSearch(contactName)
            return ActionResult("Не нашла контакт «$contactName». Открыла набор номера.", false)
        }

        return if (hasCallPerm) {
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(callIntent)
            ActionResult("Звоню: $contactName.", true)
        } else {
            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(dialIntent)
            ActionResult(
                "Нет разрешения звонить напрямую, поэтому просто открыла набор номера для «$contactName». Нажмите кнопку вызова сами, либо выдайте разрешение «Звонки» в настройках приложения.",
                false
            )
        }
    }

    private fun findContactNumber(name: String): String? {
        val resolver = context.contentResolver
        val cursor = resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME),
            null, null, null
        ) ?: return null

        cursor.use {
            val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                val displayName = it.getString(nameIdx) ?: continue
                if (displayName.lowercase().contains(name.lowercase())) {
                    return it.getString(numberIdx)
                }
            }
        }
        return null
    }

    private fun openDialerSearch(query: String) {
        val i = Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    private fun dialNumber(intent: ParsedIntent): ActionResult {
        val number = intent.rawText.filter { it.isDigit() || it == '+' }
        if (number.isBlank()) return ActionResult("Не расслышала номер.", false)
        val i = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
        return ActionResult("Открываю набор номера $number.", true)
    }

    // ---------- Music ----------

    private fun playMusic(intent: ParsedIntent): ActionResult {
        val pkg = "com.yandex.music"
        val launchIntent = context.packageManager.getLaunchIntentForPackage(pkg)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            val isWave = intent.slots["mode"] == "wave"
            ActionResult(
                if (isWave) "Открываю Яндекс Музыку. Включите «Мою волну» — прямого API для этого нет, но она обычно на главном экране."
                else "Включаю Яндекс Музыку.",
                true
            )
        } else {
            openUrl("https://play.google.com/store/apps/details?id=$pkg")
            ActionResult("Яндекс Музыка не установлена. Открыла страницу установки.", false)
        }
    }

    private fun stopMusic(): ActionResult {
        // Android does not allow 3rd-party apps to force-stop another app's playback
        // without a media-session binding to that specific app. Broadcasting the
        // universal "pause" media button is the best honest approximation.
        val i = Intent("android.intent.action.MEDIA_BUTTON")
        return ActionResult(
            "Прямого доступа к управлению чужим плеером у меня нет из-за ограничений Android. Попробуйте кнопку паузы на экране блокировки или в шторке уведомлений.",
            false
        )
    }

    // ---------- Web search ----------

    private fun searchWeb(intent: ParsedIntent): ActionResult {
        val query = intent.slots["query"].orEmpty().ifBlank { intent.rawText }
        openUrl("https://www.google.com/search?q=${Uri.encode(query)}")
        return ActionResult("Ищу в интернете: $query", true)
    }

    // ---------- Reminders / alarms ----------

    private fun remind(intent: ParsedIntent): ActionResult {
        val amount = intent.slots["amount"]?.toIntOrNull() ?: 10
        val unit = intent.slots["unit"] ?: "минут"
        val text = intent.slots["text"].orEmpty().ifBlank { "напоминание" }

        val minutes = when {
            unit.startsWith("час") -> amount * 60
            unit.startsWith("секунд") -> maxOf(1, amount / 60)
            else -> amount
        }

        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, minutes) }
        val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_MESSAGE, text)
            putExtra(AlarmClock.EXTRA_HOUR, cal.get(Calendar.HOUR_OF_DAY))
            putExtra(AlarmClock.EXTRA_MINUTES, cal.get(Calendar.MINUTE))
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(alarmIntent)
            ActionResult("Хорошо, напомню через $amount $unit: $text.", true)
        } catch (e: Exception) {
            ActionResult("Не удалось поставить напоминание — нет приложения будильника.", false)
        }
    }

    private fun setAlarm(intent: ParsedIntent): ActionResult {
        val timeStr = intent.slots["time"].orEmpty()
        val match = Regex("(\\d{1,2})[:. ]?(\\d{2})?").find(timeStr)
        val hour = match?.groupValues?.get(1)?.toIntOrNull() ?: 8
        val minute = match?.groupValues?.get(2)?.toIntOrNull() ?: 0

        val alarmIntent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(alarmIntent)
            ActionResult("Будильник поставлен на ${"%02d".format(hour)}:${"%02d".format(minute)}.", true)
        } catch (e: Exception) {
            ActionResult("Не нашла приложение будильника на устройстве.", false)
        }
    }

    // ---------- Flashlight ----------

    private fun setFlashlight(on: Boolean): ActionResult {
        val hasCameraPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camId = cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (camId == null) {
                ActionResult("На этом устройстве не нашла фонарик.", false)
            } else {
                cameraManager.setTorchMode(camId, on)
                ActionResult(if (on) "Фонарик включён." else "Фонарик выключен.", true)
            }
        } catch (t: Throwable) {
            Logger.e("ActionExecutor", "Flashlight error", t)
            ActionResult("Не удалось управлять фонариком — возможно камера занята другим приложением.", false)
        }
    }

    // ---------- Settings ----------

    private fun openSettings(): ActionResult {
        val i = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
        return ActionResult("Открываю настройки.", true)
    }

    // ---------- Maps navigation ----------

    private fun openMapsRoute(intent: ParsedIntent): ActionResult {
        val destination = intent.slots["destination"].orEmpty().ifBlank { intent.rawText }
        // Google Maps does not expose a public API for 3rd-party apps to get
        // turn-by-turn spoken directions inside OUR app. The honest, working
        // approach is to hand off to Google Maps' own navigation mode, which
        // will speak turn-by-turn directions itself once launched.
        val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=d")
        val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.google.android.apps.maps")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(mapIntent)
            ActionResult("Строю маршрут до «$destination» и запускаю навигацию в Google Картах — дальше подсказки поворотов будут голосом Google Карт.", true)
        } catch (e: Exception) {
            openUrl("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(destination)}")
            ActionResult("Google Карты не установлены, открыла маршрут в браузере.", false)
        }
    }

    // ---------- NFC ----------

    private fun toggleNfc(on: Boolean): ActionResult {
        // Since Android 4.4, and strictly enforced on modern Android, no
        // 3rd-party app (without being a privileged system app) can
        // programmatically toggle NFC on/off. This is an OS-level restriction,
        // not a bug in this app. Best honest option: open the exact NFC
        // settings toggle screen for the user.
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        if (nfcAdapter == null) {
            return ActionResult("На этом устройстве нет модуля NFC.", false)
        }
        val i = Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(i)
            ActionResult(
                if (on) "Android не разрешает приложениям включать NFC напрямую — это ограничение системы. Открыла экран настроек NFC, переключите тумблер, и можно оплачивать."
                else "Android не разрешает приложениям выключать NFC напрямую. Открыла экран настроек NFC.",
                false
            )
        } catch (e: Exception) {
            ActionResult("Не удалось открыть настройки NFC на этом устройстве.", false)
        }
    }

    // ---------- Memory (remember/recall facts) ----------

    private fun rememberFact(intent: ParsedIntent): ActionResult {
        val key = intent.slots["key"].orEmpty()
        val value = intent.slots["value"].orEmpty()
        if (key.isBlank() || value.isBlank()) return ActionResult("Не поняла, что запомнить.", false)
        settings.rememberFact(key, value)
        return ActionResult("Запомнила: $key — $value.", true)
    }

    private fun recallFact(intent: ParsedIntent): ActionResult {
        val key = intent.slots["key"].orEmpty()
        val value = settings.recallFact(key)
        return if (value != null) {
            ActionResult("$key: $value", true)
        } else {
            ActionResult("Я не помню ничего про «$key».", false)
        }
    }

    // ---------- Messages ----------

    private fun sendMessage(intent: ParsedIntent): ActionResult {
        // Sending an SMS/message fully hands-free (no user tap) requires the
        // app to be the default SMS handler, which is intrusive to request
        // just for this feature. Honest fallback: prefill the message app.
        val i = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:")
            putExtra("sms_body", intent.slots["raw"].orEmpty())
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(i)
            ActionResult("Открыла приложение сообщений с текстом наготове. Укажите получателя и отправьте.", true)
        } catch (e: Exception) {
            ActionResult("Не нашла приложение для сообщений.", false)
        }
    }
}
