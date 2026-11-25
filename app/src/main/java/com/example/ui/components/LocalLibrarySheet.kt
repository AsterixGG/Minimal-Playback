package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.media.LocalLibraryScanResult
import com.example.model.LocalAudioTrack
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAppFontColors
import com.example.ui.theme.contrastOnColor
import com.example.util.formatDurationMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalLibrarySheet(
    hasAudioPermission: Boolean,
    isScanning: Boolean,
    library: LocalLibraryScanResult,
    accentColor: Color,
    onRequestPermission: () -> Unit,
    onRescan: () -> Unit,
    onPlayTracks: (List<LocalAudioTrack>, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val surfaceBg = MaterialTheme.colorScheme.surface
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val fontColors = LocalAppFontColors.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        stringResource(R.string.tab_songs),
        stringResource(R.string.tab_albums),
        stringResource(R.string.tab_folders)
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = surfaceBg,
        contentColor = fontColors.primary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.84f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.local_library_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = fontColors.primary
                )
                if (hasAudioPermission) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onRescan,
                            modifier = Modifier.testTag("rescan_local_button")
                        ) {
                            Text(stringResource(R.string.rescan_library), color = fontColors.primary)
                        }
                        if (library.songs.isNotEmpty()) {
                            Button(
                                onClick = {
                                    onPlayTracks(library.songs, 0)
                                    onDismiss()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accentColor,
                                    contentColor = contrastOnColor(accentColor)
                                ),
                                modifier = Modifier.testTag("play_all_local_button")
                            ) {
                                Text(stringResource(R.string.play_all), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (!hasAudioPermission) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .border(1.dp, HairlineBorder, RoundedCornerShape(20.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.local_permission_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = fontColors.primary
                    )
                    Text(
                        text = stringResource(R.string.local_permission_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = fontColors.secondary
                    )
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = contrastOnColor(accentColor)
                        ),
                        modifier = Modifier.testTag("grant_audio_permission_button")
                    ) {
                        Text(stringResource(R.string.grant_audio_permission), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                // Songs / Albums / Folders segmented switcher
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { idx, label ->
                        val active = selectedTab == idx
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) accentColor else cardBg)
                                .border(1.dp, if (active) accentColor else HairlineBorder, RoundedCornerShape(12.dp))
                                .clickable { selectedTab = idx }
                                .padding(vertical = 10.dp)
                                .testTag("local_tab_$idx")
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) contrastOnColor(accentColor) else fontColors.primary,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isScanning) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        CircularProgressIndicator(color = accentColor)
                    }
                } else if (library.songs.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.no_local_audio),
                            style = MaterialTheme.typography.bodyLarge,
                            color = fontColors.secondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        when (selectedTab) {
                            0 -> {
                                itemsIndexed(library.songs, key = { _, t -> t.id }) { idx, track ->
                                    LocalLibraryRow(
                                        iconRes = R.drawable.ic_vec_music_note,
                                        title = track.title,
                                        subtitle = "${track.artist} • ${track.album}",
                                        trailing = formatDurationMs(track.durationMs),
                                        accentColor = accentColor,
                                        onClick = {
                                            onPlayTracks(library.songs, idx)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                            1 -> {
                                items(library.albums, key = { it.albumName }) { album ->
                                    LocalLibraryRow(
                                        iconRes = R.drawable.ic_vec_album,
                                        title = album.albumName,
                                        subtitle = "${album.artistName} • ${album.tracks.size} tracks",
                                        trailing = "${album.tracks.size}",
                                        accentColor = accentColor,
                                        onClick = {
                                            onPlayTracks(album.tracks, 0)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                            else -> {
                                items(library.folders, key = { it.folderPath }) { folder ->
                                    LocalLibraryRow(
                                        iconRes = R.drawable.ic_vec_folder,
                                        title = folder.folderName,
                                        subtitle = folder.folderPath,
                                        trailing = "${folder.tracks.size}",
                                        accentColor = accentColor,
                                        onClick = {
                                            onPlayTracks(folder.tracks, 0)
                                            onDismiss()
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

@Composable
private fun LocalLibraryRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    trailing: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val fontColors = LocalAppFontColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg)
            .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.14f))
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(accentColor),
                modifier = Modifier.size(20.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = fontColors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = fontColors.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = trailing,
            style = MaterialTheme.typography.labelMedium,
            color = fontColors.secondary
        )
    }
}
