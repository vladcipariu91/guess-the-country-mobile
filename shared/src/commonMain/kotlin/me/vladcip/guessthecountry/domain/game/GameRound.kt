package me.vladcip.guessthecountry.domain.game

import me.vladcip.guessthecountry.domain.model.Country

enum class GameMode {
    /** Hints from hard to easy, 5 points down to 1 for the capital. */
    GuessTheCountry,

    /** One flag, one guess, 1 point. */
    GuessTheFlag,
}

/** One country to guess from multiple-choice [options]. Immutable: every action returns the next state. */
sealed interface GameRound {
    val country: Country
    val options: List<Country>
    val eliminatedOptions: Set<Country>
    val isFinished: Boolean
    val isWon: Boolean
    val pointsEarned: Int
    val maxPoints: Int

    fun isSelectable(option: Country): Boolean =
        !isFinished && option in options && option !in eliminatedOptions

    fun guess(option: Country): GameRound
}
