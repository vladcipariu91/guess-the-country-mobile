package me.vladcip.guessthecountry.di

import android.app.Application
import me.vladcip.guessthecountry.ads.AdService
import me.vladcip.guessthecountry.ads.GoogleAdService
import me.vladcip.guessthecountry.data.db.databaseBuilder
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single { databaseBuilder(androidContext()) }
    single<AdService> { GoogleAdService(androidContext() as Application) }
}
