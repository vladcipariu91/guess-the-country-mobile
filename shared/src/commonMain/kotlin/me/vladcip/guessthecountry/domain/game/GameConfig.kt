package me.vladcip.guessthecountry.domain.game

data class GameConfig(
    val roundsPerGame: Int = 7,
    val optionsPerQuestion: Int = 6,
) {
    init {
        require(roundsPerGame > 0)
        require(optionsPerQuestion >= 2)
    }
}
