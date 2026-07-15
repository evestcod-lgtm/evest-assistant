package com.evest.assistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val CyanAccent = Color(0xFF4FD1FF)
val VioletAccent = Color(0xFF7C6CFF)
val BackgroundDark = Color(0xFF0B0E14)
val SurfaceDark = Color(0xFF151A24)
val TextPrimary = Color(0xFFF2F5FA)
val TextSecondary = Color(0xFF9AA5B8)
val ErrorRed = Color(0xFFFF5C7A)
val SuccessGreen = Color(0xFF3DDC97)

private val EvestDarkScheme = darkColorScheme(
    primary = CyanAccent,
    secondary = VioletAccent,
    background = BackgroundDark,
    surface = SurfaceDark,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    error = ErrorRed
)

@Composable
fun EvestTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EvestDarkScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
