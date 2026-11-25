package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.audio.VisualizerFrame
import com.example.media.PlaybackStateBus
import com.example.model.AiRecommendation
import com.example.model.AppThemeStyle
import com.example.model.LocalAudioTrack
import com.example.model.LyricLine
import com.example.model.LyricsOrigin
import com.example.model.LyricsUiState
import com.example.model.PlaybackSnapshot
import com.example.model.PlaybackSource
import com.example.model.PlaybackStatsState
import com.example.model.QueueTrack
import com.example.model.RecommendationsUiState
import com.example.model.RepeatModeState
import com.example.model.SleepTimerMode
import com.example.model.SleepTimerState
import com.example.model.TrackArtworkState
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAppFontColors
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.ObsidianVoid
import com.example.ui.theme.contrastOnColor
import com.example.util.formatCountdownMs
import com.example.util.formatDurationMs
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun LeftLyricsAndArtCard(
    snapshot: PlaybackSnapshot,
    artwork: TrackArtworkState,
    lyricsState: LyricsUiState,
    accentColor: Color,
    themeStyle: AppThemeStyle = AppThemeStyle.ADAPTIVE_ART,
    cardBackground: Color,
    voidBackground: Color,
    glassBlurIntensity: Float,
    bassEnergy: Float,
    isCompactHeight: Boolean,
    lyricsTextScale: Float = 1.0f,
    onSeekTo: (Long) -> Unit,
    onNudgeOffset: (Long) -> Unit,
    onRetryLyrics: () -> Unit,
    onExportLyrics: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var lyricsViewportHeightPx by remember { mutableIntStateOf(0) }

    // 60fps real-time position interpolator using Choreographer frame nanos
    var smoothPositionMs by remember(artwork.trackKey) {
        mutableLongStateOf(PlaybackStateBus.interpolatedPositionMs(snapshot))
    }

    LaunchedEffect(
        snapshot.hasActiveSession,
        snapshot.isPlaying,
        snapshot.positionMs,
        snapshot.lastPositionUpdateElapsedMs,
        snapshot.playbackSpeed,
        snapshot.durationMs
    ) {
        if (!snapshot.hasActiveSession) {
            smoothPositionMs = 0L
            return@LaunchedEffect
        }
        if (!snapshot.isPlaying) {
            smoothPositionMs = PlaybackStateBus.interpolatedPositionMs(snapshot)
            return@LaunchedEffect
        }
        while (isActive) {
            withFrameNanos {
                smoothPositionMs = PlaybackStateBus.interpolatedPositionMs(snapshot)
            }
        }
    }

    val activeLineIndex by remember(lyricsState) {
        derivedStateOf {
            if (lyricsState is LyricsUiState.Ready && lyricsState.isSynced && lyricsState.lines.isNotEmpty()) {
                val adjusted = smoothPositionMs - lyricsState.vocalOffsetMs
                lyricsState.lines.indexOfLast { it.timestampMs <= adjusted }
            } else {
                -1
            }
        }
    }

    LaunchedEffect(activeLineIndex, lyricsViewportHeightPx) {
        if (activeLineIndex >= 0) {
            val offset = if (lyricsViewportHeightPx > 0) {
                -(lyricsViewportHeightPx * 0.22f).roundToInt()
            } else {
                0
            }
            listState.animateScrollToItem(
                index = activeLineIndex,
                scrollOffset = offset
            )
        }
    }

    val isFullScreenArtFallback = lyricsState is LyricsUiState.Empty
    val fontColors = LocalAppFontColors.current

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(if (themeStyle == AppThemeStyle.AMOLED_PURE_BLACK) Color.Black else cardBackground)
            .border(1.dp, HairlineBorder, RoundedCornerShape(28.dp))
    ) {
        BlurredArtBackdrop(
            trackKey = artwork.trackKey,
            bitmap = artwork.bitmap,
            imageBitmap = artwork.imageBitmap,
            accentColor = accentColor,
            themeStyle = themeStyle,
            cardBackground = cardBackground,
            voidBackground = voidBackground,
            glassBlurIntensity = glassBlurIntensity
        )

        AnimatedContent(
            targetState = isFullScreenArtFallback,
            transitionSpec = {
                fadeIn(animationSpec = tween(420)) togetherWith fadeOut(animationSpec = tween(320))
            },
            label = "leftCardMode"
        ) { fullArtMode ->
            if (fullArtMode) {
                FullScreenCoverFallback(
                    snapshot = snapshot,
                    artwork = artwork,
                    accentColor = accentColor,
                    isPlaying = snapshot.isPlaying
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Top Metadata Header
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BreathingAlbumArt(
                            trackKey = artwork.trackKey,
                            bitmap = artwork.bitmap,
                            imageBitmap = artwork.imageBitmap,
                            isPlaying = snapshot.isPlaying,
                            accentColor = accentColor,
                            size = if (isCompactHeight) 66.dp else 82.dp,
                            bassEnergy = bassEnergy
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AnimatedEqualizerWave(
                                    isPlaying = snapshot.isPlaying,
                                    accentColor = accentColor
                                )
                                Text(
                                    text = snapshot.track.sourceAppLabel.ifBlank {
                                        if (snapshot.source == PlaybackSource.LOCAL) "Local Audio" else "Active Media"
                                    }.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor
                                )
                            }

                            Text(
                                text = snapshot.track.title.ifBlank { "No Track Title" },
                                style = MaterialTheme.typography.headlineMedium,
                                color = fontColors.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = buildString {
                                    append(snapshot.track.artist.ifBlank { "Unknown Artist" })
                                    if (snapshot.track.album.isNotBlank()) {
                                        append(" • ")
                                        append(snapshot.track.album)
                                    }
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = fontColors.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Lyrics Header + Sync Badge + Offset Nudges
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Image(
                                painter = painterResource(
                                    if (lyricsState is LyricsUiState.Ready && lyricsState.origin == LyricsOrigin.AI_ENHANCED) {
                                        R.drawable.ic_vec_sparkles
                                    } else {
                                        R.drawable.ic_vec_lyrics
                                    }
                                ),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(accentColor),
                                modifier = Modifier.size(16.dp)
                            )
                            val badgeText = when (lyricsState) {
                                is LyricsUiState.Ready -> when (lyricsState.origin) {
                                    LyricsOrigin.AI_ENHANCED -> stringResource(R.string.lyrics_ai_badge)
                                    LyricsOrigin.ID3_EMBEDDED -> stringResource(R.string.lyrics_embedded_badge)
                                    LyricsOrigin.LRCLIB -> stringResource(R.string.lyrics_lrclib_badge)
                                    LyricsOrigin.KARALYR -> stringResource(R.string.lyrics_karalyr_badge)
                                    LyricsOrigin.LYRICS_OVH -> stringResource(R.string.lyrics_ovh_badge)
                                }
                                else -> "Lyrics"
                            }
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelMedium,
                                color = accentColor
                            )
                            if (lyricsState is LyricsUiState.Ready && !lyricsState.aiNote.isNullOrBlank()) {
                                Text(
                                    text = "• ${lyricsState.aiNote}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fontColors.secondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (lyricsState is LyricsUiState.Ready) {
                                IconButton(
                                    onClick = {
                                        val title = snapshot.track.title.ifBlank { "Untitled" }.replace(Regex("[^A-Za-z0-9-_ ]"), "").trim()
                                        val artist = snapshot.track.artist.ifBlank { "Unknown Artist" }.replace(Regex("[^A-Za-z0-9-_ ]"), "").trim()
                                        onExportLyrics("lyrics_${title}_${artist}.lrc")
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("export_lyrics_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = "Save Lyrics",
                                        tint = accentColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            if (lyricsState is LyricsUiState.Ready && lyricsState.isSynced) {
                                OffsetNudgeChip(
                                    label = "-.25s",
                                    onClick = { onNudgeOffset(-250L) },
                                    tag = "lyrics_offset_minus"
                                )
                                Text(
                                    text = "${if (lyricsState.vocalOffsetMs >= 0) "+" else ""}${lyricsState.vocalOffsetMs}ms",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = fontColors.secondary
                                )
                                OffsetNudgeChip(
                                    label = "+.25s",
                                    onClick = { onNudgeOffset(250L) },
                                    tag = "lyrics_offset_plus"
                                )
                            }
                        }
                    }

                    // Synced Apple Music-Style Lyrics Body
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .onSizeChanged { lyricsViewportHeightPx = it.height }
                    ) {
                        when (lyricsState) {
                            LyricsUiState.Idle -> {
                                CenterHint(text = stringResource(R.string.waiting_for_playback_desc))
                            }
                            LyricsUiState.Loading, LyricsUiState.AiAligning -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    CircularProgressIndicator(
                                        color = accentColor,
                                        modifier = Modifier.size(28.dp),
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = stringResource(
                                            if (lyricsState is LyricsUiState.AiAligning) R.string.lyrics_ai_syncing
                                            else R.string.lyrics_loading
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = fontColors.secondary
                                    )
                                }
                            }
                            is LyricsUiState.Empty -> {
                                CenterHint(text = stringResource(R.string.lyrics_unavailable_all_sources))
                            }
                            is LyricsUiState.Error -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Text(
                                        text = lyricsState.message,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = fontColors.secondary
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(onClick = onRetryLyrics) {
                                        Text(stringResource(R.string.lyrics_retry), color = accentColor)
                                    }
                                }
                            }
                            is LyricsUiState.Ready -> {
                                LazyColumn(
                                    state = listState,
                                    contentPadding = PaddingValues(vertical = 20.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("lyrics_list")
                                ) {
                                    itemsIndexed(lyricsState.lines) { idx, line ->
                                        val isActive = idx == activeLineIndex
                                        AppleMusicLyricRow(
                                            line = line,
                                            isActive = isActive,
                                            isSynced = lyricsState.isSynced,
                                            isPast = idx < activeLineIndex,
                                            vocalOffsetMs = lyricsState.vocalOffsetMs,
                                            lyricsTextScale = lyricsTextScale,
                                            accentColor = accentColor,
                                            primaryTextColor = fontColors.primary,
                                            positionMsProvider = { smoothPositionMs },
                                            onClick = {
                                                onSeekTo((line.timestampMs + lyricsState.vocalOffsetMs).coerceAtLeast(0L))
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppleMusicLyricRow(
    line: LyricLine,
    isActive: Boolean,
    isSynced: Boolean,
    isPast: Boolean,
    vocalOffsetMs: Long,
    lyricsTextScale: Float,
    accentColor: Color,
    primaryTextColor: Color,
    positionMsProvider: () -> Long,
    onClick: () -> Unit
) {
    val clampedScale = lyricsTextScale.coerceIn(0.75f, 1.65f)
    val activeFontSp = (20f * clampedScale).sp
    val inactiveFontSp = (17f * clampedScale).sp
    val activeLineHeightSp = (activeFontSp.value * 1.28f).sp
    val inactiveLineHeightSp = (inactiveFontSp.value * 1.28f).sp
    val wordGapDp = (6f * clampedScale.coerceIn(0.85f, 1.35f)).dp

    val lineScale by animateFloatAsState(
        targetValue = if (isActive) 1.06f else 1.0f,
        animationSpec = spring(
            dampingRatio = 0.78f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "appleLyricScale"
    )
    val inactiveColor by animateColorAsState(
        targetValue = when {
            !isSynced -> primaryTextColor.copy(alpha = 0.86f)
            isPast -> primaryTextColor.copy(alpha = 0.42f)
            else -> primaryTextColor.copy(alpha = 0.45f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "appleLyricAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 6.dp)
            .graphicsLayer {
                scaleX = lineScale
                scaleY = lineScale
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) accentColor.copy(alpha = 0.12f) else Color.Transparent)
            .clickable(enabled = isSynced, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        if (isActive && isSynced && line.words.isNotEmpty()) {
            val adjustedPosMs = positionMsProvider() - vocalOffsetMs
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(wordGapDp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                line.words.forEach { word ->
                    val wordDuration = (word.endMs - word.startMs).coerceAtLeast(1L).toFloat()
                    val progress = ((adjustedPosMs - word.startMs).toFloat() / wordDuration).coerceIn(0f, 1f)
                    val wordBrush = when {
                        progress >= 1f -> Brush.horizontalGradient(listOf(primaryTextColor, primaryTextColor))
                        progress <= 0f -> Brush.horizontalGradient(
                            listOf(primaryTextColor.copy(alpha = 0.42f), primaryTextColor.copy(alpha = 0.42f))
                        )
                        else -> {
                            val fadeEnd = (progress + 0.16f).coerceAtMost(1f)
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0f to primaryTextColor,
                                    progress to primaryTextColor,
                                    fadeEnd to primaryTextColor.copy(alpha = 0.42f),
                                    1f to primaryTextColor.copy(alpha = 0.42f)
                                )
                            )
                        }
                    }

                    Text(
                        text = word.text,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = activeFontSp,
                            lineHeight = activeLineHeightSp,
                            fontWeight = FontWeight.ExtraBold,
                            brush = wordBrush,
                            shadow = if (progress > 0.05f) {
                                Shadow(
                                    color = accentColor.copy(alpha = 0.52f * progress),
                                    offset = Offset.Zero,
                                    blurRadius = 14f
                                )
                            } else {
                                null
                            }
                        )
                    )
                }
            }
        } else {
            Text(
                text = line.text,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = if (isActive) activeFontSp else inactiveFontSp,
                    lineHeight = if (isActive) activeLineHeightSp else inactiveLineHeightSp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = if (isActive) primaryTextColor else inactiveColor
            )
        }
    }
}

@Composable
private fun FullScreenCoverFallback(
    snapshot: PlaybackSnapshot,
    artwork: TrackArtworkState,
    accentColor: Color,
    isPlaying: Boolean
) {
    val fontColors = LocalAppFontColors.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("full_screen_cover_fallback")
    ) {
        StableFullScreenArtContent(
            trackKey = artwork.trackKey,
            imageBitmap = artwork.imageBitmap,
            fallbackBitmap = artwork.bitmap,
            accentColor = accentColor
        )

        // Subtle Bottom Gradient Scrim + Metadata + Exact Unavailable Footer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.48f to Color.Transparent,
                            0.74f to Color.Black.copy(alpha = 0.62f),
                            1.0f to Color.Black.copy(alpha = 0.92f)
                        )
                    )
                )
        )

        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnimatedEqualizerWave(isPlaying = isPlaying, accentColor = accentColor)
                Text(
                    text = snapshot.track.sourceAppLabel.ifBlank {
                        if (snapshot.source == PlaybackSource.LOCAL) "Local Audio" else "Active Media"
                    }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
            }

            Text(
                text = snapshot.track.title.ifBlank { "No Track Title" },
                style = MaterialTheme.typography.headlineMedium,
                color = fontColors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = snapshot.track.artist.ifBlank { "Unknown Artist" },
                style = MaterialTheme.typography.bodyMedium,
                color = fontColors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = stringResource(R.string.lyrics_unavailable_all_sources),
                style = MaterialTheme.typography.labelSmall,
                color = fontColors.secondary.copy(alpha = 0.85f),
                modifier = Modifier.testTag("lyrics_unavailable_footer")
            )
        }
    }
}

@Composable
private fun StableFullScreenArtContent(
    trackKey: String,
    imageBitmap: ImageBitmap?,
    fallbackBitmap: android.graphics.Bitmap?,
    accentColor: Color
) {
    val cachedImages = remember { mutableStateMapOf<String, ImageBitmap?>() }
    val resolvedCurrent = remember(trackKey, imageBitmap, fallbackBitmap) {
        imageBitmap ?: fallbackBitmap?.takeUnless { it.isRecycled }?.let {
            runCatching { it.asImageBitmap() }.getOrNull()
        }
    }
    if (trackKey.isNotEmpty() && (!cachedImages.containsKey(trackKey) || (cachedImages[trackKey] == null && resolvedCurrent != null))) {
        if (cachedImages.size > 3) {
            cachedImages.keys.filter { it != trackKey }.take(cachedImages.size - 2).forEach { cachedImages.remove(it) }
        }
        cachedImages[trackKey] = resolvedCurrent
    }

    androidx.compose.animation.Crossfade(
        targetState = trackKey,
        animationSpec = tween(420),
        label = "fullScreenArtCrossfade"
    ) { targetKey ->
        val img = if (targetKey == trackKey) (resolvedCurrent ?: cachedImages[targetKey]) else cachedImages[targetKey]
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = stringResource(R.string.cd_album_art),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.35f), ObsidianVoid)
                        )
                    )
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_vec_music_note),
                    contentDescription = stringResource(R.string.cd_album_art),
                    colorFilter = ColorFilter.tint(accentColor),
                    modifier = Modifier.size(96.dp)
                )
            }
        }
    }
}

