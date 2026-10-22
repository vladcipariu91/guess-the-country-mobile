package me.vladcip.guessthecountry.domain.game

import me.vladcip.guessthecountry.testing.testCountries
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GameFactoryTest {
    private val pool = testCountries(21)
    private val factory = GameFactory(GameConfig(roundsPerGame = 7, optionsPerQuestion = 6), Random(42))

    @Test
    fun `creates 7 rounds with no repeated countries`() {
        GameMode.entries.forEach { mode ->
            val game = factory.create(pool, mode)
            assertEquals(mode, game.mode)
            assertEquals(7, game.totalRounds)
            assertEquals(7, game.rounds.map { it.country.id }.toSet().size)
        }
    }

    @Test
    fun `each round has 6 distinct options including the answer`() {
        GameMode.entries.forEach { mode ->
            factory.create(pool, mode).rounds.forEach { round ->
                assertEquals(6, round.options.size)
                assertEquals(6, round.options.toSet().size)
                assertTrue(round.country in round.options)
            }
        }
    }

    @Test
    fun `mode decides the kind of round`() {
        factory.create(pool, GameMode.GuessTheCountry).rounds.forEach { assertIs<HintRound>(it) }
        factory.create(pool, GameMode.GuessTheFlag).rounds.forEach { assertIs<FlagRound>(it) }
    }

    @Test
    fun `same seed produces the same game`() {
        val a = GameFactory(random = Random(7)).create(pool)
        val b = GameFactory(random = Random(7)).create(pool)
        assertEquals(a, b)
    }

    @Test
    fun `fails when there are not enough countries`() {
        assertFailsWith<IllegalArgumentException> { factory.create(testCountries(6)) }
    }
}
