package com.example.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.audio.AudioRecordManager
import com.example.audio.AudioTrackPlayer
import com.example.audio.WakeWordDetector
import com.example.live.LiveSessionManager
import com.example.model.AssistantState
import com.example.tools.ToolExecutionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ZoyaBackgroundService : Service() {

    companion object {
        const val TAG = "ZoyaBackgroundService"
        const val CHANNEL_ID = "zoya_assistant_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.example.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"
        const val ACTION_WAKE_ZOYA = "com.example.action.WAKE_ZOYA"

        fun start(context: Context) {
            val intent = Intent(context, ZoyaBackgroundService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ZoyaBackgroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.stopService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): ZoyaBackgroundService = this@ZoyaBackgroundService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var toolEngine: ToolExecutionEngine
    private lateinit var audioRecordManager: AudioRecordManager
    private lateinit var audioTrackPlayer: AudioTrackPlayer
    private lateinit var wakeWordDetector: WakeWordDetector
    private lateinit var liveSessionManager: LiveSessionManager

    private val _micAmplitude = MutableStateFlow(0f)
    val micAmplitude: StateFlow<Float> = _micAmplitude.asStateFlow()

    private val _speakerAmplitude = MutableStateFlow(0f)
    val speakerAmplitude: StateFlow<Float> = _speakerAmplitude.asStateFlow()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "ZoyaBackgroundService creating")

        createNotificationChannel()

        toolEngine = ToolExecutionEngine(this)
        audioRecordManager = AudioRecordManager()
        audioTrackPlayer = AudioTrackPlayer().apply {
            initialize()
            startPlaybackLoop(serviceScope)
            onAmplitudeUpdated = { amp ->
                _speakerAmplitude.value = amp
            }
        }

        wakeWordDetector = WakeWordDetector(this, serviceScope).apply {
            onWakeWordDetected = {
                Log.i(TAG, "Wake-word 'Zoya' triggered in background service!")
                wakeSession()
            }
        }

        liveSessionManager = LiveSessionManager(
            scope = serviceScope,
            toolEngine = toolEngine,
            audioTrackPlayer = audioTrackPlayer
        )

        // Monitor assistant state to update notification
        serviceScope.launch {
            liveSessionManager.assistantState.collect { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE
        when (action) {
            ACTION_STOP_SERVICE -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_WAKE_ZOYA -> {
                wakeSession()
            }
            else -> {
                startForegroundWithNotification()
                startBackgroundListening()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun getLiveSessionManager(): LiveSessionManager = liveSessionManager

    fun wakeSession() {
        wakeWordDetector.stopListening()
        liveSessionManager.startSession()
    }

    fun sleepSession() {
        liveSessionManager.stopSession()
        wakeWordDetector.startListening()
    }

    private fun startBackgroundListening() {
        audioRecordManager.startRecording(serviceScope) { data, amplitude ->
            _micAmplitude.value = amplitude

            if (liveSessionManager.isConnected.value) {
                // Pipe continuous audio stream to Gemini Live
                liveSessionManager.sendAudioChunk(data, amplitude)
            } else {
                // Monitor acoustic wake-word pattern
                wakeWordDetector.processAudioChunk(amplitude)
            }
        }
        wakeWordDetector.startListening()
    }

    @SuppressLint("InlinedApi")
    private fun startForegroundWithNotification() {
        val notification = buildNotification(liveSessionManager.assistantState.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(state: AssistantState) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(state))
    }

    private fun buildNotification(state: AssistantState): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingLaunch = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val wakeIntent = Intent(this, ZoyaBackgroundService::class.java).apply {
            action = ACTION_WAKE_ZOYA
        }
        val pendingWake = PendingIntent.getService(
            this,
            1,
            wakeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, ZoyaBackgroundService::class.java).apply {
            action = ACTION_STOP_SERVICE
        }
        val pendingStop = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusString = when (state) {
            AssistantState.IDLE -> "Zoya is listening for \"Zoya\" in the background"
            AssistantState.LISTENING -> "Zoya is actively listening to you…"
            AssistantState.THINKING -> "Zoya is thinking…"
            AssistantState.SPEAKING -> "Zoya is speaking…"
            AssistantState.ERROR -> "Zoya encountered an issue"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Zoya Voice Assistant")
            .setContentText(statusString)
            .setContentIntent(pendingLaunch)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "Talk to Zoya", pendingWake)
            .addAction(0, "Stop Service", pendingStop)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Zoya Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Zoya assistant ready for wake-word activation"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "ZoyaBackgroundService destroying")
        serviceScope.cancel()
        audioRecordManager.stopRecording()
        audioTrackPlayer.release()
        wakeWordDetector.stopListening()
        liveSessionManager.stopSession()
    }
}
