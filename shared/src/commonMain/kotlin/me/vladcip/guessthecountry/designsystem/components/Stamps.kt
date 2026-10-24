package me.vladcip.guessthecountry.designsystem.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import me.vladcip.guessthecountry.designsystem.theme.GtcTheme
import me.vladcip.guessthecountry.designsystem.tokens.Border
import me.vladcip.guessthecountry.designsystem.tokens.Opacity
import me.vladcip.guessthecountry.designsystem.tokens.Radius
import me.vladcip.guessthecountry.designsystem.tokens.Size
import me.vladcip.guessthecountry.designsystem.tokens.Space

/** The stamp families from P-Stamps. The shape follows the style; the ink follows [ink]. */
enum class StampStyle {
    /** 5 pts, gold double ring with ring text. */
    FirstClue,

    /** 4 pts, blue single heavy ring with ring text. */
    Admitted,

    /** 3 pts, green rectangle. */
    Entry,

    /** 2 pts, violet octagon. */
    Transit,

    /** 1 pt, black oval. */
    Capital,

    /** 0 pts, red double-border rectangle. */
    Missed,

    /** Flag round, correct: small blue rectangle. */
    Cleared,

    /** New country: green pill. */
    FirstVisit,

    /** Daily postmark: black circle with wavy lines. */
    Postmark,
}

/** The ink colour of this stamp, from the theme's stamp palette. */
@Composable
fun StampStyle.ink(): Color {
    val stamp = GtcTheme.colors.stamp
    return when (this) {
        StampStyle.FirstClue -> stamp.tier5
        StampStyle.Admitted -> stamp.tier4
        StampStyle.Entry -> stamp.tier3
        StampStyle.Transit -> stamp.tier2
        StampStyle.Capital -> stamp.tier1
        StampStyle.Missed -> stamp.missed
        StampStyle.Cleared -> stamp.tier4
        StampStyle.FirstVisit -> stamp.tier3
        StampStyle.Postmark -> stamp.tier1
    }
}

/**
 * What a stamp says. [title] is the big word (usually the country), [topLine] the small line above
 * it (on ring stamps it runs around the ring) and [bottomLine] the small line below (points, date).
 * Everything is drawn upper-case. The Postmark has no bottom line.
 */
@Immutable
data class StampSpec(
    val style: StampStyle,
    val title: String,
    val topLine: String,
    val bottomLine: String,
)

/**
 * A rubber stamp. [size] is its width; ring, octagon, oval and postmark shapes keep their canvas
 * proportions, while the rectangle and pill shapes size to their text and scale their type and
 * border with [size] (their canvas look is at [Size.StampMedium]). [tiltDegrees] rotates the
 * drawing about its centre without changing its layout size.
 *
 * Long text is fitted, never overprinted: titles shrink to a width that fits their shape (down to
 * [MIN_TITLE_SCALE] of the canvas size) and then end in an ellipsis. Rectangle and pill stamps are
 * capped at [maxWidth], or [RECT_MAX_WIDTH_FACTOR] x [size] when unspecified.
 *
 * In light mode ink multiplies into the page beneath at [Opacity.Stamp]; in dark mode it is drawn
 * as a plain overlay. The blend only reaches what is drawn in the same layer, so it is lost when an
 * ancestor puts the stamp in its own graphics layer (as `stampSlam` does while it animates).
 */
