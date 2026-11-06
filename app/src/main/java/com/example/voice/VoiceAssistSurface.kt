package com.example.voice

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
    onHoldStart: () -> Unit = {},
    onHoldRelease: (Boolean) -> Unit = {},
    onLockToggle: () -> Unit = {},
    onPauseToggle: () -> Unit = {},
    onClearVoice: () -> Unit = {},
    onMicTap: () -> Unit = {},
    onPauseTap: () -> Unit = {},
    onACTap: () -> Unit = {},
    onVoiceEqualTap: () -> Unit = {},
    onSilenceAutoPause: () -> Unit = {},
    onAppBackgrounded: () -> Unit = {},
    onAddVoiceTotalToManual: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isMenuOpen by remember { mutableStateOf(false) }
    var isOverlayOpen by remember { mutableStateOf(false) }
    var showRawOutputDialog by remember { mutableStateOf(false) }

    val isListening = voiceState.mode == VoiceMode.LISTENING

    // Breathing & Glowing animation for active voice microphone drawing & pause/play button when listening
    val infiniteTransition = rememberInfiniteTransition(label = "mic_badge_pulse")
    val badgeScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge_scale"
    )

    val badgeGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = if (isListening) 0.6f else 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge_glow"
    )

    // Breathing & glowing animation for keyboard icon when manual typing is active (auto-stops after 1200ms of inactivity)
    var isKeyboardActive by remember { mutableStateOf(false) }

    LaunchedEffect(manualExpression) {
        if (manualExpression.isNotBlank() && manualExpression != "0") {
            isKeyboardActive = true
            kotlinx.coroutines.delay(1200)
            isKeyboardActive = false
        } else {
            isKeyboardActive = false
        }
    }

    val manualGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = if (isKeyboardActive) 0.5f else 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "manual_glow"
    )

    // Check microphone permission state
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasMicPermission = isGranted
        permissionDenied = !isGranted
        if (isGranted) {
            onMicTap()
        }
    }

    // App backgrounding / screen off observer
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) {
                onAppBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 60-second silence auto-pause safeguard
    LaunchedEffect(voiceState.mode, voiceTranscript) {
        if (voiceState.mode == VoiceMode.LISTENING) {
            kotlinx.coroutines.delay(60_000)
            onSilenceAutoPause()
        }
    }

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
        val maxKeyHoriz = (availableWidth - 9.dp) / 4

        val topBarHeight = 44.dp
        val baseCardHeight = 88.dp
        val micContainerHeight = 80.dp // Clean 80dp container without external text

        val topToVoiceGap = 8.dp
        val voiceToManualGap = 12.dp
        val manualToKeypadGap = 12.dp
        val keypadToMicGap = 12.dp
        val keyGap = 3.dp

        val nonKeypadBaseHeight = topBarHeight + topToVoiceGap + (baseCardHeight * 2) + voiceToManualGap + manualToKeypadGap + keypadToMicGap + micContainerHeight
        val availableForKeypad = (totalHeight - nonKeypadBaseHeight).coerceAtLeast(0.dp)

        val maxKeyVert = (availableForKeypad - 12.dp) / 5
        val keyDiameter = minOf(maxKeyHoriz, maxKeyVert).coerceAtLeast(52.dp)
        val keypadHeight = (keyDiameter * 5) + 12.dp

        val usedHeightWithBaseCards = nonKeypadBaseHeight + keypadHeight
        val surplus = (totalHeight - usedHeightWithBaseCards).coerceAtLeast(0.dp)
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

            // Zone 2: Voice Card
            VoiceCard(
                transcript = voiceTranscript,
                total = voiceTotal,
                height = cardHeight,
                isListening = isListening,
                badgeScale = badgeScale,
                badgeGlowAlpha = badgeGlowAlpha,
                onOpenOverlay = { isOverlayOpen = true },
                onAddVoiceTotalToManual = onAddVoiceTotalToManual,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(voiceToManualGap)) // 12dp

            // Zone 3: Manual Card
            ManualCard(
                expression = manualExpression,
                result = manualResult,
                height = cardHeight,
                manualGlowAlpha = manualGlowAlpha,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(manualToKeypadGap)) // 12dp

            // Zone 4: Keypad Grid
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

            // Zone 5: Mic Container (Clean, no external text captions)
            VoiceBar(
                voiceMode = voiceState.mode,
                onMicTap = {
                    if (hasMicPermission) {
                        onMicTap()
                    } else {
                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onPauseTap = onPauseTap,
                onACTap = onACTap,
                onEqualTap = onVoiceEqualTap,
                permissionDenied = permissionDenied,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Dropdown Overlay covering the manual calculator section
        AnimatedVisibility(
            visible = isOverlayOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .clickable { isOverlayOpen = false }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(bottom = 100.dp) // Leaves space for bottom mic bar!
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(28.dp))
                        .clickable(enabled = false) {}
                        .padding(16.dp)
                        .testTag("overlay_voice_dropdown")
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .graphicsLayer {
                                            scaleX = if (isListening) badgeScale else 1f
                                            scaleY = if (isListening) badgeScale else 1f
                                        }
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = "Voice active",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Voice Calculation Breakdown",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                            }

                            IconButton(
                                onClick = {
                                    HapticHelper.performHeavyClick(context)
                                    isOverlayOpen = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close overlay",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Recognized Speech",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                                .padding(12.dp)
                        ) {
                            Text(
                                text = if (voiceTranscript.isBlank()) "Speak food items or numbers..." else voiceTranscript,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Detected Items & Prices",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        if (voiceState.detectedItems.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No items calculated yet. Try saying '2 cups of rice and 1 chicken'",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(voiceState.detectedItems) { item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainer)
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = item.name,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "Qty: ${item.quantity}  ×  GH₵ ${String.format(java.util.Locale.US, "%.2f", item.unitPrice)}",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Text(
                                            text = "GH₵ ${String.format(java.util.Locale.US, "%.2f", item.lineTotal)}",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Button to inspect exact raw Gemini model response for troubleshooting
                        OutlinedButton(
                            onClick = {
                                HapticHelper.performHeavyClick(context)
                                showRawOutputDialog = true
                                val rawText = voiceState.rawModelOutput.ifBlank { voiceTranscript }
                                com.example.diag.Diag.log(
                                    "GEMINI_RAW_RESPONSE",
                                    "User opened Raw Gemini Model Response view. Raw output: '$rawText'"
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "View Raw Gemini Model Output",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.tertiary)
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Grand Total",
                                color = MaterialTheme.colorScheme.onTertiary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "GH₵ %.2f", voiceTotal),
                                color = MaterialTheme.colorScheme.onTertiary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Dialog showing exact raw text response from live Gemini model for direct comparison & log attachment
    if (showRawOutputDialog) {
        val rawText = voiceState.rawModelOutput.ifBlank { voiceTranscript }.ifBlank { "(No raw model response captured yet)" }

        Dialog(onDismissRequest = { showRawOutputDialog = false }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Raw Gemini Live Output",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                        }

                        IconButton(
                            onClick = { showRawOutputDialog = false },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text(
                        text = "Compare the live Gemini model's raw output with the application's processed interpretation for troubleshooting:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    // Raw Model Response Box
                    Text(
                        text = "1. Exact Raw Gemini Model Output",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = rawText,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }

                    // Processed Application Interpretation Summary Box
                    Text(
                        text = "2. Application Processed Interpretation",
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        val processedSummary = if (voiceState.detectedItems.isEmpty()) {
                            "Recognized Transcript: '$voiceTranscript'\nNo items calculated."
                        } else {
                            val itemsText = voiceState.detectedItems.joinToString("\n") {
                                "• ${it.name} (Qty: ${it.quantity} × GH₵ ${String.format(java.util.Locale.US, "%.2f", it.unitPrice)}) = GH₵ ${String.format(java.util.Locale.US, "%.2f", it.lineTotal)}"
                            }
                            "Parsed Items (${voiceState.detectedItems.size}):\n$itemsText\nGrand Total: GH₵ ${String.format(java.util.Locale.US, "%.2f", voiceTotal)}"
                        }

                        Text(
                            text = processedSummary,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                HapticHelper.performHeavyClick(context)
                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Raw Gemini Model Output", rawText)
                                clipboard.setPrimaryClip(clip)
                                com.example.diag.Diag.log(
                                    "GEMINI_RAW_RESPONSE",
                                    "User copied & attached raw Gemini model output to diagnostic log. Output: '$rawText'"
                                )
                                android.widget.Toast.makeText(context, "Copied raw output & attached to logs", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Copy & Attach to Logs", fontSize = 11.sp, maxLines = 1)
                        }

                        Button(
                            onClick = { showRawOutputDialog = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Close", fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceBar(
    voiceMode: VoiceMode,
    onMicTap: () -> Unit,
    onPauseTap: () -> Unit,
    onACTap: () -> Unit,
    onEqualTap: () -> Unit = {},
    permissionDenied: Boolean = false,
    onRequestPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isActive = voiceMode == VoiceMode.LISTENING || voiceMode == VoiceMode.PAUSED
    val isListening = voiceMode == VoiceMode.LISTENING

    // Pulsing animation for middle play/pause button when listening
    val infiniteTransition = rememberInfiniteTransition(label = "mic_bar_pulse")
    val middleButtonScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "middle_scale"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Permission warning banner if needed
        if (permissionDenied) {
            Row(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable {
                        HapticHelper.performHeavyClick(context)
                        try {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null)
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {
                            onRequestPermission()
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Microphone permission needed - Tap Settings",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Voice Bar Pill Container: 80dp height, fully rounded ends, green outline
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(40.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(40.dp)
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isActive,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "voice_bar_mode_transition"
            ) { active ->
                if (!active) {
                    // Inactive Voice State (Default): Single centered microphone floating action button
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    HapticHelper.performHeavyClick(context)
                                    onMicTap()
                                }
                                .testTag("btn_voice_mic_idle"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Start voice",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                } else {
                    // Active Voice State (Session Ongoing): 4-button control bar with larger outer buttons
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Outer Left: Stop button (Distinctly larger: 64dp)
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFF3B8BA))
                                .clickable {
                                    HapticHelper.performHeavyClick(context)
                                    onMicTap()
                                }
                                .testTag("btn_voice_stop"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Stop",
                                color = Color(0xFF680014),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        // 2. Inner Middle: Pause / Play (|| / ▶) (Standard size: 50dp)
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .graphicsLayer {
                                    scaleX = if (isListening) middleButtonScale else 1f
                                    scaleY = if (isListening) middleButtonScale else 1f
                                }
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable {
                                    HapticHelper.performHeavyClick(context)
                                    onPauseTap()
                                }
                                .testTag(if (voiceMode == VoiceMode.PAUSED) "btn_voice_resume" else "btn_voice_pause"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (voiceMode == VoiceMode.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (voiceMode == VoiceMode.PAUSED) "Resume" else "Pause",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // 3. Inner Middle: AC button (Standard size: 50dp)
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E2215))
                                .clickable {
                                    HapticHelper.performHeavyClick(context)
                                    onACTap()
                                }
                                .testTag("btn_voice_bar_ac"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AC",
                                color = Color(0xFFF4ABAA),
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }

                        // 4. Outer Right: Equal (=) button (Distinctly larger: 64dp)
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFFFBEA))
                                .clickable {
                                    HapticHelper.performHeavyClick(context)
                                    onEqualTap()
                                }
                                .testTag("btn_voice_bar_equal"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "=",
                                color = Color(0xFF1E2215),
                                fontFamily = CalculatorTokens.CalcTypeface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 28.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceCard(
    transcript: String,
    total: Double,
    height: Dp,
    isListening: Boolean = false,
    badgeScale: Float = 1f,
    badgeGlowAlpha: Float = 0.2f,
    onOpenOverlay: () -> Unit = {},
    onAddVoiceTotalToManual: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
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
            // Row 1: Glowing vector mic icon badge left + GH₵ total right (stops glowing when paused)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .graphicsLayer {
                            scaleX = if (isListening) badgeScale else 1f
                            scaleY = if (isListening) badgeScale else 1f
                        }
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = if (isListening) badgeGlowAlpha else 0.22f))
                        .border(
                            width = if (isListening) 2.dp else 0.dp,
                            color = if (isListening) contentColor else Color.Transparent,
                            shape = CircleShape
                        )
                        .clickable {
                            HapticHelper.performHeavyClick(context)
                            onOpenOverlay()
                        }
                        .testTag("btn_mic_drawing_overlay"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Open voice dropdown breakdown",
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

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

            // Row 2: Status prompt left (raw transcript hidden), Bridge '+' right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val promptText = if (isListening) "Tap = below to calculate" else "Tap mic below to start speaking"
                Text(
                    text = promptText,
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
                        .testTag("txt_voice_prompt")
                )

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
    manualGlowAlpha: Float = 0.15f,
    modifier: Modifier = Modifier
) {
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
            // Row 1: Glowing keyboard vector icon left (no text 'Manual') + Result right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(contentColor.copy(alpha = manualGlowAlpha))
                        .border(
                            width = if (manualGlowAlpha > 0.3f) 1.5.dp else 0.dp,
                            color = contentColor.copy(alpha = 0.6f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Manual entry",
                        tint = contentColor.copy(alpha = 0.85f),
                        modifier = Modifier.size(20.dp)
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
