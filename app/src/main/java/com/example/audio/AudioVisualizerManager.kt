package com.example.audio

import android.media.audiofx.Visualizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.hypot
import kotlin.math.min

data class VisualizerFrame(
    val waveformSamples: List<Float> = List(48) { 0f },
    val spectrumBars: List<Float> = List(28) { 0f },
    val bassEnergy: Float = 0f,
    val isHardwareActive: Boolean = false,
    val hasPermission: Boolean = false,
    val errorMessage: String? = null
)

class AudioVisualizerManager {

    companion object {
        private const val TAG = "AudioVisualizerManager"
        private const val WAVEFORM_BUCKETS = 48
        private const val SPECTRUM_BINS = 28
    }

    private var visualizer: Visualizer? = null
    private var currentSessionId: Int = -1
    private var hasRecordAudioPermission: Boolean = false
    private var shouldCapture: Boolean = false

    private val _frame = MutableStateFlow(VisualizerFrame())
    val frame: StateFlow<VisualizerFrame> = _frame.asStateFlow()

    @Synchronized
    fun setPermissionGranted(granted: Boolean) {
        if (hasRecordAudioPermission == granted) return
        hasRecordAudioPermission = granted
        _frame.value = _frame.value.copy(hasPermission = granted, errorMessage = null)
        if (granted && shouldCapture && currentSessionId >= 0) {
            attachInternal(currentSessionId)
        } else if (!granted) {
            releaseVisualizerOnly()
        }
    }

    @Synchronized
    fun updatePlaybackContext(sessionId: Int, isPlaying: Boolean, isLifecycleResumed: Boolean) {
        val targetCapture = isPlaying && isLifecycleResumed
        val sessionChanged = sessionId != currentSessionId
        currentSessionId = sessionId
        shouldCapture = targetCapture

        if (!hasRecordAudioPermission) {
            releaseVisualizerOnly()
            return
        }

        if (targetCapture) {
            if (visualizer == null || sessionChanged) {
                attachInternal(sessionId)
            } else {
                runCatching {
                    if (visualizer?.enabled == false) {
                        visualizer?.enabled = true
                    }
                }
            }
        } else {
            runCatching { visualizer?.enabled = false }
            _frame.value = _frame.value.copy(
                waveformSamples = List(WAVEFORM_BUCKETS) { 0f },
                spectrumBars = List(SPECTRUM_BINS) { 0f },
                bassEnergy = 0f
            )
        }
    }

    private fun attachInternal(sessionId: Int) {
        releaseVisualizerOnly()
        try {
            val viz = Visualizer(sessionId)
            val range = Visualizer.getCaptureSizeRange()
            val desiredSize = 1024.coerceIn(range[0], range[1])
            viz.captureSize = desiredSize

            val rate = (Visualizer.getMaxCaptureRate() / 2).coerceAtLeast(10_000)
            viz.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(
                        v: Visualizer?,
                        waveform: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (waveform == null || waveform.isEmpty()) return
                        val downsampled = downsampleWaveform(waveform)
                        _frame.value = _frame.value.copy(
                            waveformSamples = downsampled,
                            isHardwareActive = true,
                            hasPermission = true,
                            errorMessage = null
                        )
                    }

                    override fun onFftDataCapture(
                        v: Visualizer?,
                        fft: ByteArray?,
                        samplingRate: Int
                    ) {
                        if (fft == null || fft.size < 4) return
                        val (bars, bass) = computeSpectrumAndBass(fft)
                        _frame.value = _frame.value.copy(
                            spectrumBars = bars,
                            bassEnergy = bass,
                            isHardwareActive = true,
                            hasPermission = true,
                            errorMessage = null
                        )
                    }
                },
                rate,
                true,
                true
            )
            viz.enabled = shouldCapture
            visualizer = viz
            _frame.value = _frame.value.copy(
                isHardwareActive = true,
                hasPermission = true,
                errorMessage = null
            )
            Log.d(TAG, "Visualizer attached to audioSessionId=$sessionId (captureSize=$desiredSize)")
        } catch (e: Exception) {
            Log.w(TAG, "Visualizer unavailable on audioSessionId=$sessionId: ${e.message}")
            _frame.value = _frame.value.copy(
                isHardwareActive = false,
                hasPermission = hasRecordAudioPermission,
                errorMessage = "Audio Visualizer restricted on this output session."
            )
        }
    }

    private fun downsampleWaveform(waveform: ByteArray): List<Float> {
        val step = (waveform.size / WAVEFORM_BUCKETS).coerceAtLeast(1)
        return List(WAVEFORM_BUCKETS) { bucket ->
            val start = bucket * step
            val end = min(start + step, waveform.size)
            var sum = 0f
            var count = 0
            for (i in start until end) {
                val centered = ((waveform[i].toInt() and 0xFF) - 128) / 128f
                sum += centered
                count++
            }
            if (count > 0) (sum / count).coerceIn(-1f, 1f) else 0f
        }
    }

    private fun computeSpectrumAndBass(fft: ByteArray): Pair<List<Float>, Float> {
        val n = fft.size
        val numFreqBins = (n / 2).coerceAtLeast(1)
        val magnitudes = FloatArray(numFreqBins)
        // DC component
        magnitudes[0] = kotlin.math.abs(fft[0].toFloat()) / 128f
        for (k in 1 until numFreqBins) {
            val reIdx = k * 2
            val imIdx = reIdx + 1
            if (imIdx < n) {
                val re = fft[reIdx].toFloat()
                val im = fft[imIdx].toFloat()
                magnitudes[k] = (hypot(re, im) / 128f).coerceIn(0f, 1f)
            }
        }

        val bars = List(SPECTRUM_BINS) { idx ->
            // Focus on musically active lower-to-mid-high bins
            val startBin = (idx * numFreqBins) / (SPECTRUM_BINS * 2)
            val endBin = (((idx + 1) * numFreqBins) / (SPECTRUM_BINS * 2)).coerceAtLeast(startBin + 1)
            var peak = 0f
            for (b in startBin until min(endBin, numFreqBins)) {
                if (magnitudes[b] > peak) peak = magnitudes[b]
            }
            (peak * 1.45f).coerceIn(0f, 1f)
        }

        val bassBins = min(6, bars.size)
        var bassSum = 0f
        for (i in 0 until bassBins) {
            bassSum += bars[i]
        }
        val bassEnergy = if (bassBins > 0) (bassSum / bassBins).coerceIn(0f, 1f) else 0f
        return bars to bassEnergy
    }

    @Synchronized
    private fun releaseVisualizerOnly() {
        runCatching {
            visualizer?.enabled = false
            visualizer?.release()
        }
        visualizer = null
        _frame.value = _frame.value.copy(
            waveformSamples = List(WAVEFORM_BUCKETS) { 0f },
            spectrumBars = List(SPECTRUM_BINS) { 0f },
            bassEnergy = 0f,
            isHardwareActive = false
        )
    }

    @Synchronized
    fun release() {
        releaseVisualizerOnly()
    }
}
