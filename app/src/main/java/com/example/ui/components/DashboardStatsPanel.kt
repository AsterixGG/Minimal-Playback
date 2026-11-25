package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.PlaybackStatsCodec
import com.example.model.AppThemeStyle
import com.example.model.ArtistHabitStat
import com.example.model.PlaybackStatsState
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAppFontColors
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.contrastOnColor
import kotlinx.coroutines.delay

@Composable
fun DashboardStatsPanel(
    stats: PlaybackStatsState,
    accentColor: Color,
    secondaryAccent: Color,
    themeStyle: AppThemeStyle = AppThemeStyle.ADAPTIVE_ART,
    onResetStats: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fontColors = LocalAppFontColors.current
    val isAmoled = themeStyle == AppThemeStyle.AMOLED_PURE_BLACK
    val tileBackground = if (isAmoled) Color.Black else ObsidianCardElevated.copy(alpha = 0.78f)
    var confirmResetPending by remember { mutableStateOf(false) }

    LaunchedEffect(confirmResetPending) {
        if (confirmResetPending) {
            delay(3000L)
            confirmResetPending = false
        }
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("stats_panel_root")
    ) {
        // Header Row + Two-Step Reset Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.18f))
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_vec_stats),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(accentColor),
                        modifier = Modifier.size(17.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.stats_panel_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = fontColors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.stats_panel_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = fontColors.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (confirmResetPending) accentColor.copy(alpha = 0.22f)
                        else tileBackground
                    )
                    .border(
                        1.dp,
                        if (confirmResetPending) accentColor else HairlineBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        if (confirmResetPending) {
                            confirmResetPending = false
                            onResetStats()
                        } else {
                            confirmResetPending = true
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("stats_reset_button")
            ) {
                Text(
                    text = stringResource(
                        if (confirmResetPending) R.string.stats_reset_confirm else R.string.stats_reset_button
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (confirmResetPending) accentColor else fontColors.secondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Primary Metric Pair: Dim Lock Time + Total Listening Time
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            HabitStatTile(
                iconRes = R.drawable.ic_vec_lock,
                label = stringResource(R.string.stats_dim_lock_time),
                value = PlaybackStatsCodec.formatHabitDuration(stats.totalDimLockMs),
                subtitle = "${stats.dimLockSessionCount} ${stringResource(R.string.stats_dim_sessions).lowercase()} • ${stringResource(R.string.stats_longest_dim_lock)} ${PlaybackStatsCodec.formatHabitDuration(stats.longestDimLockSessionMs)}",
                highlightColor = accentColor,
                tileBackground = tileBackground,
                isFeatured = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stats_dim_lock_card")
            )

            HabitStatTile(
                iconRes = R.drawable.ic_vec_music_note,
                label = stringResource(R.string.stats_total_listening),
                value = PlaybackStatsCodec.formatHabitDuration(stats.totalListeningMs),
                subtitle = "${stats.totalTracksPlayed} ${stringResource(R.string.stats_tracks_played).lowercase()} • ${stats.uniqueArtistsCount} artists",
                highlightColor = secondaryAccent,
                tileBackground = tileBackground,
                isFeatured = false,
                modifier = Modifier
                    .weight(1f)
                    .testTag("stats_listening_card")
            )
        }

        // Most Listened To Artists Leaderboard
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(tileBackground)
                .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.stats_top_artists_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = fontColors.primary,
                    fontWeight = FontWeight.Bold
                )
                if (stats.topArtists.isNotEmpty()) {
                    Text(
                        text = "Top ${stats.topArtists.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor
                    )
                }
            }

            if (stats.topArtists.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag("stats_top_artists_empty")
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_vec_album),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(fontColors.tertiary),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.stats_top_artists_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = fontColors.secondary
                    )
                }
            } else {
                stats.topArtists.forEachIndexed { index, artist ->
                    ArtistLeaderboardRow(
                        rank = index + 1,
                        stat = artist,
                        accentColor = accentColor,
                        secondaryAccent = secondaryAccent,
                        isAmoled = isAmoled,
                        modifier = Modifier.testTag("stats_artist_row_$index")
                    )
                }
            }
        }

        // Playback Source Mix (External Player vs Local Device)
        val totalSourceMs = (stats.externalListenMs + stats.localListenMs).coerceAtLeast(1L)
        val externalFraction by animateFloatAsState(
            targetValue = if (stats.externalListenMs + stats.localListenMs > 0L) {
                (stats.externalListenMs.toFloat() / totalSourceMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0.5f
            },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "externalMixFraction"
        )
        val hasSourceData = stats.externalListenMs > 0L || stats.localListenMs > 0L
        val externalPct = if (hasSourceData) (externalFraction * 100).toInt() else 0
        val localPct = if (hasSourceData) (100 - externalPct).coerceAtLeast(0) else 0

        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(tileBackground)
                .border(1.dp, HairlineBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
                .testTag("stats_source_mix_card")
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.stats_source_mix_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = fontColors.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (hasSourceData) {
                        "${stringResource(R.string.source_external)} $externalPct% • ${stringResource(R.string.source_local)} $localPct%"
                    } else {
                        "No sessions yet"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = fontColors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(if (isAmoled) Color(0xFF141414) else Color.White.copy(alpha = 0.08f))
            ) {
                if (hasSourceData) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (externalFraction > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight(externalFraction.coerceAtLeast(0.01f))
                                    .background(accentColor)
                            )
                        }
                        if ((1f - externalFraction) > 0.01f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .weight((1f - externalFraction).coerceAtLeast(0.01f))
                                    .background(secondaryAccent)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitStatTile(
    iconRes: Int,
    label: String,
    value: String,
    subtitle: String,
    highlightColor: Color,
    tileBackground: Color,
    isFeatured: Boolean,
    modifier: Modifier = Modifier
) {
    val fontColors = LocalAppFontColors.current
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(tileBackground)
            .border(
                width = if (isFeatured) 1.2.dp else 1.dp,
                color = if (isFeatured) highlightColor.copy(alpha = 0.45f) else HairlineBorder,
                shape = RoundedCornerShape(18.dp)
            )
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(highlightColor),
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fontColors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = fontColors.primary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = fontColors.tertiary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ArtistLeaderboardRow(
    rank: Int,
    stat: ArtistHabitStat,
    accentColor: Color,
    secondaryAccent: Color,
    isAmoled: Boolean,
    modifier: Modifier = Modifier
) {
    val fontColors = LocalAppFontColors.current
    val animatedFraction by animateFloatAsState(
        targetValue = stat.shareFraction.coerceIn(0.06f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "artistBar_$rank"
    )
    val isTopRank = rank == 1

    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        if (isTopRank) accentColor else accentColor.copy(alpha = 0.16f)
                    )
            ) {
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isTopRank) contrastOnColor(accentColor) else accentColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = stat.artistName,
                style = MaterialTheme.typography.bodyMedium,
                color = fontColors.primary,
                fontWeight = if (isTopRank) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = "${PlaybackStatsCodec.formatHabitDuration(stat.listenMs)} • ${stat.playCount} ${stringResource(R.string.stats_plays_suffix)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isTopRank) accentColor else fontColors.secondary,
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(if (isAmoled) Color(0xFF141414) else Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFraction)
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(accentColor, secondaryAccent)
                        )
                    )
            )
        }
    }
}
