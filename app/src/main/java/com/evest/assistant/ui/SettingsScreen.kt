package com.evest.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.evest.assistant.data.SettingsStore
import com.evest.assistant.tts.VoiceSpeaker
import com.evest.assistant.ui.theme.CyanAccent
import com.evest.assistant.ui.theme.ErrorRed
import com.evest.assistant.ui.theme.SuccessGreen
import com.evest.assistant.ui.theme.SurfaceDark
import com.evest.assistant.ui.theme.TextSecondary
import com.evest.assistant.util.Logger

@Composable
fun SettingsScreen(settings: SettingsStore, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var groqKey by remember { mutableStateOf(settings.getGroqApiKey()) }
    var wakeWordLabel by remember { mutableStateOf(settings.getWakeWordLabel()) }
    var speechRate by remember { mutableStateOf(settings.getSpeechRate()) }
    var speechPitch by remember { mutableStateOf(settings.getSpeechPitch()) }
    var saved by remember { mutableStateOf(false) }
    var showErrorLog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            SectionTitle("Groq API")
            StatusRow(ok = groqKey.isNotBlank(), okText = "Ключ задан", badText = "Ключ не задан — умные ответы не будут работать")
            OutlinedTextField(
                value = groqKey,
                onValueChange = { groqKey = it },
                label = { Text("Groq API key") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                "Получить бесплатный ключ: console.groq.com/keys. Ключ хранится в зашифрованном виде на устройстве и применяется сразу, без пересборки APK.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )

            SectionTitle("Wake word (офлайн, Vosk)")
            Text(
                "Распознавание «евестевень» работает полностью офлайн: приложение постоянно слушает микрофон через локальную модель Vosk (ту же, что распознаёт команды) и ищет фразу в потоке речи. Ключи и аккаунты не нужны. Во время тишины распознавание не запускается — это экономит батарею.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )

            SectionTitle("Командное слово")
            OutlinedTextField(
                value = wakeWordLabel,
                onValueChange = { wakeWordLabel = it },
                label = { Text("Отображаемое имя (для интерфейса)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Text(
                "Само распознавание слова «евестевень» зависит от офлайн-модели Vosk (assets/vosk-model) и не меняется этим полем — это лишь подпись в интерфейсе.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp)
            )

            SectionTitle("Голос")
            Text("Скорость речи: ${"%.1f".format(speechRate)}", color = TextSecondary)
            Slider(
                value = speechRate,
                onValueChange = { speechRate = it },
                valueRange = 0.5f..2.0f
            )
            Text("Тон голоса: ${"%.1f".format(speechPitch)}", color = TextSecondary)
            Slider(
                value = speechPitch,
                onValueChange = { speechPitch = it },
                valueRange = 0.5f..2.0f
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    settings.setGroqApiKey(groqKey)
                    settings.setWakeWordLabel(wakeWordLabel)
                    settings.setSpeechRate(speechRate)
                    settings.setSpeechPitch(speechPitch)
                    saved = true
                    Logger.i("Settings", "Настройки сохранены")
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
            ) {
                Text("Сохранить", fontWeight = FontWeight.SemiBold)
            }
            if (saved) {
                Text("Сохранено ✓", color = SuccessGreen, modifier = Modifier.padding(top = 8.dp))
            }

            Spacer(Modifier.height(24.dp))

            SectionTitle("Лог ошибок")
            TextButton(onClick = { showErrorLog = !showErrorLog }) {
                Text(if (showErrorLog) "Скрыть лог" else "Показать лог", color = CyanAccent)
            }
            if (showErrorLog) {
                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                    Logger.getHistory().take(30).forEach { entry ->
                        val color = when (entry.level) {
                            "ERROR" -> ErrorRed
                            "WARN" -> Color(0xFFFFC107)
                            else -> TextSecondary
                        }
                        Text(
                            "${entry.time}  ${entry.message}",
                            color = color,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
    )
}

@Composable
private fun StatusRow(ok: Boolean, okText: String, badText: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (ok) SuccessGreen else ErrorRed,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(if (ok) okText else badText, color = if (ok) SuccessGreen else ErrorRed, style = MaterialTheme.typography.bodySmall)
    }
}
