package me.vladcip.guessthecountry.designsystem.catalogue

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.vladcip.guessthecountry.designsystem.components.AnswerOption
import me.vladcip.guessthecountry.designsystem.components.AnswerOptionState
import me.vladcip.guessthecountry.designsystem.components.ButtonSurface
import me.vladcip.guessthecountry.designsystem.components.ClueCard
import me.vladcip.guessthecountry.designsystem.components.CollectionTile
import me.vladcip.guessthecountry.designsystem.components.EntrySlot
import me.vladcip.guessthecountry.designsystem.components.GameTopBar
import me.vladcip.guessthecountry.designsystem.components.GlobeEmblem
import me.vladcip.guessthecountry.designsystem.components.GtcFilterChip
import me.vladcip.guessthecountry.designsystem.components.GtcIcons
import me.vladcip.guessthecountry.designsystem.components.GtcProgressBar
import me.vladcip.guessthecountry.designsystem.components.LinkButton
import me.vladcip.guessthecountry.designsystem.components.ModeCard
import me.vladcip.guessthecountry.designsystem.components.OutlineButton
import me.vladcip.guessthecountry.designsystem.components.PassportBook
import me.vladcip.guessthecountry.designsystem.components.PassportSwatch
import me.vladcip.guessthecountry.designsystem.components.PrimaryButton
import me.vladcip.guessthecountry.designsystem.components.SpinningGlobe
import me.vladcip.guessthecountry.designsystem.components.Stamp
import me.vladcip.guessthecountry.designsystem.components.StampSpec
import me.vladcip.guessthecountry.designsystem.components.StampStyle
import me.vladcip.guessthecountry.designsystem.components.ink
import me.vladcip.guessthecountry.designsystem.motion.StampHaptic
import me.vladcip.guessthecountry.designsystem.motion.StampSlamState
import me.vladcip.guessthecountry.designsystem.motion.stampSlam
import me.vladcip.guessthecountry.designsystem.theme.GtcTheme
import me.vladcip.guessthecountry.designsystem.theme.rememberGtcFontFamilies
import me.vladcip.guessthecountry.designsystem.theme.toTextStyle
import me.vladcip.guessthecountry.designsystem.theme.coverColor
import me.vladcip.guessthecountry.designsystem.tokens.Border
import me.vladcip.guessthecountry.designsystem.tokens.ColorToken
import me.vladcip.guessthecountry.designsystem.tokens.ColorTokens
import me.vladcip.guessthecountry.designsystem.tokens.Dash
import me.vladcip.guessthecountry.designsystem.tokens.Elevation
import me.vladcip.guessthecountry.designsystem.tokens.Motion
import me.vladcip.guessthecountry.designsystem.tokens.Opacity
import me.vladcip.guessthecountry.designsystem.tokens.Radius
import me.vladcip.guessthecountry.designsystem.tokens.Size
import me.vladcip.guessthecountry.designsystem.tokens.Space
import me.vladcip.guessthecountry.designsystem.tokens.Tilt
import me.vladcip.guessthecountry.designsystem.tokens.TypeTokens
import me.vladcip.guessthecountry.domain.passport.PassportCover

/**
 * Debug catalogue of every design token and component in every state. Top controls pick the
 * passport cover and force light or dark; the rest scrolls. Not reachable from release builds.
 */
