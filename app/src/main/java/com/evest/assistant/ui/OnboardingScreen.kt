package com.evest.assistant.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evest.assistant.ui.theme.CyanAccent
import com.evest.assistant.ui.theme.TextSecondary

data class OnboardingStepInfo(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val buttonLabel: String
)

private val steps = listOf(
    OnboardingStepInfo(
        Icons.Filled.Mic,
        "Микрофон и уведомления",
        "Евестевеню нужен доступ к микрофону, чтобы слышать командное слово «евестевень» и ваши команды. Уведомление показывает, что ассистент активен в фоне — это требование Android, его нельзя отключить.",
        "Разрешить"
    ),
    OnboardingStepInfo(
        Icons.Filled.Layers,
        "Показ поверх экрана",
        "Чтобы анимированный глаз появлялся поверх других приложений и экрана блокировки при активации, нужно разрешение «Отображение поверх других приложений». Откроется системный экран — включите тумблер для Evest Assistant.",
        "Открыть настройки"
    ),
    OnboardingStepInfo(
        Icons.Filled.BatteryChargingFull,
        "Работа без ограничений батареи",
        "Чтобы ассистент не засыпал в фоне и слышал «евестевень» даже при выключенном экране, добавьте его в исключения оптимизации батареи. Без этого шага Android может останавливать прослушивание через несколько минут.",
        "Открыть настройки"
    )
)

@Composable
fun OnboardingScreen(step: Int, onAction: () -> Unit, onSkip: () -> Unit) {
    val info = steps.getOrNull(step - 1) ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Шаг $step из ${steps.size}",
            color = TextSecondary,
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(Modifier.height(24.dp))

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = CyanAccent.copy(alpha = 0.12f),
            modifier = Modifier.size(96.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(info.icon, contentDescription = null, tint = CyanAccent, modifier = Modifier.size(48.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            info.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            info.description,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(Modifier.height(36.dp))
        Button(
            onClick = onAction,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CyanAccent)
        ) {
            Text(info.buttonLabel, fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onSkip) {
            Text("Пропустить этот шаг", color = TextSecondary)
        }
    }
}
