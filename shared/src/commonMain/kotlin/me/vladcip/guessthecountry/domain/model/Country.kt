package me.vladcip.guessthecountry.domain.model

/**
 * A country the player has to guess.
 *
 * [hints] are ordered from hardest to easiest. The capital is the final, most obvious clue.
 */
data class Country(
    val id: String,
    val name: String,
    val capital: String,
    val hints: List<String>,
) {
    init {
        require(hints.isNotEmpty()) { "Country $id needs at least one hint" }
    }
}
