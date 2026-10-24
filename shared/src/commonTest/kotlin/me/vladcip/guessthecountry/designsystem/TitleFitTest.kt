package me.vladcip.guessthecountry.designsystem

import me.vladcip.guessthecountry.designsystem.components.MIN_TITLE_SCALE
import me.vladcip.guessthecountry.designsystem.components.fitTitleScale
import me.vladcip.guessthecountry.designsystem.components.needsEllipsis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TitleFitTest {
    // Ring stamp: 14-unit Bricolage 800 caps run about 9.5 units per letter (BRAZIL is about 57),
    // and the core circle leaves 60 units.
    private val ringMax = 60f
    private fun width(name: String) = name.uppercase().length * 9.5f

    @Test
    fun `short names are not shrunk`() {
        listOf("Brazil", "Peru", "Japan").forEach {
            assertEquals(1f, fitTitleScale(width(it), ringMax, MIN_TITLE_SCALE), it)
        }
    }

    @Test
    fun `a longer name shrinks to fit without ellipsis`() {
        val scale = fitTitleScale(width("Argentina"), ringMax, MIN_TITLE_SCALE)
        assertTrue(scale < 1f && scale >= MIN_TITLE_SCALE)
        assertEquals(ringMax / width("Argentina"), scale, 0.0001f)
        assertFalse(needsEllipsis(width("Argentina"), ringMax, scale))
    }

    @Test
    fun `very long names are floored and then ellipsised`() {
        listOf("New Zealand", "South Africa", "United Kingdom", "Bosnia and Herzegovina").forEach {
            val scale = fitTitleScale(width(it), ringMax, MIN_TITLE_SCALE)
            assertEquals(MIN_TITLE_SCALE, scale, it)
            assertTrue(needsEllipsis(width(it), ringMax, scale), it)
        }
    }

    @Test
    fun `a name just above the floor still shrinks to fit without ellipsis`() {
        val w = ringMax / MIN_TITLE_SCALE * 0.999f
        val scale = fitTitleScale(w, ringMax, MIN_TITLE_SCALE)
        assertEquals(ringMax / w, scale, 0.0001f)
        assertTrue(scale > MIN_TITLE_SCALE)
        assertFalse(needsEllipsis(w, ringMax, scale))
    }

    @Test
    fun `empty text and zero width never divide`() {
        assertEquals(1f, fitTitleScale(0f, ringMax, MIN_TITLE_SCALE))
        assertEquals(MIN_TITLE_SCALE, fitTitleScale(10f, 0f, MIN_TITLE_SCALE))
    }
}
