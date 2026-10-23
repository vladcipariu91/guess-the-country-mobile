plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
}

/**
 * Rewarded ads behind a small [me.vladcip.guessthecountry.ads.AdService] interface, so Google's SDKs
 * stay out of the game code. Android uses the Google Mobile Ads SDK directly; on iOS the SDK is a
 * Swift package in iosApp, which implements [me.vladcip.guessthecountry.ads.NativeRewardedAds].
 */
kotlin {
    android {
        namespace = "me.vladcip.guessthecountry.ads"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
        }
        androidMain.dependencies {
            implementation(libs.play.services.ads)
            implementation(libs.ump)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
