package me.vladcip.guessthecountry.domain.progress

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class EntrySlotsTest {
    private val day = LocalDate(2026, 11, 17)

    private fun usedOn(usedToday: Int, today: LocalDate = day) = entrySlotsOf(usedToday, today).map { it.usedOn }

    @Test
    fun `no games used gives 3 available slots`() {
        val slots = entrySlotsOf(0, day)
        assertEquals(listOf(1, 2, 3), slots.map { it.number })
        assertEquals(listOf(null, null, null), slots.map { it.usedOn })
    }

    @Test
    fun `one game used stamps only the first slot`() {
        assertEquals(listOf("17.11", null, null), usedOn(1))
    }

    @Test
    fun `two games used stamps the first two slots`() {
        assertEquals(listOf("17.11", "17.11", null), usedOn(2))
    }

    @Test
    fun `three games used stamps all slots`() {
        assertEquals(listOf("17.11", "17.11", "17.11"), usedOn(3))
    }

    @Test
    fun `day and month are zero padded`() {
        assertEquals("05.03", usedOn(1, LocalDate(2026, 3, 5)).first())
    }

    @Test
    fun `used count outside 0 to 3 is clamped`() {
        assertEquals(listOf(null, null, null), usedOn(-1))
        assertEquals(listOf("17.11", "17.11", "17.11"), usedOn(5))
    }

    @Test
    fun `descriptions name the slot and its state`() {
        assertEquals(
            listOf("Free game 1, used today", "Free game 2, available", "Free game 3, available"),
            entrySlotsOf(1, day).map { it.description },
        )
    }
}
