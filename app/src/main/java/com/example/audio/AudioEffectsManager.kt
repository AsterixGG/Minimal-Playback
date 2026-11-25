package com.example.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.util.Log
import com.example.model.EqBandInfo
import com.example.model.EqPreset
import com.example.model.EqualizerState
import com.example.model.defaultEqBands
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioEffectsManager {

    companion object {
        private const val TAG = "AudioEffectsManager"
    }

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var currentSessionId: Int = 0

    private val _state = MutableStateFlow(EqualizerState())
    val state: StateFlow<EqualizerState> = _state.asStateFlow()

    @Synchronized
    fun attachToSession(sessionId: Int = 0) {
        if (equalizer != null && currentSessionId == sessionId) return
        release()
        currentSessionId = sessionId

        try {
            val eq = Equalizer(0, sessionId)
            val bandsCount = eq.numberOfBands.toInt().coerceAtLeast(0)
            val range = eq.bandLevelRange
            val minMb = range[0]
            val maxMb = range[1]

            val bandList = (0 until bandsCount).map { idx ->
                val band = idx.toShort()
                val freqHz = (eq.getCenterFreq(band) / 1000)
                val level = eq.getBandLevel(band)
                EqBandInfo(
                    bandIndex = band,
                    centerFreqHz = freqHz,
                    minLevelMb = minMb,
                    maxLevelMb = maxMb,
                    levelMb = level
                )
            }
            eq.enabled = _state.value.enabled
            equalizer = eq

            val bb = runCatching {
                BassBoost(0, sessionId).apply {
                    enabled = _state.value.enabled
                    if (strengthSupported) {
                        setStrength(_state.value.bassBoostStrength.toShort())
                    }
                }
            }.getOrNull()
            bassBoost = bb

            _state.value = _state.value.copy(
                isHardwareAvailable = true,
                bands = bandList.ifEmpty { defaultEqBands() }
            )
            applyCurrentLevelsToHardware()
            Log.d(TAG, "Attached Equalizer & BassBoost to audioSessionId=$sessionId (${bandList.size} bands)")
        } catch (e: Exception) {
            Log.w(TAG, "Hardware Equalizer unavailable on sessionId=$sessionId: ${e.message}")
            _state.value = _state.value.copy(isHardwareAvailable = false)
        }
    }

    @Synchronized
    fun setEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(enabled = enabled)
        runCatching { equalizer?.enabled = enabled }
        runCatching { bassBoost?.enabled = enabled }
    }

    @Synchronized
    fun applyPreset(preset: EqPreset, customSavedBands: List<Int>? = null) {
        val currentBands = _state.value.bands
        val targetLevels: List<Short> = when (preset) {
            EqPreset.FLAT -> currentBands.map { 0.toShort() }
            EqPreset.BASS_BOOST -> currentBands.mapIndexed { idx, b ->
                when (idx) {
                    0 -> (b.maxLevelMb * 0.72f).toInt().toShort()
                    1 -> (b.maxLevelMb * 0.45f).toInt().toShort()
                    2 -> 0.toShort()
                    3 -> (b.maxLevelMb * 0.1f).toInt().toShort()
                    else -> (b.maxLevelMb * 0.18f).toInt().toShort()
                }
            }
            EqPreset.VOCAL -> currentBands.mapIndexed { idx, b ->
                when (idx) {
                    0 -> (b.minLevelMb * 0.25f).toInt().toShort()
                    1 -> (b.maxLevelMb * 0.15f).toInt().toShort()
                    2 -> (b.maxLevelMb * 0.55f).toInt().toShort()
                    3 -> (b.maxLevelMb * 0.48f).toInt().toShort()
                    else -> (b.maxLevelMb * 0.15f).toInt().toShort()
                }
            }
            EqPreset.TREBLE -> currentBands.mapIndexed { idx, b ->
                when (idx) {
                    0 -> 0.toShort()
                    1 -> 0.toShort()
                    2 -> (b.maxLevelMb * 0.2f).toInt().toShort()
                    3 -> (b.maxLevelMb * 0.55f).toInt().toShort()
                    else -> (b.maxLevelMb * 0.78f).toInt().toShort()
                }
            }
            EqPreset.CUSTOM -> currentBands.mapIndexed { idx, b ->
                val raw = customSavedBands?.getOrNull(idx) ?: b.levelMb.toInt()
                raw.coerceIn(b.minLevelMb.toInt(), b.maxLevelMb.toInt()).toShort()
            }
        }

        val updatedBands = currentBands.mapIndexed { idx, band ->
            band.copy(levelMb = targetLevels.getOrElse(idx) { 0 })
        }
        _state.value = _state.value.copy(
            preset = preset,
            bands = updatedBands
        )
        applyCurrentLevelsToHardware()
    }

    @Synchronized
    fun setBandLevel(bandIndex: Short, levelMb: Short) {
        val updatedBands = _state.value.bands.map { band ->
            if (band.bandIndex == bandIndex) {
                val clamped = levelMb.coerceIn(band.minLevelMb, band.maxLevelMb)
                runCatching { equalizer?.setBandLevel(bandIndex, clamped) }
                band.copy(levelMb = clamped)
            } else {
                band
            }
        }
        _state.value = _state.value.copy(
            preset = EqPreset.CUSTOM,
            bands = updatedBands
        )
    }

    @Synchronized
    fun setBassBoostStrength(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        _state.value = _state.value.copy(bassBoostStrength = clamped)
        runCatching {
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength(clamped.toShort())
                }
            }
        }
    }

    private fun applyCurrentLevelsToHardware() {
        val eq = equalizer ?: return
        _state.value.bands.forEach { band ->
            runCatching {
                eq.setBandLevel(band.bandIndex, band.levelMb)
            }
        }
    }

    @Synchronized
    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        equalizer = null
        bassBoost = null
    }
}
