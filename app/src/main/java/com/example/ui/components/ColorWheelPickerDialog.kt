package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.LocalAppFontColors
import com.example.ui.theme.ObsidianCardElevated
import com.example.ui.theme.colorToHexString
import com.example.ui.theme.contrastOnColor
import com.example.ui.theme.parseColorHex
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ColorWheelPickerDialog(
    title: String,
    initialColor: Color,
    accentColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    val fontColors = LocalAppFontColors.current
    val initialHsv = remember(initialColor) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor.toArgb(), it) }
    }

    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var brightness by remember { mutableFloatStateOf(initialHsv[2]) }
    var hexInput by remember { mutableStateOf(colorToHexString(initialColor)) }

    val selectedColor = remember(hue, saturation, brightness) {
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    }
    val isHexValid = remember(hexInput) { parseColorHex(hexInput) != null }

    fun syncHexFromHsv(h: Float, s: Float, v: Float) {
        val color = Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
        hexInput = colorToHexString(color)
    }

    fun updateFromWheelOffset(offset: Offset, width: Float, height: Float) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = (minOf(width, height) / 2f).coerceAtLeast(1f)
        val dx = offset.x - cx
        val dy = offset.y - cy
        val angleDeg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
            .let { if (it < 0f) it + 360f else it }
        val sat = (hypot(dx, dy) / radius).coerceIn(0f, 1f)
        hue = angleDeg
        saturation = sat
        syncHexFromHsv(angleDeg, sat, brightness)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = fontColors.primary,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, HairlineBorder, RoundedCornerShape(24.dp))
                .testTag("color_wheel_picker_dialog")
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        color = fontColors.primary
                    )

                    // Live Preview Swatch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(selectedColor)
                                .border(1.5.dp, HairlineBorder, CircleShape)
                                .testTag("color_picker_preview_swatch")
                        )
                        Text(
                            text = colorToHexString(selectedColor),
                            style = MaterialTheme.typography.labelMedium,
                            color = fontColors.secondary
                        )
                    }
                }

                // Interactive Circular HSV Color Wheel
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(210.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    updateFromWheelOffset(
                                        offset,
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    )
                                }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    updateFromWheelOffset(
                                        change.position,
                                        size.width.toFloat(),
                                        size.height.toFloat()
                                    )
                                }
                            }
                            .testTag("hsv_color_wheel")
                    ) {
                        val radius = size.minDimension / 2f
                        val center = Offset(size.width / 2f, size.height / 2f)

                        // 1. Hue sweep
                        drawCircle(
                            brush = Brush.sweepGradient(
                                colors = listOf(
                                    Color.Red,
                                    Color.Yellow,
                                    Color.Green,
                                    Color.Cyan,
                                    Color.Blue,
                                    Color.Magenta,
                                    Color.Red
                                ),
                                center = center
                            ),
                            radius = radius,
                            center = center
                        )

                        // 2. Radial white saturation gradient
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White, Color.Transparent),
                                center = center,
                                radius = radius
                            ),
                            radius = radius,
                            center = center
                        )

                        // 3. Brightness / Value darkening overlay
                        if (brightness < 1f) {
                            drawCircle(
                                color = Color.Black.copy(alpha = (1f - brightness).coerceIn(0f, 1f)),
                                radius = radius,
                                center = center
                            )
                        }

                        // Subtle rim border
                        drawCircle(
                            color = Color.White.copy(alpha = 0.25f),
                            radius = radius,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx())
                        )

                        // Selector thumb
                        val angleRad = Math.toRadians(hue.toDouble())
                        val thumbX = center.x + cos(angleRad).toFloat() * saturation * radius
                        val thumbY = center.y + sin(angleRad).toFloat() * saturation * radius
                        val thumbCenter = Offset(thumbX, thumbY)

                        drawCircle(
                            color = selectedColor,
                            radius = 11.dp.toPx(),
                            center = thumbCenter
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 11.dp.toPx(),
                            center = thumbCenter,
                            style = Stroke(width = 2.5.dp.toPx())
                        )
                        drawCircle(
                            color = Color.Black.copy(alpha = 0.45f),
                            radius = 13.dp.toPx(),
                            center = thumbCenter,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }

                // Brightness / Value Slider
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.brightness_label),
                            style = MaterialTheme.typography.labelLarge,
                            color = fontColors.secondary
                        )
                        Text(
                            text = "${(brightness * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = accentColor
                        )
                    }
                    Slider(
                        value = brightness,
                        onValueChange = { newValue ->
                            brightness = newValue.coerceIn(0.05f, 1f)
                            syncHexFromHsv(hue, saturation, brightness)
                        },
                        valueRange = 0.05f..1f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("color_brightness_slider"),
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = ObsidianCardElevated
                        )
                    )
                }

                // Synchronized Hex Code Input
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { typed ->
                        val sanitized = typed.uppercase().filter { it == '#' || it in '0'..'9' || it in 'A'..'F' }
                            .take(9)
                        hexInput = sanitized
                        val parsed = parseColorHex(sanitized)
                        if (parsed != null) {
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(parsed.toArgb(), hsv)
                            hue = hsv[0]
                            saturation = hsv[1]
                            brightness = hsv[2]
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.hex_code_label),
                            color = fontColors.secondary
                        )
                    },
                    placeholder = {
                        Text("#FF9800", color = fontColors.tertiary)
                    },
                    singleLine = true,
                    isError = hexInput.isNotEmpty() && !isHexValid,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = fontColors.primary,
                        unfocusedTextColor = fontColors.primary,
                        focusedBorderColor = accentColor,
                        unfocusedBorderColor = HairlineBorder,
                        cursorColor = accentColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("color_hex_input")
                )

                // Dialog Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("color_picker_cancel")
                    ) {
                        Text(
                            text = stringResource(R.string.cancel_label),
                            color = fontColors.primary
                        )
                    }

                    Button(
                        onClick = {
                            val finalHex = colorToHexString(selectedColor)
                            onColorSelected(finalHex)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = selectedColor,
                            contentColor = contrastOnColor(selectedColor)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("color_picker_apply")
                    ) {
                        Text(
                            text = stringResource(R.string.apply_color),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
