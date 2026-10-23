package me.vladcip.guessthecountry.ads

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IosAdServiceTest {
    private class FakeNative(private val outcome: RewardedAdResult) : NativeRewardedAds {
        var initializedWith: String? = null

        override fun initialize(rewardedAdUnitId: String, onComplete: () -> Unit) {
            initializedWith = rewardedAdUnitId
            onComplete()
        }

        override fun showRewardedAd(onRewarded: () -> Unit, onDismissed: () -> Unit, onUnavailable: () -> Unit) {
            when (outcome) {
                RewardedAdResult.Rewarded -> onRewarded()
                RewardedAdResult.Dismissed -> onDismissed()
                RewardedAdResult.Unavailable -> onUnavailable()
            }
            // A late second callback from the SDK must not crash or change the result.
            onDismissed()
        }
    }

    @Test
    fun initializePassesTheRewardedAdUnit() = runTest {
        val native = FakeNative(RewardedAdResult.Rewarded)
        IosAdService(native).initialize()
        assertEquals(AdUnitIds.REWARDED, native.initializedWith)
    }

    @Test
    fun mapsEachSwiftCallbackToAResult() = runTest {
        RewardedAdResult.entries.forEach { outcome ->
            assertEquals(outcome, IosAdService(FakeNative(outcome)).showRewardedAd())
        }
    }
}
