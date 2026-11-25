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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.PersistedAudioSettings
import com.example.model.AppThemeStyle
import com.example.model.DimLockTimeoutOption
import com.example.model.FontColorPreset
import com.example.model.GestureAction
import com.example.model.HapticIntensity
import com.example.ui.theme.CyanPulse
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAppFontColors
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.contrastOnColor
import com.example.ui.theme.parseColorHex
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    settings: PersistedAudioSettings,
    fontUploadStatus: String?,
    accentColor: Color,
    onDismiss: () -> Unit,
    onUpdateVolUpShort: (GestureAction) -> Unit,
    onUpdateVolDownShort: (GestureAction) -> Unit,
    onUpdateVolUpLong: (GestureAction) -> Unit,
    onUpdateVolDownLong: (GestureAction) -> Unit,
    onUpdateWristFlickAction: (GestureAction) -> Unit,
    onToggleWristFlick: (Boolean) -> Unit,
    onWristFlickSensitivity: (Float) -> Unit,
    onUpdateFaceDownAction: (GestureAction) -> Unit,
    onToggleFaceDown: (Boolean) -> Unit,
    onFaceDownSensitivity: (Float) -> Unit,
    onSelectThemeStyle: (AppThemeStyle) -> Unit,
    onGlassBlurChange: (Float) -> Unit,
    onSelectCustomAccentHex: (String?) -> Unit,
    onSelectCustomFontHex: (String?) -> Unit,
    onResetColorsToDefault: () -> Unit,
    onToggleUse24HourClock: (Boolean) -> Unit,
    onSelectDimLockTimeout: (Int) -> Unit,
    onUploadCustomFont: () -> Unit,
    onResetCustomFont: () -> Unit,
    onLyricsTextScaleChange: (Float) -> Unit,
    onToggleHaptics: (Boolean) -> Unit,
    onSelectHapticIntensity: (HapticIntensity) -> Unit,
    onToggleHapticTransport: (Boolean) -> Unit,
    onToggleHapticSliders: (Boolean) -> Unit,
    onToggleHapticGestures: (Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val surfaceBg = MaterialTheme.colorScheme.surface
    val cardBg = MaterialTheme.colorScheme.surfaceVariant
    val fontColors = LocalAppFontColors.current

    var showAccentColorPicker by remember { mutableStateOf(false) }
    var showFontColorPicker by remember { mutableStateOf(false) }

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
                .testTag("settings_sheet"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_vec_settings),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(accentColor),
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = fontColors.primary
                )
            }

            // Section 1: Lock Screen Clock & Foreground Inactivity Timeout
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
                    text = stringResource(R.string.settings_lock_clock_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor
                )

                Text(
                    text = stringResource(R.string.clock_format_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = fontColors.secondary
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val is12hSelected = !settings.use24HourClock
                    val is24hSelected = settings.use24HourClock

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (is12hSelected) accentColor else ObsidianCardElevated)
                            .border(
                                1.dp,
                                if (is12hSelected) accentColor else HairlineBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onToggleUse24HourClock(false) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("clock_format_12h")
                    ) {
                        Text(
                            text = stringResource(R.string.clock_format_12h),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (is12hSelected) contrastOnColor(accentColor) else fontColors.primary,
                            fontWeight = if (is12hSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (is24hSelected) accentColor else ObsidianCardElevated)
                            .border(
                                1.dp,
                                if (is24hSelected) accentColor else HairlineBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onToggleUse24HourClock(true) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("clock_format_24h")
                    ) {
                        Text(
                            text = stringResource(R.string.clock_format_24h),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (is24hSelected) contrastOnColor(accentColor) else fontColors.primary,
                            fontWeight = if (is24hSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.dim_lock_timeout_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = fontColors.secondary
                )

                Text(
                    text = stringResource(R.string.dim_lock_timeout_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = fontColors.tertiary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DimLockTimeoutOption.entries.forEach { option ->
                        val selected = settings.dimLockTimeoutSeconds == option.seconds
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) accentColor else ObsidianCardElevated)
                                .border(
                                    1.dp,
                                    if (selected) accentColor else HairlineBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { onSelectDimLockTimeout(option.seconds) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("dim_timeout_${option.seconds}")
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) contrastOnColor(accentColor) else fontColors.primary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Section 2: App Theme & Appearance (Themes + Custom Accent & Font Color Wheel Pickers)
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
                    text = stringResource(R.string.settings_theme_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppThemeStyle.entries.forEach { style ->
                        val selected = settings.themeStyle == style
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) accentColor else ObsidianCardElevated)
                                .border(
                                    1.dp,
                                    if (selected) accentColor else HairlineBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onSelectThemeStyle(style)
                                    if (style == AppThemeStyle.CUSTOM && settings.customAccentHex == null) {
                                        showAccentColorPicker = true
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("theme_style_${style.name.lowercase()}")
                        ) {
                            Text(
                                text = style.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) contrastOnColor(accentColor) else fontColors.primary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Custom App Accent Color Picker Row
                val currentCustomAccent = remember(settings.customAccentHex, accentColor) {
                    parseColorHex(settings.customAccentHex) ?: accentColor
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.custom_accent_color_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = fontColors.primary
                        )
                        Text(
                            text = settings.customAccentHex ?: "Theme Default",
                            style = MaterialTheme.typography.labelSmall,
                            color = fontColors.secondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(currentCustomAccent)
                                .border(1.dp, HairlineBorder, CircleShape)
                        )
                        OutlinedButton(
                            onClick = { showAccentColorPicker = true },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("pick_custom_accent_button")
                        ) {
                            Text(
                                text = stringResource(R.string.pick_color_label),
                                color = accentColor
                            )
                        }
                    }
                }

                // Custom Font Color Presets + Color Wheel Trigger
                Text(
                    text = stringResource(R.string.custom_font_color_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = fontColors.secondary
                )

                val matchedFontPreset = remember(settings.customFontHex) {
                    val norm = settings.customFontHex?.trim()?.uppercase()
                    when {
                        norm == null -> FontColorPreset.AUTO
                        else -> FontColorPreset.entries.firstOrNull {
                            it.hex != null && it.hex.equals(norm, ignoreCase = true)
                        } ?: FontColorPreset.CUSTOM
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FontColorPreset.entries.forEach { preset ->
                        val selected = matchedFontPreset == preset
                        val swatch = preset.hex?.let { parseColorHex(it) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) accentColor else ObsidianCardElevated)
                                .border(
                                    1.dp,
                                    if (selected) accentColor else HairlineBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    when (preset) {
                                        FontColorPreset.AUTO -> onSelectCustomFontHex(null)
                                        FontColorPreset.CUSTOM -> showFontColorPicker = true
                                        else -> onSelectCustomFontHex(preset.hex)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("font_color_preset_${preset.name.lowercase()}")
                        ) {
                            if (swatch != null) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(swatch)
                                        .border(1.dp, HairlineBorder, CircleShape)
                                )
                            }
                            Text(
                                text = preset.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) contrastOnColor(accentColor) else fontColors.primary,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.glass_blur_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = fontColors.secondary,
                        modifier = Modifier.width(140.dp)
                    )
                    Slider(
                        value = settings.glassBlurIntensity,
                        onValueChange = onGlassBlurChange,
                        valueRange = 0.0f..1.0f,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("glass_blur_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = ObsidianCardElevated
                        )
                    )
                    Text(
                        text = "${(settings.glassBlurIntensity * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        modifier = Modifier.width(44.dp)
                    )
                }

                OutlinedButton(
                    onClick = onResetColorsToDefault,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("reset_colors_button")
                ) {
                    Text(
                        text = stringResource(R.string.reset_colors_default),
                        color = fontColors.primary
                    )
                }
            }

            // Section 3: Gesture & Button Customization
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
                    text = stringResource(R.string.settings_gestures_section),
                    style = MaterialTheme.typography.titleMedium,
                    color = accentColor
                )

                ActionMappingRow(
                    label = stringResource(R.string.vol_up_short_label),
                    selectedAction = settings.volUpShortAction,
                    accentColor = accentColor,
                    onSelect = onUpdateVolUpShort,
                    testTag = "action_vol_up_short"
                )
                ActionMappingRow(
                    label = stringResource(R.string.vol_down_short_label),
                    selectedAction = settings.volDownShortAction,
                    accentColor = accentColor,
                    onSelect = onUpdateVolDownShort,
                    testTag = "action_vol_down_short"
                )
                ActionMappingRow(
                    label = stringResource(R.string.vol_up_long_label),
                    selectedAction = settings.volUpLongAction,
                    accentColor = accentColor,
                    onSelect = onUpdateVolUpLong,
                    testTag = "action_vol_up_long"
                )
                ActionMappingRow(
                    label = stringResource(R.string.vol_down_long_label),
                    selectedAction = settings.volDownLongAction,
                    accentColor = accentColor,
                    onSelect = onUpdateVolDownLong,
                    testTag = "action_vol_down_long"
                )

                MotionGestureControlBlock(
                    title = stringResource(R.string.wrist_flick_label),
                    enabled = settings.wristFlickEnabled,
                    sensitivity = settings.wristFlickSensitivity,
                    selectedAction = settings.wristFlickAction,
                    accentColor = accentColor,
                    onToggle = onToggleWristFlick,
                    onSensitivityChange = onWristFlickSensitivity,
                    onSelectAction = onUpdateWristFlickAction,
                    tagPrefix = "wrist_flick"
                )

                MotionGestureControlBlock(
                    title = stringResource(R.string.face_down_label),
                    enabled = settings.faceDownEnabled,
                    sensitivity = settings.faceDownSensitivity,
                    selectedAction = settings.faceDownAction,
                    accentColor = accentColor,
                    onToggle = onToggleFaceDown,
                    onSensitivityChange = onFaceDownSensitivity,
                    onSelectAction = onUpdateFaceDownAction,
                    tagPrefix = "face_down"
                )
            }

            // Section 4: Custom App Font (.ttf / .otf Uploader)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardBg)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_vec_font),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(accentColor),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_font_section),
                        style = MaterialTheme.typography.titleMedium,
                        color = accentColor
                    )
                }

                Text(
                    text = "Active Font: ${settings.customFontName ?: stringResource(R.string.default_font_name)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = fontColors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (!fontUploadStatus.isNullOrBlank()) {
                    Text(
                        text = fontUploadStatus,
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = onUploadCustomFont,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = contrastOnColor(accentColor)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("upload_custom_font_button")
                    ) {
                        Text(
                            text = stringResource(R.string.upload_custom_font),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (settings.customFontPath != null) {
                        OutlinedButton(
                            onClick = onResetCustomFont,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("reset_custom_font_button")
                        ) {
                            Text(
                                text = stringResource(R.string.reset_default_font),
                                style = MaterialTheme.typography.labelLarge,
                                color = fontColors.primary
                            )
                        }
                    }
                }
            }

            // Section 4b: Lyrics Typography (Independent Text Size Scaling)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardBg)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val clampedLyricsScale = settings.lyricsTextScale.coerceIn(0.75f, 1.65f)
                val pct = (clampedLyricsScale * 100f).roundToInt()

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
                        Image(
                            painter = painterResource(R.drawable.ic_vec_lyrics),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(accentColor),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_lyrics_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = accentColor
                        )
                    }

                    if (pct != 100) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(ObsidianCardElevated)
                                .border(1.dp, HairlineBorder, RoundedCornerShape(12.dp))
                                .clickable { onLyricsTextScaleChange(1.0f) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("reset_lyrics_size_button")
                        ) {
                            Text(
                                text = stringResource(R.string.lyrics_size_reset),
                                style = MaterialTheme.typography.labelMedium,
                                color = accentColor,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.lyrics_text_size_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = fontColors.secondary
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_text_size_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = fontColors.secondary,
                        modifier = Modifier.width(126.dp)
                    )
                    Slider(
                        value = clampedLyricsScale,
                        onValueChange = { raw ->
                            val snapped = (raw * 20f).roundToInt() / 20f
                            onLyricsTextScaleChange(snapped.coerceIn(0.75f, 1.65f))
                        },
                        valueRange = 0.75f..1.65f,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("lyrics_text_size_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = ObsidianCardElevated
                        )
                    )
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .width(48.dp)
                            .testTag("lyrics_text_size_value")
                    )
                }

                // Live Scaled Lyric Preview (rest of UI remains unaffected)
                val previewActiveSize = (20f * clampedLyricsScale).sp
                val previewInactiveSize = (17f * clampedLyricsScale).sp
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(ObsidianCardElevated.copy(alpha = 0.65f))
                        .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag("lyrics_size_preview_box")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.lyrics_preview_active),
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = previewActiveSize,
                                lineHeight = (previewActiveSize.value * 1.25f).sp,
                                fontWeight = FontWeight.ExtraBold
                            ),
                            color = fontColors.primary
                        )
                    }
                    Text(
                        text = stringResource(R.string.lyrics_preview_next),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = previewInactiveSize,
                            lineHeight = (previewInactiveSize.value * 1.25f).sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = fontColors.primary.copy(alpha = 0.45f),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                }
            }

            // Section 5: Haptic Feedback Customization
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(cardBg)
                    .border(1.dp, HairlineBorder, RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_vec_haptic),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(accentColor),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_haptics_section),
                            style = MaterialTheme.typography.titleMedium,
                            color = accentColor
                        )
                    }
                    Switch(
                        checked = settings.hapticsEnabled,
                        onCheckedChange = onToggleHaptics,
                        modifier = Modifier.testTag("haptics_master_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = contrastOnColor(accentColor),
                            checkedTrackColor = accentColor
                        )
                    )
                }

                Text(
                    text = stringResource(R.string.haptics_intensity_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = fontColors.secondary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HapticIntensity.entries.forEach { level ->
                        val selected = settings.hapticIntensity == level
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) accentColor else ObsidianCardElevated)
                                .border(
                                    1.dp,
                                    if (selected) accentColor else HairlineBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable(enabled = settings.hapticsEnabled) {
                                    onSelectHapticIntensity(level)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .testTag("haptic_intensity_${level.name.lowercase()}")
                        ) {
                            Text(
                                text = level.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = when {
                                    selected -> contrastOnColor(accentColor)
                                    settings.hapticsEnabled -> fontColors.primary
                                    else -> fontColors.secondary
                                },
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                HapticToggleRow(
                    label = stringResource(R.string.haptics_transport_label),
                    checked = settings.hapticOnTransport,
                    enabled = settings.hapticsEnabled,
                    accentColor = accentColor,
                    onCheckedChange = onToggleHapticTransport,
                    testTag = "haptic_transport_switch"
                )
                HapticToggleRow(
                    label = stringResource(R.string.haptics_sliders_label),
                    checked = settings.hapticOnSliders,
                    enabled = settings.hapticsEnabled,
                    accentColor = accentColor,
                    onCheckedChange = onToggleHapticSliders,
                    testTag = "haptic_sliders_switch"
                )
                HapticToggleRow(
                    label = stringResource(R.string.haptics_gestures_label),
                    checked = settings.hapticOnGestures,
                    enabled = settings.hapticsEnabled,
                    accentColor = accentColor,
                    onCheckedChange = onToggleHapticGestures,
                    testTag = "haptic_gestures_switch"
                )
            }
        }
    }

    if (showAccentColorPicker) {
        val initial = parseColorHex(settings.customAccentHex) ?: accentColor.takeIf { it != Color.Transparent } ?: CyanPulse
        ColorWheelPickerDialog(
            title = stringResource(R.string.color_picker_title_accent),
            initialColor = initial,
            accentColor = accentColor,
            onDismiss = { showAccentColorPicker = false },
            onColorSelected = { hex ->
                onSelectCustomAccentHex(hex)
                showAccentColorPicker = false
            }
        )
    }

    if (showFontColorPicker) {
        val initial = parseColorHex(settings.customFontHex) ?: fontColors.primary
        ColorWheelPickerDialog(
            title = stringResource(R.string.color_picker_title_font),
            initialColor = initial,
            accentColor = accentColor,
            onDismiss = { showFontColorPicker = false },
            onColorSelected = { hex ->
                onSelectCustomFontHex(hex)
                showFontColorPicker = false
            }
        )
    }
}

