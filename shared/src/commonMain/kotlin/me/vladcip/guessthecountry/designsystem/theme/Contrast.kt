package me.vladcip.guessthecountry.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

/**
 * WCAG 2.x contrast ratio of [foreground] on [background] (1.0 to 21.0).
 * A translucent [foreground] is first composited over [background]; the background must be opaque.
 */
fun contrastRatio(foreground: Color, background: Color): Double {
    val fg = foreground.compositeOver(background)
    val l1 = fg.luminance().toDouble()
    val l2 = background.luminance().toDouble()
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}