@Composable
fun CatalogueScreen() {
    val systemDark = isSystemInDarkTheme()
    var coverIndex by rememberSaveable { mutableIntStateOf(PassportCover.Navy.ordinal) }
    // 0 = follow the system, 1 = forced light, 2 = forced dark.
    var darkMode by rememberSaveable { mutableIntStateOf(0) }
    val cover = PassportCover.entries[coverIndex]
    val dark = when (darkMode) {
        1 -> false
        2 -> true
        else -> systemDark
    }

    GtcTheme(cover = cover, darkTheme = dark) {
        val colors = GtcTheme.colors
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.cover)
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.S16, vertical = Space.S12),
            verticalArrangement = Arrangement.spacedBy(Space.S24),
        ) {
            Controls(
                cover = cover,
                dark = dark,
                onCover = { coverIndex = it.ordinal },
                onDark = { darkMode = if (it) 2 else 1 },
            )
            ColourSection()
            TypeSection()
            MetricsSection()
            ComponentsOnCover()
            ComponentsOnPaper()
            StampsSection()
            SlamSection()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Chrome
// ---------------------------------------------------------------------------------------------

@Composable
private fun Controls(
    cover: PassportCover,
    dark: Boolean,
    onCover: (PassportCover) -> Unit,
    onDark: (Boolean) -> Unit,
) {
    val colors = GtcTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.S8)) {
        Text("GTC CATALOGUE", style = GtcTheme.typography.brandTitle, color = colors.gold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.S12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PassportCover.entries.forEach { option ->
                val selected = option == cover
                Box(
                    modifier = Modifier
                        .size(Size.TouchMin)
                        .clickable { onCover(option) }
                        .padding(Space.S4)
                        .clip(CircleShape)
                        .background(coverColor(option, dark))
                        .border(if (selected) Border.B3 else Border.B1_5, if (selected) colors.gold else colors.swatchRing, CircleShape),
                )
            }
            Spacer(Space.S8)
            // The chips are made for paper, so give them a paper strip to stay legible on every cover.
            Row(
                modifier = Modifier
                    .background(colors.paper, RoundedCornerShape(Radius.Full))
                    .padding(horizontal = Space.S4),
                horizontalArrangement = Arrangement.spacedBy(Space.S4),
            ) {
                GtcFilterChip(text = "Light", selected = !dark, onClick = { onDark(false) })
                GtcFilterChip(text = "Dark", selected = dark, onClick = { onDark(true) })
            }
        }
        Text(
            text = "${cover.name} / ${if (dark) "dark" else "light"}",
            style = GtcTheme.typography.monoTag,
            color = colors.onCoverMuted,
        )
    }
}

@Composable
private fun Spacer(width: Dp) = Box(Modifier.width(width))

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    val colors = GtcTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(Space.S12)) {
        Text(title.uppercase(), style = GtcTheme.typography.monoBrand, color = colors.gold)
        Box(Modifier.fillMaxWidth().height(Border.B1).background(colors.goldLine))
        content()
    }
}

@Composable
private fun SubLabel(text: String, onPaper: Boolean = false) {
    val colors = GtcTheme.colors
    Text(
        text = text.uppercase(),
        style = GtcTheme.typography.monoMicro,
        color = if (onPaper) colors.inkLabel else colors.onCoverMuted,
    )
}

/** A paper-coloured area for components designed to sit on paper (the app's Collection screen). */
@Composable
private fun PaperPanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(GtcTheme.colors.paper, RoundedCornerShape(Radius.R16))
            .padding(Space.S16),
        verticalArrangement = Arrangement.spacedBy(Space.S12),
    ) { content() }
}

// ---------------------------------------------------------------------------------------------
// Tokens
// ---------------------------------------------------------------------------------------------

private val colorTokens: List<Pair<String, ColorToken>> = ColorTokens.All

private fun Color.hex(): String {
    val argb = toArgb()
    val alpha = (argb ushr 24) and 0xFF
    val rgb = argb and 0xFFFFFF
    val body = rgb.toString(16).uppercase().padStart(6, '0')
    return if (alpha == 0xFF) "#$body" else "#$body @${alpha * 100 / 255}%"
}

