package com.example.audio

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class WakeWordDetector(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        const val TAG = "WakeWordDetector"
        val KEYWORDS = listOf("zoya", "zoya assistant", "hey zoya", "hi zoya", "ok zoya", "joya", "soya")
    }

    @Volatile
    var onWakeWordDetected: (() -> Unit)? = null

    @Volatile
    var isListening = false
        private set

    private var speechRecognizer: SpeechRecognizer? = null
    private var restartJob: Job? = null
    private var energyHistory = mutableListOf<Float>()
    private var lastTriggerTime = 0L

    fun startListening() {
        if (isListening) return
        isListening = true

        scope.launch(Dispatchers.Main) {
            setupRecognizer()
            startRecognition()
        }
    }

    fun stopListening() {
        isListening = false
        restartJob?.cancel()
        restartJob = null

        scope.launch(Dispatchers.Main) {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up SpeechRecognizer: ${e.message}")
            }
            speechRecognizer = null
        }
    }

    /**
     * Inspect audio chunk directly from AudioRecord to detect "Zoya" energy envelope
     * (2 syllables: /z-o/ followed by /ya/ in 300-800ms window).
     */
    fun processAudioChunk(amplitude: Float) {
        if (!isListening) return

        synchronized(energyHistory) {
            energyHistory.add(amplitude)
            if (energyHistory.size > 20) {
                energyHistory.removeAt(0)
            }

            // Quick energy pattern check for two rapid peaks with intermediate valley
            if (energyHistory.size >= 12) {
                val p1 = energyHistory.subList(0, 4).maxOrNull() ?: 0f
                val valley = energyHistory.subList(4, 8).minOrNull() ?: 0f
                val p2 = energyHistory.subList(8, 12).maxOrNull() ?: 0f

                val now = System.currentTimeMillis()
                if (p1 > 0.45f && valley < 0.25f && p2 > 0.45f && (now - lastTriggerTime > 3000)) {
                    // Acoustic envelope matched
                    Log.d(TAG, "Wake-word acoustic rhythm pattern matched! Awakening Zoya.")
                    triggerWakeWord()
                }
            }
        }
    }

    private fun setupRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "SpeechRecognizer not available on this device, using acoustic pattern matching")
            return
        }

        try {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rmsdB: Float) {}
                    override fun onBufferReceived(buffer: ByteArray?) {}
                    override fun onEndOfSpeech() {}

                    override fun onError(error: Int) {
                        scheduleRestart()
                    }

                    override fun onResults(results: Bundle?) {
                        handleSpeechResults(results)
                        scheduleRestart()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        handleSpeechResults(partialResults)
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) {}
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating SpeechRecognizer: ${e.message}")
        }
    }

    private fun startRecognition() {
        if (!isListening) return
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toString())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
            }
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start SpeechRecognizer: ${e.message}")
            scheduleRestart()
        }
    }

    private fun handleSpeechResults(results: Bundle?) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        for (text in matches) {
            val lower = text.lowercase(Locale.ROOT)
            for (kw in KEYWORDS) {
                if (lower.contains(kw)) {
                    Log.i(TAG, "Wake-word detected in speech recognition: '$lower'")
                    triggerWakeWord()
                    return
                }
            }
        }
    }

    private fun triggerWakeWord() {
        val now = System.currentTimeMillis()
        if (now - lastTriggerTime < 2500) return
        lastTriggerTime = now

        vibrateHaptic()
        onWakeWordDetected?.invoke()
    }

    private fun scheduleRestart() {
        if (!isListening) return
        restartJob?.cancel()
        restartJob = scope.launch(Dispatchers.Main) {
            delay(500)
            if (isListening && isActive) {
                startRecognition()
            }
        }
    }

    private fun vibrateHaptic() {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                v?.vibrate(120)
            }
        } catch (_: Exception) {}
    }
}
