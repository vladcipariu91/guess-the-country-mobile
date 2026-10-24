package me.vladcip.guessthecountry.designsystem

import me.vladcip.guessthecountry.designsystem.components.ringGlyphPlacements
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RingTextTest {
    private val radius = 100f

    @Test
    fun `first glyph starts at nine o'clock heading up`() {
        val glyph = ringGlyphPlacements(floatArrayOf(0f, 10f), 0f, radius, 150f, 150f).first()
        assertEquals(50f, glyph.x, 0.01f)
        assertEquals(150f, glyph.y, 0.01f)
        assertEquals(-90f, glyph.rotationDegrees, 0.01f)
    }

    @Test
    fun `glyph a quarter turn along is upright at twelve o'clock`() {
        val quarter = (PI * radius / 2.0).toFloat()
        // A zero-width glyph after `quarter` of advance sits exactly at the top.
        val glyph = ringGlyphPlacements(floatArrayOf(quarter, 0f), 0f, radius, 150f, 150f)[1]
        assertEquals(150f, glyph.x, 0.01f)
        assertEquals(50f, glyph.y, 0.01f)
        assertEquals(0f, glyph.rotationDegrees, 0.01f)
    }

    @Test
    fun `glyphs run clockwise so rotation grows`() {
        val glyphs = ringGlyphPlacements(FloatArray(5) { 8f }, 2f, radius, 0f, 0f)
        assertTrue(glyphs.zipWithNext().all { (a, b) -> b.rotationDegrees > a.rotationDegrees })
    }

    @Test
    fun `glyphs past a full turn are dropped`() {
        val circumference = (2.0 * PI * radius).toFloat()
        val glyphs = ringGlyphPlacements(FloatArray(100) { 10f }, 0f, radius, 0f, 0f)
        assertTrue(glyphs.size < 100)
        assertTrue(glyphs.size * 10f >= circumference - 10f)
    }
}