@Composable
private fun ColourSection() {
    val colors = GtcTheme.colors
    Section("Colours (${colorTokens.size})") {
        Text(
            "Chip is drawn over white | black so translucent tokens read. Dark hex shown only when it differs.",
            style = GtcTheme.typography.caption,
            color = colors.onCoverMuted,
        )
        colorTokens.forEach { (name, token) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(Space.S12),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shape = RoundedCornerShape(Radius.R6)
                Box(
                    Modifier
                        .size(Size.Swatch + Space.S12, Size.Swatch)
                        .clip(shape)
                        .border(Border.B1, colors.onCoverTrack, shape),
                ) {
                    Row(Modifier.matchParentSize()) {
                        Box(Modifier.weight(1f).fillMaxSize().background(Color.White))
                        Box(Modifier.weight(1f).fillMaxSize().background(Color.Black))
                    }
                    Box(Modifier.matchParentSize().background(token.light))
                }
                Column(Modifier.weight(1f)) {
                    Text(name, style = GtcTheme.typography.captionStrong, color = colors.onCover)
                    val darkNote = if (token.dark == token.light) "" else "   dark ${token.dark.hex()}"
                    Text(
                        token.light.hex() + darkNote,
                        style = GtcTheme.typography.monoTag,
                        color = colors.onCoverMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun TypeSection() {
    val colors = GtcTheme.colors
    val typography = GtcTheme.typography
    val fonts = rememberGtcFontFamilies()
    Section("Type scale (${TypeTokens.All.size})") {
        TypeTokens.All.forEach { (name, token) ->
            val sample = if (token.uppercase) name.uppercase() else name
            Column(verticalArrangement = Arrangement.spacedBy(Space.S2)) {
                Text(sample, style = token.toTextStyle(fonts.display, fonts.mono), color = colors.onCover)
                val lineHeight = token.lineHeight?.let { " / lh $it" } ?: ""
                Text(
                    "$name  ${token.font.name} ${token.sizeSp.toInt()}sp w${token.weight} ls ${token.letterSpacingEm}em$lineHeight${if (token.uppercase) " UPPER" else ""}",
                    style = typography.monoMicro,
                    color = colors.onCoverMuted,
                )
            }
        }
    }
}

@Composable
private fun MetricsSection() {
    val colors = GtcTheme.colors
    val typography = GtcTheme.typography
    Section("Spacing, radius, border, size") {
        SubLabel("Space")
        Space.All.forEach { (name, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.CenterVertically) {
                Text("$name ${value.value.fmt()}", style = typography.monoMicro, color = colors.onCoverMuted, modifier = Modifier.width(72.dp))
                Box(Modifier.height(Space.S12).width(value).background(colors.gold))
            }
        }
        SubLabel("Radius")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
            Radius.All.forEach { (name, value) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.S4)) {
                    Box(Modifier.size(Size.Chip).border(Border.B2, colors.gold, RoundedCornerShape(value)))
                    Text(name, style = typography.monoMicro, color = colors.onCoverMuted)
                }
            }
        }
        SubLabel("Border")
        Border.All.forEach { (name, value) ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S12), verticalAlignment = Alignment.CenterVertically) {
                Text("$name ${value.value.fmt()}", style = typography.monoMicro, color = colors.onCoverMuted, modifier = Modifier.width(72.dp))
                Box(Modifier.weight(1f).height(value).background(colors.gold))
            }
        }
        SubLabel("Size (dp)")
        Text(
            Size.All.joinToString("   ") { (name, value) -> "$name ${value.value.fmt()}" },
            style = typography.monoMicro,
            color = colors.onCoverMuted,
        )
        SubLabel("Other")
        Text(
            "Elevation.Passport ${Elevation.Passport.value.fmt()}   Dash ${Dash.LengthFactor.fmt()}x/${Dash.GapFactor.fmt()}x stroke   " +
                "Tilt.UsedStamp ${Tilt.UsedStamp}   Opacity DimmedFlag ${Opacity.DimmedFlag} Stamp ${Opacity.Stamp} Graticule ${Opacity.Graticule}   " +
                "Motion slam ${Motion.StampSlamMillis}ms from ${Motion.StampSlamStartScale}x, globe ${Motion.GlobeRevolutionMillis}ms, loading ${Motion.LoadingBarMillis}ms",
            style = typography.monoMicro,
            color = colors.onCoverMuted,
        )
    }
}

private fun Float.fmt(): String = if (this == toInt().toFloat()) toInt().toString() else toString()

// ---------------------------------------------------------------------------------------------
// Components
// ---------------------------------------------------------------------------------------------

