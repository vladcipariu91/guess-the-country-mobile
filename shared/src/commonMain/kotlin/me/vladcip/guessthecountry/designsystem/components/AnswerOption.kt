package me.vladcip.guessthecountry.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import me.vladcip.guessthecountry.designsystem.theme.GtcTheme
import me.vladcip.guessthecountry.designsystem.tokens.Border
import me.vladcip.guessthecountry.designsystem.tokens.Opacity
import me.vladcip.guessthecountry.designsystem.tokens.Radius
import me.vladcip.guessthecountry.designsystem.tokens.Size
import me.vladcip.guessthecountry.designsystem.tokens.Space

/**
 * How an answer row looks. [Default] is live and tappable, [Dimmed] is a lapsed alternative once the
 * round is decided, [Correct] is the right answer, [WrongGuess] is what the player picked wrongly and
 * [Eliminated] is a choice already ruled out.
 */
enum class AnswerOptionState { Default, Dimmed, Correct, WrongGuess, Eliminated }

/** One answer choice on a cover (P-Correct, P-Denied); pass `onClick = null` for a non-interactive row. */
@Composable
fun AnswerOption(
    name: String,
    onClick: (() -> Unit)?,
    state: AnswerOptionState,
    modifier: Modifier = Modifier,
    flag: String? = null,
    trailingLabel: String? = null,
) {
    val colors = GtcTheme.colors
    val typography = GtcTheme.typography
    val shape = RoundedCornerShape(Radius.R12)

    val fill: Color
    val content: Color
    val label: Color
    when (state) {
        AnswerOptionState.Default -> {
            fill = colors.optionOnCover; content = colors.onCover; label = colors.onCoverDisabled
        }
        AnswerOptionState.Dimmed, AnswerOptionState.Eliminated -> {
            fill = colors.optionOnCover; content = colors.onCoverDisabled; label = colors.onCoverDisabled
        }
        AnswerOptionState.Correct -> {
            fill = colors.paper; content = colors.ink; label = colors.stamp.tier4
        }
        AnswerOptionState.WrongGuess -> {
            fill = colors.optionOnCoverStrong; content = colors.onCover; label = colors.stamp.usedText
        }
    }
    val flagFaded = state == AnswerOptionState.Dimmed || state == AnswerOptionState.Eliminated
    val struck = state == AnswerOptionState.WrongGuess || state == AnswerOptionState.Eliminated

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Size.Option)
            .clip(shape)
            .background(fill)
            .then(if (state == AnswerOptionState.Correct) Modifier.border(Border.B2_5, colors.stamp.tier4, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = if (flag != null) Space.S12 else Space.S16, vertical = Space.S8),
        horizontalArrangement = Arrangement.spacedBy(Space.S10),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (flag != null) {
            Text(
                text = flag,
                style = typography.flag,
                modifier = if (flagFaded) Modifier.alpha(Opacity.DimmedFlag) else Modifier,
            )
        }
        Text(
            text = name,
            style = typography.optionLabel,
            color = content,
            textDecoration = if (struck) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f),
        )
        if (trailingLabel != null) {
            Text(text = trailingLabel.uppercase(), style = typography.monoTag, color = label)
        }
    }
}