@Composable
private fun HapticToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    accentColor: Color,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val fontColors = LocalAppFontColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) fontColors.primary else fontColors.secondary,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
            colors = SwitchDefaults.colors(
                checkedThumbColor = contrastOnColor(accentColor),
                checkedTrackColor = accentColor
            )
        )
    }
}

@Composable
private fun ActionMappingRow(
    label: String,
    selectedAction: GestureAction,
    accentColor: Color,
    onSelect: (GestureAction) -> Unit,
    testTag: String
) {
    val fontColors = LocalAppFontColors.current
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = fontColors.primary,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        )
        Box {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(ObsidianCardElevated)
                    .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag(testTag)
            ) {
                Text(
                    text = selectedAction.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                GestureAction.entries.forEach { action ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = action.label,
                                color = if (action == selectedAction) accentColor else fontColors.primary,
                                fontWeight = if (action == selectedAction) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(action)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MotionGestureControlBlock(
    title: String,
    enabled: Boolean,
    sensitivity: Float,
    selectedAction: GestureAction,
    accentColor: Color,
    onToggle: (Boolean) -> Unit,
    onSensitivityChange: (Float) -> Unit,
    onSelectAction: (GestureAction) -> Unit,
    tagPrefix: String
) {
    val fontColors = LocalAppFontColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ObsidianCardElevated.copy(alpha = 0.48f))
            .border(1.dp, HairlineBorder, RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = fontColors.primary
            )
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                modifier = Modifier.testTag("${tagPrefix}_switch"),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = contrastOnColor(accentColor),
                    checkedTrackColor = accentColor
                )
            )
        }

        ActionMappingRow(
            label = "Mapped Action",
            selectedAction = selectedAction,
            accentColor = accentColor,
            onSelect = onSelectAction,
            testTag = "${tagPrefix}_action"
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Sensitivity",
                style = MaterialTheme.typography.labelMedium,
                color = fontColors.secondary,
                modifier = Modifier.width(86.dp)
            )
            Slider(
                value = sensitivity,
                onValueChange = onSensitivityChange,
                valueRange = 0.2f..1.0f,
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .testTag("${tagPrefix}_sensitivity_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = accentColor,
                    activeTrackColor = accentColor,
                    inactiveTrackColor = ObsidianCardElevated
                )
            )
            Text(
                text = "${(sensitivity * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = accentColor,
                modifier = Modifier.width(42.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}
