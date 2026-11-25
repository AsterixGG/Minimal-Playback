package com.example.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.model.AppThemeStyle

private val MinimalShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)

data class ResolvedThemePalette(
    val primaryAccent: Color,
    val secondaryAccent: Color,
    val voidBackground: Color,
    val surfaceBackground: Color,
    val cardBackground: Color
)

data class AppFontColorPalette(
    val primary: Color = TextPrimary,
    val secondary: Color = TextSecondary,
    val tertiary: Color = TextTertiary,
    val isCustom: Boolean = false
)

val LocalAppFontColors = staticCompositionLocalOf { AppFontColorPalette() }

fun parseColorHex(rawHex: String?): Color? {
    if (rawHex.isNullOrBlank()) return null
    val cleaned = rawHex.trim().removePrefix("#")
    return when (cleaned.length) {
        6 -> {
            val rgb = cleaned.toLongOrNull(16) ?: return null
            Color((0xFF000000L or rgb).toInt())
        }
        8 -> {
            val argb = cleaned.toLongOrNull(16) ?: return null
            Color(argb.toInt())
        }
        else -> null
    }
}

fun colorToHexString(color: Color): String {
    val argb = color.toArgb()
    return String.format("#%06X", argb and 0xFFFFFF)
}

fun contrastOnColor(background: Color): Color {
    return if (background.luminance() > 0.48f) Color(0xFF07090D) else Color(0xFFF8FAFC)
}

fun resolveThemePalette(
    themeStyle: AppThemeStyle,
    useAmoledBlack: Boolean = false,
    trackPrimary: Color,
    trackSecondary: Color,
    customAccentColor: Color? = null
): ResolvedThemePalette {
    return when (themeStyle) {
        // Dynamic album-art colors ONLY apply when ADAPTIVE_ART is explicitly active.
        AppThemeStyle.ADAPTIVE_ART -> ResolvedThemePalette(
            primaryAccent = trackPrimary,
            secondaryAccent = trackSecondary,
            voidBackground = ObsidianVoid,
            surfaceBackground = ObsidianSurface,
            cardBackground = ObsidianCard
        )
        // Pure #000000 surfaces everywhere with static accent color across song changes.
        AppThemeStyle.AMOLED_PURE_BLACK -> {
            val staticAccent = customAccentColor ?: CyanPulse
            val staticSecondary = if (customAccentColor != null) {
                customAccentColor.copy(alpha = 0.78f)
            } else {
                AmberGlow
            }
            ResolvedThemePalette(
                primaryAccent = staticAccent,
                secondaryAccent = staticSecondary,
                voidBackground = Color.Black,
                surfaceBackground = Color.Black,
                cardBackground = Color.Black
            )
        }
        AppThemeStyle.MIDNIGHT_BLUE -> ResolvedThemePalette(
            primaryAccent = MidnightAccent,
            secondaryAccent = CyanPulse,
            voidBackground = MidnightVoid,
            surfaceBackground = MidnightSurface,
            cardBackground = MidnightCard
        )
        AppThemeStyle.CRIMSON_DARK -> ResolvedThemePalette(
            primaryAccent = CrimsonAccent,
            secondaryAccent = AmberGlow,
            voidBackground = CrimsonVoid,
            surfaceBackground = CrimsonSurface,
            cardBackground = CrimsonCard
        )
        AppThemeStyle.EMERALD_DARK -> ResolvedThemePalette(
            primaryAccent = EmeraldLive,
            secondaryAccent = CyanPulse,
            voidBackground = EmeraldVoid,
            surfaceBackground = EmeraldSurface,
            cardBackground = EmeraldCard
        )
        AppThemeStyle.CUSTOM -> {
            val customAccent = customAccentColor ?: CyanPulse
            val customSecondary = customAccent.copy(alpha = 0.76f)
            ResolvedThemePalette(
                primaryAccent = customAccent,
                secondaryAccent = customSecondary,
                voidBackground = ObsidianVoid,
                surfaceBackground = ObsidianSurface,
                cardBackground = ObsidianCard
            )
        }
    }
}

@Composable
fun MinimalPlaybackTheme(
    themeStyle: AppThemeStyle = AppThemeStyle.ADAPTIVE_ART,
    dynamicAccent: Color = CyanPulse,
    dynamicSecondary: Color = AmberGlow,
    useAmoledBlack: Boolean = false,
    customAccentHex: String? = null,
    customFontHex: String? = null,
    customFontFamily: FontFamily? = null,
    content: @Composable () -> Unit
) {
    val customAccentColor = remember(customAccentHex) { parseColorHex(customAccentHex) }
    val customFontColor = remember(customFontHex) { parseColorHex(customFontHex) }

    val palette = resolveThemePalette(
        themeStyle = themeStyle,
        useAmoledBlack = useAmoledBlack,
        trackPrimary = dynamicAccent,
        trackSecondary = dynamicSecondary,
        customAccentColor = customAccentColor
    )

    val animatedPrimary by animateColorAsState(
        targetValue = palette.primaryAccent,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "primaryAccent"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = palette.secondaryAccent,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "secondaryAccent"
    )

    val fontPalette = remember(customFontColor) {
        if (customFontColor != null) {
            AppFontColorPalette(
                primary = customFontColor,
                secondary = customFontColor.copy(alpha = 0.72f),
                tertiary = customFontColor.copy(alpha = 0.50f),
                isCustom = true
            )
        } else {
            AppFontColorPalette()
        }
    }

    val colorScheme = darkColorScheme(
        primary = animatedPrimary,
        onPrimary = contrastOnColor(animatedPrimary),
        secondary = animatedSecondary,
        onSecondary = contrastOnColor(animatedSecondary),
        tertiary = EmeraldLive,
        background = palette.voidBackground,
        onBackground = fontPalette.primary,
        surface = palette.surfaceBackground,
        onSurface = fontPalette.primary,
        surfaceVariant = palette.cardBackground,
        onSurfaceVariant = fontPalette.secondary,
        outline = HairlineBorder,
        error = CoralAlert
    )

    val typography = remember(customFontFamily) {
        buildMinimalPlaybackTypography(customFontFamily)
    }

    CompositionLocalProvider(
        LocalAppFontColors provides fontPalette,
        LocalContentColor provides fontPalette.primary
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = MinimalShapes,
            content = content
        )
    }
}
