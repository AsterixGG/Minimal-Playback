package com.example.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AmberGlow
import com.example.ui.theme.CyanPulse
import kotlin.math.max
import kotlin.math.min

data class ExtractedTrackColors(
    val primary: Color = CyanPulse,
    val secondary: Color = AmberGlow
)

object ColorExtractor {
    fun extract(bitmap: Bitmap?): ExtractedTrackColors {
        if (bitmap == null || bitmap.isRecycled) {
            return ExtractedTrackColors()
        }
        return try {
            val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
                bitmap.copy(Bitmap.Config.ARGB_8888, false)
            } else {
                bitmap
            } ?: return ExtractedTrackColors()

            val scaled = Bitmap.createScaledBitmap(softwareBitmap, 24, 24, true)
            val width = scaled.width
            val height = scaled.height
            var bestPrimaryScore = -1f
            var bestPrimaryColor = CyanPulse
            var bestSecondaryScore = -1f
            var bestSecondaryColor = AmberGlow

            val hsv = FloatArray(3)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val pixel = scaled.getPixel(x, y)
                    val a = (pixel ushr 24) and 0xFF
                    if (a < 180) continue
                    android.graphics.Color.colorToHSV(pixel, hsv)
                    val hue = hsv[0]
                    val sat = hsv[1]
                    val value = hsv[2]
                    if (value < 0.22f || sat < 0.2f) continue

                    // Boost bright saturated colors suitable for dark-mode accents
                    val score = sat * 1.4f + (1f - kotlin.math.abs(value - 0.72f))
                    val liftedValue = max(0.68f, min(0.95f, value + 0.18f))
                    val liftedSat = max(0.45f, min(0.92f, sat))
                    val argb = android.graphics.Color.HSVToColor(floatArrayOf(hue, liftedSat, liftedValue))

                    if (score > bestPrimaryScore) {
                        bestPrimaryScore = score
                        bestPrimaryColor = Color(argb)
                    } else if (score > bestSecondaryScore && kotlin.math.abs(hue - colorHue(bestPrimaryColor)) > 35f) {
                        bestSecondaryScore = score
                        bestSecondaryColor = Color(argb)
                    }
                }
            }
            if (scaled != softwareBitmap) scaled.recycle()
            if (softwareBitmap != bitmap) softwareBitmap.recycle()

            ExtractedTrackColors(
                primary = bestPrimaryColor,
                secondary = if (bestSecondaryScore > 0f) bestSecondaryColor else bestPrimaryColor.copy(alpha = 0.75f)
            )
        } catch (_: Exception) {
            ExtractedTrackColors()
        }
    }

    private fun colorHue(color: Color): Float {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt(),
            hsv
        )
        return hsv[0]
    }
}

fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

fun formatCountdownMs(ms: Long): String {
    val totalSeconds = ((ms.coerceAtLeast(0L) + 999L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
