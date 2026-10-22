package me.vladcip.guessthecountry.domain.model

import me.vladcip.guessthecountry.data.seed.SeedCountries
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FlagTest {
    @Test
    fun `builds flag emoji from ISO codes`() {
        assertEquals("🇯🇵", flagEmoji("JP"))
        assertEquals("🇯🇵", flagEmoji("JP"))
        assertEquals("🇿🇦", flagEmoji("ZA"))
        assertEquals("🇩🇪", flagEmoji("DE"))
    }

    @Test
    fun `rejects anything that is not an upper-case two-letter code`() {
        listOf("jp", "JPN", "J", "1A").forEach { code ->
            assertFailsWith<IllegalArgumentException>(code) { flagEmoji(code) }
        }
    }

    @Test
    fun `every seeded country has a valid flag`() {
        SeedCountries.all.forEach { assertEquals(4, it.flag.length, it.name) }
    }
}
