package me.vladcip.guessthecountry.presentation

import me.vladcip.guessthecountry.domain.game.GameFactory
import me.vladcip.guessthecountry.domain.game.GameMode
import me.vladcip.guessthecountry.domain.game.HintRound
import me.vladcip.guessthecountry.domain.game.RoundPhase
import me.vladcip.guessthecountry.domain.progress.PlayerProgress
import me.vladcip.guessthecountry.testing.FakeCountryRepository
import me.vladcip.guessthecountry.testing.FakePlayerProgressRepository
import me.vladcip.guessthecountry.testing.FakeToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelTest {
    private val countries = FakeCountryRepository()
    private val progress = FakePlayerProgressRepository()
    private val today = FakeToday()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = GameViewModel(countries, progress, GameFactory(random = Random(1)), today)

    private val GameViewModel.playing get() = assertIs<GameUiState.Playing>(state.value)

    private val GameViewModel.hintRound get() = assertIs<HintRound>(playing.game.currentRound)

    private fun GameViewModel.playWholeGame() {
        repeat(7) {
            guess(playing.game.currentRound.country)
            continueGame()
        }
    }

    private fun GameViewModel.startNewGame() = startNewGame(GameMode.GuessTheCountry)

    private fun GameViewModel.useFreeGames() = repeat(PlayerProgress.FREE_GAMES_PER_DAY) {
        startNewGame()
        exitToMenu()
    }

    @Test
    fun `starts idle on the main menu`() {
        assertEquals(GameUiState.Idle, viewModel().state.value)
    }

    @Test
    fun `starting a game deals 7 rounds and uses a free game`() {
        val vm = viewModel()
        vm.startNewGame()

        assertEquals(7, vm.playing.game.totalRounds)
        assertEquals(2, progress.progress.value.freeGamesLeft(today.date))
    }

    @Test
    fun `guess and clue actions update the current round`() {
        val vm = viewModel()
        vm.startNewGame()
        val round = vm.playing.game.currentRound
        val wrong = round.options.first { it != round.country }

        vm.guess(wrong)
        assertEquals(4, vm.hintRound.pointsAvailable)
        vm.nextClue()
        assertEquals(3, vm.hintRound.pointsAvailable)
        vm.skipToCapital()
        assertEquals(RoundPhase.Capital, vm.hintRound.phase)
        vm.guess(round.country)
        assertEquals(1, vm.playing.game.score)
    }

    @Test
    fun `continue does nothing until the round is finished`() {
        val vm = viewModel()
        vm.startNewGame()
        vm.continueGame()
        assertEquals(1, vm.playing.game.roundNumber)
    }

    @Test
    fun `perfect game finishes with 35 out of 35 then returns to the menu`() {
        val vm = viewModel()
        vm.startNewGame()
        vm.playWholeGame()
        assertEquals(GameUiState.Finished(GameMode.GuessTheCountry, score = 35, maxScore = 35), vm.state.value)

        vm.exitToMenu()
        assertEquals(GameUiState.Idle, vm.state.value)
    }

    @Test
    fun `quitting goes straight back to the main menu without an ad`() {
        val vm = viewModel()
        vm.startNewGame()
        vm.exitToMenu()

        assertEquals(GameUiState.Idle, vm.state.value)
        vm.startNewGame()
        assertIs<GameUiState.Playing>(vm.state.value)
    }

    @Test
    fun `after 3 free games the next game needs an ad first`() {
        val vm = viewModel()
        vm.useFreeGames()

        vm.startNewGame()
        assertEquals(GameUiState.ShowingAd(GameMode.GuessTheCountry), vm.state.value)
    }

    @Test
    fun `watching an ad starts the game straight away and unlocks only that game`() {
        val vm = viewModel()
        vm.useFreeGames()

        vm.watchAd(GameMode.GuessTheFlag)
        assertEquals(GameUiState.ShowingAd(GameMode.GuessTheFlag), vm.state.value)
        vm.onAdWatched()
        assertEquals(GameMode.GuessTheFlag, vm.playing.game.mode)
        assertEquals(0, progress.progress.value.adCredits)

        vm.exitToMenu()
        vm.startNewGame()
        assertEquals(GameUiState.ShowingAd(GameMode.GuessTheCountry), vm.state.value)
    }

    @Test
    fun `free games come back the next day`() {
        val vm = viewModel()
        vm.useFreeGames()

        today.advanceOneDay()
        vm.startNewGame()
        assertIs<GameUiState.Playing>(vm.state.value)
    }

    @Test
    fun `premium player is never shown an ad`() {
        progress.progress.value = PlayerProgress(isPremium = true)
        val vm = viewModel()
        repeat(5) {
            vm.startNewGame()
            vm.playWholeGame()
            vm.exitToMenu()
        }
        assertEquals(5, progress.progress.value.gamesPlayed)
    }

    @Test
    fun `repository failure shows an error and does not use a free game`() {
        countries.error = IllegalStateException("db broken")
        val vm = viewModel()
        vm.startNewGame()

        assertEquals(GameUiState.Error(GameMode.GuessTheCountry, "db broken"), vm.state.value)
        assertEquals(0, progress.progress.value.gamesPlayed)
        assertTrue(progress.progress.value.canStartGame(today.date))
    }

    @Test
    fun `flag game deals 7 one-point rounds and shares the daily free games`() {
        val vm = viewModel()
        vm.startNewGame(GameMode.GuessTheFlag)
        assertEquals(GameMode.GuessTheFlag, vm.playing.game.mode)
        assertEquals(7, vm.playing.game.maxScore)

        vm.guess(vm.playing.game.currentRound.options.first { it != vm.playing.game.currentRound.country })
        assertEquals(0, vm.playing.game.score)
        assertTrue(vm.playing.game.currentRound.isFinished)
        assertEquals(2, progress.progress.value.freeGamesLeft(today.date))
    }

    @Test
    fun `clue actions do nothing in the flag game`() {
        val vm = viewModel()
        vm.startNewGame(GameMode.GuessTheFlag)
        val before = vm.playing.game
        vm.nextClue()
        vm.skipToCapital()
        assertEquals(before, vm.playing.game)
    }
}
