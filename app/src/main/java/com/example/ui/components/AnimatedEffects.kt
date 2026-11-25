package com.example.ui.components

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.audio.VisualizerFrame
import com.example.model.AppThemeStyle
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAppFontColors
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianVoid

@Composable
fun DynamicAmbientBackground(
    primaryAccent: Color,
    secondaryAccent: Color,
    isPlaying: Boolean,
    themeStyle: AppThemeStyle = AppThemeStyle.ADAPTIVE_ART,
    voidBackground: Color = ObsidianVoid,
    glassIntensity: Float = 0.65f,
    modifier: Modifier = Modifier
) {
    // Pure AMOLED Black enforces pure #000000 across the entire background with zero ambient tint.
    if (themeStyle == AppThemeStyle.AMOLED_PURE_BLACK) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }

    val isDynamicArtTheme = themeStyle == AppThemeStyle.ADAPTIVE_ART
    val transition = rememberInfiniteTransition(label = "ambientPulse")
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientDrift"
    )

    val activeDrift = if (isDynamicArtTheme) drift else 0.25f
    val baseAlpha = when {
        !isDynamicArtTheme -> 0.14f
        isPlaying -> 0.24f
        else -> 0.11f
    }
    val scaledAlpha = (baseAlpha * (0.45f + glassIntensity * 0.75f)).coerceIn(0.03f, 0.35f)

    val glowAlpha by animateFloatAsState(
        targetValue = scaledAlpha,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "glowAlpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(voidBackground)
            .drawBehind {
                val center1 = Offset(
                    x = size.width * (0.18f + 0.12f * activeDrift),
                    y = size.height * (0.25f + 0.10f * (1f - activeDrift))
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryAccent.copy(alpha = glowAlpha), Color.Transparent),
                        center = center1,
                        radius = size.maxDimension * 0.65f
                    ),
                    center = center1,
                    radius = size.maxDimension * 0.65f
                )

                val center2 = Offset(
                    x = size.width * (0.82f - 0.10f * activeDrift),
                    y = size.height * (0.78f - 0.12f * activeDrift)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryAccent.copy(alpha = glowAlpha * 0.78f), Color.Transparent),
                        center = center2,
                        radius = size.maxDimension * 0.55f
                    ),
                    center = center2,
                    radius = size.maxDimension * 0.55f
                )
            }
    )
}

@Composable
fun BlurredArtBackdrop(
    trackKey: String,
    bitmap: Bitmap?,
    imageBitmap: ImageBitmap? = null,
    accentColor: Color,
    themeStyle: AppThemeStyle = AppThemeStyle.ADAPTIVE_ART,
    cardBackground: Color = ObsidianCard,
    voidBackground: Color = ObsidianVoid,
    glassBlurIntensity: Float = 0.65f,
    modifier: Modifier = Modifier
) {
    // Only render the dynamic blurred album-art backdrop when ADAPTIVE_ART is selected.
    if (themeStyle == AppThemeStyle.AMOLED_PURE_BLACK) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
        )
        return
    }
    if (themeStyle != AppThemeStyle.ADAPTIVE_ART) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            cardBackground,
                            voidBackground
                        )
                    )
                )
        )
        return
    }

    val blurDp = (6f + (glassBlurIntensity * 42f)).dp
    val scrimTopAlpha = (0.86f - (glassBlurIntensity * 0.22f)).coerceIn(0.55f, 0.92f)
    val scrimBottomAlpha = (0.94f - (glassBlurIntensity * 0.10f)).coerceIn(0.78f, 0.96f)
    val legacyAlpha = (0.20f + glassBlurIntensity * 0.25f).coerceIn(0.15f, 0.45f)

    Box(modifier = modifier.fillMaxSize()) {
        StableBackdropArtworkLayer(
            trackKey = trackKey,
            imageBitmap = imageBitmap,
            fallbackBitmap = bitmap,
            blurDp = blurDp,
            legacyAlpha = legacyAlpha
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            cardBackground.copy(alpha = scrimTopAlpha),
                            accentColor.copy(alpha = 0.06f + glassBlurIntensity * 0.08f),
                            voidBackground.copy(alpha = scrimBottomAlpha)
                        )
                    )
                )
        )
    }
}

