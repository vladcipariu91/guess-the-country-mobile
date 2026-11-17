package me.vladcip.guessthecountry.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.vladcip.guessthecountry.domain.model.CountryRepository
import me.vladcip.guessthecountry.domain.model.countryOfTheDay
import me.vladcip.guessthecountry.domain.model.flag
import me.vladcip.guessthecountry.domain.passport.PassportProgress
import me.vladcip.guessthecountry.domain.passport.PassportRepository
import me.vladcip.guessthecountry.domain.passport.passportProgressOf
import me.vladcip.guessthecountry.domain.progress.PlayerProgress
import me.vladcip.guessthecountry.domain.progress.PlayerProgressRepository
import me.vladcip.guessthecountry.domain.progress.TodayProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate

data class HomeUiState(
    /** False once today's free games and ad credits are used up: show "watch an ad" and "Upgrade". */
    val canPlay: Boolean = true,
    val freeGamesLeft: Int = PlayerProgress.FREE_GAMES_PER_DAY,
    val isPremium: Boolean = false,
    /** Countries stamped so far, for the "My passport" button. */
    val stampedCount: Int = 0,
    /** 0 when the countries fail to load: show progress only through [passportProgress]. */
    val totalCountries: Int = 0,
    /** Free entries already used [today], for the "USED dd.MM" slots. */
    val freeGamesUsedToday: Int = 0,
    val today: LocalDate? = null,
    /** The main menu's "X of Y countries" line; `null` (hidden) when the countries fail to load. */
    val passportProgress: PassportProgress? = null,
    /** The main menu's "Country of the day" line; `null` (hidden) when the countries fail to load. */
    val countryOfTheDay: CountryOfTheDay? = null,
)

/** What the menu shows for today's country: its flag emoji and name. */
data class CountryOfTheDay(val flag: String, val name: String)

class HomeViewModel(
    progressRepository: PlayerProgressRepository,
    passportRepository: PassportRepository,
    countryRepository: CountryRepository,
    private val todayProvider: TodayProvider,
) : ViewModel() {
    private val today = MutableStateFlow(todayProvider.today())

    // A failed country read only costs the passport and country-of-the-day lines; the menu must still work.
    private val countries = flow { emit(countryRepository.getAllCountries()) }
        .catch { emit(emptyList()) }

    val state: StateFlow<HomeUiState> = combine(
        progressRepository.progress,
        today,
        passportRepository.stamps,
        countries,
    ) { progress, day, stamps, countries ->
        val ids = countries.map { it.id }.toSet()
        HomeUiState(
            canPlay = progress.canStartGame(day),
            freeGamesLeft = progress.freeGamesLeft(day),
            isPremium = progress.isPremium,
            stampedCount = stamps.size,
            totalCountries = ids.size,
            freeGamesUsedToday = PlayerProgress.FREE_GAMES_PER_DAY - progress.freeGamesLeft(day),
            today = day,
            passportProgress = passportProgressOf(stamps, ids),
            countryOfTheDay = countryOfTheDay(countries, day)?.let { CountryOfTheDay(flag = it.flag, name = it.name) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Re-reads the date, e.g. when the menu is shown again after midnight. */
    fun refresh() {
        today.value = todayProvider.today()
    }

    /** Payments aren't implemented yet. */
    fun onUpgradeClicked() = Unit
}
