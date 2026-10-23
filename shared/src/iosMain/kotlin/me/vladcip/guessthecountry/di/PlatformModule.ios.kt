package me.vladcip.guessthecountry.di

import me.vladcip.guessthecountry.ads.AdService
import me.vladcip.guessthecountry.ads.IosAdService
import me.vladcip.guessthecountry.ads.NativeRewardedAds
import me.vladcip.guessthecountry.data.db.databaseBuilder
import org.koin.dsl.module

actual val platformModule = module {
    single { databaseBuilder() }
}

/** Called once from the Swift app entry point, which owns the Google ads SDK. */
fun setUpKoin(nativeAds: NativeRewardedAds) {
    initKoin {
        modules(module { single<AdService> { IosAdService(nativeAds) } })
    }
}
