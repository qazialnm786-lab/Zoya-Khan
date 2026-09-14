package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class AudioTrackPlayer {
    companion object {
        const val TAG = "AudioTrackPlayer"
        const val DEFAULT_SAMPLE_RATE = 24000
    }

    private var audioTrack: AudioTrack? = null
    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()
    private val isPlaying = AtomicBoolean(false)
    private var playbackJob: Job? = null
    private var sampleRate = DEFAULT_SAMPLE_RATE

    @Volatile
    var onAmplitudeUpdated: ((Float) -> Unit)? = null
    @Volatile
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null

    fun initialize(sampleRateHz: Int = DEFAULT_SAMPLE_RATE) {
        sampleRate = sampleRateHz
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = max(minBufferSize, 8192)

        try {
            audioTrack?.release()
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AudioTrack: ${e.message}", e)
        }
    }

    fun startPlaybackLoop(scope: CoroutineScope) {
        if (playbackJob?.isActive == true) return

        playbackJob = scope.launch(Dispatchers.IO) {
            val shortBufferSize = 1024
            while (isActive) {
                val chunk = audioQueue.poll()
                if (chunk != null) {
                    if (!isPlaying.getAndSet(true)) {
                        onPlaybackStateChanged?.invoke(true)
                    }

                    // Calculate amplitude for speaking visualizer
                    val amp = calculateAmplitude(chunk)
                    onAmplitudeUpdated?.invoke(amp)

                    try {
                        audioTrack?.write(chunk, 0, chunk.size)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error writing to AudioTrack: ${e.message}")
                    }
                } else {
                    if (isPlaying.getAndSet(false)) {
                        onPlaybackStateChanged?.invoke(false)
                        onAmplitudeUpdated?.invoke(0f)
                    }
                    // Yield briefly when queue is empty
                    kotlinx.coroutines.delay(10)
                }
            }
        }
    }

    fun enqueueAudio(data: ByteArray) {
        if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            initialize(sampleRate)
        }
        audioQueue.offer(data)
    }

    fun interrupt() {
        audioQueue.clear()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            Log.w(TAG, "Error during interrupt: ${e.message}")
        }
        if (isPlaying.getAndSet(false)) {
            onPlaybackStateChanged?.invoke(false)
            onAmplitudeUpdated?.invoke(0f)
        }
    }

    fun release() {
        interrupt()
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack: ${e.message}")
        }
        audioTrack = null
    }

    private fun calculateAmplitude(buffer: ByteArray): Float {
        if (buffer.isEmpty()) return 0f
        val shortBuffer = ByteBuffer.wrap(buffer).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var sumSquares = 0.0
        val count = shortBuffer.remaining()
        if (count == 0) return 0f

        while (shortBuffer.hasRemaining()) {
            val sample = shortBuffer.get().toDouble()
            sumSquares += sample * sample
        }

        val rms = sqrt(sumSquares / count)
        val normalized = (rms / 7000.0).toFloat()
        return min(1.0f, max(0.0f, normalized))
    }
}