@Composable
fun Stamp(
    spec: StampSpec,
    modifier: Modifier = Modifier,
    size: Dp = Size.StampMedium,
    tiltDegrees: Float = 0f,
    maxWidth: Dp = Dp.Unspecified,
) {
    val colors = GtcTheme.colors
    val typography = GtcTheme.typography
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val ink = spec.style.ink()
    val paint = remember(colors.isDark) {
        Paint().apply {
            alpha = Opacity.Stamp
            blendMode = if (colors.isDark) BlendMode.SrcOver else BlendMode.Multiply
        }
    }
    val description = listOf(spec.title, spec.bottomLine).filter { it.isNotBlank() }.joinToString(", ")
    val semantic = Modifier.semantics {
        contentDescription = description
        role = Role.Image
    }

    val upper = remember(spec) {
        StampSpec(spec.style, spec.title.uppercase(), spec.topLine.uppercase(), spec.bottomLine.uppercase())
    }
    val monoBase = typography.monoMicro
    val titleBase = typography.modeTitle

    val rectLook = rectLookOf(spec.style)
    if (rectLook != null) {
        val scale = size / Size.StampMedium
        val cap = if (maxWidth == Dp.Unspecified) size * RECT_MAX_WIDTH_FACTOR else maxWidth
        val body = remember(upper, scale, cap, density, monoBase, titleBase, measurer) {
            measureRect(
                upper, rectLook, scale, cap, density, measurer, monoBase,
                if (rectLook.pill) typography.cardTitle else titleBase,
            )
        }
        Canvas(modifier.then(semantic).size(with(density) { body.width.toDp() }, with(density) { body.height.toDp() })) {
            stampLayer(paint, tiltDegrees) { drawRectStamp(body, rectLook, ink) }
        }
        return
    }

    val box = viewBoxOf(spec.style)
    val height = size * (box.height / box.width)
    val large = size >= Size.StampLarge
    Canvas(modifier.then(semantic).size(size, height)) {
        val scale = this.size.width / box.width
        val pen = StampPen(this, measurer, ink, monoBase, titleBase, scale)
        stampLayer(paint, tiltDegrees) {
            when (upper.style) {
                StampStyle.FirstClue -> pen.firstClue(upper)
                StampStyle.Admitted -> pen.admitted(upper, large)
                StampStyle.Transit -> pen.transit(upper)
                StampStyle.Capital -> pen.capital(upper)
                StampStyle.Postmark -> pen.postmark(upper)
                StampStyle.Entry, StampStyle.Missed, StampStyle.Cleared, StampStyle.FirstVisit -> Unit
            }
        }
    }
}

/** Draws [block] as one group: tilted about the centre, then composited with the stamp's blend. */
private fun DrawScope.stampLayer(paint: Paint, tiltDegrees: Float, block: DrawScope.() -> Unit) {
    val canvas = drawContext.canvas
    // Generous bounds so a tilted stamp is not clipped by its own unrotated box.
    canvas.saveLayer(Rect(-size.width, -size.height, size.width * 2f, size.height * 2f), paint)
    rotate(tiltDegrees, center) { block() }
    canvas.restore()
}

// ---------------------------------------------------------------------------------------------
// Stamp-internal geometry. All numbers are SVG viewBox units from the design canvas
// (P-Stamps, P-Results and P-Correct .dc.html) and are scaled by `canvasWidth / viewBoxWidth`.
// ---------------------------------------------------------------------------------------------

private class ViewBox(val width: Float, val height: Float)

private fun viewBoxOf(style: StampStyle): ViewBox = when (style) {
    StampStyle.Capital -> ViewBox(160f, 110f)
    StampStyle.Postmark -> ViewBox(160f, 100f)
    else -> ViewBox(120f, 120f)
}

// Ring stamps: radii in a 120x120 box.
private const val RING_CENTER = 60f
private const val RING_OUTER = 56f
private const val RING_SECOND = 52f
private const val RING_LARGE_INNER = 50.5f
private const val RING_CORE = 32f
private const val RING_TEXT_RADIUS = 41f
private const val RING_TEXT_SIZE = 8.6f
private const val RING_TEXT_TRACKING = 1.6f
private const val RING_TITLE_Y = 58f
private const val RING_BOTTOM_Y = 75f
private const val RING_BOTTOM_Y_LARGE = 76f // P-Correct; P-Stamps and P-Results use 75
private const val RING_TITLE_SIZE = 14f
private const val RING_TITLE_SIZE_LARGE = 15f
private const val RING_BOTTOM_SIZE = 12f
private const val RING_BOTTOM_SIZE_LARGE = 13f
private const val HEAVY_STROKE = 3.2f
private const val FIRST_CLUE_STROKE = 2f
private const val HAIRLINE = 1f

// Widest a centred line may be, in viewBox units, before it is shrunk. Each is the room inside the
// shape at that line's height, less a margin: ring = the r32 core (64 across); octagon = the inner
// polygon (92 across at y=66); oval = 80% of the inner ellipse (136 across); postmark = the circle.
private const val RING_LINE_MAX = 60f
private const val OCTAGON_TITLE_MAX = 80f
private const val OCTAGON_LINE_MAX = 88f
private const val OVAL_TITLE_MAX = 108f
private const val OVAL_LINE_MAX = 120f
private const val POSTMARK_LINE_MAX = 56f

