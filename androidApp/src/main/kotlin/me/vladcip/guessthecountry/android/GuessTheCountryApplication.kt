package me.vladcip.guessthecountry.android

import android.app.Application
import me.vladcip.guessthecountry.di.initKoin
import org.koin.android.ext.koin.androidContext

class GuessTheCountryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin { androidContext(this@GuessTheCountryApplication) }
    }
}
