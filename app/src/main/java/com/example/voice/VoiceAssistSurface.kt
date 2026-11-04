package com.example.voice

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculator.tokens.CalculatorTokens
import com.example.calculator.ui.SharedKeypad
import com.example.util.HapticHelper

fun Modifier.fadeLeftEdge(): Modifier = this
    .graphicsLayer { alpha = 0.99f }
    .drawWithContent {
        drawContent()
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.15f to Color.Black,
                startX = 0f,
                endX = size.width
            ),
            blendMode = BlendMode.DstIn
        )
    }

@Composable
fun VoiceAssistSurface(
    voiceTranscript: String,
    voiceTotal: Double,
    manualExpression: String,
    manualResult: String,
    voiceState: VoiceState,
    isVoiceAssistEnabled: Boolean,
    onKeypadPress: (String) -> Unit,
    onToggleVoiceAssist: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenCatalog: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenDiagnostics: () -> Unit = {},
    onOpenTestPlayground: () -> Unit = {},
    onHoldStart: () -> Unit,
    onHoldRelease: (Boolean) -> Unit,
    onLockToggle: () -> Unit,
    onPauseToggle: () -> Unit,
    onClearVoice: () -> Unit,
    onAddVoiceTotalToManual: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isMenuOpen by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
    ) {
        val totalWidth = maxWidth
        val totalHeight = maxHeight

        val availableWidth = totalWidth
        
        // 4 keys + 3 gaps of 3dp (9dp)
        val maxKeyHoriz = (availableWidth - 9.dp) / 4

        // Base heights
        val topBarHeight = 44.dp
        val baseCardHeight = 88.dp
        val micContainerHeight = 80.dp

        // Vertical gaps
        val topToVoiceGap = 8.dp
        val voiceToManualGap = 12.dp
        val manualToKeypadGap = 12.dp
        val keypadToMicGap = 12.dp
        val keyGap = 3.dp

        // Height consumed by non-keypad elements with base 88dp cards
        val nonKeypadBaseHeight = topBarHeight + topToVoiceGap + (baseCardHeight * 2) + voiceToManualGap + manualToKeypadGap + keypadToMicGap + micContainerHeight
        val availableForKeypad = (totalHeight - nonKeypadBaseHeight).coerceAtLeast(0.dp)

        // Vertical key diameter: 5 keys + 4 gaps of 3dp (12dp)
        val maxKeyVert = (availableForKeypad - 12.dp) / 5

        // Key diameter d = min(maxKeyHoriz, maxKeyVert), minimum 52dp
        val keyDiameter = minOf(maxKeyHoriz, maxKeyVert).coerceAtLeast(52.dp)

        // Keypad total height = 5 * keyDiameter + 12dp
        val keypadHeight = (keyDiameter * 5) + 12.dp

        // Calculate surplus vertical space
        val usedHeightWithBaseCards = nonKeypadBaseHeight + keypadHeight
        val surplus = (totalHeight - usedHeightWithBaseCards).coerceAtLeast(0.dp)

        // Allocate surplus space to card heights first (88dp up to maximum 100dp)
        val cardHeight = minOf(100.dp, baseCardHeight + (surplus / 2))

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top
        ) {
            // Zone 1: Top Bar (44dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(topBarHeight),
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

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            HapticHelper.performHeavyClick(context)
                            val totalText = if (voiceTotal > 0.0) {
                                String.format(java.util.Locale.US, "GH₵ %.2f", voiceTotal)
                            } else {
                                manualResult
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Total", totalText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "$totalText copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("btn_copy_clipboard")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy total to clipboard",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box {
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
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
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
                                                checkedColor = MaterialTheme.colorScheme.primary,
                                                uncheckedColor = MaterialTheme.colorScheme.onSurface,
                                                checkmarkColor = MaterialTheme.colorScheme.onPrimary
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
                                        text = "Price List / Catalog",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp
                                    )
                                },
                                onClick = {
                                    HapticHelper.performHeavyClick(context)
                                    isMenuOpen = false
                                    onOpenCatalog()
                                },
                                modifier = Modifier.testTag("menu_catalog")
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
            }

            Spacer(modifier = Modifier.height(topToVoiceGap)) // 8dp

            // Zone 2: Voice Card (tertiary token = SAME token as "=" key!)
            VoiceCard(
                transcript = voiceTranscript,
                total = voiceTotal,
                height = cardHeight,
                onAddVoiceTotalToManual = onAddVoiceTotalToManual,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(voiceToManualGap)) // 12dp

            // Zone 3: Manual Card (primaryContainer token)
            ManualCard(
                expression = manualExpression,
                result = manualResult,
                height = cardHeight,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(manualToKeypadGap)) // 12dp

            // Zone 4: Keypad Grid (3dp gaps)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                SharedKeypad(
                    onKeyPress = onKeypadPress,
                    keyDiameter = keyDiameter,
                    gap = keyGap,
                    isHybridMode = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(keypadToMicGap)) // 12dp

            // Zone 5: Mic Container (80dp)
            VoiceBar(
                voiceMode = voiceState.mode,
                onHoldStart = onHoldStart,
                onHoldRelease = onHoldRelease,
                onLockToggle = onLockToggle,
                onPauseToggle = onPauseToggle,
                onClearVoice = onClearVoice,
                statusCaption = voiceState.statusCaption,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun VoiceCard(
    transcript: String,
    total: Double,
    height: Dp,
    onAddVoiceTotalToManual: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Voice card background uses tertiary (SAME token as "=" key!)
    val bgColor = MaterialTheme.colorScheme.tertiary
    val contentColor = MaterialTheme.colorScheme.onTertiary

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("voice_display_card")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Row 1: Badge + Label left; Result + GH₵ right on SAME baseline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(contentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice input",
                            tint = contentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Voice",
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                // Row 1 Right: Currency symbol + total amount
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = "GH₵",
                        fontFamily = CalculatorTokens.CalcTypeface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = 0.9f),
                        modifier = Modifier.padding(bottom = 2.dp, end = 4.dp)
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.2f", total),
                        fontFamily = CalculatorTokens.CalcTypeface,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        modifier = Modifier.testTag("txt_voice_total")
                    )
                }
            }

            // Row 2: Left: transcript line, Right: relocated '+' deliberate bridge button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (transcript.isBlank()) "Hold mic to speak order or numbers" else transcript,
                    fontFamily = CalculatorTokens.CalcTypeface,
                    color = contentColor.copy(alpha = 0.80f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 8.dp)
                        .fadeLeftEdge()
                        .testTag("txt_voice_transcript")
                )

                // Relocated bridge button: adds voice total to Manual expression
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = 0.22f))
                        .clickable {
                            HapticHelper.performHeavyClick(context)
                            onAddVoiceTotalToManual()
                        }
                        .testTag("btn_bridge_add_to_manual"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add voice total to manual",
                        tint = contentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ManualCard(
    expression: String,
    result: String,
    height: Dp,
    modifier: Modifier = Modifier
) {
    // Manual card uses primaryContainer; result uses tertiary
    val bgColor = MaterialTheme.colorScheme.primaryContainer
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    val resultColor = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(24.dp))
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("manual_display_card")
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Row 1: Badge + Label left; Live Result right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Manual entry",
                        tint = contentColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Manual",
                        color = contentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                Text(
                    text = if (result.isBlank()) "0" else result,
                    fontFamily = CalculatorTokens.CalcTypeface,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = resultColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.testTag("txt_manual_result")
                )
            }

            // Row 2: Right-aligned expression with left-edge fade
            Text(
                text = if (expression.isBlank()) "0" else expression,
                fontFamily = CalculatorTokens.CalcTypeface,
                color = contentColor.copy(alpha = 0.85f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .fillMaxWidth()
                    .fadeLeftEdge()
                    .testTag("txt_manual_expression")
            )
        }
    }
}
