package com.example.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Noise gate for voice capture featuring:
 * 1. Rolling Lookahead Ring Buffer (200ms) to preserve initial speech onsets / consonants.
 * 2. Hangover Hold Time (500ms) to prevent clipping during conversational pauses.
 */
class AudioNoiseGate(
    initialThresholdDb: Float = -45.0f,
    private val lookaheadDurationMs: Long = 200L,
    private val hangoverDurationMs: Long = 500L,
    private val sampleRate: Int = 16000
) {
    // Noise gate threshold in dBFS (AudioRecord PCM 16-bit rmsdB ranges from -90dB to 0dB)
    private val _thresholdDb = MutableStateFlow(initialThresholdDb)
    val thresholdDb: StateFlow<Float> = _thresholdDb.asStateFlow()

    // Normalized audio level for UI (0.0 to 1.0)
    private val _normalizedLevel = MutableStateFlow(0f)
    val normalizedLevel: StateFlow<Float> = _normalizedLevel.asStateFlow()

    // Gate state: true when speech level exceeds threshold or hangover is active
    private val _isGateOpen = MutableStateFlow(false)
    val isGateOpen: StateFlow<Boolean> = _isGateOpen.asStateFlow()

    // 16000 samples/sec * 2 bytes/sample = 32000 bytes/sec -> 32 bytes/ms
    private val bytesPerMs = (sampleRate * 2) / 1000
    private val maxLookaheadBytes = (lookaheadDurationMs * bytesPerMs).toInt()

    private val lookaheadQueue = ArrayDeque<ByteArray>()
    @Volatile
    private var currentLookaheadBytes = 0

    @Volatile
    private var lastSpeechTimestamp: Long = 0L

    fun setThreshold(threshold: Float) {
        _thresholdDb.value = threshold
    }

    /**
     * Updates visual audio level metric from RMS dB.
     */
    fun processRmsDb(rmsdB: Float) {
        updateVisualLevel(rmsdB)
        val now = System.currentTimeMillis()
        if (rmsdB >= _thresholdDb.value) {
            lastSpeechTimestamp = now
            _isGateOpen.value = true
        } else if (_isGateOpen.value) {
            if (now - lastSpeechTimestamp >= hangoverDurationMs) {
                _isGateOpen.value = false
            }
        }
    }

    private fun updateVisualLevel(rmsdB: Float) {
        val minDb = -70.0f
        val maxDb = 0.0f
        val clamped = rmsdB.coerceIn(minDb, maxDb)
        val normalized = ((clamped - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)

        // Smooth visual response
        val previous = _normalizedLevel.value
        val smoothed = (previous * 0.4f) + (normalized * 0.6f)
        _normalizedLevel.value = smoothed
    }

    /**
     * Processes raw PCM audio chunks through the lookahead ring buffer and hangover gate.
     * When speech crosses the threshold, previously buffered lookahead frames are prepended,
     * ensuring words like "Two" do not lose their initial consonant onset.
     * During speech pauses, the gate stays open for [hangoverDurationMs] (500ms).
     *
     * @return List of audio chunk byte arrays to be transmitted.
     */
    @Synchronized
    fun processAudioChunk(
        pcmChunk: ByteArray,
        length: Int = pcmChunk.size,
        currentTimeMs: Long = System.currentTimeMillis()
    ): List<ByteArray> {
        val chunkData = if (pcmChunk.size == length) pcmChunk else pcmChunk.copyOf(length)
        val rmsDb = computeRmsDb(chunkData, chunkData.size)
        updateVisualLevel(rmsDb)

        val isAboveThreshold = rmsDb >= _thresholdDb.value

        return if (isAboveThreshold) {
            lastSpeechTimestamp = currentTimeMs
            if (!_isGateOpen.value) {
                // Speech onset: Transitioning CLOSED -> OPEN. Flush lookahead buffer!
                _isGateOpen.value = true
                val output = mutableListOf<ByteArray>()
                while (lookaheadQueue.isNotEmpty()) {
                    output.add(lookaheadQueue.removeFirst())
                }
                currentLookaheadBytes = 0
                output.add(chunkData)
                output
            } else {
                // Speech ongoing
                listOf(chunkData)
            }
        } else {
            // Below threshold
            if (_isGateOpen.value) {
                // Check if hangover hold time is still active
                if (currentTimeMs - lastSpeechTimestamp < hangoverDurationMs) {
                    listOf(chunkData)
                } else {
                    // Hangover expired: close gate
                    _isGateOpen.value = false
                    addToLookahead(chunkData)
                    emptyList()
                }
            } else {
                // Gate is closed: buffer chunk in lookahead ring buffer
                addToLookahead(chunkData)
                emptyList()
            }
        }
    }

    private fun addToLookahead(chunk: ByteArray) {
        lookaheadQueue.addLast(chunk)
        currentLookaheadBytes += chunk.size
        while (currentLookaheadBytes > maxLookaheadBytes && lookaheadQueue.isNotEmpty()) {
            val removed = lookaheadQueue.removeFirst()
            currentLookaheadBytes -= removed.size
        }
    }

    fun computeRmsDb(buffer: ByteArray, length: Int): Float {
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

    @Synchronized
    fun reset() {
        _normalizedLevel.value = 0f
        _isGateOpen.value = false
        lastSpeechTimestamp = 0L
        lookaheadQueue.clear()
        currentLookaheadBytes = 0
    }
}
