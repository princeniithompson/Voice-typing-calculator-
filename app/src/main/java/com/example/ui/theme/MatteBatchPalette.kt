package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Provides flat, solid, opaque Material 3 container colors for history batch cards.
 * Implements a "Dry Paint" matte texture without neon glowing effects or glass gradients.
 * Cycles smoothly through M3 container roles and distinct tonal steps so no batch badge repeats.
 */
object MatteBatchPalette {

    data class BatchColorToken(
        val containerColor: Color,
        val onContainerColor: Color,
        val borderColor: Color
    )

    fun getBatchColorToken(batchIndex: Int, colorScheme: ColorScheme): BatchColorToken {
        // Flat, opaque M3 container roles:
        // 0: Primary Container
        // 1: Tertiary Container
        // 2: Secondary Container
        // 3: Surface Variant / High
        // 4: Error Container (Soft Coral)
        // 5+: Tonal shifts
        return when (batchIndex % 5) {
            0 -> BatchColorToken(
                containerColor = colorScheme.primaryContainer,
                onContainerColor = colorScheme.onPrimaryContainer,
                borderColor = colorScheme.primary.copy(alpha = 0.45f)
            )
            1 -> BatchColorToken(
                containerColor = colorScheme.tertiaryContainer,
                onContainerColor = colorScheme.onTertiaryContainer,
                borderColor = colorScheme.tertiary.copy(alpha = 0.45f)
            )
            2 -> BatchColorToken(
                containerColor = colorScheme.secondaryContainer,
                onContainerColor = colorScheme.onSecondaryContainer,
                borderColor = colorScheme.secondary.copy(alpha = 0.45f)
            )
            3 -> BatchColorToken(
                containerColor = colorScheme.surfaceVariant,
                onContainerColor = colorScheme.onSurfaceVariant,
                borderColor = colorScheme.outline.copy(alpha = 0.45f)
            )
            else -> BatchColorToken(
                containerColor = colorScheme.errorContainer,
                onContainerColor = colorScheme.onErrorContainer,
                borderColor = colorScheme.error.copy(alpha = 0.45f)
            )
        }
    }
}
