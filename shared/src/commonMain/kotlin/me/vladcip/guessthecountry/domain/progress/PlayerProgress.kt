package me.vladcip.guessthecountry.domain.progress

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/**
 * Free players get [FREE_GAMES_PER_DAY] games per calendar day. After that, each watched ad unlocks
 * one more game ([adCredits]). Premium players play without limits.
 * Ads and payments aren't implemented yet: a placeholder ad screen grants the credit.
 */
data class PlayerProgress(
    val gamesPlayed: Int = 0,
    /** The day [freeGamesUsed] refers to; a different day means no free games used yet. */
    val freeGamesDay: LocalDate? = null,
    val freeGamesUsed: Int = 0,
    val adCredits: Int = 0,
    val isPremium: Boolean = false,
) {
    fun freeGamesLeft(today: LocalDate): Int {
        val usedToday = if (freeGamesDay == today) freeGamesUsed else 0
        return (FREE_GAMES_PER_DAY - usedToday).coerceAtLeast(0)
    }

    fun canStartGame(today: LocalDate): Boolean =
        isPremium || freeGamesLeft(today) > 0 || adCredits > 0

    /** Uses a free game if one is left today, otherwise an ad credit. */
    fun onGameStarted(today: LocalDate): PlayerProgress {
        check(canStartGame(today)) { "No free games or ad credits left" }
        val played = copy(gamesPlayed = gamesPlayed + 1)
        return when {
            isPremium -> played
            freeGamesLeft(today) > 0 -> played.copy(
                freeGamesDay = today,
                freeGamesUsed = FREE_GAMES_PER_DAY - freeGamesLeft(today) + 1,
            )
            else -> played.copy(adCredits = adCredits - 1)
        }
    }

    fun onAdWatched(): PlayerProgress = copy(adCredits = adCredits + 1)

    companion object {
        const val FREE_GAMES_PER_DAY = 3
    }
}

interface PlayerProgressRepository {
    val progress: Flow<PlayerProgress>

    suspend fun recordGameStarted(today: LocalDate)

    suspend fun recordAdWatched()
}

/** Today's date in the player's time zone; injected so tests can control the day. */
fun interface TodayProvider {
    fun today(): LocalDate
}
