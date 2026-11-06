package com.example.voice

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.HapticHelper

@Composable
fun VoiceBar(
    voiceMode: VoiceMode,
    onMicTap: () -> Unit,
    onPauseTap: () -> Unit,
    onACTap: () -> Unit,
    statusCaption: String? = null,
    permissionDenied: Boolean = false,
    onRequestPermission: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isActive = voiceMode == VoiceMode.LISTENING || voiceMode == VoiceMode.PAUSED

    // Caption text determination
    val captionText = when {
        permissionDenied -> "Microphone permission needed"
        !statusCaption.isNullOrBlank() -> statusCaption
        voiceMode == VoiceMode.LISTENING -> "Listening... tap the mic to finish"
        voiceMode == VoiceMode.PAUSED -> "Paused. The mic is muted"
        else -> "Tap the mic to start"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Caption above container
        Row(
            modifier = Modifier.padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = captionText,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("txt_voice_status_caption")
            )

            if (permissionDenied) {
                Spacer(modifier = Modifier.width(8.dp))
                Row(
                    modifier = Modifier
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
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Settings",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Mic Container: Full width, 88dp height, fully rounded ends
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(88.dp)
                .clip(RoundedCornerShape(44.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(44.dp)
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            val totalWidth = maxWidth

            // 1. Mic Button (72dp) X-Offset Animation
            val micSize = 72.dp
            val centerOffset = (totalWidth - micSize) / 2
            val leftOffset = 12.dp

            val targetMicOffset = if (isActive) leftOffset else centerOffset

            val animatedMicOffset by animateDpAsState(
                targetValue = targetMicOffset,
                animationSpec = spring(stiffness = 300f, dampingRatio = 0.75f),
                label = "mic_x_offset"
            )

            // Pulsing Ring for Listening state
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.22f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "pulse_scale"
            )
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.45f,
                targetValue = 0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "pulse_alpha"
            )

            // 2. Pause / Play Button (60dp, centered in container)
            val controlsAlpha by animateFloatAsState(
                targetValue = if (isActive) 1f else 0f,
                animationSpec = tween(300),
                label = "controls_alpha"
            )

            if (isActive || controlsAlpha > 0.05f) {
                // Pause/Play Button centered in container
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .alpha(controlsAlpha)
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(enabled = isActive) {
                            HapticHelper.performHeavyClick(context)
                            onPauseTap()
                        }
                        .testTag(if (voiceMode == VoiceMode.PAUSED) "btn_voice_resume" else "btn_voice_pause"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (voiceMode == VoiceMode.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = if (voiceMode == VoiceMode.PAUSED) "Resume" else "Pause",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // AC Pill (56dp high, 76dp wide, 14dp from right edge)
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 14.dp)
                        .alpha(controlsAlpha)
                        .width(76.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .border(
                            BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(28.dp)
                        )
                        .clickable(enabled = isActive) {
                            HapticHelper.performHeavyClick(context)
                            onACTap()
                        }
                        .testTag("btn_voice_bar_ac"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "AC",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            }

            // 3. Mic Button (72dp) with Pulsing Ring
            Box(
                modifier = Modifier
                    .offset(x = animatedMicOffset)
                    .size(micSize),
                contentAlignment = Alignment.Center
            ) {
                // Pulsing ring around mic when LISTENING
                if (voiceMode == VoiceMode.LISTENING) {
                    Box(
                        modifier = Modifier
                            .size(micSize * pulseScale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
                    )
                }

                // Mic Circle Button
                Box(
                    modifier = Modifier
                        .size(micSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            HapticHelper.performHeavyClick(context)
                            onMicTap()
                        }
                        .testTag(if (isActive) "btn_voice_mic_active" else "btn_voice_mic_idle"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = if (isActive) "Finish voice" else "Start voice",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
