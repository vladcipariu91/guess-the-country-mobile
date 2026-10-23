package me.vladcip.guessthecountry.ads

import android.app.Activity
import android.app.Application
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class GoogleAdService(private val application: Application) : AdService {
    private val activities = CurrentActivityTracker(application)
    private val consent = UserMessagingPlatform.getConsentInformation(application)
    private val initLock = Mutex()
    private var adsEnabled = false
    private var rewardedAd: RewardedAd? = null

    override suspend fun initialize() = withContext(Dispatchers.Main) {
        initLock.withLock {
            if (adsEnabled) return@withLock
            gatherConsent(activities.awaitActivity())
            // Google's rule: only request ads once the consent SDK allows it (also true outside the UK/EU).
            if (consent.canRequestAds()) {
                MobileAds.initialize(application)
                adsEnabled = true
                rewardedAd = loadRewardedAd()
            }
        }
    }

    override suspend fun showRewardedAd(): RewardedAdResult = withContext(Dispatchers.Main) {
        if (!adsEnabled) return@withContext RewardedAdResult.Unavailable
        val ad = rewardedAd ?: withTimeoutOrNull(LOAD_TIMEOUT_MS) { loadRewardedAd() }
        rewardedAd = null
        val activity = activities.activity
        val result = if (ad == null || activity == null) RewardedAdResult.Unavailable else show(ad, activity)
        rewardedAd = loadRewardedAd() // Have the next one ready.
        result
    }

    /** Shows the consent form if the player's region requires one and they haven't answered yet. */
    private suspend fun gatherConsent(activity: Activity) = suspendCancellableCoroutine { continuation ->
        consent.requestConsentInfoUpdate(
            activity,
            ConsentRequestParameters.Builder().build(),
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            },
            { if (continuation.isActive) continuation.resume(Unit) },
        )
    }

    private suspend fun loadRewardedAd(): RewardedAd? = suspendCancellableCoroutine { continuation ->
        RewardedAd.load(
            application,
            AdUnitIds.REWARDED,
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    if (continuation.isActive) continuation.resume(ad)
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (continuation.isActive) continuation.resume(null)
                }
            },
        )
    }

    private suspend fun show(ad: RewardedAd, activity: Activity): RewardedAdResult =
        suspendCancellableCoroutine { continuation ->
            var earned = false
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    if (continuation.isActive) {
                        continuation.resume(if (earned) RewardedAdResult.Rewarded else RewardedAdResult.Dismissed)
                    }
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    if (continuation.isActive) continuation.resume(RewardedAdResult.Unavailable)
                }
            }
            ad.show(activity) { earned = true }
        }

    private companion object {
        const val LOAD_TIMEOUT_MS = 8_000L
    }
}

internal object AdUnitIds {
    // TODO(admob-account): replace with the real Android rewarded ad unit ID from AdMob.
    //  This is Google's public test unit; real IDs must never be used while testing.
    const val REWARDED = "ca-app-pub-3940256099942544/5224354917"
}
