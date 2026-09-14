package com.example.model

enum class AssistantState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ERROR
}

data class ZoyaUiState(
    val assistantState: AssistantState = AssistantState.IDLE,
    val micAmplitude: Float = 0f,
    val speakerAmplitude: Float = 0f,
    val audioWaveform: List<Float> = emptyList(),
    val currentZoyaTranscript: String = "",
    val sassyStatusMessage: String = "Hey handsome, say \"Zoya\" or tap me to talk.",
    val isConnected: Boolean = false,
    val isServiceRunning: Boolean = false,
    val isWakeWordListening: Boolean = true,
    val permissionsGranted: Boolean = false,
    val lastExecutedTool: String? = null,
    val errorMessage: String? = null
)
