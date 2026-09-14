package com.example.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.live.LiveSessionManager
import com.example.model.AssistantState
import com.example.model.ZoyaUiState
import com.example.service.ZoyaBackgroundService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ZoyaViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(ZoyaUiState())
    val uiState: StateFlow<ZoyaUiState> = _uiState.asStateFlow()

    private var service: ZoyaBackgroundService? = null
    private var isBound = false
    private var serviceStateCollectJob: Job? = null
    private val waveformHistory = ArrayDeque<Float>(32)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? ZoyaBackgroundService.LocalBinder
            service = localBinder?.getService()
            isBound = true
            _uiState.update { it.copy(isServiceRunning = true) }
            observeService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            isBound = false
            _uiState.update { it.copy(isServiceRunning = false) }
            serviceStateCollectJob?.cancel()
        }
    }

    init {
        // Pre-fill waveform history
        repeat(32) { waveformHistory.add(0.05f) }
        _uiState.update { it.copy(audioWaveform = waveformHistory.toList()) }
    }

    fun bindService(context: Context) {
        if (!isBound) {
            val intent = Intent(context, ZoyaBackgroundService::class.java)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    fun unbindService(context: Context) {
        if (isBound) {
            context.unbindService(connection)
            isBound = false
            service = null
        }
    }

    fun setPermissionsGranted(granted: Boolean) {
        _uiState.update { it.copy(permissionsGranted = granted) }
        if (granted) {
            val context = getApplication<Application>()
            ZoyaBackgroundService.start(context)
            bindService(context)
        }
    }

    fun toggleAssistant() {
        val s = service ?: return
        val liveManager = s.getLiveSessionManager()

        if (liveManager.isConnected.value) {
            s.sleepSession()
        } else {
            s.wakeSession()
        }
    }

    fun toggleBackgroundService(context: Context) {
        if (_uiState.value.isServiceRunning) {
            unbindService(context)
            ZoyaBackgroundService.stop(context)
            _uiState.update {
                it.copy(
                    isServiceRunning = false,
                    assistantState = AssistantState.IDLE,
                    sassyStatusMessage = "Zoya service stopped. Turn me on whenever you miss me!"
                )
            }
        } else {
            ZoyaBackgroundService.start(context)
            bindService(context)
            _uiState.update {
                it.copy(
                    isServiceRunning = true,
                    sassyStatusMessage = "Zoya background service running! Say \"Zoya\" anytime."
                )
            }
        }
    }

    private fun observeService() {
        val s = service ?: return
        val liveManager = s.getLiveSessionManager()

        serviceStateCollectJob?.cancel()
        serviceStateCollectJob = viewModelScope.launch {
            launch {
                liveManager.assistantState.collect { state ->
                    _uiState.update { current ->
                        val msg = when (state) {
                            AssistantState.IDLE -> "Zoya is listening for \"Zoya\" in the background"
                            AssistantState.LISTENING -> "I'm listening, speak to me…"
                            AssistantState.THINKING -> "Zoya is working her magic…"
                            AssistantState.SPEAKING -> "Zoya speaking"
                            AssistantState.ERROR -> "Oops! Something went sideways, babe."
                        }
                        current.copy(assistantState = state, sassyStatusMessage = msg)
                    }
                }
            }

            launch {
                liveManager.isConnected.collect { connected ->
                    _uiState.update { it.copy(isConnected = connected) }
                }
            }

            launch {
                liveManager.statusText.collect { status ->
                    if (status.isNotBlank()) {
                        _uiState.update { it.copy(sassyStatusMessage = status) }
                    }
                }
            }

            launch {
                liveManager.lastTranscript.collect { text ->
                    _uiState.update { it.copy(currentZoyaTranscript = text) }
                }
            }

            launch {
                liveManager.lastExecutedTool.collect { tool ->
                    _uiState.update { it.copy(lastExecutedTool = tool) }
                }
            }

            launch {
                s.micAmplitude.collect { amp ->
                    _uiState.update { it.copy(micAmplitude = amp) }
                    pushWaveform(amp)
                }
            }

            launch {
                s.speakerAmplitude.collect { amp ->
                    _uiState.update { it.copy(speakerAmplitude = amp) }
                    if (_uiState.value.assistantState == AssistantState.SPEAKING) {
                        pushWaveform(amp)
                    }
                }
            }
        }
    }

    private fun pushWaveform(amplitude: Float) {
        synchronized(waveformHistory) {
            if (waveformHistory.size >= 32) {
                waveformHistory.removeFirst()
            }
            waveformHistory.addLast(amplitude)
            _uiState.update { it.copy(audioWaveform = waveformHistory.toList()) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        serviceStateCollectJob?.cancel()
    }
}
