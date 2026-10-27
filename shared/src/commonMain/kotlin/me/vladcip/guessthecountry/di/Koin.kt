package me.vladcip.guessthecountry.di

import androidx.room.RoomDatabase
import me.vladcip.guessthecountry.data.db.AppDatabase
import me.vladcip.guessthecountry.data.db.buildAppDatabase
import me.vladcip.guessthecountry.data.repository.RoomCountryRepository
import me.vladcip.guessthecountry.data.repository.RoomPassportRepository
import me.vladcip.guessthecountry.data.repository.RoomPlayerProgressRepository
import me.vladcip.guessthecountry.data.seed.DatabaseSeeder
import me.vladcip.guessthecountry.domain.game.GameFactory
import me.vladcip.guessthecountry.domain.model.CountryRepository
import me.vladcip.guessthecountry.domain.passport.PassportRepository
import me.vladcip.guessthecountry.domain.progress.PlayerProgressRepository
import me.vladcip.guessthecountry.domain.progress.SystemTodayProvider
import me.vladcip.guessthecountry.domain.progress.TodayProvider
import me.vladcip.guessthecountry.presentation.GameViewModel
import me.vladcip.guessthecountry.presentation.HomeViewModel
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.module

/** Provides a `RoomDatabase.Builder<AppDatabase>` for the platform. */
expect val platformModule: Module

val sharedModule = module {
    single { get<RoomDatabase.Builder<AppDatabase>>().buildAppDatabase() }
    single { get<AppDatabase>().countryDao() }
    single { get<AppDatabase>().playerProgressDao() }
    single { get<AppDatabase>().passportDao() }
    single { DatabaseSeeder(get()) }
    single<CountryRepository> { RoomCountryRepository(get(), get()) }
    single<PlayerProgressRepository> { RoomPlayerProgressRepository(get()) }
    single<PassportRepository> { RoomPassportRepository(get()) }
    factory { GameFactory() }
    single<TodayProvider> { SystemTodayProvider }
    viewModelOf(::GameViewModel)
    viewModelOf(::HomeViewModel)
}

fun initKoin(config: KoinAppDeclaration = {}) = startKoin {
    config()
    modules(platformModule, sharedModule)
}