@Composable
private fun ComponentsOnCover() {
    val colors = GtcTheme.colors
    Section("Components on cover") {
        SubLabel("GameTopBar")
        GameTopBar(onBack = {}, label = "Round 3 of 10")

        SubLabel("PrimaryButton on cover: enabled, disabled")
        PrimaryButton("Next country", onClick = {})
        PrimaryButton("Next country", onClick = {}, enabled = false)

        SubLabel("OutlineButton: plain, icon + trailing label")
        OutlineButton("Play today", onClick = {})
        OutlineButton("My passport", onClick = {}, leadingIcon = GtcIcons.Passport, trailingLabel = "12 stamps")

        SubLabel("LinkButton")
        LinkButton("Upgrade for unlimited entries", onClick = {})

        SubLabel("AnswerOption: every state")
        AnswerOptionState.entries.forEach { state ->
            AnswerOption(name = state.name, onClick = {}, state = state, flag = "🇦🇷", trailingLabel = if (state == AnswerOptionState.Correct) "+4 pts" else null)
        }
        AnswerOption("No flag, no handler", onClick = null, state = AnswerOptionState.Default)
        AnswerOption(
            "Bosnia and Herzegovina and a very long wrapping answer label", onClick = {},
            state = AnswerOptionState.WrongGuess, flag = "🇧🇦", trailingLabel = "Your pick",
        )

        SubLabel("ClueCard: plain, with footer")
        ClueCard(
            title = "Which country is this?",
            clues = listOf("Its capital sits on a wide river.", "Tango was born here.", "The Andes run along its western edge."),
        )
        ClueCard(
            title = "Which country is this?",
            clues = listOf("Two clues so far."),
            footer = { Text("Footer slot", style = GtcTheme.typography.captionStrong, color = colors.inkLabel) },
        )

        SubLabel("ModeCard: three inks and tilts")
        ModeCard("Daily", "Today's passport", "Five clues, one country, three free entries.", "New", colors.stamp.tier3, -4f, onClick = {})
        ModeCard("Flags", "Flag round", "Name the country from its flag.", "Quick", colors.stamp.tier4, 3f, onClick = {})
        ModeCard("Practice", "Free play", "A very long description that has to wrap onto a second and third line to show how the card grows.", "Soon", colors.stamp.missed, -6f, onClick = {})

        SubLabel("EntrySlot: free, used")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
            EntrySlot(usedOn = null, modifier = Modifier.weight(1f))
            EntrySlot(usedOn = "12 Oct", modifier = Modifier.weight(1f))
            EntrySlot(usedOn = null, modifier = Modifier.weight(1f))
        }

        SubLabel("GtcProgressBar on cover: 0, 40%, 100%")
        GtcProgressBar(0f)
        GtcProgressBar(0.4f)
        GtcProgressBar(1f)

        SubLabel("PassportBook, GlobeEmblem")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S16), verticalAlignment = Alignment.CenterVertically) {
            PassportBook(cover = colors.cover)
            GlobeEmblem()
        }

        SubLabel("SpinningGlobe (static with reduce motion)")
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SpinningGlobe() }

        SubLabel("GtcIcons")
        Row(horizontalArrangement = Arrangement.spacedBy(Space.S16)) {
            listOf("Globe" to GtcIcons.Globe, "Pin" to GtcIcons.Pin, "Check" to GtcIcons.Check, "Passport" to GtcIcons.Passport).forEach { (name, icon) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.S4)) {
                    androidx.compose.material3.Icon(icon, contentDescription = null, tint = colors.gold, modifier = Modifier.size(Size.Icon))
                    Text(name, style = GtcTheme.typography.monoMicro, color = colors.onCoverMuted)
                }
            }
        }
    }
}

