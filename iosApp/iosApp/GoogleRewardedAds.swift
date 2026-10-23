import AppTrackingTransparency
import GoogleMobileAds
import Shared
import UIKit
import UserMessagingPlatform

/// Google Mobile Ads + consent behind the shared Kotlin `AdService` (see the `ads` module).
/// Kotlin calls in on the main thread and gets exactly one callback per call.
final class GoogleRewardedAds: NSObject, NativeRewardedAds, FullScreenContentDelegate {
    private static let loadTimeout: TimeInterval = 8

    private var adUnitId = ""
    private var initializing = false
    private var adsEnabled = false
    private var rewardedAd: RewardedAd?
    private var loading = false
    private var waitingForLoad: [(RewardedAd?) -> Void] = []

    private var earnedReward = false
    private var finishShow: ((ShowOutcome) -> Void)?

    private enum ShowOutcome { case rewarded, dismissed, unavailable }

    // MARK: NativeRewardedAds

    func initialize(rewardedAdUnitId: String, onComplete: @escaping () -> Void) {
        guard !initializing else { return onComplete() }
        initializing = true
        adUnitId = rewardedAdUnitId

        // 1. GDPR consent form (shown only where required). 2. Apple's tracking prompt. 3. Start the SDK.
        ConsentInformation.shared.requestConsentInfoUpdate(with: RequestParameters()) { [weak self] error in
            guard let self else { return onComplete() }
            guard error == nil else { return self.afterConsent(onComplete: onComplete) }
            ConsentForm.loadAndPresentIfRequired(from: self.topViewController) { _ in
                self.afterConsent(onComplete: onComplete)
            }
        }
    }

    func showRewardedAd(
        onRewarded: @escaping () -> Void,
        onDismissed: @escaping () -> Void,
        onUnavailable: @escaping () -> Void
    ) {
        guard adsEnabled else { return onUnavailable() }
        withLoadedAd { [weak self] ad in
            guard let self, let ad, let viewController = self.topViewController else { return onUnavailable() }
            self.rewardedAd = nil
            self.earnedReward = false
            self.finishShow = { outcome in
                switch outcome {
                case .rewarded: onRewarded()
                case .dismissed: onDismissed()
                case .unavailable: onUnavailable()
                }
            }
            ad.fullScreenContentDelegate = self
            ad.present(from: viewController) { [weak self] in self?.earnedReward = true }
        }
    }

    // MARK: FullScreenContentDelegate

    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        finish(earnedReward ? .rewarded : .dismissed)
    }

    func ad(_ ad: FullScreenPresentingAd, didFailToPresentFullScreenContentWithError error: Error) {
        finish(.unavailable)
    }

    // MARK: Private

    private func afterConsent(onComplete: @escaping () -> Void) {
        // Google's rule: only request ads once the consent SDK allows it (also true outside the UK/EU).
        guard ConsentInformation.shared.canRequestAds else { return onComplete() }
        requestTrackingPermission { [weak self] in
            MobileAds.shared.start { _ in
                DispatchQueue.main.async {
                    self?.adsEnabled = true
                    self?.loadAd()
                    onComplete()
                }
            }
        }
    }

    private func requestTrackingPermission(then next: @escaping () -> Void) {
        guard ATTrackingManager.trackingAuthorizationStatus == .notDetermined else { return next() }
        ATTrackingManager.requestTrackingAuthorization { _ in DispatchQueue.main.async(execute: next) }
    }

    private func finish(_ outcome: ShowOutcome) {
        let finish = finishShow
        finishShow = nil
        finish?(outcome)
        loadAd() // Have the next one ready.
    }

    private func withLoadedAd(_ completion: @escaping (RewardedAd?) -> Void) {
        if let rewardedAd { return completion(rewardedAd) }
        var done = false
        let once: (RewardedAd?) -> Void = { ad in
            guard !done else { return }
            done = true
            completion(ad)
        }
        waitingForLoad.append(once)
        DispatchQueue.main.asyncAfter(deadline: .now() + Self.loadTimeout) { once(nil) }
        loadAd()
    }

    private func loadAd() {
        guard adsEnabled, rewardedAd == nil, !loading else { return }
        loading = true
        RewardedAd.load(with: adUnitId, request: Request()) { [weak self] ad, _ in
            DispatchQueue.main.async {
                guard let self else { return }
                self.loading = false
                self.rewardedAd = ad
                let waiting = self.waitingForLoad
                self.waitingForLoad = []
                waiting.forEach { $0(ad) }
            }
        }
    }

    private var topViewController: UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let scene = scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
        var viewController = scene?.windows.first { $0.isKeyWindow }?.rootViewController
        while let presented = viewController?.presentedViewController { viewController = presented }
        return viewController
    }
}
