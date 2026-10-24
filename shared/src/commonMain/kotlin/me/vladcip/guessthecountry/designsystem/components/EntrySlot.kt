package me.vladcip.guessthecountry.designsystem.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import me.vladcip.guessthecountry.designsystem.theme.GtcTheme
import me.vladcip.guessthecountry.designsystem.tokens.Border
import me.vladcip.guessthecountry.designsystem.tokens.Radius
import me.vladcip.guessthecountry.designsystem.tokens.Size
import me.vladcip.guessthecountry.designsystem.tokens.Space
import me.vladcip.guessthecountry.designsystem.tokens.Tilt

/**
 * One of the day's free entries (P-Menu). [usedOn] null shows a dashed "FREE" slot; otherwise a
 * tilted red "USED <date>" stamp. Give it `Modifier.weight(1f)` to share a row with its siblings.
 */
@Composable
fun EntrySlot(
    usedOn: String?,
    modifier: Modifier = Modifier,
) {
    val colors = GtcTheme.colors
    val typography = GtcTheme.typography
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Size.Slot)
            .then(
                if (usedOn == null) Modifier.dashedBorder(colors.goldLine, Border.B1_5, Radius.R8) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (usedOn == null) {
            Text(text = "FREE", style = typography.monoTag, color = colors.gold)
        } else {
            Text(
                text = "USED $usedOn".uppercase(),
                style = typography.monoTag,
                color = colors.stamp.usedText,
                modifier = Modifier
                    .graphicsLayer { rotationZ = Tilt.UsedStamp }
                    .border(Border.B2, colors.stamp.usedBorder, RoundedCornerShape(Radius.R6))
                    .padding(horizontal = Space.S8, vertical = Space.S4),
            )
        }
    }
}
