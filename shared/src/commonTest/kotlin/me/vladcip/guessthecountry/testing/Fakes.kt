package me.vladcip.guessthecountry.testing

import me.vladcip.guessthecountry.domain.model.Country
import me.vladcip.guessthecountry.domain.model.CountryRepository
import me.vladcip.guessthecountry.domain.passport.CountryStamp
import me.vladcip.guessthecountry.domain.passport.PassportCover
import me.vladcip.guessthecountry.domain.passport.PassportRepository
import me.vladcip.guessthecountry.domain.passport.StampOutcome
import me.vladcip.guessthecountry.domain.passport.StampTier
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

/** In-memory passport following the same rules as the Room implementation. */
class FakePassportRepository : PassportRepository {
    override val stamps = MutableStateFlow<List<CountryStamp>>(emptyList())
    override val postmarks = MutableStateFlow<List<LocalDate>>(emptyList())
    override val cover = MutableStateFlow<PassportCover?>(null)

    override suspend fun recordStamp(countryId: String, tier: StampTier, day: LocalDate): StampOutcome {
        val existing = stamps.value.firstOrNull { it.countryId == countryId }
        val isFirstVisit = existing == null
        val isNewBest = existing == null || tier.points > existing.bestTier.points
        if (isNewBest) {
            val updated = CountryStamp(countryId, tier, existing?.firstStampedOn ?: day)
            stamps.value = (stamps.value.filterNot { it.countryId == countryId } + updated).sortedBy { it.countryId }
        }
        return StampOutcome(tier, isNewBest = isNewBest, isFirstVisit = isFirstVisit)
    }

    override suspend fun recordPostmark(day: LocalDate): Boolean {
        if (day in postmarks.value) return false
        postmarks.value = (postmarks.value + day).sorted()
        return true
    }

    override suspend fun setCover(cover: PassportCover) {
        this.cover.value = cover
    }
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
