import Shared
import SwiftUI

@main
struct iOSApp: App {
    init() {
        PlatformModule_iosKt.setUpKoin()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
