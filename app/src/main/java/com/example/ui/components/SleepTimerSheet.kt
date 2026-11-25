package com.example.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.SleepTimerMode
import com.example.model.SleepTimerState
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAppFontColors
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.contrastOnColor
import com.example.util.formatCountdownMs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(
    sleepTimerState: SleepTimerState,
    accentColor: Color,
    onDismiss: () -> Unit,
    onStartTimer: (Int) -> Unit,
    onStartEndOfTrack: () -> Unit,
    onAddMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val surfaceBg = MaterialTheme.colorScheme.surface
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val fontColors = LocalAppFontColors.current
    var customMinutes by remember { mutableIntStateOf(30) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = surfaceBg,
        contentColor = fontColors.primary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .navigationBarsPadding()
                .testTag("sleep_timer_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_vec_timer),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(accentColor),
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = stringResource(R.string.sleep_timer_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = fontColors.primary
                    )
                    Text(
                        text = stringResource(R.string.sleep_timer_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = fontColors.secondary
                    )
                }
            }

            if (sleepTimerState.isActive) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardBg)
                        .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                        .padding(18.dp)
                ) {
                    Text(
                        text = when {
                            sleepTimerState.isFadingOut -> "FADING OUT MEDIA VOLUME…"
                            sleepTimerState.mode == SleepTimerMode.END_OF_TRACK -> "PAUSING AT END OF TRACK"
                            else -> "PAUSING PLAYBACK IN"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor
                    )
                    Text(
                        text = if (sleepTimerState.mode == SleepTimerMode.END_OF_TRACK && sleepTimerState.remainingMs <= 0L) {
                            "End of Track"
                        } else {
                            formatCountdownMs(sleepTimerState.remainingMs)
                        },
                        style = MaterialTheme.typography.headlineLarge,
                        color = fontColors.primary,
                        modifier = Modifier.testTag("sleep_timer_countdown_text")
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (sleepTimerState.mode == SleepTimerMode.DURATION && !sleepTimerState.isFadingOut) {
                            OutlinedButton(
                                onClick = { onAddMinutes(5) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sleep_timer_add_5")
                            ) {
                                Text(
                                    text = stringResource(R.string.sleep_timer_add_five),
                                    color = accentColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Button(
                            onClick = {
                                onCancelTimer()
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = contrastOnColor(accentColor)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sleep_timer_cancel")
                        ) {
                            Text(
                                text = stringResource(R.string.sleep_timer_cancel),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Quick Duration Presets: 15 min, 30 min, 45 min, 60 min, End of Track, Off
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardBg)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Quick Presets",
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor
                )

                val presets = listOf(15, 30, 45, 60)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presets.forEach { mins ->
                        val active = sleepTimerState.isActive &&
                            sleepTimerState.mode == SleepTimerMode.DURATION &&
                            sleepTimerState.selectedMinutes == mins
                        PresetChip(
                            label = "$mins min",
                            selected = active,
                            accentColor = accentColor,
                            onClick = {
                                onStartTimer(mins)
                                onDismiss()
                            },
                            testTag = "sleep_preset_$mins"
                        )
                    }

                    val endActive = sleepTimerState.isActive &&
                        sleepTimerState.mode == SleepTimerMode.END_OF_TRACK
                    PresetChip(
                        label = stringResource(R.string.sleep_timer_end_of_track),
                        selected = endActive,
                        accentColor = accentColor,
                        onClick = {
                            onStartEndOfTrack()
                            onDismiss()
                        },
                        testTag = "sleep_preset_end_of_track"
                    )

                    val offActive = !sleepTimerState.isActive
                    PresetChip(
                        label = stringResource(R.string.sleep_timer_off),
                        selected = offActive,
                        accentColor = accentColor,
                        onClick = {
                            onCancelTimer()
                            onDismiss()
                        },
                        testTag = "sleep_preset_off"
                    )
                }

                // Custom Minutes Slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.sleep_timer_custom),
                        style = MaterialTheme.typography.labelLarge,
                        color = fontColors.secondary
                    )
                    Text(
                        text = "$customMinutes min",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor
                    )
                }

                Slider(
                    value = customMinutes.toFloat(),
                    onValueChange = { customMinutes = it.roundToInt().coerceIn(5, 120) },
                    valueRange = 5f..120f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sleep_custom_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = accentColor,
                        activeTrackColor = accentColor,
                        inactiveTrackColor = ObsidianCardElevated
                    )
                )

                Button(
                    onClick = {
                        onStartTimer(customMinutes)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
                        contentColor = contrastOnColor(accentColor)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("sleep_start_custom_button")
                ) {
                    Text(
                        text = "${stringResource(R.string.sleep_timer_start)} ($customMinutes min)",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    val fontColors = LocalAppFontColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) accentColor else ObsidianCardElevated)
            .border(
                1.dp,
                if (selected) accentColor else HairlineBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) contrastOnColor(accentColor) else fontColors.primary,
            fontWeight = FontWeight.Bold
        )
    }
}
