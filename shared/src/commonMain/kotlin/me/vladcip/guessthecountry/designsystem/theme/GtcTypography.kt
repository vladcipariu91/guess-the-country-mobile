package me.vladcip.guessthecountry.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import me.vladcip.guessthecountry.designsystem.tokens.FontRole
import me.vladcip.guessthecountry.designsystem.tokens.TypeToken
import me.vladcip.guessthecountry.designsystem.tokens.TypeTokens
import me.vladcip.guessthecountry.resources.Res
import me.vladcip.guessthecountry.resources.bricolage_grotesque_extrabold
import me.vladcip.guessthecountry.resources.bricolage_grotesque_regular
import me.vladcip.guessthecountry.resources.bricolage_grotesque_semibold
import me.vladcip.guessthecountry.resources.ibm_plex_mono_medium
import org.jetbrains.compose.resources.Font

/**
 * One [TextStyle] per entry of `TypeTokens`, named the same in lower camel case.
 *
 * A [TextStyle] cannot upper-case text, so tokens with `uppercase = true` (`splashTitle`, `brandTitle`
 * and every `mono*` style) must be rendered by components as `text.uppercase()`. Colour is left
 * unspecified; components set it from [GtcColors].
 */
@Immutable
data class GtcTypography(
    val scoreDisplay: TextStyle,
    val countDisplay: TextStyle,
    val splashTitle: TextStyle,
    val screenTitle: TextStyle,
    val modeTitle: TextStyle,
    val brandTitle: TextStyle,
    val cardTitle: TextStyle,
    val scoreSuffix: TextStyle,
    val button: TextStyle,
    val optionLabel: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val caption: TextStyle,
    val captionStrong: TextStyle,
    val monoBrand: TextStyle,
    val monoLabel: TextStyle,
    val monoTag: TextStyle,
    val monoMicro: TextStyle,
)

private fun TypeToken.toTextStyle(display: FontFamily, mono: FontFamily): TextStyle = TextStyle(
    fontFamily = if (font == FontRole.Mono) mono else display,
    fontSize = sizeSp.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = (sizeSp * letterSpacingEm).sp,
    lineHeight = lineHeight?.let { (sizeSp * it).sp } ?: TextUnit.Unspecified,
)

/**
 * Builds the typography from the bundled static fonts (static instances avoid variable-font gaps on
 * Android API 24-25). Bricolage ships 400, 600 and 800; the 700 weight used by buttons maps to the
 * 600 file.
 */
@Composable
fun rememberGtcTypography(): GtcTypography {
    val display = FontFamily(
        Font(Res.font.bricolage_grotesque_regular, FontWeight.W400),
        Font(Res.font.bricolage_grotesque_semibold, FontWeight.W600),
        Font(Res.font.bricolage_grotesque_semibold, FontWeight.W700),
        Font(Res.font.bricolage_grotesque_extrabold, FontWeight.W800),
    )
    val mono = FontFamily(Font(Res.font.ibm_plex_mono_medium, FontWeight.W500))
    return remember(display, mono) {
        GtcTypography(
            scoreDisplay = TypeTokens.ScoreDisplay.toTextStyle(display, mono),
            countDisplay = TypeTokens.CountDisplay.toTextStyle(display, mono),
            splashTitle = TypeTokens.SplashTitle.toTextStyle(display, mono),
            screenTitle = TypeTokens.ScreenTitle.toTextStyle(display, mono),
            modeTitle = TypeTokens.ModeTitle.toTextStyle(display, mono),
            brandTitle = TypeTokens.BrandTitle.toTextStyle(display, mono),
            cardTitle = TypeTokens.CardTitle.toTextStyle(display, mono),
            scoreSuffix = TypeTokens.ScoreSuffix.toTextStyle(display, mono),
            button = TypeTokens.Button.toTextStyle(display, mono),
            optionLabel = TypeTokens.OptionLabel.toTextStyle(display, mono),
            body = TypeTokens.Body.toTextStyle(display, mono),
            bodySmall = TypeTokens.BodySmall.toTextStyle(display, mono),
            caption = TypeTokens.Caption.toTextStyle(display, mono),
            captionStrong = TypeTokens.CaptionStrong.toTextStyle(display, mono),
            monoBrand = TypeTokens.MonoBrand.toTextStyle(display, mono),
            monoLabel = TypeTokens.MonoLabel.toTextStyle(display, mono),
            monoTag = TypeTokens.MonoTag.toTextStyle(display, mono),
            monoMicro = TypeTokens.MonoMicro.toTextStyle(display, mono),
        )
    }
}
