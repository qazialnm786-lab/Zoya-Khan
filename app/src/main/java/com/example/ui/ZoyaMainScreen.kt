package com.example.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AssistantState
import com.example.ui.theme.ZoyaNeonCyan
import com.example.ui.theme.ZoyaNeonGreen
import com.example.ui.theme.ZoyaNeonMagenta
import com.example.ui.theme.ZoyaNeonPink
import com.example.ui.theme.ZoyaNeonPurple
import com.example.ui.theme.ZoyaObsidian
import com.example.ui.theme.ZoyaSurfaceDark
import com.example.ui.theme.ZoyaSurfaceElevated
import com.example.ui.theme.ZoyaTextDim
import com.example.ui.theme.ZoyaTextPrimary
import com.example.ui.theme.ZoyaTextSecondary
import com.example.viewmodel.ZoyaViewModel

@Composable
fun ZoyaMainScreen(
    viewModel: ZoyaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ZoyaObsidian,
                        Color(0xFF0C0A1C),
                        ZoyaObsidian
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP BAR: Brand, Background Service Switch, Status
            TopHeaderBar(
                isServiceRunning = uiState.isServiceRunning,
                onToggleService = { viewModel.toggleBackgroundService(context) }
            )

            // CENTER: State Badge, Animated Voice Orb, Sassy Transcript
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Current State Badge
                StateBadge(state = uiState.assistantState)

                Spacer(modifier = Modifier.height(28.dp))

                // Central Animated Orb
                ZoyaVisualizerOrb(
                    state = uiState.assistantState,
                    micAmplitude = uiState.micAmplitude,
                    speakerAmplitude = uiState.speakerAmplitude,
                    waveform = uiState.audioWaveform,
                    onOrbClick = { viewModel.toggleAssistant() }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Real-time Waveform Strip
                WaveformVisualizer(
                    waveform = uiState.audioWaveform,
                    state = uiState.assistantState
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Sassy Status / Spoken Output Card
                SassySpeechCard(
                    statusMessage = uiState.sassyStatusMessage,
                    transcript = uiState.currentZoyaTranscript,
                    lastTool = uiState.lastExecutedTool
                )
            }

            // BOTTOM BAR: Voice Command Hints & Tap instruction
            BottomHintSection(state = uiState.assistantState)
        }
    }
}

@Composable
private fun TopHeaderBar(
    isServiceRunning: Boolean,
    onToggleService: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Logo & Tagline
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(ZoyaNeonGreen, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = "ZOYA LIVE",
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    letterSpacing = 1.5.sp,
                    color = ZoyaTextPrimary
                )
                Text(
                    text = "Zero-Touch Voice AI",
                    fontSize = 11.sp,
                    color = ZoyaNeonCyan
                )
            }
        }

        // Background Service Switch Card
        Surface(
            color = ZoyaSurfaceElevated.copy(alpha = 0.8f),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x22FFFFFF))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Hearing,
                    contentDescription = "Wake-word listener",
                    tint = if (isServiceRunning) ZoyaNeonGreen else ZoyaTextDim,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isServiceRunning) "Wake: ON" else "Wake: OFF",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isServiceRunning) ZoyaNeonGreen else ZoyaTextDim
                )
                Spacer(modifier = Modifier.width(6.dp))
                Switch(
                    checked = isServiceRunning,
                    onCheckedChange = { onToggleService() },
                    modifier = Modifier.testTag("background_service_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = ZoyaNeonGreen,
                        checkedTrackColor = ZoyaNeonGreen.copy(alpha = 0.35f),
                        uncheckedThumbColor = ZoyaTextDim,
                        uncheckedTrackColor = ZoyaSurfaceDark
                    )
                )
            }
        }
    }
}

@Composable
private fun StateBadge(state: AssistantState) {
    val (badgeText, badgeColor, icon) = when (state) {
        AssistantState.IDLE -> Triple("READY • SAY \"ZOYA\"", ZoyaNeonPink, Icons.Default.Mic)
        AssistantState.LISTENING -> Triple("LISTENING…", ZoyaNeonCyan, Icons.Default.Hearing)
        AssistantState.THINKING -> Triple("PROCESSING…", ZoyaNeonPurple, Icons.Default.AutoAwesome)
        AssistantState.SPEAKING -> Triple("ZOYA IS SPEAKING", ZoyaNeonPink, Icons.Default.RecordVoiceOver)
        AssistantState.ERROR -> Triple("OFFLINE", Color.Red, Icons.Default.Mic)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(30.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .border(1.dp, badgeColor.copy(alpha = 0.6f), RoundedCornerShape(30.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("zoya_state_badge")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = badgeText,
                tint = badgeColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = badgeText,
                color = badgeColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun WaveformVisualizer(
    waveform: List<Float>,
    state: AssistantState
) {
    val barColor = when (state) {
        AssistantState.LISTENING -> ZoyaNeonCyan
        AssistantState.SPEAKING -> ZoyaNeonPink
        AssistantState.THINKING -> ZoyaNeonPurple
        else -> ZoyaTextDim.copy(alpha = 0.35f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth(0.75f)
            .height(36.dp)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        waveform.takeLast(24).forEach { amp ->
            val heightDp = (amp * 32.dp.value).coerceIn(4f, 34f)
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(heightDp.dp)
                    .background(
                        color = barColor,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
private fun SassySpeechCard(
    statusMessage: String,
    transcript: String,
    lastTool: String?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("sassy_status_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = ZoyaSurfaceElevated.copy(alpha = 0.65f)
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(ZoyaNeonPink.copy(alpha = 0.3f), ZoyaNeonCyan.copy(alpha = 0.3f))
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (transcript.isNotBlank()) "\"$transcript\"" else statusMessage,
                fontSize = 15.sp,
                color = ZoyaTextPrimary,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium
            )

            if (lastTool != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(ZoyaSurfaceDark, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Tool Executed",
                        tint = ZoyaNeonCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tool: $lastTool",
                        fontSize = 11.sp,
                        color = ZoyaNeonCyan,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomHintSection(state: AssistantState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "TRY ASKING ZOYA:",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = ZoyaTextDim,
            letterSpacing = 1.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            VoicePill(label = "“Open YouTube”")
            VoicePill(label = "“Call Alex”")
            VoicePill(label = "“WhatsApp Mom”")
            VoicePill(label = "“Send Email”")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Tap orb anytime to toggle voice • Completely zero-touch",
            fontSize = 11.sp,
            color = ZoyaTextDim
        )
    }
}

@Composable
private fun VoicePill(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(ZoyaSurfaceDark)
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = ZoyaNeonCyan,
            fontWeight = FontWeight.Medium
        )
    }
}
