package me.vladcip.guessthecountry.domain.passport

/**
 * The tier of passport stamp earned by a won "Guess the country" round, keyed by the points scored.
 * Higher tiers are worth more points, so a better stamp compares greater.
 *
 * [Transit] is the purple octagon stamp; its name is internal only.
 */
enum class StampTier(val points: Int) {
    FirstClue(5),
    Admitted(4),
    Entry(3),
    Transit(2),
    Capital(1);

    companion object {
        /** Maximum points a round can score. */
        private const val MAX_POINTS = 5

        /**
         * Returns the tier earned for [points], or `null` when the round was missed (0 points).
         *
         * @throws IllegalArgumentException if [points] is outside 0..5.
         */
        fun forPoints(points: Int): StampTier? {
            require(points in 0..MAX_POINTS) {
                "Stamp points must be between 0 and $MAX_POINTS, but was $points"
            }
            return entries.firstOrNull { it.points == points }
        }
    }
}