/** The smallest a title is shrunk to (as a fraction of its canvas size) before it is ellipsised. */
internal const val MIN_TITLE_SCALE = 0.6f

/** Rectangle and pill stamps are at most this many times [Size.StampMedium]-relative `size` wide. */
internal const val RECT_MAX_WIDTH_FACTOR = 1.5f

/**
 * The scale (1 = canvas size) at which a line of [textWidth] fits in [maxWidth]: 1 when it already
 * fits, otherwise the ratio, but never below [minScale]. A result of [minScale] with text still
 * wider than [maxWidth] means the text must also be ellipsised, see [needsEllipsis].
 */
internal fun fitTitleScale(textWidth: Float, maxWidth: Float, minScale: Float): Float = when {
    textWidth <= 0f || textWidth <= maxWidth -> 1f
    else -> max(minScale, maxWidth / textWidth)
}

/** Whether text of [textWidth] still overflows [maxWidth] after scaling by [scale] (0.1% float slack). */
internal fun needsEllipsis(textWidth: Float, maxWidth: Float, scale: Float): Boolean =
    textWidth * scale > maxWidth * 1.001f

// Transit octagon (P-Stamps / P-Results), outer then inner, as x,y pairs.
private val OctagonOuter = floatArrayOf(38f, 6f, 82f, 6f, 114f, 38f, 114f, 82f, 82f, 114f, 38f, 114f, 6f, 82f, 6f, 38f)
private val OctagonInner = floatArrayOf(41f, 14f, 79f, 14f, 106f, 41f, 106f, 79f, 79f, 106f, 41f, 106f, 14f, 79f, 14f, 41f)

// Capital oval in a 160x110 box.
private const val OVAL_CX = 80f
private const val OVAL_CY = 55f
private const val OVAL_OUTER_RX = 76f
private const val OVAL_OUTER_RY = 50f
private const val OVAL_INNER_RX = 68f
private const val OVAL_INNER_RY = 42f
private const val OVAL_STROKE = 3f

// Postmark in a 160x100 box.
private const val POSTMARK_CX = 44f
private const val POSTMARK_CY = 50f
private const val POSTMARK_R = 36f
private const val POSTMARK_STROKE = 2.4f
private const val POSTMARK_WAVE_STROKE = 1.6f
private const val WAVE_X = 86f
private const val WAVE_HALF_WIDTH = 9f
private const val WAVE_RISE = 6f
private const val WAVE_SEGMENTS = 8
private val WaveYs = floatArrayOf(34f, 50f, 66f)

/** Pairs of x,y in viewBox units. */
private fun FloatArray.toPolygon(scale: Float): Path = Path().apply {
    moveTo(this@toPolygon[0] * scale, this@toPolygon[1] * scale)
    for (i in 2 until this@toPolygon.size step 2) {
        lineTo(this@toPolygon[i] * scale, this@toPolygon[i + 1] * scale)
    }
    close()
}

