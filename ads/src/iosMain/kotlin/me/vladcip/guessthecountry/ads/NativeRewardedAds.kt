package me.vladcip.guessthecountry.ads

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Implemented in Swift (iosApp/iosApp/GoogleRewardedAds.swift), because Google's iOS SDKs are
 * Swift packages. Callbacks keep the Swift side free of coroutines.
 */
interface NativeRewardedAds {
    fun initialize(rewardedAdUnitId: String, onComplete: () -> Unit)

    fun showRewardedAd(onRewarded: () -> Unit, onDismissed: () -> Unit, onUnavailable: () -> Unit)
}

class IosAdService(private val native: NativeRewardedAds) : AdService {
    override suspend fun initialize() = suspendCancellableCoroutine { continuation ->
        native.initialize(AdUnitIds.REWARDED) { if (continuation.isActive) continuation.resume(Unit) }
    }

    override suspend fun showRewardedAd(): RewardedAdResult = suspendCancellableCoroutine { continuation ->
        fun finish(result: RewardedAdResult) {
            if (continuation.isActive) continuation.resume(result)
        }
        native.showRewardedAd(
            onRewarded = { finish(RewardedAdResult.Rewarded) },
            onDismissed = { finish(RewardedAdResult.Dismissed) },
            onUnavailable = { finish(RewardedAdResult.Unavailable) },
        )
    }
}

internal object AdUnitIds {
    // TODO(admob-account): replace with the real iOS rewarded ad unit ID from AdMob.
    //  This is Google's public test unit; real IDs must never be used while testing.
    const val REWARDED = "ca-app-pub-3940256099942544/1712485313"
}
