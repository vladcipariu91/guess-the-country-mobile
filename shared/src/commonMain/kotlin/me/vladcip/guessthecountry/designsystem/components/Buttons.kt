package me.vladcip.guessthecountry.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import me.vladcip.guessthecountry.designsystem.theme.GtcTheme
import me.vladcip.guessthecountry.designsystem.tokens.Border
import me.vladcip.guessthecountry.designsystem.tokens.Radius
import me.vladcip.guessthecountry.designsystem.tokens.Size
import me.vladcip.guessthecountry.designsystem.tokens.Space

/** The surface a button sits on, which decides its fill: gold on a cover, cover on paper. */
enum class ButtonSurface { OnCover, OnPaper }

/** Full-width filled call to action (P-Correct "Next country", P-Choose "Issue my passport"). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    surface: ButtonSurface = ButtonSurface.OnCover,
    enabled: Boolean = true,
) {
    val colors = GtcTheme.colors
    val (fill, content) = when (surface) {
        ButtonSurface.OnCover ->
            if (enabled) colors.gold to colors.ink else colors.optionOnCover to colors.onCoverDisabled
        ButtonSurface.OnPaper ->
            if (enabled) colors.cover to colors.goldLight else colors.rule to colors.inkMuted
    }
    val shape = RoundedCornerShape(Radius.R14)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Size.Button)
            .clip(shape)
            .background(fill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.S16),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = GtcTheme.typography.button, color = content)
    }
}

/** Bordered secondary button with an optional leading icon and trailing mono label (P-Menu "My passport"). */
@Composable
fun OutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingLabel: String? = null,
) {
    val colors = GtcTheme.colors
    val typography = GtcTheme.typography
    val shape = RoundedCornerShape(Radius.R14)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Size.Button)
            .clip(shape)
            .border(BorderStroke(Border.B1_5, colors.goldLine), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.S16),
        horizontalArrangement = Arrangement.spacedBy(Space.S12),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = colors.goldLight,
            )
        }
        Text(
            text = text,
            style = typography.optionLabel,
            color = colors.goldLight,
            modifier = Modifier.weight(1f),
        )
        if (trailingLabel != null) {
            Text(
                text = trailingLabel.uppercase(),
                style = typography.monoLabel,
                color = colors.goldLight,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** Underlined text link in gold, with a [Size.TouchMin] touch target (P-Menu "Upgrade for unlimited entries"). */
@Composable
fun LinkButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = Size.TouchMin)
            .widthIn(min = Size.TouchMin)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = GtcTheme.typography.bodySmall,
            color = GtcTheme.colors.gold,
            textDecoration = TextDecoration.Underline,
        )
    }
}
