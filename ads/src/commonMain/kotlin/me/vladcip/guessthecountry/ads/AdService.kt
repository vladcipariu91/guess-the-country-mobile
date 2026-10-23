package me.vladcip.guessthecountry.ads

/** What happened when the player was offered a rewarded ad. */
enum class RewardedAdResult {
    /** Watched to the end: grant the reward. */
    Rewarded,

    /** Closed before the end: no reward. */
    Dismissed,

    /** No ad could be shown (offline, no fill, no consent, SDK error). */
    Unavailable,
}

interface AdService {
    /**
     * Runs the consent flow (GDPR form, and Apple's tracking prompt on iOS) if needed, starts the
     * ads SDK and preloads a rewarded ad. Safe to call more than once.
     */
    suspend fun initialize()

    /** Shows a rewarded ad full screen and suspends until it is closed. */
    suspend fun showRewardedAd(): RewardedAdResult
}
