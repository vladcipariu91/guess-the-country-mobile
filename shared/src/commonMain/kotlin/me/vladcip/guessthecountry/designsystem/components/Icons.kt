package me.vladcip.guessthecountry.designsystem.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import me.vladcip.guessthecountry.designsystem.tokens.Size

/**
 * Line icons from the Passport design canvas, drawn on a 24-unit grid. They are stroke-only and
 * black; tint them with `Icon(tint = ...)`.
 */
object GtcIcons {
    /** Globe emblem: circle, meridian ellipse and graticule (P-Menu header, P-Choose cover). */
    val Globe: ImageVector by lazy {
        icon(
            "Globe",
            strokeWidth = 1f,
            "M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0z",
            "M7.5 12a4.5 10 0 1 0 9 0a4.5 10 0 1 0 -9 0z",
            "M2 12h20M3.5 7h17M3.5 17h17M12 2v20",
        )
    }

    /** Location pin (P-Choose suggestion note). */
    val Pin: ImageVector by lazy {
        icon(
            "Pin",
            strokeWidth = 2f,
            "M12 21s-7-6.2-7-11.5A7 7 0 0 1 19 9.5C19 14.8 12 21 12 21z",
            "M9.5 9.5a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0z",
        )
    }

    /** Selected tick (P-Choose swatch). */
    val Check: ImageVector by lazy {
        icon("Check", strokeWidth = 2.6f, "M5 12.5l4.5 4.5L19 7.5")
    }

    /** Passport booklet (P-Menu "My passport"). */
    val Passport: ImageVector by lazy {
        icon(
            "Passport",
            strokeWidth = 1.8f,
            "M7 3h10a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2H7a2 2 0 0 1 -2 -2V5a2 2 0 0 1 2 -2z",
            "M9 10a3 3 0 1 0 6 0a3 3 0 1 0 -6 0z",
            "M9 16h6",
        )
    }

    private fun icon(name: String, strokeWidth: Float, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = Size.Icon,
            defaultHeight = Size.Icon,
            viewportWidth = GRID,
            viewportHeight = GRID,
        ).apply {
            paths.forEach { data ->
                addPath(
                    pathData = PathParser().parsePathString(data).toNodes(),
                    fill = null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    private const val GRID = 24f
}
