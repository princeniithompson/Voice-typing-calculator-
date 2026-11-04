package com.example.voice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calculator.tokens.CalculatorTokens
import com.example.util.HapticHelper
import kotlinx.coroutines.launch

@Composable
fun VoiceBar(
    voiceMode: VoiceMode,
    onHoldStart: () -> Unit,
    onHoldRelease: (swipedToLock: Boolean) -> Unit,
    onLockToggle: () -> Unit,
    onPauseToggle: () -> Unit,
    onClearVoice: () -> Unit,
    statusCaption: String? = null,
    onHoldingStateChanged: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isTouchHolding by remember { mutableStateOf(false) }
    var totalDragX by remember { mutableFloatStateOf(0f) }
    var hasTriggeredHaptic by remember { mutableStateOf(false) }

    val isHoldingActive = isTouchHolding || voiceMode == VoiceMode.LISTENING

    LaunchedEffect(isTouchHolding) {
        onHoldingStateChanged?.invoke(isTouchHolding)
    }

    val micSize by animateDpAsState(
        targetValue = if (isHoldingActive) 76.dp else 68.dp,
        animationSpec = tween(durationMillis = 150),
        label = "mic_size"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (isHoldingActive) 0f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "text_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(40.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(40.dp)
            )
            .pointerInput(voiceMode) {
                if (voiceMode == VoiceMode.IDLE) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        HapticHelper.performHeavyClick(context)
                        isTouchHolding = true
                        totalDragX = 0f
                        hasTriggeredHaptic = false
                        onHoldStart()

                        var swipedToLock = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                break
                            }
                            totalDragX += change.positionChange().x
                            if (totalDragX < -150f) { // ~72dp swipe left threshold
                                if (!hasTriggeredHaptic) {
                                    // Double pulse (two heavy clicks 60ms apart)
                                    HapticHelper.performDoubleHeavyClick(context)
                                    hasTriggeredHaptic = true
                                }
                                swipedToLock = true
                            }
                        }

                        isTouchHolding = false
                        onHoldRelease(swipedToLock)
                    }
                }
            }
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            voiceMode == VoiceMode.LOCKED || voiceMode == VoiceMode.PAUSED -> {
                // State LOCKED / PAUSED
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 52dp lock circle filled with tertiary color
                    Box(
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.tertiary)
                            .clickable {
                                HapticHelper.performHeavyClick(context)
                                onLockToggle()
                            }
                            .testTag("btn_voice_locked_active"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked - Tap to unlock",
                            tint = MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Animated waveform in middle
                    AnimatedWaveform(
                        isAnimating = (voiceMode == VoiceMode.LOCKED),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .padding(horizontal = 8.dp)
                    )

                    // Right side controls: Pause button + AC pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(end = 2.dp)
                    ) {
                        // Pause / Play button (44dp circle)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable {
                                HapticHelper.performHeavyClick(context)
                                onPauseToggle()
                            }
                            .testTag(if (voiceMode == VoiceMode.PAUSED) "btn_voice_resume" else "btn_voice_pause"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (voiceMode == VoiceMode.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = if (voiceMode == VoiceMode.PAUSED) "Resume" else "Pause",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                        // AC pill (red-tinted error label on errorContainer surface)
                        Box(
                            modifier = Modifier
                                .width(54.dp)
                                .height(44.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(MaterialTheme.colorScheme.errorContainer)
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                                .clickable {
                                    HapticHelper.performHeavyClick(context)
                                    onClearVoice()
                                }
                                .testTag("btn_voice_bar_ac"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AC",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            isHoldingActive -> {
                // State HOLDING (touching / listening but not locked yet)
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 52dp lock circle to left of mic
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .testTag("btn_voice_lock_holding"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Swipe left to lock",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(22.dp))

                    // Mic circle (grows to 76dp)
                    Box(
                        modifier = Modifier
                            .size(micSize)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("btn_voice_mic_active"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Listening...",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            else -> {
                // State IDLE:
                // Left side text: "Hold to speak" (15sp) + "← Swipe left to lock" (12sp)
                // Right side: 68dp Mic button
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(
                        modifier = Modifier
                            .padding(start = 16.dp)
                            .alpha(textAlpha),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = statusCaption ?: "Hold to speak",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (statusCaption != null) "Check connection" else "← Swipe left to lock",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .testTag("btn_voice_mic_idle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Hold to speak",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedWaveform(
    isAnimating: Boolean,
    modifier: Modifier = Modifier
) {
    val barCount = 11
    val animatables = remember { List(barCount) { Animatable(0.3f) } }

    LaunchedEffect(isAnimating) {
        if (isAnimating) {
            animatables.forEachIndexed { index, anim ->
                launch {
                    val targetValues = listOf(0.2f, 0.9f, 0.5f, 1.0f, 0.4f, 0.8f, 0.3f)
                    val target = targetValues[(index * 3) % targetValues.size]
                    anim.animateTo(
                        targetValue = target,
                        animationSpec = infiniteRepeatable(
                            animation = tween(
                                durationMillis = 280 + (index * 45) % 200,
                                easing = FastOutSlowInEasing
                            ),
                            repeatMode = RepeatMode.Reverse
                        )
                    )
                }
            }
        } else {
            animatables.forEach { anim ->
                anim.snapTo(0.3f)
            }
        }
    }

    val dotColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val barWidth = 4.dp.toPx()
        val spacing = (totalWidth - (barCount * barWidth)) / (barCount - 1).coerceAtLeast(1)
        val maxHeight = size.height

        for (i in 0 until barCount) {
            val fraction = animatables[i].value
            val barH = (maxHeight * fraction).coerceIn(4.dp.toPx(), maxHeight)
            val left = i * (barWidth + spacing)
            val top = (maxHeight - barH) / 2f

            drawRoundRect(
                color = dotColor,
                topLeft = Offset(left, top),
                size = Size(barWidth, barH),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
