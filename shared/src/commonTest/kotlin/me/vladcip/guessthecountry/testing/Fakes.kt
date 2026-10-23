package me.vladcip.guessthecountry.testing

import me.vladcip.guessthecountry.domain.model.Country
import me.vladcip.guessthecountry.domain.model.CountryRepository
import me.vladcip.guessthecountry.domain.progress.PlayerProgress
import me.vladcip.guessthecountry.domain.progress.PlayerProgressRepository
import me.vladcip.guessthecountry.domain.progress.TodayProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import me.vladcip.guessthecountry.ads.AdService
import me.vladcip.guessthecountry.ads.RewardedAdResult
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

/** Each shown ad waits until the test calls [finishAd], so the "ad playing" state can be checked. */
class FakeAdService : AdService {
    var initializeCalls = 0
    var adsShown = 0
    private var pending: CompletableDeferred<RewardedAdResult>? = null

    override suspend fun initialize() {
        initializeCalls++
    }

    override suspend fun showRewardedAd(): RewardedAdResult {
        adsShown++
        return CompletableDeferred<RewardedAdResult>().also { pending = it }.await()
    }

    fun finishAd(result: RewardedAdResult) {
        checkNotNull(pending) { "No ad is showing" }.complete(result)
        pending = null
    }
}
