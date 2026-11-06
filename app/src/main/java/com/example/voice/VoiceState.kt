package com.example.voice

import java.math.BigDecimal

data class DetectedVoiceItem(
    val name: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val lineTotal: BigDecimal
)

data class VoiceBatch(
    val batchNumber: Int,
    val subtotal: BigDecimal,
    val itemsSummary: String,
    val items: List<DetectedVoiceItem>
)

data class VoiceState(
    val liveTranscript: String = "",
    val rawModelOutput: String = "",
    val detectedItems: List<DetectedVoiceItem> = emptyList(),
    val total: BigDecimal = BigDecimal.ZERO,
    val pendingItems: List<DetectedVoiceItem> = emptyList(),
    val pendingTotal: BigDecimal = BigDecimal.ZERO,
    val isEvaluated: Boolean = false,
    val accumulatedBatches: List<VoiceBatch> = emptyList(),
    val lastBatchTotal: BigDecimal? = null,
    val mode: VoiceMode = VoiceMode.IDLE,
    val statusCaption: String? = null
)

enum class VoiceMode {
    IDLE,
    LISTENING,
    LOCKED,
    PAUSED
}
