package com.example.calculator.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculator.tokens.CalculatorTokens
import com.example.util.HapticHelper

data class KeypadKey(
    val label: String,
    val value: String,
    val bgColor: Color,
    val textColor: Color,
    val isIcon: Boolean = false,
    val testTag: String
)

@Composable
fun SharedKeypad(
    onKeyPress: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyDiameter: Dp? = null,
    gap: Dp = 3.dp,
    isHybridMode: Boolean = false
) {
    val acBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val acText = MaterialTheme.colorScheme.error

    val digitBg = MaterialTheme.colorScheme.surfaceContainerHigh
    val digitText = MaterialTheme.colorScheme.onSurface

    val opBg = MaterialTheme.colorScheme.primaryContainer
    val opText = MaterialTheme.colorScheme.onPrimaryContainer

    val equalBg = MaterialTheme.colorScheme.tertiary
    val equalText = MaterialTheme.colorScheme.onTertiary

    val rows = remember(acBg, acText, digitBg, digitText, opBg, opText, equalBg, equalText) {
        listOf(
            // Row 1: AC, ( ), %, ÷
            listOf(
                KeypadKey("AC", "AC", acBg, acText, testTag = "btn_ac"),
                KeypadKey("( )", "( )", opBg, opText, testTag = "btn_paren"),
                KeypadKey("%", "%", opBg, opText, testTag = "btn_percent"),
                KeypadKey("÷", "÷", opBg, opText, testTag = "btn_div")
            ),
            // Row 2: 7, 8, 9, ×
            listOf(
                KeypadKey("7", "7", digitBg, digitText, testTag = "btn_7"),
                KeypadKey("8", "8", digitBg, digitText, testTag = "btn_8"),
                KeypadKey("9", "9", digitBg, digitText, testTag = "btn_9"),
                KeypadKey("×", "×", opBg, opText, testTag = "btn_mul")
            ),
            // Row 3: 4, 5, 6, −
            listOf(
                KeypadKey("4", "4", digitBg, digitText, testTag = "btn_4"),
                KeypadKey("5", "5", digitBg, digitText, testTag = "btn_5"),
                KeypadKey("6", "6", digitBg, digitText, testTag = "btn_6"),
                KeypadKey("−", "−", opBg, opText, testTag = "btn_sub")
            ),
            // Row 4: 1, 2, 3, +
            listOf(
                KeypadKey("1", "1", digitBg, digitText, testTag = "btn_1"),
                KeypadKey("2", "2", digitBg, digitText, testTag = "btn_2"),
                KeypadKey("3", "3", digitBg, digitText, testTag = "btn_3"),
                KeypadKey("+", "+", opBg, opText, testTag = "btn_add")
            ),
            // Row 5: 0, ., ⌫, =
            listOf(
                KeypadKey("0", "0", digitBg, digitText, testTag = "btn_0"),
                KeypadKey(".", ".", digitBg, digitText, testTag = "btn_dot"),
                KeypadKey("⌫", "⌫", digitBg, digitText, isIcon = true, testTag = "btn_backspace"),
                KeypadKey("=", "=", equalBg, equalText, testTag = "btn_equals")
            )
        )
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(gap),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        rows.forEach { rowKeys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                rowKeys.forEach { key ->
                    KeypadCircleButton(
                        key = key,
                        onClick = { onKeyPress(key.value) },
                        keyDiameter = keyDiameter,
                        modifier = if (keyDiameter == null) Modifier.weight(1f) else Modifier
                    )
                }
            }
        }
    }
}

@Composable
fun KeypadCircleButton(
    key: KeypadKey,
    onClick: () -> Unit,
    keyDiameter: Dp? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "key_scale"
    )

    val baseModifier = if (keyDiameter != null) {
        modifier.size(keyDiameter)
    } else {
        modifier
            .aspectRatio(1f)
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
    }

    val labelFontSize = if (keyDiameter != null) {
        (keyDiameter.value * 0.48f).sp
    } else {
        CalculatorTokens.KeypadLabelTextSize
    }

    Box(
        modifier = baseModifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(key.bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                HapticHelper.performHeavyClick(context)
                onClick()
            }
            .testTag(key.testTag),
        contentAlignment = Alignment.Center
    ) {
        if (key.isIcon) {
            Icon(
                imageVector = Icons.Outlined.Backspace,
                contentDescription = "Backspace",
                tint = key.textColor,
                modifier = Modifier.size(if (keyDiameter != null) (keyDiameter.value * 0.45f).dp else 24.dp)
            )
        } else {
            Text(
                text = key.label,
                fontFamily = CalculatorTokens.CalcTypeface,
                fontSize = labelFontSize,
                fontWeight = if (key.label == "=" || key.label == "AC") FontWeight.Medium else FontWeight.Normal,
                color = key.textColor
            )
        }
    }
}
