package com.example.pricing

data class PriceItem(
    val id: Long = 0,
    val name: String,
    val price: Double,
    val category: String = "General",
    val aliases: List<String> = emptyList(),
    val unitLabel: String = ""
)

data class OrderLine(
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val subtotal: Double = quantity * unitPrice
)

data class ParsedSpeechResult(
    val transcript: String,
    val total: Double,
    val isMath: Boolean = false,
    val lines: List<OrderLine> = emptyList()
)
