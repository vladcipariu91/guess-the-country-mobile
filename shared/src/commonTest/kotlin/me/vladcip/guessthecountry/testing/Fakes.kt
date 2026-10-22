package me.vladcip.guessthecountry.testing

import me.vladcip.guessthecountry.domain.model.Country
import me.vladcip.guessthecountry.domain.model.CountryRepository
import me.vladcip.guessthecountry.domain.progress.PlayerProgress
import me.vladcip.guessthecountry.domain.progress.PlayerProgressRepository
import me.vladcip.guessthecountry.domain.progress.TodayProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlinx.datetime.DatePeriod

class FakeCountryRepository(
    var countries: List<Country> = testCountries(21),
    var error: Exception? = null,
) : CountryRepository {
    override suspend fun getAllCountries(): List<Country> {
        error?.let { throw it }
        return countries
    }
}

class FakePlayerProgressRepository(initial: PlayerProgress = PlayerProgress()) : PlayerProgressRepository {
    override val progress = MutableStateFlow(initial)

    override suspend fun recordGameStarted(today: LocalDate) = progress.update { it.onGameStarted(today) }

    override suspend fun recordAdWatched() = progress.update { it.onAdWatched() }
}

class FakeToday(var date: LocalDate = LocalDate(2026, 10, 19)) : TodayProvider {
    override fun today() = date

    fun advanceOneDay() {
        date = date.plus(DatePeriod(days = 1))
    }
}
