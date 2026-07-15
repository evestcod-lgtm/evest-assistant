package com.evest.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.evest.assistant.data.SettingsStore
import com.evest.assistant.service.AssistantEngine
import com.evest.assistant.service.AssistantState
import com.evest.assistant.ui.theme.CyanAccent
import com.evest.assistant.ui.theme.SurfaceDark
import com.evest.assistant.ui.theme.TextSecondary

@Composable
fun HomeScreen(
    settings: SettingsStore,
    onOpenSettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val engine = remember { AssistantEngine(context, settings) }
    var state by remember { mutableStateOf(AssistantState.IDLE) }
    var transcript by remember { mutableStateOf("") }
    var response by remember { mutableStateOf("Скажите «${settings.getWakeWordLabel()}», чтобы начать") }
    var history by remember { mutableStateOf(settings.getHistory()) }

    DisposableEffect(Unit) {
        engine.initialize()
        engine.onStateChanged = { state = it }
        engine.onTranscript = { transcript = it }
        engine.onResponse = { response = it; history = settings.getHistory() }
        onDispose { engine.shutdown() }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Evest Assistant", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
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
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            AnimatedEye(state = state, sizeDp = 220)

            Spacer(Modifier.height(20.dp))

            Text(
                text = stateLabel(state),
                color = CyanAccent,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(8.dp))

            if (transcript.isNotBlank()) {
                Text("«$transcript»", color = TextSecondary, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(6.dp))
            }

            Text(
                response,
                color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))

            // Manual "tap to talk" button — works even if wake-word model isn't set up yet
            FloatingActionButton(
                onClick = {
                    engine.handleUserUtterance(transcript.ifBlank { "" })
                },
                containerColor = CyanAccent,
                shape = CircleShape,
                modifier = Modifier.size(72.dp)
            ) {
                Icon(Icons.Filled.Mic, contentDescription = "Говорить", tint = Color.Black, modifier = Modifier.size(32.dp))
            }

            Spacer(Modifier.height(28.dp))

            Text("История команд", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))

            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(history) { entry ->
                    Surface(
                        color = SurfaceDark,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            entry,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = androidx.compose.ui.graphics.Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun stateLabel(state: AssistantState): String = when (state) {
    AssistantState.IDLE -> "Жду командное слово"
    AssistantState.LISTENING -> "Слушаю..."
    AssistantState.THINKING -> "Думаю..."
    AssistantState.SPEAKING -> "Говорю..."
    AssistantState.ERROR -> "Ошибка"
}
