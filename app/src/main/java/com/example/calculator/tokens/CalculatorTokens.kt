package com.example.calculator.tokens

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

object CalculatorTokens {
    val CalcTypeface = FontFamily(
        Font(R.font.m_plus_rounded_1c, FontWeight.Normal)
    )

    // Geometry Tokens
    val SpacingUnit = 2.dp                  // S = G = 2.dp
    val KeyGap = SpacingUnit                // G = 2.dp
    val SideMargin = 4.dp                   // M = 4.dp (~1% of viewport width)
    val BottomMargin = 12.dp                // Mb = 12.dp (~3% of viewport height)

    // Display Tokens
    val DisplayTextSize = 58.sp             // ~12% viewport width cap height
    val DisplayRightMargin = 22.dp          // ~5.5% viewport width
    val CursorWidth = 2.dp
    val CursorSpacing = 4.dp                // 4dp after last glyph

    // Chevron handle positioning
    val ChevronX = 36.dp                    // ~9% viewport width
    val ChevronYOffset = (-8).dp            // top edge of key row - 8dp

    val KeypadLabelTextSize = 46.sp
}
