package me.vladcip.guessthecountry.domain.passport

/**
 * How much of the passport is filled: [stamped] of [total] countries in the game.
 * Build it with [passportProgressOf], which handles an empty catalogue.
 */
data class PassportProgress(val stamped: Int, val total: Int) {
    init {
        require(total > 0) { "Passport total must be positive, but was $total" }
    }

    /** Whole percent, rounded down, so it reaches 100 only when every country is stamped. */
    val percent: Int get() = stamped * 100 / total
}

/**
 * Counts each stamped country once, ignoring stamps for countries no longer in [countryIds].
 * Returns `null` when [countryIds] is empty (e.g. the countries failed to load).
 */
fun passportProgressOf(stamps: List<CountryStamp>, countryIds: Set<String>): PassportProgress? {
    if (countryIds.isEmpty()) return null
    val stamped = stamps.map { it.countryId }.toSet().count { it in countryIds }
    return PassportProgress(stamped, countryIds.size)
}
