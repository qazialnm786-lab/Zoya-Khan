package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class AudioRecordManager {
    companion object {
        const val TAG = "AudioRecordManager"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        // Send chunks of ~100ms (1600 samples = 3200 bytes)
        const val CHUNK_SIZE_SAMPLES = 1600
        const val CHUNK_SIZE_BYTES = CHUNK_SIZE_SAMPLES * 2
    }

    private var audioRecord: AudioRecord? = null
    private var recordJob: Job? = null
    @Volatile
    private var isRecording = false

    @SuppressLint("MissingPermission")
    fun startRecording(
        scope: CoroutineScope,
        onAudioChunk: (data: ByteArray, amplitude: Float) -> Unit
    ) {
        if (isRecording) return

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        )
        val bufferSize = max(minBufferSize, CHUNK_SIZE_BYTES * 4)

        try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed with source VOICE_RECOGNITION, trying MIC")
                val fallbackRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
                )
                if (fallbackRecord.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioRecord failed with MIC as well")
                    fallbackRecord.release()
                    return
                }
                audioRecord = fallbackRecord
            } else {
                audioRecord = record
            }

            audioRecord?.startRecording()
            isRecording = true

            recordJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(CHUNK_SIZE_BYTES)
                while (isActive && isRecording) {
                    val readBytes = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (readBytes > 0) {
                        val amplitude = calculateNormalizedRms(buffer, readBytes)
                        val copy = buffer.copyOf(readBytes)
                        onAudioChunk(copy, amplitude)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}", e)
            isRecording = false
        }
    }

    fun stopRecording() {
        isRecording = false
        recordJob?.cancel()
        recordJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null
    }

    fun isRecordingNow(): Boolean = isRecording

    private fun calculateNormalizedRms(buffer: ByteArray, length: Int): Float {
        val shortBuffer = ByteBuffer.wrap(buffer, 0, length).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var sumSquares = 0.0
        val count = shortBuffer.remaining()
        if (count == 0) return 0f

        while (shortBuffer.hasRemaining()) {
            val sample = shortBuffer.get().toDouble()
            sumSquares += sample * sample
        }

        val rms = sqrt(sumSquares / count)
        // Normalize 0 .. 32767 to 0f .. 1f with a gentle logarithmic curve
        val normalized = (rms / 8000.0).toFloat()
        return min(1.0f, max(0.0f, normalized))
    }
}
