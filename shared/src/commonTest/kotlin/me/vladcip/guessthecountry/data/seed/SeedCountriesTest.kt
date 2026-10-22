package me.vladcip.guessthecountry.data.seed

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SeedCountriesTest {
    private val countries = SeedCountries.all

    @Test
    fun `ships 21 countries - enough for three games without repeats`() {
        assertEquals(21, countries.size)
    }

    @Test
    fun `ids names and capitals are unique`() {
        assertEquals(countries.size, countries.map { it.id }.toSet().size)
        assertEquals(countries.size, countries.map { it.name }.toSet().size)
        assertEquals(countries.size, countries.map { it.capital }.toSet().size)
    }

    @Test
    fun `every country has 4 non-blank hints so the first one is worth 5 points`() {
        countries.forEach { country ->
            assertEquals(4, country.hints.size, country.name)
            assertTrue(country.hints.none { it.isBlank() }, country.name)
        }
    }

    @Test
    fun `hints never give away the answer or the capital clue`() {
        countries.forEach { country ->
            country.hints.forEach { hint ->
                assertFalse(hint.contains(country.name, ignoreCase = true), "${country.name}: $hint")
                assertFalse(hint.contains(country.capital, ignoreCase = true), "${country.name}: $hint")
            }
        }
    }
}
