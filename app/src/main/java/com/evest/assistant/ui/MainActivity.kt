package com.evest.assistant.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import com.evest.assistant.data.SettingsStore
import com.evest.assistant.service.AssistantForegroundService
import com.evest.assistant.util.Logger
import com.evest.assistant.util.PermissionsHelper

class MainActivity : ComponentActivity() {

    private lateinit var settings: SettingsStore
    private val onboardingStep = mutableStateOf(0)

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        advanceOnboarding()
    }

    private val overlaySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { advanceOnboarding() }

    private val batterySettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { advanceOnboarding() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(applicationContext)

        setContent {
            EvestNavHost(
                settings = settings,
                onboardingStep = onboardingStep,
                onRequestRuntimePermissions = { requestRuntimePermissions() },
                onRequestOverlayPermission = { requestOverlayPermission() },
                onRequestBatteryExemption = { requestBatteryExemption() },
                onStartService = { startAssistantService() }
            )
        }

        // Kick off onboarding automatically on first launch.
        if (PermissionsHelper.missingPermissions(this).isNotEmpty()) {
            onboardingStep.value = 1
        } else if (!PermissionsHelper.hasOverlayPermission(this)) {
            onboardingStep.value = 2
        } else {
            onboardingStep.value = 0 // already fully set up
            startAssistantService()
        }
    }

    private fun requestRuntimePermissions() {
        val missing = PermissionsHelper.missingPermissions(this)
        if (missing.isEmpty()) {
            advanceOnboarding()
        } else {
            requestPermissionsLauncher.launch(missing.toTypedArray())
        }
    }

    private fun requestOverlayPermission() {
        if (PermissionsHelper.hasOverlayPermission(this)) {
            advanceOnboarding()
        } else {
            overlaySettingsLauncher.launch(PermissionsHelper.overlaySettingsIntent(this))
        }
    }

    private fun requestBatteryExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && pm.isIgnoringBatteryOptimizations(packageName)) {
            advanceOnboarding()
        } else {
            try {
                batterySettingsLauncher.launch(PermissionsHelper.batteryOptimizationSettingsIntent(this))
            } catch (t: Throwable) {
                Logger.w("MainActivity", "Экран исключений батареи недоступен на этом устройстве")
                advanceOnboarding()
            }
        }
    }

    private fun advanceOnboarding() {
        onboardingStep.value += 1
        if (onboardingStep.value > 3) {
            onboardingStep.value = 0
            startAssistantService()
        }
    }

    private fun startAssistantService() {
        val intent = Intent(this, AssistantForegroundService::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            Logger.i("MainActivity", "Фоновая служба запущена")
        } catch (t: Throwable) {
            Logger.e("MainActivity", "Не удалось запустить фоновую службу", t)
        }
    }
}
