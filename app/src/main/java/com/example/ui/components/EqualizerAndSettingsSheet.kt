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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import com.example.data.PersistedAudioSettings
import com.example.model.EqPreset
import com.example.model.EqualizerState
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAppFontColors
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.contrastOnColor
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EqualizerAndSettingsSheet(
    eqState: EqualizerState,
    settings: PersistedAudioSettings,
    accentColor: Color,
    onDismiss: () -> Unit,
    onToggleEq: (Boolean) -> Unit,
    onSelectPreset: (EqPreset) -> Unit,
    onBandLevelChange: (Short, Short) -> Unit,
    onBassBoostChange: (Int) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val surfaceBg = MaterialTheme.colorScheme.surface
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val fontColors = LocalAppFontColors.current

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
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_vec_equalizer),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(accentColor),
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = stringResource(R.string.eq_sheet_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = fontColors.primary
                    )
                }

                Switch(
                    checked = eqState.enabled,
                    onCheckedChange = onToggleEq,
                    modifier = Modifier.testTag("eq_enable_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = contrastOnColor(accentColor),
                        checkedTrackColor = accentColor
                    )
                )
            }

            if (!eqState.isHardwareAvailable) {
                Text(
                    text = stringResource(R.string.eq_unavailable_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = fontColors.secondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(cardBg)
                        .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                )
            }

            // Presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                EqPreset.entries.forEach { preset ->
                    val selected = eqState.preset == preset
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) accentColor else cardBg)
                            .border(
                                1.dp,
                                if (selected) accentColor else HairlineBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectPreset(preset) }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("eq_preset_${preset.name.lowercase()}")
                    ) {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) contrastOnColor(accentColor) else fontColors.primary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Frequency Band Sliders
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardBg)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                eqState.bands.forEach { band ->
                    val freqLabel = if (band.centerFreqHz >= 1000) {
                        "${band.centerFreqHz / 1000}kHz"
                    } else {
                        "${band.centerFreqHz}Hz"
                    }
                    val db = band.levelMb / 100f
                    val dbText = if (db >= 0f) "+%.1f dB".format(db) else "%.1f dB".format(db)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = freqLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = fontColors.secondary,
                            modifier = Modifier.width(56.dp)
                        )
                        Slider(
                            value = band.levelMb.toFloat(),
                            onValueChange = { newVal ->
                                onBandLevelChange(band.bandIndex, newVal.roundToInt().toShort())
                            },
                            valueRange = band.minLevelMb.toFloat()..band.maxLevelMb.toFloat(),
                            enabled = eqState.enabled,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("eq_band_${band.bandIndex}"),
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                                inactiveTrackColor = ObsidianCardElevated
                            )
                        )
                        Text(
                            text = dbText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (eqState.enabled) accentColor else fontColors.secondary,
                            modifier = Modifier.width(68.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // BassBoost Slider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.bass_boost_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = fontColors.primary,
                        modifier = Modifier.width(92.dp)
                    )
                    Slider(
                        value = eqState.bassBoostStrength.toFloat(),
                        onValueChange = { onBassBoostChange(it.roundToInt()) },
                        valueRange = 0f..1000f,
                        enabled = eqState.enabled,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bass_boost_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = ObsidianCardElevated
                        )
                    )
                    Text(
                        text = "${(eqState.bassBoostStrength / 10)}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        modifier = Modifier.width(46.dp)
                    )
                }
            }
        }
    }
}
