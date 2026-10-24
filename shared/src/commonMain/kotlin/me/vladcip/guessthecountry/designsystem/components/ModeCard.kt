package me.vladcip.guessthecountry.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import me.vladcip.guessthecountry.designsystem.theme.GtcTheme
import me.vladcip.guessthecountry.designsystem.tokens.Border
import me.vladcip.guessthecountry.designsystem.tokens.Radius
import me.vladcip.guessthecountry.designsystem.tokens.Space

/**
 * Paper card for a game mode on the menu (P-Menu): a small mono [label] and a tilted outlined [tag]
 * (inked [tagInk], rotated [tagTiltDegrees]) above the [title] and [description].
 */
@Composable
fun ModeCard(
    label: String,
    title: String,
    description: String,
    tag: String,
    tagInk: Color,
    tagTiltDegrees: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GtcTheme.colors
    val typography = GtcTheme.typography
    val shape = RoundedCornerShape(Radius.R14)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.paper)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(Space.S18),
        verticalArrangement = Arrangement.spacedBy(Space.S10),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label.uppercase(), style = typography.monoTag, color = colors.inkLabel)
            Text(
                text = tag.uppercase(),
                style = typography.monoTag,
                color = tagInk,
                modifier = Modifier
                    .graphicsLayer { rotationZ = tagTiltDegrees }
                    .border(Border.B2, tagInk, RoundedCornerShape(Radius.R6))
                    .padding(horizontal = Space.S8, vertical = Space.S4),
            )
        }
        Text(text = title, style = typography.modeTitle, color = colors.ink)
        Text(text = description, style = typography.bodySmall, color = colors.inkMuted)
    }
}
