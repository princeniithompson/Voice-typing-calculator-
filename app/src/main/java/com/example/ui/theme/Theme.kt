package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

// The ONLY place hex colors may exist (Android 11 and below).
private val FallbackDark = darkColorScheme(
    primary = Color(0xFF6FA0FF), onPrimary = Color(0xFF00214D),
    primaryContainer = Color(0xFF1450C4), onPrimaryContainer = Color(0xFFFFFFFF),
    tertiary = Color(0xFFE9A8FF), onTertiary = Color(0xFF2B0A3D),
    surface = Color(0xFF000C28), onSurface = Color(0xFFEAF0FF),
    onSurfaceVariant = Color(0xFF9DB2E0),
    surfaceContainer = Color(0xFF0B1B3F), surfaceContainerHigh = Color(0xFF10306A),
    error = Color(0xFFFF9A92), errorContainer = Color(0xFF2A1530)
)

private val FallbackLight = lightColorScheme(
    primary = Color(0xFF1D4ED8), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6FF), onPrimaryContainer = Color(0xFF001A4D),
    tertiary = Color(0xFF8E3FB5), onTertiary = Color.White,
    surface = Color(0xFFF7F6FD), onSurface = Color(0xFF14132B),
    onSurfaceVariant = Color(0xFF6B6A80),
    surfaceContainer = Color(0xFFF1F0FA), surfaceContainerHigh = Color(0xFFE6E4F5),
    error = Color(0xFFB3261E), errorContainer = Color(0xFFFCE8E6)
)

@Composable
fun QuickCalcTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val config = LocalConfiguration.current   // key: recompute when wallpaper/uiMode changes
    val scheme = remember(dark, config) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) FallbackDark else FallbackLight
    }
    MaterialTheme(colorScheme = scheme, typography = Typography) {
        CompositionLocalProvider(
            LocalTextSelectionColors provides TextSelectionColors(
                handleColor = scheme.primary,
                backgroundColor = scheme.primary.copy(alpha = 0.4f)
            )
        ) {
            content()
        }
    }
}

// Alias for backwards compatibility
@Composable
fun VoicePosTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    QuickCalcTheme(content = content)
}
