package me.vladcip.guessthecountry.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import me.vladcip.guessthecountry.designsystem.theme.GtcTheme
import me.vladcip.guessthecountry.designsystem.tokens.Size

/** In-game header on a cover: gold back link on the left, mono status [label] on the right (P-Correct). */
@Composable
fun GameTopBar(
    onBack: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    backText: String = "‹ Menu",
) {
    val colors = GtcTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .heightIn(min = Size.TouchMin)
                .widthIn(min = Size.TouchMin)
                .clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(text = backText, style = GtcTheme.typography.body, color = colors.gold)
        }
        Text(
            text = label.uppercase(),
            style = GtcTheme.typography.monoLabel,
            color = colors.onCover,
        )
    }
}
