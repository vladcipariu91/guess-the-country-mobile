package me.vladcip.guessthecountry.presentation

import kotlinx.datetime.LocalDate
import me.vladcip.guessthecountry.domain.game.GameMode
import me.vladcip.guessthecountry.domain.model.Country
import me.vladcip.guessthecountry.domain.passport.StampOutcome

/**
 * One round of a finished game. [stamp] is non-null only for won "Guess the country" rounds whose
 * stamp was saved: it is `null` for a won round whose save failed. The results page therefore derives
 * the tier from [points] via `StampTier.forPoints`, not from [stamp].
 */
data class RoundSummary(
    val number: Int,
    val country: Country,
    val points: Int,
    val isWon: Boolean,
    val stamp: StampOutcome?,
)

/**
 * What the results screen shows for a finished game.
 *
 * @property firstVisits countries stamped for the first time in this game, in round order.
 * @property postmark the day's postmark if this game earned it, otherwise `null`.
 */
data class GameSummary(
    val mode: GameMode,
    val score: Int,
    val maxScore: Int,
    val playedOn: LocalDate,
    val rounds: List<RoundSummary>,
    val firstVisits: List<Country>,
    val postmark: LocalDate?,
)
