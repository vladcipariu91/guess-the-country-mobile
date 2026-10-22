package me.vladcip.guessthecountry.domain.game

import me.vladcip.guessthecountry.testing.testCountries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GameTest {
    private val countries = testCountries(3)
    private val game = Game(GameMode.GuessTheCountry, countries.map { HintRound(it, countries) })

    private fun Game.win() = updateCurrentRound { it.guess(it.country) }

    @Test
    fun `cannot move to next round before the current one is finished`() {
        assertSame(game, game.nextRound())
    }

    @Test
    fun `score adds up points across rounds`() {
        val played = game
            .win().nextRound() // 5
            .updateCurrentRound { (it as HintRound).nextClue().guess(it.country) }.nextRound() // 4
            .updateCurrentRound { (it as HintRound).skipToCapital().guess(countries[0]) } // 0
        assertEquals(9, played.score)
        assertEquals(15, played.maxScore)
        assertTrue(played.isOver)
    }

    @Test
    fun `game is over only after the last round is finished`() {
        val lastRound = game.win().nextRound().win().nextRound()
        assertEquals(3, lastRound.roundNumber)
        assertFalse(lastRound.isOver)
        val finished = lastRound.win()
        assertTrue(finished.isOver)
        assertSame(finished, finished.nextRound())
    }

    @Test
    fun `flag game scores 1 point per round`() {
        val flags = Game(GameMode.GuessTheFlag, countries.map { FlagRound(it, countries) })
        val played = flags.win().nextRound().win().nextRound().updateCurrentRound { it.guess(countries[0]) }
        assertEquals(2, played.score)
        assertEquals(3, played.maxScore)
        assertTrue(played.isOver)
    }
}