private class StampPen(
    private val scope: DrawScope,
    private val measurer: TextMeasurer,
    private val ink: Color,
    private val mono: TextStyle,
    private val display: TextStyle,
    private val scale: Float,
) {
    private fun stroke(widthUnits: Float) = Stroke(width = widthUnits * scale)

    private fun style(base: TextStyle, sizeUnits: Float, trackingUnits: Float): TextStyle = with(scope) {
        base.copy(fontSize = (sizeUnits * scale).toSp(), letterSpacing = (trackingUnits * scale).toSp())
    }

    private fun measure(text: String, style: TextStyle): TextLayoutResult =
        measurer.measure(text, style, softWrap = false, maxLines = 1)

    /**
     * Text centred on [x] with its baseline at [y], at most [maxWidthUnits] wide: shrunk by
     * [fitTitleScale] first, then ellipsised if the floor is not enough.
     */
    fun label(
        text: String,
        x: Float,
        y: Float,
        sizeUnits: Float,
        isMono: Boolean,
        trackingUnits: Float = 0f,
        maxWidthUnits: Float = Float.POSITIVE_INFINITY,
    ) {
        if (text.isBlank()) return
        val base = if (isMono) mono else display
        var layout = measure(text, style(base, sizeUnits, trackingUnits))
        val maxPx = maxWidthUnits * scale
        val natural = layout.size.width.toFloat()
        if (natural > maxPx) {
            val fit = fitTitleScale(natural, maxPx, MIN_TITLE_SCALE)
            layout = measurer.measure(
                text,
                style(base, sizeUnits * fit, trackingUnits * fit),
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxPx.toInt() + 1),
            )
        }
        scope.drawText(layout, ink, Offset(x * scale - layout.size.width / 2f, y * scale - layout.firstBaseline))
    }

    fun circle(cx: Float, cy: Float, r: Float, strokeUnits: Float) = with(scope) {
        drawCircle(ink, r * scale, Offset(cx * scale, cy * scale), style = stroke(strokeUnits))
    }

    fun oval(cx: Float, cy: Float, rx: Float, ry: Float, strokeUnits: Float) = with(scope) {
        drawOval(
            ink,
            topLeft = Offset((cx - rx) * scale, (cy - ry) * scale),
            size = GeoSize(rx * 2f * scale, ry * 2f * scale),
            style = stroke(strokeUnits),
        )
    }

    fun polygon(points: FloatArray, strokeUnits: Float) = with(scope) {
        drawPath(points.toPolygon(scale), ink, style = stroke(strokeUnits))
    }

    /** Glyphs laid clockwise along a circle, the first at 9 o'clock, as an SVG `textPath` would. */
    fun ringText(text: String, cx: Float, cy: Float, radius: Float, sizeUnits: Float, trackingUnits: Float) {
        if (text.isEmpty()) return
        val glyphStyle = style(mono, sizeUnits, 0f)
        val layouts = text.map { measure(it.toString(), glyphStyle) }
        val placements = ringGlyphPlacements(
            glyphWidths = FloatArray(layouts.size) { layouts[it].size.width.toFloat() },
            tracking = trackingUnits * scale,
            radius = radius * scale,
            cx = cx * scale,
            cy = cy * scale,
        )
        placements.forEachIndexed { i, p ->
            val layout = layouts[i]
            scope.rotate(p.rotationDegrees, Offset(p.x, p.y)) {
                drawText(layout, ink, Offset(p.x - layout.size.width / 2f, p.y - layout.firstBaseline))
            }
        }
    }

    fun waves(strokeUnits: Float) = with(scope) {
        val path = Path()
        for (y in WaveYs) {
            path.moveTo(WAVE_X * scale, y * scale)
            // `q9,-6 18,0` then `t18,0` three times: each `t` mirrors the previous control point.
            for (i in 0 until WAVE_SEGMENTS / 2) {
                path.relativeQuadraticTo(
                    WAVE_HALF_WIDTH * scale, -WAVE_RISE * scale,
                    WAVE_HALF_WIDTH * 2f * scale, 0f,
                )
                path.relativeQuadraticTo(
                    WAVE_HALF_WIDTH * scale, WAVE_RISE * scale,
                    WAVE_HALF_WIDTH * 2f * scale, 0f,
                )
            }
        }
        drawPath(path, ink, style = stroke(strokeUnits))
    }

    fun firstClue(spec: StampSpec) {
        circle(RING_CENTER, RING_CENTER, RING_OUTER, FIRST_CLUE_STROKE)
        circle(RING_CENTER, RING_CENTER, RING_SECOND, FIRST_CLUE_STROKE)
        circle(RING_CENTER, RING_CENTER, RING_CORE, HAIRLINE)
        ringCentre(spec, large = false)
    }

    fun admitted(spec: StampSpec, large: Boolean) {
        circle(RING_CENTER, RING_CENTER, RING_OUTER, HEAVY_STROKE)
        if (large) circle(RING_CENTER, RING_CENTER, RING_LARGE_INNER, HAIRLINE)
        circle(RING_CENTER, RING_CENTER, RING_CORE, HAIRLINE)
        ringCentre(spec, large)
    }

    private fun ringCentre(spec: StampSpec, large: Boolean) {
        ringText(spec.topLine, RING_CENTER, RING_CENTER, RING_TEXT_RADIUS, RING_TEXT_SIZE, RING_TEXT_TRACKING)
        label(
            spec.title, RING_CENTER, RING_TITLE_Y, if (large) RING_TITLE_SIZE_LARGE else RING_TITLE_SIZE,
            isMono = false, maxWidthUnits = RING_LINE_MAX,
        )
        label(
            spec.bottomLine, RING_CENTER, if (large) RING_BOTTOM_Y_LARGE else RING_BOTTOM_Y,
            if (large) RING_BOTTOM_SIZE_LARGE else RING_BOTTOM_SIZE, isMono = true, maxWidthUnits = RING_LINE_MAX,
        )
    }

    fun transit(spec: StampSpec) {
        polygon(OctagonOuter, HEAVY_STROKE)
        polygon(OctagonInner, HAIRLINE)
        label(spec.topLine, RING_CENTER, 44f, 9f, isMono = true, trackingUnits = 1.5f, maxWidthUnits = OCTAGON_LINE_MAX)
        label(spec.title, RING_CENTER, 66f, 16f, isMono = false, maxWidthUnits = OCTAGON_TITLE_MAX)
        label(spec.bottomLine, RING_CENTER, 84f, 12f, isMono = true, maxWidthUnits = OCTAGON_LINE_MAX)
    }

    fun capital(spec: StampSpec) {
        oval(OVAL_CX, OVAL_CY, OVAL_OUTER_RX, OVAL_OUTER_RY, OVAL_STROKE)
        oval(OVAL_CX, OVAL_CY, OVAL_INNER_RX, OVAL_INNER_RY, HAIRLINE)
        label(spec.topLine, OVAL_CX, 40f, 9f, isMono = true, trackingUnits = 2f, maxWidthUnits = OVAL_LINE_MAX)
        label(spec.title, OVAL_CX, 62f, 17f, isMono = false, maxWidthUnits = OVAL_TITLE_MAX)
        label(spec.bottomLine, OVAL_CX, 80f, 12f, isMono = true, maxWidthUnits = OVAL_LINE_MAX)
    }

    fun postmark(spec: StampSpec) {
        circle(POSTMARK_CX, POSTMARK_CY, POSTMARK_R, POSTMARK_STROKE)
        waves(POSTMARK_WAVE_STROKE)
        label(spec.topLine, POSTMARK_CX, 46f, 9f, isMono = true, trackingUnits = 1f, maxWidthUnits = POSTMARK_LINE_MAX)
        label(spec.title, POSTMARK_CX, 60f, 13f, isMono = false, maxWidthUnits = POSTMARK_LINE_MAX)
    }
}

