package me.vladcip.guessthecountry.domain.passport

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PassportProgressTest {
    private val day = LocalDate(2026, 10, 19)
    private val ids = setOf("AB", "AC", "AD")

    private fun stamp(id: String, tier: StampTier = StampTier.Entry) = CountryStamp(id, tier, day)

    @Test
    fun `percent rounds down`() {
        assertEquals(6, PassportProgress(12, 195).percent)
        assertEquals(33, PassportProgress(1, 3).percent)
        assertEquals(66, PassportProgress(2, 3).percent)
    }

    @Test
    fun `percent is below 100 until every country is stamped`() {
        assertEquals(99, PassportProgress(194, 195).percent)
        assertEquals(100, PassportProgress(195, 195).percent)
    }

    @Test
    fun `no stamps is zero percent`() {
        val progress = passportProgressOf(emptyList(), ids)

        assertEquals(PassportProgress(0, 3), progress)
        assertEquals(0, progress?.percent)
    }

    @Test
    fun `empty catalogue gives no progress`() {
        assertNull(passportProgressOf(listOf(stamp("AB")), emptySet()))
    }

    @Test
    fun `counts each stamped country once`() {
        val stamps = listOf(stamp("AB", StampTier.Entry), stamp("AB", StampTier.FirstClue))

        assertEquals(PassportProgress(1, 3), passportProgressOf(stamps, ids))
    }

    @Test
    fun `ignores stamps for countries not in the catalogue`() {
        val stamps = listOf(stamp("AB"), stamp("ZZ"))

        assertEquals(PassportProgress(1, 3), passportProgressOf(stamps, ids))
    }

    @Test
    fun `rejects a zero total`() {
        assertFailsWith<IllegalArgumentException> { PassportProgress(0, 0) }
    }
}
