package me.vladcip.guessthecountry.domain.passport

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StampTierTest {
    @Test
    fun `maps every scoring outcome`() {
        assertEquals(StampTier.FirstClue, StampTier.forPoints(5))
        assertEquals(StampTier.Admitted, StampTier.forPoints(4))
        assertEquals(StampTier.Entry, StampTier.forPoints(3))
        assertEquals(StampTier.Transit, StampTier.forPoints(2))
        assertEquals(StampTier.Capital, StampTier.forPoints(1))
        assertNull(StampTier.forPoints(0))
    }

    @Test
    fun `rejects impossible points`() {
        assertFailsWith<IllegalArgumentException> { StampTier.forPoints(6) }
        assertFailsWith<IllegalArgumentException> { StampTier.forPoints(-1) }
    }

    @Test
    fun `better tier compares higher`() {
        assertTrue(StampTier.FirstClue.points > StampTier.Capital.points)
    }
}
