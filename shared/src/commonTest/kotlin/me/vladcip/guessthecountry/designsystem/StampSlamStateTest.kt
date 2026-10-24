package me.vladcip.guessthecountry.designsystem

import me.vladcip.guessthecountry.designsystem.motion.StampSlamState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StampSlamStateTest {
    @Test
    fun `plays once per stamp id`() {
        val state = StampSlamState()
        assertTrue(state.shouldPlay("r3"))
        state.markPlayed("r3")
        assertFalse(state.shouldPlay("r3"))
        assertTrue(state.shouldPlay("r4"))
    }

    @Test
    fun `already shown ids never play`() {
        val state = StampSlamState(alreadyShown = setOf("r1"))
        assertFalse(state.shouldPlay("r1"))
        assertTrue(state.shouldPlay("r2"))
    }
}
