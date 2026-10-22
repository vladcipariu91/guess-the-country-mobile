package me.vladcip.guessthecountry.di

import me.vladcip.guessthecountry.data.db.databaseBuilder
import org.koin.dsl.module

actual val platformModule = module {
    single { databaseBuilder() }
}

/** Called once from the Swift app entry point. */
fun setUpKoin() {
    initKoin()
}
