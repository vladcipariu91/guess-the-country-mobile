package me.vladcip.guessthecountry.domain.game

import me.vladcip.guessthecountry.domain.model.Country

enum class RoundPhase {
    /** Player is guessing from the hints. */
    Hints,

    /** Player was shown the capital: one last guess worth [HintRound.CAPITAL_POINTS]. */
    Capital,
    Won,
    Lost,
}

/**
 * "Guess the country" round.
 *
 * Scoring with 4 hints: hint 1 = 5 points, hint 2 = 4, hint 3 = 3, hint 4 = 2, capital = 1.
 * A wrong guess costs the same as asking for the next clue. A wrong guess on the capital ends the round with 0.
 */
data class HintRound(
    override val country: Country,
    override val options: List<Country>,
    val hintLevel: Int = 0,
    override val eliminatedOptions: Set<Country> = emptySet(),
    val phase: RoundPhase = RoundPhase.Hints,
    override val pointsEarned: Int = 0,
) : GameRound {
    init {
        require(country in options) { "Options must contain the correct answer" }
    }

    override val maxPoints: Int get() = CAPITAL_POINTS + country.hints.size

    val revealedHints: List<String> get() = country.hints.take(hintLevel + 1)

    override val isFinished: Boolean get() = phase == RoundPhase.Won || phase == RoundPhase.Lost

    override val isWon: Boolean get() = phase == RoundPhase.Won

    val pointsAvailable: Int
        get() = when (phase) {
            RoundPhase.Hints -> maxPoints - hintLevel
            RoundPhase.Capital -> CAPITAL_POINTS
            RoundPhase.Won, RoundPhase.Lost -> 0
        }

    /** Shows the next hint, or moves to the capital clue once the hints run out. */
    fun nextClue(): HintRound = when {
        phase != RoundPhase.Hints -> this
        hintLevel < country.hints.lastIndex -> copy(hintLevel = hintLevel + 1)
        else -> copy(phase = RoundPhase.Capital)
    }

    fun skipToCapital(): HintRound = if (phase == RoundPhase.Hints) copy(phase = RoundPhase.Capital) else this

    override fun guess(option: Country): HintRound {
        if (!isSelectable(option)) return this
        if (option == country) {
            return copy(phase = RoundPhase.Won, pointsEarned = pointsAvailable)
        }
        val eliminated = copy(eliminatedOptions = eliminatedOptions + option)
        return when (phase) {
            RoundPhase.Hints -> eliminated.nextClue()
            else -> eliminated.copy(phase = RoundPhase.Lost, pointsEarned = 0)
        }
    }

    companion object {
        const val CAPITAL_POINTS = 1
    }
}