@Composable
private fun OffsetNudgeChip(label: String, onClick: () -> Unit, tag: String) {
    val fontColors = LocalAppFontColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(ObsidianCardElevated.copy(alpha = 0.85f))
            .border(1.dp, HairlineBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .testTag(tag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = fontColors.primary
        )
    }
}

@Composable
private fun CenterHint(text: String) {
    val fontColors = LocalAppFontColors.current
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = fontColors.secondary
        )
    }
}

@Composable
fun RightControlsAndQueueCard(
    snapshot: PlaybackSnapshot,
    livePositionMs: Long,
    recommendationsState: RecommendationsUiState,
    visualizerFrame: VisualizerFrame,
    mediaVolume: Pair<Int, Int>,
    sleepTimerState: SleepTimerState,
    playbackStats: PlaybackStatsState,
    openUpNextSignal: Int,
    localFallbackTracks: List<LocalAudioTrack>,
    accentColor: Color,
    secondaryAccent: Color,
    themeStyle: AppThemeStyle = AppThemeStyle.ADAPTIVE_ART,
    cardBackground: Color,
    voidBackground: Color,
    glassBlurIntensity: Float,
    onRequestVisualizerPermission: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onTriggerSongRadio: () -> Unit,
    onSetVolume: (Int) -> Unit,
    onStepVolumeUp: () -> Unit,
    onStepVolumeDown: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onSliderHapticTick: () -> Unit,
    onUpNextTabChanged: (Boolean) -> Unit,
    onSkipToQueueItem: (QueueTrack) -> Unit,
    onRetryRecommendations: () -> Unit,
    onRecommendationClick: (AiRecommendation) -> Unit,
    onPlayLocalFallbackTrack: (List<LocalAudioTrack>, Int) -> Unit,
    onResetStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontColors = LocalAppFontColors.current
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        onUpNextTabChanged(pagerState.currentPage == 1)
    }

    LaunchedEffect(openUpNextSignal) {
        if (openUpNextSignal > 0) {
            pagerState.animateScrollToPage(1)
        }
    }

    val effectiveCardBg = if (themeStyle == AppThemeStyle.AMOLED_PURE_BLACK) {
        Color.Black
    } else {
        val glassCardAlpha = (0.94f - glassBlurIntensity * 0.16f).coerceIn(0.76f, 0.96f)
        cardBackground.copy(alpha = glassCardAlpha)
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(effectiveCardBg)
            .border(1.dp, HairlineBorder, RoundedCornerShape(28.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Card Page Switcher (Controls <-> Up Next <-> Stats)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(voidBackground.copy(alpha = 0.72f))
                .padding(4.dp)
        ) {
            val pages = listOf(
                stringResource(R.string.tab_controls),
                stringResource(R.string.tab_up_next),
                stringResource(R.string.tab_stats)
            )
            pages.forEachIndexed { idx, label ->
                val selected = pagerState.currentPage == idx
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) accentColor.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable {
                            scope.launch { pagerState.animateScrollToPage(idx) }
                        }
                        .padding(vertical = 8.dp)
                        .testTag("right_card_tab_$idx")
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) accentColor else fontColors.secondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("controls_queue_pager")
        ) { page ->
            when (page) {
                0 -> ControlsPage(
                    snapshot = snapshot,
                    livePositionMs = livePositionMs,
                    visualizerFrame = visualizerFrame,
                    mediaVolume = mediaVolume,
                    sleepTimerState = sleepTimerState,
                    accentColor = accentColor,
                    secondaryAccent = secondaryAccent,
                    onRequestVisualizerPermission = onRequestVisualizerPermission,
                    onPlayPause = onPlayPause,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onSeekTo = onSeekTo,
                    onToggleShuffle = onToggleShuffle,
                    onCycleRepeat = onCycleRepeat,
                    onTriggerSongRadio = onTriggerSongRadio,
                    onSetVolume = onSetVolume,
                    onStepVolumeUp = onStepVolumeUp,
                    onStepVolumeDown = onStepVolumeDown,
                    onOpenSleepTimer = onOpenSleepTimer,
                    onSliderHapticTick = onSliderHapticTick,
                    onOpenQueue = {
                        scope.launch { pagerState.animateScrollToPage(1) }
                    }
                )
                1 -> UpNextQueuePage(
                    snapshot = snapshot,
                    recommendationsState = recommendationsState,
                    localFallbackTracks = localFallbackTracks,
                    accentColor = accentColor,
                    onSkipToQueueItem = onSkipToQueueItem,
                    onRetryRecommendations = onRetryRecommendations,
                    onRecommendationClick = onRecommendationClick,
                    onPlayLocalFallbackTrack = onPlayLocalFallbackTrack
                )
                else -> DashboardStatsPanel(
                    stats = playbackStats,
                    accentColor = accentColor,
                    secondaryAccent = secondaryAccent,
                    themeStyle = themeStyle,
                    onResetStats = onResetStats
                )
            }
        }
    }
}

