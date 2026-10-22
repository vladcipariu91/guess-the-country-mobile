package me.vladcip.guessthecountry.domain.progress

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerProgressTest {
    private val monday = LocalDate(2026, 10, 19)
    private val tuesday = LocalDate(2026, 10, 20)

    private fun PlayerProgress.start(times: Int, day: LocalDate = monday) =
        (1..times).fold(this) { progress, _ -> progress.onGameStarted(day) }

    @Test
    fun `new player has 3 free games today`() {
        assertEquals(3, PlayerProgress().freeGamesLeft(monday))
        assertTrue(PlayerProgress().canStartGame(monday))
    }

    @Test
    fun `fourth game of the day needs an ad`() {
        val progress = PlayerProgress().start(3)
        assertEquals(0, progress.freeGamesLeft(monday))
        assertFalse(progress.canStartGame(monday))
        assertEquals(3, progress.gamesPlayed)
        assertFailsWith<IllegalStateException> { progress.onGameStarted(monday) }
    }

    @Test
    fun `free games reset on a new day`() {
        val progress = PlayerProgress().start(3)
        assertEquals(3, progress.freeGamesLeft(tuesday))
        assertEquals(2, progress.onGameStarted(tuesday).freeGamesLeft(tuesday))
    }

    @Test
    fun `one ad unlocks exactly one game`() {
        val afterAd = PlayerProgress().start(3).onAdWatched()
        assertTrue(afterAd.canStartGame(monday))

        val afterGame = afterAd.onGameStarted(monday)
        assertEquals(0, afterGame.adCredits)
        assertFalse(afterGame.canStartGame(monday))
    }

    @Test
    fun `free games are used before ad credits`() {
        val progress = PlayerProgress().onAdWatched().onGameStarted(monday)
        assertEquals(1, progress.adCredits)
        assertEquals(2, progress.freeGamesLeft(monday))
    }

    @Test
    fun `premium player is never limited`() {
        val premium = PlayerProgress(isPremium = true).start(10)
        assertEquals(10, premium.gamesPlayed)
        assertTrue(premium.canStartGame(monday))
    }
}
