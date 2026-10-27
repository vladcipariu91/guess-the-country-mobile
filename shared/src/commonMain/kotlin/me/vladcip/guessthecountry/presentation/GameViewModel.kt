package me.vladcip.guessthecountry.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.vladcip.guessthecountry.ads.AdService
import me.vladcip.guessthecountry.ads.RewardedAdResult
import me.vladcip.guessthecountry.domain.game.Game
import me.vladcip.guessthecountry.domain.game.GameFactory
import me.vladcip.guessthecountry.domain.game.GameMode
import me.vladcip.guessthecountry.domain.game.GameRound
import me.vladcip.guessthecountry.domain.game.HintRound
import me.vladcip.guessthecountry.domain.model.Country
import me.vladcip.guessthecountry.domain.model.CountryRepository
import me.vladcip.guessthecountry.domain.passport.PassportRepository
import me.vladcip.guessthecountry.domain.passport.StampOutcome
import me.vladcip.guessthecountry.domain.passport.StampTier
import me.vladcip.guessthecountry.domain.progress.PlayerProgressRepository
import me.vladcip.guessthecountry.domain.progress.TodayProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

sealed interface GameUiState {
    /** No game in progress: the main menu is shown. */
    data object Idle : GameUiState
    data object Loading : GameUiState

    /** Today's free games are used up; a rewarded ad is playing to unlock a game of [mode]. */
    data class ShowingAd(val mode: GameMode) : GameUiState

    /** [stamps] holds the stamp outcome of each won hint round, keyed by round index. */
    data class Playing(
        val game: Game,
        val stamps: Map<Int, StampOutcome> = emptyMap(),
        val playedOn: LocalDate,
    ) : GameUiState

    data class Finished(val summary: GameSummary) : GameUiState
    data class Error(val mode: GameMode, val message: String) : GameUiState
}

class GameViewModel(
    private val countryRepository: CountryRepository,
    private val progressRepository: PlayerProgressRepository,
    private val passportRepository: PassportRepository,
    private val gameFactory: GameFactory,
    private val todayProvider: TodayProvider,
    private val adService: AdService,
) : ViewModel() {
    private val _state = MutableStateFlow<GameUiState>(GameUiState.Idle)
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private var startJob: Job? = null

    // Per-game bookkeeping. [gameId] changes whenever a game starts or is abandoned, so late stamp
    // results from a previous game never land in the current one.
    private var gameId = 0
    private var postmarkEarned: LocalDate? = null
    private val stampJobs = mutableListOf<Job>()

    init {
        // Consent form (if needed) and ad preloading happen at launch, not when the player is waiting.
        viewModelScope.launch { adService.initialize() }
    }

    fun startNewGame(mode: GameMode) = launchStart { start(mode) }

    fun watchAd(mode: GameMode) = launchStart { playAfterAd(mode) }

    fun guess(option: Country) {
        val before = (_state.value as? GameUiState.Playing) ?: return
        updateRound { it.guess(option) }
        val after = (_state.value as? GameUiState.Playing) ?: return
        val index = after.game.currentIndex
        val round = after.game.currentRound
        val justWon = round is HintRound && round.isWon &&
            !(before.game.currentIndex == index && before.game.currentRound.isWon)
        if (justWon && index !in after.stamps) recordStamp(index, round)
    }

    fun nextClue() = updateRound { (it as? HintRound)?.nextClue() ?: it }

    fun skipToCapital() = updateRound { (it as? HintRound)?.skipToCapital() ?: it }

    fun continueGame() {
        val playing = _state.value as? GameUiState.Playing ?: return
        if (playing.game.isOver) {
            finishGame(gameId)
        } else {
            _state.value = playing.copy(game = playing.game.nextRound())
        }
    }

    fun exitToMenu() {
        startJob?.cancel()
        gameId++
        postmarkEarned = null
        stampJobs.clear()
        _state.value = GameUiState.Idle
    }

    /** Saves the stamp right away (quitting keeps it) and shows it on the round once it is stored. */
    private fun recordStamp(index: Int, round: HintRound) {
        val id = gameId
        val playedOn = (_state.value as GameUiState.Playing).playedOn
        val tier = StampTier.forPoints(round.pointsEarned) ?: return
        val job = viewModelScope.launch {
            // A failed write must not crash the app: that round simply ends up without a stamp.
            val outcome = try {
                passportRepository.recordStamp(round.country.id, tier, playedOn)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            _state.update { state ->
                if (state is GameUiState.Playing && gameId == id) {
                    state.copy(stamps = state.stamps + (index to outcome))
                } else {
                    state
                }
            }
        }
        stampJobs += job
    }

    /** Waits for any stamp still being saved, then shows the results. */
    private fun finishGame(id: Int) {
        viewModelScope.launch {
            stampJobs.toList().joinAll()
            val playing = _state.value as? GameUiState.Playing ?: return@launch
            if (gameId != id) return@launch
            _state.value = GameUiState.Finished(buildSummary(playing))
        }
    }

    private fun buildSummary(playing: GameUiState.Playing): GameSummary {
        val game = playing.game
        val rounds = game.rounds.mapIndexed { index, round ->
            RoundSummary(
                number = index + 1,
                country = round.country,
                points = round.pointsEarned,
                isWon = round.isWon,
                stamp = playing.stamps[index],
            )
        }
        return GameSummary(
            mode = game.mode,
            score = game.score,
            maxScore = game.maxScore,
            playedOn = playing.playedOn,
            rounds = rounds,
            firstVisits = rounds.filter { it.stamp?.isFirstVisit == true }.map { it.country },
            postmark = postmarkEarned,
        )
    }

    private fun launchStart(block: suspend () -> Unit) {
        if (startJob?.isActive == true) return
        startJob = viewModelScope.launch { block() }
    }

    private suspend fun start(mode: GameMode) {
        _state.value = GameUiState.Loading
        try {
            val today = todayProvider.today()
            val progress = progressRepository.progress.first()
            if (!progress.canStartGame(today)) {
                playAfterAd(mode)
                return
            }
            val earnsPostmark = progress.usesLastFreeGame(today)
            val game = gameFactory.create(countryRepository.getAllCountries(), mode)
            progressRepository.recordGameStarted(today)
            val postmark = today.takeIf { earnsPostmark && passportRepository.recordPostmark(today) }
            gameId++
            postmarkEarned = postmark
            stampJobs.clear()
            _state.value = GameUiState.Playing(game, playedOn = today)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = GameUiState.Error(mode, e.message ?: "Couldn't start the game")
        }
    }

    private suspend fun playAfterAd(mode: GameMode) {
        _state.value = GameUiState.ShowingAd(mode)
        when (adService.showRewardedAd()) {
            // If no ad can be shown (offline, no fill), let them play rather than block them.
            RewardedAdResult.Rewarded, RewardedAdResult.Unavailable -> {
                progressRepository.recordAdWatched()
                start(mode)
            }
            RewardedAdResult.Dismissed -> _state.value = GameUiState.Idle
        }
    }

    private fun updateRound(transform: (GameRound) -> GameRound) = _state.update { state ->
        if (state is GameUiState.Playing) state.copy(game = state.game.updateCurrentRound(transform)) else state
    }
}
