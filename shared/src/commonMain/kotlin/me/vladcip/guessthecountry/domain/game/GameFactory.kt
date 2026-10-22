package me.vladcip.guessthecountry.domain.game

import me.vladcip.guessthecountry.domain.model.Country
import kotlin.random.Random

class GameFactory(
    private val config: GameConfig = GameConfig(),
    private val random: Random = Random.Default,
) {
    /** Picks distinct countries from [pool] and builds a multiple-choice round for each. */
    fun create(pool: List<Country>, mode: GameMode = GameMode.GuessTheCountry): Game {
        require(pool.size >= config.roundsPerGame) {
            "Need at least ${config.roundsPerGame} countries, got ${pool.size}"
        }
        require(pool.size >= config.optionsPerQuestion) {
            "Need at least ${config.optionsPerQuestion} countries for the answer options, got ${pool.size}"
        }
        val rounds = pool.shuffled(random)
            .take(config.roundsPerGame)
            .map { country ->
                val options = optionsFor(country, pool)
                when (mode) {
                    GameMode.GuessTheCountry -> HintRound(country, options)
                    GameMode.GuessTheFlag -> FlagRound(country, options)
                }
            }
        return Game(mode, rounds)
    }

    private fun optionsFor(country: Country, pool: List<Country>): List<Country> {
        val distractors = pool.filter { it.id != country.id }
            .shuffled(random)
            .take(config.optionsPerQuestion - 1)
        return (distractors + country).shuffled(random)
    }
}