@Composable
private fun StableBackdropArtworkLayer(
    trackKey: String,
    imageBitmap: ImageBitmap?,
    fallbackBitmap: Bitmap?,
    blurDp: Dp,
    legacyAlpha: Float
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

    Crossfade(targetState = trackKey, animationSpec = tween(520), label = "artBackdrop") { key ->
        val img = if (key == trackKey) (resolvedCurrent ?: cachedImages[key]) else cachedImages[key]
        if (img != null) {
            val blurModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.blur(blurDp)
            } else {
                Modifier.alpha(legacyAlpha)
            }
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.18f)
                    .then(blurModifier)
            )
        }
    }
}

@Composable
fun BreathingAlbumArt(
    trackKey: String,
    bitmap: Bitmap?,
    imageBitmap: ImageBitmap? = null,
    isPlaying: Boolean,
    accentColor: Color,
    size: Dp,
    bassEnergy: Float = 0f,
    modifier: Modifier = Modifier
) {
    val playScale by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "albumArtScale"
    )

    val bassPulse by animateFloatAsState(
        targetValue = if (isPlaying) (bassEnergy * 0.045f) else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "bassPulse"
    )

    val transition = rememberInfiniteTransition(label = "artHalo")
    val haloPulse by transition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "haloPulse"
    )

    val finalScale = if (isPlaying) (playScale * haloPulse) + bassPulse else playScale

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .scale(finalScale)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.28f),
                        MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            )
            .border(1.dp, accentColor.copy(alpha = if (isPlaying) 0.45f else 0.18f), RoundedCornerShape(20.dp))
    ) {
        StableCoverArtworkContent(
            trackKey = trackKey,
            imageBitmap = imageBitmap,
            fallbackBitmap = bitmap,
            accentColor = accentColor,
            placeholderIconSize = size * 0.44f
        )
    }
}

@Composable
private fun StableCoverArtworkContent(
    trackKey: String,
    imageBitmap: ImageBitmap?,
    fallbackBitmap: Bitmap?,
    accentColor: Color,
    placeholderIconSize: Dp
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

    Crossfade(targetState = trackKey, animationSpec = tween(420), label = "coverArt") { key ->
        val img = if (key == trackKey) (resolvedCurrent ?: cachedImages[key]) else cachedImages[key]
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = stringResource(R.string.cd_album_art),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(R.drawable.ic_vec_music_note),
                contentDescription = stringResource(R.string.cd_album_art),
                colorFilter = ColorFilter.tint(accentColor),
                modifier = Modifier.size(placeholderIconSize)
            )
        }
    }
}