/** Where one ring-text glyph sits: baseline-centre point and rotation about it. */
internal class GlyphPlacement(val x: Float, val y: Float, val rotationDegrees: Float)

/**
 * Lays glyphs of the given widths along a circle of [radius] about ([cx], [cy]), the first starting
 * at 9 o'clock and running clockwise over the top (an SVG `textPath` on `M60,60 m-41,0 a41,41 0 1,1 82,0`).
 * Each glyph advances by its width plus [tracking] and is centred on its own arc position, upright
 * at the top (rotation 0), turned -90 degrees at 9 o'clock and +90 at 3. Glyphs that would start
 * past one full turn are dropped rather than overprinted.
 */
internal fun ringGlyphPlacements(
    glyphWidths: FloatArray,
    tracking: Float,
    radius: Float,
    cx: Float,
    cy: Float,
): List<GlyphPlacement> {
    val circumference = (2.0 * PI * radius).toFloat()
    val placements = ArrayList<GlyphPlacement>(glyphWidths.size)
    var start = 0f
    for (width in glyphWidths) {
        if (start >= circumference) break
        val angle = (start + width / 2f) / radius
        val around = PI + angle
        placements += GlyphPlacement(
            x = cx + radius * cos(around).toFloat(),
            y = cy + radius * sin(around).toFloat(),
            rotationDegrees = (angle * 180.0 / PI).toFloat() - 90f,
        )
        start += width + tracking
    }
    return placements
}

// ---------------------------------------------------------------------------------------------
// Rectangle and pill stamps: sized by their text. Canvas look is at Size.StampMedium.
// ---------------------------------------------------------------------------------------------

private class RectLook(
    val titleTracking: Float,
    val padX: Dp,
    val padY: Dp,
    val border: Dp,
    val double: Boolean,
    val pill: Boolean,
)

private fun rectLookOf(style: StampStyle): RectLook? = when (style) {
    StampStyle.Entry, StampStyle.Cleared ->
        RectLook(titleTracking = 0.04f, padX = Space.S14, padY = Space.S8, border = Border.B3, double = false, pill = false)
    StampStyle.Missed ->
        RectLook(titleTracking = 0.06f, padX = Space.S14, padY = Space.S8, border = Border.B5, double = true, pill = false)
    StampStyle.FirstVisit ->
        RectLook(titleTracking = 0f, padX = Space.S22, padY = Space.S12, border = Border.B3, double = false, pill = true)
    else -> null
}

