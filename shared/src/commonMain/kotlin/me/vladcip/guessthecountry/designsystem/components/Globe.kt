package me.vladcip.guessthecountry.designsystem.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import me.vladcip.guessthecountry.designsystem.motion.rememberReduceMotion
import me.vladcip.guessthecountry.designsystem.theme.GtcTheme
import me.vladcip.guessthecountry.designsystem.tokens.Border
import me.vladcip.guessthecountry.designsystem.tokens.Motion
import me.vladcip.guessthecountry.designsystem.tokens.Opacity
import me.vladcip.guessthecountry.designsystem.tokens.Size

/** Line globe in gold (P-Choose cover emblem); [size] is the square it is drawn into. */
@Composable
fun GlobeEmblem(
    modifier: Modifier = Modifier,
    size: Dp = Size.Emblem,
) {
    Icon(
        imageVector = GtcIcons.Globe,
        contentDescription = null,
        tint = GtcTheme.colors.gold,
        modifier = modifier.size(size),
    )
}

/**
 * Splash globe (P-Splash): a world strip scrolls sideways inside a gold-rimmed disc, with a faint
 * outer ring and a radial highlight/shade. One revolution takes [Motion.GlobeRevolutionMillis].
 * It stays still when the OS asks for reduced motion.
 */
@Composable
fun SpinningGlobe(modifier: Modifier = Modifier) {
    val colors = GtcTheme.colors
    val reduceMotion = rememberReduceMotion()
    val revolution: State<Float>? = if (reduceMotion) {
        null
    } else {
        rememberInfiniteTransition(label = "globe").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(Motion.GlobeRevolutionMillis, easing = LinearEasing)),
            label = "globeRevolution",
        )
    }
    val land = remember { WorldGeometry.polygons.map { it.toPath() } }
    val graticule = remember { WorldGeometry.graticule() }

    Box(
        modifier = modifier
            .size(Size.GlobeRing)
            .semantics { contentDescription = "A spinning globe" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(Size.GlobeRing)) {
            val ringStroke = Border.B1_5.toPx()
            drawCircle(
                color = colors.goldLineSoft,
                radius = (size.minDimension - ringStroke) / 2f,
                style = Stroke(ringStroke),
            )

            val globe = Size.Globe.toPx()
            val rim = Border.B2_5.toPx()
            val center = Offset(size.width / 2f, size.height / 2f)
            val origin = Offset(center.x - globe / 2f, center.y - globe / 2f)
            val disc = Path().apply { addOval(Rect(center, globe / 2f)) }

            // The strip is as tall as the disc, drawn twice side by side and slid left by exactly
            // one strip width per revolution, so the loop is seamless.
            val unit = globe / WorldGeometry.HEIGHT
            val stripWidth = WorldGeometry.WIDTH * unit
            val shift = (revolution?.value ?: 0f) * stripWidth
            clipPath(disc) {
                translate(left = origin.x - shift, top = origin.y) {
                    repeat(2) { copy ->
                        translate(left = copy * stripWidth) {
                            scale(unit, unit, pivot = Offset.Zero) {
                                drawWorld(land, graticule, colors.gold)
                            }
                        }
                    }
                }
                drawCircle(
                    brush = Brush.radialGradient(
                        colorStops = arrayOf(
                            0f to colors.globeHighlight,
                            SHADE_CLEAR_STOP to colors.globeHighlight.copy(alpha = 0f),
                            SHADE_CLEAR_STOP to colors.globeShade.copy(alpha = 0f),
                            1f to colors.globeShade,
                        ),
                        center = Offset(origin.x + globe * HIGHLIGHT_X, origin.y + globe * HIGHLIGHT_Y),
                        radius = globe * SHADE_RADIUS,
                    ),
                    radius = globe / 2f,
                    center = center,
                )
            }
            drawCircle(
                color = colors.gold,
                radius = (globe - rim) / 2f,
                center = center,
                style = Stroke(rim),
            )
        }
    }
}

/** Draws the strip in its 360x180 viewBox units; the caller scales the scope. */
private fun DrawScope.drawWorld(land: List<Path>, graticule: Path, gold: Color) {
    drawPath(graticule, gold.copy(alpha = Opacity.Graticule), style = Stroke(GRATICULE_WIDTH))
    land.forEach { drawPath(it, gold) }
}

// Radial shading from P-Splash: `radial-gradient(circle at 36% 32%, ... 0%, clear 45%, shade 100%)`.
// CSS `circle` with no size is farthest-corner: the focus-to-far-corner distance of the square disc
// bounds, hypot(0.64, 0.68) of its width.
private const val HIGHLIGHT_X = 0.36f
private const val HIGHLIGHT_Y = 0.32f
private const val SHADE_CLEAR_STOP = 0.45f
private const val SHADE_RADIUS = 0.934f
private const val GRATICULE_WIDTH = 0.6f

/** World strip artwork from P-Splash.dc.html, in its 360x180 viewBox units. */
internal object WorldGeometry {
    const val WIDTH = 360f
    const val HEIGHT = 180f
    private const val GRATICULE_STEP = 30

    /** Continent outlines as flat x,y pairs. */
    val polygons: List<FloatArray> = listOf(
        floatArrayOf(12f, 25f, 40f, 20f, 85f, 18f, 105f, 28f, 120f, 40f, 100f, 60f, 83f, 72f, 95f, 80f, 75f, 68f, 62f, 57f, 55f, 42f, 30f, 30f),
        floatArrayOf(130f, 8f, 155f, 10f, 140f, 28f, 125f, 20f),
        floatArrayOf(100f, 80f, 120f, 80f, 145f, 97f, 140f, 112f, 122f, 128f, 110f, 145f, 105f, 130f, 108f, 108f, 99f, 95f),
        floatArrayOf(170f, 54f, 171f, 47f, 180f, 40f, 185f, 32f, 200f, 20f, 220f, 22f, 220f, 45f, 205f, 54f, 192f, 52f),
        floatArrayOf(175f, 32f, 181f, 38f, 175f, 40f, 172f, 36f),
        floatArrayOf(163f, 75f, 170f, 60f, 190f, 53f, 212f, 59f, 223f, 78f, 231f, 79f, 220f, 105f, 212f, 120f, 200f, 125f, 192f, 107f, 188f, 86f, 172f, 85f),
        floatArrayOf(220f, 22f, 250f, 15f, 290f, 13f, 320f, 18f, 360f, 22f, 340f, 32f, 320f, 40f, 302f, 60f, 288f, 70f, 280f, 82f, 260f, 82f, 252f, 70f, 237f, 65f, 228f, 60f, 220f, 45f),
        floatArrayOf(310f, 59f, 321f, 45f, 323f, 50f, 315f, 57f),
        floatArrayOf(294f, 112f, 310f, 102f, 322f, 101f, 333f, 117f, 326f, 129f, 295f, 124f),
    )

    /** Lines every 30 units in both directions (the canvas leaves out the frame edges). */
    fun graticule(): Path = Path().apply {
        for (y in GRATICULE_STEP until HEIGHT.toInt() step GRATICULE_STEP) {
            moveTo(0f, y.toFloat())
            lineTo(WIDTH, y.toFloat())
        }
        for (x in GRATICULE_STEP until WIDTH.toInt() step GRATICULE_STEP) {
            moveTo(x.toFloat(), 0f)
            lineTo(x.toFloat(), HEIGHT)
        }
    }
}

private fun FloatArray.toPath(): Path {
    val points = this
    return Path().apply {
        moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
        close()
    }
}
