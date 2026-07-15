package com.evest.assistant.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.evest.assistant.service.AssistantState
import com.evest.assistant.ui.theme.CyanAccent
import com.evest.assistant.ui.theme.VioletAccent
import kotlin.math.sin

/**
 * The animated "eye" — Evest Assistant's visual identity, similar in spirit
 * to Siri's orb. Its motion communicates state without needing text:
 *  - IDLE: slow, gentle pulsing ring, eye mostly closed/dim
 *  - LISTENING: eye fully open, bright, quick ripples (actively hearing you)
 *  - THINKING: swirling rotation (processing)
 *  - SPEAKING: rhythmic pulse synced to a fake waveform (talking)
 *  - ERROR: dim red tint
 */
@Composable
fun AnimatedEye(state: AssistantState, modifier: Modifier = Modifier, sizeDp: Int = 220) {
    val infinite = rememberInfiniteTransition(label = "eye")

    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.LISTENING -> 700
                    AssistantState.THINKING -> 900
                    AssistantState.SPEAKING -> 450
                    else -> 2200
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val (baseColor, glowColor) = when (state) {
        AssistantState.LISTENING -> CyanAccent to CyanAccent
        AssistantState.THINKING -> VioletAccent to CyanAccent
        AssistantState.SPEAKING -> CyanAccent to VioletAccent
        AssistantState.ERROR -> Color(0xFFFF5C7A) to Color(0xFFFF5C7A)
        AssistantState.IDLE -> Color(0xFF3A4356) to CyanAccent
    }

    Canvas(modifier = modifier.size(sizeDp.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxRadius = size.minDimension / 2

        // Outer glow ring — breathing effect
        val ringRadius = maxRadius * (0.72f + 0.12f * pulse)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor.copy(alpha = 0.35f * (0.5f + 0.5f * pulse)), Color.Transparent),
                center = center,
                radius = ringRadius * 1.4f
            ),
            radius = ringRadius * 1.4f,
            center = center
        )

        // Middle iris ring
        drawCircle(
            color = baseColor.copy(alpha = 0.85f),
            radius = maxRadius * 0.55f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = maxRadius * 0.06f)
        )

        if (state == AssistantState.THINKING) {
            // Swirling arc to communicate "processing"
            rotate(degrees = rotation, pivot = center) {
                drawArc(
                    color = baseColor,
                    startAngle = 0f,
                    sweepAngle = 120f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = maxRadius * 0.08f),
                    topLeft = Offset(center.x - maxRadius * 0.55f, center.y - maxRadius * 0.55f),
                    size = androidx.compose.ui.geometry.Size(maxRadius * 1.1f, maxRadius * 1.1f)
                )
            }
        }

        // Pupil — "eyelid" openness reflects state: closed-ish at idle, wide open when listening
        val opennessFactor = when (state) {
            AssistantState.LISTENING -> 1f
            AssistantState.SPEAKING -> 0.75f + 0.2f * sin(pulse * Math.PI).toFloat()
            AssistantState.THINKING -> 0.6f
            AssistantState.ERROR -> 0.4f
            AssistantState.IDLE -> 0.35f + 0.1f * pulse
        }
        val pupilRadius = maxRadius * 0.34f * opennessFactor
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowColor, baseColor.copy(alpha = 0.9f)),
                center = center,
                radius = pupilRadius
            ),
            radius = pupilRadius,
            center = center
        )

        // Small highlight for a "glassy" look
        drawCircle(
            color = Color.White.copy(alpha = 0.5f),
            radius = pupilRadius * 0.22f,
            center = Offset(center.x - pupilRadius * 0.35f, center.y - pupilRadius * 0.35f)
        )
    }
}