private class MeasuredRect(
    val top: TextLayoutResult?,
    val title: TextLayoutResult,
    val bottom: TextLayoutResult?,
    val width: Float,
    val height: Float,
    val gap: Float,
    val padX: Float,
    val padY: Float,
    val border: Float,
    val radius: Float,
)

private fun measureRect(
    spec: StampSpec,
    look: RectLook,
    scale: Float,
    maxWidth: Dp,
    density: Density,
    measurer: TextMeasurer,
    mono: TextStyle,
    title: TextStyle,
): MeasuredRect = with(density) {
    val monoStyle = mono.copy(fontSize = mono.fontSize * scale, letterSpacing = mono.letterSpacing * scale)
    val titleStyle = title.copy(
        fontSize = title.fontSize * scale,
        letterSpacing = title.fontSize * (look.titleTracking * scale),
    )
    val gap = (Space.S2 * scale).toPx()
    val padX = (look.padX * scale).toPx()
    val padY = (look.padY * scale).toPx()
    val border = (look.border * scale).toPx()
    val room = (maxWidth.toPx() - 2f * (padX + border)).coerceAtLeast(0f)
    fun measure(text: String, style: TextStyle, limit: Float = room) = measurer.measure(
        text, style,
        overflow = TextOverflow.Ellipsis, softWrap = false, maxLines = 1,
        constraints = Constraints(maxWidth = limit.toInt() + 1),
    )
    val top = spec.topLine.takeIf { it.isNotBlank() }?.let { measure(it, monoStyle) }
    val bottom = spec.bottomLine.takeIf { it.isNotBlank() }?.let { measure(it, monoStyle) }
    // Shrink the title first (down to the floor); only then does the ellipsis cut it.
    val unbounded = measurer.measure(spec.title, titleStyle, softWrap = false, maxLines = 1)
    val fit = fitTitleScale(unbounded.size.width.toFloat(), room, MIN_TITLE_SCALE)
    val head = if (fit >= 1f) {
        unbounded
    } else {
        measure(
            spec.title,
            titleStyle.copy(
                fontSize = titleStyle.fontSize * fit,
                letterSpacing = titleStyle.letterSpacing * fit,
            ),
        )
    }
    val lines = listOfNotNull(top, head, bottom)
    val contentWidth = lines.maxOf { it.size.width }.toFloat()
    val contentHeight = lines.sumOf { it.size.height } + gap * (lines.size - 1)
    val width = contentWidth + 2f * (padX + border)
    val height = contentHeight + 2f * (padY + border)
    MeasuredRect(
        top = top, title = head, bottom = bottom,
        width = width, height = height, gap = gap, padX = padX, padY = padY, border = border,
        radius = if (look.pill) min(width, height) / 2f else (Radius.R4 * scale).toPx(),
    )
}

private fun DrawScope.drawRectStamp(body: MeasuredRect, look: RectLook, ink: Color) {
    val canvasWidth = size.width
    val canvasHeight = size.height
    if (look.double) {
        // CSS `double`: two lines of a third of the border width with a third-width gap between.
        val line = body.border / 3f
        for (offset in floatArrayOf(line / 2f, body.border - line / 2f)) {
            drawRoundRect(
                ink,
                topLeft = Offset(offset, offset),
                size = GeoSize(canvasWidth - 2f * offset, canvasHeight - 2f * offset),
                cornerRadius = CornerRadius(max(body.radius - offset, 0f)),
                style = Stroke(line),
            )
        }
    } else {
        val half = body.border / 2f
        drawRoundRect(
            ink,
            topLeft = Offset(half, half),
            size = GeoSize(canvasWidth - body.border, canvasHeight - body.border),
            cornerRadius = CornerRadius(max(body.radius - half, 0f)),
            style = Stroke(body.border),
        )
    }
    var y = body.border + body.padY
    for (line in listOfNotNull(body.top, body.title, body.bottom)) {
        drawText(line, ink, Offset((canvasWidth - line.size.width) / 2f, y))
        y += line.size.height + body.gap
    }
}
