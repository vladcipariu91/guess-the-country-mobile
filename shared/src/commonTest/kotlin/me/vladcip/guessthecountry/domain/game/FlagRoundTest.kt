package me.vladcip.guessthecountry.domain.game

import me.vladcip.guessthecountry.testing.testCountries
import me.vladcip.guessthecountry.testing.testCountry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class FlagRoundTest {
    private val options = testCountries(6)
    private val country = options.first()
    private val round = FlagRound(country = country, options = options)

    @Test
    fun `correct guess scores 1 point and ends the round`() {
        val result = round.guess(country)
        assertTrue(result.isFinished)
        assertTrue(result.isWon)
        assertEquals(1, result.pointsEarned)
        assertEquals(1, result.maxPoints)
    }

    @Test
    fun `wrong guess scores 0 and ends the round - only one guess`() {
        val result = round.guess(options[1])
        assertTrue(result.isFinished)
        assertFalse(result.isWon)
        assertEquals(0, result.pointsEarned)
        assertEquals(setOf(options[1]), result.eliminatedOptions)
        assertSame(result, result.guess(country))
    }

    @Test
    fun `options that are not on screen are ignored`() {
        assertSame(round, round.guess(testCountry(99)))
    }
}
