package me.vladcip.guessthecountry.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import me.vladcip.guessthecountry.designsystem.theme.GtcTheme
import me.vladcip.guessthecountry.designsystem.tokens.Border
import me.vladcip.guessthecountry.designsystem.tokens.Radius
import me.vladcip.guessthecountry.designsystem.tokens.Size
import me.vladcip.guessthecountry.designsystem.tokens.Space

/** Pill filter (P-Collection): a [Size.Chip]-tall visual inside a [Size.TouchMin] touch area. */
@Composable
fun GtcFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GtcTheme.colors
    val shape = RoundedCornerShape(Radius.Full)
    val fill = if (selected) colors.ink else Color.Transparent
    val border = if (selected) colors.ink else colors.rule
    val content = if (selected) colors.paper else colors.inkMuted
    Box(
        modifier = modifier
            .heightIn(min = Size.TouchMin)
            .selectable(selected = selected, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .height(Size.Chip)
                .clip(shape)
                .background(fill)
                .border(Border.B1_5, border, shape)
                .padding(horizontal = Space.S12),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = GtcTheme.typography.captionStrong,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/**
 * Thin progress track (P-Collection header). [fraction] is clamped to 0..1 (NaN counts as 0). On a cover it is a gold
 * fill on the translucent `onCoverTrack`; on paper ([onCover] false) gold on `goldTrack`.
 */
@Composable
fun GtcProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    onCover: Boolean = true,
) {
    val colors = GtcTheme.colors
    val clamped = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(Size.Progress)
            .clip(RoundedCornerShape(Radius.Full))
            .background(if (onCover) colors.onCoverTrack else colors.goldTrack)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f) },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped)
                .background(colors.gold),
        )
    }
}
