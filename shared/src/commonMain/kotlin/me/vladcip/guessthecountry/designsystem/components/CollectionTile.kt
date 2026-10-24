package me.vladcip.guessthecountry.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import me.vladcip.guessthecountry.designsystem.theme.GtcTheme
import me.vladcip.guessthecountry.designsystem.tokens.Border
import me.vladcip.guessthecountry.designsystem.tokens.Radius
import me.vladcip.guessthecountry.designsystem.tokens.Size
import me.vladcip.guessthecountry.designsystem.tokens.Space

/**
 * One country in the passport collection (P-Collection). A stamped country ([points] not null)
 * shows its [flag] inside a tilted double ring in [ink] with the best score; an unstamped one
 * ([points] null) shows a dashed "?" and "Not yet". [flag] is an emoji string.
 */
@Composable
fun CollectionTile(
    flag: String,
    name: String,
    points: Int?,
    ink: Color?,
    tiltDegrees: Float,
    modifier: Modifier = Modifier,
) {
    val colors = GtcTheme.colors
    val typography = GtcTheme.typography
    val stamped = points != null
    val description = if (stamped) "$name, best $points points" else "$name, not yet stamped"
    Column(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.S6),
    ) {
        if (stamped) {
            val stampInk = ink ?: colors.ink
            Column(
                modifier = Modifier
                    .size(Size.Tile)
                    .graphicsLayer { rotationZ = tiltDegrees }
                    .drawBehind { drawDoubleRing(stampInk, Border.B4.toPx()) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = flag,
                    style = typography.flag.copy(fontSize = with(LocalDensity.current) { Size.TileFlag.toSp() }),
                )
                Text(
                    text = "+$points",
                    style = typography.monoTag,
                    color = stampInk,
                    modifier = Modifier.padding(top = Space.S2),
                )
            }
            Text(
                text = name,
                style = typography.captionStrong,
                color = colors.ink,
                textAlign = TextAlign.Center,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(Size.Tile)
                    .dashedBorder(colors.placeholderLine, Border.B2, Radius.Full),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "?", style = typography.brandTitle, color = colors.placeholderText)
            }
            Text(
                text = "Not yet",
                style = typography.caption,
                color = colors.placeholderCaption,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** CSS `border: Ndouble`: two rings of a third of the width each, a third apart. */
private fun DrawScope.drawDoubleRing(color: Color, width: Float) {
    val line = width / 3f
    for (inset in floatArrayOf(line / 2f, width - line / 2f)) {
        drawCircle(
            color = color,
            radius = size.minDimension / 2f - inset,
            center = Offset(size.width / 2f, size.height / 2f),
            style = Stroke(line),
        )
    }
}
