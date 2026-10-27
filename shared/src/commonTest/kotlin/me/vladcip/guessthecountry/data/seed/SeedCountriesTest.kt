package me.vladcip.guessthecountry.data.seed

import me.vladcip.guessthecountry.domain.model.Region
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

    @Test
    fun `every region has at least one country`() {
        assertEquals(Region.entries.toSet(), countries.map { it.region }.toSet())
    }

    @Test
    fun `regions match geography`() {
        val expected = mapOf(
            "JP" to Region.Asia, "BR" to Region.Americas, "CA" to Region.Americas,
            "AU" to Region.Oceania, "EG" to Region.Africa, "FR" to Region.Europe,
            "IT" to Region.Europe, "IN" to Region.Asia, "MX" to Region.Americas,
            "KE" to Region.Africa, "AR" to Region.Americas, "NO" to Region.Europe,
            "PE" to Region.Americas, "ZA" to Region.Africa, "TH" to Region.Asia,
            "MA" to Region.Africa, "GR" to Region.Europe, "NZ" to Region.Oceania,
            "VN" to Region.Asia, "IS" to Region.Europe, "DE" to Region.Europe,
        )
        assertEquals(expected, countries.associate { it.id to it.region })
    }
}
