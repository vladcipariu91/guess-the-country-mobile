package me.vladcip.guessthecountry.domain.model

import me.vladcip.guessthecountry.data.seed.SeedCountries
import me.vladcip.guessthecountry.testing.testCountries
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CountryOfTheDayRuleTest {
    private val countries = testCountries(21)
    private val start = LocalDate(2026, 10, 19)

    private fun day(offset: Int) = start.plus(DatePeriod(days = offset))

    @Test
    fun `same date gives the same country`() {
        assertEquals(countryOfTheDay(countries, start), countryOfTheDay(countries, start))
    }

    @Test
    fun `consecutive days give different countries`() {
        (1 until 60).forEach { offset ->
            assertNotEquals(
                countryOfTheDay(countries, day(offset - 1)),
                countryOfTheDay(countries, day(offset)),
                "day $offset repeats the day before",
            )
        }
    }

    @Test
    fun `covers every country before repeating`() {
        val picks = (0 until countries.size).map { countryOfTheDay(countries, day(it)) }.toSet()
        assertEquals(countries.toSet(), picks)
    }

    @Test
    fun `list order does not change the pick`() {
        val reversed = countries.reversed()
        val shuffled = countries.shuffled(Random(42))
        (0 until 60).forEach { offset ->
            val expected = countryOfTheDay(countries, day(offset))
            assertEquals(expected, countryOfTheDay(reversed, day(offset)))
            assertEquals(expected, countryOfTheDay(shuffled, day(offset)))
        }
    }

    @Test
    fun `always picks a seeded country`() {
        (0 until 400).forEach { offset ->
            val pick = assertNotNull(countryOfTheDay(SeedCountries.all, day(offset)))
            assertTrue(pick in SeedCountries.all)
            assertTrue(pick.flag.isNotEmpty())
        }
    }

    @Test
    fun `dates before 1970 still work`() {
        val pick = countryOfTheDay(countries, LocalDate(1969, 12, 31))
        assertTrue(pick in countries)
    }

    @Test
    fun `a single country is picked every day`() {
        val only = testCountries(1)
        (0 until 5).forEach { assertEquals(only.single(), countryOfTheDay(only, day(it))) }
    }

    @Test
    fun `empty list gives null`() {
        assertNull(countryOfTheDay(emptyList(), start))
    }
}
