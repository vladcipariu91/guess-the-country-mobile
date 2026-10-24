package me.vladcip.guessthecountry.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size as GeoSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import me.vladcip.guessthecountry.designsystem.theme.GtcTheme
import me.vladcip.guessthecountry.designsystem.tokens.Border
import me.vladcip.guessthecountry.designsystem.tokens.Elevation
import me.vladcip.guessthecountry.designsystem.tokens.Radius
import me.vladcip.guessthecountry.designsystem.tokens.Size
import me.vladcip.guessthecountry.designsystem.tokens.Space

/** Passport book cover (P-Choose): spine shadow, "PASSPORT", gold globe and the game's name. */
@Composable
fun PassportBook(cover: Color, modifier: Modifier = Modifier) {
    val colors = GtcTheme.colors
    val typography = GtcTheme.typography
    val shape = RoundedCornerShape(
        topStart = Radius.R6,
        topEnd = Radius.R14,
        bottomEnd = Radius.R14,
        bottomStart = Radius.R6,
    )
    // Trailing letter-spacing would pull centred tracked text off-centre; the canvas pads it back.
    val tracking = with(LocalDensity.current) { typography.monoCover.letterSpacing.toDp() }
    Column(
        modifier = modifier
            .size(Size.PassportWidth, Size.PassportHeight)
            .shadow(Elevation.Passport, shape)
            .clip(shape)
            .background(cover)
            .drawBehind {
                drawRect(colors.coverSpine, size = GeoSize(Size.Spine.toPx(), size.height))
            }
            .padding(start = Space.S12, top = Space.S22, end = Space.S12, bottom = Space.S20),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "PASSPORT",
            style = typography.monoCover,
            color = colors.gold,
            modifier = Modifier.padding(start = tracking),
        )
        GlobeEmblem(size = Size.CoverGlobe)
        Text(
            text = "GUESS THE\nCOUNTRY",
            style = typography.coverTitle,
            color = colors.gold,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Cover colour choice (P-Choose): a round colour chip and its [name]. Only the colour name shows;
 * the picker deliberately says nothing about countries. The selected row gets an ink ring and a tick.
 */
@Composable
fun PassportSwatch(
    name: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GtcTheme.colors
    val shape = RoundedCornerShape(Radius.R12)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Size.SwatchRow)
            .clip(shape)
            .background(colors.surface)
            .border(Border.B2, if (selected) colors.ink else Color.Transparent, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.S12, vertical = Space.S8),
        horizontalArrangement = Arrangement.spacedBy(Space.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(Size.Swatch)
                .clip(CircleShape)
                .background(color)
                .border(Border.B2, colors.swatchRing, CircleShape),
        )
        Text(
            text = name,
            style = GtcTheme.typography.optionLabel,
            color = colors.ink,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = GtcIcons.Check,
                contentDescription = null,
                tint = colors.ink,
                modifier = Modifier.size(Size.Icon),
            )
        } else {
            Spacer(Modifier.width(Size.Icon))
        }
    }
}
