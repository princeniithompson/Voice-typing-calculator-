package com.example.manual

data class ManualState(
    val expression: String = "",
    val result: String = "0",
    val isEvaluated: Boolean = false
)
