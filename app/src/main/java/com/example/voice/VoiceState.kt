package com.example.voice

import java.math.BigDecimal

data class DetectedVoiceItem(
    val name: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val lineTotal: BigDecimal
)

data class VoiceState(
    val liveTranscript: String = "",
    val detectedItems: List<DetectedVoiceItem> = emptyList(),
    val total: BigDecimal = BigDecimal.ZERO,
    val mode: VoiceMode = VoiceMode.IDLE,
    val statusCaption: String? = null
)

enum class VoiceMode {
    IDLE,
    LISTENING,
    LOCKED,
    PAUSED
}
