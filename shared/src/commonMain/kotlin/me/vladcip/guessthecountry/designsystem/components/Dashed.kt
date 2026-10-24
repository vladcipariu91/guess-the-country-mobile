package me.vladcip.guessthecountry.designsystem.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import me.vladcip.guessthecountry.designsystem.tokens.Dash

private fun DrawScope.dashEffect(): PathEffect =
    PathEffect.dashPathEffect(floatArrayOf(Dash.Length.toPx(), Dash.Gap.toPx()))

/** Draws a dashed rounded border of [width] inside the bounds of the modified element. */
internal fun Modifier.dashedBorder(color: Color, width: Dp, radius: Dp): Modifier = drawBehind {
    val stroke = width.toPx()
    val inset = stroke / 2f
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius.toPx() - inset),
        style = Stroke(width = stroke, pathEffect = dashEffect()),
    )
}

/** Draws a dashed horizontal line of [width] thickness across the vertical middle of the element. */
internal fun Modifier.dashedDivider(color: Color, width: Dp): Modifier = drawBehind {
    val y = size.height / 2f
    drawLine(
        color = color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = width.toPx(),
        pathEffect = dashEffect(),
    )
}