@Composable
private fun ComponentsOnPaper() {
    val colors = GtcTheme.colors
    var chipSelected by remember { mutableStateOf(0) }
    var swatchSelected by remember { mutableStateOf(0) }
    Section("Components on paper") {
        PaperPanel {
            SubLabel("PrimaryButton on paper: enabled, disabled", onPaper = true)
            PrimaryButton("Issue my passport", onClick = {}, surface = ButtonSurface.OnPaper)
            PrimaryButton("Issue my passport", onClick = {}, surface = ButtonSurface.OnPaper, enabled = false)

            SubLabel("GtcFilterChip: selected, unselected", onPaper = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                listOf("All", "Stamped", "Not yet").forEachIndexed { index, label ->
                    GtcFilterChip(label, selected = chipSelected == index, onClick = { chipSelected = index })
                }
            }

            SubLabel("GtcProgressBar on paper: 0, 40%, 100%", onPaper = true)
            GtcProgressBar(0f, onCover = false)
            GtcProgressBar(0.4f, onCover = false)
            GtcProgressBar(1f, onCover = false)

            SubLabel("PassportSwatch: four covers, one selected", onPaper = true)
            PassportCover.entries.forEachIndexed { index, option ->
                PassportSwatch(
                    name = option.name,
                    color = coverColor(option, colors.isDark),
                    selected = swatchSelected == index,
                    onClick = { swatchSelected = index },
                )
            }

            SubLabel("CollectionTile: stamped inks, unstamped", onPaper = true)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                CollectionTile("🇦🇷", "Argentina", 5, colors.stamp.tier5, -4f)
                CollectionTile("🇫🇷", "France", 4, colors.stamp.tier4, 3f)
                CollectionTile("🇯🇵", "Japan", 3, colors.stamp.tier3, -2f)
                CollectionTile("🇧🇷", "Brazil", null, null, 0f)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Stamps
// ---------------------------------------------------------------------------------------------

private fun sampleSpec(style: StampStyle, title: String): StampSpec = when (style) {
    StampStyle.FirstClue -> StampSpec(style, title, "First clue", "5 pts")
    StampStyle.Admitted -> StampSpec(style, title, "Admitted", "4 pts")
    StampStyle.Entry -> StampSpec(style, title, "Entry", "3 pts")
    StampStyle.Transit -> StampSpec(style, title, "Transit", "2 pts")
    StampStyle.Capital -> StampSpec(style, title, "Capital", "1 pt")
    StampStyle.Missed -> StampSpec(style, title, "Not admitted", "0 pts")
    StampStyle.Cleared -> StampSpec(style, title, "Cleared", "Flag round")
    StampStyle.FirstVisit -> StampSpec(style, title, "New country", "First visit")
    StampStyle.Postmark -> StampSpec(style, title, "Daily", "")
}

private val wideStyles = setOf(
    StampStyle.Entry, StampStyle.Missed, StampStyle.Cleared, StampStyle.FirstVisit, StampStyle.Postmark,
)

private val longTitles = listOf("Argentina", "United Kingdom", "Bosnia and Herzegovina")

@Composable
private fun StampsSection() {
    Section("Stamps on paper") {
        PaperPanel {
            SubLabel("Every style, Argentina, medium", onPaper = true)
            StampStyle.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.S16), verticalAlignment = Alignment.CenterVertically) {
                    pair.forEach { style ->
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.S4)) {
                            Text(style.name, style = GtcTheme.typography.monoMicro, color = style.ink())
                            Stamp(sampleSpec(style, "Argentina"), tiltDegrees = -6f)
                        }
                    }
                }
            }

            SubLabel("Long titles: Argentina, United Kingdom, Bosnia and Herzegovina", onPaper = true)
            StampStyle.entries.forEach { style ->
                Text(style.name, style = GtcTheme.typography.monoMicro, color = style.ink())
                // Rectangle, pill and postmark stamps size to their text, so they stack; the round ones share a row.
                val wide = style in wideStyles
                if (wide) {
                    longTitles.forEach { title -> Stamp(sampleSpec(style, title), size = Size.StampSmall) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.S8), verticalAlignment = Alignment.CenterVertically) {
                        longTitles.forEach { title -> Stamp(sampleSpec(style, title), size = Size.StampSmall) }
                    }
                }
            }

            SubLabel("Large (176): Admitted, FirstClue", onPaper = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Space.S8)) {
                Stamp(sampleSpec(StampStyle.Admitted, "Bosnia and Herzegovina"), size = Size.StampLarge, modifier = Modifier.weight(1f, fill = false))
            }
            Stamp(sampleSpec(StampStyle.FirstClue, "United Kingdom"), size = Size.StampLarge)
        }
    }
}

@Composable
private fun SlamSection() {
    val colors = GtcTheme.colors
    var replay by remember { mutableIntStateOf(0) }
    val slamState = remember(replay) { StampSlamState() }
    Section("Stamp slam over paper card") {
        SubLabel("Slam: settle to compare with the static card below")
        key(replay) {
            ClueCard(
                title = "Slam card",
                clues = listOf("The stamp drops in once, then rests."),
                modifier = Modifier.padding(bottom = Space.S4),
                overlay = {
                    Stamp(
                        spec = sampleSpec(StampStyle.Admitted, "Argentina"),
                        size = Size.StampLarge,
                        tiltDegrees = -8f,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(Space.S12)
                            .stampSlam("catalogue-slam-$replay", slamState, StampHaptic.Admitted),
                    )
                },
                footer = { Box(Modifier.height(Size.StampLarge * 0.6f)) },
            )
        }
        PrimaryButton("Replay slam", onClick = { replay++ })

        SubLabel("Static: same card, no slam modifier")
        ClueCard(
            title = "Static card",
            clues = listOf("Same stamp, never animated."),
            overlay = {
                Stamp(
                    spec = sampleSpec(StampStyle.Admitted, "Argentina"),
                    size = Size.StampLarge,
                    tiltDegrees = -8f,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(Space.S12),
                )
            },
            footer = { Box(Modifier.height(Size.StampLarge * 0.6f)) },
        )
        Text("Bottom of catalogue", style = GtcTheme.typography.monoMicro, color = colors.onCoverMuted)
    }
}