@Composable
fun AnimatedEqualizerWave(
    isPlaying: Boolean,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "eqWave")
    val durations = listOf(460, 620, 510, 690, 540)
    val minFractions = listOf(0.24f, 0.35f, 0.20f, 0.42f, 0.28f)
    val maxFractions = listOf(0.95f, 0.80f, 1.0f, 0.72f, 0.88f)

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = modifier.height(18.dp)
    ) {
        durations.forEachIndexed { idx, dur ->
            val animatedFrac by transition.animateFloat(
                initialValue = minFractions[idx],
                targetValue = maxFractions[idx],
                animationSpec = infiniteRepeatable(
                    animation = tween(dur, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar$idx"
            )
            val barHeight by animateFloatAsState(
                targetValue = if (isPlaying) animatedFrac else 0.22f,
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "barHeight$idx"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((18f * barHeight).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor)
            )
        }
    }
}

@Composable
fun RealTimeWaveformVisualizer(
    frame: VisualizerFrame,
    isPlaying: Boolean,
    accentColor: Color,
    secondaryAccent: Color,
    onRequestPermission: () -> Unit,
    visualizerHeight: Dp = 48.dp,
    modifier: Modifier = Modifier
) {
    val fontColors = LocalAppFontColors.current
    val smoothedBass by animateFloatAsState(
        targetValue = if (isPlaying) frame.bassEnergy else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "vizBass"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.58f))
            .border(
                1.dp,
                if (frame.isHardwareActive && isPlaying) {
                    accentColor.copy(alpha = 0.35f + smoothedBass * 0.3f)
                } else {
                    HairlineBorder
                },
                RoundedCornerShape(16.dp)
            )
            .then(
                if (!frame.hasPermission) {
                    Modifier.clickable(onClick = onRequestPermission)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("realtime_waveform_visualizer"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
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
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                !frame.hasPermission -> secondaryAccent
                                frame.isHardwareActive && isPlaying -> accentColor
                                else -> fontColors.tertiary
                            }
                        )
                )
                Text(
                    text = when {
                        !frame.hasPermission -> stringResource(R.string.visualizer_enable_cta)
                        frame.errorMessage != null -> frame.errorMessage
                        frame.isHardwareActive && isPlaying -> stringResource(R.string.visualizer_live_badge)
                        else -> stringResource(R.string.visualizer_paused_badge)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (!frame.hasPermission || (frame.isHardwareActive && isPlaying)) {
                        accentColor
                    } else {
                        fontColors.secondary
                    }
                )
            }

            if (!frame.hasPermission) {
                Text(
                    text = "ENABLE",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    modifier = Modifier.testTag("enable_visualizer_button")
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(visualizerHeight)
        ) {
            val midY = size.height / 2f
            val bars = frame.spectrumBars
            if (bars.isNotEmpty() && frame.isHardwareActive && isPlaying) {
                val barSlot = size.width / bars.size
                val barW = (barSlot * 0.42f).coerceAtLeast(2f)
                bars.forEachIndexed { idx, mag ->
                    val clamped = mag.coerceIn(0.05f, 1f)
                    val barH = clamped * size.height * 0.85f
                    val left = idx * barSlot + (barSlot - barW) / 2f
                    drawRoundRect(
                        color = secondaryAccent.copy(alpha = 0.24f + clamped * 0.22f),
                        topLeft = Offset(left, midY - barH / 2f),
                        size = Size(barW, barH),
                        cornerRadius = CornerRadius(barW / 2f, barW / 2f)
                    )
                }
            }

            // Center baseline
            drawLine(
                color = HairlineBorder,
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = 1.dp.toPx()
            )

            // Smooth Quadratic Bezier Waveform Curve
            val samples = frame.waveformSamples
            if (samples.size >= 2) {
                val path = Path()
                val stepX = size.width / (samples.size - 1).coerceAtLeast(1)
                val amplitude = size.height * 0.46f

                val firstY = midY + (samples[0] * amplitude)
                path.moveTo(0f, firstY)

                for (i in 1 until samples.size) {
                    val prevX = (i - 1) * stepX
                    val prevY = midY + (samples[i - 1] * amplitude)
                    val currX = i * stepX
                    val currY = midY + (samples[i] * amplitude)
                    val ctrlX = (prevX + currX) / 2f
                    val ctrlY = (prevY + currY) / 2f
                    path.quadraticTo(prevX, prevY, ctrlX, ctrlY)
                }
                val lastX = (samples.size - 1) * stepX
                val lastY = midY + (samples.last() * amplitude)
                path.lineTo(lastX, lastY)

                val waveBrush = Brush.horizontalGradient(
                    colors = listOf(accentColor, secondaryAccent, accentColor)
                )

                // Soft outer glow stroke
                drawPath(
                    path = path,
                    brush = waveBrush,
                    style = Stroke(
                        width = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    ),
                    alpha = if (isPlaying && frame.isHardwareActive) 0.26f else 0.08f
                )

                // Crisp core waveform stroke
                drawPath(
                    path = path,
                    brush = waveBrush,
                    style = Stroke(
                        width = 2.2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    ),
                    alpha = if (isPlaying && frame.isHardwareActive) 0.96f else 0.45f
                )
            }
        }
    }
}
