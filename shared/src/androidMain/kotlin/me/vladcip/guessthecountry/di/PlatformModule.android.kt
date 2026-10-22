package me.vladcip.guessthecountry.di

import me.vladcip.guessthecountry.data.db.databaseBuilder
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

actual val platformModule = module {
    single { databaseBuilder(androidContext()) }
}
