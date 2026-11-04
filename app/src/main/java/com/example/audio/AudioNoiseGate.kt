package com.example.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioNoiseGate(
    initialThresholdDb: Float = -45.0f
) {
    // Noise gate threshold in dBFS (AudioRecord PCM 16-bit rmsdB ranges from -90dB to 0dB)
    private val _thresholdDb = MutableStateFlow(initialThresholdDb)
    val thresholdDb: StateFlow<Float> = _thresholdDb.asStateFlow()

    // Normalized audio level for UI (0.0 to 1.0)
    private val _normalizedLevel = MutableStateFlow(0f)
    val normalizedLevel: StateFlow<Float> = _normalizedLevel.asStateFlow()

    // Gate state: true when speech level exceeds threshold
    private val _isGateOpen = MutableStateFlow(false)
    val isGateOpen: StateFlow<Boolean> = _isGateOpen.asStateFlow()

    fun setThreshold(threshold: Float) {
        _thresholdDb.value = threshold
    }

    /**
     * Process raw rmsdB from AudioRecord
     * Standard rmsdB range: approx -70.0 dB (silence) to 0.0 dB (peak)
     */
    fun processRmsDb(rmsdB: Float) {
        val minDb = -70.0f
        val maxDb = 0.0f
        val clamped = rmsdB.coerceIn(minDb, maxDb)
        val normalized = ((clamped - minDb) / (maxDb - minDb)).coerceIn(0f, 1f)

        // Smooth visual response
        val previous = _normalizedLevel.value
        val smoothed = (previous * 0.4f) + (normalized * 0.6f)
        _normalizedLevel.value = smoothed

        _isGateOpen.value = rmsdB >= _thresholdDb.value
    }

    fun reset() {
        _normalizedLevel.value = 0f
        _isGateOpen.value = false
    }
}
