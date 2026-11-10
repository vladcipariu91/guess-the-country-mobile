package me.vladcip.guessthecountry.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import me.vladcip.guessthecountry.domain.passport.PassportCover

private val LocalGtcColors = staticCompositionLocalOf<GtcColors> { error("GtcTheme is not provided") }
private val LocalGtcTypography = staticCompositionLocalOf<GtcTypography> { error("GtcTheme is not provided") }

/** Accessors for the Passport design system; valid inside [GtcTheme]. */
object GtcTheme {
    val colors: GtcColors
        @Composable @ReadOnlyComposable get() = LocalGtcColors.current

    val typography: GtcTypography
        @Composable @ReadOnlyComposable get() = LocalGtcTypography.current
}

/**
 * Provides [GtcTheme.colors] and [GtcTheme.typography] for [cover], and wraps [MaterialTheme] with a
 * scheme and typography mapped from them so leftover Material widgets (dialogs) match.
 */
@Composable
fun GtcTheme(
    cover: PassportCover,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = remember(cover, darkTheme) { gtcColors(cover, darkTheme) }
    val typography = rememberGtcTypography()
    val materialColors = remember(colors) { colors.toMaterialColorScheme() }
    val materialTypography = remember(typography) { typography.toMaterialTypography() }
    CompositionLocalProvider(
        LocalGtcColors provides colors,
        LocalGtcTypography provides typography,
    ) {
        MaterialTheme(colorScheme = materialColors, typography = materialTypography, content = content)
    }
}

private fun GtcColors.toMaterialColorScheme(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = cover,
        onPrimary = goldLight,
        secondary = gold,
        onSecondary = ink,
        tertiary = gold,
        onTertiary = ink,
        background = cover,
        onBackground = onCover,
        surface = paper,
        onSurface = ink,
        surfaceVariant = surface,
        onSurfaceVariant = inkMuted,
        surfaceTint = paper,
        surfaceBright = paper,
        surfaceDim = paper,
        surfaceContainerLowest = surface,
        surfaceContainerLow = paper,
        surfaceContainer = paper,
        surfaceContainerHigh = paper,
        surfaceContainerHighest = paper,
        outline = outlineDashed,
        outlineVariant = rule,
        error = stamp.missed,
        onError = paper,
    )
}

/** Uppercase tokens (splash/brand/mono) are deliberately not mapped, as Material cannot upper-case. */
private fun GtcTypography.toMaterialTypography(): Typography = Typography(
    displayLarge = scoreDisplay,
    displayMedium = countDisplay,
    displaySmall = screenTitle,
    headlineLarge = screenTitle,
    headlineMedium = modeTitle,
    headlineSmall = cardTitle,
    titleLarge = cardTitle,
    titleMedium = optionLabel,
    titleSmall = captionStrong,
    bodyLarge = body,
    bodyMedium = bodySmall,
    bodySmall = caption,
    labelLarge = button,
    labelMedium = captionStrong,
    labelSmall = caption,
)
