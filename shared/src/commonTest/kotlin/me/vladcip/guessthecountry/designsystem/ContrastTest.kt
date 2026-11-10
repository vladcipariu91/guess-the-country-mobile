package me.vladcip.guessthecountry.designsystem

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import me.vladcip.guessthecountry.designsystem.theme.contrastRatio
import me.vladcip.guessthecountry.designsystem.theme.gtcColors
import me.vladcip.guessthecountry.domain.passport.PassportCover
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ContrastTest {
    @Test
    fun `contrast of black on white is 21`() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
    }

    @Test
    fun `text pairs pass on every cover in light and dark`() {
        val failures = mutableListOf<String>()
        for (cover in PassportCover.entries) {
            for (dark in listOf(false, true)) {
                val c = gtcColors(cover, dark)
                val mode = if (dark) "dark" else "light"
                fun check(name: String, fg: Color, bg: Color, min: Double = 4.5) {
                    val ratio = contrastRatio(fg, bg)
                    if (ratio < min) failures += "$cover/$mode $name = ${(ratio * 100).roundToInt() / 100.0} (< $min)"
                }
                check("onCover/cover", c.onCover, c.cover)
                check("gold/cover", c.gold, c.cover)
                check("goldLight/cover", c.goldLight, c.cover)
                check("onCoverMuted/cover", c.onCoverMuted, c.cover)
                check("onCoverMuted/cover (display)", c.onCoverMuted, c.cover, min = 3.0)
                check("onCoverStrong/cover", c.onCoverStrong, c.cover)
                check("ink/gold", c.ink, c.gold)
                check("ink/paper", c.ink, c.paper)
                check("inkMuted/paper", c.inkMuted, c.paper)
                check("inkSoft/paper", c.inkSoft, c.paper)
                check("inkLabel/paper", c.inkLabel, c.paper)
                check("placeholderCaption/paper", c.placeholderCaption, c.paper)
                check("stamp.tier5/paper", c.stamp.tier5, c.paper)
                check("stamp.tier4/paper", c.stamp.tier4, c.paper)
                check("stamp.tier3/paper", c.stamp.tier3, c.paper)
                check("stamp.tier2/paper", c.stamp.tier2, c.paper)
                check("stamp.tier1/paper", c.stamp.tier1, c.paper)
                check("stamp.missed/paper", c.stamp.missed, c.paper)
                check("stamp.usedText/cover", c.stamp.usedText, c.cover)
            }
        }
        assertTrue(failures.isEmpty(), "Contrast failures:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `text on answer option fills passes on every cover`() {
        val failures = mutableListOf<String>()
        for (cover in PassportCover.entries) {
            for (dark in listOf(false, true)) {
                val c = gtcColors(cover, dark)
                val mode = if (dark) "dark" else "light"
                // Dimmed and Eliminated rows draw onCoverDisabled text; it is disabled and exempt, so not asserted here.
                fun check(name: String, fg: Color, fill: Color) {
                    val bg = fill.compositeOver(c.cover)
                    val ratio = contrastRatio(fg, bg)
                    if (ratio < 4.5) failures += "$cover/$mode $name = ${(ratio * 100).roundToInt() / 100.0} (< 4.5)"
                }
                check("Default onCover/optionOnCover", c.onCover, c.optionOnCover)
                check("WrongGuess onCover/optionOnCoverStrong", c.onCover, c.optionOnCoverStrong)
                check("WrongGuess stamp.usedText/optionOnCoverStrong", c.stamp.usedText, c.optionOnCoverStrong)
                check("Correct ink/paper", c.ink, c.paper)
                check("Correct stamp.tier4/paper", c.stamp.tier4, c.paper)
            }
        }
        assertTrue(failures.isEmpty(), "Answer-option contrast failures:\n" + failures.joinToString("\n"))
    }

    @Test
    fun `gtcColors maps cover`() {
        assertEquals(Color(0xFF16213D), gtcColors(PassportCover.Navy, false).cover)
    }
}
