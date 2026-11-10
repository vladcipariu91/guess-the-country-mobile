package me.vladcip.guessthecountry.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import me.vladcip.guessthecountry.designsystem.theme.GtcTheme
import me.vladcip.guessthecountry.designsystem.tokens.Border
import me.vladcip.guessthecountry.designsystem.tokens.Radius
import me.vladcip.guessthecountry.designsystem.tokens.Space

/**
 * Paper card holding a question [title] and its numbered [clues] (P-Correct). [footer] is laid out
 * below the clues in the same column; [overlay] is drawn on top of the card, e.g. the admitted stamp.
 * Callers reserve space for an overlay with [modifier] (the canvas pads the card bottom by 150).
 */
@Composable
fun ClueCard(
    title: String,
    clues: List<String>,
    modifier: Modifier = Modifier,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val colors = GtcTheme.colors
    val typography = GtcTheme.typography
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.paper, RoundedCornerShape(Radius.R16)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.S20),
            verticalArrangement = Arrangement.spacedBy(Space.S12),
        ) {
            Text(
                text = title,
                style = typography.cardTitle,
                color = colors.ink,
                modifier = Modifier.semantics { heading() },
            )
            clues.forEachIndexed { index, clue ->
                if (index > 0) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(Border.B1_5)
                            .dashedDivider(colors.rule, Border.B1_5),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.S12)) {
                    Text(
                        text = (index + 1).toString().padStart(2, '0'),
                        style = typography.monoLabel,
                        color = colors.inkLabel,
                    )
                    Text(text = clue, style = typography.body, color = colors.ink)
                }
            }
            footer?.invoke(this)
        }
        overlay()
    }
}
