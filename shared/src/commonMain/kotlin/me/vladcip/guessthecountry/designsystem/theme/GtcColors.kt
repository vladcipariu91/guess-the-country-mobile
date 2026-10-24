package me.vladcip.guessthecountry.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import me.vladcip.guessthecountry.designsystem.tokens.ColorToken
import me.vladcip.guessthecountry.designsystem.tokens.ColorTokens
import me.vladcip.guessthecountry.domain.passport.PassportCover

@Immutable
data class StampInks(
    val tier5: Color,
    val tier4: Color,
    val tier3: Color,
    val tier2: Color,
    val tier1: Color,
    val missed: Color,
    val usedBorder: Color,
    val usedText: Color,
)

@Immutable
data class GtcColors(
    val cover: Color,
    val gold: Color,
    val goldLight: Color,
    val goldLine: Color,
    val goldLineSoft: Color,
    val goldTrack: Color,
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkSoft: Color,
    val inkLabel: Color,
    val rule: Color,
    val outlineDashed: Color,
    val placeholderLine: Color,
    val placeholderText: Color,
    val placeholderCaption: Color,
    val onCover: Color,
    val onCoverStrong: Color,
    val onCoverMuted: Color,
    val onCoverDisabled: Color,
    val onCoverTrack: Color,
    val optionOnCover: Color,
    val optionOnCoverStrong: Color,
    val stamp: StampInks,
    val isDark: Boolean,
)

private fun ColorToken.pick(dark: Boolean): Color = if (dark) this.dark else light

private fun coverToken(cover: PassportCover): ColorToken = when (cover) {
    PassportCover.Burgundy -> ColorTokens.CoverBurgundy
    PassportCover.Navy -> ColorTokens.CoverNavy
    PassportCover.Green -> ColorTokens.CoverGreen
    PassportCover.Black -> ColorTokens.CoverBlack
}

/** The cover colour on its own, e.g. for the cover picker swatches. */
fun coverColor(cover: PassportCover, dark: Boolean): Color = coverToken(cover).pick(dark)

/** Builds the full palette for [cover] in light or [dark] mode from the generated design tokens. */
fun gtcColors(cover: PassportCover, dark: Boolean): GtcColors = GtcColors(
    cover = coverColor(cover, dark),
    gold = ColorTokens.Gold.pick(dark),
    goldLight = ColorTokens.GoldLight.pick(dark),
    goldLine = ColorTokens.GoldLine.pick(dark),
    goldLineSoft = ColorTokens.GoldLineSoft.pick(dark),
    goldTrack = ColorTokens.GoldTrack.pick(dark),
    paper = ColorTokens.Paper.pick(dark),
    surface = ColorTokens.Surface.pick(dark),
    ink = ColorTokens.Ink.pick(dark),
    inkMuted = ColorTokens.InkMuted.pick(dark),
    inkSoft = ColorTokens.InkSoft.pick(dark),
    inkLabel = ColorTokens.InkLabel.pick(dark),
    rule = ColorTokens.Rule.pick(dark),
    outlineDashed = ColorTokens.OutlineDashed.pick(dark),
    placeholderLine = ColorTokens.PlaceholderLine.pick(dark),
    placeholderText = ColorTokens.PlaceholderText.pick(dark),
    placeholderCaption = ColorTokens.PlaceholderCaption.pick(dark),
    onCover = ColorTokens.OnCover.pick(dark),
    onCoverStrong = ColorTokens.OnCoverStrong.pick(dark),
    onCoverMuted = ColorTokens.OnCoverMuted.pick(dark),
    onCoverDisabled = ColorTokens.OnCoverDisabled.pick(dark),
    onCoverTrack = ColorTokens.OnCoverTrack.pick(dark),
    optionOnCover = ColorTokens.OptionOnCover.pick(dark),
    optionOnCoverStrong = ColorTokens.OptionOnCoverStrong.pick(dark),
    stamp = StampInks(
        tier5 = ColorTokens.StampTier5.pick(dark),
        tier4 = ColorTokens.StampTier4.pick(dark),
        tier3 = ColorTokens.StampTier3.pick(dark),
        tier2 = ColorTokens.StampTier2.pick(dark),
        tier1 = ColorTokens.StampTier1.pick(dark),
        missed = ColorTokens.StampMissed.pick(dark),
        usedBorder = ColorTokens.StampUsedBorder.pick(dark),
        usedText = ColorTokens.StampUsedText.pick(dark),
    ),
    isDark = dark,
)
