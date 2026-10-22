package me.vladcip.guessthecountry.domain.game

import me.vladcip.guessthecountry.domain.model.Country

/** "Guess the flag" round: one guess, 1 point if right, 0 if wrong. */
data class FlagRound(
    override val country: Country,
    override val options: List<Country>,
    val selected: Country? = null,
) : GameRound {
    init {
        require(country in options) { "Options must contain the correct answer" }
    }

    override val isFinished: Boolean get() = selected != null
    override val isWon: Boolean get() = selected == country
    override val pointsEarned: Int get() = if (isWon) POINTS else 0
    override val maxPoints: Int get() = POINTS
    override val eliminatedOptions: Set<Country> get() = setOfNotNull(selected?.takeIf { it != country })

    override fun guess(option: Country): FlagRound = if (isSelectable(option)) copy(selected = option) else this

    companion object {
        const val POINTS = 1
    }
}
