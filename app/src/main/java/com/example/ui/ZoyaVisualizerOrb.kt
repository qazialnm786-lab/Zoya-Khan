package com.example.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.model.AssistantState
import com.example.ui.theme.ZoyaNeonCyan
import com.example.ui.theme.ZoyaNeonMagenta
import com.example.ui.theme.ZoyaNeonPink
import com.example.ui.theme.ZoyaNeonPurple
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ZoyaVisualizerOrb(
    state: AssistantState,
    micAmplitude: Float,
    speakerAmplitude: Float,
    waveform: List<Float>,
    onOrbClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_transition")

    // Slow breathing for Idle
    val idleBreathing by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "idle_breathing"
    )

    // Rotation angle for Thinking/Processing
    val thinkingRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "thinking_rotation"
    )

    // Pulse scale for Thinking
    val thinkingPulse by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinking_pulse"
    )

    // Wave phase animation for Listening & Speaking
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase"
    )

    // Smooth amplitude transitions
    val smoothMicAmp = remember { Animatable(0f) }
    LaunchedEffect(micAmplitude) {
        smoothMicAmp.animateTo(micAmplitude, tween(80))
    }

    val smoothSpeakerAmp = remember { Animatable(0f) }
    LaunchedEffect(speakerAmplitude) {
        smoothSpeakerAmp.animateTo(speakerAmplitude, tween(80))
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(280.dp)
            .testTag("zoya_orb_button")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOrbClick
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = size.minDimension * 0.26f

            when (state) {
                AssistantState.IDLE -> {
                    // Slow subtle breathing glow with gentle multi-ring aura
                    val currentRadius = baseRadius * idleBreathing

                    // Outer soft aura
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ZoyaNeonPink.copy(alpha = 0.25f),
                                ZoyaNeonPurple.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = currentRadius * 1.8f
                        ),
                        radius = currentRadius * 1.8f,
                        center = center
                    )

                    // Secondary breathing ring
                    drawCircle(
                        color = ZoyaNeonPurple.copy(alpha = 0.4f),
                        radius = currentRadius * 1.25f,
                        center = center,
                        style = Stroke(width = 2.5f)
                    )

                    // Core glowing orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.9f),
                                ZoyaNeonPink,
                                ZoyaNeonMagenta.copy(alpha = 0.8f)
                            ),
                            center = center,
                            radius = currentRadius
                        ),
                        radius = currentRadius,
                        center = center
                    )
                }

                AssistantState.LISTENING -> {
                    // Active listening waveform responding to mic input frequency & amplitude
                    val amp = smoothMicAmp.value
                    val activeRadius = baseRadius * (1.0f + amp * 0.45f)

                    // Multiple dynamic audio wave rings around orb
                    for (i in 1..3) {
                        val ringRadius = activeRadius * (1f + i * 0.28f + amp * 0.35f)
                        val ringAlpha = (0.7f / i) * (0.6f + amp * 0.4f)
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(ZoyaNeonCyan, ZoyaNeonPink, ZoyaNeonPurple, ZoyaNeonCyan),
                                center = center
                            ),
                            radius = ringRadius,
                            center = center,
                            style = Stroke(width = 3.dp.toPx() + (amp * 4f))
                        )
                    }

                    // Sine-modulated dynamic outer perimeter
                    val pointCount = 64
                    for (p in 0 until pointCount) {
                        val angle = (p.toFloat() / pointCount) * 2 * PI.toFloat()
                        val waveOffset = sin(angle * 5 + wavePhase) * (15f + amp * 35f)
                        val r = activeRadius + 20f + waveOffset
                        val x = center.x + r * cos(angle)
                        val y = center.y + r * sin(angle)
                        val dotRadius = 2.5f + (amp * 4f)

                        drawCircle(
                            color = ZoyaNeonCyan.copy(alpha = 0.85f),
                            radius = dotRadius,
                            center = Offset(x, y)
                        )
                    }

                    // Core responsive orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White,
                                ZoyaNeonCyan,
                                ZoyaNeonPurple
                            ),
                            center = center,
                            radius = activeRadius
                        ),
                        radius = activeRadius,
                        center = center
                    )
                }

                AssistantState.THINKING -> {
                    // Pulsing neon rings with rotating cybernetic particle vortex
                    val pulseRadius = baseRadius * thinkingPulse

                    // Rotating segmented neon ring
                    val segments = 8
                    for (s in 0 until segments) {
                        val startAngle = (s * (360f / segments)) + thinkingRotation
                        drawArc(
                            brush = Brush.sweepGradient(
                                colors = listOf(ZoyaNeonPink, ZoyaNeonCyan, ZoyaNeonMagenta),
                                center = center
                            ),
                            startAngle = startAngle,
                            sweepAngle = 30f,
                            useCenter = false,
                            topLeft = Offset(center.x - pulseRadius * 1.35f, center.y - pulseRadius * 1.35f),
                            size = androidx.compose.ui.geometry.Size(pulseRadius * 2.7f, pulseRadius * 2.7f),
                            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // Counter-rotating inner ring
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                ZoyaNeonMagenta.copy(alpha = 0.5f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = pulseRadius * 1.2f
                        ),
                        radius = pulseRadius * 1.2f,
                        center = center
                    )

                    // Core pulsing orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White,
                                ZoyaNeonPink,
                                ZoyaNeonPurple
                            ),
                            center = center,
                            radius = baseRadius * 0.95f
                        ),
                        radius = baseRadius * 0.95f,
                        center = center
                    )
                }

                AssistantState.SPEAKING -> {
                    // Dynamic audio wave matching Zoya's output stream
                    val speakerAmp = smoothSpeakerAmp.value
                    val speakRadius = baseRadius * (1.05f + speakerAmp * 0.55f)

                    // Multiple glowing harmonics
                    val harmonicWaves = 4
                    for (h in 1..harmonicWaves) {
                        val waveRadius = speakRadius * (1f + h * 0.22f + speakerAmp * 0.4f)
                        val color = if (h % 2 == 0) ZoyaNeonMagenta else ZoyaNeonPink

                        drawCircle(
                            color = color.copy(alpha = (0.8f / h) * (0.5f + speakerAmp * 0.5f)),
                            radius = waveRadius,
                            center = center,
                            style = Stroke(width = 3.dp.toPx() + speakerAmp * 6f)
                        )
                    }

                    // Dynamic wave frequency spokes around the orb
                    val spokes = 36
                    for (i in 0 until spokes) {
                        val angle = (i.toFloat() / spokes) * 2 * PI.toFloat()
                        val waveHeight = (sin(angle * 6 + wavePhase * 2) + 1f) * (18f + speakerAmp * 45f)
                        val start = Offset(
                            center.x + speakRadius * cos(angle),
                            center.y + speakRadius * sin(angle)
                        )
                        val end = Offset(
                            center.x + (speakRadius + waveHeight) * cos(angle),
                            center.y + (speakRadius + waveHeight) * sin(angle)
                        )
                        drawLine(
                            brush = Brush.linearGradient(listOf(ZoyaNeonPink, ZoyaNeonCyan)),
                            start = start,
                            end = end,
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Radiant core orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White,
                                ZoyaNeonPink,
                                ZoyaNeonMagenta
                            ),
                            center = center,
                            radius = speakRadius
                        ),
                        radius = speakRadius,
                        center = center
                    )
                }

                AssistantState.ERROR -> {
                    // Error state subtle pulse
                    drawCircle(
                        color = Color(0xFFFF4D4D).copy(alpha = 0.3f),
                        radius = baseRadius * 1.3f,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFFFF4D4D),
                        radius = baseRadius,
                        center = center
                    )
                }
            }
        }

        // Center Icon inside orb
        val icon = when (state) {
            AssistantState.ERROR -> Icons.Default.MicOff
            else -> Icons.Default.Mic
        }

        val iconTint = when (state) {
            AssistantState.LISTENING -> ZoyaNeonCyan
            AssistantState.SPEAKING -> Color.White
            AssistantState.THINKING -> ZoyaNeonPink
            AssistantState.IDLE -> Color.White.copy(alpha = 0.9f)
            AssistantState.ERROR -> Color.White
        }

        Icon(
            imageVector = icon,
            contentDescription = "Zoya Voice Trigger",
            tint = iconTint,
            modifier = Modifier.size(36.dp)
        )
    }
}
