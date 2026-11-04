package com.example.calculator.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculator.tokens.CalculatorTokens
import com.example.util.HapticHelper

@Composable
fun ClassicSurface(
    expression: String,
    liveResult: String?,
    isVoiceAssistEnabled: Boolean,
    onKeyPress: (String) -> Unit,
    onToggleVoiceAssist: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenTestPlayground: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isMenuOpen by remember { mutableStateOf(false) }
    var isScientificOpen by remember { mutableStateOf(false) }

    // 2dp blinking cursor animation
    val infiniteTransition = rememberInfiniteTransition(label = "cursor_blink")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    // CR-005 Platform Inset Awareness
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // 3.1 Top chrome: icon center ~9% viewport height
    val topChromePadding = if (statusInset > 0.dp) statusInset + 12.dp else 36.dp

    // 2.1 Inset-aware bottom margin: Mb = navInset + clearance (8dp), min 16dp
    val bottomMargin = if (navInset > 0.dp) navInset + 8.dp else 16.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 3.1 Top Chrome: History icon left; Overflow ⋮ right. Top chrome center Y ~9% H
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = topChromePadding,
                        start = CalculatorTokens.SideMargin,
                        end = CalculatorTokens.SideMargin
                    ),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        HapticHelper.performHeavyClick(context)
                        onOpenHistory()
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("btn_history")
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Box(modifier = Modifier.wrapContentSize(Alignment.TopEnd)) {
                    IconButton(
                        onClick = {
                            HapticHelper.performHeavyClick(context)
                            isMenuOpen = true
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("btn_overflow_menu")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = isMenuOpen,
                        onDismissRequest = { isMenuOpen = false },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .testTag("overflow_dropdown")
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(CalculatorTokens.KeyGap)
                                ) {
                                    Checkbox(
                                        checked = isVoiceAssistEnabled,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.tertiary,
                                            uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                            checkmarkColor = MaterialTheme.colorScheme.onTertiary
                                        )
                                    )
                                    Text(
                                        text = "Voice assist",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            },
                            onClick = {
                                HapticHelper.performHeavyClick(context)
                                isMenuOpen = false
                                onToggleVoiceAssist()
                            },
                            modifier = Modifier.testTag("menu_toggle_voice_assist")
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Test",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            onClick = {
                                HapticHelper.performHeavyClick(context)
                                isMenuOpen = false
                                onOpenTestPlayground()
                            },
                            modifier = Modifier.testTag("menu_test")
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "History",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp
                                )
                            },
                            onClick = {
                                HapticHelper.performHeavyClick(context)
                                isMenuOpen = false
                                onOpenHistory()
                            },
                            modifier = Modifier.testTag("menu_history")
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Diagnostics",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp
                                )
                            },
                            onClick = {
                                HapticHelper.performHeavyClick(context)
                                isMenuOpen = false
                                onOpenDiagnostics()
                            },
                            modifier = Modifier.testTag("menu_diagnostics")
                        )

                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Settings & API Key",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp
                                )
                            },
                            onClick = {
                                HapticHelper.performHeavyClick(context)
                                isMenuOpen = false
                                onOpenSettings()
                            },
                            modifier = Modifier.testTag("menu_settings")
                        )
                    }
                }
            }

            // 3.2 Display Area: Expression vertical center ~18.5% H, Right Margin 22dp (~5.5% W)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, end = CalculatorTokens.DisplayRightMargin),
                horizontalAlignment = Alignment.End
            ) {
                // Primary line: right-aligned expression in 58sp (~12% W cap height) with 2dp blinking accent cursor
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = expression,
                        fontFamily = CalculatorTokens.CalcTypeface,
                        fontSize = CalculatorTokens.DisplayTextSize,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        maxLines = 2,
                        modifier = Modifier.testTag("classic_display_expression")
                    )

                    // 2dp blinking cursor 4dp after last glyph
                    Spacer(modifier = Modifier.width(CalculatorTokens.CursorSpacing))
                    Box(
                        modifier = Modifier
                            .width(CalculatorTokens.CursorWidth)
                            .height(56.dp)
                            .alpha(cursorAlpha)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("display_cursor")
                    )
                }

                // Secondary line: right-aligned live result shown ONLY when current expression is evaluable
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (liveResult != null && expression.isNotEmpty()) {
                        Text(
                            text = liveResult,
                            fontFamily = CalculatorTokens.CalcTypeface,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.tertiary,
                            textAlign = TextAlign.End,
                            modifier = Modifier.testTag("classic_live_result")
                        )
                    }
                }
            }

            // 3.4 Spacer weight=1 absorbs ALL remaining vertical space between display block and chevron handle
            Spacer(modifier = Modifier.weight(1f))

            // 3.3 Chevron handle at x ~9% W (36dp) and y = top edge of key row - 8dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = CalculatorTokens.ChevronX, bottom = 8.dp),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.UnfoldMore,
                    contentDescription = "Expand or collapse display",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            HapticHelper.performHeavyClick(context)
                            isScientificOpen = !isScientificOpen
                        }
                        .testTag("btn_unfold_more")
                )
            }

            // 2.1 Inset-aware bottom margin: Mb = navInset + clearance (8dp)
            SharedKeypad(
                onKeyPress = onKeyPress,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = bottomMargin)
            )
        }

        // Scientific drawer slide
        ScientificPanel(
            isOpen = isScientificOpen,
            onClose = { isScientificOpen = false },
            onKeyPress = { key ->
                onKeyPress(key)
            },
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }
}

