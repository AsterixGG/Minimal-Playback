package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.model.HapticIntensity

class HapticFeedbackManager(context: Context) {

    private val vibrator: Vibrator? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }.getOrNull()

    @Volatile
    var enabled: Boolean = true

    @Volatile
    var intensity: HapticIntensity = HapticIntensity.MEDIUM

    @Volatile
    var onTransportEnabled: Boolean = true

    @Volatile
    var onSlidersEnabled: Boolean = true

    @Volatile
    var onGesturesEnabled: Boolean = true

    fun performTransportClick() {
        if (!enabled || !onTransportEnabled) return
        vibratePulse(baseMs = 18L, intensityOverride = intensity)
    }

    fun performSliderTick() {
        if (!enabled || !onSlidersEnabled) return
        vibratePulse(baseMs = 9L, intensityOverride = intensity)
    }

    fun performGestureConfirmation() {
        if (!enabled || !onGesturesEnabled) return
        vibrateDoublePulse(intensity)
    }

    fun previewIntensity(target: HapticIntensity) {
        vibratePulse(baseMs = 20L, intensityOverride = target)
    }

    private fun vibratePulse(baseMs: Long, intensityOverride: HapticIntensity) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val (durationMs, amplitude) = when (intensityOverride) {
            HapticIntensity.LIGHT -> (baseMs * 0.7f).toLong().coerceAtLeast(6L) to 65
            HapticIntensity.MEDIUM -> baseMs to 145
            HapticIntensity.STRONG -> (baseMs * 1.45f).toLong().coerceAtLeast(14L) to 235
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val amp = if (vib.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
                vib.vibrate(VibrationEffect.createOneShot(durationMs, amp))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMs)
            }
        }
    }

    private fun vibrateDoublePulse(intensityOverride: HapticIntensity) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        val (pulseMs, amplitude) = when (intensityOverride) {
            HapticIntensity.LIGHT -> 12L to 75
            HapticIntensity.MEDIUM -> 20L to 160
            HapticIntensity.STRONG -> 30L to 245
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val timings = longArrayOf(0L, pulseMs, 45L, pulseMs)
                val amp = if (vib.hasAmplitudeControl()) amplitude else VibrationEffect.DEFAULT_AMPLITUDE
                val amplitudes = intArrayOf(0, amp, 0, amp)
                vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0L, pulseMs, 45L, pulseMs), -1)
            }
        }
    }
}
