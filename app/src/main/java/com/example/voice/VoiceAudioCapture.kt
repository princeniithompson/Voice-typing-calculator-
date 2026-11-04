package com.example.voice

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
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Captures 16kHz 16-bit Mono PCM audio for Gemini Live transcription.
 * Measures real-time decibel levels for noise gating and visualizers.
 */
class VoiceAudioCapture(
    private val sampleRate: Int = 16000,
    private val channelConfig: Int = AudioFormat.CHANNEL_IN_MONO,
    private val audioFormat: Int = AudioFormat.ENCODING_PCM_16BIT
) {
    companion object {
        private const val TAG = "VoiceAudioCapture"
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    @Volatile
    private var isRecording = false

    val minBufferSize: Int = maxOf(
        AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat),
        2048
    )

    /**
     * Starts continuous audio capture.
     * @param scope CoroutineScope for the audio reading loop
     * @param onAudioChunk Callback receiving PCM byte chunks
     * @param onRmsDbChanged Callback receiving RMS amplitude in decibels (e.g. -60dB to 0dB)
     */
    @SuppressLint("MissingPermission")
    fun startCapture(
        scope: CoroutineScope,
        onAudioChunk: (ByteArray, Int) -> Unit,
        onRmsDbChanged: ((Float) -> Unit)? = null
    ): Boolean {
        if (isRecording) return true

        com.example.diag.Diag.log(
            "AUDIO",
            "startCapture request: sampleRate=$sampleRate, channels=1 (CHANNEL_IN_MONO), minBufferSize=$minBufferSize, format=PCM_16BIT"
        )

        return try {
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                channelConfig,
                audioFormat,
                minBufferSize * 2
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                com.example.diag.Diag.log("AUDIO", "AudioRecord failed to initialize (state=${record.state})")
                com.example.diag.Diag.isMicRecordingStarted = false
                record.release()
                return false
            }

            record.startRecording()
            audioRecord = record
            isRecording = true
            com.example.diag.Diag.recordMicRecordingStarted()
            com.example.diag.Diag.log("AUDIO", "AudioRecord recording started successfully (state=${record.state}, recordingState=${record.recordingState})")

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(minBufferSize)
                var totalChunks = 0L
                var totalBytes = 0L
                var lastLogTime = System.currentTimeMillis()

                while (isActive && isRecording) {
                    val readBytes = record.read(buffer, 0, buffer.size)
                    if (readBytes > 0) {
                        totalChunks++
                        totalBytes += readBytes
                        // Update the noise gate before the send callback so this chunk
                        // is judged on its own level, not the previous buffer.
                        val db = computeRmsDb(buffer, readBytes)
                        onRmsDbChanged?.invoke(db)
                        val chunkCopy = buffer.copyOf(readBytes)
                        onAudioChunk(chunkCopy, readBytes)

                        // Rate limit volume log: once per second
                        val now = System.currentTimeMillis()
                        if (now - lastLogTime >= 1000) {
                            lastLogTime = now
                            // Approximate normalized volume 0..1 from dB (-60dB -> 0, 0dB -> 1)
                            val normalizedVol = ((db + 60f) / 60f).coerceIn(0f, 1f)
                            com.example.diag.Diag.log(
                                "AUDIO",
                                "Mic audio active: volume=${String.format(java.util.Locale.US, "%.2f", normalizedVol)} (RMS: ${String.format(java.util.Locale.US, "%.1f", db)} dB), totalChunks=$totalChunks, totalBytes=$totalBytes"
                            )
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            com.example.diag.Diag.logException("AUDIO", "Error starting AudioRecord capture", e)
            isRecording = false
            com.example.diag.Diag.isMicRecordingStarted = false
            false
        }
    }

    fun stopCapture() {
        com.example.diag.Diag.log("AUDIO", "stopCapture invoked")
        isRecording = false
        com.example.diag.Diag.isMicRecordingStarted = false
        recordingJob?.cancel()
        recordingJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            com.example.diag.Diag.logException("AUDIO", "Error stopping AudioRecord", e)
        } finally {
            audioRecord = null
        }
    }

    fun isCapturing(): Boolean = isRecording

    private fun computeRmsDb(buffer: ByteArray, length: Int): Float {
        var sumSquares = 0.0
        val sampleCount = length / 2
        if (sampleCount == 0) return -100f

        var i = 0
        while (i < length - 1) {
            val sample = (buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)
            val shortVal = sample.toShort().toFloat()
            sumSquares += (shortVal * shortVal)
            i += 2
        }

        val rms = sqrt(sumSquares / sampleCount)
        return if (rms > 1.0) {
            (20.0 * log10(rms / 32768.0)).toFloat()
        } else {
            -100f
        }
    }
}