@Composable
private fun ControlsPage(
    snapshot: PlaybackSnapshot,
    livePositionMs: Long,
    visualizerFrame: VisualizerFrame,
    mediaVolume: Pair<Int, Int>,
    sleepTimerState: SleepTimerState,
    accentColor: Color,
    secondaryAccent: Color,
    onRequestVisualizerPermission: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onTriggerSongRadio: () -> Unit,
    onSetVolume: (Int) -> Unit,
    onStepVolumeUp: () -> Unit,
    onStepVolumeDown: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onSliderHapticTick: () -> Unit,
    onOpenQueue: () -> Unit
) {
    val fontColors = LocalAppFontColors.current
    val duration = snapshot.durationMs.coerceAtLeast(1L)
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }

    val sliderValue = if (isScrubbing) {
        scrubPosition
    } else {
        livePositionMs.toFloat().coerceIn(0f, duration.toFloat())
    }

    val (currVol, maxVol) = mediaVolume
    val volMaxFloat = maxVol.coerceAtLeast(1).toFloat()
    val volPercent = ((currVol * 100f) / volMaxFloat).roundToInt().coerceIn(0, 100)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 2.dp)
    ) {
        val isTight = maxHeight < 310.dp
        val vizHeight = if (isTight) 28.dp else 44.dp
        val playSize = if (isTight) 58.dp else 76.dp
        val playIconSize = if (isTight) 26.dp else 34.dp
        val skipSize = if (isTight) 48.dp else 58.dp
        val skipIconSize = if (isTight) 22.dp else 26.dp

        Column(
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            // Real-Time Audio Waveform & Spectrum Visualizer
            RealTimeWaveformVisualizer(
                frame = visualizerFrame,
                isPlaying = snapshot.isPlaying,
                accentColor = accentColor,
                secondaryAccent = secondaryAccent,
                onRequestPermission = onRequestVisualizerPermission,
                visualizerHeight = vizHeight
            )

            // Track Seek Bar + Timestamps
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        isScrubbing = true
                        scrubPosition = it
                    },
                    onValueChangeFinished = {
                        isScrubbing = false
                        onSeekTo(scrubPosition.toLong())
                    },
                    valueRange = 0f..duration.toFloat(),
                    enabled = snapshot.hasActiveSession && snapshot.durationMs > 0L,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (isTight) 32.dp else 40.dp)
                        .testTag("playback_seek_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = ObsidianCardElevated
                    )
                )
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                ) {
                    Text(
                        text = formatDurationMs(sliderValue.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = fontColors.secondary
                    )
                    Text(
                        text = formatDurationMs(snapshot.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = fontColors.secondary
                    )
                }
            }

            // Primary Transport Controls
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpringTransportButton(
                    iconRes = R.drawable.ic_vec_previous,
                    contentDesc = stringResource(R.string.cd_previous),
                    containerColor = ObsidianCardElevated,
                    tintColor = fontColors.primary,
                    size = skipSize,
                    iconSize = skipIconSize,
                    onClick = onPrevious,
                    testTag = "btn_previous"
                )

                SpringTransportButton(
                    iconRes = if (snapshot.isPlaying) R.drawable.ic_vec_pause else R.drawable.ic_vec_play,
                    contentDesc = stringResource(R.string.cd_play_pause),
                    containerColor = accentColor,
                    tintColor = contrastOnColor(accentColor),
                    size = playSize,
                    iconSize = playIconSize,
                    onClick = onPlayPause,
                    testTag = "btn_play_pause"
                )

                SpringTransportButton(
                    iconRes = R.drawable.ic_vec_next,
                    contentDesc = stringResource(R.string.cd_next),
                    containerColor = ObsidianCardElevated,
                    tintColor = fontColors.primary,
                    size = skipSize,
                    iconSize = skipIconSize,
                    onClick = onNext,
                    testTag = "btn_next"
                )
            }

            // On-Screen Media Volume Control Bar (Unlocked Mode)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianVoid.copy(alpha = 0.48f))
                    .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(if (isTight) 36.dp else 40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onStepVolumeDown)
                        .testTag("btn_volume_down")
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_vec_volume_down),
                        contentDescription = stringResource(R.string.cd_volume_down),
                        colorFilter = ColorFilter.tint(fontColors.secondary),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Slider(
                    value = currVol.toFloat().coerceIn(0f, volMaxFloat),
                    onValueChange = {
                        val newVol = it.roundToInt()
                        if (newVol != currVol) {
                            onSliderHapticTick()
                            onSetVolume(newVol)
                        }
                    },
                    valueRange = 0f..volMaxFloat,
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isTight) 28.dp else 34.dp)
                        .testTag("playback_volume_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = ObsidianCardElevated
                    )
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(if (isTight) 36.dp else 40.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onStepVolumeUp)
                        .testTag("btn_volume_up")
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_vec_volume_up),
                        contentDescription = stringResource(R.string.cd_volume_up),
                        colorFilter = ColorFilter.tint(accentColor),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = "$volPercent%",
                    style = MaterialTheme.typography.labelSmall,
                    color = fontColors.secondary,
                    modifier = Modifier.width(34.dp)
                )
            }

            // Secondary Row: Shuffle + Repeat (Local) + Song Radio + Sleep Timer + Up Next Hint
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (snapshot.shuffleEnabled) accentColor.copy(alpha = 0.2f) else ObsidianCardElevated
                            )
                            .clickable(onClick = onToggleShuffle)
                            .testTag("btn_shuffle")
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_vec_shuffle),
                            contentDescription = stringResource(R.string.cd_shuffle),
                            colorFilter = ColorFilter.tint(
                                if (snapshot.shuffleEnabled) accentColor else fontColors.secondary
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (snapshot.source == PlaybackSource.LOCAL) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (snapshot.repeatMode != RepeatModeState.OFF) accentColor.copy(alpha = 0.2f)
                                    else ObsidianCardElevated
                                )
                                .clickable(onClick = onCycleRepeat)
                                .testTag("btn_repeat")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(R.drawable.ic_vec_repeat),
                                    contentDescription = stringResource(R.string.cd_repeat),
                                    colorFilter = ColorFilter.tint(
                                        if (snapshot.repeatMode != RepeatModeState.OFF) accentColor else fontColors.secondary
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                if (snapshot.repeatMode == RepeatModeState.ONE) {
                                    Text(
                                        text = "1",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = accentColor
                                    )
                                }
                            }
                        }
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.16f))
                            .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape)
                            .clickable(onClick = onTriggerSongRadio)
                            .testTag("btn_song_radio")
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_vec_radio),
                            contentDescription = stringResource(R.string.cd_song_radio),
                            colorFilter = ColorFilter.tint(accentColor),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(24.dp))
                            .background(
                                if (sleepTimerState.isActive) accentColor.copy(alpha = 0.22f)
                                else ObsidianCardElevated
                            )
                            .border(
                                1.dp,
                                if (sleepTimerState.isActive) accentColor.copy(alpha = 0.6f) else HairlineBorder,
                                RoundedCornerShape(24.dp)
                            )
                            .clickable(onClick = onOpenSleepTimer)
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                            .testTag("btn_sleep_timer")
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_vec_timer),
                            contentDescription = stringResource(R.string.cd_sleep_timer),
                            colorFilter = ColorFilter.tint(
                                if (sleepTimerState.isActive) accentColor else fontColors.secondary
                            ),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = when {
                                !sleepTimerState.isActive -> stringResource(R.string.sleep_timer_label)
                                sleepTimerState.mode == SleepTimerMode.END_OF_TRACK -> "End"
                                else -> formatCountdownMs(sleepTimerState.remainingMs)
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (sleepTimerState.isActive) accentColor else fontColors.secondary,
                            fontWeight = if (sleepTimerState.isActive) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenQueue)
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .testTag("swipe_queue_hint")
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_vec_queue),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(accentColor),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Up Next →",
                        style = MaterialTheme.typography.labelMedium,
                        color = fontColors.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun SpringTransportButton(
    iconRes: Int,
    contentDesc: String,
    containerColor: Color,
    tintColor: Color,
    size: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    testTag: String
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "transportScale"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(color = tintColor),
                onClick = onClick
            )
            .testTag(testTag)
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDesc,
            colorFilter = ColorFilter.tint(tintColor),
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun UpNextQueuePage(
    snapshot: PlaybackSnapshot,
    recommendationsState: RecommendationsUiState,
    localFallbackTracks: List<LocalAudioTrack>,
    accentColor: Color,
    onSkipToQueueItem: (QueueTrack) -> Unit,
    onRetryRecommendations: () -> Unit,
    onRecommendationClick: (AiRecommendation) -> Unit,
    onPlayLocalFallbackTrack: (List<LocalAudioTrack>, Int) -> Unit
) {
    val fontColors = LocalAppFontColors.current

    if (snapshot.queue.isNotEmpty()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("live_queue_list"),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            itemsIndexed(snapshot.queue) { idx, item ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (item.isCurrent) accentColor.copy(alpha = 0.16f)
                            else ObsidianCardElevated.copy(alpha = 0.72f)
                        )
                        .border(
                            1.dp,
                            if (item.isNext || item.isCurrent) accentColor.copy(alpha = 0.4f) else HairlineBorder,
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { onSkipToQueueItem(item) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("queue_item_$idx")
                ) {
                    Text(
                        text = "${idx + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.isCurrent || item.isNext) accentColor else fontColors.tertiary,
                        modifier = Modifier.width(22.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        if (item.isNext) {
                            Text(
                                text = stringResource(R.string.playing_next_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = accentColor
                            )
                        }
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = fontColors.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = fontColors.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (item.durationMs > 0L) {
                        Text(
                            text = formatDurationMs(item.durationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = fontColors.secondary
                        )
                    }
                }
            }
        }
    } else {
        // External player queue empty -> Gemini Flash Recommendations or clean local fallback
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag("ai_recommendations_panel"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_vec_sparkles),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(accentColor),
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.recommended_next_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = fontColors.primary
                    )
                }
                val rightBadge = when (recommendationsState) {
                    is RecommendationsUiState.Fallback -> recommendationsState.badge
                    is RecommendationsUiState.Error -> "Standard Queue Mode"
                    else -> stringResource(R.string.recommended_by_ai)
                }
                Text(
                    text = rightBadge,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
            }

            when (recommendationsState) {
                RecommendationsUiState.Idle -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Text(
                            text = stringResource(R.string.queue_empty_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = fontColors.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.queue_empty_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = fontColors.secondary
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = onRetryRecommendations,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = contrastOnColor(accentColor)
                            )
                        ) {
                            Text(stringResource(R.string.generate_suggestions))
                        }
                    }
                }
                RecommendationsUiState.Loading -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(color = accentColor, modifier = Modifier.size(28.dp))
                    }
                }
                is RecommendationsUiState.Fallback, is RecommendationsUiState.Error -> {
                    if (localFallbackTracks.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Local Playlist Ready",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = fontColors.secondary
                                    )
                                    OutlinedButton(onClick = onRetryRecommendations) {
                                        Text(stringResource(R.string.generate_suggestions), color = accentColor)
                                    }
                                }
                            }
                            itemsIndexed(localFallbackTracks.take(12)) { idx, localTrack ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(ObsidianCardElevated.copy(alpha = 0.72f))
                                        .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                                        .clickable { onPlayLocalFallbackTrack(localFallbackTracks, idx) }
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = "${idx + 1}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = accentColor,
                                        modifier = Modifier.width(22.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = localTrack.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = fontColors.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = localTrack.artist,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = fontColors.secondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = formatDurationMs(localTrack.durationMs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = fontColors.secondary
                                    )
                                }
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = "Standard Queue Mode",
                                style = MaterialTheme.typography.titleMedium,
                                color = fontColors.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "External player queue is empty. Retry suggestions or switch to Local Device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = fontColors.secondary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(onClick = onRetryRecommendations) {
                                Text(stringResource(R.string.generate_suggestions), color = accentColor)
                            }
                        }
                    }
                }
                is RecommendationsUiState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(recommendationsState.items) { idx, rec ->
                            val swatchColor = remember(rec.accentHex) {
                                runCatching { Color(android.graphics.Color.parseColor(rec.accentHex)) }
                                    .getOrDefault(accentColor)
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(ObsidianCardElevated.copy(alpha = 0.72f))
                                    .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                                    .clickable { onRecommendationClick(rec) }
                                    .padding(10.dp)
                                    .testTag("ai_rec_item_$idx")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(swatchColor.copy(alpha = 0.22f))
                                        .border(1.dp, swatchColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                                ) {
                                    Image(
                                        painter = painterResource(R.drawable.ic_vec_music_note),
                                        contentDescription = null,
                                        colorFilter = ColorFilter.tint(swatchColor),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = rec.title,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = fontColors.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Text(
                                            text = rec.vibeTag.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = swatchColor
                                        )
                                    }
                                    Text(
                                        text = "${rec.artist} • ${rec.reason}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = fontColors.secondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
