package me.vladcip.guessthecountry.domain.game

import me.vladcip.guessthecountry.testing.testCountries
import me.vladcip.guessthecountry.testing.testCountry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HintRoundTest {
    private val options = testCountries(6)
    private val country = options.first()
    private val wrong = options[1]
    private val round = HintRound(country = country, options = options)

    @Test
    fun `starts on the first hint worth 5 points`() {
        assertEquals(RoundPhase.Hints, round.phase)
        assertEquals(5, round.pointsAvailable)
        assertEquals(listOf("Hint 1 for country 1"), round.revealedHints)
    }

    @Test
    fun `correct guess on first hint earns 5 points`() {
        val result = round.guess(country)
        assertEquals(RoundPhase.Won, result.phase)
        assertEquals(5, result.pointsEarned)
        assertTrue(result.isWon)
    }

    @Test
    fun `each extra hint costs one point down to 2`() {
        val points = generateSequence(round) { it.nextClue() }
            .take(4)
            .map { it.pointsAvailable to it.revealedHints.size }
            .toList()
        assertEquals(listOf(5 to 1, 4 to 2, 3 to 3, 2 to 4), points)
    }

    @Test
    fun `asking for a clue after the last hint shows the capital worth 1 point`() {
        val capital = round.nextClue().nextClue().nextClue().nextClue()
        assertEquals(RoundPhase.Capital, capital.phase)
        assertEquals(1, capital.pointsAvailable)
    }

    @Test
    fun `wrong guess on the 5 point hint caps the round at 4 and reveals the next hint`() {
        val result = round.guess(wrong)
        assertEquals(RoundPhase.Hints, result.phase)
        assertEquals(4, result.pointsAvailable)
        assertEquals(2, result.revealedHints.size)
        assertEquals(setOf(wrong), result.eliminatedOptions)
        assertEquals(4, result.guess(country).pointsEarned)
    }

    @Test
    fun `wrong guess on the last hint moves to the capital`() {
        assertEquals(RoundPhase.Capital, round.nextClue().nextClue().nextClue().guess(wrong).phase)
    }

    @Test
    fun `player can jump straight to the capital at any time`() {
        val result = round.skipToCapital()
        assertEquals(RoundPhase.Capital, result.phase)
        assertEquals(1, result.pointsAvailable)
    }

    @Test
    fun `correct capital guess earns 1 point`() {
        assertEquals(1, round.skipToCapital().guess(country).pointsEarned)
    }

    @Test
    fun `wrong capital guess loses the round with 0 points`() {
        val result = round.skipToCapital().guess(wrong)
        assertEquals(RoundPhase.Lost, result.phase)
        assertEquals(0, result.pointsEarned)
        assertFalse(result.isWon)
    }

    @Test
    fun `eliminated or unknown options are ignored`() {
        val afterWrong = round.guess(wrong)
        assertFalse(afterWrong.isSelectable(wrong))
        assertSame(afterWrong, afterWrong.guess(wrong))
        assertSame(afterWrong, afterWrong.guess(testCountry(99)))
    }

    @Test
    fun `finished round ignores further actions`() {
        val won = round.guess(country)
        assertSame(won, won.guess(wrong))
        assertSame(won, won.nextClue())
        assertSame(won, won.skipToCapital())
    }
}
