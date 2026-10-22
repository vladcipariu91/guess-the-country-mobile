package me.vladcip.guessthecountry.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.vladcip.guessthecountry.domain.progress.PlayerProgress
import me.vladcip.guessthecountry.domain.progress.PlayerProgressRepository
import me.vladcip.guessthecountry.domain.progress.TodayProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    /** False once today's free games and ad credits are used up: show "watch an ad" and "Upgrade". */
    val canPlay: Boolean = true,
    val freeGamesLeft: Int = PlayerProgress.FREE_GAMES_PER_DAY,
    val isPremium: Boolean = false,
)

class HomeViewModel(
    progressRepository: PlayerProgressRepository,
    private val todayProvider: TodayProvider,
) : ViewModel() {
    private val today = MutableStateFlow(todayProvider.today())

    val state: StateFlow<HomeUiState> = combine(progressRepository.progress, today) { progress, day ->
        HomeUiState(
            canPlay = progress.canStartGame(day),
            freeGamesLeft = progress.freeGamesLeft(day),
            isPremium = progress.isPremium,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Re-reads the date, e.g. when the menu is shown again after midnight. */
    fun refresh() {
        today.value = todayProvider.today()
    }

    /** Payments aren't implemented yet. */
    fun onUpgradeClicked() = Unit
}
