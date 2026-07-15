package com.evest.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.evest.assistant.data.SettingsStore
import com.evest.assistant.ui.theme.BackgroundDark
import com.evest.assistant.ui.theme.EvestTheme

private enum class Screen { HOME, SETTINGS }

@Composable
fun EvestNavHost(
    settings: SettingsStore,
    onboardingStep: MutableState<Int>,
    onRequestRuntimePermissions: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onStartService: () -> Unit
) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    val step by onboardingStep

    EvestTheme {
        Surface(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
            when {
                step == 1 -> OnboardingScreen(
                    step = 1,
                    onAction = onRequestRuntimePermissions,
                    onSkip = { onboardingStep.value = 2 }
                )
                step == 2 -> OnboardingScreen(
                    step = 2,
                    onAction = onRequestOverlayPermission,
                    onSkip = { onboardingStep.value = 3 }
                )
                step == 3 -> OnboardingScreen(
                    step = 3,
                    onAction = onRequestBatteryExemption,
                    onSkip = { onboardingStep.value = 0; onStartService() }
                )
                screen == Screen.SETTINGS -> SettingsScreen(settings = settings, onBack = { screen = Screen.HOME })
                else -> HomeScreen(settings = settings, onOpenSettings = { screen = Screen.SETTINGS })
            }
        }
    }
}
