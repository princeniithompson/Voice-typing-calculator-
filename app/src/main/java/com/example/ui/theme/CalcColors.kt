package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class CalcColors(
    val screen: Color,
    val digitKey: Color, val onDigitKey: Color,
    val opKey: Color, val onOpKey: Color,
    val acKey: Color, val onAcKey: Color,
    val equalsKey: Color, val onEqualsKey: Color,
    val voiceCard: Color, val onVoiceCard: Color,
    val manualCard: Color, val onManualCard: Color, val manualResult: Color,
    val micButton: Color, val onMicButton: Color,
    val barSurface: Color, val barOutline: Color,
    val danger: Color, val dangerContainer: Color,
    val muted: Color
)

@Composable
fun calcColors(): CalcColors {
    val c = MaterialTheme.colorScheme
    return CalcColors(
        screen = c.surface,
        digitKey = c.surfaceContainerHigh, onDigitKey = c.onSurface,
        opKey = c.primaryContainer, onOpKey = c.onPrimaryContainer,
        acKey = c.surfaceContainerHigh, onAcKey = c.error,
        equalsKey = c.tertiary, onEqualsKey = c.onTertiary,
        voiceCard = c.tertiary, onVoiceCard = c.onTertiary,   // same token as "="
        manualCard = c.primaryContainer, onManualCard = c.onPrimaryContainer,
        manualResult = c.tertiary,
        micButton = c.primary, onMicButton = c.onPrimary,
        barSurface = c.surfaceContainer, barOutline = c.primary,
        danger = c.error, dangerContainer = c.errorContainer,
        muted = c.onSurfaceVariant
    )
}
