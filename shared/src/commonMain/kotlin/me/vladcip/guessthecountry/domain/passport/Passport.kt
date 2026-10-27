package me.vladcip.guessthecountry.domain.passport

import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

/** A country's best-ever stamp. [firstStampedOn] is the day of its first stamp and never changes. */
data class CountryStamp(
    val countryId: String,
    val bestTier: StampTier,
    val firstStampedOn: LocalDate,
)

/**
 * What recording a stamp did to the collection.
 *
 * @property tier the tier just earned (not necessarily the best-ever one).
 * @property isNewBest true on a country's first stamp and on any strictly better tier.
 * @property isFirstVisit true only on a country's first stamp.
 */
data class StampOutcome(
    val tier: StampTier,
    val isNewBest: Boolean,
    val isFirstVisit: Boolean,
)

/** The player's passport: best stamp per country, daily postmarks and the chosen cover. */
interface PassportRepository {
    val stamps: Flow<List<CountryStamp>>

    /** Days a daily postmark was earned, ascending. */
    val postmarks: Flow<List<LocalDate>>

    /** `null` until the player has chosen a cover. */
    val cover: Flow<PassportCover?>

    /** Keeps the best-ever tier per country; a lower tier never downgrades it. */
    suspend fun recordStamp(countryId: String, tier: StampTier, day: LocalDate): StampOutcome

    /** Returns false when [day] already has a postmark. */
    suspend fun recordPostmark(day: LocalDate): Boolean

    suspend fun setCover(cover: PassportCover)
}
