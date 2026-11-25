package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.PersistedAudioSettings
import com.example.model.LyricsUiState
import com.example.model.PlaybackSnapshot
import com.example.ui.theme.LocalAppFontColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DimLockOverlay(
    visible: Boolean,
    snapshot: PlaybackSnapshot,
    lyricsState: LyricsUiState,
    livePositionMs: Long,
    gestureFeedback: String?,
    settings: PersistedAudioSettings,
    accentColor: Color,
    onUnlock: () -> Unit
) {
    val fontColors = LocalAppFontColors.current

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        var currentTimeStr by remember { mutableStateOf("") }
        LaunchedEffect(visible, settings.use24HourClock) {
            val pattern = if (settings.use24HourClock) "HH:mm" else "h:mm a"
            val fmt = SimpleDateFormat(pattern, Locale.getDefault())
            while (visible) {
                currentTimeStr = fmt.format(Date())
                delay(1_000L)
            }
        }

        val activeLyricText = remember(lyricsState, livePositionMs) {
            if (lyricsState is LyricsUiState.Ready && lyricsState.lines.isNotEmpty()) {
                val adjustedPos = livePositionMs - lyricsState.vocalOffsetMs
                val activeIdx = lyricsState.lines.indexOfLast { it.timestampMs <= adjustedPos }
                if (activeIdx >= 0) lyricsState.lines[activeIdx].text else lyricsState.lines.first().text
            } else {
                null
            }
        }

        val dynamicHints = remember(settings) {
            "Vol+: ${settings.volUpShortAction.label}  •  Vol-: ${settings.volDownShortAction.label}  •  " +
                "Hold +/-: ${settings.volUpLongAction.label} / ${settings.volDownLongAction.label}"
        }

        var isHolding by remember { mutableStateOf(false) }
        var holdProgress by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(isHolding) {
            if (isHolding) {
                val start = System.currentTimeMillis()
                val duration = 1100f
                while (isHolding) {
                    val elapsed = (System.currentTimeMillis() - start).toFloat()
                    holdProgress = (elapsed / duration).coerceIn(0f, 1f)
                    if (holdProgress >= 1f) {
                        isHolding = false
                        holdProgress = 0f
                        onUnlock()
                        break
                    }
                    delay(16L)
                }
            } else {
                holdProgress = 0f
            }
        }

        val clockColor = if (fontColors.isCustom) fontColors.primary else Color.White

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .pointerInput(Unit) {
                    // Absorb background taps so pocket touches do nothing
                    detectTapGestures { }
                }
                .safeDrawingPadding()
                .padding(24.dp)
                .testTag("dim_lock_overlay")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    text = currentTimeStr,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 54.sp,
                        lineHeight = 60.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.90f),
                            offset = Offset(0f, 4f),
                            blurRadius = 14f
                        )
                    ),
                    color = clockColor,
                    modifier = Modifier.testTag("dim_lock_clock")
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.dim_lock_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = accentColor.copy(alpha = 0.92f)
                )

                if (snapshot.hasActiveSession) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = snapshot.track.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = fontColors.primary.copy(alpha = 0.96f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = snapshot.track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = fontColors.secondary.copy(alpha = 0.82f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!activeLyricText.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    val dimLyricSp = (16f * settings.lyricsTextScale.coerceIn(0.75f, 1.50f)).sp
                    Text(
                        text = activeLyricText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = dimLyricSp,
                            lineHeight = (dimLyricSp.value * 1.28f).sp
                        ),
                        color = accentColor.copy(alpha = 0.94f),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }

                if (!gestureFeedback.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(accentColor.copy(alpha = 0.22f))
                            .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = gestureFeedback,
                            style = MaterialTheme.typography.labelLarge,
                            color = accentColor
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Text(
                    text = dynamicHints,
                    style = MaterialTheme.typography.labelSmall,
                    color = fontColors.tertiary.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center
                )

                // Hold 1.1s to unlock pill
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color(0xFF12151C))
                        .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(28.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isHolding = true
                                    tryAwaitRelease()
                                    isHolding = false
                                }
                            )
                        }
                        .padding(horizontal = 22.dp, vertical = 14.dp)
                        .testTag("hold_to_unlock_pill")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(26.dp)) {
                            Canvas(modifier = Modifier.size(26.dp)) {
                                drawArc(
                                    color = accentColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * holdProgress,
                                    useCenter = false,
                                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            Image(
                                painter = painterResource(R.drawable.ic_vec_unlock),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(accentColor),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = stringResource(R.string.hold_to_unlock),
                            style = MaterialTheme.typography.labelLarge,
                            color = fontColors.primary.copy(alpha = 0.92f)
                        )
                    }
                }
            }
        }
    }
}
