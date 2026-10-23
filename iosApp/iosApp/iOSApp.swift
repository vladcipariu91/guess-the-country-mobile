import Shared
import SwiftUI

@main
struct iOSApp: App {
    init() {
        PlatformModule_iosKt.setUpKoin(nativeAds: GoogleRewardedAds())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
