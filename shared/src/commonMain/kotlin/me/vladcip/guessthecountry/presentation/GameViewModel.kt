package me.vladcip.guessthecountry.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.vladcip.guessthecountry.domain.game.Game
import me.vladcip.guessthecountry.domain.game.GameFactory
import me.vladcip.guessthecountry.domain.game.GameMode
import me.vladcip.guessthecountry.domain.game.GameRound
import me.vladcip.guessthecountry.domain.game.HintRound
import me.vladcip.guessthecountry.domain.model.Country
import me.vladcip.guessthecountry.domain.model.CountryRepository
import me.vladcip.guessthecountry.domain.progress.PlayerProgressRepository
import me.vladcip.guessthecountry.domain.progress.TodayProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface GameUiState {
    /** No game in progress: the main menu is shown. */
    data object Idle : GameUiState
    data object Loading : GameUiState

    /** Today's free games are used up; the (placeholder) ad unlocks a game of [mode]. */
    data class ShowingAd(val mode: GameMode) : GameUiState
    data class Playing(val game: Game) : GameUiState
    data class Finished(val mode: GameMode, val score: Int, val maxScore: Int) : GameUiState
    data class Error(val mode: GameMode, val message: String) : GameUiState
}

class GameViewModel(
    private val countryRepository: CountryRepository,
    private val progressRepository: PlayerProgressRepository,
    private val gameFactory: GameFactory,
    private val todayProvider: TodayProvider,
) : ViewModel() {
    private val _state = MutableStateFlow<GameUiState>(GameUiState.Idle)
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private var startJob: Job? = null

    fun startNewGame(mode: GameMode) = launchStart { start(mode) }

    fun watchAd(mode: GameMode) {
        _state.value = GameUiState.ShowingAd(mode)
    }

    /** Placeholder until real ads exist: the ad screen calls this when the "ad" finishes. */
    fun onAdWatched() {
        val mode = (_state.value as? GameUiState.ShowingAd)?.mode ?: return
        launchStart {
            progressRepository.recordAdWatched()
            start(mode)
        }
    }

    fun guess(option: Country) = updateRound { it.guess(option) }

    fun nextClue() = updateRound { (it as? HintRound)?.nextClue() ?: it }

    fun skipToCapital() = updateRound { (it as? HintRound)?.skipToCapital() ?: it }

    fun continueGame() {
        val game = (_state.value as? GameUiState.Playing)?.game ?: return
        _state.value = when {
            game.isOver -> GameUiState.Finished(game.mode, game.score, game.maxScore)
            else -> GameUiState.Playing(game.nextRound())
        }
    }

    fun exitToMenu() {
        startJob?.cancel()
        _state.value = GameUiState.Idle
    }

    private fun launchStart(block: suspend () -> Unit) {
        if (startJob?.isActive == true) return
        startJob = viewModelScope.launch { block() }
    }

    private suspend fun start(mode: GameMode) {
        _state.value = GameUiState.Loading
        try {
            val today = todayProvider.today()
            if (!progressRepository.progress.first().canStartGame(today)) {
                _state.value = GameUiState.ShowingAd(mode)
                return
            }
            val game = gameFactory.create(countryRepository.getAllCountries(), mode)
            progressRepository.recordGameStarted(today)
            _state.value = GameUiState.Playing(game)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = GameUiState.Error(mode, e.message ?: "Couldn't start the game")
        }
    }

    private fun updateRound(transform: (GameRound) -> GameRound) = _state.update { state ->
        if (state is GameUiState.Playing) state.copy(game = state.game.updateCurrentRound(transform)) else state
    }
}
