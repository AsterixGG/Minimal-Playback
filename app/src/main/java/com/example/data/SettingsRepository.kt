package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppThemeStyle
import com.example.model.EqPreset
import com.example.model.GestureAction
import com.example.model.HapticIntensity
import com.example.model.PlaybackSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "minimal_playback_prefs")

data class PersistedAudioSettings(
    val eqEnabled: Boolean = false,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val customBandLevels: List<Int> = listOf(0, 0, 0, 0, 0),
    val bassBoostStrength: Int = 0,
    val motionGesturesEnabled: Boolean = true,
    val motionSensitivity: Float = 0.6f,
    val wristFlickEnabled: Boolean = true,
    val wristFlickSensitivity: Float = 0.6f,
    val faceDownEnabled: Boolean = true,
    val faceDownSensitivity: Float = 0.6f,
    val volUpShortAction: GestureAction = GestureAction.MUTE_TOGGLE,
    val volDownShortAction: GestureAction = GestureAction.VOLUME_DOWN,
    val volUpLongAction: GestureAction = GestureAction.PREVIOUS_TRACK,
    val volDownLongAction: GestureAction = GestureAction.NEXT_TRACK,
    val wristFlickAction: GestureAction = GestureAction.NEXT_TRACK,
    val faceDownAction: GestureAction = GestureAction.PLAY_PAUSE,
    val preferredSource: PlaybackSource = PlaybackSource.EXTERNAL,
    val themeStyle: AppThemeStyle = AppThemeStyle.ADAPTIVE_ART,
    val useAmoledBlack: Boolean = false,
    val glassBlurIntensity: Float = 0.65f,
    val customFontPath: String? = null,
    val customFontName: String? = null,
    val hapticsEnabled: Boolean = true,
    val hapticIntensity: HapticIntensity = HapticIntensity.MEDIUM,
    val hapticOnTransport: Boolean = true,
    val hapticOnSliders: Boolean = true,
    val hapticOnGestures: Boolean = true,
    val use24HourClock: Boolean = false,
    val dimLockTimeoutSeconds: Int = 0,
    val customAccentHex: String? = null,
    val customFontHex: String? = null,
    val lyricsTextScale: Float = 1.0f
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val BAND_LEVELS = stringPreferencesKey("band_levels")
        val BASS_BOOST = intPreferencesKey("bass_boost")
        val MOTION_ENABLED = booleanPreferencesKey("motion_enabled")
        val MOTION_SENSITIVITY = floatPreferencesKey("motion_sensitivity")
        val WRIST_FLICK_ENABLED = booleanPreferencesKey("wrist_flick_enabled")
        val WRIST_FLICK_SENSITIVITY = floatPreferencesKey("wrist_flick_sensitivity")
        val FACE_DOWN_ENABLED = booleanPreferencesKey("face_down_enabled")
        val FACE_DOWN_SENSITIVITY = floatPreferencesKey("face_down_sensitivity")
        val VOL_UP_SHORT_ACTION = stringPreferencesKey("vol_up_short_action")
        val VOL_DOWN_SHORT_ACTION = stringPreferencesKey("vol_down_short_action")
        val VOL_UP_LONG_ACTION = stringPreferencesKey("vol_up_long_action")
        val VOL_DOWN_LONG_ACTION = stringPreferencesKey("vol_down_long_action")
        val WRIST_FLICK_ACTION = stringPreferencesKey("wrist_flick_action")
        val FACE_DOWN_ACTION = stringPreferencesKey("face_down_action")
        val PREFERRED_SOURCE = stringPreferencesKey("preferred_source")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val USE_AMOLED_BLACK = booleanPreferencesKey("use_amoled_black")
        val GLASS_BLUR_INTENSITY = floatPreferencesKey("glass_blur_intensity")
        val CUSTOM_FONT_PATH = stringPreferencesKey("custom_font_path")
        val CUSTOM_FONT_NAME = stringPreferencesKey("custom_font_name")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val HAPTIC_INTENSITY = stringPreferencesKey("haptic_intensity")
        val HAPTIC_ON_TRANSPORT = booleanPreferencesKey("haptic_on_transport")
        val HAPTIC_ON_SLIDERS = booleanPreferencesKey("haptic_on_sliders")
        val HAPTIC_ON_GESTURES = booleanPreferencesKey("haptic_on_gestures")
        val USE_24_HOUR_CLOCK = booleanPreferencesKey("use_24_hour_clock")
        val DIM_LOCK_TIMEOUT_SECONDS = intPreferencesKey("dim_lock_timeout_seconds")
        val CUSTOM_ACCENT_HEX = stringPreferencesKey("custom_accent_hex")
        val CUSTOM_FONT_HEX = stringPreferencesKey("custom_font_hex")
        val LYRICS_TEXT_SCALE = floatPreferencesKey("lyrics_text_scale")
    }

    private fun parseAction(raw: String?, default: GestureAction): GestureAction {
        if (raw.isNullOrBlank()) return default
        return runCatching { GestureAction.valueOf(raw) }.getOrDefault(default)
    }

    private fun parseThemeStyle(raw: String?): AppThemeStyle {
        return when (raw) {
            "STUDIO_CYAN" -> AppThemeStyle.MIDNIGHT_BLUE
            "AMBER_DASH" -> AppThemeStyle.CRIMSON_DARK
            "EMERALD_PULSE" -> AppThemeStyle.EMERALD_DARK
            null -> AppThemeStyle.ADAPTIVE_ART
            else -> runCatching { AppThemeStyle.valueOf(raw) }.getOrDefault(AppThemeStyle.ADAPTIVE_ART)
        }
    }

    val settingsFlow: Flow<PersistedAudioSettings> = context.dataStore.data.map { prefs ->
        val presetName = prefs[Keys.EQ_PRESET] ?: EqPreset.FLAT.name
        val preset = runCatching { EqPreset.valueOf(presetName) }.getOrDefault(EqPreset.FLAT)

        val bandsStr = prefs[Keys.BAND_LEVELS] ?: "0,0,0,0,0"
        val bands = bandsStr.split(",").mapNotNull { it.trim().toIntOrNull() }
            .ifEmpty { listOf(0, 0, 0, 0, 0) }

        val sourceName = prefs[Keys.PREFERRED_SOURCE] ?: PlaybackSource.EXTERNAL.name
        val source = runCatching { PlaybackSource.valueOf(sourceName) }.getOrDefault(PlaybackSource.EXTERNAL)

        val themeStyle = parseThemeStyle(prefs[Keys.THEME_STYLE])
        val legacyMotionSens = (prefs[Keys.MOTION_SENSITIVITY] ?: 0.6f).coerceIn(0.2f, 1.0f)

        PersistedAudioSettings(
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            eqPreset = preset,
            customBandLevels = bands,
            bassBoostStrength = (prefs[Keys.BASS_BOOST] ?: 0).coerceIn(0, 1000),
            motionGesturesEnabled = prefs[Keys.MOTION_ENABLED] ?: true,
            motionSensitivity = legacyMotionSens,
            wristFlickEnabled = prefs[Keys.WRIST_FLICK_ENABLED] ?: (prefs[Keys.MOTION_ENABLED] ?: true),
            wristFlickSensitivity = (prefs[Keys.WRIST_FLICK_SENSITIVITY] ?: legacyMotionSens).coerceIn(0.2f, 1.0f),
            faceDownEnabled = prefs[Keys.FACE_DOWN_ENABLED] ?: (prefs[Keys.MOTION_ENABLED] ?: true),
            faceDownSensitivity = (prefs[Keys.FACE_DOWN_SENSITIVITY] ?: legacyMotionSens).coerceIn(0.2f, 1.0f),
            volUpShortAction = parseAction(prefs[Keys.VOL_UP_SHORT_ACTION], GestureAction.MUTE_TOGGLE),
            volDownShortAction = parseAction(prefs[Keys.VOL_DOWN_SHORT_ACTION], GestureAction.VOLUME_DOWN),
            volUpLongAction = parseAction(prefs[Keys.VOL_UP_LONG_ACTION], GestureAction.PREVIOUS_TRACK),
            volDownLongAction = parseAction(prefs[Keys.VOL_DOWN_LONG_ACTION], GestureAction.NEXT_TRACK),
            wristFlickAction = parseAction(prefs[Keys.WRIST_FLICK_ACTION], GestureAction.NEXT_TRACK),
            faceDownAction = parseAction(prefs[Keys.FACE_DOWN_ACTION], GestureAction.PLAY_PAUSE),
            preferredSource = source,
            themeStyle = themeStyle,
            useAmoledBlack = themeStyle == AppThemeStyle.AMOLED_PURE_BLACK,
            glassBlurIntensity = (prefs[Keys.GLASS_BLUR_INTENSITY] ?: 0.65f).coerceIn(0.0f, 1.0f),
            customFontPath = prefs[Keys.CUSTOM_FONT_PATH]?.takeIf { it.isNotBlank() },
            customFontName = prefs[Keys.CUSTOM_FONT_NAME]?.takeIf { it.isNotBlank() },
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            hapticIntensity = runCatching {
                HapticIntensity.valueOf(prefs[Keys.HAPTIC_INTENSITY] ?: HapticIntensity.MEDIUM.name)
            }.getOrDefault(HapticIntensity.MEDIUM),
            hapticOnTransport = prefs[Keys.HAPTIC_ON_TRANSPORT] ?: true,
            hapticOnSliders = prefs[Keys.HAPTIC_ON_SLIDERS] ?: true,
            hapticOnGestures = prefs[Keys.HAPTIC_ON_GESTURES] ?: true,
            use24HourClock = prefs[Keys.USE_24_HOUR_CLOCK] ?: false,
            dimLockTimeoutSeconds = (prefs[Keys.DIM_LOCK_TIMEOUT_SECONDS] ?: 0).coerceAtLeast(0),
            customAccentHex = prefs[Keys.CUSTOM_ACCENT_HEX]?.takeIf { it.isNotBlank() },
            customFontHex = prefs[Keys.CUSTOM_FONT_HEX]?.takeIf { it.isNotBlank() },
            lyricsTextScale = (prefs[Keys.LYRICS_TEXT_SCALE] ?: 1.0f).coerceIn(0.75f, 1.65f)
        )
    }

    suspend fun updateEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EQ_ENABLED] = enabled }
    }

    suspend fun updateEqPreset(preset: EqPreset, bands: List<Int>) {
        context.dataStore.edit {
            it[Keys.EQ_PRESET] = preset.name
            it[Keys.BAND_LEVELS] = bands.joinToString(",")
        }
    }

    suspend fun updateBassBoost(strength: Int) {
        context.dataStore.edit { it[Keys.BASS_BOOST] = strength.coerceIn(0, 1000) }
    }

    suspend fun updateMotionGestures(enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.MOTION_ENABLED] = enabled
            it[Keys.WRIST_FLICK_ENABLED] = enabled
            it[Keys.FACE_DOWN_ENABLED] = enabled
        }
    }

    suspend fun updateMotionSensitivity(sensitivity: Float) {
        val clamped = sensitivity.coerceIn(0.2f, 1.0f)
        context.dataStore.edit {
            it[Keys.MOTION_SENSITIVITY] = clamped
            it[Keys.WRIST_FLICK_SENSITIVITY] = clamped
            it[Keys.FACE_DOWN_SENSITIVITY] = clamped
        }
    }

    suspend fun updateWristFlickEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WRIST_FLICK_ENABLED] = enabled }
    }

    suspend fun updateWristFlickSensitivity(sensitivity: Float) {
        context.dataStore.edit { it[Keys.WRIST_FLICK_SENSITIVITY] = sensitivity.coerceIn(0.2f, 1.0f) }
    }

    suspend fun updateFaceDownEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FACE_DOWN_ENABLED] = enabled }
    }

    suspend fun updateFaceDownSensitivity(sensitivity: Float) {
        context.dataStore.edit { it[Keys.FACE_DOWN_SENSITIVITY] = sensitivity.coerceIn(0.2f, 1.0f) }
    }

    suspend fun updateVolUpShortAction(action: GestureAction) {
        context.dataStore.edit { it[Keys.VOL_UP_SHORT_ACTION] = action.name }
    }

    suspend fun updateVolDownShortAction(action: GestureAction) {
        context.dataStore.edit { it[Keys.VOL_DOWN_SHORT_ACTION] = action.name }
    }

    suspend fun updateVolUpLongAction(action: GestureAction) {
        context.dataStore.edit { it[Keys.VOL_UP_LONG_ACTION] = action.name }
    }

    suspend fun updateVolDownLongAction(action: GestureAction) {
        context.dataStore.edit { it[Keys.VOL_DOWN_LONG_ACTION] = action.name }
    }

    suspend fun updateWristFlickAction(action: GestureAction) {
        context.dataStore.edit { it[Keys.WRIST_FLICK_ACTION] = action.name }
    }

    suspend fun updateFaceDownAction(action: GestureAction) {
        context.dataStore.edit { it[Keys.FACE_DOWN_ACTION] = action.name }
    }

    suspend fun updatePreferredSource(source: PlaybackSource) {
        context.dataStore.edit { it[Keys.PREFERRED_SOURCE] = source.name }
    }

    suspend fun updateThemeStyle(style: AppThemeStyle) {
        context.dataStore.edit {
            it[Keys.THEME_STYLE] = style.name
            it[Keys.USE_AMOLED_BLACK] = (style == AppThemeStyle.AMOLED_PURE_BLACK)
        }
    }

    suspend fun updateUseAmoledBlack(enabled: Boolean) {
        context.dataStore.edit {
            it[Keys.USE_AMOLED_BLACK] = enabled
            if (enabled) {
                it[Keys.THEME_STYLE] = AppThemeStyle.AMOLED_PURE_BLACK.name
            } else if (it[Keys.THEME_STYLE] == AppThemeStyle.AMOLED_PURE_BLACK.name) {
                it[Keys.THEME_STYLE] = AppThemeStyle.ADAPTIVE_ART.name
            }
        }
    }

    suspend fun updateGlassBlurIntensity(intensity: Float) {
        context.dataStore.edit { it[Keys.GLASS_BLUR_INTENSITY] = intensity.coerceIn(0.0f, 1.0f) }
    }

    suspend fun updateCustomFont(path: String?, name: String?) {
        context.dataStore.edit {
            if (path.isNullOrBlank()) {
                it.remove(Keys.CUSTOM_FONT_PATH)
                it.remove(Keys.CUSTOM_FONT_NAME)
            } else {
                it[Keys.CUSTOM_FONT_PATH] = path
                it[Keys.CUSTOM_FONT_NAME] = name ?: "Custom Font"
            }
        }
    }

    suspend fun updateHapticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun updateHapticIntensity(intensity: HapticIntensity) {
        context.dataStore.edit { it[Keys.HAPTIC_INTENSITY] = intensity.name }
    }

    suspend fun updateHapticOnTransport(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_ON_TRANSPORT] = enabled }
    }

    suspend fun updateHapticOnSliders(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_ON_SLIDERS] = enabled }
    }

    suspend fun updateHapticOnGestures(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_ON_GESTURES] = enabled }
    }

    suspend fun updateUse24HourClock(use24h: Boolean) {
        context.dataStore.edit { it[Keys.USE_24_HOUR_CLOCK] = use24h }
    }

    suspend fun updateDimLockTimeoutSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.DIM_LOCK_TIMEOUT_SECONDS] = seconds.coerceAtLeast(0) }
    }

    suspend fun updateCustomAccentHex(hex: String?, switchToCustomTheme: Boolean = true) {
        context.dataStore.edit {
            if (hex.isNullOrBlank()) {
                it.remove(Keys.CUSTOM_ACCENT_HEX)
            } else {
                it[Keys.CUSTOM_ACCENT_HEX] = hex.trim().uppercase()
                if (switchToCustomTheme &&
                    it[Keys.THEME_STYLE] != AppThemeStyle.AMOLED_PURE_BLACK.name
                ) {
                    it[Keys.THEME_STYLE] = AppThemeStyle.CUSTOM.name
                }
            }
        }
    }

    suspend fun updateCustomFontHex(hex: String?) {
        context.dataStore.edit {
            if (hex.isNullOrBlank()) {
                it.remove(Keys.CUSTOM_FONT_HEX)
            } else {
                it[Keys.CUSTOM_FONT_HEX] = hex.trim().uppercase()
            }
        }
    }

    suspend fun updateLyricsTextScale(scale: Float) {
        context.dataStore.edit {
            it[Keys.LYRICS_TEXT_SCALE] = scale.coerceIn(0.75f, 1.65f)
        }
    }

    suspend fun resetColorsToDefault() {
        context.dataStore.edit {
            it[Keys.THEME_STYLE] = AppThemeStyle.ADAPTIVE_ART.name
            it[Keys.USE_AMOLED_BLACK] = false
            it.remove(Keys.CUSTOM_ACCENT_HEX)
            it.remove(Keys.CUSTOM_FONT_HEX)
        }
    }
}
